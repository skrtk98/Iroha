package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.ime.KeyAction
import io.github.skrtk98.iroha.ime.VoiceStatus
import io.github.skrtk98.iroha.ime.VoiceUiState

/** 音声入力。認識中は途中結果を表示し、確定した文はそのままエディタに入る。 */
@Composable
fun VoicePanel(
    voice: VoiceUiState,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // キーボード全体の高さを低くしても収まるよう、マイクと文を横に並べる
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            val listening = voice.status == VoiceStatus.Listening
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (listening) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    tint = if (listening) colors.onPrimary else colors.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
            val text = when (voice.status) {
                VoiceStatus.Listening -> voice.partial.ifEmpty { "話してください" }
                VoiceStatus.NeedsPermission -> "音声入力にはマイクの権限が必要です"
                VoiceStatus.Error -> voice.message
                VoiceStatus.Idle -> voice.partial.ifEmpty { "マイクを押すと話せます" }
            }
            Text(
                text,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth().height(52.dp)) {
            TextKey("キーボードに戻る", { onAction(KeyAction.ClosePanel) }, Modifier.weight(1f), KeyStyle.Function, 15.sp)
            when (voice.status) {
                VoiceStatus.Listening -> TextKey("停止", { onAction(KeyAction.StopVoice) }, Modifier.weight(1f), KeyStyle.Accent, 15.sp)
                VoiceStatus.NeedsPermission ->
                    TextKey("権限を許可", { onAction(KeyAction.RequestMicPermission) }, Modifier.weight(1f), KeyStyle.Accent, 15.sp)
                else -> TextKey("もう一度話す", { onAction(KeyAction.StartVoice) }, Modifier.weight(1f), KeyStyle.Accent, 15.sp)
            }
        }
    }
}
