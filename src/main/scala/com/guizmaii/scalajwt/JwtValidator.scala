package com.guizmaii.scalajwt

import com.nimbusds.jwt.JWTClaimsSet

final case class JwtToken(content: String)
final case class InvalidToken(cause: Throwable) extends RuntimeException(cause.getMessage, cause)

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet]
}
