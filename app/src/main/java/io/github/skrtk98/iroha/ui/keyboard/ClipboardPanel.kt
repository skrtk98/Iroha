package io.github.skrtk98.iroha.ui.keyboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import io.github.skrtk98.iroha.data.ClipboardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

/** クリップボード履歴。タップで貼り付け、ピンで固定、ゴミ箱で削除。画像は縮小して見せる。 */
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
                    "コピーした文字列や画像がここに表示されます",
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
                items(clips, key = { it.key }) { clip ->
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
            .clickable { onAction(KeyAction.PasteClip(clip)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (clip.image != null) {
            ClipThumbnail(
                clip.image,
                Modifier.weight(1f).fillMaxHeight().padding(start = 6.dp, top = 6.dp, bottom = 6.dp).clip(RoundedCornerShape(6.dp)),
            )
        } else {
            Text(
                clip.text,
                modifier = Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                // 背景色に合わせた文字色を明示する。指定しないと IME の画面では薄い既定色になる
                color = if (clip.pinned) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column {
            IconButton(onClick = { onAction(KeyAction.TogglePinClip(clip.key)) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_push_pin),
                    contentDescription = if (clip.pinned) "固定を解除" else "固定",
                    tint = if (clip.pinned) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = { onAction(KeyAction.DeleteClip(clip.key)) }, modifier = Modifier.size(36.dp)) {
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

/** 保存した画像を、カードに収まる大きさに縮めて読み込んで表示する。読めなければ何も出さない。 */
@Composable
private fun ClipThumbnail(name: String, modifier: Modifier) {
    val context = LocalContext.current
    val maxSize = with(LocalDensity.current) { ThumbnailSize.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(ClipboardRepository.imageFile(context, name), maxSize) }
    }
    Box(modifier) {
        bitmap?.let {
            Image(it, contentDescription = "コピーした画像", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

/** カードの幅程度。短辺がこれより大きい画像は 2 の累乗で間引いて読む。 */
private val ThumbnailSize = 200.dp

private fun decodeThumbnail(file: File, maxSize: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    val short = minOf(bounds.outWidth, bounds.outHeight)
    val long = maxOf(bounds.outWidth, bounds.outHeight)
    // 短辺をカードに合わせて間引く。極端に細長い画像は、長辺も上限に収まるまで間引く
    while (short / (sample * 2) >= maxSize || long / sample > maxSize * 4) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
