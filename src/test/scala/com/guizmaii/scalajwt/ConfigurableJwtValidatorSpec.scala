package com.guizmaii.scalajwt

import java.security.{KeyPair, KeyPairGenerator}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

import com.guizmaii.scalajwt.implementations.ConfigurableJwtValidator
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, WordSpec}

/**
  * Some parts of these tests code is inspired and/or copy/paste from Nimbus tests code, here:
  *
  * https://bitbucket.org/connect2id/nimbus-jose-jwt/src/15adaae86cf7d8492ce02b02bfc07166f05c03d9/src/test/java/com/nimbusds/jwt/proc/DefaultJWTProcessorTest.java?at=master&fileviewer=file-view-default
  *
  * Thanks to them for their work.
  *
  */
class ConfigurableJwtValidatorSpec extends WordSpec with Matchers with PropertyChecks {

  import Generators._
  import com.guizmaii.scalajwt.utils.ProvidedValidations._

  "true" should { "be true" in { true shouldNot be(false) } }

  "#validate" should {
    val gen: KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
    gen.initialize(2048)
    val keyPair: KeyPair = gen.generateKeyPair()

    "when the JSON Web Token is an empty String" should {
      "returns Left(EmptyJwtTokenContent)" in {
        forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
          val token     = JwtToken(content = "")
          val validator = ConfigurableJwtValidator(jwkSource)
          validator.validate(token) shouldBe Left(EmptyJwtTokenContent)
        }
      }
    }

    "when the JWT is invalid" should {
      "returns Left(InvalidJwtToken)" in {
        forAll(jwkSourceGen(keyPair), nonEmptyStringGen) { (jwkSource: JWKSource[SecurityContext], randomString: String) =>
          val token     = JwtToken(content = randomString)
          val validator = ConfigurableJwtValidator(jwkSource)
          validator.validate(token) shouldBe Left(InvalidJwtToken)
        }
      }
    }

    "when the `exp` claim" should {
      "is not required but present" should {
        "but expired" should {
          "returns Left(BadJWTException: Expired JWT)" in {
            val yesterday = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))

            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(yesterday).build
            val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
            jwt.sign(new RSASSASigner(keyPair.getPrivate))
            val token = JwtToken(content = jwt.serialize())

            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val res = ConfigurableJwtValidator(jwkSource).validate(token)
              res.toString shouldBe Left(new BadJWTException("Expired JWT")).toString
            }
          }
        }
        "and valide" should {
          "returns Right(token -> claimSet)" in {
            val tomorrow = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))

            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(tomorrow).build
            val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
            jwt.sign(new RSASSASigner(keyPair.getPrivate))
            val token = JwtToken(content = jwt.serialize())

            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val res = ConfigurableJwtValidator(jwkSource).validate(token)
              res.right.map(_._1) shouldBe Right(token)
              res.right.map(_._2).toString shouldBe Right(claims).toString
            }
          }
        }
      }
      "is required" should {
        "but not present" should {
          "returns Left(MissingExpirationClaim)" in {
            val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build
            val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
            jwt.sign(new RSASSASigner(keyPair.getPrivate))
            val token = JwtToken(content = jwt.serialize())

            forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
              val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireExpirationClaim))
              val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource)

              correctlyConfiguredValidator.validate(token) shouldBe Left(MissingExpirationClaim)
              val res = nonConfiguredValidator.validate(token)
              res.right.map(_._1) shouldBe Right(token)
              // Without the `.toString` hack, we have this stupid error:
              //  `Right({"sub":"alice","iss":"https:\/\/openid.c2id.com"}) was not equal to Right({"sub":"alice","iss":"https:\/\/openid.c2id.com"})`
              // Equality on Claims should not be well defined.
              res.right.map(_._2).toString shouldBe Right(claims).toString
            }
          }
        }
        "and present" should {
          "but expired" should {
            "returns Left(BadJWTException: Expired JWT)" in {
              val yesterday = Date.from(Instant.now().minus(1, ChronoUnit.DAYS))

              val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(yesterday).build
              val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
              jwt.sign(new RSASSASigner(keyPair.getPrivate))
              val token = JwtToken(content = jwt.serialize())

              forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
                val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireExpirationClaim))

                val res = correctlyConfiguredValidator.validate(token)
                res.toString shouldBe Left(new BadJWTException("Expired JWT")).toString
              }
            }
          }
          "and valide" should {
            "returns Right(token -> claimSet)" in {
              val tomorrow = Date.from(Instant.now().plus(1, ChronoUnit.DAYS))

              val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").expirationTime(tomorrow).build
              val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
              jwt.sign(new RSASSASigner(keyPair.getPrivate))
              val token = JwtToken(content = jwt.serialize())

              forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
                val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireExpirationClaim))

                val res = correctlyConfiguredValidator.validate(token)
                res.right.map(_._1) shouldBe Right(token)
                res.right.map(_._2).toString shouldBe Right(claims).toString
              }
            }
          }
        }
      }
    }

    "when the `use` claim is required" should {
      "but not present" should {
        "returns Left(InvalidTokenUseClaim)" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build
          val jwt      = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireTokenUseClaim(tokenUse)))
            val nonConfiguredValidator = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenUseClaim)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "present but not the one expected" should {
        "returns Left(InvalidTokenUseClaim)" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").build
          val jwt      = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireTokenUseClaim(tokenUse + "s")))
            val nonConfiguredValidator = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenUseClaim)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "present and valide" should {
        "returns Right(token -> claimSet)" in {
          val tokenUse = "some random string"
          val claims   = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("alice").claim("token_use", tokenUse).build
          val jwt      = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, additionalValidations = List(requireTokenUseClaim(tokenUse)))
            val res = correctlyConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
    }

    "when the `iss` claim is required " should {
      "but not present" should {
        "returns Left(InvalidTokenIssuerClaim)" in {
          val issuer = "https://openid.c2id.com"
          val claims = new JWTClaimsSet.Builder().subject("alice").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredIssuerClaim(issuer)))
            val nonConfiguredValidator = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenIssuerClaim)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "and present but not the one expected" should {
        "returns Left(InvalidTokenIssuerClaim)" in {
          val issuer = "https://guizmaii.com"
          val claims = new JWTClaimsSet.Builder().issuer(issuer).subject("alice").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator =
              ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredIssuerClaim(issuer + "T")))
            val nonConfiguredValidator = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenIssuerClaim)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "present and valide" should {
        "returns Right(token -> claimSet)" in {
          val issuer = "https://guizmaii.com"
          val claims = new JWTClaimsSet.Builder().issuer(issuer).subject("alice").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val res = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredIssuerClaim(issuer))).validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
    }

    "when the `sub` claim is required" should {
      "but not present" should {
        "returns Left(InvalidTokenSubject)" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredNonEmptySubject))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenSubject)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            // Without the `.toString` hack, we have this stupid error:
            //  `Right({"sub":"alice","iss":"https:\/\/openid.c2id.com"}) was not equal to Right({"sub":"alice","iss":"https:\/\/openid.c2id.com"})`
            // Equality on Claims should not be well defined.
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "present but empty" should {
        "returns Left(InvalidTokenSubject)" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val correctlyConfiguredValidator = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredNonEmptySubject))
            val nonConfiguredValidator       = ConfigurableJwtValidator(jwkSource)

            correctlyConfiguredValidator.validate(token) shouldBe Left(InvalidTokenSubject)
            val res = nonConfiguredValidator.validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
      "present and valide" should {
        "returns Right(token -> claimSet)" in {
          val claims = new JWTClaimsSet.Builder().issuer("https://openid.c2id.com").subject("Jules").build
          val jwt    = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims)
          jwt.sign(new RSASSASigner(keyPair.getPrivate))
          val token = JwtToken(content = jwt.serialize())

          forAll(jwkSourceGen(keyPair)) { jwkSource: JWKSource[SecurityContext] =>
            val res = ConfigurableJwtValidator(jwkSource, additionalValidations = List(requiredNonEmptySubject)).validate(token)
            res.right.map(_._1) shouldBe Right(token)
            res.right.map(_._2).toString shouldBe Right(claims).toString
          }
        }
      }
    }
  }

}
