package io.github.skrtk98.iroha.data

import androidx.compose.runtime.Immutable

@Immutable
data class ClipEntry(
    val text: String,
    val pinned: Boolean = false,
    val time: Long = 0,
    /** 画像のクリップなら、保存した画像のファイル名。そのとき [text] は空。 */
    val image: String? = null,
) {
    /** 履歴の中で項目を見分ける鍵。同じ文字列・同じ画像は 1 件にまとめる。 */
    val key: String get() = if (image != null) "image:$image" else "text:$text"
}

/**
 * クリップボード履歴の並び。新しいものが先頭で、固定したものは件数の上限で消えない。
 * 不変のリストを返すので、そのまま UI 状態に載せられる。
 */
class ClipboardHistory(
    private val maxUnpinned: Int = 30,
    private val maxPinned: Int = 50,
    /** 画像は大きいので、固定していないもの・固定したものそれぞれ別に少なく抑える。 */
    private val maxImages: Int = 10,
) {

    var entries: List<ClipEntry> = emptyList()
        private set

    fun add(text: String, time: Long) = add(ClipEntry(text, time = time))

    /**
     * [clip] を時刻の順の位置に加える。普通は先頭になるが、画像のように読み込みに時間がかかり、
     * 後からコピーしたものより遅れて届いた項目はその後ろに入れる。既にある項目なら固定の状態を引き継ぐ。
     */
    fun add(clip: ClipEntry) {
        if (clip.image == null && clip.text.isBlank()) return
        val existing = entries.firstOrNull { it.key == clip.key }
        // 同じ画像の保存が前後して届いても、新しいほうの時刻を残す
        val entry = clip.copy(pinned = existing?.pinned ?: false, time = maxOf(clip.time, existing?.time ?: clip.time))
        val rest = entries.filterNot { it.key == clip.key }
        val index = rest.indexOfFirst { it.time <= entry.time }.let { if (it < 0) rest.size else it }
        var unpinned = 0
        var unpinnedImages = 0
        entries = (rest.take(index) + entry + rest.drop(index)).filter {
            it.pinned || (++unpinned <= maxUnpinned && (it.image == null || ++unpinnedImages <= maxImages))
        }
    }

    /** 固定数（画像は画像の固定数）が上限に達しているときは新たに固定しない。固定の解除はいつでもできる。 */
    fun togglePin(key: String) {
        val target = entries.firstOrNull { it.key == key } ?: return
        if (!target.pinned && entries.count { it.pinned } >= maxPinned) return
        if (!target.pinned && target.image != null && entries.count { it.pinned && it.image != null } >= maxImages) return
        entries = entries.map { if (it.key == key) it.copy(pinned = !it.pinned) else it }
    }

    fun remove(key: String) {
        entries = entries.filterNot { it.key == key }
    }

    /** 固定していない項目を消す。 */
    fun clearUnpinned() {
        entries = entries.filter { it.pinned }
    }

    fun clearAll() {
        entries = emptyList()
    }

    /**
     * 1 行 1 項目。固定フラグ、時刻、本文をタブ区切りにし、本文の改行などはエスケープする。
     * 画像の項目は 4 つ目の欄に画像のファイル名を置く。
     */
    fun serialize(): String = buildString {
        for (e in entries) {
            append(if (e.pinned) '1' else '0').append('\t').append(e.time).append('\t').append(escape(e.text))
            if (e.image != null) append('\t').append(escape(e.image))
            append('\n')
        }
    }

    fun deserialize(text: String) {
        entries = text.lineSequence().mapNotNull { line ->
            val f = line.split('\t', limit = 4)
            if (f.size < 3) return@mapNotNull null
            ClipEntry(
                unescape(f[2]),
                pinned = f[0] == "1",
                time = f[1].toLongOrNull() ?: 0,
                image = f.getOrNull(3)?.let(::unescape),
            )
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
