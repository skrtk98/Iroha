package io.github.skrtk98.iroha.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.skrtk98.iroha.ui.theme.IrohaTheme

/** assets/licenses 以下のライセンス文を並べて表示する。 */
class LicensesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val texts = assets.list(DIR).orEmpty().sorted().map { name ->
            name.removeSuffix(".txt") to assets.open("$DIR/$name").bufferedReader().use { it.readText() }
        }
        setContent { IrohaTheme { LicensesScreen(texts) } }
    }

    private companion object {
        const val DIR = "licenses"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicensesScreen(texts: List<Pair<String, String>>) {
    Scaffold(topBar = { TopAppBar(title = { Text("ライセンス") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            for ((title, body) in texts) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
