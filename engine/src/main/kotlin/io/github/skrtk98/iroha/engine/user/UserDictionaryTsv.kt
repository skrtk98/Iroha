package io.github.skrtk98.iroha.engine.user

import io.github.skrtk98.iroha.engine.dict.UserPos

/**
 * Google 日本語入力 / Mozc 互換のユーザー辞書 TSV。
 * 1 行 1 語で「よみ<TAB>単語<TAB>品詞<TAB>コメント」。# で始まる行は無視する。
 */
object UserDictionaryTsv {

    data class ParseResult(val words: List<UserWord>, val skippedLines: Int)

    fun parse(text: String): ParseResult {
        val words = ArrayList<UserWord>()
        var skipped = 0
        for (rawLine in text.removePrefix("﻿").lineSequence()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank() || line.startsWith("#") || line.startsWith("!")) continue
            val fields = line.split('\t')
            if (fields.size < 2 || fields[0].isBlank() || fields[1].isBlank()) {
                skipped++
                continue
            }
            words.add(
                UserWord(
                    reading = fields[0].trim(),
                    surface = fields[1].trim(),
                    pos = UserPos.fromLabel(fields.getOrElse(2) { "" }.trim()),
                    comment = fields.getOrElse(3) { "" }.trim(),
                ),
            )
        }
        return ParseResult(words, skipped)
    }

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
