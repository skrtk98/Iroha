package io.github.skrtk98.iroha.engine.lm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class LanguageModelBuilderTest {
    private val sentences = listOf("今日は晴れです", "明日は雨です", "今日は雨です", "語学を学ぶ", "語学が好き", "明日は晴れ")

    private fun build(builder: LanguageModelBuilder, repeat: Int = 3, corpus: List<String> = sentences): LanguageModel {
        repeat(repeat) { corpus.forEach(builder::add) }
        return LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(builder::write).toByteArray()))
    }

    @Test
    fun highCodePointsGiveSameCostsAsLowOnes() {
        // 「語」(U+8A9E) で始まる 2-gram の鍵は Int として負になる。1-gram と取り違えると 2-gram の確率が引けなくなるので、
        // 同じ形のコーパスを U+8000 未満の文字 (ゑ・ゐ) に置き換えたモデルと、同じ値になることを確かめる
        val high = build(LanguageModelBuilder(minTrigramCount = 1))
        val low = build(LanguageModelBuilder(minTrigramCount = 1), corpus = sentences.map { it.replace('語', 'ゑ').replace('学', 'ゐ') })
        fun low(ch: Char) = when (ch) { '語' -> 'ゑ'; '学' -> 'ゐ'; else -> ch }
        for (g in listOf("x語学", "x語語", "x学ぶ", "x今学", "語学を")) {
            assertEquals(g, low.cost(low(g[0]), low(g[1]), low(g[2])), high.cost(g[0], g[1], g[2]), 0f)
        }
    }

    @Test
    fun nulDoesNotOverwriteUnigrams() {
        // NUL で始まる 2-gram 「\u0000あ」の鍵は「あ」の 1-gram の鍵と同じ値になる。NUL の無いコーパスと「あ」の 1-gram の値が同じこと
        val plain = build(LanguageModelBuilder(minTrigramCount = 1), corpus = listOf("かあい", "いう"))
        val nul = build(LanguageModelBuilder(minTrigramCount = 1), corpus = listOf("\u0000あい", "いう"))
        assertEquals(plain.cost('x', 'y', 'あ'), nul.cost('x', 'y', 'あ'), 0f)
    }

    @Test
    fun pruningDropsTrigramsAndKeepsModelUsable() {
        val builders = listOf(0.0, 1e-3, 3e-3, 1e-2, 3e-2, 0.1, 0.3, 10.0).map { LanguageModelBuilder(minTrigramCount = 1, pruneThreshold = it) }
        val models = builders.map { build(it) }
        val written = builders.map { it.writtenTrigrams }
        assertEquals(0, builders.first().prunedTrigrams)
        assertEquals(0, written.last())
        // しきい値を上げるほど減り、途中に一部だけ捨てるしきい値がある
        assertEquals(written.sortedDescending(), written)
        assertTrue(written.any { it in 1 until written.first() })
        // 刈り込んでも、コーパスの文は見たことのない文より自然と判断できる
        for (model in models) {
            assertTrue(model.negLogProb("", "今日は晴れです") < model.negLogProb("", "今日葉晴れ出す"))
        }
    }

    @Test
    fun longIntMapGrowsOnlyForNewKeys() {
        val map = LongIntMap()
        val capacity = map.keys.size
        val fill = (capacity * 7 / 10).toLong()
        for (i in 1..fill) map.add(i, 1)
        repeat(3) { map.add(1, 1) }
        assertEquals(capacity, map.keys.size)
        map.add(fill + 1, 1)
        assertEquals(capacity * 2, map.keys.size)
    }

    @Test
    fun longIntMapGrowsAndSums() {
        val map = LongIntMap()
        for (round in 1..3) for (i in 1..200_000L) map.add(i * 7919, round)
        var count = 0
        var sum = 0L
        map.forEach { key, value ->
            assertEquals(0L, key % 7919)
            assertEquals(6, value)
            count++
            sum += value
        }
        assertEquals(200_000, count)
        assertEquals(1_200_000L, sum)
    }
}
