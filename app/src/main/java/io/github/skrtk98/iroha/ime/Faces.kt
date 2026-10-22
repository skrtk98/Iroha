package io.github.skrtk98.iroha.ime

import io.github.skrtk98.iroha.data.KeyboardLayout

/**
 * 面の切り替え規則。日英（かな・英字）と数字・記号は、それぞれ独立にフリックか QWERTY かを選べる。
 */
object Faces {
    fun isSymbol(face: KeyboardFace) = face == KeyboardFace.FlickNumber || face == KeyboardFace.QwertySymbols

    fun isAlphabet(face: KeyboardFace) = face == KeyboardFace.FlickAlphabet || face == KeyboardFace.QwertyAlphabet

    fun layoutOf(face: KeyboardFace): KeyboardLayout = when (face) {
        KeyboardFace.FlickKana, KeyboardFace.FlickAlphabet, KeyboardFace.FlickNumber -> KeyboardLayout.Flick
        KeyboardFace.QwertyKana, KeyboardFace.QwertyAlphabet, KeyboardFace.QwertySymbols -> KeyboardLayout.Qwerty
    }

    fun text(layout: KeyboardLayout, alphabet: Boolean): KeyboardFace = when (layout) {
        KeyboardLayout.Flick -> if (alphabet) KeyboardFace.FlickAlphabet else KeyboardFace.FlickKana
        KeyboardLayout.Qwerty -> if (alphabet) KeyboardFace.QwertyAlphabet else KeyboardFace.QwertyKana
    }

    fun symbol(layout: KeyboardLayout): KeyboardFace = when (layout) {
        KeyboardLayout.Flick -> KeyboardFace.FlickNumber
        KeyboardLayout.Qwerty -> KeyboardFace.QwertySymbols
    }

    /** 同じ種類（日英なら かな・英字の別も）を保ったまま、もう一方の配列にした面。 */
    fun otherLayout(face: KeyboardFace): KeyboardFace {
        val other = if (layoutOf(face) == KeyboardLayout.Flick) KeyboardLayout.Qwerty else KeyboardLayout.Flick
        return if (isSymbol(face)) symbol(other) else text(other, isAlphabet(face))
    }
}
