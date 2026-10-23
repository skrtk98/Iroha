package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NumberVariantsTest {
    @Test
    fun readsMixedNumbers() {
        assertEquals(3000L, NumberVariants.parse("3千"))
        assertEquals(3000L, NumberVariants.parse("三千"))
        assertEquals(2024L, NumberVariants.parse("二〇二四"))
        assertEquals(12000L, NumberVariants.parse("1万2千"))
        assertEquals(15L, NumberVariants.parse("十五"))
        assertEquals(100_000L, NumberVariants.parse("十万"))
        assertEquals(42L, NumberVariants.parse("４２"))
    }

    @Test
    fun rejectsBrokenUnitOrder() {
        assertNull(NumberVariants.parse("千千"))
        assertNull(NumberVariants.parse("万億"))
        assertNull(NumberVariants.parse("99999999999999999999"))
        assertNull(NumberVariants.parse("9999999999999999千"))
    }

    @Test
    fun writesKanjiNumbers() {
        assertEquals("三千", NumberVariants.toKanji(3000))
        assertEquals("十五", NumberVariants.toKanji(15))
        assertEquals("一万二千", NumberVariants.toKanji(12000))
        assertEquals("〇", NumberVariants.toKanji(0))
    }

    @Test
    fun rewritesOnlyTheLeadingNumber() {
        assertEquals(listOf("三千円", "3000円", "3,000円", "３０００円"), NumberVariants.of("3千円"))
        assertEquals(listOf("100000円", "10万円", "100,000円", "１０００００円"), NumberVariants.of("十万円"))
        assertTrue(NumberVariants.of("円").isEmpty())
    }

    @Test
    fun numberCandidatesGetOtherForms() {
        val dictionary = TestDictionary.instance
        assumeTrue(dictionary != null)
        val list = Converter(dictionary!!).candidates("さんぜんえん", 0, 6).map { it.surface }
        assertTrue(list.toString(), list.indexOf("三千円") in 0..2)
        assertTrue(list.toString(), "3000円" in list)
    }
}
