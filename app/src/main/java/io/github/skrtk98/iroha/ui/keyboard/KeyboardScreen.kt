package io.github.skrtk98.iroha.ui.keyboard

import android.view.View
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.Faces
import io.github.skrtk98.iroha.ime.KeyAction
import io.github.skrtk98.iroha.ime.KeyboardFace
import io.github.skrtk98.iroha.ime.KeyboardUiState
import io.github.skrtk98.iroha.ime.Panel
import io.github.skrtk98.iroha.ui.theme.IrohaTheme
import kotlin.math.roundToInt

private val BarHeight = 44.dp
private val MinBarHeight = 36.dp

/** 倍率 100% のときのキー 1 段の高さ。 */
private val KeyHeight = 52.dp

/** キーボードの高さを決める段数。数字の段を出しても増やさない。 */
private const val KeyRows = 4

/**
 * キーボード全体。状態とコールバックだけを受け取るので、プレビューやテストで単体表示できる。
 */
@Composable
fun KeyboardScreen(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 調整中は保存前の値で描き、完了したときにまとめて保存する
    var draft by remember(state.resizing) { mutableStateOf(KeyboardHeight(state.keyboardHeightPercent.toFloat())) }
    val height = if (state.resizing) draft else KeyboardHeight(state.keyboardHeightPercent.toFloat())
    // 全体の高さ設定は、ツールバーとキーの両方に同じ倍率で掛ける
    val scale = height.percent / 100f
    val barHeight = (BarHeight * scale).coerceAtLeast(MinBarHeight)
    // どの面・画面でも同じ高さにする。QWERTY の数字の段は、この高さの中で各段を少し低くして収める
    val keyboardHeight = KeyHeight * KeyRows * scale
    CompositionLocalProvider(LocalKeyHaptics provides state.vibrate, LocalKeyBorders provides state.keyBorders) {
        Column(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(bottom = navigationBarHeight())
                .padding(horizontal = 3.dp, vertical = 2.dp),
        ) {
            if (state.resizing) {
                // 倍率 1% あたりの高さ。ドラッグ量を倍率に直すのに使う。
                // ツールバーが最小の高さで止まっている間は、キーの段だけが伸び縮みする
                val barScales = BarHeight * scale > MinBarHeight
                val percentHeight = ((if (barScales) BarHeight else 0.dp) + KeyHeight * KeyRows) / 100f
                ResizeBar(
                    height = draft,
                    percentHeight = percentHeight,
                    onHeightChange = { draft = it },
                    onReset = { draft = KeyboardHeight.Default },
                    onDone = { onAction(KeyAction.FinishResize(draft.percent.roundToInt())) },
                    modifier = Modifier.fillMaxWidth().height(barHeight),
                )
            } else if (state.composing) {
                CandidateBar(state, onAction, Modifier.fillMaxWidth().height(barHeight))
            } else if (state.followUps.isNotEmpty() && state.panel == Panel.None) {
                FollowUpBar(state.followUps, onAction, Modifier.fillMaxWidth().height(barHeight))
            } else {
                Toolbar(state, onAction, Modifier.fillMaxWidth().height(barHeight))
            }
            if (state.candidateListExpanded && state.composing) {
                CandidateGrid(state, onAction, Modifier.fillMaxWidth().height(keyboardHeight))
            } else if (state.panel == Panel.Voice) {
                VoicePanel(state.voice, onAction, Modifier.fillMaxWidth().height(keyboardHeight))
            } else if (state.panel == Panel.Clipboard) {
                ClipboardPanel(state.clips, onAction, Modifier.fillMaxWidth().height(keyboardHeight))
            } else if (state.panel == Panel.Cursor) {
                CursorPanel(state.selecting, onAction, Modifier.fillMaxWidth().height(keyboardHeight))
            } else {
                KeyboardArea(state, onAction, Modifier.fillMaxWidth().height(keyboardHeight))
            }
        }
    }
}

/**
 * IME のウィンドウには Compose の WindowInsets が届かないことがあるため、
 * ビューのルートのインセットからナビゲーションバーの高さを読み、レイアウトのたびに更新する。
 */
@Composable
private fun navigationBarHeight(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    var bottomPx by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        fun read() {
            // IME の戻る・切替ボタンは tappableElement に含まれ、ナビゲーションバーより高い
            val insets = ViewCompat.getRootWindowInsets(view)
            bottomPx = maxOf(
                insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0,
                insets?.getInsets(WindowInsetsCompat.Type.tappableElement())?.bottom ?: 0,
            )
        }
        read()
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> read() }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    return with(density) { bottomPx.toDp() }
}

@Composable
private fun KeyboardArea(state: KeyboardUiState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    when (state.face) {
        KeyboardFace.FlickKana, KeyboardFace.FlickAlphabet, KeyboardFace.FlickNumber -> FlickKeyboard(
            face = state.face,
            textFace = state.textFace,
            shift = state.shift,
            composing = state.composing,
            enterKind = state.enterKind,
            spaceKind = state.spaceKind,
            onAction = onAction,
            modifier = modifier,
        )
        KeyboardFace.QwertyKana, KeyboardFace.QwertyAlphabet, KeyboardFace.QwertySymbols -> QwertyKeyboard(
            face = state.face,
            textFace = state.textFace,
            shift = state.shift,
            composing = state.composing,
            enterKind = state.enterKind,
            spaceKind = state.spaceKind,
            numberRow = state.qwertyNumberRow,
            moreSymbols = state.moreSymbols,
            onAction = onAction,
            modifier = modifier,
        )
    }
}

/**
 * 上部のボタン列。どの面でも使う機能を均等に並べる。キーボードを閉じる・入力方法の切り替えは
 * ナビゲーションバーにもシステムのボタンがあるので、ここでは長押しに割り当てて場所を取らない。
 */
@Composable
private fun Toolbar(state: KeyboardUiState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    Box(modifier) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val cell = Modifier.weight(1f)
            // パネル（クリップボード・カーソル移動・音声）では切り替えても何も変わらないので押せなくする
            KanaAlphabetButton(state.textFace, onAction, cell, enabled = state.panel == Panel.None)
            ToolbarButton(R.drawable.ic_assignment, "クリップボード", cell, active = state.panel == Panel.Clipboard) {
                onAction(if (state.panel == Panel.Clipboard) KeyAction.ClosePanel else KeyAction.OpenPanel(Panel.Clipboard))
            }
            ToolbarButton(R.drawable.ic_open_with, "カーソル移動", cell, active = state.panel == Panel.Cursor) {
                onAction(if (state.panel == Panel.Cursor) KeyAction.ClosePanel else KeyAction.OpenPanel(Panel.Cursor))
            }
            ToolbarButton(
                R.drawable.ic_settings,
                "設定",
                cell,
                onLongClick = { onAction(KeyAction.HideKeyboard) },
                longClickLabel = "キーボードを閉じる",
            ) { onAction(KeyAction.OpenSettings) }
            ToolbarButton(R.drawable.ic_mic, "音声入力", cell, active = state.panel == Panel.Voice) {
                onAction(if (state.panel == Panel.Voice) KeyAction.ClosePanel else KeyAction.StartVoice)
            }
        }
        if (state.loading) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).semantics { contentDescription = "辞書を読み込み中" },
            )
        }
    }
}

/**
 * 日本語 ⇔ 英字。数字・記号の面からも使え、切り替えた日英の面に戻る。今の日英をラベルで示す。
 * 長押しで次の入力方法に切り替える。
 */
@Composable
private fun KanaAlphabetButton(
    textFace: KeyboardFace,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alphabet = Faces.isAlphabet(textFace)
    ToolbarButton(
        modifier.semantics { contentDescription = if (alphabet) "日本語に切り替え" else "英字に切り替え" },
        active = false,
        enabled = enabled,
        onLongClick = { onAction(KeyAction.SwitchInputMethod) },
        longClickLabel = "入力方法を切り替え",
        onClick = { onAction(KeyAction.ToggleKanaAlphabet) },
    ) { color ->
        // 線の太いアイコンと並べても細く見えないよう、やや太字にする
        Text(if (alphabet) "A" else "あ", color = color, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

/** 上部のアイコンボタン。[onLongClick] があれば長押しにも反応し、振動で知らせる。 */
@Composable
fun ToolbarButton(
    @DrawableRes icon: Int,
    description: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    onClick: () -> Unit,
) {
    ToolbarButton(modifier, active, enabled = true, onLongClick, longClickLabel, onClick) { color ->
        Icon(painterResource(icon), contentDescription = description, tint = color)
    }
}

/** 上部のボタンの枠。[content] には状態に応じた前景色を渡す。[enabled] が false ならタップも長押しも受け付けない。 */
@Composable
private fun ToolbarButton(
    modifier: Modifier,
    active: Boolean,
    enabled: Boolean,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxHeight()
            .widthIn(min = BarHeight)
            // バーが 48dp より低くても、IconButton と同じく押せる範囲は 48dp を保つ
            .minimumInteractiveComponentSize()
            .combinedClickable(
                interactionSource = null,
                // 均等割りで横に広がっても、波紋はアイコンの周りだけに出す
                indication = ripple(bounded = false, radius = BarHeight / 2),
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick?.let { action ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        action()
                    }
                },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(
            when {
                !enabled -> colors.onSurfaceVariant.copy(alpha = 0.38f)
                active -> colors.primary
                else -> colors.onSurfaceVariant
            },
        )
    }
}

/** 確定の後に、続けて打ちそうな語を出す。閉じるとツールバーに戻る。 */
@Composable
private fun FollowUpBar(followUps: List<String>, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(followUps) { index, text ->
                CandidateItem(
                    text = text,
                    selected = false,
                    onClick = { onAction(KeyAction.SelectFollowUp(index)) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        ToolbarButton(R.drawable.ic_close, "候補を閉じる") { onAction(KeyAction.DismissFollowUps) }
    }
}

@Composable
private fun CandidateBar(state: KeyboardUiState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.selectedCandidate) {
        if (state.selectedCandidate >= 0) listState.animateScrollToItem(state.selectedCandidate)
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (state.converting) {
            ToolbarButton(R.drawable.ic_first_page, "文節を縮める") { onAction(KeyAction.ResizeSegment(-1)) }
        }
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(state.candidates) { index, candidate ->
                CandidateItem(
                    text = candidate,
                    selected = index == state.selectedCandidate,
                    onClick = { onAction(KeyAction.SelectCandidate(index)) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
        if (state.converting) {
            ToolbarButton(R.drawable.ic_last_page, "文節を伸ばす") { onAction(KeyAction.ResizeSegment(1)) }
        } else {
            // 入力中はツールバーが隠れるので、日英の切り替えはここに残す
            KanaAlphabetButton(state.textFace, onAction)
        }
        ToolbarButton(
            if (state.candidateListExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down,
            "候補一覧",
        ) { onAction(KeyAction.ToggleCandidateList) }
    }
}

@Composable
private fun CandidateItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .widthIn(min = 44.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 18.sp,
            color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CandidateGrid(state: KeyboardUiState, onAction: (KeyAction) -> Unit, modifier: Modifier) {
    FlowRow(
        modifier.verticalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Start,
    ) {
        state.candidates.forEachIndexed { index, candidate ->
            CandidateItem(
                text = candidate,
                selected = index == state.selectedCandidate,
                onClick = { onAction(KeyAction.SelectCandidate(index)) },
                modifier = Modifier.height(48.dp),
            )
        }
    }
}

@Preview(widthDp = 400)
@Composable
private fun KeyboardScreenPreview() {
    IrohaTheme {
        KeyboardScreen(
            state = KeyboardUiState(composing = true, candidates = listOf("私", "渡し", "わたし", "ワタシ"), selectedCandidate = 0),
            onAction = {},
        )
    }
}
