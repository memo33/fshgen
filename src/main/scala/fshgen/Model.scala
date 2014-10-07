package fshgen

import scdbpf._, Fsh._, DbpfUtil._, RotFlip._
import java.io.File
import ps.tricerato.pureimage._
import java.awt.image.BufferedImage
import scala.util.matching.Regex
import com.mortennobel.imagescaling.ResampleOp
import javax.imageio.ImageIO

import scala.language.implicitConversions
import Experimental.bufferedImageAsImage

class Model(val conf: Config) extends Import with Export {

  lazy val sidewalk = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x08200004.png")))
//  lazy val grass1 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C1004.png")))
//  lazy val grass2 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C2004.png")))
//  lazy val grass3 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C3004.png")))

  lazy val sidewalkDark = Curves.Darkening(sidewalk)
//  lazy val grass1Dark = Curves.Darkening(grass1)
//  lazy val grass2Dark = Curves.Darkening(grass2)
//  lazy val grass3Dark = Curves.Darkening(grass3)

  implicit def imageToBufferedImage(img: Image[RGBA]): BufferedImage = Experimental.imageToBufferedImage(img)

  def rgba(r: Int, g: Int, b: Int, a: Int): RGBA =
    RGBA((r & 0xff) | (g & 0xff) << 8 | (b & 0xff) << 16 | (a & 0xff) << 24)

  def Progressor[A](seq: Iterable[A]): Iterator[A] = {
    val it = seq.iterator
    if (conf.silent) it else {
      val num = seq.size max 1
      new scala.collection.AbstractIterator[A] {
        var count = 0
        def hasNext = it.hasNext
        private[this] def points(n: Int) = n * 80 / num
        def next = {
          count += 1
          for (_ <- points(count-1) until points(count)) print(".")
          it.next
        }
      }
    }
  }
}

trait Import { this: Model =>
  def imageAsAlpha(img: Image[RGBA]): Image[Gray] = new Image[Gray] {
    def height = img.height; def width = img.width
    def apply(x: Int, y: Int): Gray = {
      val p = img(x, y)
      Gray(p.red & 0xff)
    }
  }

  def embedBackground(img: Image[RGBA], bg: Image[RGBA]): Image[RGBA] = new ProxyImage(img) {
    require(img.width == bg.width && img.height == bg.height)
    def apply(x: Int, y: Int): RGBA = {
      val p = img(x, y); val q = bg(x, y)
      val a = p.alpha & 0xff
      rgba(
        ((p.red   & 0xff) * a + (q.red   & 0xff) * (255 - a)) / 255,
        ((p.green & 0xff) * a + (q.green & 0xff) * (255 - a)) / 255,
        ((p.blue  & 0xff) * a + (q.blue  & 0xff) * (255 - a)) / 255,
        a)
    }
  }

  def applyEmbedBackground(img: Image[RGBA]): Image[RGBA] = {
    if (conf.backgroundBright) embedBackground(img, sidewalk)
    else if (conf.backgroundDark) embedBackground(img, sidewalkDark)
    else img
  }

  def combineImageWithAlpha(img: Image[RGBA], alpha: Image[Gray]): Image[RGBA] = new ProxyImage(img) {
    assert(img.width == alpha.width, img.height == alpha.height)
    def apply(x: Int, y: Int): RGBA = RGBA(img(x, y).i & 0x00FFFFFF | alpha(x, y).i << 24)
  }

  def scaleHalf(img: BufferedImage): BufferedImage = {
    val resampleOp = new ResampleOp(img.getWidth / 2, img.getHeight / 2)
    resampleOp.filter(img, null)
  }

  def produceMips(bi: BufferedImage): Iterable[Image[RGBA]] = {
    if (conf.mipsEmbedded || conf.mipsSeparate) {
      Iterable.iterate(bi, conf.mipsNumber + 1 + (if (conf.hd) 1 else 0))(scaleHalf).tail map bufferedImageAsImage
    } else Iterable.empty
  }

  def applyFilter(img: Image[RGBA]): Image[RGBA] =
    if (conf.darken) Curves.Darkening(img) else if (conf.brighten) Curves.Brightening(img) else img

  def buildFshs(bi: BufferedImage, tgi: Tgi, label: Option[String]): Iterable[BufferedEntry[Fsh]] = {
    val mips = produceMips(bi) map applyFilter
    val img = applyFilter(bufferedImageAsImage(bi))
    val imgs = Iterable(img) ++ (if (conf.mipsEmbedded) mips else Iterable.empty)
    val elem = new FshElement(imgs, conf.fshFormat, label)
    val fsh = Fsh(Seq(elem), conf.fshDirId)
    if (!conf.mipsSeparate) {
      Iterable(BufferedEntry(tgi, fsh, compressed = true))
    } else {
      val descendingTgis = Iterator.iterate(tgi)(x => x.copy(iid = x.iid-1))
      val mipFshs = (if (conf.hd) mips.tail else mips) map (img => Fsh(Seq(new FshElement(Iterable(img), conf.fshFormat)), conf.fshDirId))
      (Iterable(fsh) ++ mipFshs) map (f => BufferedEntry(descendingTgis.next, f, compressed = true))
    }
  }

  def fileStem(file: File): String = {
    file.getName.lastIndexOf(".") match {
      case -1 => file.getName
      case idx => file.getName.substring(0, idx)
    }
  }

  def buildTgi(id: Int): Tgi = Tgi(Tgi.Fsh.tid.get, conf.gid, id + conf.iidOffset)

  def collectImages(): Iterable[BufferedEntry[Fsh]] = {
    val defaultId = Iterator.from(4, 0x100)
    def extractId(r: Regex, s: String): Option[Int] = r findFirstIn s map (id => java.lang.Long.parseLong(id, 16).toInt)

    val entries = if (!conf.alphaSeparate) {
      Progressor(conf.inputFiles).flatMap { f =>
        val (id, rf) = new Config.IdContext(f.getName).extractLastId getOrElse (defaultId.next, R0F0)
        val img = DihImage(applyEmbedBackground(ImageIO.read(f)), rf)
        buildFshs(img, buildTgi(id), if (conf.attachName) Some(fileStem(f)) else None)
      }
    } else {
      val alphas, colors = scala.collection.mutable.Map.empty[Int, (File, RotFlip)]
      for (f <- conf.inputFiles) {
        val idContext = new Config.IdContext(f.getName)
        val (id, rf) = idContext.extractLastId getOrElse (defaultId.next, R0F0)
        (if (idContext.isAlpha) alphas else colors)(id) = f -> rf
      }
      Progressor(colors).flatMap { case (id, (colFile, colRf)) =>
        val img = DihImage(ImageIO.read(colFile), colRf)
        val combined = alphas.get(id) match {
          case Some((alphaFile, alphaRf)) =>
            val alpha = DihImage(ImageIO.read(alphaFile), alphaRf)
            applyEmbedBackground(combineImageWithAlpha(img, imageAsAlpha(alpha)))
          case None => applyEmbedBackground(img)
        }
        buildFshs(combined, buildTgi(id), if (conf.attachName) Some(fileStem(colFile)) else None)
      }
    }
    entries.toIterable
  }
}

trait Export { this: Model =>
  def killAlpha(img: Image[RGBA]): Image[RGBA] = new ProxyImage(img) {
    def apply(x: Int, y: Int): RGBA = {
      val p = img(x, y)
      rgba(p.red, p.green, p.blue, 255)
    }
  }

  def onlyAlpha(img: Image[RGBA]): Image[RGBA] = new ProxyImage(img) {
    def apply(x: Int, y: Int): RGBA = {
      val p = img(x, y)
      rgba(p.alpha, p.alpha, p.alpha, 255)
    }
  }

  def export(): Unit = {
    import rapture.core.strategy.throwExceptions
    val singleFile = conf.inputFiles.lengthCompare(1) <= 0
    for {
      file <- if (singleFile) conf.inputFiles.iterator else Progressor(conf.inputFiles)
      dbpf = DbpfFile.read(file)
      e <- if (singleFile) Progressor(dbpf.entries) else dbpf.entries
      if e.tgi matches Tgi.Fsh
      hexId = f"${e.tgi.iid}%08x" if conf.iidPattern.matcher(hexId).matches
      fsh = e.toBufferedEntry.convert[Fsh].content
      (elem, i) <- fsh.elements zip (Stream from 0)
      (image, j) <- elem.images zip (Stream from 0)
      (img, prefix) <-
        if (!conf.alphaSeparate) Seq((image, hexId))
        else Seq((killAlpha(image), hexId), (onlyAlpha(image), hexId + "_a"))
      name = prefix +
        (if (fsh.elements.tail.nonEmpty) "_" + i else "") +
        (if (elem.images.tail.nonEmpty) "_" + j else "")
      target = new File(conf.outFile, name + ".png") if conf.force || !target.exists
    } {
      ImageIO.write(img, "png", target)
    }
  }
}
