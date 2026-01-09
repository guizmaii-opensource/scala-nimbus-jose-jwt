import org.typelevel.scalacoptions.ScalacOptions
import BuildHelper.*

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / organization := "com.guizmaii"
ThisBuild / name         := "scala-nimbus-jose-jwt"

ThisBuild / scalafmtOnCompile := true
ThisBuild / scalafmtCheck     := true
ThisBuild / scalafmtSbtCheck  := true

ThisBuild / scalaVersion := "3.3.7"

// ### Aliases ###

addCommandAlias("tc", "Test/compile")
addCommandAlias("ctc", "clean; tc")
addCommandAlias("rctc", "reload; ctc")

// ### Dependencies ###

val nimbusJwt      = "com.nimbusds"       % "nimbus-jose-jwt" % "10.7"
val scalaCheck     = "org.scalacheck"    %% "scalacheck"      % "1.19.0"   % Test
val scalatest      = "org.scalatest"     %% "scalatest"       % "3.2.19"   % Test
val scalatestPlus  = "org.scalatestplus" %% "scalacheck-1-19" % "3.2.19.0" % Test
val catsScala3test = "com.ironcorelabs"  %% "cats-scalatest"  % "4.0.2"    % Test

// ZIO dependencies
val zioVersion          = "2.1.24"
val zioHttpVersion      = "3.7.4"
val zioTelemetryVersion = "4.0.0-RC10"
val zio                 = "dev.zio" %% "zio"               % zioVersion
val zioHttp             = "dev.zio" %% "zio-http"          % zioHttpVersion
val zioOpentelemetry    = "dev.zio" %% "zio-opentelemetry" % zioTelemetryVersion
val zioTest             = "dev.zio" %% "zio-test"          % zioVersion % Test
val zioTestSbt          = "dev.zio" %% "zio-test-sbt"      % zioVersion % Test

// ### Modules ###

lazy val root =
  Project(id = "scala-nimbus-jose-jwt", base = file("."))
    .settings(noDoc *)
    .settings(publish / skip := true)
    .settings(crossScalaVersions := Nil) // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
    .aggregate(
      core,
      auth0,
      cognito,
      zioModule,
    )

lazy val core =
  project
    .in(file("core"))
    .settings(stdSettings *)
    .settings(
      name := "scala-nimbus-jose-jwt",
      libraryDependencies ++= Seq(
        nimbusJwt,
        scalaCheck,
        scalatest,
        scalatestPlus,
        catsScala3test
      )
    )

lazy val auth0 =
  project
    .in(file("auth0"))
    .settings(stdSettings *)
    .settings(
      name := "scala-nimbus-jose-jwt-auth0",
    )
    .dependsOn(core % "test->test;compile->compile")

lazy val cognito =
  project
    .in(file("cognito"))
    .settings(stdSettings *)
    .settings(
      name := "scala-nimbus-jose-jwt-cognito",
    )
    .dependsOn(core % "test->test;compile->compile")

lazy val zioModule =
  project
    .in(file("zio"))
    .settings(stdSettings *)
    .settings(
      name := "scala-nimbus-jose-jwt-zio",
      libraryDependencies ++= Seq(
        zio,
        zioHttp,
        zioOpentelemetry,
        zioTest,
        zioTestSbt
      ),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )
    .dependsOn(core % "test->test;compile->compile")

Test / tpolecatExcludeOptions ++= Set(ScalacOptions.warnValueDiscard, ScalacOptions.privateWarnValueDiscard)

inThisBuild(
  List(
    organization := "com.guizmaii",
    homepage     := Some(url("https://github.com/guizmaii/scala-nimbus-jose-jwt")),
    licenses     := List("MIT" -> url("http://opensource.org/licenses/MIT")),
    developers   := List(
      Developer(
        "guizmaii",
        "Jules Ivanic",
        "jules.ivanic@gmail.com",
        url("https://blog.jules-ivanic.com/#/")
      )
    )
  )
)
