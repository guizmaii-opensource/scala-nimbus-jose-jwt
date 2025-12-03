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

val nimbusJwt      = "com.nimbusds"       % "nimbus-jose-jwt" % "10.6"
val scalaCheck     = "org.scalacheck"    %% "scalacheck"      % "1.19.0"   % Test
val scalatest      = "org.scalatest"     %% "scalatest"       % "3.2.19"   % Test
val scalatestPlus  = "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0" % Test
val catsScala3test = "com.ironcorelabs"  %% "cats-scalatest"  % "4.0.2"    % Test

// ### Modules ###

lazy val root =
  Project(id = "zonic", base = file("."))
    .settings(noDoc *)
    .settings(publish / skip := true)
    .settings(crossScalaVersions := Nil) // https://www.scala-sbt.org/1.x/docs/Cross-Build.html#Cross+building+a+project+statefully,
    .aggregate(
      core,
      auth0,
      cognito
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
