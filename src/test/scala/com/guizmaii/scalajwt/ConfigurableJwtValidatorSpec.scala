package com.guizmaii.scalajwt

import cats.scalatest.EitherMatchers
import com.guizmaii.scalajwt.implementations.ConfigurableJwtValidator
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import org.scalacheck.Shrink
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import java.security.KeyPair

/** Some parts of these tests code is inspired and/or copy/paste from Nimbus tests code, here:
  *
  * https://bitbucket.org/connect2id/nimbus-jose-jwt/src/15adaae86cf7d8492ce02b02bfc07166f05c03d9/src/test/java/com/nimbusds/jwt/proc/DefaultJWTProcessorTest.java?at=master&fileviewer=file-view-default
  *
  * Thanks to them for their work.
  */
class ConfigurableJwtValidatorSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  implicit def noShrink[T]: Shrink[T] = Shrink.shrinkAny

  override implicit val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 100)

  import EitherMatchers._
  import Generators._
  import TestsUtils._
  import cats.syntax.either._

  val keyPair: KeyPair = generateKeyPair

  "true" - { "not be false" in { true shouldNot be(false) } }

  "#validate" - {

    "when the JSON Web Token is an empty String" - {
      "returns Left(EmptyJwtTokenContent)" in {
        forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
          val token     = JwtToken(content = "")
          val validator = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)
          val res       = validator.validate(token)
          res should be(left[InvalidToken]): Unit
          res.leftMap(_.getMessage) should beLeft("Empty JWT token")
        }
      }
    }

    "when the JWT is invalid" - {
      // generating keys takes time, so lowering the minSuccessful here
      implicit def generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 20)
      "returns Left(InvalidToken(Signed JWT rejected: Invalid signature))" in {
        forAll(jwkSourceGen(keyPair)) { (jwkSource: JWKSource[SecurityContext]) =>
          val otherKeyPair = generateKeyPair
          val token        = getToken(otherKeyPair, new JWTClaimsSet.Builder().build)
          val validator    = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)
          val res          = validator.validate(token)
          res should be(left[InvalidToken]): Unit
          res.leftMap(_.getMessage) should beLeft("Signed JWT rejected: Invalid signature")
        }
      }
    }

    "when the JWT was signed with another key" - {
      "returns Left(InvalidJwtToken)" in {
        forAll(jwkSourceGen(keyPair)) { (jwkSource: JWKSource[SecurityContext]) =>
          val token     = JwtToken(content = "this is not a valid jwt token")
          val validator = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)
          val res       = validator.validate(token)
          res should be(left[InvalidToken]): Unit
          res.leftMap(_.getMessage) should beLeft("Invalid JWT serialization: Missing dot delimiter(s)")
        }
      }
    }

    "when the `exp` claim" - {
      "is not required but present" - {
        "but expired" - {
          "returns Left(InvalidToken: Expired JWT)" in {
            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(yesterday).build
            val token  = getToken(keyPair, claims)
            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val res = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier).validate(token)
              res should be(left[InvalidToken]): Unit
              res.leftMap(_.getMessage) should beLeft("Expired JWT")
            }
          }
        }
        "and valid" - {
          "returns Right(token -> claimSet)" in {
            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(tomorrow).build
            val token  = getToken(keyPair, claims)

            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val res = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier).validate(token)
              res.map(_.toString) should beRight(claims.toString)
            }
          }
        }
      }
      "is required" - {
        "but not present" - {
          "returns Left(InvalidToken: JWT missing required claims: [exp])" in {
            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build()
            val token  = getToken(keyPair, claims)

            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireExpClaimVerifier)
              val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

              val res0 = correctlyConfiguredValidator.validate(token)
              res0 should be(left[InvalidToken]): Unit
              res0.leftMap(_.getMessage) should beLeft("JWT missing required claims: [exp]"): Unit

              val res = nonConfiguredValidator.validate(token)
              res.map(_.toString) should beRight(claims.toString)
            }
          }
        }
        "and present" - {
          "but expired" - {
            "returns Left(InvalidToken: Expired JWT)" in {
              val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(yesterday).build
              val token  = getToken(keyPair, claims)

              forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
                val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireExpClaimVerifier)

                val res = correctlyConfiguredValidator.validate(token)
                res should be(left[InvalidToken]): Unit
                res.leftMap(_.getMessage) should beLeft("Expired JWT")
              }
            }
          }
          "and valide" - {
            "returns Right(token -> claimSet)" in {
              val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(tomorrow).build
              val token  = getToken(keyPair, claims)

              forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
                val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireExpClaimVerifier)

                val res = correctlyConfiguredValidator.validate(token)
                res.map(_.toString) should beRight(claims.toString)
              }
            }
          }
        }
      }
    }

    "when the `use` claim is required" - {
      "but not present" - {
        "returns Left(InvalidToken: JWT missing required claims: [token_use])" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build
          val token    = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireTokenUseClaimVerifier(tokenUse))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT missing required claims: [token_use]"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "present but not the one expected" - {
        "returns Left(InvalidToken: JWT missing required claims: [token_use])" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build
          val token    = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireTokenUseClaimVerifier(tokenUse + "s"))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT missing required claims: [token_use]"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "present and valid" - {
        "returns Right(token -> claimSet)" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").claim("token_use", tokenUse).build
          val token    = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireTokenUseClaimVerifier(tokenUse))
            val res                          = correctlyConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
    }

    "when the `iss` claim is required " - {
      "but not present" - {
        "returns Left(InvalidToken: JWT missing required claims: [iss])" in {
          val issuer = "https://openid.c2id.com"
          val claims = new JWTClaimsSet.Builder().subject("alice").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireIssuerClaimVerifier(issuer))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT missing required claims: [iss]"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "and present but not the one expected" - {
        "returns Left(InvalidToken: JWT iss claim has value <value>, must be <expected value>)" in {
          val issuer = "https://guizmaii.com"
          val claims = new JWTClaimsSet.Builder().issuer(issuer).subject("alice").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireIssuerClaimVerifier(issuer + "T"))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft(s"""JWT iss claim has value $issuer, must be ${issuer + "T"}"""): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "present and valid" - {
        "returns Right(token -> claimSet)" in {
          val issuer = "https://guizmaii.com"
          val claims = new JWTClaimsSet.Builder().issuer(issuer).subject("alice").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val res = ConfigurableJwtValidator(jwkSource, requireIssuerClaimVerifier(issuer)).validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
    }

    "when the `sub` claim is required" - {
      "when not present" - {
        "returns Left(InvalidToken: JWT missing required claims: [sub])" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair), arbNonEmptyString.arbitrary) { (jwkSource: JWKSource[SecurityContext], expectedSub) =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireSubClaimVerifier(expectedSub))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT missing required claims: [sub]"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "when present but empty" - {
        s"returns Left(JWT sub claim has value '', must be `expectedSub`)" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair), arbNonEmptyString.arbitrary) { (jwkSource: JWKSource[SecurityContext], expectedSub) =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireSubClaimVerifier(expectedSub))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft(s"""JWT sub claim has value , must be $expectedSub"""): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "when present and valid" - {
        "returns Right(token -> claimSet)" in {
          forAll(jwkSourceGen(keyPair), arbNonEmptyString.arbitrary) { (jwkSource: JWKSource[SecurityContext], expectedSub) =>
            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject(expectedSub).build
            val token  = getToken(keyPair, claims)

            val res = ConfigurableJwtValidator(jwkSource, requireSubClaimVerifier(expectedSub)).validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
    }

    "when the `aud` claim is required" - {
      "when not present" - {
        "returns Left(InvalidToken: JWT missing required audience)" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").build
          val token  = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, requireAudienceClaimVerifier("https://valid_audience.com"))
            val nonConfiguredValidator = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT missing required audience"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "when present and invalid" - {
        "returns Left(InvalidToken: JWT audience rejected: [<rejected value>])" in {
          val claims =
            new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").audience("valid_audience_1").audience("valid_audience_2").build
          val token = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, requireAudienceClaimVerifier("invalid_audience"))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource, minimalClaimsVerifier)

            val res0 = correctlyConfiguredValidator.validate(token)
            res0 should be(left[InvalidToken]): Unit
            res0.leftMap(_.getMessage) should beLeft("JWT audience rejected: [valid_audience_2]"): Unit

            val res = nonConfiguredValidator.validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
      "when present and valid" - {
        "returns Right(token -> claimSet)" in {
          val claims =
            new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").audience("valid_audience_1").audience("valid_audience_2").build
          val token = getToken(keyPair, claims)

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val res =
              ConfigurableJwtValidator(jwkSource, requireAudienceClaimVerifier("valid_audience_2")).validate(token)
            res.map(_.toString) should beRight(claims.toString)
          }
        }
      }
    }
  }

}
