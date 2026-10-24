package io.github.skrtk98.iroha.data

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import io.github.skrtk98.iroha.engine.conv.Converter
import io.github.skrtk98.iroha.engine.dict.SystemDictionary
import io.github.skrtk98.iroha.engine.lm.LanguageModel
import org.junit.Test
import java.io.FileInputStream
import java.nio.channels.FileChannel

/**
 * 実機で 1 打鍵ごとの変換にかかる時間を測る。言語モデルあり・なしを並べて logcat (タグ IrohaBench) に出す。
 * IME は打鍵のたびに入力中の読み全体で suggest を呼ぶので、読みの前から 1 文字ずつ延ばして測る。
 *
 * 実行: ./gradlew :app:connectedDebugAndroidTest 後に adb logcat -s IrohaBench
 * debug ビルドはデバッグ可能なので、実際の速さより 2 倍ほど遅く出る。実際に近い値を見るときは、
 * 一時的に buildTypes の debug に isDebuggable = false を足して測る。
 */
class ConversionBenchmark {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun mapAsset(name: String) = context.assets.openFd(name).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
        }
    }

    @Test
    fun keystrokeLatency() {
        val dictionary = SystemDictionary(mapAsset("iroha.dic"))
        val loadStart = SystemClock.elapsedRealtimeNanos()
        val languageModel = LanguageModel(mapAsset("iroha.lm"))
        log("language model load: ${ms(SystemClock.elapsedRealtimeNanos() - loadStart)} ms")
        // 初回の呼び出しは JIT 前の値になる。IME を開いた直後の打鍵はこれに近い
        measure("with model", Converter(dictionary, languageModel = languageModel))
        measure("without model", Converter(dictionary))
    }

    private fun measure(label: String, converter: Converter) {
        val coldStart = SystemClock.elapsedRealtimeNanos()
        converter.suggest(READINGS.last())
        log("$label: first call ${ms(SystemClock.elapsedRealtimeNanos() - coldStart)} ms")
        repeat(WARMUP_ROUNDS) { READINGS.forEach { r -> r.indices.forEach { converter.suggest(r.substring(0, it + 1)) } } }

        val times = ArrayList<Long>()
        val byLength = sortedMapOf<Int, MutableList<Long>>()
        repeat(ROUNDS) {
            for (reading in READINGS) {
                for (end in 1..reading.length) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    converter.suggest(reading.substring(0, end))
                    val t = SystemClock.elapsedRealtimeNanos() - start
                    times += t
                    byLength.getOrPut((end - 1) / 10 * 10 + 10) { ArrayList() } += t
                }
            }
        }
        times.sort()
        log("$label: keystrokes=${times.size} median=${ms(pct(times, 50))} p95=${ms(pct(times, 95))} max=${ms(times.last())} ms")
        for ((upTo, list) in byLength) {
            list.sort()
            log("$label:   reading ≤$upTo chars median=${ms(pct(list, 50))} p95=${ms(pct(list, 95))} ms")
        }
        for (reading in READINGS.takeLast(2)) {
            val samples = (1..ROUNDS).map {
                val start = SystemClock.elapsedRealtimeNanos()
                converter.convert(reading)
                SystemClock.elapsedRealtimeNanos() - start
            }.sorted()
            log("$label: convert ${reading.length} chars median=${ms(pct(samples, 50))} ms")
        }
    }

    private fun pct(sorted: List<Long>, p: Int) = sorted[(sorted.size - 1) * p / 100]

    private fun ms(nanos: Long) = "%.1f".format(nanos / 1e6)

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "IrohaBench"
        const val WARMUP_ROUNDS = 2
        const val ROUNDS = 3
        val READINGS = listOf(
            "きょう",
            "ありがとうございます",
            "あしたはあめがふるらしい",
            "かいぎのじかんをへんこうしました",
            "えきまえのかふぇでまちあわせしよう",
            "しゅくだいをわすれないようにきをつけてね",
            "らいしゅうのどようびにえいがをみにいきませんか",
            "でんしゃがおくれているのでやくそくのじかんにおくれないようにします",
        )
    }
}
