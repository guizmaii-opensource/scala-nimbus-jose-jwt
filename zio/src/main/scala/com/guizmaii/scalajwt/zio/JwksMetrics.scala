package com.guizmaii.scalajwt.zio

import zio.*
import zio.metrics.*

/**
 * Metrics for JWKS management and JWT validation.
 *
 * These metrics use ZIO's built-in metrics API.
 */
object JwksMetrics {

  inline private val Prefix: "scala_nimbus_" = "scala_nimbus_"

  /** Counter for total JWKS refresh attempts */
  private[scalajwt] val refreshTotal: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwks_refresh_total")

  /** Counter for successful JWKS refreshes */
  private[scalajwt] val refreshSuccess: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwks_refresh_success")

  /** Counter for failed JWKS refreshes */
  private[scalajwt] val refreshFailure: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwks_refresh_failure")

  /**
   * Histogram for JWKS fetch duration in milliseconds.
   *
   * Boundaries use exponential(start, factor, count):
   *   - start = 1.0 (first bucket at 1ms)
   *   - factor = 2.0 (each bucket doubles: 1, 2, 4, 8, ...)
   *   - count = 15 (15 buckets up to 32768ms ≈ 32s)
   *
   * Covers: fast cache hits (~1-10ms), typical network (~50-500ms),
   * slow responses (~1-5s), and timeout scenarios (up to 30s default timeout).
   */
  private[scalajwt] val refreshDuration: Metric.Histogram[Double] =
    Metric.histogram(
      s"${Prefix}jwks_refresh_duration_ms",
      MetricKeyType.Histogram.Boundaries.exponential(1.0, 2.0, 15)
    )

  /** Counter for JWT validation attempts */
  private[scalajwt] val validationTotal: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwt_validation_total")

  /** Counter for successful JWT validations */
  private[scalajwt] val validationSuccess: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwt_validation_success")

  /** Counter for failed JWT validations */
  private[scalajwt] val validationFailure: Metric.Counter[Long] =
    Metric.counter(s"${Prefix}jwt_validation_failure")

  /** Gauge for current JWKS health status (1 = healthy, 0 = degraded) */
  private[scalajwt] val healthStatus: Metric.Gauge[Double] =
    Metric.gauge(s"${Prefix}jwks_health_status")

  /** Aspect to track a JWKS refresh operation */
  private[scalajwt] val trackRefresh: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](io: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        refreshTotal.increment *>
          io.either.timed.flatMap { (duration, outcome) =>
            val counter =
              outcome match {
                case _: Right[?, ?] => refreshSuccess
                case _: Left[?, ?]  => refreshFailure
              }

            counter.increment *>
              refreshDuration.update(duration.toMillis.toDouble) *>
              ZIO.fromEither(outcome)
          }
    }

  /** Aspect to track a JWT validation operation */
  private[scalajwt] val trackValidation: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] {
      override def apply[R, E, A](io: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        validationTotal.increment *>
          io.tapBoth(_ => validationFailure.increment, _ => validationSuccess.increment)
    }
}
