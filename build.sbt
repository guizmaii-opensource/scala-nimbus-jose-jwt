organization := "com.guizmaii"
name := "scala-nimbus-jose-jwt"

scalafmtOnCompile in ThisBuild := true

scalaVersion := "2.12.4"
crossScalaVersions in ThisBuild := Seq("2.11.12", scalaVersion.value)
scalafmtVersion := "1.3.0"

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

val nimbusJwt  = "com.nimbusds"   % "nimbus-jose-jwt" % "5.1"
val scalaCheck = "org.scalacheck" %% "scalacheck"     % "1.13.5"
val scalaTest  = "org.scalatest"  %% "scalatest"      % "3.0.4"

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCheck % Test,
  scalaTest  % Test
)

// sbt-bintray options
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
bintrayPackageLabels := Seq("JWT", "Scala")
