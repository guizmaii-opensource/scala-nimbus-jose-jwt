package com.guizmaii.scalajwt
import java.net.URL

import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException

final case class S3Region(value: String)          extends AnyVal
final case class CognitoUserPoolId(value: String) extends AnyVal

object AwsCognitoJwtValidator {
  def apply(
      s3Region: S3Region,
      cognitoUserPoolId: CognitoUserPoolId
  ): AwsCognitoJwtValidator = new AwsCognitoJwtValidator(s3Region, cognitoUserPoolId)
}

final class AwsCognitoJwtValidator(
    s3Region: S3Region,
    cognitoUserPoolId: CognitoUserPoolId
) extends JwtValidator {

  import ProvidedAdditionalChelcks._

  private val cognitoIdpUrl = s"https://cognito-idp.${s3Region.value}.amazonaws.com/${cognitoUserPoolId.value}"

  private val jwkSet: JWKSource[SecurityContext] = new RemoteJWKSet(new URL(s"$cognitoIdpUrl/.well-known/jwks.json"))

  /**
    * The additional checks come from the AWS Cognito documentation:
    *   https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-with-identity-providers.html#amazon-cognito-identity-user-pools-using-id-and-access-tokens-in-web-api
    *
    * I copy the AWS Cognito documentation instructions here in order to be able to track changes:
    *
    * ---------------
    * # Using ID Tokens and Access Tokens in your Web APIs
    *
    * Since both the ID token and the access token are JSON Web Tokens (JWT), you may use any of the available JWT libraries to decode the JWT and verify the signature.
    * For example, if your platform is Java, you could use the Nimbus JOSE and JWT library.
    * The following procedure describes the high level steps you must implement to process the ID token and the access token on the server side.
    *
    * ## To verify a signature for ID and access tokens
    *
    * 1. Download and store the JSON Web Token (JWT) Set for your user pool.
    *    You can locate them at https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json.
    *
    *    Each JWT should be stored against its kid.
    *
    *     **Note**
    *       This is a one time step before your web APIs can process the tokens.
    *       Now you can perform the following steps each time the ID token or the access token are used against your web APIs.
    *
    * 2. Decode the token string into JWT format.
    *
    * 3. Check the iss claim. It should match your user pool.
    *    For example, a user pool created in the us-east-1 region will have an iss value of https://cognito-idp.us-east-1.amazonaws.com/{userPoolId}.
    *
    * 4. Check the token_use claim.
    *
    *     If you are only accepting the access token in your web APIs, its value must be access.
    *
    *     If you are only using the ID token, its value must be id.
    *
    *     If you are using both tokens, the value is either id or access.
    *
    * 5. Get the kid from the JWT token header and retrieve the corresponding JSON Web Key that was stored in step 1.
    *
    * 6. Verify the signature of the decoded JWT token.
    *
    * 7. Check the exp claim and make sure the token is not expired.
    *
    * You can now trust the claims inside the token and use it as it fits your requirements.
    * ---------------
    */
  private val configurableJwtValidator =
    new ConfigurableJwtValidator(
      keySource = jwkSet,
      additionalChecks = List(
        requireExpirationClaim,
        requireTokenUseClaim("access"),
        requiredIssuerClaim(cognitoIdpUrl),
        requiredNonEmptySubject
      )
    )

  override def validate(jwtToken: JwtToken): Either[BadJWTException, (JwtToken, JWTClaimsSet)] =
    configurableJwtValidator.validate(jwtToken)
}
