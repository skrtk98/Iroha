package io.github.skrtk98.iroha.ime

/** キーボードから IME への操作。 */
sealed interface KeyAction {
    /** 文字の入力。かな入力モードでは未確定文字列に、それ以外では直接エディタに入る。 */
    data class Text(val text: String) : KeyAction

    data object Backspace : KeyAction
    data object Space : KeyAction
    data object Enter : KeyAction
    data object CursorLeft : KeyAction
    data object CursorRight : KeyAction

    /** 濁点・半濁点・小書きの切り替え。 */
    data object ToggleModifier : KeyAction

    /** 英字の大文字・小文字切り替え。 */
    data object Shift : KeyAction

    /** フリックのかな → 英字 → 数字の切り替え。 */
    data object CycleMode : KeyAction

    /** QWERTY のかな ⇔ 英字。 */
    data object ToggleKanaAlphabet : KeyAction

    /** QWERTY の記号面。 */
    data object ToggleSymbols : KeyAction

    data class SwitchLayout(val layout: io.github.skrtk98.iroha.data.KeyboardLayout) : KeyAction
    data object ToggleNumberPad : KeyAction

    data class SelectCandidate(val index: Int) : KeyAction
    data object ToggleCandidateList : KeyAction

    /** 変換中の文節を伸縮する。 */
    data class ResizeSegment(val delta: Int) : KeyAction

    data object OpenSettings : KeyAction
    data object SwitchInputMethod : KeyAction
    data object HideKeyboard : KeyAction
}
