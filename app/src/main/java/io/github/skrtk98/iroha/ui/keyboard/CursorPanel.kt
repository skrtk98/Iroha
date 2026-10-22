package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.CursorCommand
import io.github.skrtk98.iroha.ime.KeyAction

/**
 * カーソルモード。上の 3 段が方向キーで、選択をオンにすると選択範囲を伸ばす。
 * 最下段は選択範囲への操作。
 */
@Composable
fun CursorPanel(
    selecting: Boolean,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun send(command: CursorCommand) = onAction(KeyAction.Cursor(command))
    val cell = Modifier.fillMaxHeight()
    // キーボードへは上部のカーソル移動ボタンで戻る
    Column(modifier) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Spacer(cell.weight(1f))
            IconKey(R.drawable.ic_keyboard_arrow_up, "上へ", { send(CursorCommand.Up) }, cell.weight(1f), KeyStyle.Character, repeat = true)
            BackspaceKey(onAction, cell.weight(1f))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            IconKey(R.drawable.ic_keyboard_arrow_left, "左へ", { send(CursorCommand.Left) }, cell.weight(1f), KeyStyle.Character, repeat = true)
            IconKey(
                R.drawable.ic_select,
                if (selecting) "選択中" else "選択",
                { send(CursorCommand.ToggleSelection) },
                cell.weight(1f),
                if (selecting) KeyStyle.Accent else KeyStyle.Function,
            )
            IconKey(R.drawable.ic_keyboard_arrow_right, "右へ", { send(CursorCommand.Right) }, cell.weight(1f), KeyStyle.Character, repeat = true)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            IconKey(R.drawable.ic_first_page, "行頭へ", { send(CursorCommand.LineStart) }, cell.weight(1f))
            IconKey(R.drawable.ic_keyboard_arrow_down, "下へ", { send(CursorCommand.Down) }, cell.weight(1f), KeyStyle.Character, repeat = true)
            IconKey(R.drawable.ic_last_page, "行末へ", { send(CursorCommand.LineEnd) }, cell.weight(1f))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            IconKey(R.drawable.ic_select_all, "すべて選択", { send(CursorCommand.SelectAll) }, cell.weight(1f))
            IconKey(R.drawable.ic_content_copy, "コピー", { send(CursorCommand.Copy) }, cell.weight(1f))
            IconKey(R.drawable.ic_content_cut, "切り取り", { send(CursorCommand.Cut) }, cell.weight(1f))
            IconKey(R.drawable.ic_content_paste_go, "貼り付け", { send(CursorCommand.Paste) }, cell.weight(1f))
        }
    }
}
