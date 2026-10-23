package io.github.skrtk98.iroha.data

import android.content.Context
import android.graphics.Paint
import android.util.Log
import io.github.skrtk98.iroha.engine.conv.Converter
import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import io.github.skrtk98.iroha.engine.user.LearningStore
import io.github.skrtk98.iroha.engine.user.UserDictionary
import io.github.skrtk98.iroha.engine.user.UserDictionaryTsv
import io.github.skrtk98.iroha.engine.user.UserWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap

/** 読み込み済みの変換エンジン一式。 */
class Engine(
    val dictionary: SystemDictionary,
    val userDictionary: UserDictionary,
    val learning: LearningStore,
) {
    val converter = Converter(dictionary, listOf(learning, userDictionary), EmojiSupport::canShow)
}

/** 端末のフォントで表示できる絵文字か。新しい絵文字は古い端末では豆腐になるので候補から外す。 */
private object EmojiSupport {
    private val paint = Paint()
    private val cache = ConcurrentHashMap<String, Boolean>()

    fun canShow(text: String): Boolean {
        // hasGlyph は 1 つの字形で描けるかを見るので、普通の文字列には使えない。絵文字を含むときだけ確かめる
        if (text.codePoints().noneMatch { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }) return true
        return cache.getOrPut(text) {
            // Paint はスレッドセーフではない。候補は複数のスレッドから求める
            synchronized(paint) { paint.hasGlyph(text) }
        }
    }
}

/**
 * システム辞書を APK から直接 mmap し、ユーザー辞書と学習結果をファイルから読み書きする。
 * 辞書は無圧縮で APK に入れているため、展開やヒープへの読み込みは発生しない。
 */
class EngineRepository(
    private val context: Context,
    private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()

    // load() の mutex とは分ける。Mutex は再入できず、更新の中で load() を呼ぶため。
    private val userDictionaryMutex = Mutex()
    private val learningMutex = Mutex()
    private val _engine = MutableStateFlow<Engine?>(null)
    val engine: StateFlow<Engine?> = _engine.asStateFlow()

    private val userDictionaryFile get() = File(context.filesDir, "user_dictionary.tsv")
    private val learningFile get() = File(context.filesDir, "learning.tsv")

    /** 未読み込みなら読み込む。2 回目以降はすぐ返る。 */
    suspend fun load(): Engine = _engine.value ?: mutex.withLock {
        _engine.value ?: withContext(Dispatchers.IO) { open() }.also { _engine.value = it }
    }

    private fun open(): Engine {
        val dictionary = context.assets.openFd(DICTIONARY_ASSET).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                SystemDictionary(channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length))
            }
        }
        val userDictionary = UserDictionary(dictionary::posId)
        if (userDictionaryFile.exists()) {
            // 上限は新しく追加するときだけ効かせる。保存済みの辞書が上限を超えていても起動は止めない
            val saved = UserDictionaryTsv.parse(userDictionaryFile.readText(), maxWords = Int.MAX_VALUE).words
            userDictionary.replaceAll(saved)
        }
        val learning = LearningStore()
        if (learningFile.exists()) learning.deserialize(learningFile.readText())
        return Engine(dictionary, userDictionary, learning)
    }

    /**
     * ユーザー辞書を変更して保存する。変更から保存までを直列にするので、
     * 追加・削除・読み込みが重なっても、最後の変更まで含んだ内容がファイルに残る。
     */
    suspend fun updateUserDictionary(block: (UserDictionary) -> Unit) {
        val engine = load()
        userDictionaryMutex.withLock {
            block(engine.userDictionary)
            withContext(Dispatchers.IO) {
                writeAtomically(userDictionaryFile, UserDictionaryTsv.format(engine.userDictionary.words))
            }
        }
    }

    fun userWords(): List<UserWord> = _engine.value?.userDictionary?.words.orEmpty()

    /** 学習結果をバックグラウンドで保存する。IME を閉じたときに呼ぶ。 */
    fun saveLearningAsync() {
        val engine = _engine.value ?: return
        appScope.launch(Dispatchers.IO) {
            learningMutex.withLock {
                runCatching { writeAtomically(learningFile, engine.learning.serialize()) }
                    .onFailure { Log.w(TAG, "failed to save learning", it) }
            }
        }
    }

    suspend fun clearLearning() {
        val engine = load()
        learningMutex.withLock {
            engine.learning.clear()
            withContext(Dispatchers.IO) { learningFile.delete() }
        }
    }

    /** 一時ファイルに書いてから置き換える。一時ファイル名は書き込みごとに変え、途中で失敗したら消す。 */
    private fun writeAtomically(file: File, text: String) {
        val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
        try {
            temp.writeText(text)
            check(temp.renameTo(file)) { "rename failed: $file" }
        } finally {
            temp.delete()
        }
    }

    private companion object {
        const val TAG = "EngineRepository"
        const val DICTIONARY_ASSET = "iroha.dic"
    }
}
