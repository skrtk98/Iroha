package io.github.skrtk98.iroha.ime

import android.text.InputType

/** 入力欄の種類（EditorInfo.inputType）から、キーボードの始め方を決める。 */
object InputFields {
    /** 数字だけを受け付ける欄。数字の面で始め、文字は変換せずそのまま入れる。 */
    fun isNumeric(inputType: Int): Boolean {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        return inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME
    }

    /** パスワード欄。打った文字が学習に残らないよう、日本語に切り替えても変換しない。 */
    fun isPassword(inputType: Int): Boolean = isText(inputType) && variationOf(inputType) in PASSWORD_VARIATIONS

    /**
     * 英字の面で始める欄。URL の欄は Chrome のアドレスバーのように検索を兼ねることが多いので、日本語で始める。
     */
    fun startsInAlphabet(inputType: Int): Boolean =
        isPassword(inputType) || isText(inputType) && variationOf(inputType) in EMAIL_VARIATIONS

    private fun isText(inputType: Int) = inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT

    private fun variationOf(inputType: Int) = inputType and InputType.TYPE_MASK_VARIATION

    private val PASSWORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )

    private val EMAIL_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
    )
}
