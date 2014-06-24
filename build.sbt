import AssemblyKeys._

name := "fshgen"

version := "0.1.0"

scalaVersion := "2.11.0"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-Yinline-warnings",
  //"-optimize",
  "-encoding", "UTF-8",
  "-target:jvm-1.6")

autoAPIMappings := true


zipPath <<= (target, name, version) map { (t: File, n, v) => t / s"${n}-${v}.zip" }

readmePath <<= (baseDirectory) map { (b: File) => b / "README.md" }

licensePath <<= (baseDirectory) map { (b: File) => b / "LICENSE" }

dist <<= (assembly in Compile, readmePath, licensePath, zipPath, streams) map {
  (fatjar: File, readme: File, license: File, out: File, ts: TaskStreams) =>
    val inputs: Seq[(File,String)] = Seq(fatjar, readme, license) x Path.flat
    IO.zip(inputs, out)
    ts.log.info("Created zip archive at " + out.toString)
    out
}


packSettings

packMain := Map(s"${name.value}-${version.value}" -> "fshgen.Main")

packJvmOpts := Map(s"${name.value}-${version.value}" -> Seq("-Djava.awt.headless=true"))


assemblySettings

jarName in assembly := s"${name.value}-${version.value}.jar"

mainClass in assembly := Some("fshgen.Main")


libraryDependencies += "scdbpf" %% "scdbpf" % "0.1.3" from "https://dl.dropboxusercontent.com/s/p2pe6vsqyvs3xp6/scdbpf_2.11-0.1.3.jar"

libraryDependencies += "com.jsuereth" %% "scala-arm" % "1.4"

libraryDependencies += "passera.unsigned" %% "scala-unsigned" % "0.1.1" from "https://dl.dropboxusercontent.com/s/yojvk2bb7o1c627/scala-unsigned_2.11-0.1.1.jar"

libraryDependencies += "com.propensive" %% "rapture-core" % "0.9.0"

libraryDependencies += "com.propensive" %% "rapture-io" % "0.9.1"

libraryDependencies += "jsquish" % "jsquish" % "0.1" from "https://dl.dropboxusercontent.com/s/7ijtzyjb353fyas/jsquish.jar"

libraryDependencies += "com.mortennobel" % "java-image-scaling" % "0.8.5"

resolvers += "stephenjudkins-bintray" at "http://dl.bintray.com/stephenjudkins/maven"

libraryDependencies += "ps.tricerato" %% "pureimage" % "0.1.1"

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"
