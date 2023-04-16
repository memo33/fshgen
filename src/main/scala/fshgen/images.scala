package fshgen

import scdbpf.DbpfUtil._, RotFlip._
import ps.tricerato.pureimage.Image

abstract class ProxyImage[A](img: Image[A]) extends Image[A] {
  def height = img.height
  def width = img.width
}

private class DihOrientation(val x: Int, val y: Int)

object DihImage {
  def apply[A](img: Image[A], rf: RotFlip): DihImage[A] = img match {
    case di: DihImage[A] => new DihImage(di.img, di.rf * rf)
    case _ => new DihImage(img, rf)
  }
  implicit private object DihOrientationIsDihedral extends Dihedral[DihOrientation, Int] {
    def x(a: DihOrientation): Int = a.x
    def y(a: DihOrientation): Int = a.y
    def build(from: DihOrientation, x: Int, y: Int) = new DihOrientation(x, y)
  }
}

/** A dihedral image that allows for rotating and flipping. */
class DihImage[A] private (val img: Image[A], val rf: RotFlip) extends Image[A] {
  lazy val width = if (transposed) img.height else img.width
  lazy val height = if (transposed) img.width else img.height

  import DihImage.DihOrientationIsDihedral
  @inline private[this] lazy val orientation = new DihOrientation(1, -1) *: rf
  @inline private[this] def transposed = rf.rot % 2 != 0
  @inline private[this] def project(sign: Int) = (1 - sign) / 2 // 1 to 0, -1 to 1

  def apply(x: Int, y: Int): A = {
    if (!transposed) img(project( orientation.x) * (img.width-1) + orientation.x * x, project(-orientation.y) * (img.height-1) - orientation.y * y)
    else             img(project(-orientation.y) * (img.width-1) - orientation.y * y, project( orientation.x) * (img.height-1) + orientation.x * x)
  }

  def * (rf: RotFlip): DihImage[A] = DihImage(img, this.rf * rf)
}

