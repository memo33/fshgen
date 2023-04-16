package fshgen

import org.scalatest.{WordSpec, Matchers}
import ps.tricerato.pureimage.{Image}
import scdbpf._, DbpfUtil._, RotFlip._

class ImagesSpec extends WordSpec with Matchers {

  case class SeqImg(pixels: Seq[Int], width: Int) extends Image[Int] {
    def height = pixels.length / width
    def apply(x: Int, y: Int): Int = {
      assert(0 <= x && x < width && 0 <= y && y < height)
      pixels(y * width + x)
    }
  }
  def toSeqImg(img: Image[Int]): SeqImg =
    SeqImg((0 until img.height) flatMap (y => 0 until img.width map (x => img(x,y))), img.width)

  "DihImage" should {
    "rotate and flip correctly" in {
      val img = DihImage(SeqImg(Seq(0,1,2,3,4,5,6,7), 4), R0F0)

      toSeqImg(img * R1F0) should be (SeqImg(Seq(4,0,5,1,6,2,7,3), 2))
      toSeqImg(img * R2F0) should be (SeqImg(Seq(7,6,5,4,3,2,1,0), 4))
      toSeqImg(img * R3F0) should be (SeqImg(Seq(3,7,2,6,1,5,0,4), 2))
      toSeqImg(img * R0F0) should be (SeqImg(Seq(0,1,2,3,4,5,6,7), 4))
      toSeqImg(img * R0F1) should be (SeqImg(Seq(3,2,1,0,7,6,5,4), 4))
      toSeqImg(img * R1F1) should be (SeqImg(Seq(0,4,1,5,2,6,3,7), 2))
    }
  }

}
