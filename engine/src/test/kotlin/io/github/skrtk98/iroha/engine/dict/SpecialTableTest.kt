package io.github.skrtk98.iroha.engine.dict

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SpecialTableTest {
    @Test
    fun keepsFileOrderAndSeparatesSymbolsFromWords() {
        val entries = SpecialTable.parse(listOf("# 注釈", "", "かっこ\t（）", "かっこ\t「」", "ぱいそん\tPython", "かっこ\t（）"), nounId = 1, symbolId = 2)
        assertEquals(listOf("（）", "「」", "Python"), entries.map { it.surface })
        assertEquals(ExtraKind.Special.key("かっこ"), entries[0].reading)
        // 同じ読みの中ではファイルの順に並ぶ
        assertEquals(true, entries[0].cost < entries[1].cost)
        assertEquals(listOf(2, 2, 1), entries.map { it.lid })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLinesWithoutASurface() {
        SpecialTable.parse(listOf("かっこ"), nounId = 1, symbolId = 2)
    }

    @Test
    fun theShippedTableParses() {
        // engine の作業ディレクトリから見た、同梱する表
        val lines = File("data/special.tsv").readLines()
        val entries = SpecialTable.parse(lines, nounId = 1, symbolId = 2)
        // 読みはひらがなと長音符だけ（入力の読みと一致させるため）
        val bad = entries.map { it.reading.drop(1) }.filterNot { r -> r.all { it in 'ぁ'..'ゖ' || it == 'ー' || it == 'ゔ' } }
        assertEquals(emptyList<String>(), bad)
    }
}
