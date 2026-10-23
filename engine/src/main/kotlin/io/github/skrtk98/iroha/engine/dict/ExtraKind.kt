package io.github.skrtk98.iroha.engine.dict

/**
 * 変換のラティスには入れず、候補に足すだけの mozc のデータ。システム辞書の中で、読みの頭に
 * 私用領域の文字 [marker] を付けて分ける。入力の読みがこの文字で始まることは無いので、
 * 普通の検索には掛からない。
 */
enum class ExtraKind(val marker: Char) {
    /** 単漢字 (single_kanji.tsv)。 */
    SingleKanji(''),

    /** 記号 (symbol.tsv, symbol/categorized.tsv)。 */
    Symbol(''),

    /** 顔文字 (emoticon.tsv, emoticon/categorized.tsv)。 */
    Emoticon(''),

    /** 絵文字 (emoji_data.tsv, manual_emoji_data.tsv)。 */
    Emoji(''),

    /** よくある読み間違い。誤った読みで正しい表記を出す (reading_correction.tsv)。 */
    ReadingCorrection(''),

    /** 語に続く言い回し。予測にだけ使う (suffix.txt)。 */
    Suffix(''),

    /** 確定した語の次に出す候補。読みは確定した表記 (zero_query.def)。 */
    ZeroQuery(''),

    /** 確定した数字の次に出す助数詞など。読みは数字か default (zero_query_number.def)。 */
    ZeroQueryNumber(''),

    /** 続けて使われやすい語の並び。読みも表記も並びそのもの (collocation.txt)。 */
    Collocation(''),

    /** 予測に出さない語。読みも表記も語そのもの (suggestion_filter.txt)。 */
    SuggestionFilter(''),
    ;

    fun key(reading: CharSequence): String = marker + reading.toString()
}
