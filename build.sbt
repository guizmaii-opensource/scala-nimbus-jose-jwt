organization := "com.guizmaii"
name := "scala-nimbus-jose-jwt"

scalafmtOnCompile := true
scalafmtCheck := true
scalafmtSbtCheck := true

lazy val scala211 = "2.11.12"
lazy val scala212 = "2.12.12"
lazy val scala213 = "2.13.3"

scalaVersion := scala213
crossScalaVersions := Seq(scala211, scala212, scala213)

val nimbusJwt             = "com.nimbusds"            % "nimbus-jose-jwt"         % "9.5"
val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2"
val scalaCheck            = "org.scalacheck"         %% "scalacheck"              % "1.15.2"  % Test
val scalatest             = "org.scalatest"          %% "scalatest"               % "3.2.3"   % Test
val scalatestPlus         = "org.scalatestplus"      %% "scalacheck-1-14"         % "3.2.2.0" % Test

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
