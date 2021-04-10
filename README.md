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
libraryDependencies += "com.guizmaii" %% "scala-nimbus-jose-jwt" % "1.0.2"
```

## API

The API is very simple:

```scala
final case class JwtToken(content: String) extends AnyVal

trait JwtValidator {
  def validate(jwtToken: JwtToken): Either[BadJWTException, (JwtToken, JWTClaimsSet)]
}
```

## Available API implementations

### 1. ConfigurableJwtValidator

The more flexible implementation of the `JwtValidator` interface.

It only requires a `JWKSource` instance.    
For more information on the different `JWKSource` implementations Nimbus provides, look at the classes in the `com.nimbusds.jose.jwk.source` package here: https://www.javadoc.io/doc/com.nimbusds/nimbus-jose-jwt

Example of use:
```scala
import java.net.URL

import com.guizmaii.scalajwt.JwtToken
import com.guizmaii.scalajwt.implementations.ConfigurableJwtValidator
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException

val token: JwtToken = JwtToken(content = "...")

val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(
    new URL(s"https://your.jwks.prodvider.example.com/.well-known/jwks.json"))
    
val result: Either[BadJWTException, (JwtToken, JWTClaimsSet)] = ConfigurableJwtValidator(jwkSet).validate(token)
```

For more information on JWKs, you could read:   
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
    
  - `additionalValidations: List[(JWTClaimsSet, SecurityContext) => Option[BadJWTException]] = List.empty`   
    (Optional) List of additional validations that will be executed on the JWT token.    
    Default is an empty List.
    
    Some "additional validations" are already implemented in the object `ProvidedValidations`.

### 2. AwsCognitoJwtValidator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `AwsCognitoJwtValidator` class.
It follows the AWS documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.implementations.{AwsCognitoJwtValidator, CognitoUserPoolId, S3Region}
import com.guizmaii.scalajwt.JwtToken

val jwtToken = JwtToken(content = "...")
val s3Region = S3Region(value = "eu-west-1")
val cognitoUserPoolId = CognitoUserPoolId(value = "...")

val awsCognitoJwtValidator = AwsCognitoJwtValidator(s3Region, cognitoUserPoolId).validate(jwtToken)
```

### 3. Auth0JwtValidator

You can read which properties of the JWT token are validated by this implementation in the documentation of the `Auth0JwtValidator` class.
It follows the Auth0 documentation recommandations.

Example of use:
```scala
import com.guizmaii.scalajwt.JwtToken
import com.guizmaii.scalajwt.implementations.{Auth0Audience, Auth0Domain, Auth0JwtValidator}

val jwtToken          = JwtToken(content = "...")
val auth0Domain       = Auth0Domain(value = "...")
val auth0Audience     = Auth0Audience(value = "...")

val auth0JwtValidator = Auth0JwtValidator(domain = auth0Domain, audience = auth0Audience).validate(jwtToken)
```