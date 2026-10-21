package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardSizeTest {
    private val perPercent = 2.5f

    @Test
    fun dragUpMakesKeyboardTaller() {
        val size = KeyboardSize.Default.drag(-25f, perPercent)
        assertEquals(110f, size.heightPercent, 0.01f)
        assertEquals(0f, size.bottomDp, 0.01f)
    }

    @Test
    fun dragStopsAtLimits() {
        assertEquals(60f, KeyboardSize.Default.drag(1000f, perPercent).heightPercent, 0.01f)
        assertEquals(150f, KeyboardSize.Default.drag(-1000f, perPercent).heightPercent, 0.01f)
    }

    @Test
    fun stepHeightMovesByFivePercent() {
        assertEquals(105f, KeyboardSize.Default.stepHeight(1).heightPercent, 0.01f)
        assertEquals(95f, KeyboardSize.Default.stepHeight(-1).heightPercent, 0.01f)
    }

    @Test
    fun stepHeightSnapsOddValuesToNearestStepInDirection() {
        assertEquals(105f, KeyboardSize(103f, 0f).stepHeight(1).heightPercent, 0.01f)
        assertEquals(100f, KeyboardSize(103f, 0f).stepHeight(-1).heightPercent, 0.01f)
    }

    @Test
    fun stepHeightStopsAtLimits() {
        assertEquals(150f, KeyboardSize(150f, 0f).stepHeight(1).heightPercent, 0.01f)
        assertEquals(60f, KeyboardSize(60f, 0f).stepHeight(-1).heightPercent, 0.01f)
    }

    @Test
    fun stepBottomLiftsBySixteenAndNeverGoesBelowZero() {
        assertEquals(16f, KeyboardSize.Default.stepBottom(1).bottomDp, 0.01f)
        assertEquals(0f, KeyboardSize.Default.stepBottom(-1).bottomDp, 0.01f)
        assertEquals(16f, KeyboardSize(100f, 20f).stepBottom(-1).bottomDp, 0.01f)
        assertEquals(240f, KeyboardSize(100f, 240f).stepBottom(1).bottomDp, 0.01f)
    }
}
