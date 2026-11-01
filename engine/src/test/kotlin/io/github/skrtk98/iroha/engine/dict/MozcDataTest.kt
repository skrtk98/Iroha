package io.github.skrtk98.iroha.engine.dict

import io.github.skrtk98.iroha.engine.TestDictionary
import io.github.skrtk98.iroha.engine.tools.hasHentaigana
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
    fun dropsHentaigana() {
        // 記号の表の読み。変体仮名はこの読みで引けたので、残っていればここに出る
        assertTrue(surfaces(ExtraKind.Symbol.key("へんたいがな")).isEmpty())
        // 読みが「に」の記号にも残らない
        assertFalse(surfaces(ExtraKind.Symbol.key("に")).any { it.codePointAt(0) in 0x1B000..0x1B12F })
    }

    @Test
    fun detectsHentaiganaAnywhereInSurface() {
        val first = String(Character.toChars(0x1B000))
        val last = String(Character.toChars(0x1B12F))
        assertTrue(hasHentaigana(first + "あ"))
        assertTrue(hasHentaigana("あ" + last + "あ"))
        assertFalse(hasHentaigana(String(Character.toChars(0x1AFFF))))
        assertFalse(hasHentaigana(String(Character.toChars(0x1B130))))
        assertFalse(hasHentaigana("にゝ二\uD83D\uDE00"))
    }

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
