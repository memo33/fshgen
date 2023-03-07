package fshgen

import ps.tricerato.pureimage._

object Curves {

  trait Curve {
    val red, green, blue: Array[Int]
    def apply(img: Image[RGBA]): Image[RGBA] = new Image[RGBA] {
      def width = img.width; def height = img.height
      def apply(x: Int, y: Int) = {
        val p = img(x, y)
        RGBA((p.alpha & 0xff) << 24 |
          blue(p.blue & 0xff) << 16 |
          green(p.green & 0xff) << 8 |
          red(p.red & 0xff))
      }
    }
  }

  private def buckets(oldMax: Int, newMax: Int): Seq[Int] = {
    val numBuckets = (oldMax - newMax).abs + 1
    (0 to numBuckets).map(a => (a.toFloat * (oldMax+1) / numBuckets).round)
  }

  private def darkenColor(oldMax: Int, newMax: Int): Seq[Int] = {
    // Distributes color shift evenly (and symmetrically) on the whole spectrum,
    // by dividing the spectrum into nearly-same-sized buckets.
    require(newMax < oldMax)
    val bs = buckets(oldMax, newMax)
    val res = bs.zip(bs.tail).zipWithIndex.flatMap { case ((a,b),c) => Seq.fill(b-a)(c) } .zipWithIndex.map { case (c,i) => i-c }
    require(res.zip(res.reverse).map { case (a,b) => a+b } .toSet.size == 1)
    res
  }

  private def brightenColor(oldMax: Int, newMax: Int): Seq[Int] = {
    require(newMax > oldMax)
    val bs = buckets(oldMax, newMax)
    bs.zip(bs.tail).zipWithIndex.flatMap { case ((a,b),c) => Seq.fill(b-a)(c) } .zipWithIndex.map { case (c,i) => i+c } ++ Seq.fill(newMax-oldMax)(newMax)
  }

  object Darkening extends Curve {
    val red = darkenColor(0xff, 0xde).toArray
    val green = darkenColor(0xff, 0xdb).toArray
    val blue = darkenColor(0xff, 0xdd).toArray
  }

  object Brightening extends Curve {
    val red = brightenColor(Darkening.red.last, 0xff).toArray
    val green = brightenColor(Darkening.green.last, 0xff).toArray
    val blue = brightenColor(Darkening.blue.last, 0xff).toArray
  }
}
