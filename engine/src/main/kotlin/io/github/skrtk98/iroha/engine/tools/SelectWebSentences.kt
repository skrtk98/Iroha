package io.github.skrtk98.iroha.engine.tools

import io.github.skrtk98.iroha.engine.lm.LanguageModel
import io.github.skrtk98.iroha.engine.lm.LanguageModelBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.system.exitProcess

/**
 * ウェブの文から、書き言葉のコーパスに近い文を選ぶコマンド (Moore-Lewis 法)。
 * 書き言葉のコーパスで作ったモデルと、ウェブの文の一部で作った同じくらいの大きさのモデルで各文を採点し、
 * 1 文字あたりの「書き言葉らしさ」の高い順に、合計の文字数が上限に達するまで選ぶ。選んだ文は元の順で書く。
 *
 * 使い方: SelectWebSentences <出力ファイル> <文字数の上限> <書き言葉のコーパス> <ウェブの文>
 */
fun main(args: Array<String>) {
    if (args.size != 4) {
        System.err.println("usage: SelectWebSentences <output> <max-chars> <in-domain-corpus> <web-sentences>")
        exitProcess(2)
    }
    val output = File(args[0])
    val maxChars = args[1].toLong()
    val inDomainFile = File(args[2])
    val webFile = File(args[3])

    val inDomain = LanguageModelBuilder()
    var inDomainChars = 0L
    inDomainFile.useLines { lines ->
        lines.forEach {
            val s = it.substringAfterLast('\t')
            inDomain.add(s)
            inDomainChars += s.length
        }
    }
    // 比べる側のモデルは、ウェブの文から書き言葉のコーパスと同じくらいの量を選んで作る。
    // 乱数ではなく文の指紋で選び、何度作り直しても同じ文になるようにする
    var webChars = 0L
    var webLines = 0
    webFile.useLines { lines ->
        lines.forEach {
            webChars += it.length
            webLines++
        }
    }
    val threshold = (inDomainChars.toDouble() / webChars * (1L shl 32)).toLong()
    val general = LanguageModelBuilder()
    webFile.useLines { lines -> lines.filter { fingerprint(it) < threshold }.forEach(general::add) }
    val inDomainModel = inDomain.toModel()
    val generalModel = general.toModel()

    // 点数の低い (書き言葉らしい) 順に並べる。点数が同じなら前の文から。
    // 上位 32 ビットを点数、下位 32 ビットを文の番号にした値を並べ替える
    val keys = LongArray(webLines)
    val lengths = IntArray(webLines)
    webFile.useLines { lines ->
        lines.forEachIndexed { i, s ->
            val score = if (s.isEmpty()) Float.MAX_VALUE else (inDomainModel.negLogProb("", s) - generalModel.negLogProb("", s)) / s.length
            keys[i] = (sortable(score).toLong() shl 32) or i.toLong()
            lengths[i] = s.length
        }
    }
    keys.sort()
    val selected = BooleanArray(webLines)
    var total = 0L
    var count = 0
    for (key in keys) {
        if (total >= maxChars) break
        val i = key.toInt()
        selected[i] = true
        total += lengths[i]
        count++
    }

    output.absoluteFile.parentFile.mkdirs()
    val temp = File(output.absoluteFile.parentFile, "${output.name}.part")
    temp.bufferedWriter().use { out ->
        webFile.useLines { lines ->
            lines.forEachIndexed { i, s ->
                if (selected[i]) {
                    out.write(s)
                    out.write("\n")
                }
            }
        }
    }
    check(temp.renameTo(output)) { "rename failed: $output" }
    println("selected $count of $webLines sentences ($total chars) into $output")
}

private fun LanguageModelBuilder.toModel(): LanguageModel =
    LanguageModel(ByteBuffer.wrap(ByteArrayOutputStream().also(::write).toByteArray()))

/** 大小の順が [Float] と同じになる [Int]。 */
private fun sortable(f: Float): Int {
    val bits = f.toRawBits()
    return if (bits < 0) bits xor Int.MAX_VALUE else bits
}

/** 文の 32 ビットの指紋 (FNV-1a)。符号なしの値で返す。 */
private fun fingerprint(s: String): Long {
    var h = 0x811C9DC5.toInt()
    for (c in s) {
        h = (h xor c.code) * 0x01000193
    }
    return h.toLong() and 0xFFFFFFFFL
}
