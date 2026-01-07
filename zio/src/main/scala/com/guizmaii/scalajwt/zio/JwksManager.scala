package com.guizmaii.scalajwt.zio

import com.nimbusds.jose.jwk.JWKSet
import zio.*
import zio.http.{Client, Request}
import zio.telemetry.opentelemetry.core.trace.Tracer

import java.util.concurrent.atomic.AtomicReference
import scala.util.control.NonFatal

/**
 * Service that manages JWKS caching with background refresh.
 *
 * The JWKS is stored in an AtomicReference for lock-free, non-blocking reads.
 * Background refresh ensures the cache is always warm, so validation never blocks.
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
   * 1. Fetch JWKS immediately on startup (fails if can't fetch)
   * 2. Start a background fiber that refreshes periodically
   * 3. Interrupt the background fiber when the scope closes
   *
   * All JWKS fetch operations are automatically traced via OpenTelemetry.
   */
  def live: ZLayer[Client & JwksConfig & Tracer, JwksFetchError, JwksManager] =
    ZLayer.scoped {
      for {
        config      <- ZIO.service[JwksConfig]
        client      <- ZIO.service[Client]
        tracer      <- ZIO.service[Tracer]
        // Initial fetch - fail fast if we can't get JWKS on startup
        initialJwks <- fetchJwks(client, config, tracer).retry(config.initialRetrySchedule)
        jwksRef      = new AtomicReference[JWKSet](initialJwks)
        now         <- Clock.instant
        initialState = JwksHealth.Healthy(now, now.plusMillis(config.refreshInterval.toMillis))
        healthRef   <- Ref.make[JwksHealth](initialState)
        manager      = new JwksManagerLive(jwksRef, healthRef, client, config, tracer)
        // Start background refresh fiber
        _           <- backgroundRefresh(manager, config).forkScoped
      } yield manager
    }

  private[scalajwt] def fetchJwks(client: Client, config: JwksConfig, tracer: Tracer): ZIO[Scope, JwksFetchError, JWKSet] =
    client
      .request(Request.get(config.jwksUri))
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
      } @@ JwksMetrics.trackRefresh @@ JwksTracing.trackRefresh(tracer, config.jwksUri.encode)

  private def backgroundRefresh(manager: JwksManagerLive, config: JwksConfig): URIO[Scope, Unit] = {
    val refreshOnce: UIO[Unit] =
      manager.refresh
        .retry(config.refreshRetrySchedule)
        .foldZIO(
          success = _ => JwksMetrics.healthStatus.set(1.0), // Healthy
          failure = error => {
            inline def updateHealthRef: UIO[Unit] =
              manager.healthRef.update {
                case h: JwksHealth.Healthy  => JwksHealth.Degraded(h.lastRefresh, error)
                case d: JwksHealth.Degraded => d.copy(lastError = error)
              }

            JwksMetrics.healthStatus.set(0.0) /* Degraded */ *>
              updateHealthRef *>
              ZIO.logErrorCause(s"JWKS refresh failed: ${error.getMessage}", Cause.fail(error))
          }
        )

    refreshOnce
      .repeat(Schedule.spaced(config.refreshInterval))
      .forkScoped
      .unit
  }

}

private[scalajwt] final class JwksManagerLive(
  jwksRef: AtomicReference[JWKSet],
  val healthRef: Ref[JwksHealth],
  client: Client,
  config: JwksConfig,
  tracer: Tracer
) extends JwksManager {
  import com.guizmaii.scalajwt.zio.JwksManager.fetchJwks

  override val jwkSource: AtomicJWKSource = new AtomicJWKSource(jwksRef)

  override def jwkSet: UIO[JWKSet] = ZIO.succeed(jwksRef.get())

  override def refresh: IO[JwksFetchError, JWKSet] =
    ZIO.scoped {
      for {
        jwks    <- fetchJwks(client, config, tracer)
        now     <- Clock.instant
        _        = jwksRef.set(jwks)
        newState = JwksHealth.Healthy(now, now.plusMillis(config.refreshInterval.toMillis))
        _       <- healthRef.set(newState)
      } yield jwks
    }

  override def health: UIO[JwksHealth] = healthRef.get
}
