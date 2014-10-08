import sbt._
import java.io.File

object FshGenDef extends Build {

  val readmePath = TaskKey[File]("readme-path", "path to readme file")
  val licensePath = TaskKey[File]("license-path", "path to license file")
  val examplesPath = TaskKey[File]("examples-path", "path to folder of example files")
  val zipPath = TaskKey[File]("zip-path", "path to dist zip file")
  val dist = TaskKey[File]("dist", "creates a distributable zip file")
}
