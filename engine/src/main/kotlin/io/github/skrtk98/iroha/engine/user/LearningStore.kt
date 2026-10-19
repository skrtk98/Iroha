package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.conv.WordSource
import io.github.skrtk98.iroha.engine.dict.WordEntry

/**
 * 選んだ候補を覚えて次回から優先する。新しいものほど前に置き、[capacity] を超えた古いものは捨てる。
 * 書き込みは差し替え方式なので、読み出しはロック不要。
 */
class LearningStore(private val capacity: Int = 2000) : WordSource {

    private class Snapshot(val entries: List<WordEntry>, val byReading: Map<String, List<WordEntry>>, val maxLength: Int)

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyMap(), 0)

    val entries: List<WordEntry> get() = snapshot.entries

    @Synchronized
    fun learn(reading: String, surface: String, lid: Int, rid: Int) {
        if (reading.isEmpty() || surface.isEmpty()) return
        val entry = WordEntry(reading, surface, lid, rid, LEARNED_COST)
        val updated = ArrayList<WordEntry>(snapshot.entries.size + 1)
        updated.add(entry)
        snapshot.entries.filterTo(updated) { !(it.reading == reading && it.surface == surface) }
        replace(updated.take(capacity))
    }

    @Synchronized
    fun replaceAll(entries: List<WordEntry>) = replace(entries.take(capacity))

    @Synchronized
    fun clear() = replace(emptyList())

    private fun replace(entries: List<WordEntry>) {
        // 新しい学習ほどコストを小さくして先に出す
        val ranked = entries.mapIndexed { i, e -> e.copy(cost = LEARNED_COST + minOf(i, 500)) }
        snapshot = Snapshot(
            entries,
            ranked.groupBy { it.reading },
            entries.maxOfOrNull { it.reading.length } ?: 0,
        )
    }

    override fun exact(reading: String): List<WordEntry> = snapshot.byReading[reading].orEmpty()

    override fun predict(prefix: String, limit: Int): List<WordEntry> =
        snapshot.byReading.values.asSequence()
            .flatten()
            .filter { it.reading.length > prefix.length && it.reading.startsWith(prefix) }
            .sortedBy { it.cost }
            .take(limit)
            .toList()

    override val maxReadingLength: Int get() = snapshot.maxLength

    /** 保存形式: よみ<TAB>表記<TAB>lid<TAB>rid（新しい順） */
    fun serialize(): String = buildString {
        for (e in snapshot.entries) append(e.reading).append('\t').append(e.surface).append('\t')
            .append(e.lid).append('\t').append(e.rid).append('\n')
    }

    fun deserialize(text: String) {
        val entries = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) return@mapNotNull null
            val lid = f[2].toIntOrNull() ?: return@mapNotNull null
            val rid = f[3].toIntOrNull() ?: return@mapNotNull null
            WordEntry(f[0], f[1], lid, rid, LEARNED_COST)
        }.toList()
        replaceAll(entries)
    }

    companion object {
        private const val LEARNED_COST = 1500
    }
}
