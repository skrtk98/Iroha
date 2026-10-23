package io.github.skrtk98.iroha.engine.dict

import io.github.skrtk98.iroha.engine.text.Kana
import java.nio.ByteBuffer

/**
 * mmap した iroha.dic を読む。読み取りのみなので複数スレッドから同時に使ってよい。
 */
class SystemDictionary(buffer: ByteBuffer) {
    private val buf: ByteBuffer = buffer.duplicate()

    val codec: CharCodec
    private val posClasses: Array<PosClass>
    val matrixDim: Int
    private val matrixOffset: Int
    private val matrixWords: Int
    private val resolution: Int
    val readingCount: Int
    private val blockCount: Int
    private val blockOffsetsOffset: Int
    private val dataOffset: Int
    private val dataLength: Int

    /** 未知語に使う普通名詞の品詞 ID。 */
    val unknownNounId: Int

    private val userPosIds: Map<UserPos, Int>
    private val segmentOffset: Int
    private val segmentBitsOffset: Int
    private val rightClassCount: Int

    /** ユーザー辞書の品詞に対応する品詞 ID。 */
    fun posId(pos: UserPos): Int = userPosIds.getValue(pos)

    init {
        require(buf.getInt(0) == DictionaryFormat.MAGIC) { "not an iroha dictionary" }
        require(buf.getInt(4) == DictionaryFormat.VERSION) { "unsupported dictionary version" }
        val charCount = buf.getInt(8)
        val posCount = buf.getInt(12)
        matrixDim = buf.getInt(16)
        resolution = buf.getInt(20)
        readingCount = buf.getInt(24)
        blockCount = buf.getInt(28)
        require(buf.getInt(32) == DictionaryFormat.BLOCK_SIZE)
        unknownNounId = buf.getInt(36)
        dataLength = buf.getInt(40)
        val userPosCount = buf.getInt(44)
        val leftClassCount = buf.getInt(48)
        rightClassCount = buf.getInt(52)

        var offset = DictionaryFormat.HEADER_SIZE
        codec = CharCodec(CharArray(charCount) { buf.getChar(offset + it * 2) })
        offset += charCount * 2
        val classes = PosClass.entries
        posClasses = Array(posCount) { classes[buf.get(offset + it).toInt()] }
        offset += posCount
        val userIds = IntArray(userPosCount) { buf.getShort(offset + it * 2).toInt() and 0xFFFF }
        userPosIds = UserPos.entries.associateWith { userIds.getOrElse(it.ordinal) { unknownNounId } }
        offset += userPosCount * 2
        segmentOffset = offset
        segmentBitsOffset = offset + posCount * 8
        offset = segmentBitsOffset + (leftClassCount * rightClassCount + 7) / 8
        matrixOffset = offset
        matrixWords = (matrixDim + 63) / 64
        offset = matrixOffset + buf.getInt(matrixOffset + matrixDim * 4)
        blockOffsetsOffset = offset
        offset += blockCount * 4
        dataOffset = offset
        require(dataOffset + dataLength <= buf.limit()) { "truncated dictionary" }
    }

    fun posClass(id: Int): PosClass = posClasses.getOrElse(id) { PosClass.Content }

    /** rid の語と lid の語の間で文節を区切るか (mozc の segmenter.def)。 */
    fun isBoundary(rid: Int, lid: Int): Boolean {
        val count = posClasses.size
        if (rid >= count || lid >= count) return true
        val left = segmentValue(0, rid)
        val right = segmentValue(1, lid)
        val index = left * rightClassCount + right
        return buf.get(segmentBitsOffset + (index ushr 3)).toInt() and (1 shl (index and 7)) != 0
    }

    /** 文節の先頭に lid の語が来るときに足すコスト (mozc の boundary.def)。 */
    fun prefixPenalty(lid: Int): Int = if (lid < posClasses.size) segmentValue(2, lid) else 0

    /** 文節の末尾に rid の語が来るときに足すコスト。 */
    fun suffixPenalty(rid: Int): Int = if (rid < posClasses.size) segmentValue(3, rid) else 0

    private fun segmentValue(table: Int, id: Int): Int =
        buf.getShort(segmentOffset + (table * posClasses.size + id) * 2).toInt() and 0xFFFF

    /** 左の語の rid から右の語の lid へ接続するコスト。 */
    fun connectionCost(rid: Int, lid: Int): Int {
        if (rid >= matrixDim || lid >= matrixDim) return UNKNOWN_CONNECTION_COST
        val row = matrixOffset + buf.getInt(matrixOffset + rid * 4)
        val word = lid ushr 6
        val bits = buf.getLong(row + 1 + word * 8)
        val mask = 1L shl (lid and 63)
        val value = if (bits and mask == 0L) {
            buf.get(row)
        } else {
            val rankBase = row + 1 + matrixWords * 8
            val rank = (buf.getShort(rankBase + word * 2).toInt() and 0xFFFF) +
                java.lang.Long.bitCount(bits and (mask - 1))
            buf.get(rankBase + matrixWords * 2 + rank)
        }
        return (value.toInt() and 0xFF) * resolution
    }

    /**
     * [text] の [start] から始まり辞書に読みがある語をすべて返す。
     * [maxLength] 文字を超える読みは調べない。
     */
    fun commonPrefixSearch(text: CharSequence, start: Int, maxLength: Int = MAX_READING_CHARS): List<WordEntry> {
        val limit = minOf(text.length, start + maxLength)
        val sub = text.subSequence(start, limit)
        val key = ByteArray(sub.length * 2)
        val offsets = IntArray(sub.length + 1)
        val encodable = codec.encodePrefix(sub, key, offsets)
        val result = ArrayList<WordEntry>()
        val cursor = Cursor()
        for (len in 1..encodable) {
            val keyLen = offsets[len]
            if (!cursor.seek(key, keyLen)) break
            val cmp = cursor.compareTo(key, keyLen)
            if (cmp == 0) {
                cursor.readEntries(sub.substring(0, len), result)
            } else if (!cursor.startsWith(key, keyLen)) {
                break
            }
        }
        return result
    }

    /** 読みが完全に一致する語。 */
    fun exactSearch(reading: CharSequence): List<WordEntry> {
        val key = codec.encode(reading) ?: return emptyList()
        val cursor = Cursor()
        if (!cursor.seek(key, key.size) || cursor.compareTo(key, key.size) != 0) return emptyList()
        return ArrayList<WordEntry>().also { cursor.readEntries(reading.toString(), it) }
    }

    /** 候補に足すだけのデータ ([ExtraKind]) のうち、読みが [reading] の語。並びは元のデータの順。 */
    fun extras(kind: ExtraKind, reading: CharSequence): List<WordEntry> =
        exactSearch(kind.key(reading)).map { it.copy(reading = it.reading.substring(1)) }

    /** [kind] のデータで読みが [prefix] で始まる語。 */
    fun predictExtras(kind: ExtraKind, prefix: CharSequence, maxReadings: Int, consumer: (WordEntry) -> Unit) {
        predictiveSearch(kind.key(prefix), maxReadings) { consumer(it.copy(reading = it.reading.substring(1))) }
    }

    /**
     * 読みが [prefix] で始まる語を最大 [maxReadings] 個の読みまで走査して返す。
     * 読みの昇順に走査するため、件数が多い短い接頭辞では打ち切られる。
     */
    fun predictiveSearch(prefix: CharSequence, maxReadings: Int, consumer: (WordEntry) -> Unit) {
        val key = codec.encode(prefix) ?: return
        val cursor = Cursor()
        if (!cursor.seek(key, key.size)) return
        var count = 0
        val sink = ArrayList<WordEntry>()
        while (count < maxReadings && cursor.startsWith(key, key.size)) {
            sink.clear()
            cursor.readEntries(cursor.readingString(), sink)
            sink.forEach(consumer)
            count++
            if (!cursor.next()) break
        }
    }

    /** レコード列を読み進めるカーソル。スレッドごとに作る。 */
    private inner class Cursor {
        val reading = ByteArray(MAX_READING_BYTES)
        var readingLength = 0
        private var recordPos = -1
        private var nextPos = 0
        private var entriesPos = 0
        private var entriesLength = 0
        private val scratch = ByteArray(MAX_SURFACE_BYTES)

        fun seek(key: ByteArray, keyLength: Int): Boolean {
            var lo = 0
            var hi = blockCount - 1
            var block = 0
            // 先頭の読みが key 以下である最後のブロックを探す
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (compareHead(mid, key, keyLength) <= 0) {
                    block = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            nextPos = buf.getInt(blockOffsetsOffset + block * 4)
            readingLength = 0
            if (!next()) return false
            while (compareTo(key, keyLength) < 0) {
                if (!next()) return false
            }
            return true
        }

        fun next(): Boolean {
            if (nextPos >= dataLength) return false
            recordPos = nextPos
            var p = dataOffset + nextPos
            val prefixLength = buf.get(p).toInt() and 0xFF
            val suffixLength = buf.get(p + 1).toInt() and 0xFF
            p += 2
            for (i in 0 until suffixLength) reading[prefixLength + i] = buf.get(p + i)
            readingLength = prefixLength + suffixLength
            p += suffixLength
            val (length, afterLength) = readVarint(p)
            entriesPos = afterLength
            entriesLength = length
            nextPos = afterLength + length - dataOffset
            return true
        }

        fun compareTo(key: ByteArray, keyLength: Int): Int =
            compareBytes(reading, readingLength, key, keyLength)

        fun startsWith(key: ByteArray, keyLength: Int): Boolean {
            if (readingLength < keyLength) return false
            for (i in 0 until keyLength) if (reading[i] != key[i]) return false
            return true
        }

        fun readingString(): String = buildString { codec.decode(reading, 0, readingLength, this) }

        fun readEntries(readingText: String, out: MutableList<WordEntry>) {
            var (count, p) = readVarint(entriesPos)
            repeat(count) {
                val flags = buf.get(p).toInt() and 0xFF
                val lid = buf.getShort(p + 1).toInt() and 0xFFFF
                p += 3
                val rid = if (flags and DictionaryFormat.FLAG_RID_DIFFERS != 0) {
                    (buf.getShort(p).toInt() and 0xFFFF).also { p += 2 }
                } else {
                    lid
                }
                val cost = buf.getShort(p).toInt() and 0xFFFF
                p += 2
                val surface = when ((flags ushr DictionaryFormat.SURFACE_SHIFT) and DictionaryFormat.SURFACE_MASK) {
                    DictionaryFormat.SURFACE_HIRAGANA -> readingText
                    DictionaryFormat.SURFACE_KATAKANA -> Kana.toKatakana(readingText)
                    else -> {
                        val (length, start) = readVarint(p)
                        for (i in 0 until length) scratch[i] = buf.get(start + i)
                        p = start + length
                        buildString { codec.decode(scratch, 0, length, this) }
                    }
                }
                out.add(WordEntry(readingText, surface, lid, rid, cost))
            }
        }

        private fun compareHead(block: Int, key: ByteArray, keyLength: Int): Int {
            val p = dataOffset + buf.getInt(blockOffsetsOffset + block * 4)
            val length = buf.get(p + 1).toInt() and 0xFF
            val n = minOf(length, keyLength)
            for (i in 0 until n) {
                val a = buf.get(p + 2 + i).toInt() and 0xFF
                val b = key[i].toInt() and 0xFF
                if (a != b) return a - b
            }
            return length - keyLength
        }
    }

    /** 値と、読み終えた直後の絶対位置を返す。 */
    private fun readVarint(position: Int): Pair<Int, Int> {
        var p = position
        var result = 0
        var shift = 0
        while (true) {
            val b = buf.get(p++).toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result to p
            shift += 7
        }
    }

    companion object {
        const val MAX_READING_CHARS = 40
        private const val MAX_READING_BYTES = 255
        private const val MAX_SURFACE_BYTES = 1024
        private const val UNKNOWN_CONNECTION_COST = 3000

        internal fun compareBytes(a: ByteArray, aLength: Int, b: ByteArray, bLength: Int): Int {
            val n = minOf(aLength, bLength)
            for (i in 0 until n) {
                val x = a[i].toInt() and 0xFF
                val y = b[i].toInt() and 0xFF
                if (x != y) return x - y
            }
            return aLength - bLength
        }
    }
}
