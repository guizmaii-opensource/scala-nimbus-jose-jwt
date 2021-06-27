package com.guizmaii.scalajwt

import com.nimbusds.jose.JWSAlgorithm

sealed trait SupportedJWSAlgorithm {
  private[scalajwt] def nimbusRepresentation: JWSAlgorithm
}
case object HS256 extends SupportedJWSAlgorithm {
  private[scalajwt] override def nimbusRepresentation: JWSAlgorithm = JWSAlgorithm.HS256
}
case object RS256 extends SupportedJWSAlgorithm {
  private[scalajwt] override def nimbusRepresentation: JWSAlgorithm = JWSAlgorithm.RS256
}
