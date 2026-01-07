package com.guizmaii.scalajwt.zio

import com.guizmaii.scalajwt.zio.ZioTestUtils.*
import zio.*
import zio.test.*

object AtomicJWKSourceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("AtomicJWKSource")(
      suite("::get")(
        test("should return keys from the AtomicReference") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)

          val ref       = new java.util.concurrent.atomic.AtomicReference(jwkSet)
          val jwkSource = new AtomicJWKSource(ref)

          val selector = new com.nimbusds.jose.jwk.JWKSelector(
            new com.nimbusds.jose.jwk.JWKMatcher.Builder().build()
          )
          val keys     = jwkSource.get(selector, null)

          assertTrue(
            keys.size() == 1,
            keys.get(0).getKeyID == jwkSet.getKeys.get(0).getKeyID
          )
        }
      )
    )
}
