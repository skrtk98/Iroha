package io.github.skrtk98.iroha.ui.keyboard

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
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
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.ClipEntry
import io.github.skrtk98.iroha.ime.KeyAction

/**
 * クリップボード履歴。タップで貼り付ける。長押しで選択を始め、選んだ項目をまとめて固定・削除する。
 * 画像は縮小して見せる。キーボードへは上部のクリップボードのボタンで戻る。
 */
@Composable
fun ClipboardPanel(
    clips: List<ClipEntry>,
    onAction: (KeyAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 選択中の項目のキー。null なら選択していない。履歴から消えた項目は選択から外して扱う
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    val selected = selection?.let { keys -> clips.mapNotNullTo(LinkedHashSet()) { clip -> clip.key.takeIf { it in keys } } }
    Column(modifier) {
        if (clips.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "コピーした文字列や画像がここに表示されます",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        if (selected == null) {
            BrowseHeader(clips, onAction)
        } else {
            SelectionHeader(
                clips.filter { it.key in selected },
                onCancel = { selection = null },
                onAction = {
                    onAction(it)
                    selection = null
                },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(clips, key = { it.key }) { clip ->
                ClipCard(
                    clip,
                    selected = selected?.let { clip.key in it },
                    onClick = {
                        if (selected == null) onAction(KeyAction.PasteClip(clip)) else selection = selected.toggle(clip.key)
                    },
                    onLongClick = { selection = (selected ?: emptySet()) + clip.key },
                )
            }
        }
    }
}

private fun Set<String>.toggle(key: String) = if (key in this) this - key else this + key

/** 選択していないときの見出し。操作の案内と、固定していない項目の一括消去。 */
@Composable
private fun BrowseHeader(clips: List<ClipEntry>, onAction: (KeyAction) -> Unit) {
    Row(Modifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "長押しで選択して固定・削除",
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (clips.any { !it.pinned }) {
            HeaderButton(R.drawable.ic_delete_sweep, "固定以外を消去") { onAction(KeyAction.ClearClips) }
        }
    }
}

/** 選択中の見出し。選んだ数と、まとめて削除・固定するボタン。どちらも実行したら選択を終える。 */
@Composable
private fun SelectionHeader(selected: List<ClipEntry>, onCancel: () -> Unit, onAction: (KeyAction) -> Unit) {
    val keys = selected.mapTo(LinkedHashSet()) { it.key }
    // 選んだ項目がすべて固定済みなら固定を外し、そうでなければ固定する
    val unpin = selected.isNotEmpty() && selected.all { it.pinned }
    Row(Modifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        HeaderButton(R.drawable.ic_close, "選択をやめる", onClick = onCancel)
        Text(
            "${selected.size} 件を選択",
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HeaderButton(R.drawable.ic_delete, "削除", enabled = keys.isNotEmpty()) { onAction(KeyAction.DeleteClips(keys)) }
        HeaderButton(R.drawable.ic_push_pin, if (unpin) "固定を解除" else "固定", enabled = keys.isNotEmpty()) {
            onAction(KeyAction.PinClips(keys, pinned = !unpin))
        }
    }
}

private val HeaderHeight = 44.dp

@Composable
private fun HeaderButton(@DrawableRes icon: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        // IME の画面では既定の色が背景に合わないので、色を明示する
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(painterResource(icon), contentDescription = description, tint = if (enabled) color else color.copy(alpha = 0.38f))
    }
}

/**
 * 履歴の 1 項目。[selected] が null なら選択していないときの表示で、固定した項目に印を付ける。
 * 選択中は右上に選んだかどうかを示す。
 */
@Composable
private fun ClipCard(clip: ClipEntry, selected: Boolean?, onClick: () -> Unit, onLongClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val vibrate = LocalKeyHaptics.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(CardShape)
            .background(if (clip.pinned) colors.secondaryContainer else colors.surfaceContainerHighest)
            .then(if (selected == true) Modifier.border(2.dp, colors.primary, CardShape) else Modifier)
            .combinedClickable(
                onClickLabel = if (selected == null) "貼り付け" else if (selected) "選択を外す" else "選択",
                onLongClickLabel = "選択",
                onLongClick = {
                    if (vibrate) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                onClick = onClick,
            ),
    ) {
        // 右上の印と重ならないよう、右側を空ける
        val content = Modifier.fillMaxSize().padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 26.dp)
        if (clip.image != null) {
            ClipThumbnail(clip.image, Modifier.fillMaxSize().padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 26.dp).clip(RoundedCornerShape(6.dp)))
        } else {
            Text(
                clip.text,
                modifier = content,
                // 背景色に合わせた文字色を明示する。指定しないと IME の画面では薄い既定色になる
                color = if (clip.pinned) colors.onSecondaryContainer else colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val mark = when {
            selected == true -> Triple(R.drawable.ic_check_circle, "選択中", colors.primary)
            selected == false -> Triple(R.drawable.ic_radio_button_unchecked, null, colors.onSurfaceVariant)
            clip.pinned -> Triple(R.drawable.ic_push_pin, "固定中", colors.primary)
            else -> null
        }
        mark?.let { (icon, description, tint) ->
            Icon(
                painterResource(icon),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp),
            )
        }
    }
}

private val CardShape = RoundedCornerShape(10.dp)

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
