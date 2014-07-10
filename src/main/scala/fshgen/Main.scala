package fshgen

import scala.util.matching.Regex
import java.util.regex.Pattern
import java.io.File
import scdbpf._


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
  iidOffset: Int = 0,
  gid: Int = Tgi.FshMisc.gid.get,
  darken: Boolean = false,
  brighten: Boolean = false,
  backgroundBright: Boolean = false,
  backgroundDark: Boolean = false,
  attachName: Boolean = false,
  alphaSeparate: Boolean = false,
  fshDirId: Fsh.FshDirectoryId = Fsh.FshDirectoryId.G264,
  fshFormat: Fsh.FshFormat = Fsh.FshFormat.Dxt3,
  silent: Boolean = false,
  pattern: Regex = Config.defaultPattern,
  alphaPattern: Regex = Config.defaultAlphaPattern,
  iidPatternString: String = ".*"
) {
  lazy val iidPattern: Pattern = Pattern.compile(iidPatternString, Pattern.CASE_INSENSITIVE)
}
object Config {
  val hexId = """(0[xX])?\p{XDigit}{8}"""
  val hexAlpha = hexId + """(?=[ _](a|alpha)[\W_])"""
  val defaultPattern = s"(?!.+$hexId)$hexId".r
  val defaultAlphaPattern = s"(?!.+$hexAlpha)$hexAlpha".r
}

object Main extends App {

  implicit val fshDirRead: scopt.Read[Fsh.FshDirectoryId] =
    scopt.Read.reads(s => (Fsh.FshDirectoryId withName s).asInstanceOf[Fsh.FshDirectoryId])
  implicit val fshFormatRead: scopt.Read[Fsh.FshFormat] =
    scopt.Read.reads(s => (Fsh.FshFormat withName s).asInstanceOf[Fsh.FshFormat])

  val parser = new scopt.OptionParser[Config]("fshgen") {
    head("fshgen", "0.1.1")
    note("A command line tool for converting FSH files back and forth.\n")
    help("help") text ("prints this usage text")

    cmd("import")
      .action { (_, c) => c.copy(mode = Mode.Import) }
      .text("import PNG/BMP files as FSHs into DBPF dat files")
      .children(
        opt[File]('o', "output") required() valueName("<file>") text ("output dat file")
          action { (f, c) => c.copy(outFile = f) }
          validate { f => if (!f.isDirectory) success else failure("output must not be a directory") },

        opt[Unit]('f', "force") text ("force overwriting existing dat file")
          action { (_, c) => c.copy(force = true) },

        opt[Unit]('a', "append") text ("append to existing dat file")
          action { (_, c) => c.copy(append = true) },

        opt[Unit]('m', "mips-separate") text ("create separate mipmaps")
          action { (_, c) => c.copy(mipsSeparate = true) },

        opt[Unit]('e', "mips-embedded") text ("create embedded mipmaps")
          action { (_, c) => c.copy(mipsEmbedded = true) },

        opt[Int]('N', "mips-number") text ("number of mipmaps to generate, defaults to 4")
          action { (n, c) => c.copy(mipsNumber = n) }
          validate { n => if (n >= 0) success else failure("number of mipmaps needs to be positive") },

        opt[Int]('i', "iid-offset") text ("offset of IIDs")
          action { (off, c) => c.copy(iidOffset = off) },

        opt[String]("gid") text (f"alternative GID, defaults to 0x${Tgi.FshMisc.gid.get}%08X")
          action { (gid, c) => c.copy(gid = java.lang.Long.decode(gid).toInt) },

        opt[Unit]('D', "darken") text ("darken textures for S3D models")
          action { (_, c) => c.copy(darken = true) },

        opt[Unit]('B', "brighten") text ("brighten textures for non-S3D use")
          action { (_, c) => c.copy(brighten = true) },

        opt[Unit]('d', "background-dark") text ("embed darkened sidewalk texture as background below transparency")
          action { (_, c) => c.copy(backgroundDark = true) },

        opt[Unit]('b', "background-bright") text ("embed sidewalk texture as background below transparency")
          action { (_, c) => c.copy(backgroundBright = true) },

        opt[Fsh.FshFormat]('F', "format") text("encoding format, defaults to Dxt3. Allowed values: " + Fsh.FshFormat.values.mkString(", "))
          action { (f, c) => c.copy(fshFormat = f) },

        opt[Fsh.FshDirectoryId]("dir") text ("alternative FSH directory ID, defaults to G264. Allowed values: " + Fsh.FshDirectoryId.values.mkString(", "))
          action { (d, c) => c.copy(fshDirId = d) },

        opt[Unit]("attach-name") text ("attach filename to FSH")
          action { (_, c) => c.copy(attachName = true) },

        opt[Unit]("alpha-separate") text ("look for separate alpha files among files")
          action { (_, c) => c.copy(alphaSeparate = true) },

        opt[Unit]("silent") text ("do not indicate the progress")
          action { (_, c) => c.copy(silent = true) },

        arg[File]("<file>...") unbounded() optional()
          text ("image files to import. If length is zero, the filenames are read from std.in.")
          action { (f, c) => c.copy(inputFiles = c.inputFiles :+ f) },

        checkConfig { c =>
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
          else success
        }
      )
    cmd("export")
      .action { (_, c) => c.copy(mode = Mode.Export) }
      .text("export FSH files as PNGs from DBPF dat files")
      .children(
        opt[File]('o', "output") valueName("<file>") text ("output directory for export. If absent, current directory is used.")
          action { (f, c) => c.copy(outFile = f) }
          validate { f => if (f.isDirectory) success else failure("output must be a directory and must exist") },

        opt[Unit]('f', "force") text ("force overwriting existing image files")
          action { (_, c) => c.copy(force = true) },

        opt[String]('p', "pattern") text ("a regex pattern to filter the 8-digit hex representation of the IIDs, such as '.*[49ef]' or '57.*'")
          action { (s, c) => c.copy(iidPatternString = s) },

        opt[Unit]("alpha-separate") text ("export alpha channels as separate files")
          action { (_, c) => c.copy(alphaSeparate = true) },

        opt[Unit]("silent") text ("do not indicate the progress")
          action { (_, c) => c.copy(silent = true) },

        arg[File]("<file>...") unbounded() optional()
          text ("dat files to export FSHs from. If length is zero, the filenames are read from std.in.")
          action { (f, c) => c.copy(inputFiles = c.inputFiles :+ f) }

      )
    checkConfig { c => if (c.mode != null) success else failure("either 'import' or 'export' command required") }
  }

  def collectInputFiles(config: Config): Config = {
    val files =
      if (!config.inputFiles.isEmpty)
        config.inputFiles
      else
        Stream continually scala.io.StdIn.readLine takeWhile (_ != null) filter (_.nonEmpty) map (s => new File(s))
    val existingFiles = files flatMap { file =>
      if (!file.exists) {
        println(s"File $file does not exist and has been skipped")
        None
      } else Some(file)
    }
    config.copy(inputFiles = existingFiles)
  }

  parser.parse(args, Config()) map collectInputFiles map { conf =>
    val model = new Model(conf)
    conf.mode match {
      case Mode.Import =>
        val entries = model.collectImages()
        import rapture.core.strategy.throwExceptions
        if (!conf.append || !conf.outFile.exists) {
          DbpfFile.write(entries, conf.outFile)
        } else {
          val dbpf = DbpfFile.read(conf.outFile)
          dbpf.write(dbpf.entries.toStream ++ entries) // lazy evaluation
        }
      case Mode.Export =>
        model.export()
    }
    if (!conf.silent) println() // complete the line started by progressor
    println("SUCCESS!")
  } getOrElse {
    println("FAILED!")
  }
}
