package io.github.skrtk98.iroha.ime

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeArrowTest {
    private fun blocked(keyCode: Int, before: String?, after: String?, select: Boolean = false, hasSelection: Boolean = false) =
        isBlockedAtEdge(keyCode, select, hasSelection, { before }, { after })

    @Test
    fun blocksMovingPastTheEnd() {
        assertTrue(blocked(KeyEvent.KEYCODE_DPAD_DOWN, before = "a", after = ""))
        assertTrue(blocked(KeyEvent.KEYCODE_DPAD_RIGHT, before = "a", after = ""))
        assertFalse(blocked(KeyEvent.KEYCODE_DPAD_UP, before = "a", after = ""))
    }

    @Test
    fun blocksMovingBeforeTheStart() {
        assertTrue(blocked(KeyEvent.KEYCODE_DPAD_UP, before = "", after = "a"))
        assertTrue(blocked(KeyEvent.KEYCODE_DPAD_LEFT, before = "", after = "a"))
        assertFalse(blocked(KeyEvent.KEYCODE_DPAD_DOWN, before = "", after = "a"))
    }

    @Test
    fun sendsWhenTheFieldDoesNotAnswer() {
        assertFalse(blocked(KeyEvent.KEYCODE_DPAD_DOWN, before = null, after = null))
    }

    @Test
    fun collapsingASelectionIsAllowedAtTheEdge() {
        assertFalse(blocked(KeyEvent.KEYCODE_DPAD_LEFT, before = "", after = "a", hasSelection = true))
        // 選択を伸ばしているときは、端より先へは伸ばせない
        assertTrue(blocked(KeyEvent.KEYCODE_DPAD_LEFT, before = "", after = "a", select = true, hasSelection = true))
    }

    @Test
    fun otherKeysAreNeverBlocked() {
        assertFalse(blocked(KeyEvent.KEYCODE_MOVE_HOME, before = "", after = ""))
    }
}
