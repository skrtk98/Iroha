package io.github.skrtk98.iroha.engine.text

/** フリック入力の「゛゜小」キーで直前の文字を循環させる。 */
object KanaModifier {
    private val cycles: List<String> = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご",
        "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど",
        "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ",
        "やゃ", "ゆゅ", "よょ", "わゎ",
        "カガ", "キギ", "クグ", "ケゲ", "コゴ",
        "サザ", "シジ", "スズ", "セゼ", "ソゾ",
        "タダ", "チヂ", "ツッヅ", "テデ", "トド",
        "ハバパ", "ヒビピ", "フブプ", "ヘベペ", "ホボポ",
        "アァ", "イィ", "ウゥヴ", "エェ", "オォ",
        "ヤャ", "ユュ", "ヨョ", "ワヮ",
    )

    private val next: Map<Char, Char> = buildMap {
        for (cycle in cycles) {
            for (i in cycle.indices) put(cycle[i], cycle[(i + 1) % cycle.length])
        }
    }

    /** 変化先があれば返す。対象外の文字なら null。 */
    fun cycle(c: Char): Char? = next[c]
}
