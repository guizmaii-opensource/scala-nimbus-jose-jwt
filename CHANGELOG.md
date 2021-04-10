# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

- Configure Github Actions && sbt-ci-release
- Remove TravisCI

## [v1.0.1] 2020-10-31

- **Update Scala 2.13 and 2.12**
- **Update dependencies, sbt and sbt plugins**

## [v1.0.0] 2020-05-29

- **Add Scala 2.13 in the CI build matrix and update the Scala 2.12 version used**
- **Update `nimbus-jose-jwt`, `scalatest`, `scalafmt` and `sbt-scoverage`**
- **Replace hand written scalac flag by `sbt-tpolecat`**
- **Add support for Scala 2.13**
- **Update Nimbus version from 5.10 to 8.17**
- **Update SBT to 1.3.10**

## [v0.9.0] 2018-04-27

- **Fix Scala 2.11 version publishing**
- **Improve `scalafmt` configuration**
- **Update Scala 2.12 version**
- **Improve `scalacOptions` config**
- **Update dependencies**

## [v0.8.0] 2017-12-02

- **Improve documentation**
- **Add `requiredAudience` validation**
- **Add `Auth0JwtValidator`**
- **Add `HS256` tokens support**
- **Improve performance by replacing the `Try` by a `try catch` block**
- **Update sbt to v1.0.4**
- **Add missing `final`s**
- **Rename `ProvidedAdditionalChelcks` to `ProvidedValidations`**
- **Reorganize code**
- **Improve documentation**
- **Update travis config**
- **Update dependencies**

## [v0.7.0] 2017-08-28

- **Update SBT and its plugins**
- **Update dependencies**

## [v0.6.0] 2017-08-15

- **Only handle `RS256` signed tokens for now**

## [v0.5.0] 2017-08-14

- **Handle every possible `Exception`**
- **Handle the case where the JWT token is invalid**
- **Cover `ConfigurableJwtValidator` with tests**
- **Update scalafmt**
- **Update Nimbus from v4.39.2 to v4.41.1**
- **Update Scala from v2.12.2 to v2.12.3**

## [v0.4.0] 2017-07-16

- **Add `AwsCognitoJwtValidator`**
- **Reorganize files**

## [v0.3.0] 2017-07-14

- **Rollback to sbt-bintray v0.3.0 because of this issue: https://github.com/sbt/sbt-bintray/issues/104**

## [v0.2.0] 2017-07-14

- **Config sbt-bintray && Scala cross version compilation**

## [v0.1.0] 2017-07-14

- **First JwtValidator impl: `ConfigurableJwtValidator`**
