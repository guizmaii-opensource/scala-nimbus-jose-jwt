package com.guizmaii.scalajwt.cognito

opaque type S3Region = String
object S3Region {
  def apply(value: String): S3Region = value

  extension (region: S3Region) {
    def value: String = region
  }
}
