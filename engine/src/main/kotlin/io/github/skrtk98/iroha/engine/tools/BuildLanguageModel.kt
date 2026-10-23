package io.github.skrtk98.iroha.engine.tools

import io.github.skrtk98.iroha.engine.lm.LanguageModelBuilder
import java.io.File
import kotlin.system.exitProcess

/**
 * コーパスから iroha.lm を作るコマンド。コーパスは 1 行 1 文のテキストで、タブを含む行は最後の列を文とする。
 *
 * 使い方: BuildLanguageModel <出力ファイル> <3-gram の最小出現数> <コーパス>...
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("usage: BuildLanguageModel <output> <min-trigram-count> <corpus>...")
        exitProcess(2)
    }
    val output = File(args[0])
    val builder = LanguageModelBuilder(minTrigramCount = args[1].toInt())
    for (path in args.drop(2)) {
        File(path).useLines { lines -> lines.forEach { builder.add(it.substringAfterLast('\t')) } }
    }
    output.absoluteFile.parentFile.mkdirs()
    val temp = File(output.absoluteFile.parentFile, "${output.name}.part")
    temp.outputStream().use(builder::write)
    check(temp.renameTo(output)) { "rename failed: $output" }
    println("wrote $output (${output.length()} bytes)")
}
