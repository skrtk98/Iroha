package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.dict.ExtraKind
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
    /** 絵文字を候補に出してよいか。端末のフォントで表示できない絵文字を除くのに使う。 */
    private val isDisplayable: (String) -> Boolean = { true },
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
        return applyCollocations(reading, splitSegments(reading, path), leftRid)
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
        limit: Int = 200,
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
        val ranked = LinkedHashMap<String, Candidate>()
        for (candidate in scored) {
            if (ranked.size >= DICTIONARY_CANDIDATES) break
            ranked.putIfAbsent(candidate.surface, candidate)
        }
        // 辞書の上位の後に記号と絵文字を挟み、顔文字と単漢字は辞書の候補の後ろに付ける
        val dictionaryCandidates = withNumberVariants(ranked.values)
        val ordered = dictionaryCandidates.take(CANDIDATES_BEFORE_SYMBOLS) +
            extraCandidates(ExtraKind.Symbol, segmentReading) +
            extraCandidates(ExtraKind.Emoji, segmentReading) +
            dictionaryCandidates.drop(CANDIDATES_BEFORE_SYMBOLS) +
            extraCandidates(ExtraKind.Emoticon, segmentReading) +
            extraCandidates(ExtraKind.SingleKanji, segmentReading)
        val result = LinkedHashMap<String, Candidate>()
        for (candidate in ordered) {
            if (result.size >= dictionaryLimit) break
            result.putIfAbsent(candidate.surface, candidate)
        }
        for (fallback in fallbacks) {
            if (result.size >= limit) break
            result.putIfAbsent(fallback.surface, fallback)
        }
        return result.values.toList()
    }

    /** 数で始まる候補のすぐ後に、その数の別の書き方 (3千円 → 三千円, 3000円, 3,000円 …) を置く。 */
    private fun withNumberVariants(candidates: Collection<Candidate>): List<Candidate> = buildList {
        for (candidate in candidates) {
            add(candidate)
            if (dictionary.posClass(candidate.lid) != PosClass.Number) continue
            NumberVariants.of(candidate.surface).forEach { add(candidate.copy(surface = it)) }
        }
    }

    /** [kind] のデータのうち読みが [reading] の語を候補にする。 */
    private fun extraCandidates(kind: ExtraKind, reading: String): List<Candidate> =
        dictionary.extras(kind, reading)
            .filter { kind != ExtraKind.Emoji || isDisplayable(it.surface) }
            .map { it.toCandidate() }

    /**
     * 確定した [committed] の次に出す候補 (mozc のゼロクエリ)。末尾が数字なら助数詞などを、
     * そうでなければ末尾に一致するいちばん長いきっかけの語に続く言葉を返す。
     */
    fun zeroQuery(committed: String, limit: Int = 30): List<Candidate> {
        val digits = committed.takeLastWhile { it in '0'..'9' || it in '０'..'９' }
            .map { if (it in '０'..'９') '0' + (it - '０') else it }
            .joinToString("")
        val words = if (digits.isNotEmpty()) {
            dictionary.extras(ExtraKind.ZeroQueryNumber, digits)
                .ifEmpty { dictionary.extras(ExtraKind.ZeroQueryNumber, ZERO_QUERY_DEFAULT) }
        } else {
            (minOf(committed.length, SystemDictionary.MAX_READING_CHARS) downTo 1).asSequence()
                .map { dictionary.extras(ExtraKind.ZeroQuery, committed.takeLast(it)) }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        }
        return words.asSequence()
            .filter { isDisplayable(it.surface) }
            .map { Candidate(it.surface, "", it.cost, it.lid, it.rid) }
            .distinctBy { it.surface }
            .take(limit)
            .toList()
    }

    /** collocation.txt の並び。数が少ないのですべて持っておく。 */
    private val collocations: List<String> by lazy {
        val all = ArrayList<String>()
        dictionary.predictExtras(ExtraKind.Collocation, "", Int.MAX_VALUE) { all.add(it.surface) }
        all
    }

    /**
     * mozc の collocation.txt に載っている並び (こっちに来い など) になるよう、隣の文節と合わせて
     * 候補を選び直す。並びの片側がもう選ばれていて、もう片側が候補にあるときだけ入れ替える。
     */
    private fun applyCollocations(reading: String, segments: List<Segment>, leftRid: Int): List<Segment> {
        if (segments.size < 2 || collocations.isEmpty()) return segments
        val result = segments.toMutableList()
        var start = 0
        for (i in 0 until result.size - 1) {
            val end = start + result[i].reading.length
            val nextEnd = end + result[i + 1].reading.length
            val current = result[i].candidates.first()
            val next = result[i + 1].candidates.first()
            val left = result.getOrNull(i - 1)?.candidates?.first()?.rid ?: leftRid
            val right = result.getOrNull(i + 2)?.candidates?.first()?.lid ?: BOS_EOS
            for (collocation in collocations) {
                if (collocation == current.surface + next.surface) break
                val nextCandidate = if (collocation.startsWith(current.surface)) {
                    val wanted = collocation.substring(current.surface.length)
                    candidates(reading, end, nextEnd, current.rid, right).firstOrNull { it.surface == wanted }
                } else {
                    null
                }
                if (nextCandidate != null) {
                    result[i + 1] = Segment(result[i + 1].reading, listOf(nextCandidate))
                    break
                }
                val currentCandidate = if (collocation.endsWith(next.surface)) {
                    val wanted = collocation.removeSuffix(next.surface)
                    candidates(reading, start, end, left, next.lid).firstOrNull { it.surface == wanted }
                } else {
                    null
                }
                if (currentCandidate != null) {
                    result[i] = Segment(result[i].reading, listOf(currentCandidate))
                    break
                }
            }
            start = end
        }
        return result
    }

    /**
     * 入力中の読みに対する予測候補。先頭は全体の変換結果。
     */
    fun suggest(reading: String, limit: Int = 30): List<Candidate> {
        if (reading.isEmpty()) return emptyList()
        val result = LinkedHashMap<String, Candidate>()
        fun add(c: Candidate) {
            if (result.size >= limit || c.surface in result) return
            if (dictionary.extras(ExtraKind.SuggestionFilter, c.surface).isNotEmpty()) return
            result[c.surface] = c
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
        // よくある読み間違いには正しい表記を出す (おどさん → お土産)
        dictionary.extras(ExtraKind.ReadingCorrection, reading).forEach { add(it.toCandidate()) }

        val predicted = ArrayList<WordEntry>()
        for (source in sources) predicted += source.predict(reading, PREDICT_SOURCE_LIMIT)
        val maxReadings = if (reading.length <= 1) PREDICT_SCAN_SHORT else PREDICT_SCAN
        val systemPredicted = ArrayList<WordEntry>()
        dictionary.predictiveSearch(reading, maxReadings) { w ->
            if (w.reading.length > reading.length && dictionary.posClass(w.lid).let { it == PosClass.Content || it == PosClass.Number }) {
                systemPredicted.add(w)
            }
        }
        systemPredicted.sortBy { it.cost }
        predicted += systemPredicted.take(limit)
        predicted.forEach { add(it.toCandidate()) }
        // 語に続く言い回し (しなきゃいけない など)
        val suffixes = ArrayList<WordEntry>()
        dictionary.predictExtras(ExtraKind.Suffix, reading, PREDICT_SCAN_SHORT) { suffixes.add(it) }
        suffixes.sortedBy { it.cost }.take(SUFFIX_LIMIT).forEach { add(it.toCandidate()) }
        extraCandidates(ExtraKind.Symbol, reading).forEach(::add)
        extraCandidates(ExtraKind.Emoji, reading).forEach(::add)

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

        /** 文節の候補のうち、辞書から出す数の上限。 */
        private const val DICTIONARY_CANDIDATES = 60

        /** 文節の候補で、記号・絵文字より前に置く辞書の候補の数。 */
        private const val CANDIDATES_BEFORE_SYMBOLS = 3
        private const val SUFFIX_LIMIT = 5
        private const val ZERO_QUERY_DEFAULT = "default"
    }
}
