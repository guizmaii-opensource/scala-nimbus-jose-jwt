package com.guizmaii.scalajwt.zio

import zio.http.URL
import zio.{durationInt, Duration, Schedule}

final case class JwksConfig(
  jwksUri: URL,
  refreshInterval: Duration = 4.minutes,
  fetchTimeout: Duration = 30.seconds,
  initialRetrySchedule: Schedule[Any, Throwable, Any] = Schedule.spaced(250.millis) && Schedule.upTo(5.seconds),
  refreshRetrySchedule: Schedule[Any, Throwable, Any] = Schedule.spaced(250.millis) && Schedule.upTo(30.seconds)
)

object JwksConfig {

  /** Create a JwksConfig from a JWKS URL string. Returns Left if the URL is invalid. */
  def fromString(jwksUri: String): Either[IllegalArgumentException, JwksConfig] =
    URL.decode(jwksUri) match {
      case Right(url) => Right(JwksConfig(url))
      case Left(e)    => Left(new IllegalArgumentException(s"Invalid JWKS URI: $jwksUri", e))
    }
}
