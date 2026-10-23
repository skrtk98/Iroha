package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.dict.PosClass
import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import io.github.skrtk98.iroha.engine.dict.WordEntry
import io.github.skrtk98.iroha.engine.text.Kana

/**
 * mozc と同じくラティスを作り、単語コストと連接コストの和が最小の経路をビタビで求める。
 * 状態を持たないので複数スレッドから呼んでよい。
 */
class Converter(
    private val dictionary: SystemDictionary,
    private val sources: List<WordSource> = emptyList(),
) {
    private class Node(
        val start: Int,
        val end: Int,
        val word: WordEntry,
    ) {
        var total = Int.MAX_VALUE
        var prev: Node? = null
    }

    /**
     * [reading] 全体を変換して文節に分ける。各文節の候補は先頭の 1 件だけを持つ。
     * 候補一覧は [candidates] で別に求める。
     */
    fun convert(reading: String, leftRid: Int = BOS_EOS): List<Segment> {
        if (reading.isEmpty()) return emptyList()
        val path = bestPath(reading, 0, reading.length, leftRid, BOS_EOS)
        return splitSegments(reading, path)
    }

    /**
     * 文節 [start]..[end] の候補を良い順に返す。[leftRid] は前の文節の右端品詞、
     * [rightLid] は次の文節の左端品詞（末尾なら文末）。
     */
    fun candidates(
        reading: String,
        start: Int,
        end: Int,
        leftRid: Int = BOS_EOS,
        rightLid: Int = BOS_EOS,
        limit: Int = 60,
    ): List<Candidate> {
        val nodesAt = buildLattice(reading, start, end)
        // best[node] = node から区間末尾までの最小コスト（node 自身を含む）
        val best = HashMap<Node, Int>()
        val next = HashMap<Node, Node?>()
        for (pos in end - 1 downTo start) {
            for (node in nodesAt[pos - start]) {
                var bestCost: Int
                var bestNext: Node? = null
                if (node.end == end) {
                    bestCost = dictionary.connectionCost(node.word.rid, rightLid) + dictionary.suffixPenalty(node.word.rid)
                } else {
                    bestCost = Int.MAX_VALUE
                    for (m in nodesAt[node.end - start]) {
                        val c = best[m] ?: continue
                        val total = dictionary.connectionCost(node.word.rid, m.word.lid) + c
                        if (total < bestCost) {
                            bestCost = total
                            bestNext = m
                        }
                    }
                    if (bestCost == Int.MAX_VALUE) continue
                }
                best[node] = bestCost + node.word.cost
                next[node] = bestNext
            }
        }
        val segmentReading = reading.substring(start, end)
        val scored = ArrayList<Candidate>()
        for (node in nodesAt[0]) {
            val c = best[node] ?: continue
            val surface = StringBuilder()
            var cur: Node? = node
            var last = node
            while (cur != null) {
                surface.append(cur.word.surface)
                last = cur
                cur = next[cur]
            }
            val cost = dictionary.connectionCost(leftRid, node.word.lid) + dictionary.prefixPenalty(node.word.lid) + c
            scored.add(Candidate(surface.toString(), segmentReading, cost, node.word.lid, last.word.rid))
        }
        scored.sortBy { it.cost }
        val fallbackId = dictionary.unknownNounId
        val fallbacks = listOf(segmentReading, Kana.toKatakana(segmentReading)).distinct()
            .map { Candidate(it, segmentReading, UNKNOWN_COST, fallbackId, fallbackId) }
        // ひらがな・カタカナを必ず残せるよう、その分の枠を空けておく
        val dictionaryLimit = (limit - fallbacks.size).coerceAtLeast(0)
        val result = LinkedHashMap<String, Candidate>()
        for (candidate in scored) {
            if (result.size >= dictionaryLimit) break
            result.putIfAbsent(candidate.surface, candidate)
        }
        for (fallback in fallbacks) {
            if (result.size >= limit) break
            result.putIfAbsent(fallback.surface, fallback)
        }
        return result.values.toList()
    }

    /**
     * 入力中の読みに対する予測候補。先頭は全体の変換結果。
     */
    fun suggest(reading: String, limit: Int = 30): List<Candidate> {
        if (reading.isEmpty()) return emptyList()
        val result = LinkedHashMap<String, Candidate>()
        fun add(c: Candidate) {
            if (result.size < limit) result.putIfAbsent(c.surface, c)
        }

        // 学習・ユーザー辞書の完全一致を最優先
        for (source in sources) {
            for (w in source.exact(reading)) add(w.toCandidate())
        }
        val segments = convert(reading)
        if (segments.isNotEmpty()) {
            val first = segments.first().candidates.first()
            val last = segments.last().candidates.first()
            add(
                Candidate(
                    surface = segments.joinToString("") { it.candidates.first().surface },
                    reading = reading,
                    cost = 0,
                    lid = first.lid,
                    rid = last.rid,
                ),
            )
        }
        dictionary.exactSearch(reading)
            .filter { dictionary.posClass(it.lid) != PosClass.Functional }
            .forEach { add(it.toCandidate()) }

        val predicted = ArrayList<WordEntry>()
        for (source in sources) predicted += source.predict(reading, PREDICT_SOURCE_LIMIT)
        val maxReadings = if (reading.length <= 1) PREDICT_SCAN_SHORT else PREDICT_SCAN
        val systemPredicted = ArrayList<WordEntry>()
        dictionary.predictiveSearch(reading, maxReadings) { w ->
            if (w.reading.length > reading.length && dictionary.posClass(w.lid) == PosClass.Content) {
                systemPredicted.add(w)
            }
        }
        systemPredicted.sortBy { it.cost }
        predicted += systemPredicted.take(limit)
        predicted.forEach { add(it.toCandidate()) }

        add(Candidate(reading, reading, UNKNOWN_COST, dictionary.unknownNounId, dictionary.unknownNounId))
        add(
            Candidate(
                Kana.toKatakana(reading), reading, UNKNOWN_COST,
                dictionary.unknownNounId, dictionary.unknownNounId,
            ),
        )
        return result.values.toList()
    }

    private fun WordEntry.toCandidate() = Candidate(surface, reading, cost, lid, rid)

    private fun bestPath(reading: String, start: Int, end: Int, leftRid: Int, rightLid: Int): List<Node> {
        val nodesAt = buildLattice(reading, start, end)
        val endingAt = Array(end - start + 1) { ArrayList<Node>() }
        for (pos in start until end) {
            for (node in nodesAt[pos - start]) {
                val prevs = if (pos == start) null else endingAt[pos - start]
                if (prevs == null) {
                    // mozc と同じく、変換する範囲の先頭と末尾の語には boundary.def のコストを足す
                    node.total = dictionary.connectionCost(leftRid, node.word.lid) + node.word.cost +
                        dictionary.prefixPenalty(node.word.lid)
                } else {
                    for (p in prevs) {
                        val t = p.total + dictionary.connectionCost(p.word.rid, node.word.lid) + node.word.cost
                        if (t < node.total) {
                            node.total = t
                            node.prev = p
                        }
                    }
                    if (node.prev == null) continue
                }
                endingAt[node.end - start].add(node)
            }
        }
        var bestLast: Node? = null
        var bestTotal = Int.MAX_VALUE
        for (node in endingAt[end - start]) {
            val t = node.total + dictionary.connectionCost(node.word.rid, rightLid) + dictionary.suffixPenalty(node.word.rid)
            if (t < bestTotal) {
                bestTotal = t
                bestLast = node
            }
        }
        val path = ArrayList<Node>()
        var cur = bestLast
        while (cur != null) {
            path.add(cur)
            cur = cur.prev
        }
        path.reverse()
        return path
    }

    /** [start]..[end] の各位置から始まり、[end] を越えない語のノード。 */
    private fun buildLattice(reading: String, start: Int, end: Int): Array<ArrayList<Node>> {
        val nodesAt = Array(end - start) { ArrayList<Node>() }
        val sub = reading.substring(0, end)
        for (pos in start until end) {
            val list = nodesAt[pos - start]
            for (w in dictionary.commonPrefixSearch(sub, pos)) {
                list.add(Node(pos, pos + w.reading.length, w))
            }
            for (source in sources) {
                val maxLen = minOf(source.maxReadingLength, end - pos)
                for (len in 1..maxLen) {
                    for (w in source.exact(sub.substring(pos, pos + len))) {
                        list.add(Node(pos, pos + len, w))
                    }
                }
            }
            addUnknownNodes(sub, pos, list)
        }
        return nodesAt
    }

    /** 辞書に無い文字列でも経路が切れないよう、1 文字の未知語と英数字・カタカナの連続を足す。 */
    private fun addUnknownNodes(text: String, pos: Int, list: MutableList<Node>) {
        val id = dictionary.unknownNounId
        val c = text[pos]
        list.add(Node(pos, pos + 1, WordEntry(c.toString(), c.toString(), id, id, UNKNOWN_COST)))
        if (!Kana.isHiragana(c)) {
            var runEnd = pos + 1
            val runType = charType(c)
            while (runEnd < text.length && charType(text[runEnd]) == runType) runEnd++
            if (runEnd > pos + 1) {
                val s = text.substring(pos, runEnd)
                list.add(Node(pos, runEnd, WordEntry(s, s, id, id, RUN_COST)))
            }
        }
    }

    private fun charType(c: Char): Int = when {
        Kana.isHiragana(c) -> 0
        Kana.isKatakana(c) -> 1
        c.isDigit() -> 2
        c.isLetter() && c.code < 0x3000 -> 3
        else -> 4
    }

    /** mozc の segmenter.def に従い、隣り合う語の品詞から文節を切る。 */
    private fun splitSegments(reading: String, path: List<Node>): List<Segment> {
        val segments = ArrayList<Segment>()
        var segStart = 0
        var surface = StringBuilder()
        var firstLid = 0
        var lastRid = 0
        var cost = 0
        for ((index, node) in path.withIndex()) {
            if (index > 0 && dictionary.isBoundary(path[index - 1].word.rid, node.word.lid)) {
                segments.add(
                    Segment(
                        reading.substring(segStart, node.start),
                        listOf(Candidate(surface.toString(), reading.substring(segStart, node.start), cost, firstLid, lastRid)),
                    ),
                )
                surface = StringBuilder()
                segStart = node.start
                cost = 0
            }
            if (surface.isEmpty()) firstLid = node.word.lid
            surface.append(node.word.surface)
            lastRid = node.word.rid
            cost += node.word.cost
        }
        if (path.isNotEmpty()) {
            val r = reading.substring(segStart)
            segments.add(Segment(r, listOf(Candidate(surface.toString(), r, cost, firstLid, lastRid))))
        }
        return segments
    }

    companion object {
        const val BOS_EOS = 0
        private const val UNKNOWN_COST = 12000
        private const val RUN_COST = 4000
        private const val PREDICT_SCAN = 3000
        private const val PREDICT_SCAN_SHORT = 800
        private const val PREDICT_SOURCE_LIMIT = 10
    }
}
