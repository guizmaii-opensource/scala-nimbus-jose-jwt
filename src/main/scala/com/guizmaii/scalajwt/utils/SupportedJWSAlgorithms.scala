package com.guizmaii.scalajwt.utils

import com.nimbusds.jose.JWSAlgorithm

object SupportedJWSAlgorithms {

  sealed trait SupportedJWSAlgorithm {
    val nimbusRepresentation: JWSAlgorithm
  }

  case object HS256 extends SupportedJWSAlgorithm {
    override val nimbusRepresentation: JWSAlgorithm = JWSAlgorithm.HS256
  }

  case object RS256 extends SupportedJWSAlgorithm {
    override val nimbusRepresentation: JWSAlgorithm = JWSAlgorithm.RS256
  }

}
