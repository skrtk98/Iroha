package io.github.skrtk98.iroha.engine.dict

/**
 * ユーザー辞書で選べる品詞。[label] は Google 日本語入力 / Mozc の
 * ユーザー辞書 TSV と同じ名前にして相互にインポートできるようにする。
 */
enum class UserPos(val label: String, internal val mozcPrefix: String) {
    Noun("名詞", "名詞,一般,"),
    ProperNoun("固有名詞", "名詞,固有名詞,一般,"),
    PersonName("人名", "名詞,固有名詞,人名,一般,"),
    Surname("姓", "名詞,固有名詞,人名,姓,"),
    GivenName("名", "名詞,固有名詞,人名,名,"),
    Organization("組織", "名詞,固有名詞,組織,"),
    Place("地名", "名詞,固有名詞,地域,一般,"),
    SuruNoun("名詞サ変", "名詞,サ変接続,"),
    AdjectivalNoun("名詞形動", "名詞,形容動詞語幹,"),
    Abbreviation("短縮よみ", "名詞,一般,"),
    Emoticon("顔文字", "記号,一般,"),
    Symbol("記号", "記号,一般,"),
    Adverb("副詞", "副詞,一般,"),
    Interjection("感動詞", "感動詞,"),
    ;

    companion object {
        /** 未知の品詞名は名詞として扱う。 */
        fun fromLabel(label: String): UserPos = entries.firstOrNull { it.label == label } ?: Noun
    }
}
