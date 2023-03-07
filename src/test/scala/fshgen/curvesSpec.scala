package fshgen

import org.scalatest.{WordSpec, Matchers}
import Curves._

class CurvesSpec extends WordSpec with Matchers {

  "Brightening" should {
    "be invertible" in {
      def check(bright: Array[Int], dark: Array[Int]): Boolean = {
        (0 to dark.last).forall(i => dark(bright(i)) == i)
      }
      check(Brightening.red, Darkening.red) shouldBe true
      check(Brightening.green, Darkening.green) shouldBe true
      check(Brightening.blue, Darkening.blue) shouldBe true
    }
  }
  "Darkening" should {
    "be invertible on extremes" in {
      def check(bright: Array[Int], dark: Array[Int]): Boolean = {
        bright(dark(0)) == 0 && bright(dark(255)) == 255
      }
      check(Brightening.red, Darkening.red) shouldBe true
      check(Brightening.green, Darkening.green) shouldBe true
      check(Brightening.blue, Darkening.blue) shouldBe true
    }
  }
  "Curves" should {
    "have correct length" in {
      for (c <- Seq(Darkening, Brightening)) {
        c.red.length shouldBe 256
        c.green.length shouldBe 256
        c.blue.length shouldBe 256
      }
    }
  }
}
