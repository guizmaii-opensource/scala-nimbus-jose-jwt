organization := "com.guizmaii"
name := "scala-nimbus-jose-jwt"

scalafmtOnCompile := true
scalafmtCheck := true
scalafmtSbtCheck := true

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.11"
lazy val scala213 = "2.13.2"

scalaVersion := scala213
crossScalaVersions := Seq(scala211, scala212, scala213)

val nimbusJwt             = "com.nimbusds"            % "nimbus-jose-jwt"          % "9.1.2"
val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat"  % "2.2.0"
val scalaCheck            = "org.scalacheck"         %% "scalacheck"               % "1.14.3"      % Test
val scalatest             = "org.scalatest"          %% "scalatest"                % "3.2.2"       % Test
val scalatestPlus         = "org.scalatestplus"      %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCollectionCompat,
  scalaCheck,
  scalatest,
  scalatestPlus
)

// sbt-bintray options
licenses += ("MIT", url("http://opensource.org/licenses/MIT"))
bintrayPackageLabels := Seq("JWT", "Scala")
