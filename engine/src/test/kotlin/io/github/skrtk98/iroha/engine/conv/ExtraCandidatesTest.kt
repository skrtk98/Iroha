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
    fun specialCandidatesFollowTheFirstCandidate() {
        // 予測（入力中の候補）でも変換でも、先頭の候補の次に並べる
        for (list in listOf(candidates("かっこ"), converter.suggest("かっこ").map { it.surface })) {
            assertEquals(list.toString(), listOf("（）", "「」", "｛｝"), list.subList(1, 4))
        }
        assertEquals("Python", converter.suggest("ぱいそん")[1].surface)
        assertEquals("Markdown", candidates("まーくだうん")[1])
        assertEquals("◯", candidates("まる")[1])
        assertEquals(listOf("□", "■"), converter.suggest("しかく").map { it.surface }.subList(1, 3))
    }

    @Test
    fun specialWordsSurviveSegmentation() {
        // 文節に分かれやすい長い語も、変換すると 1 つの文節になり、候補から選べる
        for ((reading, word) in listOf("たいぷすくりぷと" to "TypeScript", "おーぷんえーあい" to "OpenAI")) {
            val segments = converter.convert(reading)
            assertEquals(segments.toString(), 1, segments.size)
            assertTrue(candidates(reading).toString(), word in candidates(reading).take(3))
        }
    }

    @Test
    fun specialCandidatesLeaveRoomForPredictions() {
        val list = converter.suggest("かっこ", limit = 20).map { it.surface }
        assertTrue(list.toString(), list.any { it.length > 3 && (it.startsWith("かっこ") || it.startsWith("カッコ")) })
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
