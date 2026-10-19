package io.github.skrtk98.iroha.engine.dict

/** 文節区切りの判定に使う品詞の大分類。 */
enum class PosClass {
    /** 文節の先頭になれる自立語。 */
    Content,

    /** 前の語に付属する語（助詞・助動詞・接尾辞・非自立語）。 */
    Functional,

    /** 後ろの語に付く接頭辞。 */
    Prefix,

    /** 記号。単独で文節になる。 */
    Symbol,
    ;

    companion object {
        /** mozc の id.def の品詞名から分類する。 */
        fun fromMozcPosName(name: String): PosClass {
            val fields = name.split(',')
            val major = fields.getOrElse(0) { "" }
            val minor = fields.getOrElse(1) { "" }
            return when {
                major == "助詞" || major == "助動詞" -> Functional
                minor == "接尾" || minor == "非自立" -> Functional
                major == "接頭詞" -> Prefix
                major == "記号" -> Symbol
                else -> Content
            }
        }
    }
}
