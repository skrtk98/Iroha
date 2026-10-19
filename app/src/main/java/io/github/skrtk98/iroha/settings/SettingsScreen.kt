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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.skrtk98.iroha.data.KeyboardLayout
import io.github.skrtk98.iroha.data.Settings
import io.github.skrtk98.iroha.ui.theme.IrohaTheme
import kotlin.math.roundToInt

/** 設定画面に表示する値。 */
data class SettingsUiState(
    val layout: KeyboardLayout,
    val keyHeightDp: Int,
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
    extraSections: @Composable () -> Unit = {},
) {
    fun read() = SettingsUiState(
        layout = settings.defaultLayout,
        keyHeightDp = settings.keyHeightDp,
        vibrate = settings.vibrate,
        fullWidthSpace = settings.fullWidthSpace,
        learningEnabled = settings.learningEnabled,
        clipboardHistory = settings.clipboardHistory,
    )
    var state by remember { mutableStateOf(read()) }
    fun update(block: Settings.() -> Unit) {
        settings.block()
        state = read()
    }
    SettingsScreen(
        state = state,
        onOpenInputMethodSettings = onOpenInputMethodSettings,
        onShowInputMethodPicker = onShowInputMethodPicker,
        onLayoutChange = { update { defaultLayout = it } },
        onKeyHeightChange = { update { keyHeightDp = it } },
        onVibrateChange = { update { vibrate = it } },
        onFullWidthSpaceChange = { update { fullWidthSpace = it } },
        onLearningChange = { update { learningEnabled = it } },
        onClipboardHistoryChange = { update { clipboardHistory = it } },
        onClearClipboard = onClearClipboard,
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
    onKeyHeightChange: (Int) -> Unit,
    onVibrateChange: (Boolean) -> Unit,
    onFullWidthSpaceChange: (Boolean) -> Unit,
    onLearningChange: (Boolean) -> Unit,
    onClipboardHistoryChange: (Boolean) -> Unit,
    onClearClipboard: () -> Unit,
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
            OutlinedTextField(
                value = trial,
                onValueChange = { trial = it },
                label = { Text("ここで試し入力") },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionTitle("キーボード")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("配列", Modifier.weight(1f))
                FilterChip(
                    selected = state.layout == KeyboardLayout.Flick,
                    onClick = { onLayoutChange(KeyboardLayout.Flick) },
                    label = { Text("フリック") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.layout == KeyboardLayout.Qwerty,
                    onClick = { onLayoutChange(KeyboardLayout.Qwerty) },
                    label = { Text("QWERTY") },
                )
            }
            Text("キーの高さ: ${state.keyHeightDp}dp")
            Slider(
                value = state.keyHeightDp.toFloat(),
                onValueChange = { onKeyHeightChange(it.roundToInt()) },
                valueRange = 40f..72f,
                steps = 15,
            )
            SwitchRow("キーを押したときに振動", state.vibrate, onVibrateChange)
            SwitchRow("かな入力の空白を全角にする", state.fullWidthSpace, onFullWidthSpaceChange)

            HorizontalDivider()
            SectionTitle("変換")
            SwitchRow("選んだ候補を学習する", state.learningEnabled, onLearningChange)
            extraSections()

            HorizontalDivider()
            SectionTitle("クリップボード")
            SwitchRow("コピーした文字列を履歴に残す", state.clipboardHistory, onClipboardHistoryChange)
            Text(
                "履歴は端末内にだけ保存します。パスワードなど機密扱いのコピーは記録しません。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onClearClipboard) { Text("履歴をすべて消去") }

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
            state = SettingsUiState(KeyboardLayout.Flick, 52, vibrate = true, fullWidthSpace = false, learningEnabled = true),
            onOpenInputMethodSettings = {},
            onShowInputMethodPicker = {},
            onLayoutChange = {},
            onKeyHeightChange = {},
            onVibrateChange = {},
            onFullWidthSpaceChange = {},
            onLearningChange = {},
            onClipboardHistoryChange = {},
            onClearClipboard = {},
            onOpenLicenses = {},
        )
    }
}
