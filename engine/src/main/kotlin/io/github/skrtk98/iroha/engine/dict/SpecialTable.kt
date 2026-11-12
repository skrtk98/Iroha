package io.github.skrtk98.iroha.engine.dict

/** engine/data/special.tsv を読む。読みと表記のタブ区切りで、ファイルの順を同じ読みの中の並び順にする。 */
object SpecialTable {
    fun parse(lines: List<String>, nounId: Int, symbolId: Int): List<WordEntry> =
        lines.withIndex()
            .filter { (_, line) -> line.isNotBlank() && !line.startsWith("#") }
            .map { (index, line) ->
                val fields = line.split('\t')
                require(fields.size == 2 && fields.all { it.isNotEmpty() }) { "line ${index + 1}: expected reading<TAB>surface" }
                val (reading, surface) = fields
                require(reading.length < SystemDictionary.MAX_READING_CHARS) { "line ${index + 1}: reading too long" }
                // 文字や数字を含む表記は語として、それ以外は記号として扱う
                val id = if (surface.any(Char::isLetterOrDigit)) nounId else symbolId
                WordEntry(ExtraKind.Special.key(reading), surface, id, id, index)
            }
            .distinctBy { it.reading to it.surface }
}
