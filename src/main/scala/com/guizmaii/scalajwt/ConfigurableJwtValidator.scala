package com.guizmaii.scalajwt

import java.text.ParseException

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier, DefaultJWTProcessor}

import scala.util.{Failure, Success, Try}

object ConfigurableJwtValidator {
  def apply(
      keySource: JWKSource[SecurityContext],
      maybeCtx: Option[SecurityContext] = None,
      additionalChecks: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty
  ): ConfigurableJwtValidator = new ConfigurableJwtValidator(keySource, maybeCtx, additionalChecks)
}

/**
  * A (fully?) configurable JwtValidator implementation.
  *
  * The Nimbus code come from this example:
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
  *
  * @param keySource (Required) JSON Web Key (JWK) source.
  * @param maybeCtx (Optional) Security context. Default is `null` (no Security Context).
  * @param additionalChecks (Optional) List of additional checks that will be executed on the JWT token passed. Default is an empty List.
  */
final class ConfigurableJwtValidator(
    keySource: JWKSource[SecurityContext],
    maybeCtx: Option[SecurityContext] = None,
    additionalChecks: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty
) extends JwtValidator {

  // Set up a JWT processor to parse the tokens and then check their signature
  // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
  private val jwtProcessor = new DefaultJWTProcessor[SecurityContext]
  // The expected JWS algorithm of the access tokens (agreed out-of-band)
  private val expectedJWSAlg = JWSAlgorithm.RS256
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
      Try(jwtProcessor.process(content, ctx)) match {
        case Success(claimSet: JWTClaimsSet) => Right(jwtToken -> claimSet)
        case Failure(e: BadJWTException)     => Left(e)
        case Failure(_: ParseException)      => Left(InvalidJwtToken)
        case Failure(e: Exception)           => Left(UnknownException(e))
      }
  }
}
