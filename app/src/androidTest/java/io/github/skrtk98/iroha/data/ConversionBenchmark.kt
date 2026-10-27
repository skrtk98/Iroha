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
 * 測るのは読み込み済みの変換 1 回の処理時間で、IME を開いてからの読み込みや、打鍵が重なったときの待ちは含まない。
 *
 * 実行: connectedDebugAndroidTest は終了後にアプリをアンインストールし、端末の辞書と学習結果を消すので使わない。
 * debug と androidTest の APK を adb install -r で入れ、am instrument で走らせて adb logcat -s IrohaBench を見る
 * (手順は docs/release.md)。
 * debug ビルドはデバッグ可能なので、実際より遅く出る。実際に近い値を見るときは、
 * 一時的に buildTypes の debug に isDebuggable = false を足して測る。
 */
class ConversionBenchmark {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun mapAsset(name: String) = context.assets.openFd(name).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.length)
        }
    }

    private class Subject(val label: String, val converter: Converter) {
        val times = ArrayList<Long>()
        val byLength = sortedMapOf<Int, MutableList<Long>>()
    }

    @Test
    fun keystrokeLatency() {
        val dictionary = SystemDictionary(mapAsset("iroha.dic"))
        val loadStart = SystemClock.elapsedRealtimeNanos()
        val languageModel = LanguageModel(mapAsset("iroha.lm"))
        log("language model load: ${ms(SystemClock.elapsedRealtimeNanos() - loadStart)} ms")
        val subjects = listOf(
            Subject("with model", Converter(dictionary, languageModel = languageModel)),
            Subject("without model", Converter(dictionary)),
        )

        // JIT 前の 1 回目。アプリは言語モデルありで動くので、そちらを先に測る
        val coldStart = SystemClock.elapsedRealtimeNanos()
        subjects[0].converter.suggest(READINGS.last())
        log("with model: first call ${ms(SystemClock.elapsedRealtimeNanos() - coldStart)} ms")
        repeat(WARMUP_ROUNDS) {
            for (s in subjects) READINGS.forEach { r -> r.indices.forEach { s.converter.suggest(r.substring(0, it + 1)) } }
        }

        // 順序の影響を除くため、打鍵ごとに両方を交互に、毎回順を入れ替えて測る
        var flip = false
        repeat(ROUNDS) {
            for (reading in READINGS) {
                for (end in 1..reading.length) {
                    val prefix = reading.substring(0, end)
                    for (s in if (flip) subjects.reversed() else subjects) {
                        val start = SystemClock.elapsedRealtimeNanos()
                        s.converter.suggest(prefix)
                        val t = SystemClock.elapsedRealtimeNanos() - start
                        s.times += t
                        s.byLength.getOrPut((end - 1) / 10) { ArrayList() } += t
                    }
                    flip = !flip
                }
            }
        }
        for (s in subjects) {
            s.times.sort()
            log("${s.label}: keystrokes=${s.times.size} median=${ms(pct(s.times, 50))} p95=${ms(pct(s.times, 95))} max=${ms(s.times.last())} ms")
            for ((bucket, list) in s.byLength) {
                list.sort()
                log("${s.label}:   reading ${bucket * 10 + 1}-${bucket * 10 + 10} chars n=${list.size} median=${ms(pct(list, 50))} p95=${ms(pct(list, 95))} ms")
            }
        }
        for (reading in READINGS.takeLast(2)) {
            for (s in subjects) {
                val samples = (1..ROUNDS * 3).map {
                    val start = SystemClock.elapsedRealtimeNanos()
                    s.converter.convert(reading)
                    SystemClock.elapsedRealtimeNanos() - start
                }.sorted()
                log("${s.label}: convert ${reading.length} chars median=${ms(pct(samples, 50))} ms")
            }
        }
    }

    /** 昇順の [sorted] の p パーセンタイル (nearest-rank: 全体の p% 以上がこの値以下に収まる最小の値)。 */
    private fun pct(sorted: List<Long>, p: Int) = sorted[maxOf(0, (sorted.size * p + 99) / 100 - 1)]

    private fun ms(nanos: Long) = "%.1f".format(nanos / 1e6)

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    private companion object {
        const val TAG = "IrohaBench"
        const val WARMUP_ROUNDS = 3
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
