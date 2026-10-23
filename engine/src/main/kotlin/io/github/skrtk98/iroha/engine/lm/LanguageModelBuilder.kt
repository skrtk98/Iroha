package io.github.skrtk98.iroha.engine.lm

import java.io.DataOutputStream
import java.io.OutputStream
import kotlin.math.ln

/**
 * コーパスの文から [LanguageModel] の形式のモデルを作る。補間 Kneser-Ney で確率を求め、
 * 載せた並びには補間済みの確率を、文脈にはバックオフの重みを書く。
 */
class LanguageModelBuilder(
    /** これより少ない 3-gram は捨てて小さくする。 */
    private val minTrigramCount: Int = 2,
) {
    private val trigrams = HashMap<String, Int>()

    fun add(sentence: String) {
        val s = buildString(sentence.length + 2) {
            append(LanguageModel.BOS).append(LanguageModel.BOS)
            for (c in sentence) append(LanguageModel.normalize(c))
        }
        for (i in 2 until s.length) trigrams.merge(s.substring(i - 2, i + 1), 1, Int::plus)
    }

    fun write(out: OutputStream) {
        // 2-gram は、何種類の文字の後に来たか (継続数) で数える。1-gram も同様
        val bigramContinuations = HashMap<String, Int>()
        val trigramContexts = HashMap<String, Context>()
        for ((gram, count) in trigrams) {
            bigramContinuations.merge(gram.substring(1), 1, Int::plus)
            trigramContexts.getOrPut(gram.substring(0, 2)) { Context() }.add(count)
        }
        val unigramContinuations = HashMap<Char, Int>()
        val bigramContexts = HashMap<Char, Context>()
        for ((gram, count) in bigramContinuations) {
            unigramContinuations.merge(gram[1], 1, Int::plus)
            bigramContexts.getOrPut(gram[0]) { Context() }.add(count)
        }
        val bigramTypes = bigramContinuations.size.toDouble()
        fun unigram(c: Char) = ((unigramContinuations[c] ?: 0) + UNIGRAM_PRIOR) / (bigramTypes + UNIGRAM_PRIOR * VOCABULARY)
        fun bigram(b: Char, c: Char): Double {
            val context = bigramContexts[b] ?: return unigram(c)
            return context.interpolate(bigramContinuations["$b$c"] ?: 0, unigram(c))
        }

        val trigramTable = trigrams.entries.asSequence()
            .filter { it.value >= minTrigramCount }
            .map { (gram, count) ->
                val p = trigramContexts.getValue(gram.substring(0, 2)).interpolate(count, bigram(gram[1], gram[2]))
                gram to LanguageModel.pack(-ln(p))
            }
            .toList()

        // 1・2-gram 表: 確率を持つ並びと、文脈として使う並び (バックオフの重み) をまとめる
        val lower = HashMap<String, Pair<Byte, Byte>>()
        val none = LanguageModel.NONE
        val zero = LanguageModel.pack(0.0)
        for ((c, _) in unigramContinuations) lower[c.toString()] = LanguageModel.pack(-ln(unigram(c))) to zero
        for ((gram, _) in bigramContinuations) lower[gram] = LanguageModel.pack(-ln(bigram(gram[0], gram[1]))) to zero
        for ((c, context) in bigramContexts) {
            val key = c.toString()
            lower[key] = (lower[key]?.first ?: none) to LanguageModel.pack(-ln(context.backoff))
        }
        for ((gram, context) in trigramContexts) {
            lower[gram] = (lower[gram]?.first ?: none) to LanguageModel.pack(-ln(context.backoff))
        }

        val sortedTrigrams = sortByFingerprint(trigramTable.map { (gram, p) -> LanguageModel.fingerprint(gram) to p })
        val sortedLower = sortByFingerprint(lower.map { (gram, v) -> LanguageModel.fingerprint(gram) to v })
        val unknown = -ln(UNIGRAM_PRIOR / (bigramTypes + UNIGRAM_PRIOR * VOCABULARY))

        val data = DataOutputStream(out.buffered())
        data.writeInt(LanguageModel.MAGIC)
        data.writeInt(LanguageModel.VERSION)
        data.writeInt(sortedTrigrams.size)
        data.writeInt(sortedLower.size)
        data.writeFloat(unknown.toFloat())
        sortedTrigrams.forEach { data.writeInt(it.first) }
        sortedTrigrams.forEach { data.writeByte(it.second.toInt()) }
        sortedLower.forEach { data.writeInt(it.first) }
        sortedLower.forEach { data.writeByte(it.second.first.toInt()) }
        sortedLower.forEach { data.writeByte(it.second.second.toInt()) }
        data.flush()
    }

    /** 指紋の昇順に並べる。指紋がぶつかった並びは、先に来たものだけ残す。 */
    private fun <T> sortByFingerprint(entries: List<Pair<Int, T>>): List<Pair<Int, T>> =
        entries.sortedWith { x, y -> Integer.compareUnsigned(x.first, y.first) }
            .distinctBy { it.first }

    /** 文脈の出現数と、その後に続いた文字の種類数。 */
    private class Context {
        var seen = 0
        var types = 0

        fun add(count: Int) {
            seen += count
            types++
        }

        val backoff: Double get() = DISCOUNT * types / seen

        fun interpolate(count: Int, lower: Double): Double = (maxOf(count - DISCOUNT, 0.0) + DISCOUNT * types * lower) / seen
    }

    private companion object {
        const val DISCOUNT = 0.75
        const val UNIGRAM_PRIOR = 0.5

        /** 1-gram の平滑化で想定する文字の種類数。 */
        const val VOCABULARY = 8000.0
    }
}
