package io.github.skrtk98.iroha.engine.conv

/**
 * mozc の NumberRewriter に倣い、数で始まる候補の別の書き方を作る。
 * 「3千円」から「三千円」「3000円」「3,000円」「３０００円」のように、数の部分だけを書き換える。
 */
internal object NumberVariants {
    private const val KANJI_DIGITS = "〇一二三四五六七八九"
    private val SMALL_UNITS = mapOf('十' to 10L, '百' to 100L, '千' to 1000L)
    private val BIG_UNITS = mapOf('万' to 10_000L, '億' to 100_000_000L, '兆' to 1_000_000_000_000L)

    /** 万の位ごとの単位。大きい順。 */
    private val SECTIONS = listOf(1_000_000_000_000L to "兆", 100_000_000L to "億", 10_000L to "万")

    /** これを超える数は書き換えない。 */
    private const val MAX_VALUE = 9_999_999_999_999_999L

    /** [surface] の先頭の数を書き換えた表記。先頭が数でなければ空。元の表記は含めない。 */
    fun of(surface: String): List<String> {
        val length = numberPrefixLength(surface)
        if (length == 0) return emptyList()
        val value = parse(surface.substring(0, length)) ?: return emptyList()
        val rest = surface.substring(length)
        val arabic = value.toString()
        val forms = buildList {
            add(toKanji(value))
            add(arabic)
            if (value >= 10_000) add(toArabicWithUnits(value))
            if (value >= 1_000) add("%,d".format(value))
            add(toFullWidth(arabic))
        }
        return forms.map { it + rest }.filter { it != surface }.distinct()
    }

    private fun isNumberChar(c: Char) =
        c in '0'..'9' || c in '０'..'９' || c in KANJI_DIGITS || c in SMALL_UNITS || c in BIG_UNITS

    private fun numberPrefixLength(text: String): Int {
        var i = 0
        while (i < text.length && isNumberChar(text[i])) i++
        return i
    }

    private fun digitOf(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in '０'..'９' -> c - '０'
        else -> KANJI_DIGITS.indexOf(c)
    }

    /**
     * 算用数字・漢数字・単位の混ざった数を読む (3千、三千、二〇二四、1万2千 など)。
     * 単位の並びがおかしい・大きすぎるときは null。
     */
    fun parse(text: String): Long? {
        var total = 0L
        var section = 0L
        var current = -1L
        var lastBig = Long.MAX_VALUE
        var lastSmall = Long.MAX_VALUE
        for (c in text) {
            val digit = digitOf(c)
            when {
                digit >= 0 -> {
                    val base = if (current < 0) 0 else current
                    // 掛ける前に確かめ、桁あふれで負の数になるのを防ぐ
                    if (base > (MAX_VALUE - digit) / 10) return null
                    current = base * 10 + digit
                }
                c in SMALL_UNITS -> {
                    val unit = SMALL_UNITS.getValue(c)
                    if (unit >= lastSmall) return null
                    val count = if (current < 0) 1 else current
                    if (count > MAX_VALUE / unit) return null
                    section += count * unit
                    current = -1
                    lastSmall = unit
                }
                else -> {
                    val unit = BIG_UNITS.getValue(c)
                    if (unit >= lastBig) return null
                    section += if (current < 0) 0 else current
                    if (section == 0L) section = 1
                    if (section > MAX_VALUE / unit) return null
                    total += section * unit
                    section = 0
                    current = -1
                    lastBig = unit
                    lastSmall = Long.MAX_VALUE
                }
            }
        }
        val value = total + section + (if (current < 0) 0 else current)
        return value.takeIf { it <= MAX_VALUE }
    }

    /** 位取りの漢数字 (三千、十五、一万二千)。1 の十・百・千は「一」を付けない。 */
    fun toKanji(value: Long): String {
        if (value == 0L) return "〇"
        val out = StringBuilder()
        var rest = value
        for ((unit, name) in SECTIONS) {
            if (rest >= unit) {
                out.append(sectionToKanji((rest / unit).toInt())).append(name)
                rest %= unit
            }
        }
        if (rest > 0) out.append(sectionToKanji(rest.toInt()))
        return out.toString()
    }

    private fun sectionToKanji(value: Int): String {
        val out = StringBuilder()
        var rest = value
        for ((unit, name) in listOf(1000 to '千', 100 to '百', 10 to '十')) {
            val digit = rest / unit
            if (digit > 0) {
                if (digit > 1) out.append(KANJI_DIGITS[digit])
                out.append(name)
            }
            rest %= unit
        }
        if (rest > 0) out.append(KANJI_DIGITS[rest])
        return out.toString()
    }

    /** 万の位ごとに単位を付けた算用数字 (10万、1万2000)。 */
    private fun toArabicWithUnits(value: Long): String {
        val out = StringBuilder()
        var rest = value
        for ((unit, name) in SECTIONS) {
            if (rest >= unit) {
                out.append(rest / unit).append(name)
                rest %= unit
            }
        }
        if (rest > 0) out.append(rest)
        return out.toString()
    }

    private fun toFullWidth(arabic: String) = arabic.map { '０' + (it - '0') }.joinToString("")
}
