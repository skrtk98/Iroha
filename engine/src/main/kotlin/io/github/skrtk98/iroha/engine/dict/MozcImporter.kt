package io.github.skrtk98.iroha.engine.dict

import java.io.File

/** mozc の src/data を読み、DictionaryBuilder の入力を作る。パスは src/data からの相対。 */
class MozcImporter(private val dir: File) {

    private fun file(path: String) = File(dir, path)

    /** 空行と # で始まる行を除いた行。 */
    private fun dataLines(path: String): List<String> =
        file(path).readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }

    val posNames: List<String> by lazy {
        file(ID_DEF).readLines(Charsets.UTF_8)
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
    val unknownNounId: Int by lazy { posId("名詞,一般,") }

    /** UserPos の順に並べた品詞 ID。 */
    val userPosIds: List<Int> by lazy {
        UserPos.entries.map { pos ->
            posNames.indexOfFirst { it.startsWith(pos.mozcPrefix) }.takeIf { it >= 0 } ?: unknownNounId
        }
    }

    private fun posId(prefix: String): Int =
        posNames.indexOfFirst { it.startsWith(prefix) }.also { check(it >= 0) { "no POS $prefix" } }

    fun readMatrix(): Pair<IntArray, Int> {
        file("dictionary_oss/connection_single_column.txt").bufferedReader(Charsets.UTF_8).use { reader ->
            val dim = reader.readLine().trim().toInt()
            val matrix = IntArray(dim * dim)
            for (i in matrix.indices) matrix[i] = reader.readLine().trim().toInt()
            matrix[0] = 0
            return matrix to dim
        }
    }

    /**
     * 変換に使う語。mozc の dictionary00〜09 に、mozc のビルドと同じく aux_dictionary.tsv と
     * dictionary_manual の語を足し、dictionary_filter.tsv に当たる語を除く。
     * 自立語のうちコストが [maxContentCost] 以上の語は落とす。付属語・記号・足した語はすべて残す。
     */
    fun readEntries(maxContentCost: Int): List<WordEntry> {
        val classes = posClasses
        val main = readMainDictionary()
        val aux = readAuxDictionary(main)
        val manual = readManualWords(main, aux)
        val pruned = main.filter { classes[it.lid] != PosClass.Content || it.cost < maxContentCost }
        val filter = readDictionaryFilter()
        return (pruned + aux + manual)
            .filter { it.reading.length <= SystemDictionary.MAX_READING_CHARS }
            .filterNot { word -> filter.any { (key, value) -> key.matches(word.reading) && value.matches(word.surface) } }
            .distinct()
    }

    private fun readMainDictionary(): List<WordEntry> {
        val result = ArrayList<WordEntry>(1_300_000)
        for (index in 0..9) {
            file("dictionary_oss/dictionary0$index.txt").forEachLine(Charsets.UTF_8) { line ->
                parseDictionaryLine(line)?.let(result::add)
            }
        }
        return result
    }

    /** 読み、lid、rid、コスト、表記のタブ区切り。 */
    private fun parseDictionaryLine(line: String): WordEntry? {
        val fields = line.split('\t')
        if (fields.size < 5) return null
        return WordEntry(fields[0], fields[4], fields[1].toInt(), fields[2].toInt(), fields[3].toInt())
    }

    /** 既にある語を元に、品詞とコストを写して語を足す。元の語が無い行は飛ばす。 */
    private fun readAuxDictionary(main: List<WordEntry>): List<WordEntry> {
        val byKey = main.groupBy { it.reading to it.surface }
        val existing = main.mapTo(HashSet()) { listOf(it.reading, it.surface, it.lid, it.rid) }
        val result = ArrayList<WordEntry>()
        for (line in dataLines("dictionary_oss/aux_dictionary.tsv")) {
            val (key, value, baseKey, baseValue, offset) = line.split('\t')
            for (base in byKey[baseKey to baseValue].orEmpty()) {
                if (listOf(key, value, base.lid, base.rid) in existing) continue
                result.add(WordEntry(key, value, base.lid, base.rid, base.cost + offset.toInt()))
            }
        }
        return result
    }

    /**
     * dictionary_manual の語。mozc の gen_aux_dictionary.py と同じく、品詞名を ID に直し、
     * コストはその品詞の語のコストの中央値にする。domain.txt は本体の辞書と同じ形式。
     */
    private fun readManualWords(main: List<WordEntry>, aux: List<WordEntry>): List<WordEntry> {
        val existing = (main + aux).mapTo(HashSet()) { listOf(it.reading, it.surface, it.lid, it.rid) }
        val costsByPos = main.filter { it.lid == it.rid }.groupBy({ it.lid }, { it.cost })
        val medians = HashMap<Int, Int>()
        val result = ArrayList<WordEntry>()
        for (path in listOf("dictionary_manual/words.tsv", "dictionary_manual/places.tsv")) {
            for (line in dataLines(path)) {
                val (key, value, pos) = line.split('\t')
                val id = posNames.indexOf(checkNotNull(MANUAL_POS[pos]) { "unknown POS $pos in $path" })
                if (listOf(key, value, id, id) in existing) continue
                val cost = medians.getOrPut(id) {
                    val costs = costsByPos.getValue(id).sorted()
                    costs[(costs.size - 1) / 2]
                }
                result.add(WordEntry(key, value, id, id, cost))
            }
        }
        dataLines("dictionary_manual/domain.txt").mapNotNullTo(result, ::parseDictionaryLine)
        return result
    }

    /** 除く語の読みと表記の正規表現。 */
    private fun readDictionaryFilter(): List<Pair<Regex, Regex>> =
        dataLines("dictionary_oss/dictionary_filter.tsv").map { line ->
            val (key, value) = line.split('\t')
            Regex(key) to Regex(value)
        }

    companion object {
        /** 取得元の mozc リビジョン。辞書を更新するときはここを変える。 */
        const val MOZC_COMMIT = "0136c35782536cb43d710575c311cd4185982738"

        private const val ID_DEF = "dictionary_oss/id.def"

        /** dictionary_manual の品詞名。mozc の gen_aux_dictionary.py の pos_alias と同じ。 */
        private val MANUAL_POS = mapOf(
            "名詞" to "名詞,一般,*,*,*,*,*",
            "固有名詞" to "名詞,固有名詞,一般,*,*,*,*",
            "人名" to "名詞,固有名詞,人名,一般,*,*,*",
            "姓" to "名詞,固有名詞,人名,姓,*,*,*",
            "名" to "名詞,固有名詞,人名,名,*,*,*",
            "組織" to "名詞,固有名詞,組織,*,*,*,*",
            "地名" to "名詞,固有名詞,地域,一般,*,*,*",
            "名詞サ変" to "名詞,サ変接続,*,*,*,*,*",
            "名詞形動" to "名詞,形容動詞語幹,*,*,*,*,*",
            "副詞" to "副詞,一般,*,*,*,*,*",
            "連体詞" to "連体詞,*,*,*,*,*,*",
            "接続詞" to "接続詞,*,*,*,*,*,*",
            "感動詞" to "感動詞,*,*,*,*,*,*",
            "接頭語" to "接頭詞,名詞接続,*,*,*,*,*",
            "助数詞" to "名詞,接尾,助数詞,*,*,*,*",
            "接尾一般" to "名詞,接尾,一般,*,*,*,*",
            "接尾人名" to "名詞,接尾,人名,*,*,*,*",
            "接尾地名" to "名詞,接尾,地域,*,*,*,*",
        )

        /**
         * 必要なファイルと SHA-256。取得した中身が変わっていないことを確かめ、
         * 誰がビルドしても同じ辞書（ひいては同じ APK）になるようにする。
         * [MOZC_COMMIT] を変えたら、取得し直したファイルの値に書き換えること。
         */
        val FILE_SHA256: Map<String, String> = mapOf(
            ID_DEF to "07a05a268c3783b7e02b36e4aa591555a2dd19a1f3c93891576740761c154f2d",
            "dictionary_oss/connection_single_column.txt" to "68823a1f6617372ccb89afdbcb334d7a0bcfa82c9c5dda03fc1f80f1cab5c48e",
            "dictionary_oss/evaluation.tsv" to "66e80417eef5790e4e5e8f76d97f8751966167f95d80e7e98087fd9b337bffd6",
            "dictionary_oss/dictionary00.txt" to "9e07ce292932c4fff327ed8cb06274c09acca556bd8bdf7c7ef1f6173eae3b61",
            "dictionary_oss/dictionary01.txt" to "836fd5fd8399510de0d8ef7b33be913cca6bbaee7b712cc8045fba03f4c75a89",
            "dictionary_oss/dictionary02.txt" to "93943858d0cfd092b849a78def6901eaf228177e1ac5663544e865a3a1e45a9e",
            "dictionary_oss/dictionary03.txt" to "cb9f1b0b4a7550d682d86244a021cd17e0b15366328819e69135b5f183a1a24b",
            "dictionary_oss/dictionary04.txt" to "9fdc4e3948d03463de13ab5782adffbc11dd06e934cb80138c5920029cd3d2f6",
            "dictionary_oss/dictionary05.txt" to "199e3b0f050b691823ae7ba943090d437f51af6436b27c5f3dc69cafa12000e4",
            "dictionary_oss/dictionary06.txt" to "b95b14f7770ae2a824bc8e9fad8b03c1bc45c504f93a36554b8b56daa0368d2a",
            "dictionary_oss/dictionary07.txt" to "7f53649b529d4c8c495d3a8dd9c4d489dfe7696080f7423107ff01331174be48",
            "dictionary_oss/dictionary08.txt" to "50a31bb5fc1646721b00be371a5695dffab1292bda143e1e59cc70cbbdd8643f",
            "dictionary_oss/dictionary09.txt" to "4e5fcd4cbfbf11395166a37b506f547fc54d20edc0884f5184ecd3fc705f0d9a",
            "dictionary_oss/aux_dictionary.tsv" to "8103d07c4eb18892282983299cbb20284a0f74bc596dda63831787b69aa7ce6c",
            "dictionary_oss/dictionary_filter.tsv" to "802f2c76516bf1bbb30cd501cc343abf59aa179d3f98917297d961fb9921a2fb",
            "dictionary_manual/domain.txt" to "f6306edfab322a114c705405cd8e3375984a9f08e181a90ae65cc495070598a0",
            "dictionary_manual/places.tsv" to "15cb5d42beb4a530f06599bc64e3a6e6cd11da61728fd0b81e618cef929c9d0e",
            "dictionary_manual/words.tsv" to "e21b52a0bbb983906ee3a7767565087b950e5eb3f246c9a2f5431e643fd69ece",
        )

        val REQUIRED_FILES: List<String> = FILE_SHA256.keys.toList()

        fun rawUrl(path: String): String =
            "https://raw.githubusercontent.com/google/mozc/$MOZC_COMMIT/src/data/$path"
    }
}
