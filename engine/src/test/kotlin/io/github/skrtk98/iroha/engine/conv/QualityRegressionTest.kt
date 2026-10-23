package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.TestDictionary
import io.github.skrtk98.iroha.engine.dict.MozcImporter
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * mozc の品質回帰テスト (data/test/quality_regression_test) で変換と予測の精度を測る。
 * mozc 本体でも落ちる行を含むので合否は付けず、項目ごとの通過数を出力して比べる材料にする。
 */
class QualityRegressionTest {
    private data class Item(val key: String, val value: String, val command: String, val rank: Int)

    @Test
    fun report() {
        val dictionary = TestDictionary.instance
        val dir = File(System.getProperty("iroha.mozcDir") ?: "", "${MozcImporter.MOZC_COMMIT}/test/quality_regression_test")
        assumeTrue(dictionary != null && dir.exists())
        // 重みを試すときは -Diroha.lmWeight=… を付けて実行する
        val weight = System.getProperty("iroha.lmWeight")?.toFloatOrNull() ?: Converter.LANGUAGE_MODEL_WEIGHT
        println("quality: language model ${if (TestDictionary.languageModel == null) "off" else "weight $weight"}")
        val converter = Converter(dictionary!!, languageModel = TestDictionary.languageModel, languageModelWeight = weight)
        val items = listOf("oss.tsv", "regression.tsv").flatMap { name ->
            File(dir, name).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map(::parse)
        }
        val passed = HashMap<String, Int>()
        val total = HashMap<String, Int>()
        for (item in items) {
            val ok = when (item.command) {
                "Conversion Expected" -> conversionRank(converter, item.key, item.value).let { it in 0..item.rank }
                "Conversion Match" -> Regex(item.value).matches(top(converter, item.key))
                "Conversion Not Match" -> !Regex(item.value).matches(top(converter, item.key))
                "Prediction Expected" ->
                    converter.suggest(item.key).indexOfFirst { it.surface == item.value }.let { it in 0..item.rank }
                else -> continue
            }
            total.merge(item.command, 1, Int::plus)
            if (ok) passed.merge(item.command, 1, Int::plus) else println("quality miss: ${item.command} ${item.key} -> ${top(converter, item.key)} (want ${item.value})")
        }
        for ((command, count) in total.toSortedMap()) println("quality: $command ${passed[command] ?: 0} / $count")
        println("quality: all ${passed.values.sum()} / ${total.values.sum()}")
    }

    private fun parse(line: String): Item {
        val tokens = line.split('\t')
        var command = tokens[3]
        var rank = tokens.getOrNull(4)?.toIntOrNull() ?: 0
        if (command.startsWith("Conversion Expected ")) {
            rank = command.removePrefix("Conversion Expected ").trim().toInt()
            command = "Conversion Expected"
        }
        return Item(tokens[1], tokens[2], command, rank)
    }

    private fun top(converter: Converter, key: String) =
        converter.convert(key).joinToString("") { it.candidates.first().surface }

    /** mozc の GetRank と同じく、各文節の候補を順に辿って [value] になる組み合わせの順位の和を返す。無ければ -1。 */
    private fun conversionRank(converter: Converter, key: String, value: String): Int {
        val segments = converter.convert(key)
        val lists = ArrayList<List<Candidate>>()
        var start = 0
        for ((i, segment) in segments.withIndex()) {
            val end = start + segment.reading.length
            val leftRid = segments.getOrNull(i - 1)?.candidates?.first()?.rid ?: Converter.BOS_EOS
            val rightLid = segments.getOrNull(i + 1)?.candidates?.first()?.lid ?: Converter.BOS_EOS
            val first = segment.candidates.first()
            lists.add(listOf(first) + converter.candidates(key, start, end, leftRid, rightLid).filter { it.surface != first.surface })
            start = end
        }
        fun rank(rest: String, index: Int): Int {
            if (index == lists.size) return if (rest.isEmpty()) 0 else -1
            var r = 0
            for (candidate in lists[index]) {
                if (rest.startsWith(candidate.surface)) {
                    val next = rank(rest.substring(candidate.surface.length), index + 1)
                    if (next >= 0) return r + next
                }
                r++
            }
            return -1
        }
        return rank(value, 0)
    }
}
