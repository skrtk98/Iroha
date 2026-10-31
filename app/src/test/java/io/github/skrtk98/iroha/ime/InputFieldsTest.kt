package io.github.skrtk98.iroha.ime

import android.text.InputType
import org.junit.Assert.assertFalse
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
