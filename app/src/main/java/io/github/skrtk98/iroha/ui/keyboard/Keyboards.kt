package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.EnterKind
import io.github.skrtk98.iroha.ime.KeyAction
import io.github.skrtk98.iroha.ime.KeyboardFace
import io.github.skrtk98.iroha.ime.ShiftState
import io.github.skrtk98.iroha.ime.SpaceKind

@Composable
fun BackspaceKey(onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    IconKey(R.drawable.ic_backspace, "削除", { onAction(KeyAction.Backspace) }, modifier, repeat = true)
}

/** 空白キー。入力中は変換・次候補になる。どこにあるかすぐ分かるよう、枠の設定によらず色を付ける。 */
@Composable
fun SpaceKey(kind: SpaceKind, onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val onClick = { onAction(KeyAction.Space) }
    when (kind) {
        SpaceKind.Space -> IconKey(R.drawable.ic_space_bar, "空白", onClick, modifier, KeyStyle.Secondary)
        SpaceKind.Convert -> TextKey("変換", onClick, modifier, KeyStyle.Secondary, 15.sp)
        SpaceKind.NextCandidate -> TextKey("次候補", onClick, modifier, KeyStyle.Secondary, 15.sp)
    }
}

/**
 * 確定・改行キー。入力欄の求める動作は記号で示し、入力中の確定だけは対応する記号が無いので文字にする。
 */
@Composable
fun EnterKey(kind: EnterKind, composing: Boolean, onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val onClick = { onAction(KeyAction.Enter) }
    if (composing) {
        TextKey("確定", onClick, modifier, KeyStyle.Accent, 15.sp)
        return
    }
    val (icon, description) = when (kind) {
        EnterKind.Go -> R.drawable.ic_arrow_forward to "移動"
        EnterKind.Search -> R.drawable.ic_search to "検索"
        EnterKind.Send -> R.drawable.ic_send to "送信"
        EnterKind.Next -> R.drawable.ic_keyboard_tab to "次へ"
        EnterKind.Done -> R.drawable.ic_check to "完了"
        else -> R.drawable.ic_keyboard_return to "改行"
    }
    IconKey(icon, description, onClick, modifier, KeyStyle.Accent)
}

/**
 * 12 キーのフリック配列。左右の列が機能キー、中央 3 列が文字キー。高さは [modifier] で決める。
 * 配列やテンキーの切り替えはどの面でも使うのでツールバーに置き、ここには面ごとのキーだけを置く。
 */
@Composable
fun FlickKeyboard(
    face: KeyboardFace,
    textFace: KeyboardFace,
    shift: ShiftState,
    composing: Boolean,
    enterKind: EnterKind,
    spaceKind: SpaceKind,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = when (face) {
        KeyboardFace.FlickAlphabet -> KeyLayouts.flickAlphabet
        KeyboardFace.FlickNumber -> KeyLayouts.flickNumber
        else -> KeyLayouts.flickKana
    }
    var guide by remember { mutableStateOf<FlickGuide?>(null) }
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val onInput: (String) -> Unit = { onAction(KeyAction.Text(it)) }
    Box(modifier.onGloballyPositioned { root[0] = it }) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                IconKey(R.drawable.ic_keyboard_arrow_left, "左へ", { onAction(KeyAction.CursorLeft) }, Modifier.weight(1f).fillMaxWidth(), repeat = true)
                IconKey(R.drawable.ic_keyboard_arrow_right, "右へ", { onAction(KeyAction.CursorRight) }, Modifier.weight(1f).fillMaxWidth(), repeat = true)
                // 日英 ⇔ 数字・記号のキーは、どちらの面でも 3 段目に置く
                if (face == KeyboardFace.FlickNumber) {
                    // 右列の確定キーと対になるよう 2 段分の高さにする
                    TextKey(textFaceLabel(textFace), { onAction(KeyAction.ToggleSymbols) }, Modifier.weight(2f).fillMaxWidth(), KeyStyle.Function, 14.sp)
                } else {
                    TextKey("☆123", { onAction(KeyAction.ToggleSymbols) }, Modifier.weight(1f).fillMaxWidth(), KeyStyle.Function, 14.sp)
                    TextKey(
                        if (face == KeyboardFace.FlickAlphabet) "abc" else "あいう",
                        { onAction(KeyAction.ToggleKanaAlphabet) },
                        Modifier.weight(1f).fillMaxWidth(),
                        KeyStyle.Function,
                        14.sp,
                    )
                }
            }
            Column(Modifier.weight(3f).fillMaxHeight()) {
                for (row in 0 until 4) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val cellModifier = Modifier.weight(1f).fillMaxHeight()
                            val key = when {
                                row == 3 && col == 0 && face == KeyboardFace.FlickNumber -> KeyLayouts.flickNumberBrackets
                                row == 3 && col == 0 -> null
                                else -> keys[row * 3 + col - if (row == 3) 1 else 0]
                            }
                            if (key == null) {
                                ModifierKey(face, shift, onAction, cellModifier)
                            } else {
                                FlickKeyButton(
                                    key = key,
                                    onInput = onInput,
                                    onGuide = { guide = it },
                                    rootCoordinates = { root[0] },
                                    modifier = cellModifier,
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BackspaceKey(onAction, Modifier.weight(1f).fillMaxWidth())
                SpaceKey(spaceKind, onAction, Modifier.weight(1f).fillMaxWidth())
                EnterKey(enterKind, composing, onAction, Modifier.weight(2f).fillMaxWidth())
            }
        }
        guide?.let { FlickGuideOverlay(it, Modifier.matchParentSize()) }
    }
}

@Composable
private fun ModifierKey(face: KeyboardFace, shift: ShiftState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    when (face) {
        KeyboardFace.FlickKana -> TextKey("゛゜小", { onAction(KeyAction.ToggleModifier) }, modifier, fontSize = 15.sp)
        KeyboardFace.FlickAlphabet -> if (shift == ShiftState.Locked) {
            ShiftKey(shift, onAction, modifier)
        } else {
            TextKey(
                if (shift == ShiftState.Once) "A/a" else "a/A",
                { onAction(KeyAction.Shift) },
                modifier,
                if (shift == ShiftState.Once) KeyStyle.Accent else KeyStyle.Character,
                15.sp,
                onLongClick = { onAction(KeyAction.LockShift) },
                longClickLabel = "大文字に固定",
            )
        }
        else -> Unit
    }
}

/** シフトキー。タップで次の 1 文字だけ、長押しで解除するまで大文字にする。固定中は下線付きの矢印で示す。 */
@Composable
private fun ShiftKey(shift: ShiftState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    IconKey(
        if (shift == ShiftState.Locked) R.drawable.ic_keyboard_capslock else R.drawable.ic_keyboard_arrow_up,
        when (shift) {
            ShiftState.Off -> "シフト"
            ShiftState.Once -> "シフト（1 文字）"
            ShiftState.Locked -> "シフト（固定中）"
        },
        { onAction(KeyAction.Shift) },
        modifier,
        style = if (shift == ShiftState.Off) KeyStyle.Function else KeyStyle.Accent,
        onLongClick = { onAction(KeyAction.LockShift) },
        longClickLabel = "大文字に固定",
    )
}

/** 数字の段の高さ。英字の段 1 段を 1 とした比。 */
private const val QwertyNumberRowWeight = 0.8f

/** QWERTY 配列。かな面ではローマ字入力になる。[numberRow] なら日英の面で英字の上に数字の段を出す。 */
@Composable
fun QwertyKeyboard(
    face: KeyboardFace,
    textFace: KeyboardFace,
    shift: ShiftState,
    composing: Boolean,
    enterKind: EnterKind,
    spaceKind: SpaceKind,
    numberRow: Boolean,
    moreSymbols: Boolean,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val symbols = face == KeyboardFace.QwertySymbols
    val rows: List<List<String>> = if (symbols) {
        if (moreSymbols) KeyLayouts.moreSymbolRows else KeyLayouts.symbolRows
    } else {
        KeyLayouts.qwertyRows.map { row -> row.map { if (shift != ShiftState.Off) it.uppercaseChar().toString() else it.toString() } }
    }
    val punctuation = KeyLayouts.qwertyPunctuation(face == KeyboardFace.QwertyKana, shift != ShiftState.Off)
    Column(modifier) {
        val rowModifier = Modifier.fillMaxWidth().weight(1f)
        if (numberRow && !symbols) {
            Row(Modifier.fillMaxWidth().weight(QwertyNumberRowWeight)) { CharacterKeys(KeyLayouts.symbolRows[0], onAction) }
        }
        Row(rowModifier) { CharacterKeys(rows[0], onAction) }
        val kana = face == KeyboardFace.QwertyKana
        Row(rowModifier) {
            if (symbols) {
                Spacer(Modifier.weight(0.5f))
                CharacterKeys(rows[1], onAction)
                Spacer(Modifier.weight(0.5f))
            } else {
                // 日英の面は長音・ハイフンを l の右に置き、下段を句読点と ！？ に空ける
                CharacterKeys(rows[1] + punctuation.dash, onAction)
            }
        }
        Row(rowModifier) {
            if (symbols) {
                // シフトの位置で記号の 2 ページを切り替える。ラベルは切り替え先を表す
                TextKey(
                    if (moreSymbols) "?123" else "=\\<",
                    { onAction(KeyAction.ToggleMoreSymbols) },
                    Modifier.weight(1.5f).fillMaxHeight(),
                    KeyStyle.Function,
                    14.sp,
                )
            } else {
                ShiftKey(shift, onAction, Modifier.weight(1.5f).fillMaxHeight())
            }
            CharacterKeys(rows[2], onAction)
            BackspaceKey(onAction, Modifier.weight(1.5f).fillMaxHeight())
        }
        Row(rowModifier) {
            TextKey(
                if (symbols) textFaceLabel(textFace) else "?123",
                { onAction(KeyAction.ToggleSymbols) },
                Modifier.weight(1.5f).fillMaxHeight(),
                KeyStyle.Function,
                14.sp,
            )
            // 空白が中央に来るよう、左右に同じ数の記号を置く。日英の切り替えは上部のボタンで行う
            val (left, right) = when {
                symbols && moreSymbols -> listOf("<") to listOf(">")
                symbols -> listOf(",") to listOf(".")
                else -> punctuation.left to punctuation.right
            }
            PunctuationKeys(left, onAction)
            SpaceKey(spaceKind, onAction, Modifier.weight(if (symbols) 6.2f else 4.4f).fillMaxHeight())
            PunctuationKeys(right, onAction)
            EnterKey(enterKind, composing, onAction, Modifier.weight(1.5f).fillMaxHeight())
        }
    }
}

@Composable
private fun RowScope.PunctuationKeys(keys: List<String>, onAction: (KeyAction) -> Unit) {
    for (key in keys) {
        TextKey(key, { onAction(KeyAction.Text(key)) }, Modifier.weight(0.9f).fillMaxHeight())
    }
}

@Composable
private fun RowScope.CharacterKeys(keys: List<String>, onAction: (KeyAction) -> Unit) {
    for (key in keys) {
        TextKey(key, { onAction(KeyAction.Text(key)) }, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 数字・記号の面から日英に戻るキーのラベル。戻り先の面を表す。 */
private fun textFaceLabel(textFace: KeyboardFace) = when (textFace) {
    KeyboardFace.FlickAlphabet -> "abc"
    KeyboardFace.QwertyAlphabet -> "ABC"
    else -> "あいう"
}
