package com.guizmaii.scalajwt.zio

import com.guizmaii.scalajwt.zio.ZioTestUtils.*
import zio.*
import zio.http.*
import zio.test.*

object JwksManagerSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("JwksManager")(
      suite("::live")(
        test("should fetch JWKS on startup and make it available") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)
          val keyId   = jwkSet.getKeys.get(0).getKeyID

          for {
            port    <- serverPort(jwkSet)
            jwksUrl <- ZIO.fromEither(URL.decode(s"http://localhost:$port/.well-known/jwks.json"))
            config   = JwksConfig(jwksUri = jwksUrl, refreshInterval = 1.hour)
            client  <- ZIO.service[Client]
            manager <- ZIO
                         .scoped {
                           JwksManager.live.build.flatMap(env => ZIO.succeed(env.get[JwksManager]))
                         }
                         .provide(ZLayer.succeed(config), ZLayer.succeed(client), noopTracerLayer)
            result  <- manager.jwkSet
            health  <- manager.health
          } yield assertTrue(
            result.getKeys.size() == 1,
            result.getKeys.get(0).getKeyID == keyId,
            health match {
              case JwksHealth.Healthy(_, _) => true
              case _                        => false
            }
          )
        }.provide(Client.default, Server.default),
        test("should fail on startup if JWKS cannot be fetched") {
          for {
            jwksUrl <- ZIO.fromEither(URL.decode("http://localhost:1/.well-known/jwks.json"))
            config   = JwksConfig(
                         jwksUri = jwksUrl,                        // Invalid port - connection will be refused
                         refreshInterval = 1.hour,
                         initialRetrySchedule = Schedule.recurs(0) // No retries for faster test
                       )
            client  <- ZIO.service[Client]
            result  <- ZIO
                         .scoped {
                           JwksManager.live.build
                         }
                         .provide(ZLayer.succeed(config), ZLayer.succeed(client), noopTracerLayer)
                         .either
          } yield assertTrue(
            result.is(_.left).isInstanceOf[JwksFetchError]
          )
        }.provide(Client.default) @@ TestAspect.timeout(10.seconds),
        test("should return Healthy status with correct timestamps after startup") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)

          for {
            port    <- serverPort(jwkSet)
            jwksUrl <- ZIO.fromEither(URL.decode(s"http://localhost:$port/.well-known/jwks.json"))
            config   = JwksConfig(jwksUri = jwksUrl, refreshInterval = 1.hour)
            client  <- ZIO.service[Client]
            manager <- ZIO
                         .scoped {
                           JwksManager.live.build.flatMap(env => ZIO.succeed(env.get[JwksManager]))
                         }
                         .provide(ZLayer.succeed(config), ZLayer.succeed(client), noopTracerLayer)
            health  <- manager.health
          } yield health match {
            case JwksHealth.Healthy(lastRefresh, nextRefresh) =>
              val durationMinutes = java.time.Duration.between(lastRefresh, nextRefresh).toMinutes
              assertTrue(
                nextRefresh.isAfter(lastRefresh),
                durationMinutes == 60L // 1 hour refresh interval
              )
            case JwksHealth.Degraded(_, _)                    =>
              assertNever("Expected Healthy but got Degraded")
          }
        }.provide(Client.default, Server.default),
        test("should expose jwkSource for non-blocking reads") {
          val keyPair = generateKeyPair()
          val jwkSet  = generateJwkSet(keyPair)
          val keyId   = jwkSet.getKeys.get(0).getKeyID

          for {
            port     <- serverPort(jwkSet)
            jwksUrl  <- ZIO.fromEither(URL.decode(s"http://localhost:$port/.well-known/jwks.json"))
            config    = JwksConfig(jwksUri = jwksUrl, refreshInterval = 1.hour)
            client   <- ZIO.service[Client]
            manager  <- ZIO
                          .scoped {
                            JwksManager.live.build.flatMap(env => ZIO.succeed(env.get[JwksManager]))
                          }
                          .provide(ZLayer.succeed(config), ZLayer.succeed(client), noopTracerLayer)
            jwkSource = manager.jwkSource
            selector  = new com.nimbusds.jose.jwk.JWKSelector(new com.nimbusds.jose.jwk.JWKMatcher.Builder().build())
            keys      = jwkSource.get(selector, null)
          } yield assertTrue(
            keys.size() == 1,
            keys.get(0).getKeyID == keyId
          )
        }.provide(Client.default, Server.default)
      ) @@ TestAspect.sequential // Run live tests sequentially to avoid port conflicts
    )

  /** Helper to start a mock JWKS server and return the port */
  private def serverPort(jwkSet: com.nimbusds.jose.jwk.JWKSet): ZIO[Server, Nothing, Int] =
    for {
      port <- Server.install(mockJwksServer(jwkSet))
      _    <- ZIO.logInfo(s"Mock JWKS server started on port $port")
    } yield port
}
