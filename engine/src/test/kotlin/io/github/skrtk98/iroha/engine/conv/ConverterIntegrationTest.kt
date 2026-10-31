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
        // 重みの既定値が変わっても仕組みだけを確かめられるよう、このモデルに合わせた重みで固定する
        val withModel = Converter(dictionary, languageModel = model, languageModelWeight = 350f)
        assertEquals("ローマ字文を書く", withModel.convert("ろーまじぶんをかく").joinToString("") { it.candidates.first().surface })
    }

    @Test
    fun usesLeftContext() {
        // 「かがおおい」だけでは「蚊」か「課」か決まらないが、確定済みの左の文で選べる言語モデル
        val builder = LanguageModelBuilder(minTrigramCount = 1)
        repeat(100) { builder.add("夏は蚊が多い") }
        repeat(100) { builder.add("会社の課が多い") }
        val model = LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(builder::write).toByteArray()))
        val withModel = Converter(dictionary, languageModel = model, languageModelWeight = 350f)
        fun convert(left: String) = withModel.convert("かがおおい", leftContext = left).joinToString("") { it.candidates.first().surface }
        assertTrue(convert("夏は").startsWith("蚊"))
        assertTrue(convert("会社の").startsWith("課"))
        // 文の終わりより前は文脈にしない
        assertEquals(convert(""), convert("会社の。"))
        assertEquals(convert("夏は"), convert("会社の。夏は"))
        // 文節の候補も同じ規則で文脈を使う
        fun top(left: String) = withModel.candidates("か", 0, 1, leftSurface = left).first().surface
        assertEquals("蚊", top("夏は"))
        assertEquals("課", top("会社の"))
        assertEquals(top(""), top("会社の。"))
    }

    @Test
    fun decimalPointIsNotSentenceEnd() {
        // 「.5」の後なら課、文頭の「5」の後なら蚊になる言語モデル。小数点を文の終わりとみなすと、全角の「３．５」が「5」だけになる
        val builder = LanguageModelBuilder(minTrigramCount = 1)
        repeat(100) { builder.add("1.5課が多い") }
        repeat(100) { builder.add("5蚊が多い") }
        val model = LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(builder::write).toByteArray()))
        val withModel = Converter(dictionary, languageModel = model, languageModelWeight = 350f)
        fun convert(left: String) = withModel.convert("かがおおい", leftContext = left).joinToString("") { it.candidates.first().surface }
        assertTrue(convert("3.5").startsWith("課"))
        assertTrue(convert("３．５").startsWith("課"))
        assertTrue(convert("5").startsWith("蚊"))
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
