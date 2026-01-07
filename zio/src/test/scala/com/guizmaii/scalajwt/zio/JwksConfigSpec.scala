package com.guizmaii.scalajwt.zio

import zio.Scope
import zio.test.*

object JwksConfigSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("JwksConfig")(
      suite("::fromString")(
        test("should return Right for valid URL") {
          val result = JwksConfig.fromString("https://example.com/.well-known/jwks.json")
          assertTrue(
            result.is(_.right).jwksUri.encode == "https://example.com/.well-known/jwks.json"
          )
        },
        test("should return Left for invalid URL") {
          val result = JwksConfig.fromString("not a valid url :::")
          assertTrue(
            result.is(_.left).isInstanceOf[IllegalArgumentException]
          )
        }
      )
    )
}
