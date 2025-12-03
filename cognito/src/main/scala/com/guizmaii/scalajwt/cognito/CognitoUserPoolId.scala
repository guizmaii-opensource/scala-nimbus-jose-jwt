package com.guizmaii.scalajwt.cognito

opaque type CognitoUserPoolId = String
object CognitoUserPoolId {
  def apply(value: String): CognitoUserPoolId = value

  extension (id: CognitoUserPoolId) {
    def value: String = id
  }
}
