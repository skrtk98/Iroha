package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * 手元で作った評価データ (scripts/eval/prepare_eval.sh) で変換精度を測る。-Diroha.evalDir=… を渡したときだけ動く。
 * 言語モデルの重みなどを決めるのに使っていない、くだけた雑談の文 (chat) と Wikipedia の文 (ajimee) で、
 * 品質回帰テストと MS IME に合わせすぎていないかを確かめる。
 * 各ファイルは 1 行 1 件の TSV で、1 列目が読み、2 列目以降が正解 (どれと一致しても正解)。
 * 名前が _context.tsv で終わるファイルは、読みの前の 1 列目が左の文脈 (確定済みの文)。文脈ありと文脈なしの両方を測る。
 */
class EvaluationSetTest {
    @Test
    fun conversionAccuracy() {
        val dictionary = TestDictionary.instance
        val dir = System.getProperty("iroha.evalDir")?.let(::File)
        assumeTrue(dir != null)
        // 測るよう指定されたのに測れないときは、黙って飛ばさずに失敗させる
        check(dictionary != null) { "dictionary is not built" }
        check(dir!!.isDirectory) { "iroha.evalDir is not a directory: $dir" }
        val weight = System.getProperty("iroha.lmWeight")?.toFloatOrNull() ?: Converter.LANGUAGE_MODEL_WEIGHT
        val converter = Converter(dictionary, languageModel = TestDictionary.languageModel, languageModelWeight = weight)
        println("eval: language model ${if (TestDictionary.languageModel == null) "off" else "weight $weight"}")
        val files = dir.listFiles { f -> f.name.endsWith(".tsv") }!!.sortedBy { it.name }
        check(files.isNotEmpty()) { "no .tsv in $dir" }
        for (file in files) {
            // どの版の評価データで測った数字かを結果に残す (prepare_eval.sh が確かめる SHA-256 と同じ)
            val sha = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            val name = file.nameWithoutExtension
            val sha8 = sha.take(8)
            val rows = items(file)
            if (file.name.endsWith("_context.tsv")) {
                check(rows.all { it.size >= 3 }) { "bad line in $file" }
                report(name, sha8, converter, rows.map { it.drop(1) }, rows.map { it[0] })
                report("${name}_nocontext", sha8, converter, rows.map { it.drop(1) }, rows.map { "" })
            } else {
                report(name, sha8, converter, rows, rows.map { "" })
            }
        }
    }

    private fun report(name: String, sha: String, converter: Converter, items: List<List<String>>, contexts: List<String>) {
        val results = items.indices.toList().parallelStream().map { i ->
            val row = items[i]
            val output = converter.convert(row[0], leftContext = contexts[i]).joinToString("") { it.candidates.first().surface }
            // 正解が複数あるときは、正解の長さあたりの差がいちばん小さいものと比べる (AJIMEE-Bench の MinCER と同じ)。
            // 並びに左右されないよう、同じ値なら差の小さいもの、次に文字列の順で選ぶ
            val best = row.drop(1).map { it to editDistance(it, output) }
                .minWith(compareBy<Pair<String, Int>> { it.second.toDouble() / it.first.length }.thenBy { it.second }.thenBy { it.first })
            Triple(best.first, output, best.second)
        }.toList()
        val exact = results.count { it.third == 0 }
        val errors = results.sumOf { it.third }
        val chars = results.sumOf { it.first.length }
        println("eval $name ($sha): exact $exact / ${results.size}, CER ${"%.2f".format(100.0 * errors / chars)}%")
        if (System.getProperty("iroha.evalMisses") == "true") {
            for ((reference, output, distance) in results) if (distance > 0) println("eval miss $name: $output (want $reference)")
        }
    }

    private fun items(file: File) = file.readLines(Charsets.UTF_8).map { line ->
        line.split('\t').also { row ->
            // 列がずれると別の文どうしを比べてしまうので、読みと正解がそろっていなければ止める
            check(row.size >= 2 && row.all { it.isNotEmpty() }) { "bad line in $file: $line" }
        }
    }.also { check(it.isNotEmpty()) { "empty: $file" } }

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
}
