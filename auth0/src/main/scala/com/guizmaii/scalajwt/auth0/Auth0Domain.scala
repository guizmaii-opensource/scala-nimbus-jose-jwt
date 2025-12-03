package com.guizmaii.scalajwt.auth0

opaque type Auth0Domain = String
object Auth0Domain {
  def apply(value: String): Auth0Domain = value

  extension (domain: Auth0Domain) {
    def value: String = domain
  }
}
