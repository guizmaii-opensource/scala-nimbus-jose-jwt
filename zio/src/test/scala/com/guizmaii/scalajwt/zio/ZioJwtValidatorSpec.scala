package com.guizmaii.scalajwt.zio

import com.guizmaii.scalajwt.zio.ZioTestUtils.*
import zio.*
import zio.test.*
import zio.telemetry.opentelemetry.core.trace.Tracer

import java.util.concurrent.atomic.AtomicReference

object ZioJwtValidatorSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ZioJwtValidator")(
      suite("::validate")(
        test("should successfully validate a valid token") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)
          val issuer  = "https://test-issuer.com"
          val subject = "test-subject"
          val claims  = validClaims(issuer = issuer, subject = subject)
          val token   = generateToken(keyPair, claims)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, minimalClaimsVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(token)
          } yield assertTrue(
            result.getSubject == subject,
            result.getIssuer == issuer
          )
        },
        test("should reject an expired token") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)
          val claims  = expiredClaims()
          val token   = generateToken(keyPair, claims)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, requireExpClaimVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(token).either
          } yield assertTrue(
            result.is(_.left).getMessage.contains("Expired")
          )
        },
        test("should reject an empty token") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, minimalClaimsVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(JwtToken("")).either
          } yield assertTrue(
            result.is(_.left).getMessage == "Empty JWT token"
          )
        },
        test("should reject a blank token") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, minimalClaimsVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(JwtToken("   ")).either
          } yield assertTrue(
            result.is(_.left).getMessage == "Empty JWT token"
          )
        },
        test("should reject a malformed token") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, minimalClaimsVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(JwtToken("not.a.valid.jwt")).either
          } yield assertTrue(
            result.is(_.left).getMessage == "Invalid unsecured/JWS/JWE header: Invalid JSON object"
          )
        },
        test("should reject a token signed with a different key") {
          val keyPair1 = generateKeyPair()
          val keyPair2 = generateKeyPair()
          val jwkSet   = generateJwkSet(keyPair1)        // JWKSet has keyPair1's public key
          val claims   = validClaims()
          val token    = generateToken(keyPair2, claims) // Token signed with keyPair2

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, minimalClaimsVerifier, SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(token).either
          } yield assertTrue(
            result.is(_.left).getMessage.contains("Signed JWT rejected")
          )
        },
        test("should reject a token with wrong issuer when verifier requires it") {
          val keyPair        = generateKeyPair()
          val jwkSet         = generateJwkSet(keyPair)
          val wrongIssuer    = "https://wrong-issuer.com"
          val expectedIssuer = "https://expected-issuer.com"
          val audience       = "test-audience"
          val claims         = validClaims(issuer = wrongIssuer, audience = audience)
          val token          = generateToken(keyPair, claims)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, claimsVerifier(expectedIssuer, audience), SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(token).either
          } yield assertTrue(
            result.is(_.left).getMessage == "JWT iss claim value rejected"
          )
        },
        test("should reject a token with wrong audience when verifier requires it") {
          val keyPair          = generateKeyPair()
          val jwkSet           = generateJwkSet(keyPair)
          val issuer           = "https://issuer.com"
          val wrongAudience    = "wrong-audience"
          val expectedAudience = "expected-audience"
          val claims           = validClaims(issuer = issuer, audience = wrongAudience)
          val token            = generateToken(keyPair, claims)

          val jwksRef   = new AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(jwksRef)

          for {
            tracer   <- ZIO.service[Tracer]
            validator = new ZioJwtValidatorLive(jwkSource, claimsVerifier(issuer, expectedAudience), SupportedJWSAlgorithm.RS256, tracer)
            result   <- validator.validate(token).either
          } yield assertTrue(
            result.is(_.left).getMessage == "JWT aud claim rejected"
          )
        }
      )
    ).provide(noopTracerLayer)
}
