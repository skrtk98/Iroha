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

/**
 * キーボードの高さ（百分率）と下の余白（dp）。ドラッグ中は小数で持ち、保存するときに丸める。
 */
data class KeyboardSize(val heightPercent: Float, val bottomDp: Float) {
    /** 上端を [dy] dp 動かす（下向きが正）。[percentHeight] は倍率 1% あたりの高さ。 */
    fun drag(dy: Float, percentHeight: Float) = copy(heightPercent = clampHeight(heightPercent - dy / percentHeight))

    /** 高さを [HeightStep] 刻みの次の段に合わせる。[direction] は +1 で高く、-1 で低く。 */
    fun stepHeight(direction: Int) = copy(heightPercent = clampHeight(nextStep(heightPercent, HeightStep, direction)))

    /** 下の余白を [BottomStep] 刻みの次の段に合わせる。[direction] は +1 で持ち上げ、-1 で下げる。 */
    fun stepBottom(direction: Int) = copy(bottomDp = clampBottom(nextStep(bottomDp, BottomStep, direction)))

    companion object {
        val Default = KeyboardSize(100f, 0f)
        const val HeightStep = 5f
        const val BottomStep = 16f

        /** 半端な値からは、まず向きに合わせた最寄りの段に揃える。 */
        private fun nextStep(value: Float, step: Float, direction: Int): Float {
            val index = floor(value / step + 1e-3f)
            val onStep = value - index * step < 1e-2f
            return if (direction > 0) (index + 1) * step else (if (onStep) index - 1 else index) * step
        }

        private fun clampHeight(value: Float) =
            value.coerceIn(Settings.MIN_HEIGHT_PERCENT.toFloat(), Settings.MAX_HEIGHT_PERCENT.toFloat())

        private fun clampBottom(value: Float) = value.coerceIn(0f, Settings.MAX_BOTTOM_DP.toFloat())
    }
}

/**
 * 高さと位置の調整中に、ツールバーの代わりに出すバー。キーは隠さず、打ちながら合わせられる。
 * バー自体を上下にドラッグすると高さが変わり、ボタンで高さと位置を一段ずつ動かす。
 */
@Composable
fun ResizeBar(
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
                    topLeft = Offset((this.size.width - w) / 2, 3.dp.toPx()),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2),
                )
            }
            .draggable(dragState, Orientation.Vertical)
            .semantics { contentDescription = "上下にドラッグして高さを変える" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label("高さ${size.heightPercent.roundToInt()}%")
        Step(R.drawable.ic_remove, "低くする") { change(current.stepHeight(-1)) }
        Step(R.drawable.ic_add, "高くする") { change(current.stepHeight(1)) }
        Label("位置${size.bottomDp.roundToInt()}")
        Step(R.drawable.ic_keyboard_arrow_down, "下げる") { change(current.stepBottom(-1)) }
        Step(R.drawable.ic_keyboard_arrow_up, "持ち上げる") { change(current.stepBottom(1)) }
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
 * バーのボタン。幅は均等に分けるので 48dp に満たないことがあるが、高さはバーいっぱいに取る。
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
