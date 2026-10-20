package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.dict.UserPos

/**
 * Google 日本語入力 / Mozc 互換のユーザー辞書 TSV。
 * 1 行 1 語で「よみ<TAB>単語<TAB>品詞<TAB>コメント」。# で始まる行は無視する。
 */
object UserDictionaryTsv {

    /** よみ・単語の最大文字数。これを超える行は読み飛ばす。 */
    const val MAX_FIELD_LENGTH = 100

    /** コメントの最大文字数。超えた分は切り捨てる。 */
    const val MAX_COMMENT_LENGTH = 200

    data class ParseResult(val words: List<UserWord>, val skippedLines: Int)

    fun parse(text: String, maxWords: Int = UserDictionary.MAX_WORDS): ParseResult =
        parse(text.lineSequence(), maxWords)

    /**
     * 1 行ずつ読む。語数が [maxWords] を超えた時点で [TooManyWordsException] を投げ、
     * それ以上は読まない。巨大なファイルでもメモリを使い切らないようにするため。
     */
    fun parse(lines: Sequence<String>, maxWords: Int = UserDictionary.MAX_WORDS): ParseResult {
        val words = ArrayList<UserWord>()
        var skipped = 0
        for ((index, rawLine) in lines.withIndex()) {
            val line = (if (index == 0) rawLine.removePrefix("\uFEFF") else rawLine).trimEnd('\r')
            if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) continue
            // 行全体が極端に長いときは split で大きな配列を作る前に捨てる
            if (line.length > MAX_LINE_LENGTH) {
                skipped++
                continue
            }
            val fields = line.split('\t')
            if (fields.size < 2 || fields[0].isBlank() || fields[1].isBlank() ||
                fields[0].trim().length > MAX_FIELD_LENGTH || fields[1].trim().length > MAX_FIELD_LENGTH
            ) {
                skipped++
                continue
            }
            if (words.size >= maxWords) throw TooManyWordsException(maxWords)
            words.add(
                UserWord(
                    reading = fields[0].trim(),
                    surface = fields[1].trim(),
                    pos = UserPos.fromLabel(fields.getOrElse(2) { "" }.trim()),
                    comment = fields.getOrElse(3) { "" }.trim().take(MAX_COMMENT_LENGTH),
                ),
            )
        }
        return ParseResult(words, skipped)
    }

    private const val MAX_LINE_LENGTH = 4096

    fun format(words: List<UserWord>): String = buildString {
        for (word in words) {
            append(word.reading).append('\t')
            append(word.surface).append('\t')
            append(word.pos.label).append('\t')
            append(word.comment.replace('\t', ' ').replace('\n', ' '))
            append('\n')
        }
    }
}

/** 語数がユーザー辞書の上限を超えた。 */
class TooManyWordsException(val limit: Int) : IllegalStateException("too many words (limit $limit)")
