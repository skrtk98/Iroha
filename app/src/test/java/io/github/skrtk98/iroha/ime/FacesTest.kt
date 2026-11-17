package io.github.skrtk98.iroha.ime

import io.github.skrtk98.iroha.data.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacesTest {
    @Test
    fun symbolFaceFollowsItsOwnLayout() {
        assertEquals(KeyboardFace.FlickNumber, Faces.symbol(KeyboardLayout.Flick))
        assertEquals(KeyboardFace.QwertySymbols, Faces.symbol(KeyboardLayout.Qwerty))
        assertTrue(Faces.isSymbol(KeyboardFace.FlickNumber))
        assertFalse(Faces.isSymbol(KeyboardFace.FlickAlphabet))
    }

    @Test
    fun textFaceSelectsAlphabetOrKana() {
        assertEquals(KeyboardFace.FlickAlphabet, Faces.text(KeyboardLayout.Flick, alphabet = true))
        assertEquals(KeyboardFace.QwertyKana, Faces.text(KeyboardLayout.Qwerty, alphabet = false))
    }

    @Test
    fun otherLanguageKeepsTheLayout() {
        assertEquals(KeyboardFace.FlickAlphabet, Faces.otherLanguage(KeyboardFace.FlickKana))
        assertEquals(KeyboardFace.FlickKana, Faces.otherLanguage(KeyboardFace.FlickAlphabet))
        assertEquals(KeyboardFace.QwertyAlphabet, Faces.otherLanguage(KeyboardFace.QwertyKana))
        assertEquals(KeyboardFace.QwertyKana, Faces.otherLanguage(KeyboardFace.QwertyAlphabet))
    }

    @Test
    fun languageToggleStaysOnTheSymbolFace() {
        assertEquals(KeyboardFace.QwertySymbols, Faces.afterLanguageToggle(KeyboardFace.QwertySymbols, KeyboardFace.QwertyKana))
        assertEquals(KeyboardFace.FlickNumber, Faces.afterLanguageToggle(KeyboardFace.FlickNumber, KeyboardFace.FlickAlphabet))
        assertEquals(KeyboardFace.QwertyAlphabet, Faces.afterLanguageToggle(KeyboardFace.QwertyKana, KeyboardFace.QwertyKana))
        assertEquals(KeyboardFace.FlickKana, Faces.afterLanguageToggle(KeyboardFace.FlickAlphabet, KeyboardFace.FlickAlphabet))
    }
}
