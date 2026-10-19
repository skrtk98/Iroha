package io.github.skrtk98.iroha.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardHistoryTest {
    @Test
    fun newestFirstWithoutDuplicates() {
        val h = ClipboardHistory()
        h.add("a", 1)
        h.add("b", 2)
        h.add("a", 3)
        assertEquals(listOf("a", "b"), h.entries.map { it.text })
    }

    @Test
    fun pinnedEntriesSurviveTheLimit() {
        val h = ClipboardHistory(maxUnpinned = 2)
        h.add("keep", 0)
        h.togglePin("keep")
        h.add("1", 1)
        h.add("2", 2)
        h.add("3", 3)
        assertEquals(listOf("3", "2", "keep"), h.entries.map { it.text })
        h.clearUnpinned()
        assertEquals(listOf("keep"), h.entries.map { it.text })
    }

    @Test
    fun ignoresBlankText() {
        val h = ClipboardHistory()
        h.add("  ", 1)
        assertEquals(emptyList<ClipEntry>(), h.entries)
    }

    @Test
    fun roundTripsSpecialCharacters() {
        val h = ClipboardHistory()
        h.add("line1\nline2\ttab\\slash", 5)
        h.togglePin("line1\nline2\ttab\\slash")
        val restored = ClipboardHistory()
        restored.deserialize(h.serialize())
        assertEquals(h.entries, restored.entries)
    }
}
