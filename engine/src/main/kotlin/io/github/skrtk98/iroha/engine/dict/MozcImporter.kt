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

        /**
         * 必要なファイルと SHA-256。取得した中身が変わっていないことを確かめ、
         * 誰がビルドしても同じ辞書（ひいては同じ APK）になるようにする。
         * [MOZC_COMMIT] を変えたら、取得し直したファイルの値に書き換えること。
         */
        val FILE_SHA256: Map<String, String> = mapOf(
            "id.def" to "07a05a268c3783b7e02b36e4aa591555a2dd19a1f3c93891576740761c154f2d",
            "connection_single_column.txt" to "68823a1f6617372ccb89afdbcb334d7a0bcfa82c9c5dda03fc1f80f1cab5c48e",
            "evaluation.tsv" to "66e80417eef5790e4e5e8f76d97f8751966167f95d80e7e98087fd9b337bffd6",
            "dictionary00.txt" to "9e07ce292932c4fff327ed8cb06274c09acca556bd8bdf7c7ef1f6173eae3b61",
            "dictionary01.txt" to "836fd5fd8399510de0d8ef7b33be913cca6bbaee7b712cc8045fba03f4c75a89",
            "dictionary02.txt" to "93943858d0cfd092b849a78def6901eaf228177e1ac5663544e865a3a1e45a9e",
            "dictionary03.txt" to "cb9f1b0b4a7550d682d86244a021cd17e0b15366328819e69135b5f183a1a24b",
            "dictionary04.txt" to "9fdc4e3948d03463de13ab5782adffbc11dd06e934cb80138c5920029cd3d2f6",
            "dictionary05.txt" to "199e3b0f050b691823ae7ba943090d437f51af6436b27c5f3dc69cafa12000e4",
            "dictionary06.txt" to "b95b14f7770ae2a824bc8e9fad8b03c1bc45c504f93a36554b8b56daa0368d2a",
            "dictionary07.txt" to "7f53649b529d4c8c495d3a8dd9c4d489dfe7696080f7423107ff01331174be48",
            "dictionary08.txt" to "50a31bb5fc1646721b00be371a5695dffab1292bda143e1e59cc70cbbdd8643f",
            "dictionary09.txt" to "4e5fcd4cbfbf11395166a37b506f547fc54d20edc0884f5184ecd3fc705f0d9a",
        )

        val REQUIRED_FILES: List<String> = FILE_SHA256.keys.toList()

        fun rawUrl(fileName: String): String =
            "https://raw.githubusercontent.com/google/mozc/$MOZC_COMMIT/src/data/dictionary_oss/$fileName"
    }
}
