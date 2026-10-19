package io.github.skrtk98.iroha.engine.tools

import io.github.skrtk98.iroha.engine.dict.DictionaryBuilder
import io.github.skrtk98.iroha.engine.dict.MozcImporter
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

/**
 * ビルド時に mozc の辞書から iroha.dic を作るコマンド。
 *
 * 使い方: BuildDictionary <mozc キャッシュディレクトリ> <出力ファイル> <自立語の最大コスト>
 * キャッシュに無いファイルは固定リビジョンから取得する。
 */
fun main(args: Array<String>) {
    if (args.size != 3) {
        System.err.println("usage: BuildDictionary <mozc-cache-dir> <output> <max-content-cost>")
        exitProcess(2)
    }
    val cacheDir = File(args[0], MozcImporter.MOZC_COMMIT)
    val output = File(args[1])
    val maxCost = args[2].toInt()

    cacheDir.mkdirs()
    for (name in MozcImporter.REQUIRED_FILES) {
        val target = File(cacheDir, name)
        if (target.exists()) continue
        println("downloading $name")
        val temp = File(cacheDir, "$name.part")
        URI(MozcImporter.rawUrl(name)).toURL().openStream().use { input ->
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    val importer = MozcImporter(cacheDir)
    val entries = importer.readEntries(maxCost)
    val (matrix, dim) = importer.readMatrix()
    output.parentFile?.mkdirs()
    output.outputStream().use { out ->
        DictionaryBuilder(importer.posClasses, matrix, dim, importer.unknownNounId, importer.userPosIds).write(entries, out)
    }
    println("wrote ${output.length()} bytes, ${entries.size} entries")
}
