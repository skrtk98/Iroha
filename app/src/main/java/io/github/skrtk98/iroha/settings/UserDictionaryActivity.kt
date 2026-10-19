package io.github.skrtk98.iroha.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.skrtk98.iroha.IrohaApplication
import io.github.skrtk98.iroha.R
import io.github.skrtk98.iroha.data.EngineRepository
import io.github.skrtk98.iroha.engine.dict.UserPos
import io.github.skrtk98.iroha.engine.text.Kana
import io.github.skrtk98.iroha.engine.user.UserDictionary
import io.github.skrtk98.iroha.engine.user.UserDictionaryTsv
import io.github.skrtk98.iroha.engine.user.UserWord
import io.github.skrtk98.iroha.ui.theme.IrohaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ユーザー辞書の一覧・追加・削除と、Google 日本語入力 / Mozc 互換 TSV の読み込み・書き出し。
 */
class UserDictionaryActivity : ComponentActivity() {
    private val repository: EngineRepository get() = (application as IrohaApplication).engineRepository

    private var words by mutableStateOf<List<UserWord>>(emptyList())
    private var message by mutableStateOf<String?>(null)

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importFrom(uri)
    }
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri ->
            if (uri != null) exportTo(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repository.load()
            words = repository.userWords()
        }
        setContent {
            IrohaTheme {
                UserDictionaryScreen(
                    words = words,
                    message = message,
                    onMessageShown = { message = null },
                    onAdd = { word -> update { it.add(word) } },
                    onDelete = { word -> update { it.remove(word) } },
                    onImport = { importLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                    onExport = { exportLauncher.launch("iroha_user_dictionary.txt") },
                    onBack = ::finish,
                )
            }
        }
    }

    private fun update(block: (UserDictionary) -> Unit) {
        lifecycleScope.launch {
            repository.updateUserDictionary(block)
            words = repository.userWords()
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                } ?: error("ファイルを開けません")
                UserDictionaryTsv.parse(text)
            }
            result.onSuccess { parsed ->
                var added = 0
                repository.updateUserDictionary { added = it.merge(parsed.words) }
                words = repository.userWords()
                message = buildString {
                    append("${added} 語を追加しました")
                    if (parsed.skippedLines > 0) append("（読めない行 ${parsed.skippedLines} 行）")
                }
            }.onFailure { message = "読み込みに失敗しました: ${it.message}" }
        }
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            val text = "# Iroha user dictionary (Google 日本語入力 / Mozc 互換)\n" + UserDictionaryTsv.format(words)
            runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                        ?: error("ファイルを作れません")
                }
            }.onSuccess { message = "${words.size} 語を書き出しました" }
                .onFailure { message = "書き出しに失敗しました: ${it.message}" }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDictionaryScreen(
    words: List<UserWord>,
    message: String?,
    onMessageShown: () -> Unit,
    onAdd: (UserWord) -> Unit,
    onDelete: (UserWord) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    var adding by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("ユーザー辞書") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(onClick = onImport) { Text("読み込み") }
                    TextButton(onClick = onExport) { Text("書き出し") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Text("追加", Modifier.padding(horizontal = 16.dp)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (words.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "登録した語はありません。\n「追加」で登録するか、Google 日本語入力などから書き出した辞書を読み込めます。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                items(words, key = { it.reading + "\t" + it.surface }) { word ->
                    WordRow(word, onDelete = { onDelete(word) })
                    HorizontalDivider()
                }
            }
        }
    }
    if (adding) {
        AddWordDialog(onDismiss = { adding = false }, onAdd = { onAdd(it); adding = false })
    }
}

@Composable
private fun WordRow(word: UserWord, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(word.surface, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${word.reading} ・ ${word.pos.label}" + if (word.comment.isNotEmpty()) " ・ ${word.comment}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), contentDescription = "${word.surface} を削除")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWordDialog(onDismiss: () -> Unit, onAdd: (UserWord) -> Unit) {
    var reading by rememberSaveable { mutableStateOf("") }
    var surface by rememberSaveable { mutableStateOf("") }
    var pos by rememberSaveable { mutableStateOf(UserPos.Noun) }
    var expanded by remember { mutableStateOf(false) }
    // 読みはひらがなで照合するので、カタカナで入力されても揃える
    val normalizedReading = Kana.toHiragana(reading.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("語を追加") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(reading, { reading = it }, label = { Text("よみ（ひらがな）") }, singleLine = true)
                OutlinedTextField(surface, { surface = it }, label = { Text("単語") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = pos.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("品詞") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (p in UserPos.entries) {
                            DropdownMenuItem(text = { Text(p.label) }, onClick = { pos = p; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalizedReading.isNotEmpty() && surface.isNotBlank(),
                onClick = { onAdd(UserWord(normalizedReading, surface.trim(), pos)) },
            ) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
    )
}
