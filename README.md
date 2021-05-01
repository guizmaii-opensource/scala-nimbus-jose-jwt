# scala-nimbus-jose-jwt

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.guizmaii/scala-nimbus-jose-jwt/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.guizmaii/scala-nimbus-jose-jwt)
[![CI](https://github.com/guizmaii/scala-nimbus-jose-jwt/actions/workflows/CI.yaml/badge.svg)](https://github.com/guizmaii/scala-nimbus-jose-jwt/actions/workflows/CI.yaml)
[![codecov](https://codecov.io/gh/guizmaii/scala-nimbus-jose-jwt/branch/master/graph/badge.svg)](https://codecov.io/gh/guizmaii/scala-nimbus-jose-jwt)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)

**Small, simple and opinionated JWT token validator for Scala.**

## Goal

**Provide a very simple API to help people do JWT token validation correctly.**

This project uses `Nimbus JOSE + JWT` (https://connect2id.com/products/nimbus-jose-jwt) to validate JWT tokens.
The aim of this project is not, and will never be, to provide a Scala interface to `Nimbus JOSE + JWT`.

I chose `Nimbus JOSE + JWT` because it seems to be audited and battle-tested.

The code size is small in order to be as readable as possible, so as free of bugs as possible.

## Setup

```scala
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt" % "2.0.0-RC1"
```

## API

The API is very simple:

```scala
import com.nimbusds.jwt.JWTClaimsSet

final case class JwtToken(content: String)
final case class InvalidToken(cause: Throwable) extends RuntimeException(cause.getMessage, cause)

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet]
}
```

## Available API implementations

### 1. ConfigurableJwtValidator

The more flexible implementation of the `JwtValidator` interface.

It only requires a `JWKSource` instance and a `JWTClaimsSetVerifier[SecurityContext]` instance.    

For more information on the different `JWKSource` implementations Nimbus provides, look at the classes in the `com.nimbusds.jose.jwk.source` package here: https://www.javadoc.io/doc/com.nimbusds/nimbus-jose-jwt    

For more information on how to create a `JWTClaimsSetVerifier[SecurityContext]`, which is the interface provided by `nimbus-jose-jwt` to
declare your claims validation rules, see [validating-jwt-access-tokens#claims](https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens#claims)

Example of use:
```scala
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.guizmaii.scalajwt.implementations.ConfigurableJwtValidator

import java.net.URL
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier


val jwtValidator: JwtValidator = {
  val jwkSource: JWKSource[SecurityContext] = 
    new RemoteJWKSet(new URL(s"https://<your.jwks.prodvider.example.com>/.well-known/jwks.json"))

  val claimsSetVerifier: JWTClaimsSetVerifier[SecurityContext] =
    new DefaultJWTClaimsVerifier[SecurityContext](
      Set("...").asJava,
      new JWTClaimsSet.Builder().issuer("...").build(),
      Set("exp").asJava,
      null
    )
  
  ConfigurableJwtValidator(keySource = jwkSource, claimsVerifier = claimsSetVerifier)
}

val token: JwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

For more information on JWKs, you can read:   
  - Auth0 doc: https://auth0.com/docs/jwks    
  - Nimbus doc: https://connect2id.com/products/server/docs/api/jwk-set       
  - AWS Cognito doc: https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-with-identity-providers.html#amazon-cognito-identity-user-pools-using-id-and-access-tokens-in-web-api

Other constructor parameters are:

  - `algorithm: SupportedJWSAlgorithm = RS256`   
    (Optional) The algorithm used to decode tokens.   
    Default is `RS256`.

  - `maybeCtx: Option[SecurityContext] = None`   
    (Optional) Security context.    
    Default is `null` (no Security Context).
    
### 2. AWS Cognito JWT Validator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `AwsCognitoJwtValidator.scala` file.      
It follows the AWS documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.guizmaii.scalajwt.implementations.{AwsCognitoJwtValidator, CognitoUserPoolId, S3Region}

import com.nimbusds.jwt.JWTClaimsSet

val awsCognitoJwtValidator: JwtValidator = {
  val s3Region          = S3Region(value = "eu-west-1")
  val cognitoUserPoolId = CognitoUserPoolId(value = "...")
  
  AwsCognitoJwtValidator(s3Region, cognitoUserPoolId)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

An additional constructor is provided.    
This second constructor allows you to add more contraints and/or to replace the default `JWTClaimsSetVerifier` used by the implementation:

```scala
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.guizmaii.scalajwt.implementations.{AwsCognitoJwtValidator, CognitoUserPoolId, S3Region}

import com.nimbusds.jwt.JWTClaimsSet

val awsCognitoJwtValidator: JwtValidator = {
  val s3Region          = S3Region(value = "eu-west-1")
  val cognitoUserPoolId = CognitoUserPoolId(value = "...")
  
  def customiseDefaultJWTClaimsSetVerifier(default: DefaultJWTClaimsVerifier[SecurityContext]): JWTClaimsSetVerifier[SecurityContext] = ??? // To implement
  
  AwsCognitoJwtValidator(s3Region, cognitoUserPoolId, customiseDefaultJWTClaimsSetVerifier)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

### 3. Auth0 JWT Validator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `Auth0JwtValidator.scala` file.      
It follows the Auth0 documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.guizmaii.scalajwt.implementations.{Auth0Audience, Auth0Domain, Auth0JwtValidator}

import com.nimbusds.jwt.JWTClaimsSet

val auth0JwtValidator: JwtValidator = {
  val auth0Domain   = Auth0Domain(value = "...")
  val auth0Audience = Auth0Audience(value = "...")
  
  Auth0JwtValidator(auth0Domain, auth0Audience)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

An additional constructor is provided.    
This second constructor allows you to add more contraints and/or to replace the default `JWTClaimsSetVerifier` used by the implementation:

```scala
import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.guizmaii.scalajwt.implementations.{Auth0Audience, Auth0Domain, Auth0JwtValidator}

import com.nimbusds.jwt.JWTClaimsSet

val auth0JwtValidator: JwtValidator = {
  val auth0Domain   = Auth0Domain(value = "...")
  val auth0Audience = Auth0Audience(value = "...")

  def customiseDefaultJWTClaimsSetVerifier(default: DefaultJWTClaimsVerifier[SecurityContext]): JWTClaimsSetVerifier[SecurityContext] = ??? // To implement

  Auth0JwtValidator(auth0Domain, auth0Audience, customiseDefaultJWTClaimsSetVerifier)
}

val jwtToken = JwtToken(content = "...")
val result: Either[InvalidToken, JWTClaimsSet] = jwtValidator.validate(token)
```

## Migrations

### Migrating from v1.x.x to v2.x.x

Be sure to read the CHANGELOG to understand how to migrate to v2.
