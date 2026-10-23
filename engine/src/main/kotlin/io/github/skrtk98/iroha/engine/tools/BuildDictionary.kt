package io.github.skrtk98.iroha.engine.tools

import io.github.skrtk98.iroha.engine.dict.DictionaryBuilder
import io.github.skrtk98.iroha.engine.dict.MozcImporter
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.system.exitProcess

/**
 * ビルド時に mozc の辞書から iroha.dic を作るコマンド。
 *
 * 使い方: BuildDictionary <mozc キャッシュディレクトリ> <出力ファイル> <自立語の最大コスト>
 * キャッシュに無いファイルは固定リビジョンから取得する。キャッシュ済みのものも含め、
 * すべて SHA-256 を照合してから使う。
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
        target.parentFile.mkdirs()
        val expected = MozcImporter.FILE_SHA256.getValue(name)
        if (target.exists()) {
            check(sha256(target) == expected) {
                "$target does not match the pinned SHA-256; delete it to download again"
            }
            continue
        }
        println("downloading $name")
        val temp = File(target.parentFile, "${target.name}.part")
        URI(MozcImporter.rawUrl(name)).toURL().openStream().use { input ->
            Files.copy(input, temp.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        val actual = sha256(temp)
        if (actual != expected) {
            temp.delete()
            error("SHA-256 mismatch for $name: expected $expected, got $actual")
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

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
