package io.github.skrtk98.iroha.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolInputTest {
    @Test
    fun widensSymbols() {
        assertEquals("：", SymbolInput.fullWidth(":", before = 'あ'))
        assertEquals("（", SymbolInput.fullWidth("(", before = null))
        assertEquals("￥", SymbolInput.fullWidth("¥", before = null))
        // 全角しかない記号はそのまま読みに入れる
        assertEquals("「", SymbolInput.fullWidth("「", before = null))
    }

    @Test
    fun keepsDigitsAndNumberJoinersHalfWidth() {
        assertNull(SymbolInput.fullWidth("7", before = null))
        assertNull(SymbolInput.fullWidth(":", before = '2'))
        assertNull(SymbolInput.fullWidth(".", before = '3'))
        assertNull(SymbolInput.fullWidth("^", before = '2'))
        // 数字の直後でなければ全角に戻す (1☆( の ( など)
        assertEquals("（", SymbolInput.fullWidth("(", before = '☆'))
    }

    @Test
    fun widensBracketsAfterDigits() {
        // （1） のように左右の幅をそろえる
        assertEquals("）", SymbolInput.fullWidth(")", before = '1'))
        assertEquals("＂", SymbolInput.fullWidth("\"", before = '1'))
    }

    @Test
    fun labelsShowSymbolsFullWidthAndKeepDigits() {
        assertEquals("：", SymbolInput.label(":"))
        assertEquals("（）［］", SymbolInput.label("()[]"))
        assertEquals("￥", SymbolInput.label("¥"))
        // 数字のキーの印字は変えない
        assertEquals("1", SymbolInput.label("1"))
    }
}
