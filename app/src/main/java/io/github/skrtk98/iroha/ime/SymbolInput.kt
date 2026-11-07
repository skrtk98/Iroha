package io.github.skrtk98.iroha.ime

import io.github.skrtk98.iroha.engine.text.Kana

/** かなの面から移った数字・記号の面で打った文字を、全角にして未確定文字列に入れるか決める。 */
object SymbolInput {
    /**
     * 未確定文字列に入れる全角の文字。null なら打った文字をそのまま確定する。[before] はカーソル直前の文字。
     * 数字は半角のままにする。数字の直後の、数をつなぐ記号も「12:30」「3.14」「2026/11/06」を崩さないよう半角のままにする。
     * 括弧や引用符は数を囲んでも左右の幅をそろえるため、数字の直後でも全角にする。
     */
    fun fullWidth(text: String, before: Char?): String? {
        if (text.isEmpty() || text.any { it in '0'..'9' }) return null
        if (before != null && before in '0'..'9' && text.all { it in NUMBER_JOINERS }) return null
        return Kana.toFullWidth(text)
    }

    /** 記号を全角で打つ面でのキーの印字。打つ文字は変えず、数字はそのまま、記号は全角で見せる。 */
    fun label(text: String): String = if (text.any { it in '0'..'9' }) text else Kana.toFullWidth(text)

    private const val NUMBER_JOINERS = ".,:/-+*^%=<>~"
}
