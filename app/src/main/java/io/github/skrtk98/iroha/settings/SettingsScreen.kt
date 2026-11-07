package io.github.skrtk98.iroha.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.KeyboardLayout
import io.github.skrtk98.iroha.data.Settings
import io.github.skrtk98.iroha.ime.ResizeRequest
import io.github.skrtk98.iroha.ui.theme.IrohaTheme
import kotlinx.coroutines.launch

/** 設定画面に表示する値。 */
data class SettingsUiState(
    val layout: KeyboardLayout,
    val symbolLayout: KeyboardLayout = layout,
    val keyboardHeightPercent: Int = 100,
    val keyBorders: Boolean = true,
    val qwertyNumberRow: Boolean = false,
    val vibrate: Boolean,
    val fullWidthSpace: Boolean,
    val fullWidthSymbols: Boolean = true,
    val learningEnabled: Boolean,
    val clipboardHistory: Boolean = true,
)

/** Iroha を使い始めるための端末側の設定の状態。 */
data class SetupState(val enabled: Boolean, val selected: Boolean) {
    val done get() = enabled && selected
}

/** Settings と画面をつなぐ。値の保持は Settings が持ち、ここでは画面用の状態に写す。 */
@Composable
fun SettingsRoute(
    settings: Settings,
    setup: SetupState,
    appVersion: String,
    onOpenInputMethodSettings: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onOpenUserDictionary: () -> Unit,
    onClearLearning: () -> Unit,
    onOpenLicenses: () -> Unit,
    onClearClipboard: () -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
) {
    fun read() = SettingsUiState(
        layout = settings.defaultLayout,
        symbolLayout = settings.symbolLayout,
        keyboardHeightPercent = settings.keyboardHeightPercent,
        keyBorders = settings.keyBorders,
        qwertyNumberRow = settings.qwertyNumberRow,
        vibrate = settings.vibrate,
        fullWidthSpace = settings.fullWidthSpace,
        fullWidthSymbols = settings.fullWidthSymbols,
        learningEnabled = settings.learningEnabled,
        clipboardHistory = settings.clipboardHistory,
    )
    var state by remember { mutableStateOf(read()) }
    // キーボード側で高さを調整したときなど、この画面の外で変わった値も表示に映す
    LaunchedEffect(settings) { settings.changes.collect { state = read() } }
    fun update(block: Settings.() -> Unit) {
        settings.block()
        state = read()
    }
    SettingsScreen(
        state = state,
        setup = setup,
        appVersion = appVersion,
        onOpenInputMethodSettings = onOpenInputMethodSettings,
        onShowInputMethodPicker = onShowInputMethodPicker,
        onLayoutChange = { update { defaultLayout = it } },
        onSymbolLayoutChange = { update { symbolLayout = it } },
        onStartResize = { ResizeRequest.pending.value = true },
        onKeyBordersChange = { update { keyBorders = it } },
        onQwertyNumberRowChange = { update { qwertyNumberRow = it } },
        onVibrateChange = { update { vibrate = it } },
        onFullWidthSpaceChange = { update { fullWidthSpace = it } },
        onFullWidthSymbolsChange = { update { fullWidthSymbols = it } },
        onLearningChange = { update { learningEnabled = it } },
        onOpenUserDictionary = onOpenUserDictionary,
        onClearLearning = onClearLearning,
        onClipboardHistoryChange = { update { clipboardHistory = it } },
        onClearClipboard = onClearClipboard,
        micGranted = micGranted,
        onRequestMic = onRequestMic,
        onOpenLicenses = onOpenLicenses,
    )
}

/** 確認してから消去する対象。 */
private enum class ClearTarget(val title: String, val text: String, val done: String) {
    Learning("学習結果を消去", "選んだ候補の順位がはじめの状態に戻ります。ユーザー辞書の単語は消えません。", "学習結果を消去しました"),
    Clipboard("履歴をすべて消去", "固定した項目も含めて、すべての履歴を消去します。", "クリップボードの履歴を消去しました"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    setup: SetupState,
    appVersion: String,
    onOpenInputMethodSettings: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onLayoutChange: (KeyboardLayout) -> Unit,
    onSymbolLayoutChange: (KeyboardLayout) -> Unit,
    onStartResize: () -> Unit,
    onKeyBordersChange: (Boolean) -> Unit,
    onQwertyNumberRowChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onFullWidthSpaceChange: (Boolean) -> Unit,
    onFullWidthSymbolsChange: (Boolean) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onOpenUserDictionary: () -> Unit,
    onClearLearning: () -> Unit,
    onClipboardHistoryChange: (Boolean) -> Unit,
    onClearClipboard: () -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf<ClearTarget?>(null) }
    // スクロールしたら上部のバーに色を付け、内容との境目を示す
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { Text("Iroha の設定") }, scrollBehavior = scrollBehavior) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            val trialFocus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            SetupCard(setup, onOpenInputMethodSettings, onShowInputMethodPicker)
            TrialField(trialFocus)

            SectionTitle("配列")
            LayoutItem("日本語・英字", state.layout, onLayoutChange)
            LayoutItem("数字・記号", state.symbolLayout, onSymbolLayoutChange)
            Hint("キーボードの「☆123」「?123」キーで、日本語・英字と数字・記号を切り替えます。")

            SectionTitle("見た目と操作")
            SettingItem(
                headline = "キーボードの高さ",
                supporting = "${state.keyboardHeightPercent}%",
                trailing = {
                    TextButton(
                        onClick = {
                            // 試し入力欄でキーボードを開き、その上で枠をドラッグして調整する
                            trialFocus.requestFocus()
                            keyboard?.show()
                            onStartResize()
                        },
                    ) { Text("調整") }
                },
            )
            SwitchItem("キーの枠を表示する", state.keyBorders, onKeyBordersChange)
            SwitchItem(
                "数字の段を表示する",
                state.qwertyNumberRow,
                onQwertyNumberRowChange,
                supporting = if (state.layout == KeyboardLayout.Qwerty) {
                    "日本語・英字の面で、英字の上に 1〜0 を並べます"
                } else {
                    "日本語・英字を QWERTY にしたときに使えます"
                },
                enabled = state.layout == KeyboardLayout.Qwerty,
            )
            SwitchItem("キーを押したときに振動する", state.vibrate, onVibrateChange)

            SectionTitle("かな入力")
            SwitchItem(
                "空白を全角にする",
                state.fullWidthSpace,
                onFullWidthSpaceChange,
                supporting = "日本語の面で空白キーを押すと、全角の空白（　）を入れます",
            )
            SwitchItem(
                "記号を全角にする",
                state.fullWidthSymbols,
                onFullWidthSymbolsChange,
                supporting = "日本語から数字・記号に切り替えて打つ記号を「：」「（」のように全角にします。数字の直後の . : / などは半角のままです",
            )

            SectionTitle("変換")
            SwitchItem(
                "選んだ候補を学習する",
                state.learningEnabled,
                onLearningChange,
                supporting = "よく選ぶ候補を先に出します。学習結果は端末内にだけ保存します",
            )
            SettingItem(
                headline = "ユーザー辞書",
                supporting = "単語の登録と、ファイルからの読み込み・書き出し",
                onClick = onOpenUserDictionary,
                trailing = { Chevron() },
            )
            SettingItem(
                headline = "学習結果を消去",
                onClick = { confirming = ClearTarget.Learning },
                destructive = true,
            )

            SectionTitle("クリップボード")
            SwitchItem(
                "コピーした文字列や画像を履歴に残す",
                state.clipboardHistory,
                onClipboardHistoryChange,
                supporting = "履歴は端末内にだけ保存します。パスワードなど機密扱いのコピーは記録しません",
            )
            SettingItem(
                headline = "履歴をすべて消去",
                onClick = { confirming = ClearTarget.Clipboard },
                destructive = true,
            )

            SectionTitle("音声入力")
            SettingItem(
                headline = "マイクの権限",
                supporting = if (micGranted) "許可済み" else "音声入力を使うには許可が必要です",
                trailing = {
                    if (micGranted) {
                        Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        OutlinedButton(onClick = onRequestMic) { Text("許可する") }
                    }
                },
            )
            Hint(
                "端末内の音声認識だけを使い、音声を外部に送りません。Android 12 以降で端末内認識に対応した端末が必要です。" +
                    "日本語の認識データは端末の設定からダウンロードしてください。",
            )

            SectionTitle("このアプリについて")
            Hint(
                "Iroha は完全にオフラインで動作します。インターネット接続の権限を持たず、入力内容を外部に送信しません。" +
                    "変換辞書は Mozc の OSS 辞書から作成しています。",
            )
            SettingItem(headline = "バージョン", supporting = appVersion)
            SettingItem(headline = "ライセンス", onClick = onOpenLicenses, trailing = { Chevron() })
        }
    }
    confirming?.let { target ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(target.title) },
            text = { Text(target.text) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        when (target) {
                            ClearTarget.Learning -> onClearLearning()
                            ClearTarget.Clipboard -> onClearClipboard()
                        }
                        scope.launch { snackbar.showSnackbar(target.done) }
                    },
                ) { Text("消去", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("キャンセル") } },
        )
    }
}

/** 使い始めの 2 手順。両方済んでいれば、済んだことだけを短く示す。 */
@Composable
private fun SetupCard(setup: SetupState, onOpenInputMethodSettings: () -> Unit, onShowInputMethodPicker: () -> Unit) {
    if (setup.done) {
        ListItem(
            headlineContent = { Text("Iroha を使う準備ができています") },
            leadingContent = {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
        )
        return
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("はじめに", style = MaterialTheme.typography.titleMedium)
            SetupStep(1, "入力方法の設定で「Iroha 日本語入力」を有効にします", setup.enabled) {
                Button(onClick = onOpenInputMethodSettings) { Text("入力方法の設定を開く") }
            }
            SetupStep(2, "使う入力方法として Iroha を選びます", setup.selected) {
                // 有効にする前は選べないので、手順 1 が済むまでは押せないようにする
                Button(onClick = onShowInputMethodPicker, enabled = setup.enabled) { Text("入力方法を選ぶ") }
            }
        }
    }
}

@Composable
private fun SetupStep(number: Int, text: String, done: Boolean, action: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (done) {
                Icon(
                    painterResource(R.drawable.ic_check_circle),
                    contentDescription = "済み",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("$number.", style = MaterialTheme.typography.bodyMedium)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        if (!done) action()
    }
}

@Composable
private fun TrialField(focus: FocusRequester) {
    var trial by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = trial,
        onValueChange = { trial = it },
        label = { Text("ここで試し入力") },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
    )
}

@Composable
fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 16.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun Chevron() {
    Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null)
}

/** 1 行の設定項目。[onClick] があれば行全体を押せる。[destructive] は消去などの操作で、文字を警告色にする。[enabled] は見た目だけを変える。 */
@Composable
private fun SettingItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    ListItem(
        headlineContent = { Text(headline) },
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = when {
                !enabled -> colors.onSurface.copy(alpha = 0.38f)
                destructive -> colors.error
                else -> colors.onSurface
            },
            supportingColor = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    )
}

/** スイッチの設定項目。行全体を押して切り替えられる。 */
@Composable
fun SwitchItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    SettingItem(
        headline = headline,
        supporting = supporting,
        modifier = Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** フリックと QWERTY を選ぶ項目。 */
@Composable
private fun LayoutItem(label: String, selected: KeyboardLayout, onChange: (KeyboardLayout) -> Unit) {
    SettingItem(
        headline = label,
        trailing = {
            SingleChoiceSegmentedButtonRow {
                val options = listOf(KeyboardLayout.Flick to "フリック", KeyboardLayout.Qwerty to "QWERTY")
                options.forEachIndexed { index, (layout, name) ->
                    SegmentedButton(
                        selected = selected == layout,
                        onClick = { onChange(layout) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        icon = {},
                    ) { Text(name) }
                }
            }
        },
    )
}

@Preview(heightDp = 2000)
@Composable
private fun SettingsScreenPreview() {
    IrohaTheme {
        SettingsScreen(
            state = SettingsUiState(KeyboardLayout.Flick, vibrate = true, fullWidthSpace = false, learningEnabled = true),
            setup = SetupState(enabled = true, selected = false),
            appVersion = "0.4.1",
            onOpenInputMethodSettings = {},
            onShowInputMethodPicker = {},
            onLayoutChange = {},
            onSymbolLayoutChange = {},
            onStartResize = {},
            onKeyBordersChange = {},
            onQwertyNumberRowChange = {},
            onVibrateChange = {},
            onFullWidthSpaceChange = {},
            onFullWidthSymbolsChange = {},
            onLearningChange = {},
            onOpenUserDictionary = {},
            onClearLearning = {},
            onClipboardHistoryChange = {},
            onClearClipboard = {},
            micGranted = false,
            onRequestMic = {},
            onOpenLicenses = {},
        )
    }
}
