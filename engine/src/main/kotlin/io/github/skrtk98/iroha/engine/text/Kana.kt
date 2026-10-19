package io.github.skrtk98.iroha.engine.text

/** ひらがな・カタカナ・全角半角の変換をまとめた文字ユーティリティ。 */
object Kana {
    private const val HIRA_START = 'ぁ'
    private const val HIRA_END = 'ゖ'
    private const val KATA_OFFSET = 0x60

    fun isHiragana(c: Char): Boolean = c in HIRA_START..HIRA_END || c == 'ー'

    fun isKatakana(c: Char): Boolean = c in 'ァ'..'ヺ' || c == 'ー'

    fun toKatakana(text: CharSequence): String = buildString(text.length) {
        for (c in text) append(if (c in HIRA_START..HIRA_END) c + KATA_OFFSET else c)
    }

    fun toHiragana(text: CharSequence): String = buildString(text.length) {
        for (c in text) append(if (c in 'ァ'..'ヶ') c - KATA_OFFSET else c)
    }

    /** ASCII の印字可能文字を全角に変換する。 */
    fun toFullWidth(text: CharSequence): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    ' ' -> '　'
                    in '!'..'~' -> c + 0xFEE0
                    else -> c
                },
            )
        }
    }

    /** 全角英数記号を半角に戻す。 */
    fun toHalfWidth(text: CharSequence): String = buildString(text.length) {
        for (c in text) {
            append(
                when (c) {
                    '　' -> ' '
                    in '！'..'～' -> c - 0xFEE0
                    else -> c
                },
            )
        }
    }

    private const val HALF_KATA_SOURCE =
        "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン"
    private val halfKataByFull: Map<Char, String> = buildMap {
        for (i in HALF_KATA_SOURCE.indices) put(HALF_KATA_SOURCE[i], (0xFF61 + i).toChar().toString())
        val voiced = "ガギグゲゴザジズゼゾダヂヅデドバビブベボ"
        val voicedBase = "カキクケコサシスセソタチツテトハヒフヘホ"
        for (i in voiced.indices) put(voiced[i], getValue(voicedBase[i]) + "ﾞ")
        val semi = "パピプペポ"
        val semiBase = "ハヒフヘホ"
        for (i in semi.indices) put(semi[i], getValue(semiBase[i]) + "ﾟ")
        put('ヴ', "ｳﾞ")
    }

    init {
        check(HALF_KATA_SOURCE.length == 0xFF9D - 0xFF61 + 1)
    }

    /** カタカナ（ひらがなを含む）を半角カタカナにする。 */
    fun toHalfWidthKatakana(text: CharSequence): String = buildString(text.length) {
        for (c in toKatakana(text)) append(halfKataByFull[c] ?: c.toString())
    }
}
