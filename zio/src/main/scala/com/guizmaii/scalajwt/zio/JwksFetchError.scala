package com.guizmaii.scalajwt.zio

import zio.Duration

import scala.util.control.NoStackTrace

sealed trait JwksFetchError extends RuntimeException with NoStackTrace
object JwksFetchError {
  final case class NetworkError(cause: Throwable) extends JwksFetchError {
    override def getMessage: String  = s"Network error while fetching JWKS: ${cause.getMessage}"
    override def getCause: Throwable = cause
  }

  final case class ParseError(cause: Throwable) extends JwksFetchError {
    override def getMessage: String  = s"Failed to parse JWKS: ${cause.getMessage}"
    override def getCause: Throwable = cause
  }

  final case class Timeout(duration: Duration) extends JwksFetchError {
    override def getMessage: String = s"JWKS fetch timed out after $duration"
  }
}
