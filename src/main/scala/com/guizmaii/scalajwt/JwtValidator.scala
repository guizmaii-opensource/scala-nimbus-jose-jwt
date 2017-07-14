package com.guizmaii.scalajwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier, DefaultJWTProcessor}

import scala.util.Try

final case class JwtToken(content: String) extends AnyVal

sealed abstract class ValidationError(message: String) extends BadJWTException(message)
case object EmptyJwtTokenContent                       extends ValidationError("Empty JWT token")
case object MissingExpirationClaim                     extends ValidationError("Missing `exp` claim")
case object InvalidTokenUseClaim                       extends ValidationError("Invalid `token_use` claim")
case object InvalidTokenIssuerClaim                    extends ValidationError("Invalid `iss` claim")
case object InvalidTokenSubject                        extends ValidationError("Invalid `sub` claim")

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[BadJWTException, (JwtToken, JWTClaimsSet)]
}

object ProvidedAdditionalChelcks {

  /**
    * Will ensure that the `exp` is present.
    * It'll not check its value nor the validity of its value.
    *
    * The DefaultJWTClaimsVerifier will check the token expiration vut only if `exp` claim is present.
    * We could need to require its presence.
    */
  val requireExpirationClaim: (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
      if (jwtClainSet.getExpirationTime == null)
        Some(MissingExpirationClaim)
      else
        None
    }

  /**
    * Will ensure that the `token_use` claim is equal to the passed String value.
    */
  val requireTokenUseClaim: (String) => (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
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
  val requiredIssuerClaim: (String) => (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
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
  val requiredNonEmptySubject: (JWTClaimsSet, SecurityContext) => Option[BadJWTException] =
    (jwtClainSet: JWTClaimsSet, _: SecurityContext) => {
      val userId: String = jwtClainSet.getSubject
      if (userId == null || userId.isEmpty)
        Some(InvalidTokenSubject)
      else
        None
    }

}

/**
  * A (fully?) configurable JwtValidator implementation.
  *
  * The Nimbus code come from this example:
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
  *
  * @param keySource JSON Web Key (JWK) source
  * @param maybeCtx Optional Security context. Default is `null` (no Security Context)
  * @param maybeJwsAlgorithm Optional JWSAlgorithm. Default is `RS256`
  * @param additionalChecks Optional list of additional checks that will be executed on the JWT token passed. Default is an empty List.
  */
final class ConfigurableJwtValidator(
    keySource: JWKSource[SecurityContext],
    maybeCtx: Option[SecurityContext] = None,
    maybeJwsAlgorithm: Option[JWSAlgorithm] = None,
    additionalChecks: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty
) extends JwtValidator {

  // Set up a JWT processor to parse the tokens and then check their signature
  // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
  private val jwtProcessor = new DefaultJWTProcessor[SecurityContext]
  // The expected JWS algorithm of the access tokens (agreed out-of-band)
  private val expectedJWSAlg = maybeJwsAlgorithm.getOrElse(JWSAlgorithm.RS256)
  // Configure the JWT processor with a key selector to feed matching public
  // RSA keys sourced from the JWK set URL
  private val keySelector = new JWSVerificationKeySelector[SecurityContext](expectedJWSAlg, keySource)
  jwtProcessor.setJWSKeySelector(keySelector)

  // Set the additional checks.
  //
  // Updated and adapted version of this example:
  //   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#claims-validator
  jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier[SecurityContext] {
    override def verify(claimsSet: JWTClaimsSet, context: SecurityContext): Unit = {
      super.verify(claimsSet, context)

      additionalChecks.toStream
        .map(f => f(claimsSet, context))
        .collect { case Some(e) => e }
        .foreach(e => throw e)
    }
  })

  private val ctx: SecurityContext = maybeCtx.orNull

  override def validate(jwtToken: JwtToken): Either[BadJWTException, (JwtToken, JWTClaimsSet)] = {
    val content: String = jwtToken.content
    if (content.isEmpty) Left(EmptyJwtTokenContent)
    else
      Try(jwtProcessor.process(content, ctx))
        .fold({ case e: BadJWTException => Left(e) }, (claimSet: JWTClaimsSet) => Right(jwtToken -> claimSet))
  }
}
