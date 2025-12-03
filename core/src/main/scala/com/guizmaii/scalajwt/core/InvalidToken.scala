package com.guizmaii.scalajwt.core

final case class InvalidToken(cause: Throwable) extends RuntimeException(cause.getMessage, cause)
