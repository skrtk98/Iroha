package io.github.skrtk98.iroha.ime

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 設定画面から「高さの調整」を始める合図。設定画面と IME は同じプロセスで動くので、
 * 保存する必要のない一時的な要求はここで受け渡す。IME が受け取ったら false に戻す。
 */
object ResizeRequest {
    val pending = MutableStateFlow(false)
}
