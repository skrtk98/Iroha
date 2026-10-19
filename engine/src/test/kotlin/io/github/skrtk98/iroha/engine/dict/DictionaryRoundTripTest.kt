package io.github.skrtk98.iroha.engine.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class DictionaryRoundTripTest {
    private val entries = listOf(
        WordEntry("かんじ", "漢字", 1, 1, 3000),
        WordEntry("かんじ", "感じ", 1, 1, 3500),
        WordEntry("かんじ", "カンジ", 1, 1, 8000),
        WordEntry("かん", "缶", 1, 1, 5000),
        WordEntry("か", "か", 2, 3, 100),
        WordEntry("かんじゃ", "患者", 1, 1, 4000),
        WordEntry("あ", "亜", 1, 1, 6000),
    ) + (1..100).map { WordEntry("てすと$it", "テスト$it", 1, 1, 7000 + it) }

    private fun build(): SystemDictionary {
        val posClasses = listOf(PosClass.Content, PosClass.Content, PosClass.Functional, PosClass.Functional)
        val matrix = IntArray(16) { it * 100 }
        val out = ByteArrayOutputStream()
        DictionaryBuilder(posClasses, matrix, 4, 1).write(entries, out)
        return SystemDictionary(ByteBuffer.wrap(out.toByteArray()))
    }

    @Test
    fun exactSearchReturnsAllEntriesSortedByCost() {
        val dict = build()
        assertEquals(listOf("漢字", "感じ", "カンジ"), dict.exactSearch("かんじ").map { it.surface })
        assertEquals(3, dict.exactSearch("か").single().rid)
        assertTrue(dict.exactSearch("かんじは").isEmpty())
        assertTrue(dict.exactSearch("ん").isEmpty())
    }

    @Test
    fun commonPrefixSearchFindsEveryPrefix() {
        val dict = build()
        val readings = dict.commonPrefixSearch("かんじゃさん", 0).map { it.reading }.distinct()
        assertEquals(listOf("か", "かん", "かんじ", "かんじゃ"), readings)
    }

    @Test
    fun predictiveSearchCrossesBlocks() {
        val dict = build()
        val found = ArrayList<String>()
        dict.predictiveSearch("てすと", 1000) { found.add(it.surface) }
        assertEquals(100, found.size)
    }

    @Test
    fun connectionCostIsQuantized() {
        val dict = build()
        assertEquals(dict.connectionCost(1, 2) / 64, (600 + 32) / 64)
    }
}
