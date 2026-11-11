package com.tuyenmai.openkey.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Immutable UI state observed by KeyboardScreen (REQ-011, ADR-011).
 */
data class EditorUiState(
    val enterActionLabel: String = "↵",
    val imeActionId: Int = InputContextEvaluator.IME_ACTION_UNSPECIFIED,
    val shouldSendNewlineOnEnter: Boolean = false,
    val bypassVietnamese: Boolean = false,
    val isSecureField: Boolean = false,
    val isNumericField: Boolean = false,
    val language: Int = 1,
    val packageName: String = ""
)

/**
 * Result of onKeyPress invocation for touch/test session tracking.
 */
data class KeyPressSessionResult(
    val success: Boolean = true,
    val isBypassed: Boolean = false,
    val text: String = ""
)

/**
 * Unified 120Hz session coordinator in :openkey-core (REQ-011, REQ-012, REQ-017, ADR-011, ADR-012, ADR-017).
 * Centralizes keystroke routing, EditorInfo evaluation, SmartSwitchStore per-app language,
 * BatchReplacementCoordinator atomic commits, candidate selection for touch inputs, and diagnostic logging.
 */
class KeyboardSessionController(
    val settingsStore: OpenKeySettingsStore = OpenKeySettingsStore(InMemoryKeyValueStorage()),
    val smartSwitchStore: SmartSwitchStore = SmartSwitchStore(InMemoryKeyValueStorage()),
    var committer: TextCommitterTarget? = null,
    val batchCoordinator: BatchReplacementCoordinator = BatchReplacementCoordinator(),
    val autoWordingEngine: AutoWordingEngine = AutoWordingEngine.shared,
    private val logger: OpenKeyLogger = OpenKeyLogger.shared
) {

    var editorUiState: EditorUiState = EditorUiState(language = settingsStore.language)
        private set

    private var _composingWord: String = ""
    val composingWord: String get() = _composingWord

    private var _previousWord: String = ""
    val previousWord: String get() = _previousWord

    private var _englishWordStartOk: Boolean = true
    private var _canDoubleSpacePeriod: Boolean = false
    private var _lastWordSpaceTimestampMs: Long = 0L

    private val uiStateListeners = CopyOnWriteArrayList<(EditorUiState) -> Unit>()

    init {
        editorUiState = EditorUiState(language = settingsStore.language)
        settingsStore.addListener { key ->
            if (key == OpenKeySettingsStore.KEY_LANGUAGE) {
                if (editorUiState.language != settingsStore.language) {
                    updateEditorUiState(editorUiState.copy(language = settingsStore.language))
                }
            }
        }
    }

    fun addUiStateListener(listener: (EditorUiState) -> Unit) {
        uiStateListeners.add(listener)
        listener(editorUiState)
    }

    fun removeUiStateListener(listener: (EditorUiState) -> Unit) {
        uiStateListeners.remove(listener)
    }

    private fun updateEditorUiState(newState: EditorUiState) {
        editorUiState = newState
        for (listener in uiStateListeners) {
            listener(newState)
        }
    }

    /**
     * Called on IME startInput / onStartInputView to evaluate context and publish reactive EditorUiState.
     */
    fun onStartInput(context: EditorContext, initialSelStart: Int = 0, initialSelEnd: Int = 0) {
        VietnameseEngine.resetSession()
        autoWordingEngine.clearCandidates()
        _composingWord = ""
        _previousWord = ""
        _englishWordStartOk = true
        _canDoubleSpacePeriod = false

        val eval = InputContextEvaluator.evaluate(context)
        val appLang = if (settingsStore.useSmartSwitchKey) {
            smartSwitchStore.onAppSwitched(
                packageName = context.packageName,
                currentGlobalLanguage = settingsStore.language,
                useSmartSwitchKey = true
            )
        } else {
            settingsStore.language
        }
        VietnameseEngine.syncConfig(language = appLang)

        batchCoordinator.resetExpectedCursor(initialSelStart, initialSelStart >= 0)

        val newState = EditorUiState(
            enterActionLabel = eval.enterActionLabel,
            imeActionId = eval.imeActionId,
            shouldSendNewlineOnEnter = eval.shouldSendNewlineOnEnter,
            bypassVietnamese = eval.bypassVietnamese,
            isSecureField = eval.isSecureField,
            isNumericField = eval.isNumericField,
            language = appLang,
            packageName = context.packageName
        )
        updateEditorUiState(newState)
    }

    /**
     * Called when cursor / selection changes in framework.
     */
    fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        val resetOccurred = batchCoordinator.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
        if (resetOccurred) {
            autoWordingEngine.clearCandidates()
            _composingWord = ""
            _previousWord = ""
            _englishWordStartOk = true
            _canDoubleSpacePeriod = false
        }
        return resetOccurred
    }

    /**
     * Convenience key press entry point with secure-field redaction (REQ-017, ADR-017).
     */
    fun onKeyPress(label: String): KeyPressSessionResult {
        if (editorUiState.isSecureField) {
            logger.log(LogSubsystem.SESSION) { "Secure input active — keystroke redacted, bypassing IME" }
            val res = onKeyAction(KeyAction.CommitChar(label))
            return KeyPressSessionResult(success = res, isBypassed = true, text = label)
        }
        val res = onKeyAction(KeyAction.CommitChar(label))
        logger.log(LogSubsystem.ENGINE) { "Processed key press (composing=${_composingWord.length})" }
        return KeyPressSessionResult(success = res, isBypassed = editorUiState.bypassVietnamese, text = label)
    }

    /**
     * Touch key action router executing the commit-first pipeline (REQ-011, REQ-012, REQ-017).
     */
    fun onKeyAction(action: KeyAction): Boolean {
        val target = committer ?: return false

        when (action) {
            is KeyAction.CommitChar -> {
                _canDoubleSpacePeriod = false
                val charStr = action.char
                if (editorUiState.isSecureField) {
                    logger.log(LogSubsystem.SESSION) { "Secure input active — keystroke redacted, bypassing IME" }
                }
                if (editorUiState.bypassVietnamese) {
                    _composingWord = ""
                    _previousWord = ""
                    _englishWordStartOk = true
                    batchCoordinator.commitBatch(target, 0, charStr)
                    return true
                }

                if (editorUiState.language == 0) {
                    if (charStr.length == 1 && (charStr[0].isLetter() || charStr[0] == '\'')) {
                        _composingWord += charStr
                        batchCoordinator.commitBatch(target, 0, charStr)
                        autoWordingEngine.didProcessEnglishKeyEvent(
                            charStr[0].code,
                            false,
                            _composingWord,
                            _previousWord,
                            wordStartOk = _englishWordStartOk
                        )
                        return true
                    } else if (charStr.length == 1 && (charStr[0] == ',' || charStr[0] == '.' || charStr[0] == '!' || charStr[0] == '?' || charStr[0] == ';' || charStr[0] == ':')) {
                        val ch0 = charStr[0]
                        if (_composingWord.isNotEmpty()) {
                            val rep = autoWordingEngine.didProcessEnglishKeyEvent(
                                ch0.code,
                                false,
                                _composingWord,
                                _previousWord,
                                wordStartOk = _englishWordStartOk
                            )
                            if (rep != null) {
                                batchCoordinator.commitBatch(target, rep.first, rep.second)
                                _previousWord = rep.second.dropLast(1).trim()
                            } else {
                                batchCoordinator.commitBatch(target, 0, charStr)
                                _previousWord = _composingWord
                            }
                            _composingWord = ""
                        } else {
                            batchCoordinator.commitBatch(target, 0, charStr)
                        }
                        _englishWordStartOk = !(ch0 == '.' || ch0 == ':')
                        return true
                    } else {
                        _composingWord = ""
                        val ch0 = charStr.firstOrNull()
                        _englishWordStartOk = if (ch0 != null && (ch0 == '/' || ch0 == '-' || ch0 == '_' || ch0 == '@' || ch0.isDigit())) {
                            false
                        } else {
                            true
                        }
                        batchCoordinator.commitBatch(target, 0, charStr)
                        autoWordingEngine.clearCandidates()
                        return true
                    }
                }

                if (charStr.length == 1) {
                    val ch = charStr[0]
                    val mapping = VietnameseData.charToKeyCode(ch)
                    val keyCode = mapping.first
                    val caps = mapping.second

                    if (keyCode == VietnameseData.KEY_EMPTY.toInt()) {
                        _composingWord = ""
                        batchCoordinator.commitBatch(target, 0, charStr)
                        return true
                    }

                    VietnameseEngine.handleKeyEventCommitFirst(keyCode, caps, false) { snapshot ->
                        if (snapshot.code == HookCodeState.WILL_PROCESS ||
                            snapshot.code == HookCodeState.RESTORE ||
                            snapshot.code == HookCodeState.RESTORE_AND_START_NEW_SESSION
                        ) {
                            batchCoordinator.commitBatch(target, snapshot.backspaceCount, snapshot.decodedString)
                        } else {
                            batchCoordinator.commitBatch(target, 0, charStr)
                        }
                    }
                    val wordContext = VietnameseEngine.getWordContext()
                    _composingWord = wordContext.composedWord
                    return true
                } else {
                    _composingWord = ""
                    batchCoordinator.commitBatch(target, 0, charStr)
                    return true
                }
            }

            is KeyAction.Backspace -> {
                _canDoubleSpacePeriod = false
                if (editorUiState.bypassVietnamese) {
                    _composingWord = ""
                    _previousWord = ""
                    target.deleteSurroundingText(1, 0)
                    batchCoordinator.recordSelfDelete(1)
                    VietnameseEngine.resetSession()
                    autoWordingEngine.clearCandidates()
                    return true
                }

                if (editorUiState.language == 0) {
                    if (_composingWord.isNotEmpty()) {
                        _composingWord = _composingWord.dropLast(1)
                        target.deleteSurroundingText(1, 0)
                        batchCoordinator.recordSelfDelete(1)
                        if (_composingWord.isNotEmpty()) {
                            autoWordingEngine.didProcessEnglishKeyEvent(0, true, _composingWord, _previousWord, wordStartOk = _englishWordStartOk)
                        } else {
                            autoWordingEngine.clearCandidates()
                        }
                    } else {
                        target.deleteSurroundingText(1, 0)
                        batchCoordinator.recordSelfDelete(1)
                        _englishWordStartOk = true
                        autoWordingEngine.clearCandidates()
                    }
                    return true
                }

                val keyCode = VietnameseData.KEY_DELETE.toInt()
                VietnameseEngine.handleKeyEventCommitFirst(keyCode, 0, false) { snapshot ->
                    if (snapshot.code == HookCodeState.WILL_PROCESS ||
                        snapshot.code == HookCodeState.RESTORE ||
                        snapshot.code == HookCodeState.RESTORE_AND_START_NEW_SESSION
                    ) {
                        batchCoordinator.commitBatch(target, snapshot.backspaceCount, snapshot.decodedString)
                    } else {
                        target.deleteSurroundingText(1, 0)
                        batchCoordinator.recordSelfDelete(1)
                    }
                }
                val wordContext = VietnameseEngine.getWordContext()
                _composingWord = wordContext.composedWord
                return true
            }

            is KeyAction.Space -> {
                if (editorUiState.bypassVietnamese) {
                    _composingWord = ""
                    _previousWord = ""
                    _englishWordStartOk = true
                    _canDoubleSpacePeriod = false
                    batchCoordinator.commitBatch(target, 0, " ")
                    VietnameseEngine.resetSession()
                    autoWordingEngine.clearCandidates()
                    return true
                }

                val nowMs = System.currentTimeMillis()
                if (_canDoubleSpacePeriod &&
                    autoWordingEngine.isEnabled &&
                    autoWordingEngine.doubleSpacePeriodEnabled &&
                    (nowMs - _lastWordSpaceTimestampMs) <= 500L
                ) {
                    _canDoubleSpacePeriod = false
                    _englishWordStartOk = true
                    batchCoordinator.commitBatch(target, 1, ". ")
                    VietnameseEngine.resetSession()
                    val state = VietnameseEngineState.shared
                    state.lock.lock()
                    try {
                        state.sentenceEndedWithPunctuation = true
                    } finally {
                        state.lock.unlock()
                    }
                    autoWordingEngine.clearCandidates()
                    return true
                }

                if (editorUiState.language == 0) {
                    val hadWord = _composingWord.isNotEmpty()
                    if (hadWord) {
                        val rep = autoWordingEngine.didProcessEnglishKeyEvent(
                            VietnameseData.KEY_SPACE.toInt(),
                            false,
                            _composingWord,
                            _previousWord,
                            wordStartOk = _englishWordStartOk
                        )
                        if (rep != null) {
                            batchCoordinator.commitBatch(target, rep.first, rep.second)
                            _previousWord = rep.second.trim()
                        } else {
                            batchCoordinator.commitBatch(target, 0, " ")
                            _previousWord = _composingWord
                        }
                        _composingWord = ""
                        _canDoubleSpacePeriod = true
                        _lastWordSpaceTimestampMs = nowMs
                    } else {
                        batchCoordinator.commitBatch(target, 0, " ")
                        _canDoubleSpacePeriod = false
                        autoWordingEngine.clearCandidates()
                    }
                    _englishWordStartOk = true
                    return true
                }

                val hadVietnameseWord = VietnameseEngineState.shared.let { st ->
                    st.lock.lock()
                    try {
                        st.index > 0
                    } finally {
                        st.lock.unlock()
                    }
                }
                _composingWord = ""
                val keyCode = VietnameseData.KEY_SPACE.toInt()
                VietnameseEngine.handleKeyEventCommitFirst(keyCode, 0, false) { snapshot ->
                    if (snapshot.code == HookCodeState.WILL_PROCESS ||
                        snapshot.code == HookCodeState.RESTORE ||
                        snapshot.code == HookCodeState.RESTORE_AND_START_NEW_SESSION
                    ) {
                        batchCoordinator.commitBatch(target, snapshot.backspaceCount, snapshot.decodedString)
                    } else {
                        batchCoordinator.commitBatch(target, 0, " ")
                    }
                }
                _canDoubleSpacePeriod = hadVietnameseWord
                if (hadVietnameseWord) {
                    _lastWordSpaceTimestampMs = nowMs
                }
                return true
            }

            is KeyAction.Enter -> {
                _composingWord = ""
                _previousWord = ""
                _englishWordStartOk = true
                _canDoubleSpacePeriod = false
                VietnameseEngine.resetSession()
                autoWordingEngine.clearCandidates()
                if (editorUiState.shouldSendNewlineOnEnter) {
                    batchCoordinator.commitBatch(target, 0, "\n")
                } else {
                    target.performEditorAction(editorUiState.imeActionId)
                }
                return true
            }

            is KeyAction.SwitchLanguage -> {
                _composingWord = ""
                _previousWord = ""
                _englishWordStartOk = true
                _canDoubleSpacePeriod = false
                val nextLang = if (editorUiState.language == 1) 0 else 1
                settingsStore.language = nextLang
                VietnameseEngine.syncConfig(language = nextLang)
                if (editorUiState.packageName.isNotBlank()) {
                    smartSwitchStore.setAppLanguage(editorUiState.packageName, nextLang)
                }
                VietnameseEngine.resetSession()
                autoWordingEngine.clearCandidates()
                updateEditorUiState(editorUiState.copy(language = nextLang))
                return true
            }

            is KeyAction.Shift,
            is KeyAction.SwitchLayer -> {
                return true
            }
        }
    }

    /**
     * Candidate selection by index with diagnostic logging (REQ-017).
     */
    fun onCandidateSelected(index: Int): Boolean {
        logger.log(LogSubsystem.AUTO_WORDING) { "Candidate index=$index selected" }
        val cands = autoWordingEngine.candidates
        if (index in cands.indices) {
            return onCandidateSelected(cands[index])
        }
        return false
    }

    /**
     * Candidate selection handling.
     */
    fun onCandidateSelected(candidate: WordCandidate): Boolean {
        val target = committer ?: return false
        val idx = autoWordingEngine.candidates.indexOf(candidate)
        if (idx >= 0) {
            logger.log(LogSubsystem.AUTO_WORDING) { "Candidate index=$idx selected" }
        }
        _composingWord = ""
        _englishWordStartOk = true
        _canDoubleSpacePeriod = true
        _lastWordSpaceTimestampMs = System.currentTimeMillis()
        batchCoordinator.commitBatch(target, candidate.replaceLength, candidate.text + " ")
        autoWordingEngine.recordCandidateAcceptance(candidate.text)
        VietnameseEngine.resetSession()
        _previousWord = candidate.text
        val nextCands = autoWordingEngine.computeNextWordCandidates(candidate.text)
        autoWordingEngine.updateCandidates(nextCands)
        return true
    }

    /**
     * Spacebar trackpad horizontal cursor dragging with zero IPC text extractions (REQ-013, ADR-013).
     */
    fun onMoveCursor(deltaChars: Int): Boolean {
        _canDoubleSpacePeriod = false
        val target = committer ?: return false
        return target.moveCursorBy(deltaChars)
    }

    /**
     * Swipe left on backspace word deletion.
     */
    fun onDeletePreviousWord(): Boolean {
        val target = committer ?: return false
        _composingWord = ""
        _previousWord = ""
        _englishWordStartOk = true
        _canDoubleSpacePeriod = false
        VietnameseEngine.resetSession()
        autoWordingEngine.clearCandidates()
        return target.deletePreviousWord()
    }
}
