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
    /**
     * 0 より大きければ、捨ててもモデルの交差エントロピー (1 文字あたりの -ln 確率の期待値) がこれ未満しか増えない 3-gram を捨てる。
     * Stolcke の刈り込みの簡略版で、捨てた後に文脈のバックオフの重みを計算し直さない (出現数で捨てるときと同じ)。
     * 出現数で切るより、2-gram から予想のつく 3-gram を選んで落とせる。
     */
    private val pruneThreshold: Double = 0.0,
) {
    // 数億字のコーパスでは 3-gram が数千万種類になるので、文字列ではなく 3 文字を詰めた Long で数える
    private val trigrams = LongIntMap()

    /** 書き出した 3-gram の数と、刈り込みで捨てた数。[write] の後に分かる。 */
    var writtenTrigrams = 0
        private set
    var prunedTrigrams = 0
        private set

    fun add(sentence: String) {
        var a = LanguageModel.BOS
        var b = LanguageModel.BOS
        for (raw in sentence) {
            val c = LanguageModel.normalize(raw)
            trigrams.add(trigramKey(a, b, c), 1)
            a = b
            b = c
        }
    }

    fun write(out: OutputStream) {
        // 2-gram は、何種類の文字の後に来たか (継続数) で数える。1-gram も同様
        val bigramContinuations = HashMap<Int, Int>()
        val trigramContexts = HashMap<Int, Context>()
        var total = 0L
        trigrams.forEach { key, count ->
            total += count
            bigramContinuations.merge(bigramKey(key.b, key.c), 1, Int::plus)
            trigramContexts.getOrPut(bigramKey(key.a, key.b)) { Context() }.add(count)
        }
        val unigramContinuations = HashMap<Char, Int>()
        val bigramContexts = HashMap<Char, Context>()
        for ((gram, count) in bigramContinuations) {
            unigramContinuations.merge(gram.second, 1, Int::plus)
            bigramContexts.getOrPut(gram.first) { Context() }.add(count)
        }
        val bigramTypes = bigramContinuations.size.toDouble()
        fun unigram(c: Char) = ((unigramContinuations[c] ?: 0) + UNIGRAM_PRIOR) / (bigramTypes + UNIGRAM_PRIOR * VOCABULARY)
        fun bigram(b: Char, c: Char): Double {
            val context = bigramContexts[b] ?: return unigram(c)
            return context.interpolate(bigramContinuations[bigramKey(b, c)] ?: 0, unigram(c))
        }

        // 3-gram 表: 上位に指紋、下位 8 ビットに -ln 確率を詰めて並べる
        val trigramTable = LongArrayBuilder()
        prunedTrigrams = 0
        trigrams.forEach { key, count ->
            if (count < minTrigramCount) return@forEach
            val context = trigramContexts.getValue(bigramKey(key.a, key.b))
            val lower = bigram(key.b, key.c)
            val p = context.interpolate(count, lower)
            // 捨てると、この並びは文脈のバックオフ × 2-gram の確率で引かれる。文脈が出る割合で重みを付けた差が小さければ捨てる
            if (pruneThreshold > 0 && context.seen.toDouble() / total * p * ln(p / (context.backoff * lower)) < pruneThreshold) {
                prunedTrigrams++
                return@forEach
            }
            trigramTable.add(entry(LanguageModel.fingerprint(key.a, key.b, key.c), LanguageModel.pack(-ln(p)).toInt() and 0xFF, 8))
        }

        // 1・2-gram 表: 確率を持つ並びと、文脈として使う並び (バックオフの重み) をまとめる。値は確率とバックオフの 2 バイト
        // 1-gram と 2-gram は別の表に持つ (NUL で始まる 2-gram の鍵は 1-gram の鍵と同じ値になるため)
        val lowerUnigrams = HashMap<Char, Int>()
        val lowerBigrams = HashMap<Int, Int>()
        val none = LanguageModel.NONE.toInt() and 0xFF
        val zero = LanguageModel.pack(0.0).toInt() and 0xFF
        fun cost(p: Double) = LanguageModel.pack(-ln(p)).toInt() and 0xFF
        for ((c, _) in unigramContinuations) lowerUnigrams[c] = cost(unigram(c)) shl 8 or zero
        for ((gram, _) in bigramContinuations) lowerBigrams[gram] = cost(bigram(gram.first, gram.second)) shl 8 or zero
        for ((c, context) in bigramContexts) {
            lowerUnigrams[c] = (lowerUnigrams[c]?.ushr(8) ?: none) shl 8 or cost(context.backoff)
        }
        for ((gram, context) in trigramContexts) {
            lowerBigrams[gram] = (lowerBigrams[gram]?.ushr(8) ?: none) shl 8 or cost(context.backoff)
        }
        val lowerTable = LongArrayBuilder()
        for ((c, value) in lowerUnigrams) lowerTable.add(entry(LanguageModel.fingerprint(c), value, 16))
        for ((gram, value) in lowerBigrams) lowerTable.add(entry(LanguageModel.fingerprint(gram.first, gram.second), value, 16))

        val sortedTrigrams = sortByFingerprint(trigramTable.toArray(), 8)
        val sortedLower = sortByFingerprint(lowerTable.toArray(), 16)
        writtenTrigrams = sortedTrigrams.size
        val unknown = -ln(UNIGRAM_PRIOR / (bigramTypes + UNIGRAM_PRIOR * VOCABULARY))

        val data = DataOutputStream(out.buffered())
        data.writeInt(LanguageModel.MAGIC)
        data.writeInt(LanguageModel.VERSION)
        data.writeInt(sortedTrigrams.size)
        data.writeInt(sortedLower.size)
        data.writeFloat(unknown.toFloat())
        sortedTrigrams.forEach { data.writeInt((it ushr 8).toInt()) }
        sortedTrigrams.forEach { data.writeByte(it.toInt()) }
        sortedLower.forEach { data.writeInt((it ushr 16).toInt()) }
        sortedLower.forEach { data.writeByte((it ushr 8).toInt()) }
        sortedLower.forEach { data.writeByte(it.toInt()) }
        data.flush()
    }

    /** 指紋 (符号なし 32 ビット) を上位に、[bits] ビットの値を下位に詰める。並べると指紋の昇順になる。 */
    private fun entry(fingerprint: Int, value: Int, bits: Int): Long = (fingerprint.toLong() and 0xFFFFFFFFL) shl bits or value.toLong()

    /** 指紋の昇順に並べる。指紋がぶつかった並びは、値の小さいほうだけ残す。 */
    private fun sortByFingerprint(entries: LongArray, bits: Int): LongArray {
        entries.sort()
        var size = 0
        for (e in entries) if (size == 0 || entries[size - 1] ushr bits != e ushr bits) entries[size++] = e
        return entries.copyOf(size)
    }

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

        // 3-gram は 16 ビットずつ詰め、空きの印 (0) と区別できるよう 49 ビット目を立てる。2-gram は先頭の文字を上位 16 ビットに置く
        fun trigramKey(a: Char, b: Char, c: Char): Long = 1L shl 48 or (a.code.toLong() shl 32) or (b.code.toLong() shl 16) or c.code.toLong()

        val Long.a: Char get() = (this ushr 32 and 0xFFFF).toInt().toChar()
        val Long.b: Char get() = (this ushr 16 and 0xFFFF).toInt().toChar()
        val Long.c: Char get() = (this and 0xFFFF).toInt().toChar()

        fun bigramKey(a: Char, b: Char): Int = a.code shl 16 or b.code

        val Int.first: Char get() = (this ushr 16).toChar()
        val Int.second: Char get() = (this and 0xFFFF).toChar()
    }
}

/** Long を鍵、Int を値にした開番地法のハッシュ表。0 は空きの印なので鍵にできない。 */
internal class LongIntMap {
    @PublishedApi internal var keys = LongArray(1 shl 16)
    @PublishedApi internal var values = IntArray(1 shl 16)
    private var size = 0

    fun add(key: Long, delta: Int) {
        require(key != 0L)
        val mask = keys.size - 1
        var i = slot(key, mask)
        while (true) {
            val k = keys[i]
            if (k == key) {
                values[i] += delta
                return
            }
            if (k == 0L) {
                // 広げるのは新しい鍵を入れるときだけ。既にある鍵に足すだけなら広げない
                if ((size + 1) * 10L > keys.size * 7L) {
                    grow()
                    add(key, delta)
                    return
                }
                keys[i] = key
                values[i] = delta
                size++
                return
            }
            i = (i + 1) and mask
        }
    }

    /** 鍵の並びは決まった順 (表の中の位置の順) になる。 */
    inline fun forEach(action: (key: Long, value: Int) -> Unit) {
        val k = keys
        val v = values
        for (i in k.indices) if (k[i] != 0L) action(k[i], v[i])
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        check(oldKeys.size < 1 shl 30) { "too many keys" }
        keys = LongArray(oldKeys.size * 2)
        values = IntArray(oldKeys.size * 2)
        val mask = keys.size - 1
        for (j in oldKeys.indices) {
            val key = oldKeys[j]
            if (key == 0L) continue
            var i = slot(key, mask)
            while (keys[i] != 0L) i = (i + 1) and mask
            keys[i] = key
            values[i] = oldValues[j]
        }
    }

    private fun slot(key: Long, mask: Int): Int = ((key * -0x61c8864680b583ebL) ushr 32).toInt() and mask
}

/** 伸びる LongArray。 */
private class LongArrayBuilder {
    private var array = LongArray(1 shl 16)
    private var size = 0

    fun add(value: Long) {
        if (size == array.size) array = array.copyOf(array.size * 2)
        array[size++] = value
    }

    fun toArray(): LongArray = array.copyOf(size)
}
