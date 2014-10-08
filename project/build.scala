import sbt._

object FshGenDef extends Build {

  def readme(base: File): File = base / "README.md"
  def license(base: File): File = base / "LICENSE"
  def examples(base: File): File = base / "examples"
  val zipPath = TaskKey[File]("zip-path", "path to dist zip file")
  val dist = TaskKey[File]("dist", "creates a distributable zip file")
}
