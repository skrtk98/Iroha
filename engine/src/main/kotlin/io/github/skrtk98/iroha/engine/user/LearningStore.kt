package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.conv.WordSource
import io.github.skrtk98.iroha.engine.dict.WordEntry

/**
 * 選んだ候補を覚えて次回から優先する。新しいものほど前に置き、[capacity] を超えた古いものは捨てる。
 * 書き込みは差し替え方式なので、読み出しはロック不要。
 *
 * 語ごとの学習に加え、直前の文節ごとの学習も持つ。「工作|きかい」で機械、「よい|きかい」で機会を選べば、
 * 次からはそれぞれの前の文節に応じて出し分ける。直前ごとの学習は変換の先頭候補を決めるのにだけ使い、
 * 語の供給元 ([WordSource]) には出さない。
 */
class LearningStore(private val capacity: Int = 4000) : WordSource {

    /** [previous] は直前の文節の表記。語ごとの学習では null。 */
    private class Learned(val entry: WordEntry, val previous: String?)

    private class Snapshot(
        val learned: List<Learned>,
        val byReading: Map<String, List<WordEntry>>,
        val byContext: Map<Pair<String, String>, WordEntry>,
        val maxLength: Int,
    )

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyMap(), emptyMap(), 0)

    /** 語ごとの学習。新しい順。 */
    val entries: List<WordEntry> get() = snapshot.learned.filter { it.previous == null }.map { it.entry }

    /** 語として覚える。[previous] があれば、その文節の後に来たことも覚える。 */
    @Synchronized
    fun learn(reading: String, surface: String, lid: Int, rid: Int, previous: String? = null) {
        if (reading.isEmpty() || surface.isEmpty()) return
        val entry = WordEntry(reading, surface, lid, rid, LEARNED_COST)
        val added = listOfNotNull(
            Learned(entry, null),
            previous?.takeIf { it.isNotEmpty() }?.let { Learned(entry, it.takeLast(MAX_PREVIOUS)) },
        )
        val updated = ArrayList<Learned>(snapshot.learned.size + added.size)
        updated.addAll(added)
        // 同じ読みと直前に別の表記を覚えていたら、新しい方で置き換える
        snapshot.learned.filterTo(updated) { old ->
            added.none { new ->
                old.entry.reading == reading && old.previous == new.previous &&
                    (new.previous != null || old.entry.surface == surface)
            }
        }
        replace(updated.take(capacity))
    }

    /** 直前の文節が [previous] のときに、読み [reading] で選んだ語。 */
    fun inContext(previous: String, reading: String): WordEntry? =
        snapshot.byContext[previous.takeLast(MAX_PREVIOUS) to reading]

    @Synchronized
    fun replaceAll(entries: List<WordEntry>) = replace(entries.take(capacity).map { Learned(it, null) })

    @Synchronized
    fun clear() = replace(emptyList())

    private fun replace(learned: List<Learned>) {
        // 新しい学習ほどコストを小さくして先に出す
        val words = learned.filter { it.previous == null }
            .mapIndexed { i, l -> l.entry.copy(cost = LEARNED_COST + minOf(i, 500)) }
        val contexts = HashMap<Pair<String, String>, WordEntry>()
        for (l in learned) if (l.previous != null) contexts.putIfAbsent(l.previous to l.entry.reading, l.entry)
        snapshot = Snapshot(
            learned,
            words.groupBy { it.reading },
            contexts,
            words.maxOfOrNull { it.reading.length } ?: 0,
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

    /** 保存形式: よみ<TAB>表記<TAB>lid<TAB>rid[<TAB>直前の文節]（新しい順） */
    fun serialize(): String = buildString {
        for (l in snapshot.learned) {
            val e = l.entry
            append(e.reading).append('\t').append(e.surface).append('\t').append(e.lid).append('\t').append(e.rid)
            if (l.previous != null) append('\t').append(l.previous)
            append('\n')
        }
    }

    @Synchronized
    fun deserialize(text: String) {
        val learned = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) return@mapNotNull null
            val lid = f[2].toIntOrNull() ?: return@mapNotNull null
            val rid = f[3].toIntOrNull() ?: return@mapNotNull null
            Learned(WordEntry(f[0], f[1], lid, rid, LEARNED_COST), f.getOrNull(4)?.takeIf { it.isNotEmpty() })
        }.toList()
        replace(learned.take(capacity))
    }

    companion object {
        private const val LEARNED_COST = 1500

        /** 直前の文節として覚える長さ。長い文節は末尾だけ見る。 */
        private const val MAX_PREVIOUS = 8
    }
}
