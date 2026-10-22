package io.github.skrtk98.iroha.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftStateTest {
    @Test
    fun tapShiftsOnlyTheNextLetter() {
        val once = ShiftState.Off.tap()
        assertEquals(ShiftState.Once, once)
        assertEquals(ShiftState.Off, once.afterInput())
    }

    @Test
    fun tapCancelsEitherState() {
        assertEquals(ShiftState.Off, ShiftState.Once.tap())
        assertEquals(ShiftState.Off, ShiftState.Locked.tap())
    }

    @Test
    fun lockSurvivesInput() {
        assertEquals(ShiftState.Locked, ShiftState.Locked.afterInput())
        assertEquals(ShiftState.Off, ShiftState.Off.afterInput())
    }
}
