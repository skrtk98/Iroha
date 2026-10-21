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
    val shifted: Boolean = false,
    val composing: Boolean = false,
    val converting: Boolean = false,
    val candidates: List<String> = emptyList(),
    val selectedCandidate: Int = -1,
    val candidateListExpanded: Boolean = false,
    val enterKind: EnterKind = EnterKind.Newline,
    val spaceKind: SpaceKind = SpaceKind.Space,
    val keyHeightDp: Int = 52,
    /** キーボード全体の高さの倍率（百分率）。 */
    val keyboardHeightPercent: Int = 100,
    val keyBorders: Boolean = true,
    val qwertyNumberRow: Boolean = false,
    val vibrate: Boolean = true,
    val loading: Boolean = false,
    val panel: Panel = Panel.None,
    /** カーソルモードで選択範囲を伸ばしているか。 */
    val selecting: Boolean = false,
    val clips: List<io.github.skrtk98.iroha.data.ClipEntry> = emptyList(),
    val voice: VoiceUiState = VoiceUiState(),
)
