package com.guizmaii.scalajwt

import cats.scalatest.EitherMatchers
import com.guizmaii.scalajwt.implementations.Auth0JwtValidator.defaultAuth0ClaimSetVerifier
import com.guizmaii.scalajwt.implementations.{Auth0Audience, Auth0Domain, Auth0JwtValidator}
import com.nimbusds.jwt.JWTClaimsSet
import org.scalacheck.{Gen, Shrink}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.security.KeyPair
import java.util.Date

class Auth0JwtValidatorSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  import EitherMatchers._
  import Generators._
  import TestsUtils._
  import cats.syntax.either._

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  override implicit val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 100)

  val genAuth0Domain: Gen[Auth0Domain]     = arbNonEmptyString.arbitrary.map(Auth0Domain)
  val genAuth0Audience: Gen[Auth0Audience] = arbNonEmptyString.arbitrary.map(Auth0Audience)

  def allClaims(domain: Auth0Domain, audience: Auth0Audience, expiration: Date): JWTClaimsSet =
    new JWTClaimsSet.Builder()
      .audience(audience.value)
      .issuer(s"https://${domain.value}/")
      .expirationTime(expiration)
      .build()

  val keyPair: KeyPair = generateKeyPair

  "true" - { "not be false" in { true shouldNot be(false) } }

  "Auth0JwtValidator" - {
    "validates the audience, the issuer and the token expiration" - {
      "when all claims are fine" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val allClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer(s"https://${domain.value}/")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, allClaims)

        val result = service.validate(token)
        result.map(_.toString) should beRight(allClaims.toString)
      }
    }

    "when the audience is wrong" - {
      "when the claim is absent" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val claims =
          new JWTClaimsSet.Builder()
            .issuer(s"https://${domain.value}/")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, claims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("JWT missing required audience")
      }
      "when the claim value is empty" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val claims =
          new JWTClaimsSet.Builder()
            .audience("")
            .issuer(s"https://${domain.value}/")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, claims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("JWT audience rejected: []")
      }
      "when the claim value is wrong" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val claims =
          new JWTClaimsSet.Builder()
            .audience("this is not the expected value")
            .issuer(s"https://${domain.value}/")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, claims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("JWT audience rejected: [this is not the expected value]")
      }
    }

    "when the issuer is wrong" - {
      "when the claim is absent" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val claims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, claims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("JWT missing required claims: [iss]")
      }
      "when the claim value is empty" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val emptyIssuerClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer("")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, emptyIssuerClaims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft(s"""JWT iss claim has value , must be https://${domain.value}/""")
      }
      "when the claim value is wrong" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val wrongIssuerClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer("this is not the expected value")
            .expirationTime(tomorrow)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, wrongIssuerClaims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft(
          s"""JWT iss claim has value this is not the expected value, must be https://${domain.value}/"""
        )
      }
    }

    "when the expiration is wrong" - {
      "when the claim is absent" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val noExpirationClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer(s"https://${domain.value}/")
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, noExpirationClaims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("JWT missing required claims: [exp]")
      }
      "when the claim value is empty" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val emptyExpirationClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer(s"https://${domain.value}/")
            .claim("exp", "")
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, emptyExpirationClaims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("Unexpected type of JSON object member with key exp")
      }
      "when the claim value is wrong" in forAll(jwkSourceGen(keyPair), genAuth0Domain, genAuth0Audience) { (source, domain, audience) =>
        val expiredClaims =
          new JWTClaimsSet.Builder()
            .audience(audience.value)
            .issuer(s"https://${domain.value}/")
            .expirationTime(yesterday)
            .build()

        val service = new Auth0JwtValidator(source, defaultAuth0ClaimSetVerifier(domain, audience))
        val token   = getToken(keyPair, expiredClaims)

        val result = service.validate(token)
        result should be(left[InvalidToken])
        result.leftMap(_.getMessage) should beLeft("Expired JWT")
      }
    }
  }

}
