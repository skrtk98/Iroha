package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.dict.UserPos
import org.junit.Assert.assertEquals
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
}
