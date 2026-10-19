package io.github.skrtk98.iroha.engine.dict

/**
 * 辞書内の文字を可変長コードにする。出現頻度の高い 127 文字は 1 バイト、
 * それ以外は 2 バイト。符号は接頭語なしなので、部分文字列の符号は全体の符号の部分列になる。
 */
class CharCodec(private val chars: CharArray) {
    private val codeOf = IntArray(65536) { -1 }

    init {
        require(chars.size <= MAX_CHARS) { "too many chars: ${chars.size}" }
        for (i in chars.indices) codeOf[chars[i].code] = i
    }

    val size: Int get() = chars.size

    fun charAt(index: Int): Char = chars[index]

    fun contains(c: Char): Boolean = codeOf[c.code] >= 0

    /** 1 文字の符号長。未登録の文字は -1。 */
    fun byteLength(c: Char): Int {
        val index = codeOf[c.code]
        return when {
            index < 0 -> -1
            index < ONE_BYTE_CHARS -> 1
            else -> 2
        }
    }

    /** 未登録の文字を含む場合は null。 */
    fun encode(text: CharSequence): ByteArray? {
        var length = 0
        for (c in text) {
            val l = byteLength(c)
            if (l < 0) return null
            length += l
        }
        val out = ByteArray(length)
        var pos = 0
        for (c in text) pos = write(c, out, pos)
        return out
    }

    /**
     * 文字列を符号化し、各文字境界のバイト位置を [offsets] に書く。
     * 未登録の文字に到達したらそこで止め、符号化できた文字数を返す。
     */
    fun encodePrefix(text: CharSequence, out: ByteArray, offsets: IntArray): Int {
        var pos = 0
        offsets[0] = 0
        for (i in text.indices) {
            val c = text[i]
            if (byteLength(c) < 0) return i
            pos = write(c, out, pos)
            offsets[i + 1] = pos
        }
        return text.length
    }

    private fun write(c: Char, out: ByteArray, pos: Int): Int {
        val index = codeOf[c.code]
        return if (index < ONE_BYTE_CHARS) {
            out[pos] = (index + 1).toByte()
            pos + 1
        } else {
            val v = index - ONE_BYTE_CHARS
            out[pos] = (0x80 or (v ushr 8)).toByte()
            out[pos + 1] = v.toByte()
            pos + 2
        }
    }

    /** [bytes] の [start] から [length] バイトを文字列に戻す。 */
    fun decode(bytes: ByteArray, start: Int, length: Int, out: StringBuilder) {
        var pos = start
        val end = start + length
        while (pos < end) {
            val b = bytes[pos].toInt() and 0xFF
            if (b < 0x80) {
                out.append(chars[b - 1])
                pos++
            } else {
                val v = ((b and 0x7F) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
                out.append(chars[v + ONE_BYTE_CHARS])
                pos += 2
            }
        }
    }

    companion object {
        const val ONE_BYTE_CHARS = 127
        const val MAX_CHARS = ONE_BYTE_CHARS + 0x8000
    }
}
