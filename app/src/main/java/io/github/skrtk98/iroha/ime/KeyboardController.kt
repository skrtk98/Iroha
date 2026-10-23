package io.github.skrtk98.iroha.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import io.github.skrtk98.iroha.data.ClipEntry
import io.github.skrtk98.iroha.data.ClipboardRepository
import io.github.skrtk98.iroha.data.Engine
import io.github.skrtk98.iroha.data.Settings
import io.github.skrtk98.iroha.engine.conv.Candidate
import io.github.skrtk98.iroha.engine.session.ComposingSession
import io.github.skrtk98.iroha.engine.session.Preedit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

    /** キーボードの外に短い知らせを出す。 */
    fun showMessage(message: String)
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

    /** 数字・パスワードの欄。日本語の面でも変換を使わず、打った文字をそのまま入れる。 */
    private var directInput = false

    /** 入力欄で文字列が選択されているか。選択の解除で、選択が無いのにカーソルを動かさないために使う。 */
    private var textSelected = false

    /** 確定の後に出している続きの候補。 */
    private var followUps: List<Candidate> = emptyList()
    private var followUpJob: Job? = null

    /** 続きの候補を引くための、直前に確定した文字列。続きの候補を選ぶとつなげていく。 */
    private var committedTail = ""

    /**
     * 変換して確定した後のカーソル位置。そこから動いたら、続きの候補を閉じ、確定した語を次の変換の文脈にしない。
     * 確定の直後は位置が届くのを待つ ([awaitingCommitCursor])。
     */
    private var commitCursor: Int? = null
    private var awaitingCommitCursor = false

    init {
        scope.launch { collectSuggestions() }
        scope.launch { clipboard.entries.collect { clips -> _state.update { it.copy(clips = clips) } } }
        // 設定画面の試し入力欄で、見た目の変更をすぐ確かめられるようにする
        scope.launch { settings.changes.collect { _state.update { it.withAppearance() } } }
        scope.launch {
            ResizeRequest.pending.collect { requested ->
                if (!requested) return@collect
                ResizeRequest.pending.value = false
                commitAll()
                _state.update { it.copy(resizing = true, panel = Panel.None, candidateListExpanded = false) }
            }
        }
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
        textSelected = info != null && info.initialSelStart != info.initialSelEnd
        val isNumeric = InputFields.isNumeric(inputType)
        val textFace = Faces.text(settings.defaultLayout, alphabet = InputFields.startsInAlphabet(inputType))
        // 数値欄は数字の打ちやすいテンキー（フリック）にする
        val face = if (isNumeric) KeyboardFace.FlickNumber else textFace
        // それ以外の欄は、日本語の面に切り替えればローマ字・かなを変換する
        directInput = isNumeric || InputFields.isPassword(inputType)
        _state.update {
            it.copy(
                face = face,
                textFace = textFace,
                shift = ShiftState.Off,
                moreSymbols = false,
                enterKind = enterKindOf(info),
                candidateListExpanded = false,
                panel = if (restarting) it.panel else Panel.None,
                selecting = restarting && it.selecting,
            ).withAppearance()
        }
        render()
    }

    private fun KeyboardUiState.withAppearance() = copy(
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

    /** キーボードが隠れたら、高さの調整は保存せずに終える。欄の移動だけなら続ける。 */
    fun onFinishInputView() {
        _state.update { it.copy(resizing = false) }
    }

    /** エディタ側でカーソルが未確定文字列の外へ動いたら、入力を打ち切る。 */
    fun onSelectionChanged(newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        textSelected = newSelStart != newSelEnd
        val s = session ?: return
        if (!s.isComposing) {
            if (awaitingCommitCursor && !textSelected) {
                commitCursor = newSelStart
                awaitingCommitCursor = false
            } else if (commitCursor.let { it != null && (it != newSelStart || textSelected) }) {
                // 入力欄のタップやカーソルキーで確定した語の後ろから離れた
                commitCursor = null
                clearFollowUps()
                s.forgetContext()
            }
            return
        }
        if (candidatesStart < 0 || newSelStart < candidatesStart || newSelEnd > candidatesEnd) {
            s.clear()
            editor.finishComposing()
            render()
        }
    }

    fun onAction(action: KeyAction) {
        // 続きの候補は次の操作までのもの。選んだときだけ、その続きをまた出す
        if (action !is KeyAction.SelectFollowUp) clearFollowUps()
        when (action) {
            is KeyAction.Text -> inputText(action.text)
            KeyAction.Backspace -> backspace()
            KeyAction.Space -> space()
            KeyAction.Enter -> enter()
            KeyAction.CursorLeft -> moveCursor(-1)
            KeyAction.CursorRight -> moveCursor(1)
            KeyAction.ToggleModifier -> if (session?.toggleModifier() == true) render()
            KeyAction.Shift -> _state.update { it.copy(shift = it.shift.tap()) }
            KeyAction.LockShift -> _state.update { it.copy(shift = ShiftState.Locked) }
            KeyAction.ToggleMoreSymbols -> _state.update { it.copy(moreSymbols = !it.moreSymbols) }
            KeyAction.ToggleKanaAlphabet -> switchFace(Faces.otherLanguage(_state.value.textFace))
            KeyAction.ToggleSymbols -> {
                val state = _state.value
                switchFace(if (Faces.isSymbol(state.face)) state.textFace else Faces.symbol(settings.symbolLayout))
            }
            is KeyAction.SelectCandidate -> selectCandidate(action.index)
            KeyAction.ToggleCandidateList -> _state.update { it.copy(candidateListExpanded = !it.candidateListExpanded) }
            is KeyAction.SelectFollowUp -> selectFollowUp(action.index)
            KeyAction.DismissFollowUps -> Unit
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
            is KeyAction.PasteClip -> pasteClip(action.clip)
            is KeyAction.PinClips -> if (clipboard.setPinned(action.keys, action.pinned) > 0) {
                host.showMessage("固定できる数の上限に達したため、一部は固定していません")
            }
            is KeyAction.DeleteClips -> clipboard.remove(action.keys)
            KeyAction.ClearClips -> clipboard.clearUnpinned()
            KeyAction.StartVoice -> startVoice()
            KeyAction.StopVoice -> voice.stop()
            KeyAction.RequestMicPermission -> host.requestMicPermission()
            is KeyAction.FinishResize -> {
                settings.keyboardHeightPercent = action.heightPercent
                _state.update { it.copy(resizing = false).withAppearance() }
            }
            KeyAction.OpenSettings -> host.openSettings()
            KeyAction.SwitchInputMethod -> host.switchInputMethod()
            KeyAction.HideKeyboard -> host.hideKeyboard()
        }
    }

    private fun pasteClip(clip: ClipEntry) {
        commitAll()
        if (clip.image == null) {
            editor.commitText(clip.text)
            return
        }
        val content = clipboard.imageContent(clip)
        when {
            content == null -> host.showMessage("画像が見つかりません")
            !editor.commitImage(content.first, content.second) -> host.showMessage("この入力欄には画像を貼り付けられません")
        }
    }

    private fun inputText(text: String) {
        val s = session
        val face = _state.value.face
        val kanaFace = face == KeyboardFace.FlickKana || face == KeyboardFace.QwertyKana
        val shifted = _state.value.shifted && face != KeyboardFace.QwertySymbols
        // かなの面でもシフト中の英字はローマ字にせず、大文字のまま確定する
        val shiftedLetter = shifted && text.length == 1 && text[0].lowercaseChar() in 'a'..'z'
        if (s == null || directInput || !kanaFace || shiftedLetter) {
            commitAll()
            editor.commitText(if (shifted) text.uppercase() else text)
            if (shifted) _state.update { it.copy(shift = it.shift.afterInput()) }
            return
        }
        val committed = s.insert(text, romaji = face == KeyboardFace.QwertyKana)
        if (committed.isNotEmpty()) editor.commitText(committed)
        // シフト中の記号（～ や 「 など）は読みに入れ、英字と同じく 1 文字でシフトを戻す
        if (shifted) _state.update { it.copy(shift = it.shift.afterInput()) }
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
            commitConverted(s.commit())
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
        if (committed.isNotEmpty()) commitConverted(committed)
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
                if (selecting && textSelected) collapseSelection()
                _state.update { it.copy(selecting = !selecting) }
            }
            CursorCommand.Deselect -> {
                if (textSelected) collapseSelection()
                _state.update { it.copy(selecting = false) }
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
        // 欄からの通知を待たずに解除済みとする。続けて押されても、もう一度カーソルを動かさない
        textSelected = false
    }

    private fun switchFace(face: KeyboardFace) {
        commitAll()
        _state.update {
            it.copy(face = face, textFace = if (Faces.isSymbol(face)) it.textFace else face, shift = ShiftState.Off, moreSymbols = false)
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

    /** 変換して確定した文字列を入れ、その続きの候補を出す。 */
    private fun commitConverted(text: String) {
        awaitCommitCursor()
        editor.commitText(text)
        committedTail = text
        showFollowUps()
    }

    private fun selectFollowUp(index: Int) {
        val candidate = followUps.getOrNull(index) ?: return
        awaitCommitCursor()
        session?.continueContext(candidate.surface)
        editor.commitText(candidate.surface)
        committedTail = (committedTail + candidate.surface).takeLast(FOLLOW_UP_TAIL)
        showFollowUps()
    }

    private fun awaitCommitCursor() {
        commitCursor = null
        awaitingCommitCursor = true
    }

    private fun showFollowUps() {
        val converter = engine?.converter ?: return
        val tail = committedTail
        followUpJob?.cancel()
        followUpJob = scope.launch {
            val list = withContext(Dispatchers.Default) { converter.zeroQuery(tail) }
            followUps = list
            _state.update { it.copy(followUps = list.map(Candidate::surface)) }
        }
    }

    private fun clearFollowUps() {
        if (followUpJob == null) return
        followUpJob?.cancel()
        followUpJob = null
        followUps = emptyList()
        _state.update { it.copy(followUps = emptyList()) }
    }

    /** 未確定文字列があれば確定する。 */
    fun commitAll() {
        val s = session ?: return
        if (!s.isComposing) return
        awaitCommitCursor()
        editor.commitText(s.commit())
        render()
    }

    private fun resetComposition() {
        clearFollowUps()
        session?.clear()
        session?.forgetContext()
        commitCursor = null
        awaitingCommitCursor = false
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
}

/** 続きの候補を引くときに見る、直前に確定した文字列の長さ。 */
private const val FOLLOW_UP_TAIL = 40
