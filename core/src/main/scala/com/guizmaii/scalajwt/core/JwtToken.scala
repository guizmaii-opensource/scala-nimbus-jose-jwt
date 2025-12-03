package com.guizmaii.scalajwt.core

opaque type JwtToken = String
object JwtToken {
  def apply(content: String): JwtToken = content

  extension (jt: JwtToken) {
    def content: String = jt
  }
}
