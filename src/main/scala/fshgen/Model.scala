package io.github.memo33.fshgen

import io.github.memo33.scdbpf._, Fsh._, DbpfUtil._, RotFlip._
import java.io.File
import io.github.memo33.scdbpf.compat.{Image, RGBA, Gray}
import java.awt.image.BufferedImage
import scala.util.matching.Regex
import com.mortennobel.imagescaling.ResampleOp
import javax.imageio.ImageIO
import scala.collection.mutable
import scala.collection.immutable.SortedSet

import scala.language.implicitConversions
import Experimental.bufferedImageAsImage
import Config.IdContext

class Model(val conf: Config) extends Import with Export {

  lazy val sidewalk = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x08200004.png")))
  lazy val grass1 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C1004.png")))
  lazy val grass2 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C2004.png")))
  lazy val grass3 = bufferedImageAsImage(ImageIO.read(ClassLoader.getSystemResourceAsStream("textures/0x251C3004.png")))
  lazy val grass = List(grass1, grass2, grass3)

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
        private def points(n: Int) = n * 80 / num
        def next() = {
          count += 1
          for (_ <- points(count-1) until points(count)) print(".")
          it.next()
        }
      }
    }
  }
}

trait FileMatching { this: Model =>

  private class LazyImageHolder(file: File, context: IdContext) {
    private var ids: Set[Int] = context.extractAllIds.map(_._1).toSet - 0
    private var img: Image[RGBA] = null
    var destroyed = false

    def image: Image[RGBA] = {
      require(!destroyed)
      if (img == null) {
        img = ImageIO.read(file)
      }
      img
    }
    def release(id: Int) = {
      ids -= id
      if (ids.isEmpty) {
        img = null
        destroyed = true
      }
    }
    lazy val subimages: LazyList[Image[RGBA]] = {
      require(image.height % conf.sliceHeight == 0 && image.width % conf.sliceWidth == 0,
        s"dimensions (width ${image.width}, height ${image.height}) of file $file are not" +
        s"multiples of slice width (${conf.sliceWidth}) and height (${conf.sliceHeight})")
      for {
        yOff <- (0 until image.height by conf.sliceWidth).to(LazyList)
        xOff <- 0 until image.width by conf.sliceHeight
      } yield new Image[RGBA] {
        def width = conf.sliceWidth
        def height = conf.sliceHeight
        def apply(x: Int, y: Int): RGBA = image(xOff + x, yOff + y)
      }
    }
  }

  private lazy val contexts: Map[File, IdContext] =
    conf.inputFiles.iterator.map(f => f -> new IdContext(f.getName)).toMap
  private lazy val ids: Seq[Int] = (conf.inputFiles.map(contexts).flatMap(_.extractAllIds).map(_._1)).distinct
  private lazy val filenameOrdering = Ordering.by[File, String](fileStem(_).toLowerCase)
  private lazy val srcFiles: scala.collection.Map[Int, SortedSet[File]] = {
    val m = mutable.Map.empty[Int, SortedSet[File]]
    for ((f, idc) <- contexts; (id, _) <- idc.extractAllIds) {
      m(id) = m.getOrElse(id, SortedSet.empty(filenameOrdering)) + f
    }
    m
  }
  private lazy val lazyImages: Map[File, LazyImageHolder] =
    contexts map { case (f, idc) => f -> new LazyImageHolder(f, idc) }

  def buildSlicedEntries: Iterator[BufferedEntry[Fsh]] = {
    Progressor(ids) flatMap { id => if (id == 0) Seq.empty else {
      val layersWithContexts: Seq[(DihImage[RGBA], IdContext)] =
        srcFiles(id).toSeq flatMap { file =>
          val lazyImg = lazyImages(file)
          val context = contexts(file)
          context.extractAllIds.zip(lazyImg.subimages).collect {
            case ((i, rf), img) if i == id => DihImage(img, rf) -> context
          }
        }
      // TODO processing here, stack layers, add alphas, etc.
      val colorLayers: List[Image[RGBA]] =
        layersWithContexts.iterator.collect { case (img, context) if context.isColor => img }.toList
      val result = if (colorLayers.nonEmpty) {
        val combinedLayers: Image[RGBA] = colorLayers.tail.foldLeft(colorLayers.head) {
          case (bottom, top) => overlay(top, bottom)
        }
        val alphaOpt = layersWithContexts find (_._2.isAlpha) map (x => imageAsAlpha(x._1))
        val sidewalkAlphaOpt = layersWithContexts find (_._2.isSidewalkAlpha) map (x => imageAsAlpha(x._1))

        val mainTexture: Image[RGBA] = if (alphaOpt.isEmpty) {
          applyEmbedBackground(combinedLayers)
        } else {
          applyEmbedBackground(combineImageWithAlpha(combinedLayers, alphaOpt.get))
        }
        val sidewalkTextures = sidewalkAlphaOpt.toList.flatMap { alpha =>
          // grass.map(g => killInvisibleBackground(combineImageWithAlpha(embedBackgroundWhereTransparent(combinedLayers, g), alpha)))
          // The following should better handle semitransparent pixels between overlay and grass, avoiding black out.
          grass.map(g => killInvisibleBackground(applyEmbedBackground(combineImageWithAlpha(overlay(combinedLayers, g), alpha))))
        }
        (mainTexture :: sidewalkTextures).zipWithIndex flatMap { case (img, i) =>
          buildFshs(img, buildTgi(id + i * 0x10), if (conf.attachName && i == 0) Some(fileStem(srcFiles(id).head)) else None)
        }
      } else Seq.empty
      // release processed files
      srcFiles(id) foreach { file => lazyImages(file).release(id) }
      result
    }}
  }
}

trait Import extends FileMatching { this: Model =>
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

  def embedBackgroundWhereTransparent(img: Image[RGBA], bg: Image[RGBA]): Image[RGBA] = new ProxyImage(img) {
    require(img.width == bg.width && img.height == bg.height)
    def apply(x: Int, y: Int): RGBA = {
      val p = img(x, y); val a = p.alpha & 0xff
      if (a != 0) RGBA(p.i | 0xff000000)
      else RGBA(bg(x, y).i | 0xff000000)
    }
  }

  def killInvisibleBackground(img: Image[RGBA]): Image[RGBA] = new ProxyImage(img) {
    def apply(x: Int, y: Int): RGBA = {
      val p = img(x, y)
      if (p.alpha != 0) p else RGBA(0)
    }
  }

  def overlay(top: Image[RGBA], bottom: Image[RGBA]): Image[RGBA] = new ProxyImage(bottom) {
    require(top.width == bottom.width && top.height == bottom.height)
    def apply(x: Int, y: Int): RGBA = {
      val p = top(x, y);      val q = bottom(x, y)
      val a = p.alpha & 0xff; val b = q.alpha & 0xff
      val c = (a * 255 + b * (255 - a)) / 255
      if (c == 0) RGBA(0) else {
        rgba(
          ((p.red   & 0xff) * a * 255 + (q.red   & 0xff) * b * (255 - a)) / 255 / c,
          ((p.green & 0xff) * a * 255 + (q.green & 0xff) * b * (255 - a)) / 255 / c,
          ((p.blue  & 0xff) * a * 255 + (q.blue  & 0xff) * b * (255 - a)) / 255 / c,
          c)
      }
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
      (Iterable(fsh) ++ mipFshs).map(f => BufferedEntry(descendingTgis.next(), f, compressed = true))
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
    val entries = if (conf.slice) {
      buildSlicedEntries
    } else if (!conf.alphaSeparate) {
      Progressor(conf.inputFiles).flatMap { f =>
        val (id, rf) = new IdContext(f.getName).extractLastId.getOrElse(defaultId.next(), R0F0)
        val img = DihImage(applyEmbedBackground(ImageIO.read(f)), rf)
        buildFshs(img, buildTgi(id), if (conf.attachName) Some(fileStem(f)) else None)
      }
    } else {
      val alphas, colors = scala.collection.mutable.Map.empty[Int, (File, RotFlip)]
      for (f <- conf.inputFiles) {
        val idContext = new IdContext(f.getName)
        val (id, rf) = idContext.extractLastId.getOrElse(defaultId.next(), R0F0)
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
    entries.to(Iterable)
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

  def `export`(): Unit = {
    import io.github.memo33.scdbpf.strategy.throwExceptions
    val singleFile = conf.inputFiles.lengthCompare(1) <= 0
    val iter = for {
      file <- if (singleFile) conf.inputFiles.iterator else Progressor(conf.inputFiles)
      dbpf = DbpfFile.read(file)
      e <- if (singleFile) Progressor(dbpf.entries) else dbpf.entries.iterator
      if e.tgi matches Tgi.Fsh
      hexId = f"${e.tgi.iid}%08x" if conf.iidPattern.matcher(hexId).matches
      fsh = e.toBufferedEntry.convert[Fsh].content
      (elem, i) <- fsh.elements zip (LazyList.from(0))
      (image, j) <- elem.images zip (LazyList.from(0))
      (img, prefix) <-
        if (!conf.alphaSeparate) Seq((image, hexId))
        else Seq((killAlpha(image), hexId), (onlyAlpha(image), hexId + "_a"))
      name = prefix +
        (if (fsh.elements.lengthCompare(1) > 0) "_" + i else "") +
        (if (elem.images.tail.nonEmpty) "_" + j else "")
      target = new File(conf.outFile, name + ".png") if conf.force || !target.exists
    } yield (img, target)
    import concurrent.ExecutionContext.Implicits.global
    ParItr.map(iter) {
      case (img, target) => ImageIO.write(img, "png", target)
    } foreach { _ => /*consume*/ }
  }
}
