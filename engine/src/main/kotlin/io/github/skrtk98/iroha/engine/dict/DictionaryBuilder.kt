package io.github.skrtk98.iroha.engine.dict

import io.github.skrtk98.iroha.engine.text.Kana
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream

/** 語の一覧・品詞分類・連接行列から iroha.dic を書き出す。 */
class DictionaryBuilder(
    private val posClasses: List<PosClass>,
    /** rid 行 lid 列の連接コスト。 */
    private val matrix: IntArray,
    private val matrixDim: Int,
    private val unknownNounId: Int,
    private val userPosIds: List<Int> = UserPos.entries.map { unknownNounId },
    private val segmentRules: SegmentRules = SegmentRules.fromPosClasses(posClasses),
) {
    init {
        require(matrix.size == matrixDim * matrixDim)
        require(segmentRules.leftClass.size == posClasses.size)
    }

    fun write(entries: List<WordEntry>, out: OutputStream) {
        val codec = buildCodec(entries)
        val groups = entries
            .groupBy { it.reading }
            .map { (reading, words) -> checkNotNull(codec.encode(reading)) to words.sortedBy { it.cost } }
            .sortedWith { a, b -> SystemDictionary.compareBytes(a.first, a.first.size, b.first, b.first.size) }

        val data = ByteArrayOutputStream()
        val blockOffsets = ArrayList<Int>()
        var previous = ByteArray(0)
        for ((index, group) in groups.withIndex()) {
            val (key, words) = group
            require(key.size <= 255) { "reading too long: ${words.first().reading}" }
            val prefixLength = if (index % DictionaryFormat.BLOCK_SIZE == 0) {
                blockOffsets.add(data.size())
                0
            } else {
                commonPrefix(previous, key)
            }
            data.write(prefixLength)
            data.write(key.size - prefixLength)
            data.write(key, prefixLength, key.size - prefixLength)
            val entryBytes = encodeEntries(codec, words)
            writeVarint(data, entryBytes.size)
            data.write(entryBytes)
            previous = key
        }

        val dataOut = DataOutputStream(out.buffered())
        dataOut.writeInt(DictionaryFormat.MAGIC)
        dataOut.writeInt(DictionaryFormat.VERSION)
        dataOut.writeInt(codec.size)
        dataOut.writeInt(posClasses.size)
        dataOut.writeInt(matrixDim)
        dataOut.writeInt(DictionaryFormat.RESOLUTION)
        dataOut.writeInt(groups.size)
        dataOut.writeInt(blockOffsets.size)
        dataOut.writeInt(DictionaryFormat.BLOCK_SIZE)
        dataOut.writeInt(unknownNounId)
        dataOut.writeInt(data.size())
        dataOut.writeInt(userPosIds.size)
        dataOut.writeInt(segmentRules.leftClassCount)
        dataOut.writeInt(segmentRules.rightClassCount)
        for (i in 0 until codec.size) dataOut.writeChar(codec.charAt(i).code)
        for (posClass in posClasses) dataOut.writeByte(posClass.ordinal)
        for (id in userPosIds) dataOut.writeShort(id)
        writeSegmentRules(dataOut)
        dataOut.write(encodeMatrix())
        for (offset in blockOffsets) dataOut.writeInt(offset)
        data.writeTo(dataOut)
        dataOut.flush()
    }

    private fun writeSegmentRules(out: DataOutputStream) {
        val rules = segmentRules
        for (array in listOf(rules.leftClass, rules.rightClass, rules.prefixPenalty, rules.suffixPenalty)) {
            for (value in array) out.writeShort(value.coerceIn(0, 0xFFFF))
        }
        val bits = ByteArray((rules.boundary.size + 7) / 8)
        for ((i, b) in rules.boundary.withIndex()) {
            if (b) bits[i ushr 3] = (bits[i ushr 3].toInt() or (1 shl (i and 7))).toByte()
        }
        out.write(bits)
    }

    private fun encodeEntries(codec: CharCodec, words: List<WordEntry>): ByteArray {
        val bytes = ByteArrayOutputStream()
        writeVarint(bytes, words.size)
        val out = DataOutputStream(bytes)
        for (word in words) {
            val surfaceKind = when (word.surface) {
                word.reading -> DictionaryFormat.SURFACE_HIRAGANA
                Kana.toKatakana(word.reading) -> DictionaryFormat.SURFACE_KATAKANA
                else -> DictionaryFormat.SURFACE_EXPLICIT
            }
            var flags = surfaceKind shl DictionaryFormat.SURFACE_SHIFT
            if (word.lid != word.rid) flags = flags or DictionaryFormat.FLAG_RID_DIFFERS
            out.writeByte(flags)
            out.writeShort(word.lid)
            if (word.lid != word.rid) out.writeShort(word.rid)
            out.writeShort(word.cost.coerceIn(0, 0xFFFF))
            if (surfaceKind == DictionaryFormat.SURFACE_EXPLICIT) {
                val surface = checkNotNull(codec.encode(word.surface))
                out.flush()
                writeVarint(bytes, surface.size)
                bytes.write(surface)
            }
        }
        out.flush()
        return bytes.toByteArray()
    }

    private fun buildCodec(entries: List<WordEntry>): CharCodec {
        val counts = HashMap<Char, Int>()
        for (entry in entries) {
            for (c in entry.reading) counts.merge(c, 4, Int::plus)
            for (c in entry.surface) counts.merge(c, 1, Int::plus)
        }
        val chars = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Char, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        return CharCodec(chars.toCharArray())
    }

    private fun commonPrefix(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        var i = 0
        while (i < n && a[i] == b[i]) i++
        // 2 バイト文字の途中で切らない
        var p = 0
        var safe = 0
        while (p < i) {
            val step = if (b[p].toInt() and 0x80 == 0) 1 else 2
            if (p + step > i) break
            p += step
            safe = p
        }
        return safe
    }

    /** 行オフセット表 (dim + 1 個。最後は行列全体の長さ) と各行。 */
    private fun encodeMatrix(): ByteArray {
        val words = (matrixDim + 63) / 64
        val rows = ByteArrayOutputStream()
        val rowOffsets = IntArray(matrixDim + 1)
        val headerSize = (matrixDim + 1) * 4
        for (rid in 0 until matrixDim) {
            rowOffsets[rid] = headerSize + rows.size()
            val values = IntArray(matrixDim) { quantize(matrix[rid * matrixDim + it]) }
            val mode = values.toList().groupingBy { it }.eachCount().maxBy { it.value }.key
            val bitmap = LongArray(words)
            val extra = ByteArrayOutputStream()
            for (lid in 0 until matrixDim) {
                if (values[lid] != mode) {
                    bitmap[lid ushr 6] = bitmap[lid ushr 6] or (1L shl (lid and 63))
                    extra.write(values[lid])
                }
            }
            val out = DataOutputStream(rows)
            out.writeByte(mode)
            for (w in bitmap) out.writeLong(w)
            var rank = 0
            for (w in bitmap) {
                out.writeShort(rank)
                rank += java.lang.Long.bitCount(w)
            }
            out.flush()
            extra.writeTo(rows)
        }
        rowOffsets[matrixDim] = headerSize + rows.size()
        val all = ByteArrayOutputStream()
        val out = DataOutputStream(all)
        for (o in rowOffsets) out.writeInt(o)
        rows.writeTo(out)
        out.flush()
        return all.toByteArray()
    }

    private fun quantize(cost: Int): Int =
        ((cost + DictionaryFormat.RESOLUTION / 2) / DictionaryFormat.RESOLUTION).coerceIn(0, 254)

    private fun writeVarint(out: OutputStream, value: Int) {
        var v = value
        while (v >= 0x80) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v)
    }
}
