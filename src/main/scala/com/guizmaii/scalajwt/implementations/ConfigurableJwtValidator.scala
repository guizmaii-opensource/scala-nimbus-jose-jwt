package com.guizmaii.scalajwt.implementations

import java.text.ParseException

import com.guizmaii.scalajwt._
import com.guizmaii.scalajwt.utils.SupportedJWSAlgorithms._
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier, DefaultJWTProcessor}

import scala.collection.compat.immutable._

object ConfigurableJwtValidator {
  def apply(
      keySource: JWKSource[SecurityContext],
      algorithm: SupportedJWSAlgorithm = RS256,
      maybeCtx: Option[SecurityContext] = None,
      additionalValidations: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty
  ): ConfigurableJwtValidator = new ConfigurableJwtValidator(keySource, algorithm, maybeCtx, additionalValidations)
}

/** A (fully?) configurable JwtValidator implementation.
  *
  * The Nimbus code come from this example:
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
  *
  * @param keySource (Required) JSON Web Key (JWK) source.
  * @param maybeCtx (Optional) Security context. Default is `null` (no Security Context).
  * @param additionalValidations (Optional) List of additional validations that will be executed on the JWT token. Default is an empty List.
  */
final class ConfigurableJwtValidator(
    keySource: JWKSource[SecurityContext],
    algorithm: SupportedJWSAlgorithm = RS256,
    maybeCtx: Option[SecurityContext] = None,
    additionalValidations: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty
) extends JwtValidator {

  // Set up a JWT processor to parse the tokens and then check their signature
  // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
  private val jwtProcessor = new DefaultJWTProcessor[SecurityContext]
  // Configure the JWT processor with a key selector to feed matching public
  // RSA keys sourced from the JWK set URL
  private val keySelector = new JWSVerificationKeySelector[SecurityContext](algorithm.nimbusRepresentation, keySource)
  jwtProcessor.setJWSKeySelector(keySelector)

  // Set the additional validations.
  //
  // Updated and adapted version of this example:
  //   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#claims-validator
  jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier[SecurityContext] {
    override def verify(claimsSet: JWTClaimsSet, context: SecurityContext): Unit = {
      super.verify(claimsSet, context)

      additionalValidations
        .to(LazyList)
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
      try {
        val claimsSet = jwtProcessor.process(content, ctx)
        Right(jwtToken -> claimsSet)
      } catch {
        case e: BadJWTException => Left(e)
        case _: ParseException  => Left(InvalidJwtToken)
        case e: Exception       => Left(UnknownException(e))
      }
  }
}
