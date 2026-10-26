package io.github.skrtk98.iroha.engine.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class SegmentRulesTest {
    private val names = listOf(
        "BOS/EOS,*,*,*,*,*,*",
        "名詞,一般,*,*,*,*,*",
        "助詞,格助詞,一般,*,*,*,が",
        "接頭詞,名詞接続,*,*,*,*,*",
        "記号,句点,*,*,*,*,*",
    )

    private val rules = SegmentRules.fromMozc(
        names,
        listOf(
            "接頭詞,名詞接続 * false",
            "* 記号,(句点|読点) true",
            "* ^助詞 false",
        ),
        listOf(
            "PREFIX 助詞,格助詞 3000",
            "SUFFIX 接頭詞, 5000",
        ),
    )

    @Test
    fun firstMatchingRuleDecides() {
        assertFalse(rules.isBoundary(rid = 1, lid = 2)) // 名詞 + 助詞
        assertFalse(rules.isBoundary(rid = 3, lid = 1)) // 接頭詞 + 名詞
        assertFalse(rules.isBoundary(rid = 3, lid = 4)) // 接頭詞の規則が先に当たる
        assertTrue(rules.isBoundary(rid = 1, lid = 4)) // 句点の前
        assertTrue(rules.isBoundary(rid = 1, lid = 1)) // どれにも当たらなければ区切る
        assertTrue(rules.isBoundary(rid = 0, lid = 2)) // 文頭
    }

    @Test
    fun penaltiesFollowThePosNames() {
        assertEquals(listOf(0, 0, 3000, 0, 0), rules.prefixPenalty.toList())
        assertEquals(listOf(0, 0, 0, 5000, 0), rules.suffixPenalty.toList())
    }

    @Test
    fun rulesSurviveTheDictionaryFile() {
        val out = ByteArrayOutputStream()
        val classes = names.map(PosClass::fromMozcPosName)
        DictionaryBuilder(classes, IntArray(25), 5, 1, segmentRules = rules)
            .write(listOf(WordEntry("ねこ", "猫", 1, 1, 3000)), out)
        val dict = SystemDictionary(ByteBuffer.wrap(out.toByteArray()))
        for (rid in names.indices) for (lid in names.indices) {
            assertEquals("$rid $lid", rules.isBoundary(rid, lid), dict.isBoundary(rid, lid))
        }
        assertEquals(3000, dict.prefixPenalty(2))
        assertEquals(5000, dict.suffixPenalty(3))
        assertEquals("猫", dict.exactSearch("ねこ").single().surface)
    }
}
