package io.github.skrtk98.iroha.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolInputTest {
    @Test
    fun widensSymbols() {
        assertEquals("：", SymbolInput.fullWidth(":", afterDigit = false))
        assertEquals("（", SymbolInput.fullWidth("(", afterDigit = false))
        assertEquals("￥", SymbolInput.fullWidth("¥", afterDigit = false))
        // 全角しかない記号はそのまま読みに入れる
        assertEquals("「", SymbolInput.fullWidth("「", afterDigit = false))
    }

    @Test
    fun keepsDigitsAndSymbolsAfterDigitsHalfWidth() {
        assertNull(SymbolInput.fullWidth("7", afterDigit = false))
        assertNull(SymbolInput.fullWidth(":", afterDigit = true))
        assertNull(SymbolInput.fullWidth(".", afterDigit = true))
    }
}
