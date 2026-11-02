package io.github.skrtk98.iroha.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateClipTest {
    private fun overflow(offset: Int, size: Int) = clippedOverflow(offset, size, 0, 1000, tolerance = 30)

    @Test
    fun fullyVisibleItemIsNotClipped() {
        assertEquals(0, overflow(100, 200))
        assertEquals(0, overflow(800, 200))
    }

    @Test
    fun clippedAtTheEndScrollsForward() {
        assertEquals(100, overflow(900, 200))
    }

    @Test
    fun clippedAtTheStartScrollsBackward() {
        assertEquals(-100, overflow(-100, 200))
    }

    @Test
    fun onlyThePaddingBeingClippedDoesNotCount() {
        assertEquals(0, overflow(830, 200))
        assertEquals(0, overflow(-30, 200))
    }

    @Test
    fun itemWiderThanTheViewportCanStillBeSelected() {
        assertEquals(0, overflow(-50, 1200))
    }
}
