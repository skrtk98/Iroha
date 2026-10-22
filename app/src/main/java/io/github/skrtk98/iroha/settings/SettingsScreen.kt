package io.github.skrtk98.iroha.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skrtk98.iroha.data.KeyboardLayout
import io.github.skrtk98.iroha.data.Settings
import io.github.skrtk98.iroha.ime.ResizeRequest
import io.github.skrtk98.iroha.ui.theme.IrohaTheme

/** 設定画面に表示する値。 */
data class SettingsUiState(
    val layout: KeyboardLayout,
    val symbolLayout: KeyboardLayout = layout,
    val keyboardHeightPercent: Int = 100,
    val keyBorders: Boolean = true,
    val qwertyNumberRow: Boolean = false,
    val vibrate: Boolean,
    val fullWidthSpace: Boolean,
    val learningEnabled: Boolean,
    val clipboardHistory: Boolean = true,
)

/** Settings と画面をつなぐ。値の保持は Settings が持ち、ここでは画面用の状態に写す。 */
@Composable
fun SettingsRoute(
    settings: Settings,
    onOpenInputMethodSettings: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onOpenLicenses: () -> Unit,
    onClearClipboard: () -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    extraSections: @Composable () -> Unit = {},
) {
    fun read() = SettingsUiState(
        layout = settings.defaultLayout,
        symbolLayout = settings.symbolLayout,
        keyboardHeightPercent = settings.keyboardHeightPercent,
        keyBorders = settings.keyBorders,
        qwertyNumberRow = settings.qwertyNumberRow,
        vibrate = settings.vibrate,
        fullWidthSpace = settings.fullWidthSpace,
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
        onOpenInputMethodSettings = onOpenInputMethodSettings,
        onShowInputMethodPicker = onShowInputMethodPicker,
        onLayoutChange = { update { defaultLayout = it } },
        onSymbolLayoutChange = { update { symbolLayout = it } },
        onStartResize = { ResizeRequest.pending.value = true },
        onKeyBordersChange = { update { keyBorders = it } },
        onQwertyNumberRowChange = { update { qwertyNumberRow = it } },
        onVibrateChange = { update { vibrate = it } },
        onFullWidthSpaceChange = { update { fullWidthSpace = it } },
        onLearningChange = { update { learningEnabled = it } },
        onClipboardHistoryChange = { update { clipboardHistory = it } },
        onClearClipboard = onClearClipboard,
        micGranted = micGranted,
        onRequestMic = onRequestMic,
        onOpenLicenses = onOpenLicenses,
        extraSections = extraSections,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onOpenInputMethodSettings: () -> Unit,
    onShowInputMethodPicker: () -> Unit,
    onLayoutChange: (KeyboardLayout) -> Unit,
    onSymbolLayoutChange: (KeyboardLayout) -> Unit,
    onStartResize: () -> Unit,
    onKeyBordersChange: (Boolean) -> Unit,
    onQwertyNumberRowChange: (Boolean) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onFullWidthSpaceChange: (Boolean) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onClipboardHistoryChange: (Boolean) -> Unit,
    onClearClipboard: () -> Unit,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    extraSections: @Composable () -> Unit = {},
) {
    Scaffold(modifier = modifier, topBar = { TopAppBar(title = { Text("Iroha の設定") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("はじめに")
            Text("1. 入力方法の設定で「Iroha 日本語入力」を有効にします。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpenInputMethodSettings) { Text("入力方法の設定を開く") }
            Text("2. 使用する入力方法として Iroha を選びます。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onShowInputMethodPicker) { Text("入力方法を選ぶ") }
            var trial by rememberSaveable { mutableStateOf("") }
            val trialFocus = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current
            OutlinedTextField(
                value = trial,
                onValueChange = { trial = it },
                label = { Text("ここで試し入力") },
                modifier = Modifier.fillMaxWidth().focusRequester(trialFocus),
            )

            HorizontalDivider()
            SectionTitle("キーボード")
            LayoutRow("日本語・英字の配列", state.layout, onLayoutChange)
            LayoutRow("数字・記号の配列", state.symbolLayout, onSymbolLayoutChange)
            Text(
                "キーボードの「☆123」「?123」キーで、日本語・英字と数字・記号を切り替えます。",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("キーボードの高さ: ${state.keyboardHeightPercent}%")
            OutlinedButton(
                onClick = {
                    // 試し入力欄でキーボードを開き、その上で枠をドラッグして調整する
                    trialFocus.requestFocus()
                    keyboard?.show()
                    onStartResize()
                },
            ) { Text("高さを調整") }
            Text(
                "キーボード上部が調整用のバーに変わります。バーを上下にドラッグするか ＋− で高さを変えます。調整中も文字を打って試せます。✓ で保存します。",
                style = MaterialTheme.typography.bodySmall,
            )
            SwitchRow("キーの枠を表示する", state.keyBorders, onKeyBordersChange)
            SwitchRow("QWERTY の日本語・英字で数字の段を表示する", state.qwertyNumberRow, onQwertyNumberRowChange)
            SwitchRow("キーを押したときに振動", state.vibrate, onVibrateChange)
            SwitchRow("かな入力の空白を全角にする", state.fullWidthSpace, onFullWidthSpaceChange)

            HorizontalDivider()
            SectionTitle("変換")
            SwitchRow("選んだ候補を学習する", state.learningEnabled, onLearningChange)
            extraSections()

            HorizontalDivider()
            SectionTitle("クリップボード")
            SwitchRow("コピーした文字列や画像を履歴に残す", state.clipboardHistory, onClipboardHistoryChange)
            Text(
                "履歴は端末内にだけ保存します。パスワードなど機密扱いのコピーは記録しません。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onClearClipboard) { Text("履歴をすべて消去") }

            HorizontalDivider()
            SectionTitle("音声入力")
            Text(
                "端末内の音声認識だけを使い、音声を外部に送りません。Android 12 以降で端末内認識に対応した端末が必要です。日本語の認識データは端末の設定からダウンロードしてください。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (micGranted) {
                Text("マイクの権限: 許可済み", style = MaterialTheme.typography.bodyMedium)
            } else {
                OutlinedButton(onClick = onRequestMic) { Text("マイクの権限を許可") }
            }

            HorizontalDivider()
            SectionTitle("このアプリについて")
            Text(
                "Iroha は完全にオフラインで動作します。インターネット接続の権限を持たず、入力内容を外部に送信しません。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "変換辞書は Mozc の OSS 辞書から作成しています。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "ライセンス",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenLicenses).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun LayoutRow(label: String, selected: KeyboardLayout, onChange: (KeyboardLayout) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        FilterChip(
            selected = selected == KeyboardLayout.Flick,
            onClick = { onChange(KeyboardLayout.Flick) },
            label = { Text("フリック") },
        )
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = selected == KeyboardLayout.Qwerty,
            onClick = { onChange(KeyboardLayout.Qwerty) },
            label = { Text("QWERTY") },
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    IrohaTheme {
        SettingsScreen(
            state = SettingsUiState(KeyboardLayout.Flick, vibrate = true, fullWidthSpace = false, learningEnabled = true),
            onOpenInputMethodSettings = {},
            onShowInputMethodPicker = {},
            onLayoutChange = {},
            onSymbolLayoutChange = {},
            onStartResize = {},
            onKeyBordersChange = {},
            onQwertyNumberRowChange = {},
            onVibrateChange = {},
            onFullWidthSpaceChange = {},
            onLearningChange = {},
            onClipboardHistoryChange = {},
            onClearClipboard = {},
            micGranted = false,
            onRequestMic = {},
            onOpenLicenses = {},
        )
    }
}

/** 辞書まわりの設定。ユーザー辞書の管理画面への入口と、学習結果の消去。 */
@Composable
fun DictionarySection(onOpenUserDictionary: () -> Unit, onClearLearning: () -> Unit) {
    var confirmed by remember { mutableStateOf(false) }
    Text(
        "ユーザー辞書（読み込み・書き出し）",
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenUserDictionary).padding(vertical = 8.dp),
    )
    OutlinedButton(
        onClick = {
            onClearLearning()
            confirmed = true
        },
        enabled = !confirmed,
    ) { Text(if (confirmed) "学習結果を消去しました" else "学習結果を消去") }
}
