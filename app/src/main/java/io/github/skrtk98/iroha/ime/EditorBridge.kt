package io.github.skrtk98.iroha.ime

import android.content.ClipDescription
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import io.github.skrtk98.iroha.engine.session.Preedit

/** エディタへの書き込み。テストで差し替えられるよう InputConnection を隠す。 */
interface EditorBridge {
    fun commitText(text: String)
    fun setPreedit(preedit: Preedit)
    fun finishComposing()
    fun deleteBackward()
    fun sendEnter()
    fun moveCursor(delta: Int)

    /** 方向キーなどのキーイベントを送る。[select] なら Shift を押した状態で送り、選択範囲を伸ばす。 */
    fun sendNavigationKey(keyCode: Int, select: Boolean)

    /** android.R.id.copy などのコンテキストメニュー操作。 */
    fun performContextMenuAction(id: Int)

    /** 画像を貼り付ける。入力欄がその形式の画像を受け付けなければ false。 */
    fun commitImage(uri: Uri, mimeType: String): Boolean
}

class InputConnectionBridge(
    private val connection: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo?,
    private val highlightColor: Int,
) : EditorBridge {

    override fun commitText(text: String) {
        connection()?.commitText(text, 1)
    }

    override fun setPreedit(preedit: Preedit) {
        val ic = connection() ?: return
        if (preedit.text.isEmpty()) {
            ic.setComposingText("", 1)
            return
        }
        val spanned = SpannableString(preedit.text)
        spanned.setSpan(UnderlineSpan(), 0, preedit.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or Spanned.SPAN_COMPOSING)
        if (preedit.highlightStart in 0 until preedit.highlightEnd) {
            spanned.setSpan(
                BackgroundColorSpan(highlightColor),
                preedit.highlightStart,
                preedit.highlightEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        // newCursorPosition はテキスト末尾からの相対位置
        ic.setComposingText(spanned, preedit.caret - preedit.text.length + 1)
    }

    override fun finishComposing() {
        connection()?.finishComposingText()
    }

    override fun deleteBackward() {
        val ic = connection() ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            sendKey(ic, KeyEvent.KEYCODE_DEL)
        }
    }

    override fun sendEnter() {
        val ic = connection() ?: return
        val info = editorInfo()
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_UNSPECIFIED
        val noEnterAction = (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiLine = (info?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (!noEnterAction && !multiLine && action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            sendKey(ic, KeyEvent.KEYCODE_ENTER)
        }
    }

    override fun moveCursor(delta: Int) {
        val ic = connection() ?: return
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) {
            if (blockedAtEdge(ic, code, select = false)) return
            sendKey(ic, code)
        }
    }

    override fun sendNavigationKey(keyCode: Int, select: Boolean) {
        val ic = connection() ?: return
        if (blockedAtEdge(ic, keyCode, select)) return
        val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    override fun performContextMenuAction(id: Int) {
        connection()?.performContextMenuAction(id)
    }

    override fun commitImage(uri: Uri, mimeType: String): Boolean {
        val ic = connection() ?: return false
        val info = editorInfo() ?: return false
        val accepted = EditorInfoCompat.getContentMimeTypes(info)
        if (accepted.none { ClipDescription.compareMimeTypes(mimeType, it) }) return false
        val content = InputContentInfoCompat(uri, ClipDescription("image", arrayOf(mimeType)), null)
        return InputConnectionCompat.commitContent(
            ic,
            info,
            content,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }

    private fun blockedAtEdge(ic: InputConnection, keyCode: Int, select: Boolean) = isBlockedAtEdge(
        keyCode,
        select,
        hasSelection = !ic.getSelectedText(0).isNullOrEmpty(),
        before = { ic.getTextBeforeCursor(1, 0) },
        after = { ic.getTextAfterCursor(1, 0) },
    )

    private fun sendKey(ic: InputConnection, code: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}

/**
 * 文頭で ← ↑、文末で → ↓ を送らないか。欄が処理できない方向キーは隣の部品へのフォーカス移動に使われ、
 * 欄からフォーカスが外れてキーボードが閉じてしまう。選択を伸ばさずに ← → を押すと選択を畳むだけなので送る。
 * [before] と [after] はカーソルの前後 1 文字で、欄が答えなければ null（そのときは送る）。
 */
internal fun isBlockedAtEdge(
    keyCode: Int,
    select: Boolean,
    hasSelection: Boolean,
    before: () -> CharSequence?,
    after: () -> CharSequence?,
): Boolean {
    val horizontal = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
    if (horizontal && hasSelection && !select) return false
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> before()?.isEmpty() == true
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> after()?.isEmpty() == true
        else -> false
    }
}
