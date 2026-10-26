package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class QwertyPunctuationTest {
    @Test
    fun shiftSwapsJapanesePunctuation() {
        assertEquals(QwertyPunctuation("ー", listOf("！", "？"), listOf("、", "。")), KeyLayouts.qwertyPunctuation(kana = true, shifted = false))
        assertEquals(QwertyPunctuation("～", listOf("（", "）"), listOf("「", "」")), KeyLayouts.qwertyPunctuation(kana = true, shifted = true))
    }

    @Test
    fun shiftSwapsAlphabetPunctuation() {
        assertEquals(QwertyPunctuation("-", listOf("!", "?"), listOf(",", ".")), KeyLayouts.qwertyPunctuation(kana = false, shifted = false))
        assertEquals(QwertyPunctuation("_", listOf("(", ")"), listOf("'", "\"")), KeyLayouts.qwertyPunctuation(kana = false, shifted = true))
    }

    @Test
    fun everyFaceKeepsTheSameNumberOfKeys() {
        // シフトで記号が替わっても、キーの数と並びの幅は変えない
        for (kana in listOf(true, false)) {
            val plain = KeyLayouts.qwertyPunctuation(kana, shifted = false)
            val shifted = KeyLayouts.qwertyPunctuation(kana, shifted = true)
            assertEquals(plain.left.size, shifted.left.size)
            assertEquals(plain.right.size, shifted.right.size)
        }
    }
}
