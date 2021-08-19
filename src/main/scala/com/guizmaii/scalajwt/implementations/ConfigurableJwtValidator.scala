package com.guizmaii.scalajwt.implementations

import com.guizmaii.scalajwt._
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTProcessor, JWTClaimsSetVerifier}

import java.text.ParseException
import scala.util.control.NonFatal

object ConfigurableJwtValidator {
  def apply(
      keySource: JWKSource[SecurityContext],
      claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
      algorithm: SupportedJWSAlgorithm = RS256,
      maybeCtx: Option[SecurityContext] = None
  ): ConfigurableJwtValidator =
    new ConfigurableJwtValidator(
      keySource = keySource,
      claimsVerifier = claimsVerifier,
      algorithm = algorithm,
      maybeCtx = maybeCtx
    )
}

/** A (fully?) configurable JwtValidator implementation.
  *
  * The Nimbus code come from this example: https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens
  *
  * @param keySource
  *   (Required) JSON Web Key (JWK) source.
  * @param claimsVerifier
  *   (Required) The claims validation rules. See
  *   https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#claims
  * @param algorithm
  *   (Optional) Algorithm used to encrypt the token. Default is `RS256`.
  * @param maybeCtx
  *   (Optional) Security context. Default is `null` (no Security Context).
  */
final class ConfigurableJwtValidator private[scalajwt] (
    keySource: JWKSource[SecurityContext],
    claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
    algorithm: SupportedJWSAlgorithm = RS256,
    maybeCtx: Option[SecurityContext] = None
) extends JwtValidator {

  // Set up a JWT processor to parse the tokens and then check their signature
  // and validity time window (bounded by the "iat", "nbf" and "exp" claims)
  private val jwtProcessor = new DefaultJWTProcessor[SecurityContext]
  // Configure the JWT processor with a key selector to feed matching public
  // RSA keys sourced from the JWK set URL
  private val keySelector = new JWSVerificationKeySelector[SecurityContext](algorithm.nimbusRepresentation, keySource)
  jwtProcessor.setJWSKeySelector(keySelector)
  jwtProcessor.setJWTClaimsSetVerifier(claimsVerifier)

  private val ctx: SecurityContext = maybeCtx.orNull

  override def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet] = {
    val content: String = jwtToken.content
    if (content.isEmpty) Left(InvalidToken(new RuntimeException("Empty JWT token")))
    else
      try Right(jwtProcessor.process(content, ctx))
      catch {
        case e: BadJWTException => Left(InvalidToken(e))
        case e: ParseException  => Left(InvalidToken(e))
        case NonFatal(e)        => Left(InvalidToken(e))
      }
  }
}
