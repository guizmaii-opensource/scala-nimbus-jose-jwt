organization := "com.guizmaii"
name := "scala-nimbus-jose-jwt"

scalafmtOnCompile := true
scalafmtCheck := true
scalafmtSbtCheck := true

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.5"

scalaVersion := scala213
crossScalaVersions := Seq(scala211, scala212, scala213)

val nimbusJwt             = "com.nimbusds"            % "nimbus-jose-jwt"         % "9.9.1"
val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3"
val scalaCheck            = "org.scalacheck"         %% "scalacheck"              % "1.15.2"  % Test
val scalatest             = "org.scalatest"          %% "scalatest"               % "3.2.8"   % Test
val scalatestPlus         = "org.scalatestplus"      %% "scalacheck-1-14"         % "3.2.2.0" % Test

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCollectionCompat,
  scalaCheck,
  scalatest,
  scalatestPlus
)

inThisBuild(List(
  organization := "com.guizmaii",
  homepage := Some(url("https://github.com/guizmaii/scala-nimbus-jose-jwt")),
  licenses := List("MIT" -> url("http://opensource.org/licenses/MIT")),
  developers := List(
    Developer(
      "guizmaii",
      "Jules Ivanic",
      "jules.ivanic@gmail.com",
      url("https://blog.jules-ivanic.com/#/")
    )
  )
))