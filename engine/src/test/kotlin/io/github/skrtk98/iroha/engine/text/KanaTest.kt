package io.github.skrtk98.iroha.engine.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanaTest {
    @Test
    fun convertsBetweenScripts() {
        assertEquals("カタカナー", Kana.toKatakana("かたかなー"))
        assertEquals("ひらがな", Kana.toHiragana("ヒラガナ"))
        assertEquals("ｶﾞｯﾂ", Kana.toHalfWidthKatakana("がっつ"))
        assertEquals("ＡＢＣ１", Kana.toFullWidth("ABC1"))
        assertEquals("ABC1", Kana.toHalfWidth("ＡＢＣ１"))
    }

    @Test
    fun cyclesModifiers() {
        assertEquals('が', KanaModifier.cycle('か'))
        assertEquals('か', KanaModifier.cycle('が'))
        assertEquals('ば', KanaModifier.cycle('は'))
        assertEquals('ぱ', KanaModifier.cycle('ば'))
        assertEquals('っ', KanaModifier.cycle('つ'))
        assertNull(KanaModifier.cycle('ん'))
    }
}
