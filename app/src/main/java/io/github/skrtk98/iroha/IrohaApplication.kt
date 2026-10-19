package io.github.skrtk98.iroha

import android.app.Application
import io.github.skrtk98.iroha.data.ClipboardRepository
import io.github.skrtk98.iroha.data.EngineRepository
import io.github.skrtk98.iroha.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** IME サービスと設定画面で共有する依存をプロセス単位で持つ。 */
class IrohaApplication : Application() {
    /** プロセスと同じ寿命の処理（辞書の保存など）に使う。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { Settings(this) }
    val engineRepository by lazy { EngineRepository(this, appScope) }
    val clipboardRepository by lazy { ClipboardRepository(this, appScope, settings) }
}
