package io.github.skrtk98.iroha.ui.keyboard

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.Settings
import kotlin.math.floor
import kotlin.math.roundToInt

/** キーボードの高さ（百分率）。ドラッグ中は小数で持ち、保存するときに丸める。 */
data class KeyboardHeight(val percent: Float) {
    /** 上端を [dy] dp 動かす（下向きが正）。[percentHeight] は倍率 1% あたりの高さ。 */
    fun drag(dy: Float, percentHeight: Float) = KeyboardHeight(clamp(percent - dy / percentHeight))

    /**
     * [Step] 刻みで一段動かす。[direction] は +1 で高く、-1 で低く。
     * 半端な値からは、まず向きに合わせた最寄りの段に揃える。
     */
    fun step(direction: Int): KeyboardHeight {
        val index = floor(percent / Step + 1e-3f)
        val onStep = percent - index * Step < 1e-2f
        val next = if (direction > 0) index + 1 else if (onStep) index - 1 else index
        return KeyboardHeight(clamp(next * Step))
    }

    companion object {
        val Default = KeyboardHeight(100f)
        const val Step = 5f

        private fun clamp(value: Float) =
            value.coerceIn(Settings.MIN_HEIGHT_PERCENT.toFloat(), Settings.MAX_HEIGHT_PERCENT.toFloat())
    }
}

/**
 * 高さの調整中に、ツールバーの代わりに出すバー。キーは隠さず、打ちながら合わせられる。
 * バー自体を上下にドラッグするか、ボタンで一段ずつ高さを変える。
 */
@Composable
fun ResizeBar(
    height: KeyboardHeight,
    percentHeight: Dp,
    onHeightChange: (KeyboardHeight) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val current by rememberUpdatedState(height)
    val change by rememberUpdatedState(onHeightChange)
    val dragState = rememberDraggableState { px ->
        change(current.drag(with(density) { px.toDp().value }, percentHeight.value))
    }
    Row(
        modifier
            .background(colors.secondaryContainer, RoundedCornerShape(12.dp))
            // バー全体がつまみであることを、上辺中央の短い棒で示す
            .drawBehind {
                val w = 32.dp.toPx()
                val h = 3.dp.toPx()
                drawRoundRect(
                    colors.onSecondaryContainer.copy(alpha = 0.5f),
                    topLeft = Offset((size.width - w) / 2, 3.dp.toPx()),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2),
                )
            }
            .draggable(dragState, Orientation.Vertical)
            .semantics { contentDescription = "上下にドラッグして高さを変える" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("高さ ${height.percent.roundToInt()}%")
        Step(R.drawable.ic_remove, "低くする") { change(current.step(-1)) }
        Step(R.drawable.ic_add, "高くする") { change(current.step(1)) }
        Step(R.drawable.ic_refresh, "リセット", onClick = onReset)
        Step(R.drawable.ic_check, "完了", active = true, onClick = onDone)
    }
}

@Composable
private fun RowScope.Label(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        fontSize = 12.sp,
        maxLines = 1,
    )
}

/**
 * バーのボタン。残りの幅を均等に分け、高さはバーいっぱいに取る。
 * [active] は保存のように主となる操作を目立たせる。
 */
@Composable
private fun RowScope.Step(@DrawableRes icon: Int, description: String, active: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (active) colors.primary else colors.onSecondaryContainer,
        )
    }
}
