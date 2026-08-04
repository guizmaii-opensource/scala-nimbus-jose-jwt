package com.guizmaii.scalajwt.zio

import com.guizmaii.zio.background.cache.core.{BackgroundCache, CacheState}
import com.nimbusds.jose.jwk.JWKSet
import zio.*
import zio.http.{Client, Request}
import zio.telemetry.opentelemetry.core.trace.Tracer

import scala.util.control.{NonFatal, NoStackTrace}

/**
 * Service that manages JWKS caching with background refresh.
 *
 * The JWKS is served from a `BackgroundCache`'s plain, synchronous read, so validation never
 * blocks.
 */
trait JwksManager {

  /** Current JWKS - never blocks, always returns cached value */
  def jwkSet: UIO[JWKSet]

  /** The JWKSource backed by this manager's cache. Thread-safe and non-blocking. */
  def jwkSource: AtomicJWKSource

  /** Force an immediate refresh (useful for key rotation scenarios) */
  def refresh: IO[JwksFetchError, JWKSet]

  /** Health status of the JWKS cache */
  def health: UIO[JwksHealth]
}

//noinspection MutatorLikeMethodIsParameterless
object JwksManager {

  /**
   * Creates a JwksManager with background refresh and OpenTelemetry tracing.
   *
   * The layer will:
   * 1. Fetch JWKS immediately on startup, retrying per `config.initialRetrySchedule` - fails the
   *    layer if that's exhausted without a successful fetch
   * 2. Start a background fiber that refreshes every `config.refreshInterval`. Before the first
   *    success, retries follow `config.initialRetrySchedule` too, same as startup; once healthy,
   *    each refresh attempt gets its own `config.refreshRetrySchedule` retry budget before that
   *    cycle is considered failed
   * 3. Interrupt the background fiber when the scope closes
   *
   * All JWKS fetch operations are automatically traced via OpenTelemetry.
   */
  def live: ZLayer[Client & JwksConfig & Tracer, JwksFetchError, JwksManager] =
    ZLayer.scoped {
      for {
        config           <- ZIO.service[JwksConfig]
        client           <- ZIO.service[Client]
        tracer           <- ZIO.service[Tracer]
        hasSucceededOnce <- Ref.make(false)
        // Only retry within a single attempt once we're past the warmup phase (driven by
        // `initialRetrySchedule` via `warmupSchedule` below): retrying here too, on top of that,
        // would let one warmup attempt eat the whole warmup budget by itself.
        fetch             = hasSucceededOnce.get.flatMap { succeededBefore =>
                              val attempt  = fetchJwks(client, config) @@
                                JwksMetrics.trackRefresh @@ JwksTracing.trackRefresh(tracer, config.jwksUri.encode)
                              val retrying = if (succeededBefore) attempt.retry(config.refreshRetrySchedule) else attempt
                              retrying.tap(_ => hasSucceededOnce.set(true))
                            }
        cache            <- BackgroundCache.make(
                              fetch,
                              schedule = Schedule.spaced(config.refreshInterval),
                              warmupSchedule = widenSchedule(config.initialRetrySchedule)
                            )
        // Fail fast if JWKS is unreachable on startup, matching the pre-BackgroundCache contract:
        // `cache.refresh` runs synchronously (not on the forked loop), so once this succeeds,
        // `cache.state()` is guaranteed to already reflect it - no race with the background loop.
        _                <- cache.refresh.retry(config.initialRetrySchedule)
      } yield new JwksManagerLive(cache, config)
    }

  private[scalajwt] def fetchJwks(client: Client, config: JwksConfig): ZIO[Any, JwksFetchError, JWKSet] =
    client
      .batched(Request.get(config.jwksUri))
      .mapError(JwksFetchError.NetworkError.apply)
      .timeoutFail(JwksFetchError.Timeout(config.fetchTimeout))(config.fetchTimeout)
      .flatMap { response =>
        response.body.asString.foldZIO(
          failure = e => Exit.fail(JwksFetchError.ParseError(e)),
          success = body =>
            try Exit.succeed(JWKSet.parse(body))
            catch {
              case e if NonFatal(e) => Exit.fail(JwksFetchError.ParseError(e))
            }
        )
      }

  // `JwksConfig`'s retry schedules are typed `Schedule[Any, Throwable, Any]` (so they compose with
  // `.retry` anywhere a `Throwable`-failing effect is retried). `BackgroundCache.make`'s schedule
  // parameters are typed `Schedule[R, Any, Any]`, which - because `Schedule` is contravariant in
  // its input - a `Throwable`-typed schedule doesn't satisfy (`Any` is not `<: Throwable`). The
  // schedules here never actually inspect their input, so this is a safe, local widening: it just
  // needs *some* `Throwable` value to satisfy the type, never a meaningful one.
  private def widenSchedule[Out](schedule: Schedule[Any, Throwable, Out]): Schedule[Any, Any, Out] =
    schedule.contramap((_: Any) => new RuntimeException("unused: BackgroundCache never inspects this input") with NoStackTrace)

}

private[scalajwt] final class JwksManagerLive(
  cache: BackgroundCache[JwksFetchError, JWKSet],
  config: JwksConfig
) extends JwksManager {

  // Safe: `JwksManagerLive` is only ever constructed after `JwksManager.live` has already
  // confirmed a successful fetch, and CacheState only ever returns to `Loading` before the very
  // first success. So `cache.get()` can never actually be `NotYetAvailable` here.
  private def currentJwks: JWKSet =
    cache.get().value.getOrElse(throw new IllegalStateException("JwksManager: no JWKS available after a confirmed successful fetch"))

  override val jwkSource: AtomicJWKSource = new AtomicJWKSource(() => currentJwks)

  override def jwkSet: UIO[JWKSet] = ZIO.succeed(currentJwks)

  override def refresh: IO[JwksFetchError, JWKSet] = cache.refresh *> ZIO.succeed(currentJwks)

  override def health: UIO[JwksHealth] =
    ZIO.succeed {
      cache.state() match {
        case CacheState.Healthy(_, lastRefresh)     =>
          JwksHealth.Healthy(lastRefresh, lastRefresh.plusMillis(config.refreshInterval.toMillis))
        case CacheState.Degraded(_, lastRefresh, e) =>
          JwksHealth.Degraded(lastRefresh, e)
        case CacheState.Loading                     =>
          throw new IllegalStateException("JwksManager: reached Loading after a confirmed successful fetch")
      }
    }
}
