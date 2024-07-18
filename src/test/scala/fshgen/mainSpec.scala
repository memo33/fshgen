package io.github.memo33.fshgen

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import Config.IdContext
import io.github.memo33.scdbpf.DbpfUtil._, RotFlip._

class MainSpec extends AnyWordSpec with Matchers {

  "IdContext" should {
    val s = "0x1a001000-0-1___+100_0_+0x200 +300-2-1    2a001000_0_2a002000-2-0_-e00-1-0_-0x100_b_somemoretextandnumbers_1_2_3.png"
    val context = new IdContext(s)
    "allow extraction of single absolute ID" in {
      context.extractLastId shouldBe Some(0x2a002000 -> R2F0)
    }
    "allow extraction of multiple IDs" in {
      context.extractAllIds shouldBe Seq(
        0x1a001000 -> R0F1,
        0x1a001100 -> R0F1,
        0          -> R0F0,
        0x1a001200 -> R0F1,
        0x1a001300 -> R2F1,
        0x2a001000 -> R0F0,
        0          -> R0F0,
        0x2a002000 -> R2F0,
        0x2a001200 -> R3F0, // sic! rf must be inverted
        0x2a001f00 -> R2F0)
    }
    "should detect alphas" in {
      context.isAlpha shouldBe false
      context.isSidewalkAlpha shouldBe true
    }
  }
}
