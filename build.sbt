import org.typelevel.scalacoptions.ScalacOptions

organization := "com.guizmaii"
name         := "scala-nimbus-jose-jwt"

scalafmtOnCompile := true
scalafmtCheck     := true
scalafmtSbtCheck  := true

scalaVersion := "3.3.7"

val nimbusJwt             = "com.nimbusds"            % "nimbus-jose-jwt"         % "10.5"
val scalaCollectionCompat = "org.scala-lang.modules" %% "scala-collection-compat" % "2.14.0"
val scalaCheck            = "org.scalacheck"         %% "scalacheck"              % "1.19.0"   % Test
val scalatest             = "org.scalatest"          %% "scalatest"               % "3.2.19"   % Test
val scalatestPlus         = "org.scalatestplus"      %% "scalacheck-1-16"         % "3.2.14.0" % Test
val catsScala3test        = "com.ironcorelabs"       %% "cats-scalatest"          % "4.0.2"    % Test

libraryDependencies ++= Seq(
  nimbusJwt,
  scalaCollectionCompat,
  scalaCheck,
  scalatest,
  scalatestPlus,
  catsScala3test
)

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
