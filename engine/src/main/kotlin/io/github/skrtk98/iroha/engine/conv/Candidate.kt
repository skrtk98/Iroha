package io.github.skrtk98.iroha.engine.conv

/** 変換候補。[lid]/[rid] は候補全体の左端・右端の品詞 ID。 */
data class Candidate(
    val surface: String,
    val reading: String,
    val cost: Int,
    val lid: Int,
    val rid: Int,
)

/** 文節。[candidates] の先頭が最良の候補。 */
data class Segment(
    val reading: String,
    val candidates: List<Candidate>,
)
