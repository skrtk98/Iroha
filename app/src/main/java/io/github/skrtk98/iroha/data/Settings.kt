package io.github.skrtk98.iroha.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class KeyboardLayout { Flick, Qwerty }

/** 設定値。SharedPreferences は小さく依存も無いので軽量化の方針に合う。 */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 日英（かな・英字）の配列。 */
    var defaultLayout: KeyboardLayout
        get() = layoutOf(KEY_LAYOUT) ?: KeyboardLayout.Flick
        set(value) = prefs.edit().putString(KEY_LAYOUT, value.name).apply()

    /** 数字・記号の配列。未設定なら日英と同じにする。 */
    var symbolLayout: KeyboardLayout
        get() = layoutOf(KEY_SYMBOL_LAYOUT) ?: defaultLayout
        set(value) = prefs.edit().putString(KEY_SYMBOL_LAYOUT, value.name).apply()

    private fun layoutOf(key: String) = KeyboardLayout.entries.firstOrNull { it.name == prefs.getString(key, null) }

    /** キーボード全体（ツールバーとキー）の高さの倍率。百分率で持つ。 */
    var keyboardHeightPercent: Int
        get() = prefs.getInt(KEY_KEYBOARD_HEIGHT, 100).coerceIn(MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT)
        set(value) = prefs.edit().putInt(KEY_KEYBOARD_HEIGHT, value).apply()

    /** キーボードの下に空ける余白。片手で届きやすいよう、キーボードを持ち上げる。 */
    var keyboardBottomDp: Int
        get() = prefs.getInt(KEY_KEYBOARD_BOTTOM, 0).coerceIn(0, MAX_BOTTOM_DP)
        set(value) = prefs.edit().putInt(KEY_KEYBOARD_BOTTOM, value).apply()

    /** キーの背景（枠）を描くか。false ならキーの文字だけを並べる。 */
    var keyBorders: Boolean
        get() = prefs.getBoolean(KEY_BORDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BORDERS, value).apply()

    /** QWERTY の日本語・英字で、英字の上に数字の段を出すか。 */
    var qwertyNumberRow: Boolean
        get() = prefs.getBoolean(KEY_QWERTY_NUMBER_ROW, false)
        set(value) = prefs.edit().putBoolean(KEY_QWERTY_NUMBER_ROW, value).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var learningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEARNING, true)
        set(value) = prefs.edit().putBoolean(KEY_LEARNING, value).apply()

    var fullWidthSpace: Boolean
        get() = prefs.getBoolean(KEY_FULL_WIDTH_SPACE, false)
        set(value) = prefs.edit().putBoolean(KEY_FULL_WIDTH_SPACE, value).apply()

    var clipboardHistory: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD, value).apply()

    /** いずれかの設定が変わるたびに Unit を流す。 */
    val changes: Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        const val MIN_HEIGHT_PERCENT = 60
        const val MAX_HEIGHT_PERCENT = 150
        const val MAX_BOTTOM_DP = 240

        private const val KEY_LAYOUT = "layout"
        private const val KEY_SYMBOL_LAYOUT = "symbol_layout"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
        private const val KEY_KEYBOARD_BOTTOM = "keyboard_bottom"
        private const val KEY_BORDERS = "key_borders"
        private const val KEY_QWERTY_NUMBER_ROW = "qwerty_number_row"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_LEARNING = "learning"
        private const val KEY_FULL_WIDTH_SPACE = "full_width_space"
        private const val KEY_CLIPBOARD = "clipboard_history"
    }
}
