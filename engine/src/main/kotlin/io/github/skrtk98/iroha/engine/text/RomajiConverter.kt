package io.github.skrtk98.iroha.engine.text

/**
 * ローマ字列をひらがなに変換する。入力途中の未確定ローマ字は末尾にそのまま残す。
 */
object RomajiConverter {

    private val table: Map<String, String> = buildMap {
        val rows = listOf(
            "" to listOf("あ", "い", "う", "え", "お"),
            "k" to listOf("か", "き", "く", "け", "こ"),
            "s" to listOf("さ", "し", "す", "せ", "そ"),
            "t" to listOf("た", "ち", "つ", "て", "と"),
            "n" to listOf("な", "に", "ぬ", "ね", "の"),
            "h" to listOf("は", "ひ", "ふ", "へ", "ほ"),
            "m" to listOf("ま", "み", "む", "め", "も"),
            "y" to listOf("や", "い", "ゆ", "いぇ", "よ"),
            "r" to listOf("ら", "り", "る", "れ", "ろ"),
            "w" to listOf("わ", "うぃ", "う", "うぇ", "を"),
            "g" to listOf("が", "ぎ", "ぐ", "げ", "ご"),
            "z" to listOf("ざ", "じ", "ず", "ぜ", "ぞ"),
            "d" to listOf("だ", "ぢ", "づ", "で", "ど"),
            "b" to listOf("ば", "び", "ぶ", "べ", "ぼ"),
            "p" to listOf("ぱ", "ぴ", "ぷ", "ぺ", "ぽ"),
            "f" to listOf("ふぁ", "ふぃ", "ふ", "ふぇ", "ふぉ"),
            "j" to listOf("じゃ", "じ", "じゅ", "じぇ", "じょ"),
            "v" to listOf("ゔぁ", "ゔぃ", "ゔ", "ゔぇ", "ゔぉ"),
            "l" to listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ"),
            "x" to listOf("ぁ", "ぃ", "ぅ", "ぇ", "ぉ"),
            "q" to listOf("くぁ", "くぃ", "く", "くぇ", "くぉ"),
            "c" to listOf("か", "し", "く", "せ", "こ"),
        )
        val vowels = "aiueo"
        for ((consonant, kana) in rows) {
            for (i in vowels.indices) put(consonant + vowels[i], kana[i])
        }
        val youon = listOf(
            "ky" to "き", "sh" to "し", "sy" to "し", "ch" to "ち", "ty" to "ち", "cy" to "ち",
            "ny" to "に", "hy" to "ひ", "my" to "み", "ry" to "り", "gy" to "ぎ", "zy" to "じ",
            "jy" to "じ", "dy" to "ぢ", "by" to "び", "py" to "ぴ", "fy" to "ふ", "vy" to "ゔ",
        )
        for ((prefix, base) in youon) {
            put(prefix + "a", base + "ゃ")
            put(prefix + "u", base + "ゅ")
            put(prefix + "o", base + "ょ")
            put(prefix + "e", base + "ぇ")
            put(prefix + "i", base + "ぃ")
        }
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("ji", "じ"); put("fu", "ふ")
        put("she", "しぇ"); put("che", "ちぇ")
        put("tsa", "つぁ"); put("tsi", "つぃ"); put("tse", "つぇ"); put("tso", "つぉ")
        put("thi", "てぃ"); put("dhi", "でぃ"); put("thu", "てゅ"); put("dhu", "でゅ")
        put("twu", "とぅ"); put("dwu", "どぅ")
        put("wha", "うぁ"); put("whi", "うぃ"); put("whe", "うぇ"); put("who", "うぉ")
        put("nn", "ん"); put("n'", "ん"); put("xn", "ん")
        put("ltu", "っ"); put("xtu", "っ"); put("ltsu", "っ")
        put("lya", "ゃ"); put("lyu", "ゅ"); put("lyo", "ょ")
        put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
        put("lwa", "ゎ"); put("xwa", "ゎ"); put("lka", "ゕ"); put("lke", "ゖ")
        put("-", "ー"); put(",", "、"); put(".", "。"); put("[", "「"); put("]", "」")
        put("/", "・"); put("~", "〜")
        put("zh", "←"); put("zj", "↓"); put("zk", "↑"); put("zl", "→")
        put("z.", "…"); put("z,", "‥"); put("z/", "・"); put("z-", "〜")
    }

    private val prefixes: Set<String> = buildSet {
        for (key in table.keys) for (i in 1 until key.length) add(key.substring(0, i))
    }

    /**
     * ローマ字を含む文字列を変換する。変換できない末尾の途中入力は残す。
     * 例: "kanji" → "かんじ", "kank" → "かんk"
     */
    fun convert(input: CharSequence): String {
        val out = StringBuilder()
        var i = 0
        val lower = input.toString().lowercase()
        while (i < lower.length) {
            val c = lower[i]
            // 促音: 同じ子音の連続 (n を除く)
            if (i + 1 < lower.length && c == lower[i + 1] && c in "bcdfghjklmpqrstvwxyz" && c != 'n') {
                out.append('っ')
                i++
                continue
            }
            // n の後ろが子音（y と n を除く）なら「ん」
            if (c == 'n' && i + 1 < lower.length && lower[i + 1] !in "aiueoyn'") {
                out.append('ん')
                i++
                continue
            }
            var matched = false
            for (len in 4 downTo 1) {
                if (i + len > lower.length) continue
                val chunk = lower.substring(i, i + len)
                val kana = table[chunk] ?: continue
                out.append(kana)
                i += len
                matched = true
                break
            }
            if (matched) continue
            val rest = lower.substring(i)
            if (rest in prefixes) {
                // 入力途中: 元の大文字小文字のまま残す
                out.append(input, i, input.length)
                break
            }
            out.append(input[i])
            i++
        }
        return out.toString()
    }

    /** 確定時に末尾の n を「ん」として扱う。 */
    fun finish(input: CharSequence): String {
        val converted = convert(input)
        return if (converted.endsWith("n") || converted.endsWith("N")) {
            converted.dropLast(1) + "ん"
        } else {
            converted
        }
    }
}
