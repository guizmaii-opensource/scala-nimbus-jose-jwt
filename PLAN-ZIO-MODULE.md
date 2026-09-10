# ZIO Module Implementation Plan

## Problem Statement

The current JWT validation can block the calling thread when the nimbus-jose-jwt library needs to fetch JWKS (JSON Web Key Set) from a remote URL. This happens because:

1. **Cold start**: No cached JWKS exists when the application starts
2. **Cache expiry**: The cached JWKS has expired and needs refresh
3. **Key rotation**: A new signing key is encountered that isn't in the current cache
4. **Outage recovery**: Recovering from a network outage

While nimbus-jose-jwt provides refresh-ahead caching (refreshing 30s before the 5-minute TTL expires), this still has edge cases where blocking occurs. For a high-throughput API where every request requires JWT validation, using `ZIO.attemptBlocking` is too expensive (it shifts work to the blocking thread pool).

## Goal

Provide a ZIO module where **validation never blocks the calling fiber** after initialization, allowing safe use of `ZIO.attempt` instead of `ZIO.attemptBlocking`.

---

## Solution Architecture

### Key Insight

The JWT validation process has two distinct phases:
1. **JWKS retrieval** (I/O bound, potentially blocking) - fetching public keys from a URL
2. **Token validation** (CPU bound, non-blocking) - parsing JWT, verifying signature, checking claims

By separating these concerns, we can:
- Handle JWKS retrieval with ZIO-native non-blocking I/O
- Keep token validation synchronous (it's CPU-bound anyway)

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ZIO Module                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────┐     ┌──────────────────────────────────────┐   │
│  │   JwksManager       │     │        ZioJwtValidator               │   │
│  │   (ZIO Service)     │     │        (ZIO Service)                 │   │
│  │                     │     │                                      │   │
│  │  - Ref[JWKSet]      │────▶│  - validate: JwtToken => ZIO[...]   │   │
│  │  - Background       │     │  - Uses in-memory JWKSet             │   │
│  │    refresh fiber    │     │  - Pure CPU-bound validation         │   │
│  │  - ZIO HTTP fetch   │     │  - Never blocks                      │   │
│  └─────────────────────┘     └──────────────────────────────────────┘   │
│           ▲                                                              │
│           │                                                              │
│  ┌────────┴────────┐                                                    │
│  │  ZLayer.scoped  │  (manages lifecycle of background refresh fiber)  │
│  └─────────────────┘                                                    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Detailed Design

### 1. Core Types

```scala
// zio/src/main/scala/com/guizmaii/scalajwt/zio/

// Re-export core types for convenience
export com.guizmaii.scalajwt.core.{JwtToken, InvalidToken, SupportedJWSAlgorithm}

// JWKS configuration
final case class JwksConfig(
  jwksUrl: URL,
  refreshInterval: Duration = 4.minutes,     // Refresh before typical 5min TTL
  initialRetrySchedule: Schedule[Any, Throwable, Any] = Schedule.exponential(1.second) && Schedule.recurs(5),
  refreshRetrySchedule: Schedule[Any, Throwable, Any] = Schedule.exponential(1.second) && Schedule.recurs(3)
)
```

### 2. JwksManager Service

Manages the JWKS cache with background refresh:

```scala
trait JwksManager {
  /** Current JWKS - never blocks, always returns cached value */
  def jwkSet: UIO[JWKSet]

  /** Force an immediate refresh (useful for key rotation scenarios) */
  def refresh: IO[JwksFetchError, JWKSet]

  /** Health status of the JWKS cache */
  def health: UIO[JwksHealth]
}

object JwksManager {
  /** Creates a JwksManager with background refresh.
    * The returned Scoped effect:
    * 1. Fetches JWKS immediately (fails if can't fetch on startup)
    * 2. Starts a background fiber that refreshes periodically
    * 3. Interrupts the background fiber on scope close
    */
  def live(config: JwksConfig): ZLayer[Client, JwksFetchError, JwksManager]
}

// Health status
enum JwksHealth {
  case Healthy(lastRefresh: Instant, nextRefresh: Instant)
  case Degraded(lastRefresh: Instant, lastError: JwksFetchError, usingStaleCache: Boolean)
}

// Errors
sealed trait JwksFetchError extends Throwable
object JwksFetchError {
  final case class NetworkError(cause: Throwable) extends JwksFetchError
  final case class ParseError(cause: Throwable) extends JwksFetchError
  final case class Timeout(duration: Duration) extends JwksFetchError
}
```

### 3. In-Memory JWKSource

A `JWKSource[SecurityContext]` implementation that reads from memory:

```scala
import java.util.concurrent.atomic.AtomicReference

/** A JWKSource that reads from an AtomicReference[JWKSet].
  * The get() method is O(1) and never blocks.
  */
final class AtomicJWKSource(ref: AtomicReference[JWKSet]) extends JWKSource[SecurityContext] {
  override def get(selector: JWKSelector, context: SecurityContext): java.util.List[JWK] =
    selector.select(ref.get())
}
```

**Why `AtomicReference` instead of ZIO `Ref`:**
- `AtomicReference.get()` is a simple volatile read - pure Java, no ZIO runtime needed
- Avoids `Unsafe.unsafe { Runtime.default.unsafe.run(...) }` ceremony
- Same lock-free, non-blocking guarantees
- The `JwksManager` updates the `AtomicReference` from ZIO code; the `JWKSource` reads it from Java/Scala code
- Clean separation: ZIO manages the lifecycle, Java primitive provides the synchronous interface

### 4. ZioJwtValidator Service

```scala
trait ZioJwtValidator {
  /** Validate a JWT token.
    * Never blocks - safe to use with ZIO.attempt (not attemptBlocking)
    */
  def validate(token: JwtToken): IO[InvalidToken, JWTClaimsSet]
}

object ZioJwtValidator {
  /** Create a validator from a JwksManager and claims verifier */
  def live(
    claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
    algorithm: SupportedJWSAlgorithm = SupportedJWSAlgorithm.RS256
  ): ZLayer[JwksManager, Nothing, ZioJwtValidator]

  /** Convenience: Create a complete validator layer from config and claims verifier */
  def configured(
    config: JwksConfig,
    claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
    algorithm: SupportedJWSAlgorithm = SupportedJWSAlgorithm.RS256
  ): ZLayer[Client, JwksFetchError, ZioJwtValidator]
}
```

### 5. Implementation Details

#### Background Refresh Strategy

```scala
private def backgroundRefresh(
  config: JwksConfig,
  jwksRef: AtomicReference[JWKSet],      // Java AtomicReference for lock-free reads
  healthRef: Ref[JwksHealth],             // ZIO Ref for health (only accessed from ZIO code)
  fetchJwks: IO[JwksFetchError, JWKSet]
): URIO[Scope, Unit] = {
  val refreshOnce: UIO[Unit] =
    fetchJwks
      .tap { jwks =>
        ZIO.succeed(jwksRef.set(jwks)) *> healthRef.set(JwksHealth.Healthy(...))
      }
      .retry(config.refreshRetrySchedule)
      .catchAll { error =>
        healthRef.update {
          case h: JwksHealth.Healthy  => JwksHealth.Degraded(..., usingStaleCache = true)
          case d: JwksHealth.Degraded => d.copy(lastError = error)
        } *> ZIO.logWarning(s"JWKS refresh failed: $error")
      }

  refreshOnce
    .repeat(Schedule.fixed(config.refreshInterval))
    .forkScoped
    .unit
}
```

#### JWKS Fetching with ZIO HTTP

```scala
private def fetchJwks(url: URL): ZIO[Client, JwksFetchError, JWKSet] =
  Client
    .request(Request.get(url.toString))
    .flatMap(_.body.asString)
    .mapError(e => JwksFetchError.NetworkError(e))
    .flatMap { body =>
      ZIO.attempt(JWKSet.parse(body))
        .mapError(e => JwksFetchError.ParseError(e))
    }
    .timeoutFail(JwksFetchError.Timeout(30.seconds))(30.seconds)
```

---

## Alternative Approaches Considered

### Option A: Use nimbus-jose-jwt's refresh-ahead caching

**Pros:**
- Minimal code changes
- Leverage battle-tested caching logic

**Cons:**
- Still uses blocking I/O under the hood
- Background refresh happens on nimbus's internal thread, not ZIO fibers
- Edge cases still block (cold start, key rotation)
- Can't benefit from ZIO's structured concurrency

**Verdict**: Rejected - doesn't solve the core problem

### Option B: Wrap nimbus with attemptBlocking everywhere

**Pros:**
- Simple to implement
- Uses existing nimbus infrastructure

**Cons:**
- Expensive for high-throughput APIs
- Every validation pays the blocking thread pool tax
- Defeats the purpose of using ZIO

**Verdict**: Rejected - too expensive

### Option C: Custom ZIO-native JWKS management (RECOMMENDED)

**Pros:**
- True non-blocking validation after initialization
- ZIO-native HTTP (zio-http)
- Integrates with ZIO's lifecycle (ZLayer, Scope)
- Health monitoring and observability
- Graceful degradation with stale cache

**Cons:**
- More code to maintain
- Need to handle JWKS parsing ourselves

**Verdict**: Recommended - solves the problem correctly

---

## Module Structure

```
zio/
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── com/
│   │           └── guizmaii/
│   │               └── scalajwt/
│   │                   └── zio/
│   │                       ├── JwksConfig.scala          # Configuration
│   │                       ├── JwksFetchError.scala      # Error types
│   │                       ├── JwksHealth.scala          # Health status
│   │                       ├── JwksManager.scala         # JWKS cache service
│   │                       ├── AtomicJWKSource.scala     # In-memory JWKSource (uses AtomicReference)
│   │                       └── ZioJwtValidator.scala     # Main validator service
│   └── test/
│       └── scala/
│           └── com/
│               └── guizmaii/
│                   └── scalajwt/
│                       └── zio/
│                           ├── JwksManagerSpec.scala
│                           ├── ZioJwtValidatorSpec.scala
│                           └── TestUtils.scala
```

---

## Dependencies

```scala
// In build.sbt
val zioVersion     = "2.1.14"
val zioHttpVersion = "3.0.1"

lazy val zioModule =
  project
    .in(file("zio"))
    .settings(stdSettings *)
    .settings(
      name := "scala-nimbus-jose-jwt-zio",
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio"          % zioVersion,
        "dev.zio" %% "zio-http"     % zioHttpVersion,
        "dev.zio" %% "zio-test"     % zioVersion     % Test,
        "dev.zio" %% "zio-test-sbt" % zioVersion     % Test,
      ),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
    .dependsOn(core % "test->test;compile->compile")
```

---

## Usage Example

```scala
import com.guizmaii.scalajwt.zio.*
import com.guizmaii.scalajwt.core.*
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jose.proc.SecurityContext
import zio.*
import zio.http.Client
import java.net.URL

object MyApp extends ZIOAppDefault {

  // Define your claims verifier (validates iss, aud, exp, etc.)
  val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] = {
    import scala.jdk.CollectionConverters.*
    new DefaultJWTClaimsVerifier[SecurityContext](
      Set("https://my-api.example.com").asJava,                    // accepted audiences
      new JWTClaimsSet.Builder().issuer("https://issuer.com").build(), // exact match claims
      Set("exp", "sub").asJava,                                    // required claims
      null                                                         // prohibited claims
    )
  }

  val config = JwksConfig(
    jwksUrl = new URL("https://issuer.com/.well-known/jwks.json"),
    refreshInterval = 4.minutes
  )

  val validatorLayer: ZLayer[Client, JwksFetchError, ZioJwtValidator] =
    ZioJwtValidator.configured(config, claimsVerifier)

  val program: ZIO[ZioJwtValidator, InvalidToken, JWTClaimsSet] =
    for {
      validator <- ZIO.service[ZioJwtValidator]
      claims    <- validator.validate(JwtToken("eyJ..."))
    } yield claims

  def run =
    program
      .provide(validatorLayer, Client.default)
      .debug("Claims")
}
```

### With Health Monitoring

```scala
val programWithHealthCheck: ZIO[JwksManager & ZioJwtValidator, Nothing, Unit] =
  for {
    jwks      <- ZIO.service[JwksManager]
    health    <- jwks.health
    _         <- health match {
      case JwksHealth.Degraded(_, error, _) =>
        ZIO.logWarning(s"JWKS cache degraded: $error")
      case JwksHealth.Healthy(_, _) =>
        ZIO.unit
    }
    validator <- ZIO.service[ZioJwtValidator]
    // ... use validator
  } yield ()
```

---

## Testing Strategy

### Unit Tests

1. **JwksManager tests**
   - Initial fetch success/failure
   - Background refresh works
   - Degraded mode with stale cache
   - Health status updates correctly

2. **ZioJwtValidator tests**
   - Valid token validation
   - Invalid token rejection
   - Expired token rejection
   - Wrong issuer/audience rejection

3. **AtomicJWKSource tests**
   - Returns correct keys from cache
   - Thread-safety verification

### Integration Tests

1. **With mock JWKS server** (using zio-http test server)
   - Full flow: fetch -> cache -> validate
   - Key rotation simulation
   - Server outage simulation

---

## Implementation Order

1. **Phase 1: Core infrastructure**
   - [ ] Add `zio` module to build.sbt
   - [ ] Implement `JwksFetchError` sealed trait
   - [ ] Implement `JwksHealth` enum
   - [ ] Implement `JwksConfig` case class

2. **Phase 2: JWKS management**
   - [ ] Implement `AtomicJWKSource`
   - [ ] Implement `JwksManager` service with background refresh
   - [ ] Add unit tests for JWKS management

3. **Phase 3: Validator**
   - [ ] Implement `ZioJwtValidator` trait and live implementation
   - [ ] Add unit tests for validator

4. **Phase 4: Polish**
   - [ ] Integration tests with mock server
   - [ ] Documentation and examples
   - [ ] Update README

5. **Phase 5: Observability**
   - [ ] Add metrics via ZIO Metrics (refresh latency, refresh success/failure counts, cache age)
   - [ ] Add OpenTelemetry tracing for JWKS fetch operations

---

## Summary

This design provides:

- **Guaranteed non-blocking validation** after initialization
- **ZIO-native implementation** using zio-http and ZIO services
- **Proper lifecycle management** with ZLayer and Scope
- **Health monitoring** for observability
- **Graceful degradation** with stale cache support
- **Clean API** that matches ZIO idioms
- **Provider-agnostic** - works with any JWKS URL (Auth0, Cognito, Keycloak, custom, etc.)
