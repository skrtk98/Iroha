package io.github.skrtk98.iroha.engine.conv

import io.github.skrtk98.iroha.engine.dict.WordEntry

/** システム辞書以外の語の供給元（ユーザー辞書・学習結果）。実装はスレッドセーフであること。 */
interface WordSource {
    /** 読みが完全一致する語。 */
    fun exact(reading: String): List<WordEntry>

    /** 読みが [prefix] で始まる語。 */
    fun predict(prefix: String, limit: Int): List<WordEntry>

    /** 登録されている最長の読みの文字数。 */
    val maxReadingLength: Int
}
