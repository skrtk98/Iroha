package io.github.skrtk98.iroha.ime

import androidx.compose.runtime.Immutable

/** 表示中のキー配列。 */
enum class KeyboardFace {
    FlickKana,
    FlickAlphabet,
    FlickNumber,
    QwertyKana,
    QwertyAlphabet,
    QwertySymbols,
}

enum class EnterKind { Newline, Done, Go, Search, Send, Next, Confirm }

enum class SpaceKind { Space, Convert, NextCandidate }

/** シフトの状態。タップで次の 1 文字だけ、長押しで解除するまで大文字にする。 */
enum class ShiftState {
    Off, Once, Locked;

    fun tap() = if (this == Off) Once else Off

    /** 1 文字入力した後の状態。 */
    fun afterInput() = if (this == Once) Off else this
}

enum class VoiceStatus { Idle, Listening, NeedsPermission, Error }

@Immutable
data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val partial: String = "",
    val message: String = "",
)

@Immutable
data class KeyboardUiState(
    val face: KeyboardFace = KeyboardFace.FlickKana,
    /** 数字・記号の面から戻る先の日英の面。 */
    val textFace: KeyboardFace = KeyboardFace.FlickKana,
    val shift: ShiftState = ShiftState.Off,
    /** QWERTY の数字・記号の面で、2 ページ目を出しているか。 */
    val moreSymbols: Boolean = false,
    val composing: Boolean = false,
    val converting: Boolean = false,
    val candidates: List<String> = emptyList(),
    val selectedCandidate: Int = -1,
    val candidateListExpanded: Boolean = false,
    /** 確定した語の後に続けて出す候補 (ありがとう → ございます など)。入力していないときだけ出す。 */
    val followUps: List<String> = emptyList(),
    val enterKind: EnterKind = EnterKind.Newline,
    val spaceKind: SpaceKind = SpaceKind.Space,
    /** キーボード全体の高さの倍率（百分率）。 */
    val keyboardHeightPercent: Int = 100,
    /** 高さの調整中か。上部のボタン列が調整バーになる。 */
    val resizing: Boolean = false,
    val keyBorders: Boolean = true,
    val qwertyNumberRow: Boolean = false,
    val vibrate: Boolean = true,
    val loading: Boolean = false,
    val panel: Panel = Panel.None,
    /** カーソルモードで選択範囲を伸ばしているか。 */
    val selecting: Boolean = false,
    val clips: List<io.github.skrtk98.iroha.data.ClipEntry> = emptyList(),
    val voice: VoiceUiState = VoiceUiState(),
) {
    val shifted get() = shift != ShiftState.Off
}
