package com.guizmaii.scalajwt.core

final case class InvalidToken(message: String, cause: Throwable = null) extends RuntimeException(message, cause)
object InvalidToken {
  def apply(t: Throwable): InvalidToken = InvalidToken(message = t.getMessage, cause = t)
}
