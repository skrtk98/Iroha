package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.Settings

/**
 * キーボードの高さ（百分率）と下の余白（dp）。ドラッグ中は小数で持ち、保存するときに丸める。
 * 各操作は dp 単位の縦の移動量（下向きが正）と、倍率 1% あたりの高さ [percentHeight] を受け取る。
 */
data class KeyboardSize(val heightPercent: Float, val bottomDp: Float) {
    /** 上端を動かす。下端はそのままで、高さだけが変わる。 */
    fun dragTop(dy: Float, percentHeight: Float) = copy(heightPercent = clampHeight(heightPercent - dy / percentHeight))

    /** 下端を動かす。上端はそのままなので、高さと余白が逆向きに同じだけ変わる。 */
    fun dragBottom(dy: Float, percentHeight: Float): KeyboardSize {
        val bottom = clampBottom(bottomDp - dy)
        val height = clampHeight(heightPercent + (bottomDp - bottom) / percentHeight)
        // 高さが上限・下限で止まったら、余白も止まった分だけにする
        return KeyboardSize(height, bottomDp - (height - heightPercent) * percentHeight)
    }

    /** 高さを変えずに全体を上下に動かす。 */
    fun move(dy: Float) = copy(bottomDp = clampBottom(bottomDp - dy))

    companion object {
        val Default = KeyboardSize(100f, 0f)

        private fun clampHeight(value: Float) =
            value.coerceIn(Settings.MIN_HEIGHT_PERCENT.toFloat(), Settings.MAX_HEIGHT_PERCENT.toFloat())

        private fun clampBottom(value: Float) = value.coerceIn(0f, Settings.MAX_BOTTOM_DP.toFloat())
    }
}

/** 読み上げの操作 1 回で動かす量。 */
private const val StepDp = 16f

/**
 * 高さと位置の調整枠。キーボードに重ねて表示し、下のキーには触れさせない。
 * 上端のつまみで高さ、下端のつまみで下端の位置、中央のつまみで全体の位置を変える。
 */
@Composable
fun ResizeFrame(
    size: KeyboardSize,
    percentHeight: Dp,
    onSizeChange: (KeyboardSize) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val current by rememberUpdatedState(size)
    val change by rememberUpdatedState(onSizeChange)
    val perPercent = percentHeight.value
    @Composable
    fun dragModifier(update: KeyboardSize.(Float) -> KeyboardSize) = Modifier.draggable(
        state = rememberDraggableState { px -> change(current.update(with(density) { px.toDp().value })) },
        orientation = Orientation.Vertical,
    )
    fun actions(vararg pairs: Pair<String, KeyboardSize.() -> KeyboardSize>) = pairs.map { (label, update) ->
        CustomAccessibilityAction(label) {
            change(current.update())
            true
        }
    }

    Box(
        modifier
            .background(colors.scrim.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .border(2.dp, colors.primary, RoundedCornerShape(16.dp))
            // 枠がタッチを受け取れば、重なっている下のキーには届かない。
            // 消費するとつまみのドラッグが取り消されるので、受け取るだけにする
            .pointerInput(Unit) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Handle(
            Modifier
                .align(Alignment.TopCenter)
                .then(dragModifier { dragTop(it, perPercent) })
                .semantics {
                    contentDescription = "上端。上下にドラッグして高さを変える"
                    customActions = actions(
                        "高くする" to { dragTop(-StepDp, perPercent) },
                        "低くする" to { dragTop(StepDp, perPercent) },
                    )
                },
        )
        Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onReset) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("リセット")
            }
            Box(
                Modifier
                    .size(48.dp)
                    .background(colors.surfaceContainerHighest, CircleShape)
                    .then(dragModifier { move(it) })
                    .semantics {
                        contentDescription = "上下にドラッグしてキーボード全体を動かす"
                        customActions = actions(
                            "上へ動かす" to { move(-StepDp) },
                            "下へ動かす" to { move(StepDp) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_open_with), contentDescription = null, tint = colors.onSurface)
            }
            Button(onClick = onDone) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("完了")
            }
        }
        Handle(
            Modifier
                .align(Alignment.BottomCenter)
                .then(dragModifier { dragBottom(it, perPercent) })
                .semantics {
                    contentDescription = "下端。上下にドラッグして下端の位置を変える"
                    customActions = actions(
                        "下端を上げる" to { dragBottom(-StepDp, perPercent) },
                        "下端を下げる" to { dragBottom(StepDp, perPercent) },
                    )
                },
        )
    }
}

/** 枠の上端・下端のつまみ。見た目は短い棒だが、横いっぱいの帯でドラッグを受ける。 */
@Composable
private fun Handle(modifier: Modifier) {
    Box(modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 48.dp, height = 5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
    }
}
