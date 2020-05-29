package com.guizmaii.scalajwt

import java.security.KeyPair

import com.nimbusds.jose.jwk.source.{ImmutableJWKSet, JWKSource}
import com.nimbusds.jose.proc.SecurityContext
import org.scalacheck.Gen

import com.nimbusds.jose.jwk.{JWK, JWKSet, RSAKey}
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

object Generators {

  val nonEmptyStringGen: Gen[String] = Gen.alphaStr.filter(_.trim.length > 0)

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
