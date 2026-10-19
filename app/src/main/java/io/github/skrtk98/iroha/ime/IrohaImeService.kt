package io.github.skrtk98.iroha.ime

import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.skrtk98.iroha.IrohaApplication
import io.github.skrtk98.iroha.settings.SettingsActivity
import io.github.skrtk98.iroha.ui.keyboard.KeyboardScreen
import io.github.skrtk98.iroha.ui.theme.IrohaTheme
import kotlinx.coroutines.launch

/**
 * IME 本体。InputMethodService は Activity ではないため、Compose が必要とする
 * Lifecycle と SavedState のオーナーを自前で提供する。
 */
class IrohaImeService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ImeHost {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val app get() = application as IrohaApplication
    private lateinit var controller: KeyboardController
    private val clipboardManager by lazy { getSystemService(ClipboardManager::class.java) }

    /** IME が既定の入力方法である間だけ、バックグラウンドでもクリップボードを読める。 */
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull()
        app.clipboardRepository.onPrimaryClipChanged(clip)
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val bridge = InputConnectionBridge(
            connection = { currentInputConnection },
            editorInfo = { currentInputEditorInfo },
            highlightColor = PREEDIT_HIGHLIGHT,
        )
        controller = KeyboardController(lifecycleScope, app.settings, bridge, this, app.clipboardRepository)
        lifecycleScope.launch { controller.onEngineLoaded(app.engineRepository.load()) }
        clipboardManager.addPrimaryClipChangedListener(clipListener)
    }

    override fun onCreateInputView(): View {
        val decorView = checkNotNull(window.window).decorView
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        return ComposeView(this).apply {
            setContent {
                val state by controller.state.collectAsStateWithLifecycle()
                IrohaTheme {
                    KeyboardScreen(state = state, onAction = controller::onAction)
                }
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        controller.onStartInput(info, restarting)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.commitAll()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        controller.onFinishInput()
        app.engineRepository.saveLearningAsync()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(clipListener)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun switchInputMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!switchToNextInputMethod(false)) showPicker()
        } else {
            showPicker()
        }
    }

    private fun showPicker() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun hideKeyboard() {
        requestHideSelf(0)
    }

    private companion object {
        const val PREEDIT_HIGHLIGHT = 0x664A90E2
    }
}
