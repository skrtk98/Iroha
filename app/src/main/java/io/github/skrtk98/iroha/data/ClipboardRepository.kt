package io.github.skrtk98.iroha.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * クリップボード履歴を端末内だけに保存する。変更はメインスレッドから行い、保存はバックグラウンドで行う。
 * パスワードなど機密扱いのクリップは記録しない。
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
        val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        ensureLoaded()
        history.add(text.take(MAX_TEXT_LENGTH), System.currentTimeMillis())
        publish()
    }

    @MainThread
    fun togglePin(text: String) = mutate { history.togglePin(text) }

    @MainThread
    fun remove(text: String) = mutate { history.remove(text) }

    @MainThread
    fun clearUnpinned() = mutate { history.clearUnpinned() }

    @MainThread
    fun clearAll() = mutate { history.clearAll() }

    private fun mutate(block: () -> Unit) {
        ensureLoaded()
        block()
        publish()
    }

    private fun publish() {
        _entries.value = history.entries
        val snapshot = history.serialize()
        appScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                runCatching {
                    val temp = File(file.parentFile, file.name + ".tmp")
                    temp.writeText(snapshot)
                    check(temp.renameTo(file))
                }.onFailure { Log.w(TAG, "failed to save clipboard history", it) }
            }
        }
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

    private companion object {
        const val TAG = "ClipboardRepository"
        const val MAX_TEXT_LENGTH = 10_000
    }
}
