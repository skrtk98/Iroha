package io.github.skrtk98.iroha.engine.text

import org.junit.Assert.assertEquals
import org.junit.Test

class RomajiConverterTest {
    @Test
    fun convertsBasicSyllables() {
        assertEquals("かんじ", RomajiConverter.convert("kanji"))
        assertEquals("にほんご", RomajiConverter.convert("nihongo"))
        assertEquals("しゃしん", RomajiConverter.convert("shashinn"))
        assertEquals("きって", RomajiConverter.convert("kitte"))
        assertEquals("こんにちは", RomajiConverter.convert("konnnichiha"))
    }

    @Test
    fun keepsPendingRomaji() {
        assertEquals("かんk", RomajiConverter.convert("kank"))
        assertEquals("かn", RomajiConverter.convert("kan"))
        assertEquals("ky", RomajiConverter.convert("ky"))
    }

    @Test
    fun finishTurnsTrailingNIntoKana() {
        assertEquals("かん", RomajiConverter.finish("kan"))
    }

    @Test
    fun convertsSymbols() {
        assertEquals("ー、。", RomajiConverter.convert("-,."))
    }
}
