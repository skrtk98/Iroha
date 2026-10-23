package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import io.github.skrtk98.iroha.engine.dict.MozcImporter
import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** mozc 付属の評価セット (mozc 本体が通る行) で変換精度を測る。 */
class MozcEvaluationTest {
    @Test
    fun conversionAccuracy() {
        val dictPath = System.getProperty("iroha.dictionary")
        val evalFile = File(System.getProperty("iroha.mozcDir") ?: "", "${MozcImporter.MOZC_COMMIT}/dictionary_oss/evaluation.tsv")
        assumeTrue(dictPath != null && File(dictPath).exists() && evalFile.exists())
        val converter = RandomAccessFile(dictPath, "r").use { file ->
            Converter(
                SystemDictionary(file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())),
                languageModel = TestDictionary.languageModel,
            )
        }
        var total = 0
        var passed = 0
        evalFile.forEachLine { line ->
            val f = line.split('\t')
            if (f.size < 5 || f[0] != "OK:") return@forEachLine
            val match = when (f[3]) {
                "Conversion Match" -> true
                "Conversion Not Match" -> false
                else -> return@forEachLine
            }
            val output = converter.convert(f[1]).joinToString("") { it.candidates.first().surface }
            total++
            if (Regex(f[4]).matches(output) == match) passed++ else println("mozc evaluation miss: ${f[1]} -> $output (${f[3]} ${f[4]})")
        }
        val rate = passed * 100.0 / total
        println("mozc evaluation: $passed / $total (${"%.1f".format(rate)}%)")
        assertTrue(rate > 60.0)
    }
}
