package com.guizmaii.scalajwt.core

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTClaimsVerifier, JWTClaimsSetVerifier}

import java.security.{KeyPair, KeyPairGenerator}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import scala.util.Random

object TestsUtils {
  import scala.jdk.CollectionConverters._

  def tomorrow: Date  = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))
  def yesterday: Date = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))

  def generateKeyPair: KeyPair = {
    val gen: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    gen.initialize(Random.shuffle(List(2048, 4096)).head) // Poor man's PBT
    gen.generateKeyPair()
  }

  val minimalClaimsVerifier: JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](null, null)

  val requireExpClaimVerifier: JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](null, Set("exp").asJava)

  def requireSubClaimVerifier(expectedValue: String): JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](new JWTClaimsSet.Builder().subject(expectedValue).build(), null)

  def requireTokenUseClaimVerifier(expectedValue: String): JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](new JWTClaimsSet.Builder().claim("token_use", expectedValue).build(), null)

  def requireIssuerClaimVerifier(expectedValue: String): JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](new JWTClaimsSet.Builder().issuer(expectedValue).build(), null)

  def requireAudienceClaimVerifier(expectedValue: String): JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](expectedValue, null, null)
}
