import AssemblyKeys._

name := "fshgen"

organization := "com.github.memo33"

version := "0.1.3"

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-Yinline-warnings",
  "-optimize",
  "-encoding", "UTF-8",
  "-target:jvm-1.6")

autoAPIMappings := true


zipPath <<= (target, name, version) map { (t: File, n, v) => t / s"${n}-${v}.zip" }

dist <<= (assembly in Compile, baseDirectory, zipPath, streams) map {
  (fatjar: File, base: File, out: File, ts: TaskStreams) =>
    val inputs: Seq[(File,String)] = Seq(fatjar, readme(base), license(base)) x Path.flat
    val images: Seq[(File, String)] = (examples(base) * "*.png").get x Path.relativeTo(base)
    IO.zip(inputs ++ images, out)
    ts.log.info("Created zip archive at " + out.toString)
    out
}


packSettings

packMain := Map(s"${name.value}-${version.value}" -> "fshgen.Main")

packJvmOpts := Map(s"${name.value}-${version.value}" -> Seq("-Djava.awt.headless=true"))


assemblySettings

jarName in assembly := s"${name.value}-${version.value}.jar"

mainClass in assembly := Some("fshgen.Main")


libraryDependencies += "org.scalatest" %% "scalatest" % "2.1.5" % "test"

libraryDependencies += "com.mortennobel" % "java-image-scaling" % "0.8.5"


resolvers += Resolver.sonatypeRepo("public")

libraryDependencies += "com.github.scopt" %% "scopt" % "3.2.0"


resolvers += "stephenjudkins-bintray" at "https://dl.bintray.com/stephenjudkins/maven"

resolvers += "memo33-bintray" at "https://dl.bintray.com/memo33/maven"

libraryDependencies += "com.github.memo33" %% "scdbpf" % "0.1.7"
