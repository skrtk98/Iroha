package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.conv.WordSource
import io.github.skrtk98.iroha.engine.dict.UserPos
import io.github.skrtk98.iroha.engine.dict.WordEntry

/** ユーザーが登録した語。 */
data class UserWord(
    val reading: String,
    val surface: String,
    val pos: UserPos = UserPos.Noun,
    val comment: String = "",
)

/**
 * ユーザー辞書。書き込みのたびに索引を作り直して差し替えるので、
 * 変換スレッドからはロックなしで読める。
 */
class UserDictionary(private val posId: (UserPos) -> Int) : WordSource {

    private class Snapshot(val words: List<UserWord>, val byReading: Map<String, List<WordEntry>>, val maxLength: Int)

    @Volatile
    private var snapshot = Snapshot(emptyList(), emptyMap(), 0)

    val words: List<UserWord> get() = snapshot.words

    @Synchronized
    fun replaceAll(words: List<UserWord>) {
        val unique = words
            .filter { it.reading.isNotBlank() && it.surface.isNotBlank() }
            .distinctBy { it.reading to it.surface }
        val byReading = unique.groupBy({ it.reading }) { word ->
            val id = posId(word.pos)
            WordEntry(word.reading, word.surface, id, id, USER_WORD_COST)
        }
        snapshot = Snapshot(unique, byReading, unique.maxOfOrNull { it.reading.length } ?: 0)
    }

    @Synchronized
    fun add(word: UserWord) {
        val rest = words.filterNot { it.reading == word.reading && it.surface == word.surface }
        if (rest.size >= MAX_WORDS) throw TooManyWordsException(MAX_WORDS)
        replaceAll(rest + word)
    }

    @Synchronized
    fun remove(word: UserWord) = replaceAll(words - word)

    /** 既存の語に [imported] を加える。追加された語数を返す。上限を超えるときは何も変えずに例外を投げる。 */
    @Synchronized
    fun merge(imported: List<UserWord>): Int {
        val before = words.size
        val merged = (words + imported).distinctBy { it.reading to it.surface }
        if (merged.size > MAX_WORDS) throw TooManyWordsException(MAX_WORDS)
        replaceAll(merged)
        return words.size - before
    }

    override fun exact(reading: String): List<WordEntry> = snapshot.byReading[reading].orEmpty()

    override fun predict(prefix: String, limit: Int): List<WordEntry> {
        val current = snapshot
        return current.byReading.asSequence()
            .filter { (reading, _) -> reading.length > prefix.length && reading.startsWith(prefix) }
            .flatMap { it.value }
            .take(limit)
            .toList()
    }

    override val maxReadingLength: Int get() = snapshot.maxLength

    companion object {
        /** 登録できる語数の上限。Mozc の 1 辞書あたりの上限より小さく、端末のメモリに収まる大きさ。 */
        const val MAX_WORDS = 100_000

        private const val USER_WORD_COST = 2000
    }
}
