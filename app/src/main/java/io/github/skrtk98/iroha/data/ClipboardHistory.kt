package io.github.skrtk98.iroha.data

import androidx.compose.runtime.Immutable

@Immutable
data class ClipEntry(
    val text: String,
    val pinned: Boolean = false,
    val time: Long = 0,
)

/**
 * クリップボード履歴の並び。新しいものが先頭で、固定したものは件数の上限で消えない。
 * 不変のリストを返すので、そのまま UI 状態に載せられる。
 */
class ClipboardHistory(private val maxUnpinned: Int = 30, private val maxPinned: Int = 50) {

    var entries: List<ClipEntry> = emptyList()
        private set

    fun add(text: String, time: Long) {
        if (text.isBlank()) return
        val existing = entries.firstOrNull { it.text == text }
        val entry = ClipEntry(text, pinned = existing?.pinned ?: false, time = time)
        val rest = entries.filterNot { it.text == text }
        var unpinned = if (entry.pinned) 0 else 1
        entries = listOf(entry) + rest.filter { it.pinned || ++unpinned <= maxUnpinned }
    }

    /** 固定数が上限に達しているときは新たに固定しない。固定の解除はいつでもできる。 */
    fun togglePin(text: String) {
        val target = entries.firstOrNull { it.text == text } ?: return
        if (!target.pinned && entries.count { it.pinned } >= maxPinned) return
        entries = entries.map { if (it.text == text) it.copy(pinned = !it.pinned) else it }
    }

    fun remove(text: String) {
        entries = entries.filterNot { it.text == text }
    }

    /** 固定していない項目を消す。 */
    fun clearUnpinned() {
        entries = entries.filter { it.pinned }
    }

    fun clearAll() {
        entries = emptyList()
    }

    /** 1 行 1 項目。固定フラグ、時刻、本文をタブ区切りにし、本文の改行などはエスケープする。 */
    fun serialize(): String = buildString {
        for (e in entries) {
            append(if (e.pinned) '1' else '0').append('\t').append(e.time).append('\t').append(escape(e.text)).append('\n')
        }
    }

    fun deserialize(text: String) {
        entries = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t', limit = 3)
            if (f.size < 3) return@mapNotNull null
            ClipEntry(unescape(f[2]), pinned = f[0] == "1", time = f[1].toLongOrNull() ?: 0)
        }.toList()
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                out.append(
                    when (s[i + 1]) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> s[i + 1]
                    },
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
