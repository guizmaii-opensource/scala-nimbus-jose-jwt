# scala-nimbus-jose-jwt

[![CI](https://github.com/guizmaii-opensource/scala-nimbus-jose-jwt/actions/workflows/ci.yaml/badge.svg)](https://github.com/guizmaii-opensource/scala-nimbus-jose-jwt/actions/workflows/ci.yaml)
[![codecov](https://codecov.io/gh/guizmaii/scala-nimbus-jose-jwt/branch/master/graph/badge.svg)](https://codecov.io/gh/guizmaii/scala-nimbus-jose-jwt)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

**Small, simple and opinionated JWT token validator for Scala.**

## Previous versions

You're reading the README of the v3.x.x of the lib.

To read the doc of previous versions:
- v2.x.x: [see here](https://github.com/guizmaii/scala-nimbus-jose-jwt/tree/v2.3.6)
- v1.x.x: [see here](https://github.com/guizmaii/scala-nimbus-jose-jwt/tree/v1.0.2)

## Goal

**Provide a very simple API to help people do JWT token validation correctly.**

This project uses `Nimbus JOSE + JWT` (https://connect2id.com/products/nimbus-jose-jwt) to validate JWT tokens.
The aim of this project is not, and will never be, to provide a Scala interface to `Nimbus JOSE + JWT`.

I chose `Nimbus JOSE + JWT` because it seems to be audited and battle-tested.

The code size is small in order to be as readable as possible, so as free of bugs as possible.

## Setup

The library is split into four modules:

### Core module

Contains the base `JwtValidator` trait and `ConfigurableJwtValidator`:

```scala
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt" % "3.0.0"
```

### AWS Cognito module

Contains the `AwsCognitoJwtValidator` (depends on core):

```scala
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt-cognito" % "3.0.0"
```

### Auth0 module

Contains the `Auth0JwtValidator` (depends on core):

```scala
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt-auth0" % "3.0.0"
```

### ZIO module

Contains `ZioJwtValidator` for non-blocking JWT validation with ZIO (depends on core):

```scala
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt-zio" % "3.0.0"
```

## API

The API is very simple:

```scala
import com.nimbusds.jwt.JWTClaimsSet
import com.guizmaii.scalajwt.core.*

opaque type JwtToken = String
object JwtToken {
  def apply(content: String): JwtToken = content
  extension (jt: JwtToken) { def content: String = jt }
}

final case class InvalidToken(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
object InvalidToken {
  def apply(t: Throwable): InvalidToken = InvalidToken(message = t.getMessage, cause = t)
}

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet]
}
```

## Available API implementations

### 1. ConfigurableJwtValidator

The more flexible implementation of the `JwtValidator` interface.

It only requires a `JWKSource` instance and a `JWTClaimsSetVerifier[SecurityContext]` instance.    

For more information on the different `JWKSource` implementations Nimbus provides, look at the classes in the `com.nimbusds.jose.jwk.source` package here: https://www.javadoc.io/doc/com.nimbusds/nimbus-jose-jwt    

For more information on how to create a `JWTClaimsSetVerifier[SecurityContext]`, which is the interface provided by `nimbus-jose-jwt` to
declare your claims validation rules, see [validating-jwt-access-tokens#claims](https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#claims)

Example of use:
```scala
import com.guizmaii.scalajwt.core.*

import java.net.URL
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier


val jwtValidator: JwtValidator = {
  val jwkSource: JWKSource[SecurityContext] =
    new RemoteJWKSet(new URL(s"https://<your.jwks.prodvider.example.com>/.well-known/jwks.json"))

  val claimsSetVerifier: JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](
      Set("...").asJava,
      new JWTClaimsSet.Builder().issuer("...").build(),
      Set("exp").asJava,
      null
    )

  ConfigurableJwtValidator(keySource = jwkSource, claimsVerifier = claimsSetVerifier)
}

val token: JwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

For more information on JWKs, you can read:   
  - Auth0 doc: https://auth0.com/docs/jwks    
  - Nimbus doc: https://connect2id.com/products/server/docs/api/jwk-set       
  - AWS Cognito doc: https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-with-identity-providers.html#amazon-cognito-identity-user-pools-using-id-and-access-tokens-in-web-api

Other constructor parameters are:

  - `algorithm: SupportedJWSAlgorithm = RS256`   
    (Optional) The algorithm used to decode tokens.   
    Default is `RS256`.

  - `maybeCtx: Option[SecurityContext] = None`   
    (Optional) Security context.    
    Default is `null` (no Security Context).
    
### 2. AWS Cognito JWT Validator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `AwsCognitoJwtValidator.scala` file.      
It follows the AWS documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.core.*
import com.guizmaii.scalajwt.cognito.*

import com.nimbusds.jwt.JWTClaimsSet

val awsCognitoJwtValidator: JwtValidator = {
  val s3Region          = S3Region(value = "eu-west-1")
  val cognitoUserPoolId = CognitoUserPoolId(value = "...")

  AwsCognitoJwtValidator(s3Region, cognitoUserPoolId)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = awsCognitoJwtValidator.validate(jwtToken)
```

An additional constructor is provided.
This second constructor allows you to add more contraints and/or to replace the default `JWTClaimsSetVerifier` used by the implementation:

```scala
import com.guizmaii.scalajwt.core.*
import com.guizmaii.scalajwt.cognito.*

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTClaimsVerifier, JWTClaimsSetVerifier}

val awsCognitoJwtValidator: JwtValidator = {
  val s3Region          = S3Region(value = "eu-west-1")
  val cognitoUserPoolId = CognitoUserPoolId(value = "...")

  def customiseDefaultJWTClaimsSetVerifier(default: DefaultJWTClaimsVerifier[SecurityContext]): JWTClaimsSetVerifier[SecurityContext] = ??? // To implement

  AwsCognitoJwtValidator(s3Region, cognitoUserPoolId, customiseDefaultJWTClaimsSetVerifier)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = awsCognitoJwtValidator.validate(jwtToken)
```

### 3. Auth0 JWT Validator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `Auth0JwtValidator.scala` file.
It follows the Auth0 documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.core.*
import com.guizmaii.scalajwt.auth0.*

import com.nimbusds.jwt.JWTClaimsSet

val auth0JwtValidator: JwtValidator = {
  val auth0Domain   = Auth0Domain(value = "...")
  val auth0Audience = Auth0Audience(value = "...")

  Auth0JwtValidator(auth0Domain, auth0Audience)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = auth0JwtValidator.validate(jwtToken)
```

An additional constructor is provided.    
This second constructor allows you to add more contraints and/or to replace the default `JWTClaimsSetVerifier` used by the implementation:

```scala
import com.guizmaii.scalajwt.core.*
import com.guizmaii.scalajwt.auth0.*

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTClaimsVerifier, JWTClaimsSetVerifier}

val auth0JwtValidator: JwtValidator = {
  val auth0Domain   = Auth0Domain(value = "...")
  val auth0Audience = Auth0Audience(value = "...")

  def customiseDefaultJWTClaimsSetVerifier(default: DefaultJWTClaimsVerifier[SecurityContext]): JWTClaimsSetVerifier[SecurityContext] = ??? // To implement

  Auth0JwtValidator(auth0Domain, auth0Audience, customiseDefaultJWTClaimsSetVerifier)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = auth0JwtValidator.validate(jwtToken)
```

### 4. ZIO JWT Validator

The ZIO module provides **non-blocking JWT validation** for ZIO applications. Unlike the other validators, this one guarantees that validation never blocks the calling fiber after initialization.

**Key features:**
- JWKS is fetched using zio-http (non-blocking I/O)
- JWKS is stored in an `AtomicReference` for instant, lock-free reads
- Background fiber refreshes JWKS periodically
- Health monitoring for observability
- Graceful degradation with stale cache on refresh failures
- Built-in metrics (ZIO Metrics) and tracing (OpenTelemetry)

**Why use this instead of the core validators with `ZIO.attemptBlocking`?**

The core validators can block when fetching JWKS. For high-throughput APIs where every request requires JWT validation, using `ZIO.attemptBlocking` is expensive because it shifts work to the blocking thread pool. The ZIO module ensures validation is always non-blocking after startup.

Example of use:
```scala
import com.guizmaii.scalajwt.zio.*
import com.guizmaii.scalajwt.core.{InvalidToken, JwtToken}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import zio.*
import zio.http.{Client, URL}
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.core.trace.Tracer

object MyApp extends ZIOAppDefault {

  // Define your claims verifier
  val claimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] = {
    import scala.jdk.CollectionConverters.*
    new DefaultJWTClaimsVerifier[SecurityContext](
      Set("https://my-api.example.com").asJava,                        // accepted audiences
      new JWTClaimsSet.Builder().issuer("https://issuer.com").build(), // exact match claims
      Set("exp", "sub").asJava,                                        // required claims
      null                                                             // prohibited claims
    )
  }

  // JwksConfig uses zio.http.URL
  val configLayer: ULayer[JwksConfig] =
    ZLayer.succeed(JwksConfig(URL.decode("https://issuer.com/.well-known/jwks.json").toOption.get))

  // The validator layer requires Client, JwksConfig, and Tracer
  val validatorLayer: ZLayer[Client & JwksConfig & Tracer, JwksFetchError, ZioJwtValidator] =
    ZioJwtValidator.configured(claimsVerifier)

  val program: ZIO[ZioJwtValidator, InvalidToken, JWTClaimsSet] =
    for {
      validator <- ZIO.service[ZioJwtValidator]
      claims    <- validator.validate(JwtToken("eyJ..."))
    } yield claims

  // Noop tracer layer for when you don't need tracing
  val noopTracerLayer: TaskLayer[Tracer] =
    OpenTelemetry.noop() >>> OpenTelemetry.tracer("my-app")

  def run =
    program
      .provide(
        validatorLayer,
        configLayer,
        Client.default,
        noopTracerLayer
      )
      .debug("Claims")
}
```

**Health monitoring:**

You can monitor the JWKS cache health status:

```scala
val healthCheck: ZIO[JwksManager, Nothing, Unit] =
  for {
    manager <- ZIO.service[JwksManager]
    health  <- manager.health
    _       <- health match {
      case JwksHealth.Healthy(lastRefresh, nextRefresh) =>
        ZIO.logInfo(s"JWKS healthy, last refresh: $lastRefresh, next: $nextRefresh")
      case JwksHealth.Degraded(lastRefresh, error) =>
        ZIO.logWarning(s"JWKS degraded since $lastRefresh: ${error.getMessage}")
    }
  } yield ()
```

**Configuration options:**

- `jwksUri`: The URL to fetch JWKS from (type: `zio.http.URL`, use `JwksConfig.fromString` for convenience)
- `refreshInterval`: How often to refresh JWKS (default: 4 minutes)
- `fetchTimeout`: Timeout for JWKS fetch requests (default: 30 seconds)
- `initialRetrySchedule`: Retry schedule for initial JWKS fetch on startup (default: every 250ms for up to 5 seconds)
- `refreshRetrySchedule`: Retry schedule for background refresh failures (default: every 250ms for up to 30 seconds)

**Metrics:**

The ZIO module includes built-in metrics using ZIO's native metrics API:

- `scala_nimbus_jwks_refresh_total` - Counter for total JWKS refresh attempts
- `scala_nimbus_jwks_refresh_success` - Counter for successful refreshes
- `scala_nimbus_jwks_refresh_failure` - Counter for failed refreshes
- `scala_nimbus_jwks_refresh_duration_ms` - Histogram of refresh durations
- `scala_nimbus_jwt_validation_total` - Counter for validation attempts
- `scala_nimbus_jwt_validation_success` - Counter for successful validations
- `scala_nimbus_jwt_validation_failure` - Counter for failed validations
- `scala_nimbus_jwks_health_status` - Gauge (1 = healthy, 0 = degraded)

**OpenTelemetry tracing:**

The module has built-in OpenTelemetry tracing. All JWKS refresh and JWT validation operations are automatically traced when a `Tracer` is provided.

Spans:
- `scala_nimbus.jwks.refresh` - Traces JWKS fetch operations
  - Attribute: `jwks.uri` - The JWKS endpoint URL
- `scala_nimbus.jwt.validation` - Traces JWT validation operations
  - Attribute: `jwt.validation.success` - Boolean indicating validation result

On errors, spans are marked with `StatusCode.ERROR` and include the error message.

To enable tracing, provide a `Tracer` layer from [zio-opentelemetry v4](https://github.com/zio/zio-telemetry):

```scala
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.core.trace.Tracer

// With real tracing (configure your OpenTelemetry SDK)
program.provide(validatorLayer, configLayer, Client.default, realTracerLayer)

// Without tracing (use noop tracer)
val noopTracerLayer: TaskLayer[Tracer] = OpenTelemetry.noop() >>> OpenTelemetry.tracer("my-app")
program.provide(validatorLayer, configLayer, Client.default, noopTracerLayer)
```

## Migrations

### Migrating from v2.x.x to v3.x.x

v3 introduces a modular architecture. The library is now split into three separate modules:

1. **Package changes:**
   - `com.guizmaii.scalajwt.*` → `com.guizmaii.scalajwt.core.*`
   - `com.guizmaii.scalajwt.implementations.ConfigurableJwtValidator` → `com.guizmaii.scalajwt.core.ConfigurableJwtValidator`
   - `com.guizmaii.scalajwt.implementations.AwsCognitoJwtValidator` → `com.guizmaii.scalajwt.cognito.AwsCognitoJwtValidator`
   - `com.guizmaii.scalajwt.implementations.{S3Region, CognitoUserPoolId}` → `com.guizmaii.scalajwt.cognito.*`
   - `com.guizmaii.scalajwt.implementations.Auth0JwtValidator` → `com.guizmaii.scalajwt.auth0.Auth0JwtValidator`
   - `com.guizmaii.scalajwt.implementations.{Auth0Domain, Auth0Audience}` → `com.guizmaii.scalajwt.auth0.*`

2. **Dependency changes:**
   - For core functionality only: `"com.guizmaii" %% "scala-nimbus-jose-jwt" % "3.0.0"`
   - For AWS Cognito: `"com.guizmaii" %% "scala-nimbus-jose-jwt-cognito" % "3.0.0"`
   - For Auth0: `"com.guizmaii" %% "scala-nimbus-jose-jwt-auth0" % "3.0.0"`

### Migrating from v1.x.x to v2.x.x

Be sure to read the CHANGELOG to understand how to migrate to v2.
