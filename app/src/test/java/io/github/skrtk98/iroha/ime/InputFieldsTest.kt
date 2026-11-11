package io.github.skrtk98.iroha.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputFieldsTest {
    private val text = InputType.TYPE_CLASS_TEXT

    @Test
    fun onlyMultiLineTextFieldsEnterNewlineAsText() {
        assertTrue(InputFields.entersNewlineAsText(text or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertFalse(InputFields.entersNewlineAsText(text))
        // 文字の欄でなければ、同じビットが立っていても改行の文字は入れない
        assertFalse(InputFields.entersNewlineAsText(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
    }

    @Test
    fun enterRunsTheFieldActionUnlessNewlineIsRequested() {
        assertEquals(EditorInfo.IME_ACTION_SEARCH, InputFields.enterAction(EditorInfo.IME_ACTION_SEARCH))
        // TextView の複数行の欄は動作を指定していても改行を求める
        assertNull(InputFields.enterAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertNull(InputFields.enterAction(EditorInfo.IME_ACTION_UNSPECIFIED))
        assertNull(InputFields.enterAction(EditorInfo.IME_ACTION_NONE))
    }

    @Test
    fun enterKeyShowsWhatItDoes() {
        // Google アプリの検索欄（複数行・検索の動作）
        assertEquals(EnterKind.Search, InputFields.enterKind(0x10000003))
        assertEquals(EditorInfo.IME_ACTION_SEARCH, InputFields.enterAction(0x10000003))
        // 表示を用意していない動作は、改行と表示して改行する
        assertEquals(EnterKind.Newline, InputFields.enterKind(EditorInfo.IME_ACTION_PREVIOUS))
        assertNull(InputFields.enterAction(EditorInfo.IME_ACTION_PREVIOUS))
        for (action in 0..7) {
            val shownAsAction = InputFields.enterKind(action) != EnterKind.Newline
            assertEquals(shownAsAction, InputFields.enterAction(action) != null)
        }
    }

    @Test
    fun uriFieldsStartInJapanese() {
        // Chrome のアドレスバー（URI・候補なし）
        val omnibox = text or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        assertFalse(InputFields.startsInAlphabet(omnibox))
        assertFalse(InputFields.isPassword(omnibox))
    }

    @Test
    fun emailFieldsStartInAlphabetButCanConvert() {
        val email = text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertTrue(InputFields.startsInAlphabet(email))
        assertFalse(InputFields.isPassword(email))
    }

    @Test
    fun forceAsciiFieldsStartInAlphabetButCanConvert() {
        // ログイン ID の欄のように、種類は文字のままで英字を求める
        assertTrue(InputFields.startsInAlphabet(text, EditorInfo.IME_FLAG_FORCE_ASCII or EditorInfo.IME_ACTION_NEXT))
        assertFalse(InputFields.startsInAlphabet(text, EditorInfo.IME_ACTION_NEXT))
        assertFalse(InputFields.isPassword(text))
    }

    @Test
    fun passwordFieldsNeverConvert() {
        for (variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )) {
            assertTrue(InputFields.isPassword(text or variation))
            assertTrue(InputFields.startsInAlphabet(text or variation))
        }
    }

    @Test
    fun numberFieldsAreNumeric() {
        assertTrue(InputFields.isNumeric(InputType.TYPE_CLASS_NUMBER))
        assertTrue(InputFields.isNumeric(InputType.TYPE_CLASS_PHONE))
        assertFalse(InputFields.isNumeric(text))
        // 数字のパスワードは数字の欄として扱う
        assertFalse(InputFields.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }
}
