package io.github.skrtk98.iroha.engine.dict

/** 辞書の 1 語。lid/rid は mozc の品詞 ID、cost は小さいほど出やすい。 */
data class WordEntry(
    val reading: String,
    val surface: String,
    val lid: Int,
    val rid: Int,
    val cost: Int,
)
