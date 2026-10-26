package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NumberVariantsTest {
    @Test
    fun readsMixedNumbers() {
        assertEquals(3000L, NumberVariants.parse("3千"))
        assertEquals(3000L, NumberVariants.parse("三千"))
        assertEquals(2024L, NumberVariants.parse("二〇二四"))
        assertEquals(12000L, NumberVariants.parse("1万2千"))
        assertEquals(15L, NumberVariants.parse("十五"))
        assertEquals(100_000L, NumberVariants.parse("十万"))
        assertEquals(42L, NumberVariants.parse("４２"))
    }

    @Test
    fun rejectsBrokenUnitOrder() {
        assertNull(NumberVariants.parse("千千"))
        assertNull(NumberVariants.parse("万億"))
        assertNull(NumberVariants.parse("99999999999999999999"))
        assertNull(NumberVariants.parse("9999999999999999千"))
    }

    @Test
    fun writesKanjiNumbers() {
        assertEquals("三千", NumberVariants.toKanji(3000))
        assertEquals("十五", NumberVariants.toKanji(15))
        assertEquals("一万二千", NumberVariants.toKanji(12000))
        assertEquals("〇", NumberVariants.toKanji(0))
    }

    @Test
    fun rewritesOnlyTheLeadingNumber() {
        assertEquals(listOf("三千円", "3000円", "3,000円", "３０００円", "三〇〇〇円"), NumberVariants.of("3千円"))
        assertEquals(listOf("100000円", "10万円", "100,000円", "１０００００円"), NumberVariants.of("十万円"))
        assertTrue(NumberVariants.of("円").isEmpty())
    }

    @Test
    fun findsKanjiNumbers() {
        assertTrue(NumberVariants.isKanjiNumber("十万"))
        assertTrue(NumberVariants.isKanjiNumber("三千"))
        assertTrue(!NumberVariants.isKanjiNumber("万一"))
        assertTrue(!NumberVariants.isKanjiNumber("十分"))
    }

    @Test
    fun putsKanjiFirst() {
        assertEquals("一億", NumberVariants.toKanjiNumbers("1億"))
        assertEquals("二千円札", NumberVariants.toKanjiNumbers("2千円札"))
        assertEquals("二時三分", NumberVariants.toKanjiNumbers("2時3分"))
        assertNull(NumberVariants.toKanjiNumbers("三千円"))
        // 型番・小数・区切り・0 で始まる数字は崩さない
        assertEquals("二台のM1A2", NumberVariants.toKanjiNumbers("2台のM1A2"))
        assertNull(NumberVariants.toKanjiNumbers("3.14"))
        assertNull(NumberVariants.toKanjiNumbers("1,000"))
        assertNull(NumberVariants.toKanjiNumbers("100-0001"))
        assertNull(NumberVariants.toKanjiNumbers("007"))
    }

    @Test
    fun numberWordsGetOtherFormsButOtherWordsDoNot() {
        val dictionary = TestDictionary.instance
        assumeTrue(dictionary != null)
        val converter = Converter(dictionary!!)
        fun list(reading: String) = converter.candidates(reading, 0, reading.length).map { it.surface }
        assertTrue(list("ひとり").toString(), "1人" in list("ひとり"))
        assertTrue(list("じゅうまん").toString(), "10万" in list("じゅうまん"))
        // 数の品詞で登録の無い語は書き換えない
        assertTrue(list("じゅうぶん").toString(), "10分" !in list("じゅうぶん"))
        assertTrue(list("いっぽう").toString(), "1方" !in list("いっぽう"))
        // 長い数は 1 桁ずつの漢数字にしない
        assertTrue(list("いちおく").toString(), "一〇〇〇〇〇〇〇〇" !in list("いちおく"))
    }

    @Test
    fun kanaNumbersConvertToKanji() {
        val dictionary = TestDictionary.instance
        assumeTrue(dictionary != null)
        val converter = Converter(dictionary!!, languageModel = TestDictionary.languageModel)
        assertEquals("一億", converter.convert("いちおく").joinToString("") { it.candidates.first().surface })
        // 文節を伸び縮みさせたときに使う候補一覧でも、漢数字が先に来る
        assertEquals("一億", converter.candidates("いちおく", 0, 4).first().surface)
        // 数が 2 つある文節でも、通常の変換と同じく両方を漢数字にする
        assertEquals("二時三分", converter.candidates("にじさんぷん", 0, 6).first().surface)
        // 同じ表記が記号より前の枠を食わない
        val list = converter.candidates("にじ", 0, 2).map { it.surface }
        assertEquals(list.toString(), listOf("二時", "2時", "２時"), list.take(3))
    }

    @Test
    fun numberCandidatesGetOtherForms() {
        val dictionary = TestDictionary.instance
        assumeTrue(dictionary != null)
        val list = Converter(dictionary!!).candidates("さんぜんえん", 0, 6).map { it.surface }
        assertTrue(list.toString(), list.indexOf("三千円") in 0..2)
        assertTrue(list.toString(), "3000円" in list)
    }
}
