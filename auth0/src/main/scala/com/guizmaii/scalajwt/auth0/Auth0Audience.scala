package com.guizmaii.scalajwt.auth0

opaque type Auth0Audience = String
object Auth0Audience {
  def apply(value: String): Auth0Audience = value

  extension (audience: Auth0Audience) {
    def value: String = audience
  }
}
