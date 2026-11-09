package io.github.skrtk98.iroha.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo

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
     * アカウント ID の欄などは、種類を文字のままにして [EditorInfo.IME_FLAG_FORCE_ASCII] で英字を求めることがある。
     */
    fun startsInAlphabet(inputType: Int, imeOptions: Int = 0): Boolean =
        isPassword(inputType) ||
            isText(inputType) && variationOf(inputType) in EMAIL_VARIATIONS ||
            imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0

    /**
     * Enter キーで実行する欄の動作（検索・送信など）。null なら改行する。
     * 複数行の欄でも、アプリが動作を指定して改行を求めていなければ動作を優先する（Google アプリの検索欄など）。
     * TextView は複数行の欄に [EditorInfo.IME_FLAG_NO_ENTER_ACTION] を自動で付けるので、ふつうのメモ欄は改行のまま。
     */
    fun enterAction(imeOptions: Int): Int? {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return action.takeIf { it in ENTER_KINDS }
    }

    /** Enter キーの表示。[enterAction] と同じ判定から決め、表示と動作を食い違わせない。 */
    fun enterKind(imeOptions: Int): EnterKind = enterAction(imeOptions)?.let(ENTER_KINDS::getValue) ?: EnterKind.Newline

    /** キーに表示を用意してある動作。前の欄へ戻る動作などは表示がないので改行として扱う。 */
    private val ENTER_KINDS = mapOf(
        EditorInfo.IME_ACTION_GO to EnterKind.Go,
        EditorInfo.IME_ACTION_SEARCH to EnterKind.Search,
        EditorInfo.IME_ACTION_SEND to EnterKind.Send,
        EditorInfo.IME_ACTION_NEXT to EnterKind.Next,
        EditorInfo.IME_ACTION_DONE to EnterKind.Done,
    )

    /** Enter で改行の文字を入れる、複数行の欄。 */
    fun entersNewlineAsText(inputType: Int): Boolean =
        isText(inputType) && inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

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
