package io.github.skrtk98.iroha.engine.dict

import java.io.File

/** mozc の dictionary_oss ディレクトリを読み、DictionaryBuilder の入力を作る。 */
class MozcImporter(private val dir: File) {

    val posNames: List<String> by lazy {
        File(dir, "id.def").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .map { line ->
                val (id, name) = line.split(' ', limit = 2)
                id.toInt() to name
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    val posClasses: List<PosClass> by lazy { posNames.map(PosClass::fromMozcPosName) }

    /** 未知語用の「名詞,一般」の ID。 */
    val unknownNounId: Int by lazy {
        posNames.indexOfFirst { it.startsWith("名詞,一般,") }.also { check(it >= 0) }
    }

    /** UserPos の順に並べた品詞 ID。 */
    val userPosIds: List<Int> by lazy {
        UserPos.entries.map { pos ->
            posNames.indexOfFirst { it.startsWith(pos.mozcPrefix) }.takeIf { it >= 0 } ?: unknownNounId
        }
    }

    fun readMatrix(): Pair<IntArray, Int> {
        File(dir, "connection_single_column.txt").bufferedReader(Charsets.UTF_8).use { reader ->
            val dim = reader.readLine().trim().toInt()
            val matrix = IntArray(dim * dim)
            for (i in matrix.indices) matrix[i] = reader.readLine().trim().toInt()
            matrix[0] = 0
            return matrix to dim
        }
    }

    /**
     * 自立語のうちコストが [maxContentCost] 以上の語は落とす。付属語・記号はすべて残す。
     */
    fun readEntries(maxContentCost: Int): List<WordEntry> {
        val classes = posClasses
        val result = ArrayList<WordEntry>(1_000_000)
        val files = dir.listFiles { f -> f.name.matches(Regex("dictionary\\d\\d\\.txt")) }
            ?.sortedBy { it.name }
            .orEmpty()
        check(files.isNotEmpty()) { "no mozc dictionary files in $dir" }
        for (file in files) {
            file.forEachLine(Charsets.UTF_8) { line ->
                val fields = line.split('\t')
                if (fields.size < 5) return@forEachLine
                val reading = fields[0]
                val lid = fields[1].toInt()
                val rid = fields[2].toInt()
                val cost = fields[3].toInt()
                val surface = fields[4]
                if (reading.length > SystemDictionary.MAX_READING_CHARS) return@forEachLine
                if (classes[lid] == PosClass.Content && cost >= maxContentCost) return@forEachLine
                result.add(WordEntry(reading, surface, lid, rid, cost))
            }
        }
        return result.distinct()
    }

    companion object {
        /** 取得元の mozc リビジョン。辞書を更新するときはここを変える。 */
        const val MOZC_COMMIT = "0136c35782536cb43d710575c311cd4185982738"

        val REQUIRED_FILES: List<String> =
            listOf("id.def", "connection_single_column.txt", "evaluation.tsv") +
                (0..9).map { "dictionary0$it.txt" }

        fun rawUrl(fileName: String): String =
            "https://raw.githubusercontent.com/google/mozc/$MOZC_COMMIT/src/data/dictionary_oss/$fileName"
    }
}
