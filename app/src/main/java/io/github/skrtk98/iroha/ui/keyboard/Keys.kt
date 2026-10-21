package io.github.skrtk98.iroha.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** キー押下時に振動させるかどうか。 */
val LocalKeyHaptics = staticCompositionLocalOf { true }

/** キーの背景（枠）を描くかどうか。false でも確定キーなどの強調キーと押下中の表示は残す。 */
val LocalKeyBorders = staticCompositionLocalOf { true }

enum class KeyStyle { Character, Function, Accent }

@Composable
private fun KeyStyle.container(pressed: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        pressed -> colors.primaryContainer
        this != KeyStyle.Accent && !LocalKeyBorders.current -> Color.Transparent
        this == KeyStyle.Character -> colors.surfaceContainerHighest
        this == KeyStyle.Function -> colors.surfaceContainerHigh
        else -> colors.primary
    }
}

@Composable
private fun KeyStyle.content(pressed: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    return when {
        pressed -> colors.onPrimaryContainer
        this == KeyStyle.Accent -> colors.onPrimary
        else -> colors.onSurface
    }
}

private val KeyShape = RoundedCornerShape(8.dp)

@Composable
private fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    val enabled = LocalKeyHaptics.current
    return remember(view, enabled) {
        { if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    }
}

/**
 * 押して離すと [onClick] が呼ばれるキー。[repeat] が true なら押した瞬間に 1 回呼び、
 * 押し続けると連続して呼ぶ。
 */
@Composable
fun KeyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Character,
    repeat: Boolean = false,
    description: String? = null,
    content: @Composable (contentColor: Color) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val haptic = rememberHaptic()
    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(KeyShape)
            .background(style.container(pressed))
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .pointerInput(repeat) {
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        pressed = true
                        haptic()
                        // 長押しの連続入力はジェスチャーの外側のスコープで回す
                        val repeatJob = if (repeat) {
                            currentOnClick()
                            launch {
                                delay(REPEAT_START_MS)
                                while (true) {
                                    currentOnClick()
                                    delay(REPEAT_INTERVAL_MS)
                                }
                            }
                        } else {
                            null
                        }
                        try {
                            val up = waitForUpOrCancellation()
                            if (!repeat && up != null) currentOnClick()
                        } finally {
                            repeatJob?.cancel()
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        content(style.content(pressed))
    }
}

@Composable
fun TextKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Character,
    fontSize: TextUnit = 20.sp,
) {
    KeyButton(onClick = onClick, modifier = modifier, style = style, description = label) { color ->
        Text(label, color = color, fontSize = fontSize, maxLines = 1)
    }
}

@Composable
fun IconKey(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Function,
    repeat: Boolean = false,
) {
    KeyButton(onClick = onClick, modifier = modifier, style = style, repeat = repeat, description = description) { color ->
        Icon(painterResource(icon), contentDescription = null, tint = color)
    }
}

/** フリック中のキーの情報。キーボード全体の上にガイドを描くために使う。 */
data class FlickGuide(
    val key: FlickKey,
    val origin: Offset,
    val width: Float,
    val height: Float,
    val direction: FlickDirection,
)

/**
 * フリックキー。指を離した方向の文字を [onInput] に渡す。押している間は [onGuide] にガイド情報を渡し、
 * 離すと null を渡す。[rootCoordinates] はガイドを描くキーボード全体の座標。
 */
@Composable
fun FlickKeyButton(
    key: FlickKey,
    onInput: (String) -> Unit,
    onGuide: (FlickGuide?) -> Unit,
    rootCoordinates: () -> LayoutCoordinates?,
    modifier: Modifier = Modifier,
    threshold: Dp = 24.dp,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnInput by rememberUpdatedState(onInput)
    val currentOnGuide by rememberUpdatedState(onGuide)
    val currentRoot by rememberUpdatedState(rootCoordinates)
    val haptic = rememberHaptic()
    // 座標は描画に使わないので State にしない
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        modifier = modifier
            .padding(3.dp)
            .onGloballyPositioned { coordinates[0] = it }
            .clip(KeyShape)
            .background(KeyStyle.Character.container(pressed))
            .semantics { contentDescription = key.label }
            .pointerInput(key) {
                val thresholdPx = threshold.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    haptic()
                    var direction = FlickDirection.Center
                    fun publishGuide() {
                        val own = coordinates[0]
                        val root = currentRoot()
                        if (own == null || root == null || !own.isAttached || !root.isAttached) return
                        val origin = root.localPositionOf(own, Offset.Zero)
                        currentOnGuide(
                            FlickGuide(key, origin, own.size.width.toFloat(), own.size.height.toFloat(), direction),
                        )
                    }
                    publishGuide()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val next = directionOf(change.position - down.position, thresholdPx)
                                .takeIf { key.output(it) != null } ?: FlickDirection.Center
                            if (next != direction) {
                                direction = next
                                publishGuide()
                            }
                            change.consume()
                            if (!change.pressed) {
                                key.output(direction)?.let(currentOnInput)
                                break
                            }
                        }
                    } finally {
                        pressed = false
                        currentOnGuide(null)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val color = KeyStyle.Character.content(pressed)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                key.label,
                color = color,
                fontSize = if (key.label.length > 2) 15.sp else 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            // フリックで出せる文字を小さく添える（数字テンキーの記号など）
            key.hint?.let { Text(it, color = color.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1) }
        }
    }
}

private fun directionOf(delta: Offset, threshold: Float): FlickDirection {
    if (abs(delta.x) < threshold && abs(delta.y) < threshold) return FlickDirection.Center
    return if (abs(delta.x) > abs(delta.y)) {
        if (delta.x < 0) FlickDirection.Left else FlickDirection.Right
    } else {
        if (delta.y < 0) FlickDirection.Up else FlickDirection.Down
    }
}

/** 押しているキーを中心に、各方向の文字を十字に並べたガイド。 */
@Composable
fun FlickGuideOverlay(guide: FlickGuide, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val w = with(density) { guide.width.toDp() }
    val h = with(density) { guide.height.toDp() }
    val x = with(density) { guide.origin.x.toDp() }
    val y = with(density) { guide.origin.y.toDp() }
    val colors = MaterialTheme.colorScheme
    Box(modifier) {
        for (direction in FlickDirection.entries) {
            val text = guide.key.output(direction) ?: continue
            val (dx, dy) = when (direction) {
                FlickDirection.Center -> 0 to 0
                FlickDirection.Left -> -1 to 0
                FlickDirection.Up -> 0 to -1
                FlickDirection.Right -> 1 to 0
                FlickDirection.Down -> 0 to 1
            }
            val selected = direction == guide.direction
            Box(
                Modifier
                    .absoluteOffset(x + w * dx, y + h * dy)
                    .size(w, h)
                    .padding(2.dp)
                    .clip(KeyShape)
                    .background(if (selected) colors.primary else colors.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (selected) colors.onPrimary else colors.onSecondaryContainer,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val REPEAT_START_MS = 400L
private const val REPEAT_INTERVAL_MS = 50L
