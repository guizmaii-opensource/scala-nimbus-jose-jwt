package com.guizmaii.scalajwt.implementations

import com.guizmaii.scalajwt.{InvalidToken, JwtToken, JwtValidator}
import com.nimbusds.jose.jwk.source.{JWKSource, JWKSourceBuilder}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.{DefaultJWTClaimsVerifier, JWTClaimsSetVerifier}

import java.net.URI

final case class Auth0Domain(value: String)
final case class Auth0Audience(value: String)

object Auth0JwtValidator {
  def apply(domain: Auth0Domain, audience: Auth0Audience): Auth0JwtValidator =
    new Auth0JwtValidator(jwkSource(domain), defaultAuth0ClaimSetVerifier(domain, audience))

  def apply(
      domain: Auth0Domain,
      audience: Auth0Audience,
      customClaimsSetVerifier: DefaultJWTClaimsVerifier[SecurityContext] => JWTClaimsSetVerifier[SecurityContext]
  ): Auth0JwtValidator =
    new Auth0JwtValidator(jwkSource(domain), customClaimsSetVerifier(defaultAuth0ClaimSetVerifier(domain, audience)))

  private[scalajwt] def defaultAuth0ClaimSetVerifier(
      domain: Auth0Domain,
      audience: Auth0Audience
  ): DefaultJWTClaimsVerifier[SecurityContext] = {
    import scala.jdk.CollectionConverters.*

    new DefaultJWTClaimsVerifier[SecurityContext](
      Set(audience.value).asJava,
      new JWTClaimsSet.Builder().issuer(s"${Auth0JwtValidator.auth0IdpUrl(domain)}/").build(),
      Set("exp").asJava,
      null
    )
  }

  private[scalajwt] def jwkSource(domain: Auth0Domain): JWKSource[SecurityContext] =
    JWKSourceBuilder.create(URI.create(s"${auth0IdpUrl(domain)}/.well-known/jwks.json").toURL).build()

  private[scalajwt] def auth0IdpUrl(domain: Auth0Domain): String = s"https://${domain.value}"
}

/** The additional validations come from the Auth0 documentation: https://auth0.com/docs/api-auth/tutorials/verify-access-token
  *
  * I copy the Auth0 documentation instructions here in order to be able to track changes:
  *
  * The API needs to verify the signature. The signature is used to verify that the sender of the JWT is who it says it is and to ensure
  * that the message wasn't changed along the way.
  *
  * Remember that the signature is created using the header and the payload of the JWT, a secret and the hashing algorithm being used (as
  * specified in the header: HMAC, SHA256 or RSA). The way to verify it, depends on the hashing algorithm:
  *
  * For HS256, the API's Signing Secret is used. You can find this information at your API's Settings. Note that the field is only displayed
  * for APIs that use HS256. For RS256, the tenant's JSON Web Key Set (JWKS) is used. Your tenant's JWKS is
  * https://{YOUR_DOMAIN}/.well-known/jwks.json. The most secure practice, and our recommendation, is to use RS256.
  *
  * Once the API verifies the token's signature, the next step is to validate the standard claims of the token's payload. The following
  * validations need to be made:
  *
  * Token expiration: The current date/time must be before the expiration date/time listed in the exp claim (which is a Unix timestamp). If
  * not, the request must be rejected. Token issuer: The iss claim denotes the issuer of the JWT. The value must match the one configured in
  * your API. For JWTs issued by Auth0, iss holds your Auth0 domain with a https:// prefix and a / suffix:
  * https://toutatis-testing.eu.auth0.com/. Token audience: The aud claim identifies the recipients that the JWT is intended for. For JWTs
  * issued by Auth0, aud holds the unique identifier of the target API (field Identifier at your API's Settings). If the API is not the
  * intended audience of the JWT, it must reject the request.
  *
  * To validate the claims, you have to decode the JWT, retrieve the claims (exp, iss, aud) and validate their values.
  *
  * ---------------
  */
final class Auth0JwtValidator private[scalajwt] (
    jwkSet: JWKSource[SecurityContext],
    claimsetVerifier: JWTClaimsSetVerifier[SecurityContext]
) extends JwtValidator {

  private val configurableJwtValidator = ConfigurableJwtValidator(keySource = jwkSet, claimsVerifier = claimsetVerifier)

  override def validate(jwtToken: JwtToken): Either[InvalidToken, JWTClaimsSet] =
    configurableJwtValidator.validate(jwtToken)
}
