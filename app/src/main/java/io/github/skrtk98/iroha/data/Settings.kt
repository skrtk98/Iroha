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

    var defaultLayout: KeyboardLayout
        get() = KeyboardLayout.entries.firstOrNull { it.name == prefs.getString(KEY_LAYOUT, null) } ?: KeyboardLayout.Flick
        set(value) = prefs.edit().putString(KEY_LAYOUT, value.name).apply()

    var keyHeightDp: Int
        get() = prefs.getInt(KEY_HEIGHT, 52)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    /** キーボード全体（ツールバーとキー）の高さの倍率。百分率で持つ。 */
    var keyboardHeightPercent: Int
        get() = prefs.getInt(KEY_KEYBOARD_HEIGHT, 100)
        set(value) = prefs.edit().putInt(KEY_KEYBOARD_HEIGHT, value).apply()

    /** キーの背景（枠）を描くか。false ならキーの文字だけを並べる。 */
    var keyBorders: Boolean
        get() = prefs.getBoolean(KEY_BORDERS, true)
        set(value) = prefs.edit().putBoolean(KEY_BORDERS, value).apply()

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

    private companion object {
        const val KEY_LAYOUT = "layout"
        const val KEY_HEIGHT = "key_height"
        const val KEY_KEYBOARD_HEIGHT = "keyboard_height"
        const val KEY_BORDERS = "key_borders"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_LEARNING = "learning"
        const val KEY_FULL_WIDTH_SPACE = "full_width_space"
        const val KEY_CLIPBOARD = "clipboard_history"
    }
}
