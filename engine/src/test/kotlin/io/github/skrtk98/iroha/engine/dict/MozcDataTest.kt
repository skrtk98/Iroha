package io.github.skrtk98.iroha.engine.dict

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** mozc の本体以外のデータが実辞書に入っていることを確かめる。 */
class MozcDataTest {
    private lateinit var dictionary: SystemDictionary

    @Before
    fun setUp() {
        val dict = TestDictionary.instance
        assumeTrue(dict != null)
        dictionary = dict!!
    }

    private fun surfaces(reading: String) = dictionary.exactSearch(reading).map { it.surface }

    @Test
    fun addsAuxDictionaryWords() {
        assertTrue("朝ごはん" in surfaces("あさごはん"))
    }

    @Test
    fun addsManualWords() {
        assertTrue("CJK統合漢字" in surfaces("CJKとうごうかんじ"))
        assertTrue("厚別区" in surfaces("あつべつく"))
    }

    @Test
    fun removesFilteredWords() {
        assertFalse("お見" in surfaces("おみ"))
    }
}
