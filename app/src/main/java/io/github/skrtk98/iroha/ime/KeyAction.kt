package io.github.skrtk98.iroha.ime

import io.github.skrtk98.iroha.data.ClipEntry

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

    /** シフトのタップ。オフなら次の 1 文字だけ大文字にし、オンなら解除する。 */
    data object Shift : KeyAction

    /** シフトの長押し。解除するまで大文字にする。 */
    data object LockShift : KeyAction

    /** 日本語 ⇔ 英字。配列はそのままで、数字・記号の面からは切り替えた日英の面に戻る。 */
    data object ToggleKanaAlphabet : KeyAction

    /** 日英 ⇔ 数字・記号。それぞれ設定した配列の面に切り替える。 */
    data object ToggleSymbols : KeyAction

    data class SelectCandidate(val index: Int) : KeyAction
    data object ToggleCandidateList : KeyAction

    /** 変換中の文節を伸縮する。 */
    data class ResizeSegment(val delta: Int) : KeyAction

    /** キーボードの代わりに表示するパネルを切り替える。 */
    data class OpenPanel(val panel: Panel) : KeyAction
    data object ClosePanel : KeyAction

    /** カーソルモードの操作。 */
    data class Cursor(val command: CursorCommand) : KeyAction

    data class PasteClip(val clip: ClipEntry) : KeyAction

    /** クリップボード履歴で選んだ項目（[ClipEntry.key]）をまとめて固定する、または固定を外す。 */
    data class PinClips(val keys: Set<String>, val pinned: Boolean) : KeyAction

    /** クリップボード履歴で選んだ項目（[ClipEntry.key]）をまとめて消す。 */
    data class DeleteClips(val keys: Set<String>) : KeyAction
    data object ClearClips : KeyAction

    data object StartVoice : KeyAction
    data object StopVoice : KeyAction

    /** マイクの権限を得るため設定画面を開く。 */
    data object RequestMicPermission : KeyAction

    /** 調整した高さ（百分率）を保存して調整を終える。 */
    data class FinishResize(val heightPercent: Int) : KeyAction

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
