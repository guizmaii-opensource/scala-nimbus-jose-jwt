package com.guizmaii.scalajwt.core

final case class InvalidToken(message: String, unsafeCause: Throwable | Null = null) extends RuntimeException(message, unsafeCause) {
  override def getCause: Throwable = unsafeCause
  def cause: Option[Throwable]     = Option(unsafeCause)
}
object InvalidToken {
  def apply(t: Throwable): InvalidToken = InvalidToken(message = t.getMessage, unsafeCause = t)
}
