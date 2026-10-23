package io.github.skrtk98.iroha.engine.lm

import java.nio.ByteBuffer

/**
 * 表記の文字の並びの自然さを測る、文字 3-gram の言語モデル (補間 Kneser-Ney をバックオフ形式で持つ)。
 * 辞書のコストだけでは選べない「この商品を|買いたい」と「この|商品|を解体」のような並びを、
 * 文字のつながりで選び分けるのに使う。
 *
 * 形式 ([LanguageModelBuilder] が書く。数値はビッグエンディアン):
 * - ヘッダー: MAGIC, VERSION, 3-gram の数, 1・2-gram の数, 未知の文字の -ln 確率 (float)
 * - 3-gram 表: 指紋 (u32) の昇順の配列と、同じ順の -ln 確率 (u8) の配列
 * - 1・2-gram 表: 指紋の昇順の配列と、-ln 確率 (u8, [NONE] は確率なし) と、文脈としての -ln バックオフ (u8) の配列
 *
 * -ln の値は [STEP] 刻みで 1 バイトにまとめる。指紋の衝突で別の並びの値を返すことはあり得るが、
 * 32 ビットなので数十万の並びでも起きるのはまれで、起きても候補の順が少し変わるだけで済む。
 */
class LanguageModel(private val buf: ByteBuffer) {
    private val trigramCount: Int
    private val lowerCount: Int
    private val unknownCost: Float
    private val trigramKeys: Int
    private val trigramProbs: Int
    private val lowerKeys: Int
    private val lowerProbs: Int
    private val lowerBackoffs: Int

    /** 指紋の上位 16 ビットごとの、表の中の開始位置。二分探索の範囲を数件に絞る。 */
    private val trigramBuckets: IntArray
    private val lowerBuckets: IntArray

    init {
        require(buf.getInt(0) == MAGIC) { "not a language model" }
        require(buf.getInt(4) == VERSION) { "unsupported language model version ${buf.getInt(4)}" }
        trigramCount = buf.getInt(8)
        lowerCount = buf.getInt(12)
        unknownCost = buf.getFloat(16)
        trigramKeys = HEADER_SIZE
        trigramProbs = trigramKeys + trigramCount * 4
        lowerKeys = trigramProbs + trigramCount
        lowerProbs = lowerKeys + lowerCount * 4
        lowerBackoffs = lowerProbs + lowerCount
        require(lowerBackoffs + lowerCount <= buf.limit()) { "truncated language model" }
        trigramBuckets = buckets(trigramKeys, trigramCount)
        lowerBuckets = buckets(lowerKeys, lowerCount)
    }

    private fun buckets(offset: Int, count: Int): IntArray {
        val starts = IntArray(BUCKETS + 1)
        var bucket = 0
        for (i in 0 until count) {
            val high = buf.getInt(offset + i * 4) ushr 16
            while (bucket <= high) starts[bucket++] = i
        }
        while (bucket <= BUCKETS) starts[bucket++] = count
        return starts
    }

    /** [previous] の後に [text] が続くことの -ln 確率。[previous] が空なら文頭から始める。 */
    fun negLogProb(previous: String, text: String): Float {
        var a = previous.getOrNull(previous.length - 2)?.let(::normalize) ?: BOS
        var b = previous.lastOrNull()?.let(::normalize) ?: BOS
        var sum = 0f
        for (raw in text) {
            val c = normalize(raw)
            sum += cost(a, b, c)
            a = b
            b = c
        }
        return sum
    }

    /** 正規化済みの 2 文字 [a] [b] の後に [c] が来ることの -ln 確率。 */
    fun cost(a: Char, b: Char, c: Char): Float {
        val tri = find(trigramKeys, trigramBuckets, fingerprint(a, b, c))
        if (tri >= 0) return unpack(buf.get(trigramProbs + tri))
        val context = find(lowerKeys, lowerBuckets, fingerprint(a, b))
        val backoff = if (context >= 0) unpack(buf.get(lowerBackoffs + context)) else 0f
        return backoff + bigramCost(b, c)
    }

    private fun bigramCost(b: Char, c: Char): Float {
        val bi = find(lowerKeys, lowerBuckets, fingerprint(b, c))
        if (bi >= 0) {
            val prob = buf.get(lowerProbs + bi)
            if (prob != NONE) return unpack(prob)
        }
        val context = find(lowerKeys, lowerBuckets, fingerprint(b))
        val backoff = if (context >= 0) unpack(buf.get(lowerBackoffs + context)) else 0f
        return backoff + unigramCost(c)
    }

    private fun unigramCost(c: Char): Float {
        val uni = find(lowerKeys, lowerBuckets, fingerprint(c))
        if (uni >= 0) {
            val prob = buf.get(lowerProbs + uni)
            if (prob != NONE) return unpack(prob)
        }
        return unknownCost
    }

    /** 指紋の昇順に並んだ u32 の表から [key] の位置を探す。無ければ -1。 */
    private fun find(offset: Int, buckets: IntArray, key: Int): Int {
        val high = key ushr 16
        var lo = buckets[high]
        var hi = buckets[high + 1] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = buf.getInt(offset + mid * 4)
            val cmp = Integer.compareUnsigned(v, key)
            when {
                cmp < 0 -> lo = mid + 1
                cmp > 0 -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        const val MAGIC = 0x49524C4D // "IRLM"
        const val VERSION = 1
        const val HEADER_SIZE = 20
        private const val BUCKETS = 1 shl 16

        /** 文頭を表す文字。コーパスにも候補にも現れない。 */
        const val BOS = '\u0002'

        /** -ln の値を 1 バイトにまとめる刻み。 */
        const val STEP = 0.1f

        /** 1・2-gram 表で、確率を持たない (文脈としてだけ載せた) 並び。 */
        const val NONE: Byte = -1

        /** 1 バイトで表せる -ln の最大値。これより小さい確率は丸める。 */
        const val MAX_COST = 254 * STEP

        internal fun unpack(b: Byte): Float = (b.toInt() and 0xFF) * STEP

        internal fun pack(cost: Double): Byte = Math.round((cost / STEP).coerceIn(0.0, 254.0)).toInt().toByte()

        /** 全角の英数字・記号を半角にそろえる。候補とコーパスで書き方の揺れを数え分けないため。 */
        fun normalize(c: Char): Char = if (c in '！'..'～') c - 0xFEE0 else c

        // FNV-1a。並びの長さも混ぜ、1・2-gram を同じ表に入れても区別できるようにする。
        // 変換のたびに何万回も呼ぶので、配列を作らないよう長さごとに分けている
        internal fun fingerprint(a: Char): Int = mix(0x811C9DC5.toInt() xor 1, a)

        internal fun fingerprint(a: Char, b: Char): Int = mix(mix(0x811C9DC5.toInt() xor 2, a), b)

        internal fun fingerprint(a: Char, b: Char, c: Char): Int = mix(mix(mix(0x811C9DC5.toInt() xor 3, a), b), c)

        internal fun fingerprint(gram: String): Int = when (gram.length) {
            1 -> fingerprint(gram[0])
            2 -> fingerprint(gram[0], gram[1])
            3 -> fingerprint(gram[0], gram[1], gram[2])
            else -> throw IllegalArgumentException(gram)
        }

        private fun mix(h: Int, c: Char): Int {
            val low = (h xor (c.code and 0xFF)) * 0x01000193
            return (low xor (c.code ushr 8)) * 0x01000193
        }
    }
}
