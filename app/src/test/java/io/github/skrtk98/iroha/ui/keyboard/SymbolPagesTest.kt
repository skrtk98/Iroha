package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolPagesTest {
    @Test
    fun pagesHaveTheSameShape() {
        // 3 段目は左端の切り替えキーと右端の削除キーの間に収める
        assertEquals(listOf(10, 10, 8), KeyLayouts.symbolRows.map { it.size })
        assertEquals(listOf(10, 10, 8), KeyLayouts.moreSymbolRows.map { it.size })
    }

    @Test
    fun noSymbolAppearsTwice() {
        // 下段の , . < > も含めて、2 つのページで同じ記号を重ねない
        val all = KeyLayouts.symbolRows.flatten() + KeyLayouts.moreSymbolRows.flatten() + listOf(",", ".", "<", ">")
        val duplicates = all.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("重複: $duplicates", duplicates.isEmpty())
    }
}
