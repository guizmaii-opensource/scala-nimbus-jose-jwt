package com.guizmaii.scalajwt.core

opaque type JwtToken <: String = String
object JwtToken {
  def apply(content: String): JwtToken = content
}
