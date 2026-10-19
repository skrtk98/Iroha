package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.CursorCommand
import io.github.skrtk98.iroha.ime.KeyAction

/**
 * カーソルモード。方向キーでカーソルを動かし、選択をオンにすると選択範囲を伸ばす。
 * 右の列は選択範囲への操作。
 */
@Composable
fun CursorPanel(
    selecting: Boolean,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun send(command: CursorCommand) = onAction(KeyAction.Cursor(command))
    val cell = Modifier.fillMaxHeight()
    Row(modifier) {
        Column(Modifier.weight(3f).fillMaxHeight()) {
            Row(Modifier.weight(1f)) {
                TextKey("行頭", { send(CursorCommand.LineStart) }, cell.weight(1f), KeyStyle.Function, 15.sp)
                IconKey(R.drawable.ic_keyboard_arrow_up, "上へ", { send(CursorCommand.Up) }, cell.weight(1f), KeyStyle.Character, repeat = true)
                TextKey("行末", { send(CursorCommand.LineEnd) }, cell.weight(1f), KeyStyle.Function, 15.sp)
            }
            Row(Modifier.weight(1f)) {
                IconKey(R.drawable.ic_keyboard_arrow_left, "左へ", { send(CursorCommand.Left) }, cell.weight(1f), KeyStyle.Character, repeat = true)
                TextKey(
                    if (selecting) "選択中" else "選択",
                    { send(CursorCommand.ToggleSelection) },
                    cell.weight(1f),
                    if (selecting) KeyStyle.Accent else KeyStyle.Function,
                    15.sp,
                )
                IconKey(R.drawable.ic_keyboard_arrow_right, "右へ", { send(CursorCommand.Right) }, cell.weight(1f), KeyStyle.Character, repeat = true)
            }
            Row(Modifier.weight(1f)) {
                Spacer(Modifier.weight(1f))
                IconKey(R.drawable.ic_keyboard_arrow_down, "下へ", { send(CursorCommand.Down) }, cell.weight(1f), KeyStyle.Character, repeat = true)
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.weight(1f)) {
                BackspaceKey(onAction, cell.weight(1f))
                TextKey("キーボードに戻る", { onAction(KeyAction.ClosePanel) }, cell.weight(2f), KeyStyle.Function, 15.sp)
            }
        }
        Column(Modifier.weight(1.3f).fillMaxHeight()) {
            val side = Modifier.weight(1f).fillMaxWidth()
            IconKey(R.drawable.ic_select_all, "すべて選択", { send(CursorCommand.SelectAll) }, side)
            IconKey(R.drawable.ic_content_copy, "コピー", { send(CursorCommand.Copy) }, side)
            IconKey(R.drawable.ic_content_cut, "切り取り", { send(CursorCommand.Cut) }, side)
            IconKey(R.drawable.ic_content_paste, "貼り付け", { send(CursorCommand.Paste) }, side)
        }
    }
}
