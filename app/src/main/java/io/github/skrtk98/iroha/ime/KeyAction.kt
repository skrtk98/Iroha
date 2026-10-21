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

    /** フリックのかな ⇔ 英字。 */
    data object CycleMode : KeyAction

    /** QWERTY のかな ⇔ 英字。 */
    data object ToggleKanaAlphabet : KeyAction

    /** 日英 ⇔ 数字・記号。それぞれ設定した配列の面に切り替える。 */
    data object ToggleSymbols : KeyAction

    /** 表示中の種類（日英か数字・記号か）の配列をフリック ⇔ QWERTY で切り替え、設定にも保存する。 */
    data object ToggleLayout : KeyAction

    data class SelectCandidate(val index: Int) : KeyAction
    data object ToggleCandidateList : KeyAction

    /** 変換中の文節を伸縮する。 */
    data class ResizeSegment(val delta: Int) : KeyAction

    /** キーボードの代わりに表示するパネルを切り替える。 */
    data class OpenPanel(val panel: Panel) : KeyAction
    data object ClosePanel : KeyAction

    /** カーソルモードの操作。 */
    data class Cursor(val command: CursorCommand) : KeyAction

    data class PasteClip(val text: String) : KeyAction
    data class TogglePinClip(val text: String) : KeyAction
    data class DeleteClip(val text: String) : KeyAction
    data object ClearClips : KeyAction

    data object StartVoice : KeyAction
    data object StopVoice : KeyAction

    /** マイクの権限を得るため設定画面を開く。 */
    data object RequestMicPermission : KeyAction

    /** 調整した高さ（百分率）と下の余白（dp）を保存して調整を終える。 */
    data class FinishResize(val heightPercent: Int, val bottomDp: Int) : KeyAction

    data object OpenSettings : KeyAction
    data object SwitchInputMethod : KeyAction
    data object HideKeyboard : KeyAction
}

/** キーボード領域に出すパネル。 */
enum class Panel { None, Cursor, Clipboard, Voice }

enum class CursorCommand {
    Left, Right, Up, Down, LineStart, LineEnd,
    ToggleSelection, SelectAll, Copy, Cut, Paste,
}
