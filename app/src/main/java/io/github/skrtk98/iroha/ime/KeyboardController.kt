package io.github.skrtk98.iroha.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import io.github.skrtk98.iroha.data.ClipboardRepository
import io.github.skrtk98.iroha.data.Engine
import io.github.skrtk98.iroha.data.Settings
import io.github.skrtk98.iroha.engine.conv.Candidate
import io.github.skrtk98.iroha.engine.session.ComposingSession
import io.github.skrtk98.iroha.engine.session.Preedit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** IME 本体が担う、キーボード以外のプラットフォーム操作。 */
interface ImeHost {
    fun openSettings()
    fun requestMicPermission()
    fun switchInputMethod()
    fun hideKeyboard()
}

/**
 * キー操作を受けて未確定文字列と候補を管理し、エディタに反映する。
 * IME サービスの寿命に合わせた [scope] の中で、メインスレッドから使う。
 */
class KeyboardController(
    private val scope: CoroutineScope,
    private val settings: Settings,
    private val editor: EditorBridge,
    private val host: ImeHost,
    private val clipboard: ClipboardRepository,
    private val voice: VoiceRecognizer,
) {
    private val _state = MutableStateFlow(KeyboardUiState(loading = true))
    val state: StateFlow<KeyboardUiState> = _state.asStateFlow()

    private var engine: Engine? = null
    private var session: ComposingSession? = null

    /** 予測候補を求める読み。最新の値だけを処理する。 */
    private val suggestionRequests = MutableStateFlow<String?>(null)
    private var suggestions: List<Candidate> = emptyList()

    /** エディタに未確定文字列を表示しているか。 */
    private var hasPreedit = false

    /** 直接入力（英字・数字・パスワード欄など）で変換を使わない。 */
    private var directInput = false

    init {
        scope.launch { collectSuggestions() }
        scope.launch { clipboard.entries.collect { clips -> _state.update { it.copy(clips = clips) } } }
        // 設定画面の試し入力欄で、見た目の変更をすぐ確かめられるようにする
        scope.launch { settings.changes.collect { _state.update { it.withAppearance() } } }
    }

    fun onEngineLoaded(engine: Engine) {
        this.engine = engine
        session = ComposingSession(engine.converter, if (settings.learningEnabled) engine.learning else null)
        _state.update { it.copy(loading = false) }
    }

    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        // restarting でも別の欄に移っていることがある（Compose の同一ビュー内のフォーカス移動など）ので必ず捨てる
        resetComposition()
        val inputType = info?.inputType ?: 0
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val layout = settings.defaultLayout
        val isNumeric = inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME
        val isAscii = inputClass == InputType.TYPE_CLASS_TEXT && variation in ASCII_VARIATIONS
        val textFace = Faces.text(layout, alphabet = isAscii)
        // 数値欄は数字の打ちやすいテンキー（フリック）にする
        val face = if (isNumeric) KeyboardFace.FlickNumber else textFace
        directInput = isNumeric || isAscii
        _state.update {
            it.copy(
                face = face,
                textFace = textFace,
                shifted = false,
                enterKind = enterKindOf(info),
                candidateListExpanded = false,
                panel = if (restarting) it.panel else Panel.None,
                selecting = restarting && it.selecting,
            ).withAppearance()
        }
        render()
    }

    private fun KeyboardUiState.withAppearance() = copy(
        keyHeightDp = settings.keyHeightDp,
        keyboardHeightPercent = settings.keyboardHeightPercent,
        keyBorders = settings.keyBorders,
        qwertyNumberRow = settings.qwertyNumberRow,
        vibrate = settings.vibrate,
    )

    fun onFinishInput() {
        resetComposition()
        voice.cancel()
        _state.update { if (it.panel == Panel.Voice) it.copy(panel = Panel.None, voice = VoiceUiState()) else it }
    }

    /** エディタ側でカーソルが未確定文字列の外へ動いたら、入力を打ち切る。 */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        val s = session ?: return
        if (!s.isComposing) return
        if (candidatesStart < 0 || newSelStart < candidatesStart || newSelEnd > candidatesEnd) {
            s.clear()
            editor.finishComposing()
            render()
        }
    }

    fun onAction(action: KeyAction) {
        when (action) {
            is KeyAction.Text -> inputText(action.text)
            KeyAction.Backspace -> backspace()
            KeyAction.Space -> space()
            KeyAction.Enter -> enter()
            KeyAction.CursorLeft -> moveCursor(-1)
            KeyAction.CursorRight -> moveCursor(1)
            KeyAction.ToggleModifier -> if (session?.toggleModifier() == true) render()
            KeyAction.Shift -> _state.update { it.copy(shifted = !it.shifted) }
            KeyAction.CycleMode -> cycleFlickMode()
            KeyAction.ToggleKanaAlphabet -> switchFace(
                if (_state.value.face == KeyboardFace.QwertyKana) KeyboardFace.QwertyAlphabet else KeyboardFace.QwertyKana,
            )
            KeyAction.ToggleSymbols -> {
                val state = _state.value
                switchFace(if (Faces.isSymbol(state.face)) state.textFace else Faces.symbol(settings.symbolLayout))
            }
            KeyAction.ToggleLayout -> {
                val next = Faces.otherLayout(_state.value.face)
                if (Faces.isSymbol(next)) settings.symbolLayout = Faces.layoutOf(next) else settings.defaultLayout = Faces.layoutOf(next)
                switchFace(next)
            }
            is KeyAction.SelectCandidate -> selectCandidate(action.index)
            KeyAction.ToggleCandidateList -> _state.update { it.copy(candidateListExpanded = !it.candidateListExpanded) }
            is KeyAction.ResizeSegment -> if (session?.resizeSegment(action.delta) == true) render()
            is KeyAction.OpenPanel -> {
                commitAll()
                if (action.panel == Panel.Clipboard) clipboard.ensureLoaded()
                if (action.panel != Panel.Voice) voice.cancel()
                _state.update { it.copy(panel = action.panel, selecting = false, candidateListExpanded = false) }
            }
            KeyAction.ClosePanel -> {
                voice.cancel()
                _state.update { it.copy(panel = Panel.None, selecting = false, voice = VoiceUiState()) }
            }
            is KeyAction.Cursor -> cursor(action.command)
            is KeyAction.PasteClip -> {
                commitAll()
                editor.commitText(action.text)
            }
            is KeyAction.TogglePinClip -> clipboard.togglePin(action.text)
            is KeyAction.DeleteClip -> clipboard.remove(action.text)
            KeyAction.ClearClips -> clipboard.clearUnpinned()
            KeyAction.StartVoice -> startVoice()
            KeyAction.StopVoice -> voice.stop()
            KeyAction.RequestMicPermission -> host.requestMicPermission()
            KeyAction.OpenSettings -> host.openSettings()
            KeyAction.SwitchInputMethod -> host.switchInputMethod()
            KeyAction.HideKeyboard -> host.hideKeyboard()
        }
    }

    private fun inputText(text: String) {
        val s = session
        val face = _state.value.face
        val kanaFace = face == KeyboardFace.FlickKana || face == KeyboardFace.QwertyKana
        if (s == null || directInput || !kanaFace) {
            commitAll()
            val shifted = _state.value.shifted
            editor.commitText(if (shifted) text.uppercase() else text)
            if (shifted && face != KeyboardFace.QwertySymbols) _state.update { it.copy(shifted = false) }
            return
        }
        val committed = s.insert(text, romaji = face == KeyboardFace.QwertyKana)
        if (committed.isNotEmpty()) editor.commitText(committed)
        render()
    }

    private fun backspace() {
        val s = session
        if (s != null && s.backspace()) {
            render()
        } else {
            editor.deleteBackward()
        }
    }

    private fun space() {
        val s = session
        if (s != null && s.isComposing) {
            s.convertOrNext()
            render()
            return
        }
        val kana = _state.value.face == KeyboardFace.FlickKana || _state.value.face == KeyboardFace.QwertyKana
        editor.commitText(if (kana && settings.fullWidthSpace) "　" else " ")
    }

    private fun enter() {
        val s = session
        if (s != null && s.isComposing) {
            editor.commitText(s.commit())
            render()
        } else {
            editor.sendEnter()
        }
    }

    private fun moveCursor(delta: Int) {
        val s = session
        if (s != null && s.moveCursor(delta)) {
            render()
        } else {
            editor.moveCursor(delta)
        }
    }

    private fun selectCandidate(index: Int) {
        val s = session ?: return
        val committed = if (s.isConverting) {
            s.selectCandidate(index)
        } else {
            val candidate = suggestions.getOrNull(index) ?: return
            s.selectSuggestion(candidate, index)
        }
        if (committed.isNotEmpty()) editor.commitText(committed)
        _state.update { it.copy(candidateListExpanded = false) }
        render()
    }

    private fun cursor(command: CursorCommand) {
        val selecting = _state.value.selecting
        when (command) {
            CursorCommand.Left -> editor.sendNavigationKey(KeyEvent.KEYCODE_DPAD_LEFT, selecting)
            CursorCommand.Right -> editor.sendNavigationKey(KeyEvent.KEYCODE_DPAD_RIGHT, selecting)
            CursorCommand.Up -> editor.sendNavigationKey(KeyEvent.KEYCODE_DPAD_UP, selecting)
            CursorCommand.Down -> editor.sendNavigationKey(KeyEvent.KEYCODE_DPAD_DOWN, selecting)
            CursorCommand.LineStart -> editor.sendNavigationKey(KeyEvent.KEYCODE_MOVE_HOME, selecting)
            CursorCommand.LineEnd -> editor.sendNavigationKey(KeyEvent.KEYCODE_MOVE_END, selecting)
            CursorCommand.ToggleSelection -> {
                if (selecting) collapseSelection()
                _state.update { it.copy(selecting = !selecting) }
            }
            CursorCommand.SelectAll -> {
                editor.performContextMenuAction(android.R.id.selectAll)
                _state.update { it.copy(selecting = true) }
            }
            CursorCommand.Copy -> {
                editor.performContextMenuAction(android.R.id.copy)
                collapseSelection()
                _state.update { it.copy(selecting = false) }
            }
            CursorCommand.Cut -> {
                editor.performContextMenuAction(android.R.id.cut)
                _state.update { it.copy(selecting = false) }
            }
            CursorCommand.Paste -> {
                editor.performContextMenuAction(android.R.id.paste)
                _state.update { it.copy(selecting = false) }
            }
        }
    }

    /**
     * 選択を解除して末尾にカーソルを置く。setSelection は直前のコピーより先に反映されることがあるため、
     * 同じキューを通る方向キーで解除する。
     */
    private fun collapseSelection() {
        editor.sendNavigationKey(KeyEvent.KEYCODE_DPAD_RIGHT, select = false)
    }

    private fun cycleFlickMode() {
        switchFace(if (_state.value.face == KeyboardFace.FlickKana) KeyboardFace.FlickAlphabet else KeyboardFace.FlickKana)
    }

    private fun switchFace(face: KeyboardFace) {
        commitAll()
        _state.update {
            it.copy(face = face, textFace = if (Faces.isSymbol(face)) it.textFace else face, shifted = false)
        }
    }

    private fun startVoice() {
        commitAll()
        if (!voice.hasPermission) {
            _state.update { it.copy(panel = Panel.Voice, voice = VoiceUiState(VoiceStatus.NeedsPermission)) }
            return
        }
        _state.update {
            it.copy(panel = Panel.Voice, voice = VoiceUiState(VoiceStatus.Listening))
        }
        voice.start(
            object : VoiceListener {
                override fun onPartial(text: String) {
                    _state.update { it.copy(voice = it.voice.copy(partial = text)) }
                }

                override fun onResult(text: String) {
                    editor.commitText(text)
                    _state.update { it.copy(voice = it.voice.copy(partial = text)) }
                }

                override fun onError(message: String) {
                    _state.update { it.copy(voice = it.voice.copy(status = VoiceStatus.Error, message = message)) }
                }

                override fun onFinished() {
                    _state.update {
                        if (it.voice.status == VoiceStatus.Listening) it.copy(voice = it.voice.copy(status = VoiceStatus.Idle)) else it
                    }
                }
            },
        )
    }

    /** 未確定文字列があれば確定する。 */
    fun commitAll() {
        val s = session ?: return
        if (!s.isComposing) return
        editor.commitText(s.commit())
        render()
    }

    private fun resetComposition() {
        session?.clear()
        hasPreedit = false
        suggestions = emptyList()
        suggestionRequests.value = null
        _state.update { it.copy(composing = false, converting = false, candidates = emptyList(), selectedCandidate = -1) }
    }

    /** セッションの状態をエディタと UI に反映する。 */
    private fun render() {
        val s = session
        val composing = s?.isComposing == true
        val converting = s?.isConverting == true
        if (s != null && composing) {
            editor.setPreedit(s.preedit)
            hasPreedit = true
        } else if (hasPreedit) {
            // 削除で空になった・確定前に破棄したときに残りを消す
            editor.setPreedit(Preedit.Empty)
            hasPreedit = false
        }
        if (converting) {
            suggestionRequests.value = null
            val list = s.focusedCandidates
            _state.update {
                it.copy(
                    composing = true,
                    converting = true,
                    candidates = list.map(Candidate::surface),
                    selectedCandidate = s.focusedIndex,
                    spaceKind = SpaceKind.NextCandidate,
                )
            }
        } else {
            suggestionRequests.value = if (composing) s.suggestionReading else null
            _state.update {
                it.copy(
                    composing = composing,
                    converting = false,
                    candidates = if (composing) it.candidates else emptyList(),
                    selectedCandidate = -1,
                    spaceKind = if (composing) SpaceKind.Convert else SpaceKind.Space,
                    candidateListExpanded = it.candidateListExpanded && composing,
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun collectSuggestions() {
        suggestionRequests.collectLatest { reading ->
            val converter = engine?.converter
            if (reading == null || converter == null) {
                suggestions = emptyList()
                return@collectLatest
            }
            val result = withContext(Dispatchers.Default) { converter.suggest(reading) }
            // 計算中に入力が進んでいれば捨てる
            if (suggestionRequests.value != reading) return@collectLatest
            suggestions = result
            _state.update { it.copy(candidates = result.map(Candidate::surface)) }
        }
    }

    private fun enterKindOf(info: EditorInfo?): EnterKind {
        if (info == null) return EnterKind.Newline
        if (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0) return EnterKind.Newline
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterKind.Newline
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterKind.Go
            EditorInfo.IME_ACTION_SEARCH -> EnterKind.Search
            EditorInfo.IME_ACTION_SEND -> EnterKind.Send
            EditorInfo.IME_ACTION_NEXT -> EnterKind.Next
            EditorInfo.IME_ACTION_DONE -> EnterKind.Done
            else -> EnterKind.Newline
        }
    }

    private companion object {
        val ASCII_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
        )
    }
}
