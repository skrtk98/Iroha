package io.github.skrtk98.iroha.engine.session

import io.github.skrtk98.iroha.engine.TestDictionary
import io.github.skrtk98.iroha.engine.conv.Converter
import io.github.skrtk98.iroha.engine.user.LearningStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class ComposingSessionTest {
    private lateinit var learning: LearningStore
    private lateinit var session: ComposingSession

    @Before
    fun setUp() {
        val dict = TestDictionary.instance
        assumeTrue(dict != null)
        learning = LearningStore()
        session = ComposingSession(Converter(dict!!, listOf(learning)), learning)
    }

    @Test
    fun romajiInputBuildsKana() {
        "nihongo".forEach { session.insert(it.toString(), romaji = true) }
        assertEquals("にほんご", session.preedit.text)
        assertEquals("にほんご", session.commit())
        assertFalse(session.isComposing)
    }

    @Test
    fun flickInputWithModifier() {
        session.insert("か", romaji = false)
        assertTrue(session.toggleModifier())
        assertEquals("が", session.preedit.text)
    }

    @Test
    fun convertsAndCommits() {
        session.insert("わたしのなまえ", romaji = false)
        session.convertOrNext()
        assertTrue(session.isConverting)
        assertEquals("私の名前", session.preedit.text)
        assertEquals(0, session.preedit.highlightStart)
        assertEquals("私の".length, session.preedit.highlightEnd)
        assertEquals("私の名前", session.commit())
    }

    @Test
    fun resizingSegmentReconvertsRest() {
        session.insert("きょうはいしゃにいく", romaji = false)
        session.convertOrNext()
        val before = session.preedit.text
        session.resizeSegment(-1)
        assertTrue(session.preedit.text != before || session.preedit.highlightEnd > 0)
        assertEquals("きょうはいしゃにいく".length, session.commitReadingLength())
    }

    @Test
    fun selectingCandidateLearnsIt() {
        session.insert("かいて", romaji = false)
        session.convertOrNext()
        val candidates = session.focusedCandidates
        val target = candidates.indexOfFirst { it.surface == "描いて" }
        assumeTrue(target > 0)
        assertEquals("描いて", session.selectCandidate(target))
        session.insert("かいて", romaji = false)
        session.convertOrNext()
        assertEquals("描いて", session.preedit.text)
    }

    @Test
    fun backspaceCancelsConversionThenDeletes() {
        session.insert("あい", romaji = false)
        session.convertOrNext()
        assertTrue(session.backspace())
        assertEquals("あい", session.preedit.text)
        assertTrue(session.backspace())
        assertEquals("あ", session.preedit.text)
        assertTrue(session.backspace())
        assertFalse(session.backspace())
    }

    private fun ComposingSession.commitReadingLength(): Int {
        cancel()
        return preedit.text.length
    }

    private fun convertAndPick(reading: String, surface: String): String {
        session.insert(reading, romaji = false)
        session.convertOrNext()
        val index = session.focusedCandidates.indexOfFirst { it.surface == surface }
        assumeTrue(index > 0)
        return session.selectCandidate(index)
    }

    private fun convertTop(reading: String): String {
        session.insert(reading, romaji = false)
        session.convertOrNext()
        return session.preedit.text.also { session.commit() }
    }

    @Test
    fun learnsChoicesPerPrecedingSegment() {
        // 「こうさく」の後では機械、「よい」の後では機会を選ぶ
        convertTop("こうさく")
        convertAndPick("きかい", "機械")
        convertTop("よい")
        convertAndPick("きかい", "機会")
        // 語ごとの学習は最後の機会だが、こうさくの後では機械を覚えている
        assertEquals("機会", convertTop("きかい").also { session.forgetContext() })
        convertTop("こうさく")
        assertEquals("機械", convertTop("きかい"))
        convertTop("よい")
        assertEquals("機会", convertTop("きかい"))
    }

    @Test
    fun contextLearningSurvivesSaving() {
        convertTop("こうさく")
        convertAndPick("きかい", "機械")
        convertTop("よい")
        convertAndPick("きかい", "機会")
        val restored = LearningStore().apply { deserialize(learning.serialize()) }
        assertEquals("機械", restored.inContext("工作", "きかい")?.surface)
        assertEquals(listOf("機会", "機械"), restored.entries.filter { it.reading == "きかい" }.map { it.surface })
    }
}
