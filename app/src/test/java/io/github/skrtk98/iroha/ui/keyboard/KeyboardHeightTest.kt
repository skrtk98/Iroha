package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardHeightTest {
    private val perPercent = 2.5f

    @Test
    fun dragUpMakesKeyboardTaller() {
        assertEquals(110f, KeyboardHeight.Default.drag(-25f, perPercent).percent, 0.01f)
    }

    @Test
    fun dragStopsAtLimits() {
        assertEquals(60f, KeyboardHeight.Default.drag(1000f, perPercent).percent, 0.01f)
        assertEquals(150f, KeyboardHeight.Default.drag(-1000f, perPercent).percent, 0.01f)
    }

    @Test
    fun stepMovesByFivePercent() {
        assertEquals(105f, KeyboardHeight.Default.step(1).percent, 0.01f)
        assertEquals(95f, KeyboardHeight.Default.step(-1).percent, 0.01f)
    }

    @Test
    fun stepSnapsOddValuesToNearestStepInDirection() {
        assertEquals(105f, KeyboardHeight(103f).step(1).percent, 0.01f)
        assertEquals(100f, KeyboardHeight(103f).step(-1).percent, 0.01f)
    }

    @Test
    fun stepStopsAtLimits() {
        assertEquals(150f, KeyboardHeight(150f).step(1).percent, 0.01f)
        assertEquals(60f, KeyboardHeight(60f).step(-1).percent, 0.01f)
    }
}
