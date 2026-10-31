package io.github.skrtk98.iroha.engine.lm

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class LanguageModelBuilderTest {
    private val sentences = listOf("今日は晴れです", "明日は雨です", "今日は雨です", "語学を学ぶ", "語学が好き", "明日は晴れ")

    private fun build(builder: LanguageModelBuilder, repeat: Int = 3, corpus: List<String> = sentences): LanguageModel {
        repeat(repeat) { corpus.forEach(builder::add) }
        return LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(builder::write).toByteArray()))
    }

    private fun bytes(builder: LanguageModelBuilder): ByteArray {
        repeat(3) { sentences.forEach(builder::add) }
        return ByteArrayOutputStream().also(builder::write).toByteArray()
    }

    @Test
    fun writesIndexMatchingKeys() {
        assertTrue(build(LanguageModelBuilder(minTrigramCount = 1)).indexMatchesKeys())
    }

    @Test
    fun shippedModelIndexMatchesKeys() {
        // 出荷するモデルの索引は、旧形式のファイルに後から足したもの。鍵と食い違うと、引けるはずの並びが引けなくなる
        val model = TestDictionary.languageModel
        assumeTrue(model != null)
        assertTrue(model!!.indexMatchesKeys())
    }

    @Test
    fun rejectsIndexOutsideTable() {
        val data = bytes(LanguageModelBuilder(minTrigramCount = 1))
        val trigrams = ByteBuffer.wrap(data).getInt(8)
        // 3-gram の索引の 2 番目を表の外に向ける。最後は件数なので、どこかで減ることになる
        ByteBuffer.wrap(data).putInt(LanguageModel.HEADER_SIZE + 4, trigrams + 1)
        assertThrows(IllegalArgumentException::class.java) { LanguageModel(ByteBuffer.wrap(data)) }
    }

    @Test
    fun rejectsCountsBeyondFile() {
        // 件数 × 表の幅が Int であふれる大きさでも、ファイルに収まらないとして拒む。索引は範囲の検証を通る形にする
        for (field in listOf(8, 12)) {
            val data = bytes(LanguageModelBuilder(minTrigramCount = 1))
            val buffer = ByteBuffer.wrap(data)
            val huge = 1 shl 29
            buffer.putInt(field, huge)
            val index = LanguageModel.HEADER_SIZE + if (field == 8) 0 else LanguageModel.INDEX_SIZE
            buffer.putInt(index + LanguageModel.INDEX_SIZE - 4, huge)
            assertThrows(IllegalArgumentException::class.java) { LanguageModel(ByteBuffer.wrap(data)) }
        }
    }

    @Test
    fun indexCheckNoticesUnsortedKeys() {
        // 同じ上位 16 ビットの 2 つの鍵を入れ替えると索引は変わらないが、二分探索で引けなくなる。
        // そうした鍵の組ができるよう、3-gram が数千種類になるコーパスで作る
        val builder = LanguageModelBuilder(minTrigramCount = 1)
        ('ぁ'..'ゖ').forEach { a -> builder.add("$a" + ('ァ'..'ヶ').joinToString("")) }
        val data = ByteArrayOutputStream().also(builder::write).toByteArray()
        val buffer = ByteBuffer.wrap(data)
        val count = buffer.getInt(8)
        val keys = LanguageModel.HEADER_SIZE + LanguageModel.INDEX_SIZE * 2
        val i = (1 until count).first { buffer.getInt(keys + (it - 1) * 4) ushr 16 == buffer.getInt(keys + it * 4) ushr 16 }
        val a = buffer.getInt(keys + (i - 1) * 4)
        buffer.putInt(keys + (i - 1) * 4, buffer.getInt(keys + i * 4))
        buffer.putInt(keys + i * 4, a)
        assertFalse(LanguageModel(ByteBuffer.wrap(data)).indexMatchesKeys())
    }

    @Test
    fun rejectsOldFormat() {
        val data = bytes(LanguageModelBuilder(minTrigramCount = 1))
        ByteBuffer.wrap(data).putInt(4, 1)
        assertThrows(IllegalArgumentException::class.java) { LanguageModel(ByteBuffer.wrap(data)) }
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
