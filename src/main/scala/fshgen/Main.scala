package io.github.memo33.fshgen

import scala.util.matching.Regex, Regex.Match
import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import java.util.regex.Pattern
import java.io.File
import io.github.memo33.scdbpf._, DbpfUtil._, RotFlip._


object Mode extends Enumeration {
  val Import, Export = Value
}

case class Config(
  mode: Mode.Value = null,
  outFile: File = null,
  inputFiles: Seq[File] = Seq.empty,
  force: Boolean = false,
  append: Boolean = false,
  mipsSeparate: Boolean = false,
  mipsEmbedded: Boolean = false,
  mipsNumber: Int = 4,
  hd: Boolean = false,
  slice: Boolean = false,
  sliceWidth: Int = 128,
  sliceHeight: Int = 128,
  iidOffset: Int = 0,
  gid: Option[Int] = None,
  darken: Boolean = false,
  brighten: Boolean = false,
  backgroundBright: Boolean = false,
  backgroundDark: Boolean = false,
  attachName: Boolean = false,
  alphaSeparate: Boolean = false,
  fshDirId: Fsh.FshDirectoryId = Fsh.FshDirectoryId.G264,
  fshFormat: Fsh.FshFormat = Fsh.FshFormat.Dxt3,
  silent: Boolean = false,
  iidPatternString: String = ".*",
  withBatModels: Boolean = false,
  noFlipV: Boolean = false,
) {
  lazy val iidPattern: Pattern = Pattern.compile(iidPatternString, Pattern.CASE_INSENSITIVE)
}
object Config {
  private val AbsId = "absId"; private val Sign = "sign"; private val RelId = "relId"
  private val Rot = "rot"; private val Flip = "flip"; private val Alpha = "alpha"

  private val absIdGroup = """(?:0x)?(\p{XDigit}{8}|0)"""         // absId
  private val relIdGroup = """(\+|-)(?:0x)?(\p{XDigit}{1,8})"""   // sign, relId
  private lazy val sliceRegex = new Regex(
    "(?i)" +                              // case insensitve matching
    """(?<![-\p{XDigit}])""" +            // avoids unwanted preceding chars
    s"(?:$absIdGroup|$relIdGroup)" +      // matches id
    "(?:-([0-3])-([01]))?" +              // matches optional rot flip
    "(?:[_ ](a|b|b?alpha))?" +             // matches optional alpha/balpha tag
    """(?=[_ \.])""",                     // ensures separation of id tokens
    AbsId, Sign, RelId, Rot, Flip, Alpha)

  class IdContext(text: String) {
    val matches = (sliceRegex findAllMatchIn text).toSeq
    def isColor: Boolean = matches forall (_.group(Alpha) == null)
    def isAlpha: Boolean =
      matches exists { m => val s = m.group(Alpha); s != null && s.toLowerCase.startsWith("a") }
    def isSidewalkAlpha: Boolean =
      matches exists { m => val s = m.group(Alpha); s != null && s.toLowerCase.startsWith("b") }
    private def extractRF(m: Match, default: RotFlip): RotFlip = {
      val r = m.group(Rot); val f = m.group(Flip)
      if (r != null & f != null) R0F0 / RotFlip(r.toInt, f.toInt)
      else default
    }
    def extractLastId: Option[(Int, RotFlip)] =
      matches.view.reverse.filter(_.group(AbsId) != null).headOption map { m =>
        (java.lang.Long.parseLong(m.group(AbsId), 16).toInt, extractRF(m, R0F0))
      }
    lazy val extractAllIds: Seq[(Int, RotFlip)] = {
      val buf = new scala.collection.mutable.ListBuffer[(Int, RotFlip)]()
      matches.foldLeft(0 -> R0F0) { case (lastAbs @ (lastAbsId, lastAbsRF), m) =>
        val absIdString = m.group(AbsId)
        val signString = m.group(Sign)
        val relIdString = m.group(RelId)
        if (absIdString != null) {
          val id = java.lang.Long.parseLong(absIdString, 16).toInt
          val res = id -> extractRF(m, R0F0)
          buf += res
          if (id == 0) lastAbs else res
        } else {
          val id = lastAbsId + java.lang.Long.parseLong(signString + relIdString, 16).toInt
          buf += id -> extractRF(m, lastAbsRF)
          lastAbs
        }
      }
      buf.result()
    }
  }

}

object Main {

  implicit val fshDirRead: scopt.Read[Fsh.FshDirectoryId] =
    scopt.Read.reads(s => (Fsh.FshDirectoryId withName s).asInstanceOf[Fsh.FshDirectoryId])
  implicit val fshFormatRead: scopt.Read[Fsh.FshFormat] =
    scopt.Read.reads(s => (Fsh.FshFormat withName s).asInstanceOf[Fsh.FshFormat])

  val parser = new scopt.OptionParser[Config]("fshgen") {
    head("fshgen", BuildInfo.version)
    note("A command line tool for converting FSH files back and forth.\n")
    help("help") text ("prints this usage text")

    cmd("import")
      .action { (_, c) => c.copy(mode = Mode.Import) }
      .text("import PNG/BMP files as FSHs (and optionally OBJ files as S3D) into DBPF dat files")
      .children(
        opt[File]('o', "output").required().valueName("<file>").text("output dat file")
          .action { (f, c) => c.copy(outFile = f) }
          .validate { f => if (!f.isDirectory) success else failure("output must not be a directory") },

        opt[Unit]('f', "force").text("force overwriting existing dat file")
          .action { (_, c) => c.copy(force = true) },

        opt[Unit]('a', "append").text("append to existing dat file")
          .action { (_, c) => c.copy(append = true) },

        opt[Unit]('m', "mips-separate").text("create separate mipmaps")
          .action { (_, c) => c.copy(mipsSeparate = true) },

        opt[Unit]("hd").text("for HD textures, effectively scales first separate mipmap to 0.25 instead of 0.5, implies 'mips-separate' flag, also sets slice width and height to 256 if not specified otherwise")
          .action { (_, c) => c.copy(hd = true, mipsSeparate = true,
            sliceWidth = if (c.sliceWidth != 128) c.sliceWidth else 256,
            sliceHeight = if (c.sliceHeight != 128) c.sliceHeight else 256) },

        opt[Unit]('e', "mips-embedded").text("create embedded mipmaps")
          .action { (_, c) => c.copy(mipsEmbedded = true) },

        opt[Int]('N', "mips-number").text("number of mipmaps to generate, defaults to 4")
          .action { (n, c) => c.copy(mipsNumber = n) }
          .validate { n => if (n >= 0) success else failure("number of mipmaps needs to be positive") },

        opt[Unit]('s', "slice").text("slice images into rectangles, optionally compose different layers for same ID, optionally apply separate alpha mask (a/alpha), optionally generate sidewalk textures if sidewalk alpha is present (b/balpha)")
          .action { (_, c) => c.copy(slice = true) },

        opt[Int]("slice-width").text("the width of sliced images")
          .action { (n, c) => c.copy(sliceWidth = n) },

        opt[Int]("slice-height").text("the height of sliced images")
          .action { (n, c) => c.copy(sliceHeight = n) },

        opt[Int]('i', "iid-offset").text("offset of IIDs")
          .action { (off, c) => c.copy(iidOffset = off) },

        opt[String]("gid").text(f"alternative GID, defaults to 0x${Tgi.FshMisc.gid.get}%08X for textures and 0x${Tgi.S3dMaxis.gid.get}%08X for models")
          .action { (gid, c) => c.copy(gid = Some(java.lang.Long.decode(gid).toInt)) },

        opt[Unit]('D', "darken").text("darken textures for S3D models")
          .action { (_, c) => c.copy(darken = true) },

        opt[Unit]('B', "brighten").text("brighten textures for non-S3D use")
          .action { (_, c) => c.copy(brighten = true) },

        opt[Unit]('d', "background-dark").text("embed darkened sidewalk texture as background below transparency")
          .action { (_, c) => c.copy(backgroundDark = true) },

        opt[Unit]('b', "background-bright").text("embed sidewalk texture as background below transparency")
          .action { (_, c) => c.copy(backgroundBright = true) },

        opt[Fsh.FshFormat]('F', "format").text("encoding format, defaults to Dxt3. Allowed values: " + Fsh.FshFormat.values.mkString(", "))
          .action { (f, c) => c.copy(fshFormat = f) },

        opt[Fsh.FshDirectoryId]("dir").text("alternative FSH directory ID, defaults to G264. Allowed values: " + Fsh.FshDirectoryId.values.mkString(", "))
          .action { (d, c) => c.copy(fshDirId = d) },

        opt[Unit]("attach-name").text("attach filename to FSH")
          .action { (_, c) => c.copy(attachName = true) },

        opt[Unit]("alpha-separate").text("look for separate alpha files among files")
          .action { (_, c) => c.copy(alphaSeparate = true) },

        opt[Unit]("with-BAT-models").text("convert .obj files to S3D and include them")
          .action { (_, c) => c.copy(withBatModels = true) },

        opt[Unit]("no-flip-v").text("unless this option is set, direction of v-coordinates is by default reversed upon conversion from OBJ to S3D")
          .action { (_, c) => c.copy(noFlipV = true) },

        opt[Unit]("silent").text("do not indicate the progress")
          .action { (_, c) => c.copy(silent = true) },

        arg[File]("<file>...").unbounded().optional()
          .text("image files to import. If length is zero, the filenames are read from std.in.")
          .action { (f, c) => c.copy(inputFiles = c.inputFiles :+ f) }
      )

    cmd("export")
      .action { (_, c) => c.copy(mode = Mode.Export) }
      .text("export FSH files as PNGs from DBPF dat files")
      .children(
        opt[File]('o', "output").valueName("<file>").text("output directory for export. If absent, current directory is used.")
          .action { (f, c) => c.copy(outFile = f) }
          .validate { f => if (f.isDirectory) success else failure("output must be a directory and must exist") },

        opt[Unit]('f', "force").text("force overwriting existing image files")
          .action { (_, c) => c.copy(force = true) },

        opt[String]('p', "pattern").text("a regex pattern to filter the 8-digit hex representation of the IIDs, such as '.*[49ef]' or '57.*'")
          .action { (s, c) => c.copy(iidPatternString = s) },

        opt[Unit]("alpha-separate").text("export alpha channels as separate files")
          .action { (_, c) => c.copy(alphaSeparate = true) },

        opt[Unit]("silent").text("do not indicate the progress")
          .action { (_, c) => c.copy(silent = true) },

        arg[File]("<file>...").unbounded().optional()
          .text ("dat files to export FSHs from. If length is zero, the filenames are read from std.in.")
          .action { (f, c) => c.copy(inputFiles = c.inputFiles :+ f) }
      )

    checkConfig { c => c.mode match {
      case Mode.Import =>
        if (c.outFile != null && c.outFile.exists && !c.force && !c.append)
          failure(s"Output file ${c.outFile} already exists. Pass '-f' to force overwriting of existing file or '-a' for appending.")
        else if (c.brighten && c.darken)
          failure("cannot apply both 'darken' and 'brighten'")
        else if (c.backgroundBright && c.backgroundDark)
          failure("cannot apply both 'background-bright' and 'background-dark'")
        else if (c.brighten && c.backgroundBright)
          failure("background is applied first and would be brightened additionally, use 'background-dark' instead")
        else if (c.darken && c.backgroundDark)
          failure("background is applied first and would be darkened additionally, use 'background-bright' instead")
        else if (c.withBatModels && c.slice)
          failure("options 'with-BAT-models' and 'slice' are incompatible with each other")
        else if (c.withBatModels && c.alphaSeparate)
          failure("options 'with-BAT-models' and 'alpha-separate' are incompatible with each other")
        else success
      case Mode.Export => success
      case _ => failure("either 'import' or 'export' command required")
    }}
  }

  def collectInputFiles(config: Config): Config = {
    val files =
      if (!config.inputFiles.isEmpty)
        config.inputFiles
      else
        LazyList.continually(scala.io.StdIn.readLine()).takeWhile(_ != null).filter(_.nonEmpty).map(s => new File(s))
    val existingFiles = files flatMap { file =>
      if (!file.exists) {
        println(s"File $file does not exist and has been skipped")
        None
      } else Some(file)
    }
    config.copy(inputFiles = existingFiles)
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Config()).map(collectInputFiles).map { conf =>
      val model = new Model(conf)
      val (failures: Seq[Throwable], errFuture: Future[Unit]) =
        conf.mode match {
          case Mode.Import =>
            import io.github.memo33.scdbpf.strategy.throwExceptions
            import concurrent.ExecutionContext.Implicits.global
            val failuresBuilder = Seq.newBuilder[Throwable]
            val (entriesTry: Iterator[Try[DbpfEntry]], errFuture) = ParItr.map(model.collectImagesAndModels())(_.toRawEntry)
            val entries: Iterator[DbpfEntry] = entriesTry.flatMap {
              case Success(entry) => Some(entry)
              case Failure(err) => failuresBuilder += err; None
            }
            if (!conf.append || !conf.outFile.exists) {
              DbpfFile.write(entries, conf.outFile)
            } else {
              val dbpf = DbpfFile.read(conf.outFile)
              dbpf.write(dbpf.entries.iterator ++ entries) // lazy evaluation
            }
            (failuresBuilder.result(), errFuture)
          case Mode.Export =>
            model.`export`()
        }

      if (!conf.silent) println() // complete the line started by progressor
      if (failures.nonEmpty) {
        println(s"Encountered ${failures.length} errors:")
        failures.head.printStackTrace()
        System.exit(1)
      } else {
        import concurrent.ExecutionContext.Implicits.global
        errFuture.onComplete {
          case Success(_) => println("SUCCESS!")
          case Failure(err) =>
            println(s"Encountered 1 error:")
            err.printStackTrace()
            System.exit(3)
        }
      }
    } getOrElse {
      println("FAILED!")
      System.exit(2)
    }
  }
}
