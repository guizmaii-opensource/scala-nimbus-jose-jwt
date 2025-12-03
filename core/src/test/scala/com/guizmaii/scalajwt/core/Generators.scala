package com.guizmaii.scalajwt.core

import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.source.{ImmutableJWKSet, JWKSource}
import com.nimbusds.jose.jwk.{JWK, JWKSet, RSAKey}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.scalacheck.{Arbitrary, Gen}

import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.{KeyPair, KeyPairGenerator}
import java.util.UUID

object Generators {

  def getToken(keyPair: KeyPair, claims: JWTClaimsSet): JwtToken = {
    val jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
    jwt.sign(new RSASSASigner(keyPair.getPrivate))
    JwtToken(content = jwt.serialize())
  }

  implicit final val arbNonEmptyString: Arbitrary[String] = Arbitrary(Gen.alphaStr.filter(_.trim.nonEmpty))

  implicit final val arbKeyPair: Arbitrary[KeyPair] =
    Arbitrary {
      for {
        size <- Gen.oneOf(2048, 4096)
      } yield {
        val gen: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
        gen.initialize(size)
        gen.generateKeyPair()
      }
    }

  def jwkGen(keyPair: KeyPair): Gen[JWK] =
    Gen.const {
      new RSAKey.Builder(keyPair.getPublic.asInstanceOf[RSAPublicKey])
        .privateKey(keyPair.getPrivate.asInstanceOf[RSAPrivateKey])
        .keyID(UUID.randomUUID.toString)
        .build
    }

  def jwkSetGen(keyPair: KeyPair): Gen[JWKSet] =
    for {
      jwk1 <- jwkGen(keyPair)
      jwk2 <- jwkGen(keyPair)
    } yield new JWKSet(java.util.Arrays.asList(jwk1, jwk2))

  def jwkSourceGen(keyPair: KeyPair): Gen[JWKSource[SecurityContext]] =
    for { jwkSet <- jwkSetGen(keyPair) } yield new ImmutableJWKSet(jwkSet)

}
