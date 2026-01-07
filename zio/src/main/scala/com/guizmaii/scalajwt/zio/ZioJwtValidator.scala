package com.guizmaii.scalajwt.zio

import com.guizmaii.scalajwt.core.{InvalidToken, JwtToken, SupportedJWSAlgorithm}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTProcessor, JWTClaimsSetVerifier}
import zio.*
import zio.http.Client
import zio.telemetry.opentelemetry.core.trace.Tracer

import scala.util.control.NonFatal

/**
 * ZIO-native JWT validator service.
 *
 * Validation never blocks the calling fiber - the JWKS is always available
 * in memory via the AtomicJWKSource, and signature verification is CPU-bound.
 */
trait ZioJwtValidator {

  /**
   * Validate a JWT token.
   *
   * This operation is non-blocking and safe to call from any fiber.
   * The JWKS is read from an in-memory cache (AtomicReference), and
   * signature verification is pure CPU work.
   */
  def validate(token: JwtToken): IO[InvalidToken, JWTClaimsSet]
}

object ZioJwtValidator {

  /**
   * Create a validator layer from a JwksManager.
   *
   * Includes automatic OpenTelemetry tracing for validation operations.
   *
   * @param claimsVerifier The claims verifier to use (validates iss, aud, exp, etc.)
   * @param algorithm The expected signing algorithm (default: RS256)
   */
  def live(
    claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
    algorithm: SupportedJWSAlgorithm = SupportedJWSAlgorithm.RS256
  ): ZLayer[JwksManager & Tracer, Nothing, ZioJwtValidator] =
    ZLayer {
      for {
        manager <- ZIO.service[JwksManager]
        tracer  <- ZIO.service[Tracer]
      } yield new ZioJwtValidatorLive(manager.jwkSource, claimsVerifier, algorithm, tracer)
    }

  /**
   * Convenience: Create a complete validator layer from claims verifier.
   *
   * This combines JwksManager.live and ZioJwtValidator.live into a single layer.
   * Requires JwksConfig and Tracer in the environment.
   */
  def configured(
    claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
    algorithm: SupportedJWSAlgorithm = SupportedJWSAlgorithm.RS256
  ): ZLayer[Client & JwksConfig & Tracer, JwksFetchError, JwksManager & ZioJwtValidator] =
    ZLayer.makeSome[Client & JwksConfig & Tracer, JwksManager & ZioJwtValidator](
      JwksManager.live,
      live(claimsVerifier, algorithm)
    )

}

private[scalajwt] final class ZioJwtValidatorLive(
  jwkSource: AtomicJWKSource,
  claimsVerifier: JWTClaimsSetVerifier[SecurityContext],
  algorithm: SupportedJWSAlgorithm,
  tracer: Tracer
) extends ZioJwtValidator {

  // Set up the JWT processor once - it's thread-safe and reusable
  private val jwtProcessor: DefaultJWTProcessor[SecurityContext] = {
    val processor   = new DefaultJWTProcessor[SecurityContext]
    val keySelector = new JWSVerificationKeySelector[SecurityContext](algorithm.nimbusRepresentation, jwkSource)
    processor.setJWSKeySelector(keySelector)
    processor.setJWTClaimsSetVerifier(claimsVerifier)
    processor
  }

  override def validate(token: JwtToken): IO[InvalidToken, JWTClaimsSet] =
    (
      if (token.isBlank) ZIO.fail(InvalidToken("Empty JWT token"))
      else
        ZIO.suspendSucceed {
          try Exit.succeed(jwtProcessor.process(token, null))
          catch {
            case e if NonFatal(e) => ZIO.fail(InvalidToken(e))
          }
        }
    ) @@ JwksMetrics.trackValidation @@ JwksTracing.trackValidation(tracer)
}
