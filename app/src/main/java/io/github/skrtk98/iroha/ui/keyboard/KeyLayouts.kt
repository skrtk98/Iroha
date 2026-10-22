package io.github.skrtk98.iroha.ui.keyboard

import androidx.compose.runtime.Immutable

/** フリック方向。 */
enum class FlickDirection { Center, Left, Up, Right, Down }

/** フリックキー 1 個。方向ごとの文字を持ち、無い方向は null。[hint] はラベルの下に小さく出す補足。 */
@Immutable
data class FlickKey(
    val label: String,
    val center: String,
    val left: String? = null,
    val up: String? = null,
    val right: String? = null,
    val down: String? = null,
    val hint: String? = null,
) {
    fun output(direction: FlickDirection): String? = when (direction) {
        FlickDirection.Center -> center
        FlickDirection.Left -> left
        FlickDirection.Up -> up
        FlickDirection.Right -> right
        FlickDirection.Down -> down
    }

    companion object {
        /** "あいうえお" のように中央・左・上・右・下の順で並べた文字列から作る。 */
        fun of(chars: String, label: String = chars.take(1)): FlickKey {
            val c = chars.map { it.toString() }
            return FlickKey(label, c[0], c.getOrNull(1), c.getOrNull(2), c.getOrNull(3), c.getOrNull(4))
        }
    }
}

object KeyLayouts {
    /** 3 列 × 4 段の中央部。4 段目の左端は濁点・大文字切替キーなので含めない (11 個)。 */
    val flickKana: List<FlickKey> = listOf(
        FlickKey.of("あいうえお"), FlickKey.of("かきくけこ"), FlickKey.of("さしすせそ"),
        FlickKey.of("たちつてと"), FlickKey.of("なにぬねの"), FlickKey.of("はひふへほ"),
        FlickKey.of("まみむめも"), FlickKey("や", "や", "「", "ゆ", "」", "よ"), FlickKey.of("らりるれろ"),
        FlickKey("わ", "わ", "を", "ん", "ー"), FlickKey("、。?!", "、", "。", "？", "！", "…"),
    )

    val flickAlphabet: List<FlickKey> = listOf(
        FlickKey("@#/&_", "@", "#", "/", "&", "_"), FlickKey.of("abc", "abc"), FlickKey.of("def", "def"),
        FlickKey.of("ghi", "ghi"), FlickKey.of("jkl", "jkl"), FlickKey.of("mno", "mno"),
        FlickKey.of("pqrs", "pqrs"), FlickKey.of("tuv", "tuv"), FlickKey.of("wxyz", "wxyz"),
        FlickKey("'\"()", "'", "\"", "(", ")"), FlickKey(".,?!", ".", ",", "?", "!", "-"),
    )

    /** 数字・記号のテンキー。数字をそのまま押すと数字、フリックすると記号になる。 */
    val flickNumber: List<FlickKey> = listOf(
        numberKey("1", "☆", "♪", "→"), numberKey("2", "¥", "$", "€"), numberKey("3", "%", "°", "#"),
        numberKey("4", "○", "*", "・"), numberKey("5", "+", "×", "÷"), numberKey("6", "<", "=", ">"),
        numberKey("7", "「", "」", ":"), numberKey("8", "〒", "々", "〆"), numberKey("9", "^", "|", "\\"),
        numberKey("0", "〜", "…", "~"), FlickKey(".,-/", ".", ",", "-", "/"),
    )

    /** 数字テンキーの左下。括弧をまとめる。 */
    val flickNumberBrackets = FlickKey("()[]", "(", ")", "[", "]")

    private fun numberKey(digit: String, left: String, up: String, right: String) =
        FlickKey(digit, digit, left, up, right, hint = left + up + right)

    val qwertyRows: List<String> = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    /** QWERTY の数字・記号の面。3 段目の左端は 2 ページ目への切り替えキー。 */
    val symbolRows: List<List<String>> = listOf(
        "1234567890".map { it.toString() },
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?", "="),
    )

    /** 数字・記号の面の 2 ページ目。1 ページ目に無い記号と、日本語でよく使う記号。 */
    val moreSymbolRows: List<List<String>> = listOf(
        listOf("~", "`", "|", "・", "√", "π", "÷", "×", "§", "Δ"),
        listOf("£", "€", "¥", "¢", "^", "°", "※", "{", "}", "\\"),
        listOf("_", "[", "]", "「", "」", "…", "〜", "✓"),
    )
}
