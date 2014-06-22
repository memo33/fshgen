package fshgen

import scdbpf._, Fsh._
import java.io.File
import ps.tricerato.pureimage._
import java.awt.image.BufferedImage
import scala.util.matching.Regex
import com.mortennobel.imagescaling.ResampleOp
import javax.imageio.ImageIO

import scala.language.implicitConversions

class Model(conf: Config) {

  lazy val sidewalk = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x08200004.png")))
  lazy val grass1 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C1004.png")))
  lazy val grass2 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C2004.png")))
  lazy val grass3 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C3004.png")))

  lazy val sidewalkDark = Curves.Darkening(sidewalk)
  lazy val grass1Dark = Curves.Darkening(grass1)
  lazy val grass2Dark = Curves.Darkening(grass2)
  lazy val grass3Dark = Curves.Darkening(grass3)

  def argbToRGBA(argb: Int): RGBA = {
    RGBA(
       argb & 0xFF000000        | // alpha
      (argb & 0x00FF0000) >> 16 | // red
       argb & 0x0000FF00        | // green
      (argb & 0x000000FF) << 16   // blue
    )
  }

  implicit def imageToBufferedImage(img: Image[RGBA]): BufferedImage = {
    val w = img.width
    val h = img.height
    val arr = new Array[Int](img.width * img.height)
    for (i <- 0 until h; j <- 0 until w) {
      val x: RGBA = img(j, i)
      arr(i * w + j) = (x.alpha & 0xFF) << 24 | (x.red & 0xFF) << 16 | (x.green & 0xFF) << 8 | (x.blue & 0xFF)
    }
    val bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    bi.setRGB(0, 0, w, h, arr, 0, w)
    bi
  }

  implicit def bufferedImageAsImage(img: BufferedImage): Image[RGBA] = new Image[RGBA] {
    def height = img.getHeight
    def width = img.getWidth
    def apply(x: Int, y: Int): RGBA = argbToRGBA(img.getRGB(x, y))
  }

  def imageAsAlpha(img: Image[RGBA]): Image[Gray] = new Image[Gray] {
    def height = img.height
    def width = img.width
    def apply(x: Int, y: Int): Gray = {
      val p = img(x, y)
      Gray(Seq(p.alpha & 0xff, p.red & 0xff, p.green & 0xff, p.blue & 0xff).max)
    }
  }

  def rgba(r: Int, g: Int, b: Int, a: Int): RGBA =
    RGBA((r & 0xff) | (g & 0xff) << 8 | (b & 0xff) << 16 | (a & 0xff) << 24)

  def embedBackground(img: Image[RGBA], bg: Image[RGBA]): Image[RGBA] = new Image[RGBA] {
    require(img.width == bg.width && img.height == bg.height)
    def width = bg.width
    def height = bg.height
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

  def applyEmbedBackgroundBI(bi: BufferedImage): BufferedImage = applyEmbedBackground(bi)

  def combineImageWithAlpha(img: Image[RGBA], alpha: Image[Gray]): Image[RGBA] = new Image[RGBA] {
    assert(img.width == alpha.width, img.height == alpha.height)
    def height = img.height
    def width = img.width
    def apply(x: Int, y: Int): RGBA = RGBA(img(x, y).i & 0x00FFFFFF | alpha(x, y).i << 24)
  }

  def scaleHalf(img: BufferedImage): BufferedImage = {
    val resampleOp = new ResampleOp(img.getWidth / 2, img.getHeight / 2)
    resampleOp.filter(img, null)
  }

  def produceMips(bi: BufferedImage): Iterable[Image[RGBA]] = {
    if (conf.mipsEmbedded || conf.mipsSeparate) {
      Iterable.iterate(bi, conf.mipsNumber + 1)(scaleHalf).tail map bufferedImageAsImage
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
      val mipFshs = mips map (img => Fsh(Seq(new FshElement(Iterable(img), conf.fshFormat)), conf.fshDirId))
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

  def collectImages(): Iterable[BufferedEntry[Fsh]] = {
    val defaultId = Iterator.from(4, 0x100)
    def extractId(r: Regex, s: String): Option[Int] = r findFirstIn s map (id => java.lang.Long.parseLong(id, 16).toInt)

    val entries = if (!conf.alphaSeparate) {
      Progressor(conf.inputFiles).flatMap { f =>
        val id = extractId(conf.pattern, f.getName) getOrElse (defaultId.next)
        val bi = applyEmbedBackgroundBI(ImageIO.read(f))
        buildFshs(bi, buildTgi(id), if (conf.attachName) Some(fileStem(f)) else None)
      }
    } else {
      val alphas, colors = scala.collection.mutable.Map.empty[Int, File]
      for (f <- conf.inputFiles) {
        val alphaId = extractId(conf.alphaPattern, f.getName)
        val id = if (alphaId.isDefined) alphaId.get else extractId(conf.pattern, f.getName) getOrElse (defaultId.next)
        (if (alphaId.isDefined) alphas else colors)(id) = f
      }
      Progressor(colors).flatMap { case (id, colFile) =>
        val alphaFile = alphas.get(id)
        val bi = ImageIO.read(colFile)
        val biCombined: BufferedImage = if (alphaFile.isEmpty)
          applyEmbedBackgroundBI(bi)
        else {
          val biAlpha = ImageIO.read(alphaFile.get)
          applyEmbedBackground(combineImageWithAlpha(bufferedImageAsImage(bi), imageAsAlpha(bufferedImageAsImage(biAlpha))))
        }
        buildFshs(biCombined, buildTgi(id), if (conf.attachName) Some(fileStem(colFile)) else None)
      }
    }
    entries.toIterable
  }
}
