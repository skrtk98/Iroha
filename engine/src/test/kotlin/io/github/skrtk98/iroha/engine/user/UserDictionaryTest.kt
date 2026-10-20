package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.dict.UserPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserDictionaryTest {
    @Test
    fun parsesMozcCompatibleTsv() {
        val text = "﻿# comment\nいろは\tIroha\t固有名詞\tアプリ名\nかお\t(^_^)\t顔文字\n\nbroken\n"
        val result = UserDictionaryTsv.parse(text)
        assertEquals(1, result.skippedLines)
        assertEquals(
            listOf(
                UserWord("いろは", "Iroha", UserPos.ProperNoun, "アプリ名"),
                UserWord("かお", "(^_^)", UserPos.Emoticon, ""),
            ),
            result.words,
        )
        assertEquals(result.words, UserDictionaryTsv.parse(UserDictionaryTsv.format(result.words)).words)
    }

    @Test
    fun indexesWordsByReading() {
        val dict = UserDictionary { 7 }
        dict.add(UserWord("いろは", "Iroha"))
        dict.add(UserWord("いろは", "Iroha"))
        assertEquals(1, dict.words.size)
        assertEquals("Iroha", dict.exact("いろは").single().surface)
        assertEquals("Iroha", dict.predict("いろ", 5).single().surface)
        assertEquals(3, dict.maxReadingLength)
    }

    @Test
    fun learningKeepsNewestFirstAndRoundTrips() {
        val store = LearningStore(capacity = 2)
        store.learn("a", "A", 1, 1)
        store.learn("b", "B", 1, 2)
        store.learn("a", "A", 1, 1)
        store.learn("c", "C", 1, 1)
        assertEquals(listOf("C", "A"), store.entries.map { it.surface })
        val restored = LearningStore()
        restored.deserialize(store.serialize())
        assertEquals(store.entries, restored.entries)
    }

    @Test
    fun parseStopsAtWordLimit() {
        val lines = generateSequence(0) { it + 1 }.map { "よみ$it\t語$it" }
        // 無限の行でも上限の次の語で止まる
        val e = assertThrows(TooManyWordsException::class.java) { UserDictionaryTsv.parse(lines, maxWords = 3) }
        assertEquals(3, e.limit)
        assertEquals(3, UserDictionaryTsv.parse("a\tA\nb\tB\nc\tC\n", maxWords = 3).words.size)
    }

    @Test
    fun parseSkipsOverlongFieldsAndTruncatesComments() {
        val long = "あ".repeat(UserDictionaryTsv.MAX_FIELD_LENGTH + 1)
        val text = "$long\t語\nよみ\t$long\nよみ\t語\t名詞\t${"c".repeat(1000)}\n"
        val result = UserDictionaryTsv.parse(text)
        assertEquals(2, result.skippedLines)
        assertEquals(UserDictionaryTsv.MAX_COMMENT_LENGTH, result.words.single().comment.length)
    }

    @Test
    fun mergeOverLimitLeavesDictionaryUnchanged() {
        val dictionary = UserDictionary { 1 }
        dictionary.replaceAll(listOf(UserWord("a", "A")))
        val tooMany = (0..UserDictionary.MAX_WORDS).map { UserWord("r$it", "s$it") }
        assertThrows(TooManyWordsException::class.java) { dictionary.merge(tooMany) }
        assertEquals(listOf(UserWord("a", "A")), dictionary.words)
    }
}
