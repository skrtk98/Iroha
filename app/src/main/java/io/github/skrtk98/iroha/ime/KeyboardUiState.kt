package io.github.skrtk98.iroha.ime

import androidx.compose.runtime.Immutable
import io.github.skrtk98.iroha.data.KeyboardLayout

/** 表示中のキー配列。 */
enum class KeyboardFace {
    FlickKana,
    FlickAlphabet,
    FlickNumber,
    QwertyKana,
    QwertyAlphabet,
    QwertySymbols,
    NumberPad,
}

enum class EnterKind { Newline, Done, Go, Search, Send, Next, Confirm }

enum class SpaceKind { Space, Convert, NextCandidate }

@Immutable
data class KeyboardUiState(
    val layout: KeyboardLayout = KeyboardLayout.Flick,
    val face: KeyboardFace = KeyboardFace.FlickKana,
    val shifted: Boolean = false,
    val composing: Boolean = false,
    val converting: Boolean = false,
    val candidates: List<String> = emptyList(),
    val selectedCandidate: Int = -1,
    val candidateListExpanded: Boolean = false,
    val enterKind: EnterKind = EnterKind.Newline,
    val spaceKind: SpaceKind = SpaceKind.Space,
    val keyHeightDp: Int = 52,
    val vibrate: Boolean = true,
    val loading: Boolean = false,
    val panel: Panel = Panel.None,
    /** カーソルモードで選択範囲を伸ばしているか。 */
    val selecting: Boolean = false,
)
