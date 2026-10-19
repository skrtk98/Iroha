package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.KeyboardLayout
import io.github.skrtk98.iroha.ime.EnterKind
import io.github.skrtk98.iroha.ime.KeyAction
import io.github.skrtk98.iroha.ime.KeyboardFace
import io.github.skrtk98.iroha.ime.SpaceKind

@Composable
fun BackspaceKey(onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    IconKey(R.drawable.ic_backspace, "削除", { onAction(KeyAction.Backspace) }, modifier, repeat = true)
}

@Composable
fun SpaceKey(kind: SpaceKind, onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val label = when (kind) {
        SpaceKind.Space -> "空白"
        SpaceKind.Convert -> "変換"
        SpaceKind.NextCandidate -> "次候補"
    }
    TextKey(label, { onAction(KeyAction.Space) }, modifier, style = KeyStyle.Function, fontSize = 15.sp)
}

@Composable
fun EnterKey(kind: EnterKind, composing: Boolean, onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    val label = when {
        composing -> "確定"
        kind == EnterKind.Go -> "移動"
        kind == EnterKind.Search -> "検索"
        kind == EnterKind.Send -> "送信"
        kind == EnterKind.Next -> "次へ"
        kind == EnterKind.Done -> "完了"
        else -> null
    }
    KeyButton(
        onClick = { onAction(KeyAction.Enter) },
        modifier = modifier,
        style = KeyStyle.Accent,
        description = label ?: "改行",
    ) { color ->
        if (label != null) {
            Text(label, color = color, fontSize = 15.sp)
        } else {
            Icon(painterResource(R.drawable.ic_keyboard_return), contentDescription = null, tint = color)
        }
    }
}

/**
 * 12 キーのフリック配列。左右の列が機能キー、中央 3 列が文字キー。
 */
@Composable
fun FlickKeyboard(
    face: KeyboardFace,
    shifted: Boolean,
    composing: Boolean,
    enterKind: EnterKind,
    spaceKind: SpaceKind,
    keyHeightDp: Int,
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
    val rowHeight = keyHeightDp.dp
    val onInput: (String) -> Unit = { onAction(KeyAction.Text(it)) }
    Box(modifier.onGloballyPositioned { root[0] = it }) {
        Row(Modifier.fillMaxWidth().height(rowHeight * 4)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                IconKey(R.drawable.ic_keyboard_arrow_left, "左へ", { onAction(KeyAction.CursorLeft) }, Modifier.weight(1f).fillMaxWidth(), repeat = true)
                IconKey(R.drawable.ic_keyboard_arrow_right, "右へ", { onAction(KeyAction.CursorRight) }, Modifier.weight(1f).fillMaxWidth(), repeat = true)
                IconKey(
                    R.drawable.ic_keyboard,
                    "QWERTY に切り替え",
                    { onAction(KeyAction.SwitchLayout(KeyboardLayout.Qwerty)) },
                    Modifier.weight(1f).fillMaxWidth(),
                )
                val modeLabel = when (face) {
                    KeyboardFace.FlickAlphabet -> "abc"
                    KeyboardFace.FlickNumber -> "123"
                    else -> "あいう"
                }
                TextKey(modeLabel, { onAction(KeyAction.CycleMode) }, Modifier.weight(1f).fillMaxWidth(), KeyStyle.Function, 14.sp)
            }
            Column(Modifier.weight(3f).fillMaxHeight()) {
                for (row in 0 until 4) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (col in 0 until 3) {
                            val cellModifier = Modifier.weight(1f).fillMaxHeight()
                            if (row == 3 && col == 0) {
                                ModifierKey(face, shifted, onAction, cellModifier)
                            } else {
                                val index = row * 3 + col - if (row == 3) 1 else 0
                                FlickKeyButton(
                                    key = keys[index],
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
private fun ModifierKey(face: KeyboardFace, shifted: Boolean, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    when (face) {
        KeyboardFace.FlickKana -> TextKey("゛゜小", { onAction(KeyAction.ToggleModifier) }, modifier, fontSize = 15.sp)
        KeyboardFace.FlickAlphabet -> TextKey(
            if (shifted) "A/a" else "a/A",
            { onAction(KeyAction.Shift) },
            modifier,
            if (shifted) KeyStyle.Accent else KeyStyle.Character,
            15.sp,
        )
        else -> TextKey("( )", { onAction(KeyAction.Text("()")) }, modifier, fontSize = 15.sp)
    }
}

/** QWERTY 配列。かな面ではローマ字入力になる。 */
@Composable
fun QwertyKeyboard(
    face: KeyboardFace,
    shifted: Boolean,
    composing: Boolean,
    enterKind: EnterKind,
    spaceKind: SpaceKind,
    keyHeightDp: Int,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowModifier = Modifier.fillMaxWidth().height(keyHeightDp.dp)
    val symbols = face == KeyboardFace.QwertySymbols
    val rows: List<List<String>> = if (symbols) {
        KeyLayouts.symbolRows
    } else {
        KeyLayouts.qwertyRows.map { row -> row.map { if (shifted) it.uppercaseChar().toString() else it.toString() } }
    }
    Column(modifier) {
        Row(rowModifier) { CharacterKeys(rows[0], onAction) }
        Row(rowModifier) {
            Spacer(Modifier.weight(0.5f))
            CharacterKeys(rows[1], onAction)
            Spacer(Modifier.weight(if (symbols) 0.5f else 0.5f))
        }
        Row(rowModifier) {
            if (symbols) {
                Spacer(Modifier.weight(0.5f))
            } else {
                IconKey(
                    R.drawable.ic_keyboard_arrow_up,
                    "シフト",
                    { onAction(KeyAction.Shift) },
                    Modifier.weight(1.5f).fillMaxHeight(),
                    style = if (shifted) KeyStyle.Accent else KeyStyle.Function,
                )
            }
            CharacterKeys(rows[2], onAction)
            BackspaceKey(onAction, Modifier.weight(1.5f).fillMaxHeight())
        }
        Row(rowModifier) {
            TextKey(
                if (symbols) "ABC" else "?123",
                { onAction(KeyAction.ToggleSymbols) },
                Modifier.weight(1.5f).fillMaxHeight(),
                KeyStyle.Function,
                14.sp,
            )
            TextKey(
                if (face == KeyboardFace.QwertyKana) "あ" else "A",
                { onAction(KeyAction.ToggleKanaAlphabet) },
                Modifier.weight(1f).fillMaxHeight(),
                KeyStyle.Function,
                16.sp,
            )
            IconKey(
                R.drawable.ic_dialpad,
                "フリックに切り替え",
                { onAction(KeyAction.SwitchLayout(KeyboardLayout.Flick)) },
                Modifier.weight(1f).fillMaxHeight(),
            )
            SpaceKey(spaceKind, onAction, Modifier.weight(3.5f).fillMaxHeight())
            val punctuation = if (face == KeyboardFace.QwertyKana) listOf("ー", "、", "。") else listOf("-", ",", ".")
            for (p in punctuation) {
                TextKey(p, { onAction(KeyAction.Text(p)) }, Modifier.weight(0.9f).fillMaxHeight())
            }
            EnterKey(enterKind, composing, onAction, Modifier.weight(1.5f).fillMaxHeight())
        }
    }
}

@Composable
private fun RowScope.CharacterKeys(keys: List<String>, onAction: (KeyAction) -> Unit) {
    for (key in keys) {
        TextKey(key, { onAction(KeyAction.Text(key)) }, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 数値入力欄向けのテンキー。 */
@Composable
fun NumberPad(
    enterKind: EnterKind,
    keyHeightDp: Int,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf("1", "2", "3", "-"),
        listOf("4", "5", "6", "+"),
        listOf("7", "8", "9", ","),
        listOf("*", "0", ".", "/"),
    )
    Row(modifier.fillMaxWidth().height(keyHeightDp.dp * 4)) {
        Column(Modifier.weight(4f).fillMaxHeight()) {
            for (row in rows) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for ((i, key) in row.withIndex()) {
                        TextKey(
                            key,
                            { onAction(KeyAction.Text(key)) },
                            Modifier.weight(1f).fillMaxHeight(),
                            style = if (i == 3 || key == "*" || key == ".") KeyStyle.Function else KeyStyle.Character,
                            fontSize = 24.sp,
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            BackspaceKey(onAction, Modifier.weight(1f).fillMaxWidth())
            IconKey(R.drawable.ic_space_bar, "空白", { onAction(KeyAction.Space) }, Modifier.weight(1f).fillMaxWidth())
            TextKey("あA", { onAction(KeyAction.ToggleNumberPad) }, Modifier.weight(1f).fillMaxWidth(), KeyStyle.Function, 15.sp)
            EnterKey(enterKind, composing = false, onAction = onAction, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
