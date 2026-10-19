package io.github.skrtk98.iroha.engine

import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** engine:test が渡す実辞書を一度だけ mmap する。無ければ null。 */
object TestDictionary {
    val instance: SystemDictionary? by lazy {
        val path = System.getProperty("iroha.dictionary") ?: return@lazy null
        if (!File(path).exists()) return@lazy null
        RandomAccessFile(path, "r").use { file ->
            SystemDictionary(file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length()))
        }
    }
}
