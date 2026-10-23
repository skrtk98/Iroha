package io.github.skrtk98.iroha.engine.dict

/**
 * 文節の区切りと、文節の端に来る語のコスト。mozc の segmenter.def と boundary.def に当たる。
 *
 * 区切りの表は、左の語の rid と右の語の lid をそれぞれ同じ振る舞いの組（クラス）にまとめて小さくする。
 */
class SegmentRules(
    /** rid ごとのクラス。 */
    val leftClass: IntArray,
    /** lid ごとのクラス。 */
    val rightClass: IntArray,
    val rightClassCount: Int,
    /** leftClass × rightClassCount + rightClass の位置が、そこで文節を区切るか。 */
    val boundary: BooleanArray,
    /** 文節の先頭に来る語に足すコスト。lid ごと。 */
    val prefixPenalty: IntArray,
    /** 文節の末尾に来る語に足すコスト。rid ごと。 */
    val suffixPenalty: IntArray,
) {
    val leftClassCount: Int get() = boundary.size / rightClassCount

    init {
        require(leftClass.size == rightClass.size && prefixPenalty.size == leftClass.size && suffixPenalty.size == leftClass.size)
        require(rightClassCount > 0 && boundary.size % rightClassCount == 0)
    }

    /** rid の語と lid の語の間で文節を区切るか。 */
    fun isBoundary(rid: Int, lid: Int): Boolean {
        if (rid >= leftClass.size || lid >= rightClass.size) return true
        return boundary[leftClass[rid] * rightClassCount + rightClass[lid]]
    }

    companion object {
        /**
         * 品詞の大分類だけから決める規則。自立語・記号の手前で区切り、付属語は前の文節に、
         * 接頭辞の次の語は接頭辞の文節に付ける。端のコストは付けない。
         */
        fun fromPosClasses(classes: List<PosClass>): SegmentRules {
            val kinds = PosClass.entries
            val boundary = BooleanArray(kinds.size * kinds.size) { index ->
                val left = kinds[index / kinds.size]
                when (kinds[index % kinds.size]) {
                    PosClass.Functional -> left == PosClass.Symbol
                    PosClass.Content, PosClass.Prefix -> left != PosClass.Prefix
                    PosClass.Symbol -> true
                }
            }
            val ids = IntArray(classes.size) { classes[it].ordinal }
            return SegmentRules(ids, ids.copyOf(), kinds.size, boundary, IntArray(classes.size), IntArray(classes.size))
        }

        /**
         * mozc の規則から作る。[segmenterLines] は「左の品詞 右の品詞 true|false」の行で、上から最初に
         * 当てはまった行が決める。どれにも当たらなければ区切る。ID 0 (文頭・文末) の前後は必ず区切る。
         * [boundaryLines] は「PREFIX|SUFFIX 品詞 コスト」の行で、最初に当てはまった行のコストを使う。
         */
        fun fromMozc(posNames: List<String>, segmenterLines: List<String>, boundaryLines: List<String>): SegmentRules {
            val rules = segmenterLines.map { line ->
                val (left, right, result) = line.trim().split(Regex("\\s+"))
                Triple(segmenterPattern(left), segmenterPattern(right), result.lowercase() == "true")
            }
            // 当てはまる規則の集まりが同じ品詞は、区切り方も同じなので 1 つのクラスにまとめる
            fun classes(side: (Triple<Regex?, Regex?, Boolean>) -> Regex?): Pair<IntArray, List<Int>> {
                val signatures = LinkedHashMap<List<Boolean>, Int>()
                val representatives = ArrayList<Int>()
                val ids = IntArray(posNames.size) { id ->
                    val signature = if (id == 0) listOf() else rules.map { rule -> side(rule).matchesStart(posNames[id]) }
                    signatures.getOrPut(signature) { representatives.add(id); signatures.size }
                }
                return ids to representatives
            }
            val (leftClass, leftReps) = classes { it.first }
            val (rightClass, rightReps) = classes { it.second }
            val boundary = BooleanArray(leftReps.size * rightReps.size) { index ->
                val rid = leftReps[index / rightReps.size]
                val lid = rightReps[index % rightReps.size]
                if (rid == 0 || lid == 0) {
                    true
                } else {
                    rules.firstOrNull { (l, r, _) -> l.matchesStart(posNames[rid]) && r.matchesStart(posNames[lid]) }?.third ?: true
                }
            }
            val penalties = boundaryLines.map { line ->
                val (label, feature, cost) = line.trim().split(Regex("\\s+"))
                Triple(label, boundaryPattern(feature), cost.toInt())
            }
            fun penalty(label: String, name: String) =
                penalties.firstOrNull { (l, pattern, _) -> l == label && pattern.containsMatchIn(name) }?.third ?: 0
            return SegmentRules(
                leftClass,
                rightClass,
                rightReps.size,
                boundary,
                IntArray(posNames.size) { penalty("PREFIX", posNames[it]) },
                IntArray(posNames.size) { penalty("SUFFIX", posNames[it]) },
            )
        }

        /** segmenter.def の品詞の型。* だけなら何にでも当たる (null)。それ以外は先頭から当てる。 */
        private fun segmenterPattern(pattern: String): Regex? =
            if (pattern == "*") null else Regex(pattern.replace("*", "[^,]+"))

        private fun Regex?.matchesStart(name: String): Boolean =
            this == null || toPattern().matcher(name).lookingAt()

        /** boundary.def の品詞の型。先頭から当て、末尾が , でなければ項目の切れ目で終わらせる。 */
        private fun boundaryPattern(pattern: String): Regex {
            var regex = "^" + pattern.replace("*", "[^,]+")
            if (!regex.endsWith(",")) regex += "(?:,|$)"
            return Regex(regex)
        }
    }
}
