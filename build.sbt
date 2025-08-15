import org.typelevel.scalacoptions.ScalacOption
import org.typelevel.scalacoptions.ScalacOptions

organization := "com.guizmaii"
name         := "scala-nimbus-jose-jwt"

scalafmtOnCompile := true
scalafmtCheck     := true
scalafmtSbtCheck  := true

lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.16"
lazy val scala3   = "3.3.6"

scalaVersion       := scala213
crossScalaVersions := Seq(scala212, scala213, scala3)

val nimbusJwt             = "com.nimbusds"            % "nimbus-jose-jwt"         % "10.4.2"
val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.13.0"
val scalaCheck            = "org.scalacheck"         %% "scalacheck"              % "1.18.1"   % Test
val scalatest             = "org.scalatest"          %% "scalatest"               % "3.2.19"   % Test
val scalatestPlus         = "org.scalatestplus"      %% "scalacheck-1-16"         % "3.2.14.0" % Test
val catsScalatest         = "com.ironcorelabs"       %% "cats-scalatest"          % "3.1.1"    % Test
val catsScala3test        = "com.ironcorelabs"       %% "cats-scalatest"          % "4.0.2"    % Test

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCollectionCompat,
  scalaCheck,
  scalatest,
  scalatestPlus,
  if (scalaVersion.value == scala3) catsScala3test else catsScalatest
)

Test / tpolecatExcludeOptions ++= Set(ScalacOptions.warnValueDiscard, ScalacOptions.privateWarnValueDiscard)

inThisBuild(
  List(
    organization := "com.guizmaii",
    homepage     := Some(url("https://github.com/guizmaii/scala-nimbus-jose-jwt")),
    licenses     := List("MIT" -> url("http://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "guizmaii",
        "Jules Ivanic",
        "jules.ivanic@gmail.com",
        url("https://blog.jules-ivanic.com/#/")
      )
    )
  )
)
