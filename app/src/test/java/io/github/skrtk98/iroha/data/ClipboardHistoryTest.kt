package io.github.skrtk98.iroha.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        h.togglePin(key("keep"))
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
        h.togglePin(key("line1\nline2\ttab\\slash"))
        val restored = ClipboardHistory()
        restored.deserialize(h.serialize())
        assertEquals(h.entries, restored.entries)
    }

    @Test
    fun pinningStopsAtTheLimit() {
        val h = ClipboardHistory(maxUnpinned = 10, maxPinned = 1)
        h.add("a", 1)
        h.add("b", 2)
        h.togglePin(key("a"))
        h.togglePin(key("b"))
        assertEquals(listOf("a"), h.entries.filter { it.pinned }.map { it.text })
        h.togglePin(key("a"))
        h.togglePin(key("b"))
        assertEquals(listOf("b"), h.entries.filter { it.pinned }.map { it.text })
    }

    @Test
    fun imagesAreKeptApartFromTextAndDeduplicated() {
        val h = ClipboardHistory()
        h.add("a.png", 1)
        h.add(ClipEntry("", time = 2, image = "a.png"))
        h.add(ClipEntry("", time = 3, image = "b.png"))
        h.add(ClipEntry("", time = 4, image = "a.png"))
        assertEquals(listOf("a.png", "b.png", null), h.entries.map { it.image })
        h.remove(ClipEntry("", image = "b.png").key)
        assertEquals(listOf("a.png", null), h.entries.map { it.image })
    }

    @Test
    fun roundTripsImagesAndReadsTheOldFormat() {
        val h = ClipboardHistory()
        h.add("text", 1)
        h.add(ClipEntry("", time = 2, image = "abc.png"))
        h.togglePin(ClipEntry("", image = "abc.png").key)
        val restored = ClipboardHistory()
        restored.deserialize(h.serialize())
        assertEquals(h.entries, restored.entries)

        val old = ClipboardHistory()
        old.deserialize("1\t5\tline\\nnext\n")
        assertEquals(listOf(ClipEntry("line\nnext", pinned = true, time = 5)), old.entries)
    }

    @Test
    fun lateArrivalsAreOrderedByCopyTime() {
        val h = ClipboardHistory()
        h.add("old", 1)
        h.add("newest", 5)
        h.add(ClipEntry("", time = 3, image = "late.png"))
        assertEquals(listOf("text:newest", "image:late.png", "text:old"), h.entries.map { it.key })
    }

    @Test
    fun duplicateImagesKeepTheNewestTime() {
        val h = ClipboardHistory()
        h.add(ClipEntry("", time = 5, image = "a.png"))
        h.add(ClipEntry("", time = 3, image = "a.png"))
        assertEquals(listOf(5L), h.entries.map { it.time })
    }

    @Test
    fun imagesHaveTheirOwnLimits() {
        val h = ClipboardHistory(maxImages = 2)
        h.add(ClipEntry("", time = 1, image = "1.png"))
        h.add("text", 2)
        h.add(ClipEntry("", time = 3, image = "2.png"))
        h.add(ClipEntry("", time = 4, image = "3.png"))
        assertEquals(listOf("image:3.png", "image:2.png", "text:text"), h.entries.map { it.key })

        h.togglePin("image:3.png")
        h.togglePin("image:2.png")
        h.add(ClipEntry("", time = 5, image = "4.png"))
        h.togglePin("image:4.png")
        assertEquals(listOf("3.png", "2.png"), h.entries.filter { it.pinned }.mapNotNull { it.image })
    }

    @Test
    fun unpinningReappliesTheLimits() {
        val h = ClipboardHistory(maxImages = 1)
        h.add(ClipEntry("", time = 1, image = "old.png"))
        h.togglePin("image:old.png")
        h.add(ClipEntry("", time = 2, image = "new.png"))
        h.togglePin("image:old.png")
        assertEquals(listOf("image:new.png"), h.entries.map { it.key })
    }

    @Test
    fun pinsSeveralAtOnceUpToTheLimit() {
        val h = ClipboardHistory(maxPinned = 2)
        h.add("a", 1)
        h.add("b", 2)
        h.add("c", 3)
        // 新しい順に固定し、上限を超えた分は固定しない
        assertEquals(1, h.setPinned(setOf(key("a"), key("b"), key("c")), pinned = true))
        assertEquals(listOf("c", "b"), h.entries.filter { it.pinned }.map { it.text })

        assertEquals(0, h.setPinned(setOf(key("b"), key("c")), pinned = false))
        assertTrue(h.entries.none { it.pinned })
    }

    @Test
    fun removesSeveralAtOnce() {
        val h = ClipboardHistory()
        h.add("a", 1)
        h.add("b", 2)
        h.add("c", 3)
        h.remove(setOf(key("a"), key("c")))
        assertEquals(listOf("b"), h.entries.map { it.text })
    }

    private fun key(text: String) = ClipEntry(text).key
}
