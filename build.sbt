name := "fshgen"

organization := "com.github.memo33"

version := "0.1.6-SNAPSHOT"

scalaVersion := "2.11.12"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-Yinline-warnings",
  "-optimize",
  "-encoding", "UTF-8",
  "-target:jvm-1.8")

autoAPIMappings := true


val zipPath = TaskKey[File]("zip-path", "path to dist zip file")
val dist = TaskKey[File]("dist", "creates a distributable zip file")
zipPath := target.value / s"${name.value}-${version.value}.zip"

// create a distributable zip file with `sbt zip` (containing the large jar)
dist := {
  val fatjar: File = (Compile / assembly).value
  val inputs: Seq[(File, String)] = Seq(fatjar, (baseDirectory.value / "README.md"), (baseDirectory.value / "LICENSE")) pair Path.flat
  val images: Seq[(File, String)] = ((baseDirectory.value / "examples") * "*.png").get pair Path.relativeTo(baseDirectory.value) // TODO
  IO.zip(inputs ++ images, zipPath.value, time = None)
  streams.value.log.info("Created zip archive at " + zipPath.value.toString)
  zipPath.value
}


// Create a large executable jar with `sbt assembly`.
assembly / assemblyJarName := s"${name.value}-${version.value}.jar"

assembly / mainClass := Some("fshgen.Main")



libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.5" % "test"

libraryDependencies += "com.mortennobel" % "java-image-scaling" % "0.8.5"

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"

libraryDependencies += "io.github.memo33" %% "scdbpf" % "0.2.1"
