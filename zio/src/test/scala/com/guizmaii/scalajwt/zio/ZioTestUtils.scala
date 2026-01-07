package com.guizmaii.scalajwt.zio

import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.core.trace.Tracer

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.{Date, UUID}

private[scalajwt] object ZioTestUtils {
  import scala.jdk.CollectionConverters.*

  def tomorrow: Date  = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))
  def yesterday: Date = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))

  def generateKeyPair(size: Int = 2048): KeyPair = {
    val gen: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    gen.initialize(size)
    gen.generateKeyPair()
  }

  def generateJwkSet(keyPair: KeyPair): JWKSet = {
    val rsaKey = new RSAKey.Builder(keyPair.getPublic.asInstanceOf[RSAPublicKey])
      .privateKey(keyPair.getPrivate.asInstanceOf[RSAPrivateKey])
      .keyID(UUID.randomUUID.toString)
      .build()
    new JWKSet(rsaKey)
  }

  def generateToken(keyPair: KeyPair, claims: JWTClaimsSet): JwtToken = {
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
    jwt.sign(new RSASSASigner(keyPair.getPrivate))
    JwtToken(content = jwt.serialize())
  }

  def validClaims(
    issuer: String = "https://test-issuer.com",
    subject: String = "test-subject",
    audience: String = "test-audience"
  ): JWTClaimsSet =
    new JWTClaimsSet.Builder()
      .issuer(issuer)
      .subject(subject)
      .audience(audience)
      .expirationTime(tomorrow)
      .build()

  def expiredClaims(
    issuer: String = "https://test-issuer.com",
    subject: String = "test-subject"
  ): JWTClaimsSet =
    new JWTClaimsSet.Builder()
      .issuer(issuer)
      .subject(subject)
      .expirationTime(yesterday)
      .build()

  def minimalClaimsVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](null, null)

  def requireExpClaimVerifier: DefaultJWTClaimsVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](null, Set("exp").asJava)

  def claimsVerifier(
    issuer: String,
    audience: String
  ): DefaultJWTClaimsVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](
      Set(audience).asJava,
      new JWTClaimsSet.Builder().issuer(issuer).build(),
      Set("exp", "sub").asJava,
      null
    )

  /** Creates a mock JWKS HTTP app that serves the provided JWKSet */
  def mockJwksServer(jwkSet: JWKSet): Routes[Any, Nothing] =
    Routes(
      Method.GET / ".well-known" / "jwks.json" -> handler { (_: Request) =>
        Response.json(jwkSet.toString)
      }
    )

  /** No-op tracer layer for testing */
  val noopTracerLayer: TaskLayer[Tracer] =
    OpenTelemetry.noop() >>> OpenTelemetry.tracer("test")
}
