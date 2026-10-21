package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardSizeTest {
    private val perPercent = 2.5f

    @Test
    fun dragTopUpMakesKeyboardTaller() {
        val size = KeyboardSize.Default.dragTop(-25f, perPercent)
        assertEquals(110f, size.heightPercent, 0.01f)
        assertEquals(0f, size.bottomDp, 0.01f)
    }

    @Test
    fun dragTopStopsAtLimits() {
        assertEquals(60f, KeyboardSize.Default.dragTop(1000f, perPercent).heightPercent, 0.01f)
        assertEquals(150f, KeyboardSize.Default.dragTop(-1000f, perPercent).heightPercent, 0.01f)
    }

    @Test
    fun dragBottomUpLiftsBottomAndKeepsTop() {
        val size = KeyboardSize.Default.dragBottom(-50f, perPercent)
        assertEquals(50f, size.bottomDp, 0.01f)
        assertEquals(80f, size.heightPercent, 0.01f)
    }

    @Test
    fun dragBottomStopsWhenHeightHitsMinimum() {
        val size = KeyboardSize.Default.dragBottom(-200f, perPercent)
        assertEquals(60f, size.heightPercent, 0.01f)
        assertEquals(100f, size.bottomDp, 0.01f)
    }

    @Test
    fun dragBottomDownCannotGoBelowScreenEdge() {
        assertEquals(KeyboardSize.Default, KeyboardSize.Default.dragBottom(30f, perPercent))
    }

    @Test
    fun moveKeepsHeight() {
        val size = KeyboardSize(90f, 20f).move(-40f)
        assertEquals(90f, size.heightPercent, 0.01f)
        assertEquals(60f, size.bottomDp, 0.01f)
        assertEquals(0f, size.move(500f).bottomDp, 0.01f)
    }
}
