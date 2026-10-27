package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Microsoft Research IME Corpus (新聞記事の 6,000 文の読みと正解) で変換精度を測る。
 * コーパスはライセンスで再配布が禁じられているのでリポジトリに置かず、手元で展開した場所を
 * -Diroha.msImeDir=… で渡したときだけ動く。mozc のデータとも学習データとも無関係な評価に使う。
 */
class MsImeEvaluationTest {
    @Test
    fun conversionAccuracy() {
        val dictionary = TestDictionary.instance
        val dir = System.getProperty("iroha.msImeDir")?.let(::File)
        assumeTrue(dictionary != null && dir != null && dir.isDirectory)
        // 重みを試すときは -Diroha.lmWeight=… を付けて実行する (品質回帰テストと同じ)
        val weight = System.getProperty("iroha.lmWeight")?.toFloatOrNull() ?: Converter.LANGUAGE_MODEL_WEIGHT
        val converter = Converter(dictionary!!, languageModel = TestDictionary.languageModel, languageModelWeight = weight)
        val pairs = listOf("news1", "news2").flatMap { name ->
            val readings = lines(File(dir, "$name.read.3000.txt"))
            val references = lines(File(dir, "$name.ref.3000.txt"))
            // 行がずれると別の文どうしを比べてしまうので、件数が合わなければ止める
            check(readings.size == SENTENCES && references.size == SENTENCES) { "$name: ${readings.size} / ${references.size} lines" }
            readings.zip(references)
        }
        println("msime: language model ${if (TestDictionary.languageModel == null) "off" else "weight $weight"}")
        // 句読点で切った句。読みと正解で句の数が合う文だけを使う。IME では句ごとに変換することが多い
        val clauses = pairs.flatMap { (reading, reference) ->
            val readings = reading.split(PUNCTUATION).filter { it.isNotEmpty() }
            val references = reference.split(PUNCTUATION).filter { it.isNotEmpty() }
            if (readings.size == references.size) readings.zip(references) else emptyList()
        }
        report("sentences", converter, pairs)
        report("clauses", converter, clauses)
    }

    private fun report(label: String, converter: Converter, items: List<Pair<String, String>>) {
        val results = items.parallelStream().map { (reading, reference) ->
            val output = converter.convert(reading).joinToString("") { it.candidates.first().surface }
            Triple(reference, output, editDistance(reference, output))
        }.toList()
        val exact = results.count { it.third == 0 }
        val errors = results.sumOf { it.third }
        val chars = results.sumOf { it.first.length }
        println("msime $label: exact $exact / ${results.size}, CER ${"%.2f".format(100.0 * errors / chars)}%")
        // 外れの一覧はコーパスの本文を含むので、-Diroha.msImeMisses=true のときだけ出す
        if (label == "clauses" && System.getProperty("iroha.msImeMisses") == "true") {
            for ((reference, output, distance) in results) if (distance > 0) println("msime miss: $output (want $reference)")
        }
    }

    private fun lines(file: File) = file.readLines(Charsets.UTF_8)
        .map { it.removePrefix("﻿").trimEnd('\r') }
        .filter { !it.startsWith("//") }
        .onEach { check(it.isNotEmpty()) { "empty line in $file" } }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private companion object {
        val PUNCTUATION = Regex("[、。]")
        const val SENTENCES = 3000
    }
}
