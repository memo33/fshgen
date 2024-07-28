name := "fshgen"

organization := "io.github.memo33"

version := "0.1.6-SNAPSHOT"

scalaVersion := "3.4.2"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-Yinline-warnings",
  //"-optimize",
  "-encoding", "UTF-8",
  "-release:8")

// make build info available in source files
lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, organization, version, scalaVersion, sbtVersion, licenses),
    buildInfoPackage := "io.github.memo33.fshgen"
  )

autoAPIMappings := true


lazy val zipPath = TaskKey[File]("zip-path", "path to dist zip file")
zipPath := target.value / s"${name.value}-${version.value}.zip"

// create a distributable zip file with `sbt dist` (containing the large jar)
lazy val dist = TaskKey[File]("dist", "creates a distributable zip file")
dist := {
  val fatjar: File = (Compile / assembly).value
  val inputs: Seq[(File, String)] =
    Seq(
      fatjar,
      (baseDirectory.value / "README.md"),
      (baseDirectory.value / "LICENSE"),
      (baseDirectory.value / "src" / "scripts" / "fshgen"),
      (baseDirectory.value / "src" / "scripts" / "fshgen.bat"),
    ) pair Path.flat
  // val images: Seq[(File, String)] = ((baseDirectory.value / "examples") * "*.png").get pair Path.relativeTo(baseDirectory.value) // TODO
  val images: Seq[(File, String)] = Seq(baseDirectory.value / "examples") pair Path.relativeTo(baseDirectory.value)
  // IO.zip(inputs ++ images, zipPath.value, time = None)
  // We use an external `zip` command in order to preserve file permissions (executable bit)
  zipPath.value.delete()
  import scala.sys.process._
  (Seq("zip", "-j", zipPath.value.toString) ++ inputs.map(_._1.toString)).!
  (Seq("zip", "-r", zipPath.value.toString) ++ images.map(_._2)).!
  streams.value.log.info("Created zip archive at " + zipPath.value.toString)
  zipPath.value
}


// Create a large executable jar with `sbt assembly`.
assembly / assemblyJarName := s"${name.value}.jar"

assembly / mainClass := Some("io.github.memo33.fshgen.Main")



libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.15" % "test"

libraryDependencies += "com.mortennobel" % "java-image-scaling" % "0.8.5"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"

libraryDependencies += "io.github.memo33" %% "scdbpf" % "0.2.1" cross CrossVersion.for3Use2_13

libraryDependencies += "com.github.mokiat" % "java-data-front" % "v2.0.1" from "https://github.com/mokiat/java-data-front/releases/download/v2.0.1/com.mokiat.data.front-2.0.1.jar"
