package com.guizmaii.scalajwt.core

import com.guizmaii.scalajwt
import com.nimbusds.jose.JWSAlgorithm

enum SupportedJWSAlgorithm {
  case HS256, RS256

  def nimbusRepresentation: JWSAlgorithm =
    this match {
      case HS256 => JWSAlgorithm.HS256
      case RS256 => JWSAlgorithm.RS256
    }
}
