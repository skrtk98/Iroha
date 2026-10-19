package io.github.skrtk98.iroha.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/** 音声認識の結果の受け口。 */
interface VoiceListener {
    fun onPartial(text: String)
    fun onResult(text: String)
    fun onError(message: String)
    fun onFinished()
}

/** 音声認識。テストで差し替えられるよう Android の SpeechRecognizer を隠す。 */
interface VoiceRecognizer {
    val hasPermission: Boolean

    /** 端末内で認識できる（ネットワークを使わない）か。 */
    val isOnDevice: Boolean
    fun start(listener: VoiceListener)
    fun stop()
    fun cancel()
}

/**
 * 端末内認識 (API 31+) が使えればそれを使い、使えなければオフライン優先を指定した通常の認識を使う。
 * メインスレッドから呼ぶこと。
 */
class AndroidVoiceRecognizer(private val context: Context) : VoiceRecognizer {
    private var recognizer: SpeechRecognizer? = null

    override val hasPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override val isOnDevice: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun start(listener: VoiceListener) {
        cancel()
        val created = when {
            isOnDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
        if (created == null) {
            listener.onError("この端末では音声認識を使えません")
            listener.onFinished()
            return
        }
        recognizer = created
        created.setRecognitionListener(Listener(listener))
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        created.startListening(intent)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.destroy()
        recognizer = null
    }

    private inner class Listener(private val listener: VoiceListener) : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(listener::onPartial)
        }

        override fun onResults(results: Bundle?) {
            firstResult(results)?.takeIf { it.isNotBlank() }?.let(listener::onResult)
            finish()
        }

        override fun onError(error: Int) {
            listener.onError(messageOf(error))
            finish()
        }

        private fun finish() {
            cancel()
            listener.onFinished()
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun messageOf(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "聞き取れませんでした"
        SpeechRecognizer.ERROR_AUDIO -> "マイクを使えません"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイクの権限がありません"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "端末内の音声認識データがありません。端末の設定で日本語のオフライン音声認識をダウンロードしてください"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "日本語の端末内音声認識データがありません。端末の設定の「オンデバイス音声認識」で日本語をダウンロードしてください"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です"
        else -> "音声認識に失敗しました ($error)"
    }
}
