package com.guizmaii.scalajwt.core

import com.guizmaii.scalajwt.core.{InvalidToken, JwtToken}
import com.nimbusds.jwt.JWTClaimsSet

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet]
}
