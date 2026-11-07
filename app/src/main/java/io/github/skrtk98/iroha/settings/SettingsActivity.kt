package io.github.skrtk98.iroha.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.skrtk98.iroha.IrohaApplication
import io.github.skrtk98.iroha.ui.theme.IrohaTheme

class SettingsActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
    }
    private var micGranted by mutableStateOf(false)
    private var setup by mutableStateOf(SetupState(enabled = false, selected = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as IrohaApplication
        micGranted = hasMicPermission()
        setup = readSetup()
        val appVersion = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_REQUEST_MIC, false) && !micGranted) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        setContent {
            IrohaTheme {
                SettingsRoute(
                    settings = app.settings,
                    setup = setup,
                    appVersion = appVersion,
                    onOpenInputMethodSettings = {
                        startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onShowInputMethodPicker = {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                    onOpenUserDictionary = { startActivity(Intent(this, UserDictionaryActivity::class.java)) },
                    onClearLearning = { lifecycleScope.launch { app.engineRepository.clearLearning() } },
                    onOpenLicenses = { startActivity(Intent(this, LicensesActivity::class.java)) },
                    onClearClipboard = { app.clipboardRepository.clearAll() },
                    micGranted = micGranted,
                    onRequestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        micGranted = hasMicPermission()
        setup = readSetup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 入力方法の選択ダイアログは画面の上に出るだけで onResume を通らないので、閉じたときに読み直す
        if (hasFocus) setup = readSetup()
    }

    private fun readSetup(): SetupState {
        val enabled = getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == packageName }
        val current = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
        return SetupState(enabled, selected = current?.substringBefore('/') == packageName)
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** IME から開いたときにマイクの権限を求める。 */
        const val EXTRA_REQUEST_MIC = "request_mic"
    }
}
