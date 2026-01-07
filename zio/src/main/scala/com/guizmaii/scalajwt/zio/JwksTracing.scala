package com.guizmaii.scalajwt.zio

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.StatusCode
import zio.*
import zio.telemetry.opentelemetry.core.trace.Tracer

/**
 * OpenTelemetry tracing for JWKS operations.
 *
 * Tracing is automatically applied to JWKS refresh and JWT validation operations
 * when using JwksManager.live and ZioJwtValidator.live layers.
 */
private[scalajwt] object JwksTracing {

  inline private val Prefix: "scala_nimbus." = "scala_nimbus."

  private val `jwks.uri`: AttributeKey[String] = AttributeKey.stringKey("jwks.uri")

  /** Aspect to trace a JWKS refresh operation */
  private[scalajwt] def trackRefresh(tracer: Tracer, jwksUri: String): ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] {
      override def apply[R, E <: Throwable, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        tracer.span(s"${Prefix}jwks.refresh", attributes = Attributes.of(`jwks.uri`, jwksUri)) { span =>
          zio.tapError(error => span.setStatus(StatusCode.ERROR, error.getMessage))
        }
    }

  /** Aspect to trace a JWT validation operation */
  private[scalajwt] def trackValidation(tracer: Tracer): ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] =
    new ZIOAspect[Nothing, Any, Nothing, Throwable, Nothing, Any] {
      override def apply[R, E <: Throwable, A](zio: ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
        tracer.span(s"${Prefix}jwt.validation") { span =>
          zio.tapBoth(
            error =>
              span.setAttribute("jwt.validation.success", false) *>
                span.setStatus(StatusCode.ERROR, error.getMessage),
            _ => span.setAttribute("jwt.validation.success", true)
          )
        }
    }
}
