package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** mozc の記号・絵文字・単漢字などのデータが候補に出ることを確かめる。 */
class ExtraCandidatesTest {
    private lateinit var converter: Converter

    @Before
    fun setUp() {
        val dictionary = TestDictionary.instance
        assumeTrue(dictionary != null)
        converter = Converter(dictionary!!)
    }

    private fun candidates(reading: String) = converter.candidates(reading, 0, reading.length).map { it.surface }

    @Test
    fun symbolsFollowTheTopCandidates() {
        val list = candidates("やじるし")
        assertEquals("矢印", list.first())
        assertTrue(list.toString(), list.indexOf("→") in 1..5)
    }

    @Test
    fun emojiAndEmoticonsAreCandidates() {
        assertTrue("🐱" in candidates("ねこ"))
        assertTrue("(^o^)" in candidates("にこにこ"))
    }

    @Test
    fun emojiTheDeviceCannotShowAreLeftOut() {
        val noEmoji = Converter(TestDictionary.instance!!, isDisplayable = { false })
        assertFalse("🐱" in noEmoji.candidates("ねこ", 0, 2).map { it.surface })
    }

    @Test
    fun singleKanjiComeAfterWords() {
        val list = candidates("あ")
        assertTrue(list.toString(), list.indexOf("婀") > list.indexOf("亜"))
        // ひらがな・カタカナは最後に必ず残る
        assertEquals("ア", list.last())
        assertTrue("あ" in list)
    }

    @Test
    fun keepsTheLimitIncludingFallbacks() {
        assertEquals(listOf("あ"), converter.candidates("あ", 0, 1, limit = 1).map { it.surface })
    }

    @Test
    fun suggestsCorrectedReadingsAndSuffixes() {
        assertTrue("お土産" in converter.suggest("おどさん").map { it.surface })
        assertTrue("しなきゃいけない" in converter.suggest("しなきゃ").map { it.surface })
    }

    @Test
    fun suggestsWhatUsuallyFollows() {
        assertTrue("ございます" in converter.zeroQuery("いつもありがとう").map { it.surface })
        assertTrue("人" in converter.zeroQuery("3").map { it.surface })
        assertTrue("人" in converter.zeroQuery("３").map { it.surface })
        assertTrue(converter.zeroQuery("12345678").isNotEmpty())
        assertTrue(converter.zeroQuery("ぬ").isEmpty())
    }

    @Test
    fun picksCollocations() {
        assertEquals("こっちに来い", converter.convert("こっちにこい").joinToString("") { it.candidates.first().surface })
    }
}
