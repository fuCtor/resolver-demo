ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

val commonSettings = Seq(
  scalacOptions ++= Seq("-Ymacro-annotations"),
  libraryDependencies ++= Seq("com.twitter" %% "finagle-http" % "22.7.0")
)

val circeDeps = Seq(
  libraryDependencies ++= Seq("io.circe" %% "circe-yaml" % "0.14.2", "io.circe" %% "circe-generic" % "0.14.2")
)

lazy val root = (project in file("."))
  .aggregate(simple, async, fileExample)

lazy val simple = (project in file("simple"))
  .settings(commonSettings)

lazy val async = (project in file("async"))
  .settings(commonSettings)

lazy val fileExample = (project in file("file"))
  .settings(commonSettings, circeDeps)

lazy val dtab = (project in file("dtab"))
  .settings(commonSettings)

lazy val routing = (project in file("routing"))
  .settings(commonSettings)
