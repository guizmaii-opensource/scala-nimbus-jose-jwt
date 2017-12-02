package com.guizmaii.scalajwt.implementations

import java.net.URL

import com.guizmaii.scalajwt.{JwtToken, JwtValidator}
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException

final case class Auth0Domain(value: String)   extends AnyVal
final case class Auth0Audience(value: String) extends AnyVal

object Auth0JwtValidator {
  def apply(domain: Auth0Domain, audience: Auth0Audience): Auth0JwtValidator =
    new Auth0JwtValidator(domain, audience)
}

/**
  * The additional validations come from the Auth0 documentation:
  *   https://auth0.com/docs/api-auth/tutorials/verify-access-token
  *
  * I copy the Auth0 documentation instructions here in order to be able to track changes:
  *
  * The API needs to verify the signature. The signature is used to verify that the sender of the JWT is who it says it is and to ensure that the message wasn't changed along the way.
  *
  * Remember that the signature is created using the header and the payload of the JWT, a secret and the hashing algorithm being used (as specified in the header: HMAC, SHA256 or RSA). The way to verify it, depends on the hashing algorithm:
  *
  * For HS256, the API's Signing Secret is used. You can find this information at your API's Settings. Note that the field is only displayed for APIs that use HS256.
  * For RS256, the tenant's JSON Web Key Set (JWKS) is used. Your tenant's JWKS is https://{YOUR_DOMAIN}/.well-known/jwks.json.
  * The most secure practice, and our recommendation, is to use RS256.
  *
  * Once the API verifies the token's signature, the next step is to validate the standard claims of the token's payload.
  * The following validations need to be made:
  *
  * Token expiration:
  *   The current date/time must be before the expiration date/time listed in the exp claim (which is a Unix timestamp).
  *   If not, the request must be rejected.
  * Token issuer:
  *   The iss claim denotes the issuer of the JWT. The value must match the one configured in your API.
  *   For JWTs issued by Auth0, iss holds your Auth0 domain with a https:// prefix and a / suffix: https://toutatis-testing.eu.auth0.com/.
  * Token audience:
  *   The aud claim identifies the recipients that the JWT is intended for.
  *   For JWTs issued by Auth0, aud holds the unique identifier of the target API (field Identifier at your API's Settings).
  *   If the API is not the intended audience of the JWT, it must reject the request.
  *
  *   To validate the claims, you have to decode the JWT, retrieve the claims (exp, iss, aud) and validate their values.
  *
  * ---------------
  */
final class Auth0JwtValidator(domain: Auth0Domain, audience: Auth0Audience) extends JwtValidator {

  import com.guizmaii.scalajwt.utils.ProvidedValidations._

  private val auth0IdpUrl: String = s"https://${domain.value}"

  private val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(s"$auth0IdpUrl/.well-known/jwks.json"))

  private val configurableJwtValidator =
    new ConfigurableJwtValidator(
      keySource = jwkSet,
      additionalValidations = List(
        requireAudience(audience.value),
        requireExpirationClaim,
        requiredIssuerClaim(s"$auth0IdpUrl/"),
        requiredNonEmptySubject
      )
    )

  override def validate(jwtToken: JwtToken): Either[BadJWTException, (JwtToken, JWTClaimsSet)] =
    configurableJwtValidator.validate(jwtToken)

}
