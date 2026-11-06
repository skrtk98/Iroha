package io.github.skrtk98.iroha.ime

import io.github.skrtk98.iroha.engine.text.Kana

/** かなの面から移った数字・記号の面で打った文字を、全角にして未確定文字列に入れるか決める。 */
object SymbolInput {
    /**
     * 未確定文字列に入れる全角の文字。null なら打った文字をそのまま確定する。
     * 数字は半角のままにする。数字の直後の記号も、「12:30」「3.14」「2026/11/06」を崩さないよう半角のままにする。
     */
    fun fullWidth(text: String, afterDigit: Boolean): String? {
        if (text.isEmpty() || afterDigit || text.any { it in '0'..'9' }) return null
        return Kana.toFullWidth(text)
    }
}
