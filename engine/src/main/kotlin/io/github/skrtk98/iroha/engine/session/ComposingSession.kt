package io.github.skrtk98.iroha.engine.session

import io.github.skrtk98.iroha.engine.conv.Candidate
import io.github.skrtk98.iroha.engine.conv.Converter
import io.github.skrtk98.iroha.engine.conv.Segment
import io.github.skrtk98.iroha.engine.text.KanaModifier
import io.github.skrtk98.iroha.engine.text.RomajiConverter
import io.github.skrtk98.iroha.engine.user.LearningStore

/** 未確定文字列の表示内容。[highlightStart]..[highlightEnd] は注目文節。 */
data class Preedit(
    val text: String,
    val caret: Int,
    val highlightStart: Int = -1,
    val highlightEnd: Int = -1,
) {
    companion object {
        val Empty = Preedit("", 0)
    }
}

/**
 * かな漢字変換の入力状態を管理する。キー操作ごとに呼び、確定した文字列を戻り値で返す。
 * メインスレッドからだけ使う。候補一覧の計算は重いので呼び出し側で別スレッドに逃がせるよう、
 * [suggestionReading] と [Converter] を分けて公開している。
 */
class ComposingSession(
    private val converter: Converter,
    private val learning: LearningStore? = null,
) {
    private class ConvSegment(
        val reading: String,
        var candidates: List<Candidate>,
        var expanded: Boolean = false,
        var selected: Int = 0,
    ) {
        val current: Candidate get() = candidates[selected]
    }

    private var buffer = StringBuilder()
    private var caret = 0
    private var segments: List<ConvSegment>? = null
    private var focus = 0

    /** 直前に確定した文節の表記。次の変換で、最初の文節の前の文脈として使う。 */
    private var previousSurface: String? = null

    val isComposing: Boolean get() = buffer.isNotEmpty()
    val isConverting: Boolean get() = segments != null

    /** 予測候補を求めるための読み。変換中は null。 */
    val suggestionReading: String?
        get() = if (isComposing && !isConverting) RomajiConverter.finish(buffer) else null

    /** 変換中の注目文節の候補。変換中でなければ空。 */
    val focusedCandidates: List<Candidate>
        get() = segments?.get(focus)?.let { ensureExpanded(it); it.candidates }.orEmpty()

    val focusedIndex: Int get() = segments?.get(focus)?.selected ?: -1

    val preedit: Preedit
        get() {
            val segs = segments ?: return Preedit(buffer.toString(), caret)
            val text = StringBuilder()
            var start = 0
            var end = 0
            for ((i, s) in segs.withIndex()) {
                if (i == focus) start = text.length
                text.append(s.current.surface)
                if (i == focus) end = text.length
            }
            return Preedit(text.toString(), text.length, start, end)
        }

    /**
     * 文字を入力する。[romaji] が true ならローマ字として、カーソル前の文字列と合わせてかなに直す。
     * 変換中なら現在の変換を確定してから入力し、確定した文字列を返す。
     */
    fun insert(text: String, romaji: Boolean): String {
        val committed = if (isConverting) commit() else ""
        val before = buffer.substring(0, caret) + text
        val after = buffer.substring(caret)
        val converted = if (romaji) RomajiConverter.convert(before) else before
        buffer = StringBuilder(converted).append(after)
        caret = converted.length
        return committed
    }

    /** カーソル直前の文字を濁点・半濁点・小書きに切り替える。対象が無ければ false。 */
    fun toggleModifier(): Boolean {
        if (isConverting || caret == 0) return false
        val next = KanaModifier.cycle(buffer[caret - 1]) ?: return false
        buffer.setCharAt(caret - 1, next)
        return true
    }

    /** 処理したら true。何も入力していなければ false を返し、呼び出し側でエディタの文字を消す。 */
    fun backspace(): Boolean {
        if (isConverting) {
            segments = null
            return true
        }
        if (buffer.isEmpty()) return false
        if (caret > 0) {
            buffer.deleteCharAt(caret - 1)
            caret--
        }
        return true
    }

    /** 変換を始める。変換中なら注目文節の次の候補へ。 */
    fun convertOrNext(): Boolean {
        if (!isComposing) return false
        val segs = segments
        if (segs == null) {
            startConversion()
        } else {
            moveCandidate(1)
        }
        return true
    }

    fun previousCandidate(): Boolean {
        if (!isConverting) return false
        moveCandidate(-1)
        return true
    }

    /** 変換中は注目文節を移動、入力中はカーソルを移動する。 */
    fun moveCursor(delta: Int): Boolean {
        val segs = segments
        if (segs != null) {
            focus = (focus + delta).coerceIn(0, segs.lastIndex)
            return true
        }
        if (!isComposing) return false
        caret = (caret + delta).coerceIn(0, buffer.length)
        return true
    }

    /** 注目文節の長さを [delta] 文字だけ伸び縮みさせ、後ろの文節を変換し直す。 */
    fun resizeSegment(delta: Int): Boolean {
        val segs = segments ?: return false
        val reading = segs.joinToString("") { it.reading }
        val start = segs.take(focus).sumOf { it.reading.length }
        val newLength = (segs[focus].reading.length + delta).coerceIn(1, reading.length - start)
        if (newLength == segs[focus].reading.length) return true
        val leftRid = segs.getOrNull(focus - 1)?.current?.rid ?: Converter.BOS_EOS
        val end = start + newLength
        val resized = ConvSegment(reading.substring(start, end), converter.candidates(reading, start, end, leftRid))
        resized.expanded = true
        val rest = if (end < reading.length) {
            toConvSegments(converter.convert(reading.substring(end), resized.current.rid), resized.current.surface)
        } else {
            emptyList()
        }
        segments = segs.take(focus) + resized + rest
        return true
    }

    /** 変換中に候補一覧から選ぶ。最後の文節なら全体を確定して返す。 */
    fun selectCandidate(index: Int): String {
        val segs = segments ?: return ""
        val seg = segs[focus]
        ensureExpanded(seg)
        if (index !in seg.candidates.indices) return ""
        seg.selected = index
        if (focus < segs.lastIndex) {
            focus++
            return ""
        }
        return commit()
    }

    /** 入力中に予測候補を選んで確定する。 */
    fun selectSuggestion(candidate: Candidate, index: Int): String {
        if (index > 0) learning?.learn(candidate.reading, candidate.surface, candidate.lid, candidate.rid, previousSurface)
        clear()
        previousSurface = candidate.surface
        return candidate.surface
    }

    /** 現在の内容を確定して返す。 */
    fun commit(): String {
        val segs = segments
        val text = if (segs != null) {
            for ((i, seg) in segs.withIndex()) {
                if (seg.selected > 0) {
                    val previous = if (i == 0) previousSurface else segs[i - 1].current.surface
                    learning?.learn(seg.reading, seg.current.surface, seg.current.lid, seg.current.rid, previous)
                }
            }
            segs.joinToString("") { it.current.surface }
        } else {
            RomajiConverter.finish(buffer)
        }
        clear()
        previousSurface = segs?.last()?.current?.surface
        return text
    }

    /** 変換中なら変換前に戻し、入力中なら破棄する。 */
    fun cancel() {
        if (segments != null) segments = null else clear()
    }

    /** 入力中の読みを [transform] で置き換える（カタカナ・英数変換など）。 */
    fun transformComposing(transform: (String) -> String): Boolean {
        if (!isComposing || isConverting) return false
        val replaced = transform(RomajiConverter.finish(buffer))
        buffer = StringBuilder(replaced)
        caret = replaced.length
        return true
    }

    /** カーソルが動いたときなど、直前の文脈が当てにならなくなったときに呼ぶ。 */
    fun forgetContext() {
        previousSurface = null
    }

    /** 変換せずに続けて入れた [text] を、次の変換の文脈にする。 */
    fun continueContext(text: String) {
        previousSurface = text
    }

    fun clear() {
        buffer = StringBuilder()
        caret = 0
        segments = null
        focus = 0
    }

    private fun startConversion() {
        val reading = RomajiConverter.finish(buffer)
        buffer = StringBuilder(reading)
        caret = reading.length
        val converted = converter.convert(reading)
        if (converted.isEmpty()) return
        segments = toConvSegments(converted, previousSurface)
        focus = 0
    }

    /** 前から順に、直前の文節を文脈にして [toConvSegment] する。 */
    private fun toConvSegments(converted: List<Segment>, previous: String?): List<ConvSegment> {
        var before = previous
        return converted.map { segment -> toConvSegment(segment, before).also { before = it.current.surface } }
    }

    /**
     * mozc の文節履歴と同じく、過去に選んだ表記があればそれを先頭にする。
     * 同じ直前の文節の後で選んだ表記があれば、それを語ごとの学習より優先する。
     */
    private fun toConvSegment(segment: Segment, previous: String?): ConvSegment {
        val learned = previous?.let { learning?.inContext(it, segment.reading) }
            ?: learning?.exact(segment.reading)?.minByOrNull { it.cost }
        val first = if (learned != null) {
            Candidate(learned.surface, learned.reading, learned.cost, learned.lid, learned.rid)
        } else {
            segment.candidates.first()
        }
        return ConvSegment(segment.reading, listOf(first))
    }

    private fun moveCandidate(delta: Int) {
        val seg = segments?.get(focus) ?: return
        ensureExpanded(seg)
        val size = seg.candidates.size
        seg.selected = ((seg.selected + delta) % size + size) % size
    }

    /** 文節の候補一覧を必要になった時点で求める。今の候補を先頭に保つ。 */
    private fun ensureExpanded(seg: ConvSegment) {
        if (seg.expanded) return
        val segs = checkNotNull(segments)
        val index = segs.indexOf(seg)
        val reading = segs.joinToString("") { it.reading }
        val start = segs.take(index).sumOf { it.reading.length }
        val leftRid = segs.getOrNull(index - 1)?.current?.rid ?: Converter.BOS_EOS
        val rightLid = segs.getOrNull(index + 1)?.current?.lid ?: Converter.BOS_EOS
        val current = seg.current
        val list = converter.candidates(reading, start, start + seg.reading.length, leftRid, rightLid)
        seg.candidates = listOf(current) + list.filter { it.surface != current.surface }
        seg.selected = 0
        seg.expanded = true
    }
}
