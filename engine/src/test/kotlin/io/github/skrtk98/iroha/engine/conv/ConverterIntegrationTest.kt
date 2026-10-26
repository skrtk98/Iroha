package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import io.github.skrtk98.iroha.engine.lm.LanguageModel
import io.github.skrtk98.iroha.engine.lm.LanguageModelBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** ビルドした実辞書での変換結果を確かめる。 */
class ConverterIntegrationTest {

    private fun convert(reading: String): String =
        converter.convert(reading).joinToString("") { it.candidates.first().surface }

    @Test
    fun convertsSentences() {
        assertEquals("私の名前は中野です", convert("わたしのなまえはなかのです"))
        assertEquals("今日はいい天気ですね", convert("きょうはいいてんきですね"))
        assertEquals("日本語を入力する", convert("にほんごをにゅうりょくする"))
    }

    @Test
    fun keepsContextAcrossOneCharacterParticles() {
        // 「を」までは「ローマ字分を」のほうが安いが、「書く」まで見ると「ローマ字文を書く」のほうが安い言語モデル。
        // 「を」の時点で安い経路だけを残すと「書く」の前の文字 (文 / 分) が分からず、「ローマ字分を書く」になる。
        // 実際のモデルの中身に左右されないよう小さく作る
        val builder = LanguageModelBuilder(minTrigramCount = 1)
        repeat(100) { builder.add("ローマ字分を足す") }
        repeat(30) { builder.add("ローマ字文を書く") }
        val model = LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(builder::write).toByteArray()))
        val withModel = Converter(dictionary, languageModel = model)
        assertEquals("ローマ字文を書く", withModel.convert("ろーまじぶんをかく").joinToString("") { it.candidates.first().surface })
    }

    @Test
    fun keepsContextWithShippedModel() {
        assumeTrue(TestDictionary.languageModel != null)
        // 「に」の前が「蚊」か「家」かで「噛まれる」の自然さが変わる
        assertTrue(convert("かにかまれる").startsWith("蚊に"))
    }

    @Test
    fun splitsIntoSegments() {
        val segments = converter.convert("わたしのなまえはなかのです").map { it.reading }
        assertEquals(listOf("わたしの", "なまえは", "なかのです"), segments)
    }

    @Test
    fun listsSegmentCandidates() {
        val candidates = converter.candidates("かいて", 0, 3).map { it.surface }
        assertTrue(candidates.toString(), "書いて" in candidates)
        assertTrue(candidates.toString(), "描いて" in candidates)
    }

    @Test
    fun candidateLimitIncludesFallbacks() {
        assertEquals(1, converter.candidates("かいて", 0, 3, limit = 1).size)
        val three = converter.candidates("かいて", 0, 3, limit = 3).map { it.surface }
        assertEquals(3, three.size)
        assertTrue(three.toString(), "かいて" in three)
    }

    @Test
    fun suggestsPredictions() {
        val suggestions = converter.suggest("ありがと").map { it.surface }
        assertTrue(suggestions.toString(), "ありがとう" in suggestions)
    }

    @Test
    fun isFastEnough() {
        val text = "きょうはとうきょうでかいぎがあるのでおくれないようにいえをでます"
        repeat(5) { converter.convert(text) }
        val start = System.nanoTime()
        repeat(20) { converter.convert(text) }
        val ms = (System.nanoTime() - start) / 1_000_000 / 20
        println("convert: ${ms}ms -> ${converter.convert(text).joinToString("|") { it.candidates.first().surface }}")
        val s2 = System.nanoTime()
        repeat(20) { converter.suggest("か") }
        println("suggest(1 char): ${(System.nanoTime() - s2) / 1_000_000 / 20}ms")
        assertTrue(ms < 100)
    }

    companion object {
        private lateinit var converter: Converter
        private lateinit var dictionary: SystemDictionary

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val path = System.getProperty("iroha.dictionary")
            assumeTrue(path != null && File(path).exists())
            RandomAccessFile(path, "r").use { file ->
                val buffer = file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
                dictionary = SystemDictionary(buffer)
                // アプリと同じく言語モデルも使う
                converter = Converter(dictionary, languageModel = TestDictionary.languageModel)
            }
        }
    }
}
