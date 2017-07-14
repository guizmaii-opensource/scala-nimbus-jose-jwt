organization := "com.guizmaii"
name := "scala-nimbus-jose-jwt"
version := "0.1"
scalaVersion := "2.12.2"
scalafmtOnCompile in ThisBuild := true

scalacOptions ++= Seq(
  "-deprecation",
  "-target:jvm-1.8",
  "-encoding",
  "UTF-8",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-unchecked",
  "-Xlint",
  "-Xlint:missing-interpolator",
  "-Yno-adapted-args",
  "-Ywarn-unused",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-Ywarn-unused-import"
)

val nimbusJwt  = "com.nimbusds"   % "nimbus-jose-jwt" % "4.39.2"
val scalaCheck = "org.scalacheck" %% "scalacheck"     % "1.13.5"
val scalaTest  = "org.scalatest"  %% "scalatest"      % "3.0.3"

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCheck % Test,
  scalaTest  % Test
)
