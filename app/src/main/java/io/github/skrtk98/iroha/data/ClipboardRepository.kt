package io.github.skrtk98.iroha.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.MainThread
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * クリップボード履歴を端末内だけに保存する。変更はメインスレッドから行い、保存はバックグラウンドで行う。
 * パスワードなど機密扱いのクリップは記録しない。
 *
 * 画像はコピー元の URI がクリップボードの変更で読めなくなるため、中身を [imageDir] に複製して持つ。
 * ファイル名は中身のハッシュなので、同じ画像を何度コピーしても 1 件になる。
 */
class ClipboardRepository(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val settings: Settings,
) {
    private val history = ClipboardHistory()
    private val _entries = MutableStateFlow<List<ClipEntry>>(emptyList())
    val entries: StateFlow<List<ClipEntry>> = _entries.asStateFlow()

    private val file get() = File(context.noBackupFilesDir, "clipboard.tsv")
    private val saveMutex = Mutex()

    /** 保存を終えて、まだ履歴に加えていない画像のファイル名。 */
    private val pendingImages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var loaded = false

    /** 初回だけファイルから読む。小さいファイルなのでメインスレッドで読む。 */
    @MainThread
    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        runCatching { if (file.exists()) history.deserialize(file.readText()) }
            .onFailure { Log.w(TAG, "failed to load clipboard history", it) }
        _entries.value = history.entries
    }

    @MainThread
    fun onPrimaryClipChanged(clip: ClipData?) {
        if (!settings.clipboardHistory || clip == null || clip.itemCount == 0) return
        if (isSensitive(clip.description)) return
        ensureLoaded()
        val item = clip.getItemAt(0)
        val uri = item.uri
        val mimeType = uri?.let { imageMimeType(clip.description, it) }
        if (uri != null && mimeType != null) {
            saveImage(uri, mimeType)
            return
        }
        val text = item.coerceToText(context)?.toString() ?: return
        history.add(text.take(MAX_TEXT_LENGTH), System.currentTimeMillis())
        publish()
    }

    @MainThread
    fun togglePin(key: String) = mutate { history.togglePin(key) }

    @MainThread
    fun remove(key: String) = mutate { history.remove(key) }

    @MainThread
    fun clearUnpinned() = mutate { history.clearUnpinned() }

    @MainThread
    fun clearAll() = mutate { history.clearAll() }

    /** 他のアプリに渡すための、画像の項目の URI と MIME タイプ。 */
    fun imageContent(entry: ClipEntry): Pair<Uri, String>? {
        val name = entry.image ?: return null
        val file = imageFile(context, name)
        if (!file.exists()) return null
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: return null
        return FileProvider.getUriForFile(context, context.packageName + PROVIDER_SUFFIX, file) to mimeType
    }

    private fun mutate(block: () -> Unit) {
        ensureLoaded()
        block()
        publish()
    }

    /** 履歴から外れた画像のファイルを消し、履歴を保存する。 */
    private fun publish() {
        val removed = _entries.value.mapNotNullTo(HashSet()) { it.image } - history.entries.mapNotNullTo(HashSet()) { it.image }
        _entries.value = history.entries
        val snapshot = history.serialize()
        appScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(snapshot)
                    check(temp.renameTo(file))
                }.onFailure { Log.w(TAG, "failed to save clipboard history", it) }
                // 消した後に同じ画像がコピーし直されていれば、そのファイルは残す
                val inUse = _entries.value.mapNotNullTo(HashSet()) { it.image } + pendingImages
                for (name in removed - inUse) imageFile(context, name).delete()
            }
        }
    }

    /** コピー元から画像を読んで保存し、保存できたら履歴に加える。 */
    private fun saveImage(uri: Uri, mimeType: String) {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: return
        val time = System.currentTimeMillis()
        appScope.launch(Dispatchers.IO) {
            val name = runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_IMAGE_BYTES) }
                    ?: return@launch
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val name = "${hash.take(32)}.$extension"
                saveMutex.withLock {
                    val target = imageFile(context, name)
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, "$name.tmp")
                    temp.writeBytes(bytes)
                    check(temp.renameTo(target))
                    // 履歴に加えるまでの間に、古い削除の後始末で消されないようにする
                    pendingImages += name
                }
                name
            }.onFailure { Log.w(TAG, "failed to save clipboard image", it) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                history.add(ClipEntry("", time = time, image = name))
                publish()
                pendingImages -= name
            }
        }
    }

    /** 最後まで読み、[limit] バイトを超えたら null。 */
    private fun InputStream.readAtMost(limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
    }

    /** 貼り付け先で扱える静止画・アニメーション画像なら、その MIME タイプ。 */
    private fun imageMimeType(description: ClipDescription, uri: Uri): String? {
        val declared = (0 until description.mimeTypeCount).map { description.getMimeType(it) }
        val candidates = declared + listOfNotNull(runCatching { context.contentResolver.getType(uri) }.getOrNull())
        return candidates.firstOrNull { it in IMAGE_TYPES }
    }

    private fun isSensitive(description: ClipDescription?): Boolean {
        val extras: PersistableBundle = description?.extras ?: return false
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        return extras.getBoolean(key, false)
    }

    companion object {
        private const val TAG = "ClipboardRepository"
        private const val MAX_TEXT_LENGTH = 10_000
        private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
        private const val IMAGE_DIR = "clips"

        /** AndroidManifest の FileProvider の authority は、パッケージ名にこれを付けたもの。 */
        private const val PROVIDER_SUFFIX = ".clips"
        private val IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

        /** 画像の項目のファイル。FileProvider の files-path と同じ場所に置く。 */
        fun imageFile(context: Context, name: String) = File(File(context.filesDir, IMAGE_DIR), name)
    }
}
