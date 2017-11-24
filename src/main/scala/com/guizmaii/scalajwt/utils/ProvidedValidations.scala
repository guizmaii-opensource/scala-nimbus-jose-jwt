package com.guizmaii.scalajwt.utils

import com.guizmaii.scalajwt.{InvalidTokenIssuerClaim, InvalidTokenSubject, InvalidTokenUseClaim, MissingExpirationClaim}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException

object ProvidedValidations {

  /**
    * Will ensure that the `exp` is present.
    * It'll not check its value nor the validity of its value.
    *
    * The DefaultJWTClaimsVerifier will check the token expiration but only if `exp` claim is present.
    * We could need to require its presence.
    */
  final val requireExpirationClaim: (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
      if (jwtClainSet.getExpirationTime == null)
        Some(MissingExpirationClaim)
      else
        None
    }

  /**
    * Will ensure that the `token_use` claim is equal to the passed String value.
    */
  final val requireTokenUseClaim: (String) => (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (requiredTokenUseValue: String) =>
      (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
        val tokenUse: String = jwtClainSet.getStringClaim("token_use")
        if (requiredTokenUseValue != tokenUse)
          Some(InvalidTokenUseClaim)
        else
          None
    }

  /**
    * Will ensure that the `iss` claim contains the passed String value.
    */
  final val requiredIssuerClaim: (String) => (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (requiredIssuerValue: String) =>
      (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
        val iss: String = jwtClainSet.getIssuer
        if (iss == null || !iss.contains(requiredIssuerValue))
          Some(InvalidTokenIssuerClaim)
        else
          None
    }

  /**
    * Will ensure that the `sub` claim is present.
    */
  final val requiredNonEmptySubject: (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
      val userId: String = jwtClainSet.getSubject
      if (userId == null || userId.isEmpty)
        Some(InvalidTokenSubject)
      else
        None
    }

}
