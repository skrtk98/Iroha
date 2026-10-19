package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import io.github.skrtk98.iroha.data.ClipEntry
import io.github.skrtk98.iroha.ime.KeyAction

/** クリップボード履歴。タップで貼り付け、ピンで固定、ゴミ箱で削除。 */
@Composable
fun ClipboardPanel(
    clips: List<ClipEntry>,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (clips.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "コピーした文字列がここに表示されます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(clips, key = { it.text }) { clip ->
                    ClipCard(clip, onAction)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            TextKey("キーボードに戻る", { onAction(KeyAction.ClosePanel) }, Modifier.weight(2f), KeyStyle.Function, 15.sp)
            TextKey("固定以外を消去", { onAction(KeyAction.ClearClips) }, Modifier.weight(1.5f), KeyStyle.Function, 14.sp)
        }
    }
}

@Composable
private fun ClipCard(clip: ClipEntry, onAction: (KeyAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (clip.pinned) colors.secondaryContainer else colors.surfaceContainerHighest)
            .clickable { onAction(KeyAction.PasteClip(clip.text)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            clip.text,
            modifier = Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
        )
        Column {
            IconButton(onClick = { onAction(KeyAction.TogglePinClip(clip.text)) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_push_pin),
                    contentDescription = if (clip.pinned) "固定を解除" else "固定",
                    tint = if (clip.pinned) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onAction(KeyAction.DeleteClip(clip.text)) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "削除",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
