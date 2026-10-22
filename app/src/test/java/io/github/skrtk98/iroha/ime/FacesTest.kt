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
    fun otherLayoutKeepsTheKindOfFace() {
        assertEquals(KeyboardFace.QwertyKana, Faces.otherLayout(KeyboardFace.FlickKana))
        assertEquals(KeyboardFace.FlickAlphabet, Faces.otherLayout(KeyboardFace.QwertyAlphabet))
        assertEquals(KeyboardFace.QwertySymbols, Faces.otherLayout(KeyboardFace.FlickNumber))
        assertEquals(KeyboardFace.FlickNumber, Faces.otherLayout(KeyboardFace.QwertySymbols))
    }

    @Test
    fun textFaceSelectsAlphabetOrKana() {
        assertEquals(KeyboardFace.FlickAlphabet, Faces.text(KeyboardLayout.Flick, alphabet = true))
        assertEquals(KeyboardFace.QwertyKana, Faces.text(KeyboardLayout.Qwerty, alphabet = false))
    }
}
