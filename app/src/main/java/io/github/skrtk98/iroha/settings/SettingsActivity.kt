package io.github.skrtk98.iroha.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.skrtk98.iroha.IrohaApplication
import io.github.skrtk98.iroha.ui.theme.IrohaTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as IrohaApplication
        setContent {
            IrohaTheme {
                SettingsRoute(
                    settings = app.settings,
                    onOpenInputMethodSettings = {
                        startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onShowInputMethodPicker = {
                        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
                    },
                    onOpenLicenses = { startActivity(Intent(this, LicensesActivity::class.java)) },
                )
            }
        }
    }
}
