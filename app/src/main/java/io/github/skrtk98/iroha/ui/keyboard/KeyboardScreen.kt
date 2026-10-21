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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.KeyAction
import io.github.skrtk98.iroha.ime.KeyboardFace
import io.github.skrtk98.iroha.ime.KeyboardUiState
import io.github.skrtk98.iroha.ime.Panel
import io.github.skrtk98.iroha.ui.theme.IrohaTheme

private val BarHeight = 44.dp
private val MinBarHeight = 36.dp

/**
 * キーボード全体。状態とコールバックだけを受け取るので、プレビューやテストで単体表示できる。
 */
@Composable
fun KeyboardScreen(
    state: KeyboardUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 全体の高さ設定は、ツールバーとキーの両方に同じ倍率で掛ける
    val scale = state.keyboardHeightPercent / 100f
    val barHeight = (BarHeight * scale).coerceAtLeast(MinBarHeight)
    CompositionLocalProvider(LocalKeyHaptics provides state.vibrate, LocalKeyBorders provides state.keyBorders) {
        Column(
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(bottom = navigationBarHeight())
                .padding(horizontal = 3.dp, vertical = 2.dp),
        ) {
            if (state.composing) {
                CandidateBar(state, onAction, Modifier.fillMaxWidth().height(barHeight))
            } else {
                Toolbar(state, onAction, Modifier.fillMaxWidth().height(barHeight))
            }
            val numberRow = state.qwertyNumberRow &&
                (state.face == KeyboardFace.QwertyKana || state.face == KeyboardFace.QwertyAlphabet)
            val rows = if (numberRow) 4 + QwertyNumberRowWeight else 4f
            val keyboardHeight = state.keyHeightDp.dp * rows * scale
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
            shifted = state.shifted,
            composing = state.composing,
            enterKind = state.enterKind,
            spaceKind = state.spaceKind,
            onAction = onAction,
            modifier = modifier,
        )
        KeyboardFace.QwertyKana, KeyboardFace.QwertyAlphabet, KeyboardFace.QwertySymbols -> QwertyKeyboard(
            face = state.face,
            textFace = state.textFace,
            shifted = state.shifted,
            composing = state.composing,
            enterKind = state.enterKind,
            spaceKind = state.spaceKind,
            numberRow = state.qwertyNumberRow,
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
            ToolbarButton(
                R.drawable.ic_settings,
                "設定",
                cell,
                onLongClick = { onAction(KeyAction.HideKeyboard) },
                longClickLabel = "キーボードを閉じる",
            ) { onAction(KeyAction.OpenSettings) }
            LayoutSwitchButton(onAction, cell)
            ToolbarButton(R.drawable.ic_content_paste, "クリップボード", cell, active = state.panel == Panel.Clipboard) {
                onAction(if (state.panel == Panel.Clipboard) KeyAction.ClosePanel else KeyAction.OpenPanel(Panel.Clipboard))
            }
            ToolbarButton(R.drawable.ic_open_with, "カーソル移動", cell, active = state.panel == Panel.Cursor) {
                onAction(if (state.panel == Panel.Cursor) KeyAction.ClosePanel else KeyAction.OpenPanel(Panel.Cursor))
            }
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

/** 配列の切り替え。長押しで次の入力方法に切り替える。 */
@Composable
private fun LayoutSwitchButton(onAction: (KeyAction) -> Unit, modifier: Modifier = Modifier) {
    ToolbarButton(
        R.drawable.ic_keyboard,
        "配列を切り替え",
        modifier,
        onLongClick = { onAction(KeyAction.SwitchInputMethod) },
        longClickLabel = "入力方法を切り替え",
    ) { onAction(KeyAction.ToggleLayout) }
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
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (active) colors.primary else colors.onSurfaceVariant,
        )
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
            // 入力中はツールバーが隠れるので、配列の切り替えだけはここに残す
            LayoutSwitchButton(onAction)
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
