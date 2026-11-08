package com.tuyenmai.openkey.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SmoothArchitectureTest {

    private lateinit var mockCommitter: MockTextCommitterTarget
    private lateinit var sessionController: KeyboardSessionController

    @BeforeEach
    fun setUp() {
        VietnameseEngine.initEngine()
        VietnameseEngine.resetSession()
        VietnameseEngine.syncConfig(
            language = 1,
            inputType = 0, // Telex
            checkSpelling = 1,
            useModernOrthography = 1,
            restoreIfWrongSpelling = 1,
            fixRecommendBrowser = 1,
            useSmartSwitchKey = 1
        )
        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = true,
            shorthandExpansion = true,
            candidateSuggestions = true,
            doubleSpacePeriod = true
        )
        AutoWordingEngine.shared.asyncCandidateExecutionEnabled = false
        AutoWordingEngine.shared.clearCandidates()

        mockCommitter = MockTextCommitterTarget()
        sessionController = KeyboardSessionController(committer = mockCommitter)
    }

    @AfterEach
    fun tearDown() {
        AutoWordingEngine.shared.asyncCandidateExecutionEnabled = false
        AutoWordingEngine.shared.onCandidatesChanged = null
        AutoWordingEngine.shared.clearCandidates()
        VietnameseEngine.resetSession()
    }

    // -------------------------------------------------------------------------
    // 1. REQ-011: Touch bypass in Password, Email, URI, and Terminal fields
    // -------------------------------------------------------------------------
    @Test
    fun testTouchBypassInPasswordEmailUriAndTerminalFields() {
        val bypassContexts = listOf(
            EditorContext(
                inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_PASSWORD,
                imeOptions = InputContextEvaluator.IME_ACTION_DONE,
                packageName = "com.banking.app"
            ),
            EditorContext(
                inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                imeOptions = InputContextEvaluator.IME_ACTION_NEXT,
                packageName = "com.google.android.gm"
            ),
            EditorContext(
                inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_URI,
                imeOptions = InputContextEvaluator.IME_ACTION_GO,
                packageName = "com.android.chrome"
            ),
            EditorContext(
                inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
                imeOptions = InputContextEvaluator.IME_ACTION_UNSPECIFIED,
                packageName = "com.termux"
            )
        )

        for (ctx in bypassContexts) {
            mockCommitter.reset()
            sessionController.onStartInput(ctx)

            assertTrue(
                sessionController.editorUiState.bypassVietnamese,
                "Expected bypassVietnamese == true for package ${ctx.packageName} / inputType 0x${ctx.inputType.toString(16)}"
            )

            // Type "test"
            sessionController.onKeyAction(KeyAction.CommitChar("t"))
            sessionController.onKeyAction(KeyAction.CommitChar("e"))
            sessionController.onKeyAction(KeyAction.CommitChar("s"))
            sessionController.onKeyAction(KeyAction.CommitChar("t"))

            assertEquals(
                "test",
                mockCommitter.textBuffer.toString(),
                "Field with bypass must commit raw 'test' without Telex 'tes' -> 'té' conversion"
            )
        }

        // Switched to normal chat field (com.zing.zalo)
        val normalChatCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_SEND,
            packageName = "com.zing.zalo"
        )
        mockCommitter.reset()
        sessionController.onStartInput(normalChatCtx)

        assertFalse(sessionController.editorUiState.bypassVietnamese, "com.zing.zalo must not bypass Vietnamese")

        // Type "tiengs" -> produces "tiếng"
        val chars = listOf("t", "i", "e", "n", "g", "s")
        for (c in chars) {
            sessionController.onKeyAction(KeyAction.CommitChar(c))
        }

        assertEquals("tiếng", mockCommitter.textBuffer.toString(), "Typing 'tiengs' in chat field must produce 'tiếng'")
    }

    // -------------------------------------------------------------------------
    // 2. REQ-011: Live EditorUiState reactivity and Enter action routing
    // -------------------------------------------------------------------------
    @Test
    fun testLiveEditorUiStateAndEnterActionRouting() {
        val observedStates = mutableListOf<EditorUiState>()
        sessionController.addUiStateListener { observedStates.add(it) }

        // Start search input
        val searchCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_SEARCH,
            packageName = "com.google.android.youtube"
        )
        sessionController.onStartInput(searchCtx)

        assertEquals("Search", sessionController.editorUiState.enterActionLabel)
        assertEquals(InputContextEvaluator.IME_ACTION_SEARCH, sessionController.editorUiState.imeActionId)
        assertFalse(sessionController.editorUiState.shouldSendNewlineOnEnter)
        assertTrue(observedStates.any { it.enterActionLabel == "Search" && it.imeActionId == InputContextEvaluator.IME_ACTION_SEARCH })

        // Press Enter on search field -> invokes performEditorAction(IME_ACTION_SEARCH)
        sessionController.onKeyAction(KeyAction.Enter)
        assertTrue(
            mockCommitter.callLog.contains("performEditorAction(${InputContextEvaluator.IME_ACTION_SEARCH})"),
            "Pressing Enter on search field must call performEditorAction(IME_ACTION_SEARCH)"
        )

        // Switch to multiline field
        mockCommitter.reset()
        val multilineCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_FLAG_MULTI_LINE,
            imeOptions = InputContextEvaluator.IME_ACTION_UNSPECIFIED,
            packageName = "com.google.android.keep"
        )
        sessionController.onStartInput(multilineCtx)

        assertEquals("↵", sessionController.editorUiState.enterActionLabel)
        assertTrue(sessionController.editorUiState.shouldSendNewlineOnEnter)

        // Press Enter on multiline field -> commits "\n"
        sessionController.onKeyAction(KeyAction.Enter)
        assertEquals("\n", mockCommitter.textBuffer.toString(), "Pressing Enter on multiline field must commit '\\n'")
    }

    // -------------------------------------------------------------------------
    // 3. REQ-012: Commit-First execution order and async generation tagging
    // -------------------------------------------------------------------------
    @Test
    fun testCommitFirstExecutionOrderAndAsyncGenerationTagging() {
        // 3a. Commit-First execution order verification
        val orderLog = mutableListOf<String>()

        mockCommitter.onCommitCallback = { text ->
            orderLog.add("COMMIT:$text")
        }
        AutoWordingEngine.shared.onCandidatesChanged = { candidates ->
            if (candidates.isNotEmpty()) {
                orderLog.add("CANDIDATES:${candidates.map { it.text }}")
            }
        }

        val chatCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_SEND,
            packageName = "com.zing.zalo"
        )
        sessionController.onStartInput(chatCtx)

        // Type 't', 'i', 'e', 'n', 'g', 's'
        val keys = listOf("t", "i", "e", "n", "g", "s")
        for (k in keys) {
            sessionController.onKeyAction(KeyAction.CommitChar(k))
        }

        // Verify that in the log, every COMMIT precedes the CANDIDATES emission for that step
        val firstCommitIdx = orderLog.indexOfFirst { it.startsWith("COMMIT:") }
        val firstCandidateIdx = orderLog.indexOfFirst { it.startsWith("CANDIDATES:") }

        assertTrue(firstCommitIdx != -1, "At least one COMMIT must be recorded")
        if (firstCandidateIdx != -1) {
            assertTrue(
                firstCommitIdx < firstCandidateIdx,
                "TextCommitterTarget.commitText must be called strictly before AutoWordingEngine.onCandidatesChanged"
            )
        }

        // 3b. VietnameseDictionary.prewarmAsync() verification
        val latch = CountDownLatch(1)
        VietnameseDictionary.prewarmAsync {
            latch.countDown()
        }
        assertTrue(latch.await(2, TimeUnit.SECONDS), "prewarmAsync callback must fire within 2s")
        assertTrue(VietnameseDictionary.isPrewarmed, "VietnameseDictionary.isPrewarmed must be true")

        // 3c. AutoWordingEngine async generation-tagging
        AutoWordingEngine.shared.asyncCandidateExecutionEnabled = true
        val engine = AutoWordingEngine.shared

        val gen1 = engine.nextGenerationId()
        val gen2 = engine.nextGenerationId()

        val staleTaskFinished = CountDownLatch(1)
        val freshTaskFinished = CountDownLatch(1)
        var staleExecuted = false
        var freshExecuted = false

        // Submit stale task (gen1)
        engine.submitCandidateTaskForGeneration(gen1) {
            staleExecuted = true
            staleTaskFinished.countDown()
            listOf(WordCandidate(1, "stale", CandidateKind.STEM_COMPLETION, 0))
        }

        // Submit fresh task (gen2 == currentGenerationId)
        engine.submitCandidateTaskForGeneration(gen2) {
            freshExecuted = true
            freshTaskFinished.countDown()
            listOf(WordCandidate(2, "fresh", CandidateKind.STEM_COMPLETION, 0))
        }

        assertTrue(freshTaskFinished.await(2, TimeUnit.SECONDS), "Fresh task must complete")
        assertTrue(freshExecuted, "Fresh task compute block must be executed")
        assertFalse(staleExecuted, "Stale generation ID must be discarded before executing computeBlock")
    }

    // -------------------------------------------------------------------------
    // 4. REQ-013: BatchReplacementCoordinator selection and trackpad cursor tracking
    // -------------------------------------------------------------------------
    @Test
    fun testBatchReplacementCoordinatorCursorTracking() {
        val coordinator = BatchReplacementCoordinator()

        // Initial cursor at 10 with known selection
        coordinator.resetExpectedCursor(10, knownSelection = true)
        assertEquals(10, coordinator.expectedCursorPosition)
        assertTrue(coordinator.hasKnownSelection)

        // commitBatch replaces text and clears known selection
        val target = MockTextCommitterTarget()
        coordinator.commitBatch(target, backspaceCount = 2, replacement = "abc")
        // (10 - 2) + 3 = 11
        assertEquals(11, coordinator.expectedCursorPosition)
        assertFalse(coordinator.hasKnownSelection)

        // onCursorMovedByTrackpad moves cursor relatively
        val newPos = coordinator.onCursorMovedByTrackpad(-3)
        assertEquals(8, newPos)
        assertEquals(8, coordinator.expectedCursorPosition)
        assertFalse(coordinator.hasKnownSelection)

        // onUpdateSelection reports external jump
        val resetOccurred = coordinator.onUpdateSelection(
            oldSelStart = 8,
            oldSelEnd = 8,
            newSelStart = 20,
            newSelEnd = 25
        )
        assertTrue(resetOccurred)
        assertEquals(20, coordinator.expectedCursorPosition)
        assertEquals(25, coordinator.currentSelEnd)
        assertTrue(coordinator.hasKnownSelection)
    }

    // -------------------------------------------------------------------------
    // 5. REQ-014: Cached KeyboardLayerDef referential identity
    // -------------------------------------------------------------------------
    @Test
    fun testCachedKeyboardLayerDefReferentialIdentity() {
        val layer1 = KeyboardLayoutDefinition.getLayer(
            KeyboardLayer.QWERTY,
            isShifted = false,
            language = 1,
            imeActionLabel = "Search"
        )
        val layer2 = KeyboardLayoutDefinition.getLayer(
            KeyboardLayer.QWERTY,
            isShifted = false,
            language = 1,
            imeActionLabel = "Search"
        )

        assertSame(layer1, layer2, "KeyboardLayoutDefinition.getLayer must return cached instance (referential identity ===)")

        val layerShifted = KeyboardLayoutDefinition.getLayer(
            KeyboardLayer.QWERTY,
            isShifted = true,
            language = 1,
            imeActionLabel = "Search"
        )
        assertNotSame(layer1, layerShifted, "Different layer parameters must yield different cache entries")
    }

    // -------------------------------------------------------------------------
    // 6. REQ-015: 120Hz Hot-Path Throughput Benchmark (< 0.2ms Average per Keystroke)
    // -------------------------------------------------------------------------
    @Test
    fun test120HzHotPathThroughputBenchmark() {
        val chatCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_SEND,
            packageName = "com.zing.zalo"
        )
        sessionController.onStartInput(chatCtx)

        val telexSentence = "anh ddang test cais list nafy owr thanhf phoos hoof chis minh "
        val expectedSentence = "anh đang test cái list này ở thành phố hồ chí minh "

        // 1. Warm-up 100 keystrokes to ensure JIT warmup
        for (i in 0 until 100) {
            val ch = telexSentence[i % telexSentence.length]
            if (ch == ' ') {
                sessionController.onKeyAction(KeyAction.Space)
            } else {
                sessionController.onKeyAction(KeyAction.CommitChar(ch.toString()))
            }
        }

        // Reset for measurement
        mockCommitter.reset()
        sessionController.onStartInput(chatCtx)

        // 2. Measure 1,000 keystrokes
        val targetKeystrokeCount = 1000
        val fullExpectedBuffer = StringBuilder()
        var currentPhrasePos = 0

        val startNs = System.nanoTime()
        for (i in 0 until targetKeystrokeCount) {
            val ch = telexSentence[currentPhrasePos]
            if (ch == ' ') {
                sessionController.onKeyAction(KeyAction.Space)
            } else {
                sessionController.onKeyAction(KeyAction.CommitChar(ch.toString()))
            }
            currentPhrasePos++
            if (currentPhrasePos == telexSentence.length) {
                fullExpectedBuffer.append(expectedSentence)
                currentPhrasePos = 0
            }
        }
        val totalElapsedNs = System.nanoTime() - startNs

        val avgNsPerKeystroke = totalElapsedNs.toDouble() / targetKeystrokeCount
        println("120Hz Throughput Benchmark: $avgNsPerKeystroke ns/keystroke ($totalElapsedNs ns total for $targetKeystrokeCount keystrokes)")

        // 120Hz frame budget requirement: < 200,000 ns (< 0.2ms)
        assertTrue(
            avgNsPerKeystroke < 200_000.0,
            "Average keystroke latency must be < 200,000 ns (< 0.2ms), got: $avgNsPerKeystroke ns"
        )

        // Accuracy verification
        if (currentPhrasePos == 0) {
            assertEquals(
                fullExpectedBuffer.toString(),
                mockCommitter.textBuffer.toString(),
                "Output buffer text must be 100% accurate after 1,000 continuous keystrokes"
            )
        } else {
            assertTrue(
                mockCommitter.textBuffer.toString().startsWith(fullExpectedBuffer.toString()),
                "Output buffer text must match expected content"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helper Mock
    // -------------------------------------------------------------------------
    class MockTextCommitterTarget : TextCommitterTarget {
        val callLog = mutableListOf<String>()
        val textBuffer = StringBuilder()
        var cursorPosition = 0
        var onCommitCallback: ((CharSequence) -> Unit)? = null

        fun reset() {
            callLog.clear()
            textBuffer.clear()
            cursorPosition = 0
        }

        override fun beginBatchEdit(): Boolean {
            callLog.add("beginBatchEdit")
            return true
        }

        override fun endBatchEdit(): Boolean {
            callLog.add("endBatchEdit")
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            callLog.add("deleteSurroundingText($beforeLength, $afterLength)")
            val start = (cursorPosition - beforeLength).coerceAtLeast(0)
            textBuffer.delete(start, cursorPosition)
            cursorPosition = start
            return true
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            callLog.add("commitText(\"$text\", $newCursorPosition)")
            onCommitCallback?.invoke(text)
            textBuffer.insert(cursorPosition, text)
            cursorPosition += text.length
            return true
        }

        override fun sendKeyChar(char: Char): Boolean {
            callLog.add("sendKeyChar('$char')")
            textBuffer.insert(cursorPosition, char)
            cursorPosition++
            return true
        }

        override fun performEditorAction(actionId: Int): Boolean {
            callLog.add("performEditorAction($actionId)")
            return true
        }

        override fun moveCursorBy(deltaChars: Int): Boolean {
            callLog.add("moveCursorBy($deltaChars)")
            cursorPosition = (cursorPosition + deltaChars).coerceIn(0, textBuffer.length)
            return true
        }

        override fun deletePreviousWord(): Boolean {
            callLog.add("deletePreviousWord")
            if (cursorPosition == 0) return true
            var pos = cursorPosition
            while (pos > 0 && textBuffer[pos - 1].isWhitespace()) pos--
            while (pos > 0 && !textBuffer[pos - 1].isWhitespace()) pos--
            textBuffer.delete(pos, cursorPosition)
            cursorPosition = pos
            return true
        }
    }

    // -------------------------------------------------------------------------
    // 6. REQ-017: Android Debug Mode, OpenKeyLogger, Version Scoping & Redaction
    // -------------------------------------------------------------------------
    @Test
    fun testOpenKeyLoggerLazyEvaluationWhenDisabled() {
        val tempLog = java.io.File.createTempFile("openkey-test-lazy", ".log")
        tempLog.deleteOnExit()
        val logger = OpenKeyLogger(logFile = tempLog)
        logger.isDebugEnabled = false

        var lambdaEvaluated = false
        logger.log(LogSubsystem.ENGINE) {
            lambdaEvaluated = true
            "Expensive log message"
        }

        assertFalse(lambdaEvaluated, "Logger must not evaluate lambda when isDebugEnabled == false")
        assertFalse(tempLog.exists() && tempLog.length() > 0, "No file content must be written when isDebugEnabled == false")

        // Enable debug
        logger.isDebugEnabled = true
        logger.log(LogSubsystem.ENGINE) {
            lambdaEvaluated = true
            "Engine initialized"
        }
        assertTrue(lambdaEvaluated, "Lambda must be evaluated when isDebugEnabled == true")
        val content = logger.readRecentLogs()
        assertTrue(content.contains("Engine initialized"))
        assertTrue(content.contains("# OpenKeyAndroidVersionStamp: 1.0.0 (1)"))
    }

    @Test
    fun testOpenKeyLoggerVersionStampPruningAndRotationAndClearLog() {
        val tempLog = java.io.File.createTempFile("openkey-test-prune", ".log")
        tempLog.deleteOnExit()
        val logger = OpenKeyLogger(logFile = tempLog, maxFileSizeBytes = 250L, currentVersion = "1.0.0 (1)")
        logger.isDebugEnabled = true

        // Write outdated version stamp
        tempLog.writeText("# OpenKeyAndroidVersionStamp: 0.9.0\nOld legacy log line\n")
        logger.pruneOutdatedLogsIfNeeded("1.0.0 (1)")

        val pruned = logger.readRecentLogs()
        assertTrue(pruned.startsWith("# OpenKeyAndroidVersionStamp: 1.0.0 (1)"))
        assertFalse(pruned.contains("Old legacy log line"), "Outdated logs must be pruned on version mismatch")

        // Test size cap rotation
        for (i in 1..20) {
            logger.log(LogSubsystem.SESSION) { "Test log payload line $i for size cap rotation checking" }
        }
        val rotated = logger.readRecentLogs()
        assertTrue(rotated.contains("[LOG_ROTATED_SIZE_CAP]"), "File exceeding size cap must be rotated/truncated")

        // Test clearLog()
        logger.clearLog()
        val cleared = logger.readRecentLogs()
        assertEquals("# OpenKeyAndroidVersionStamp: 1.0.0 (1)\n", cleared, "clearLog must reset file to version stamp header")
    }

    @Test
    fun testOpenKeySettingsStoreDebugModeSyncAndKeystrokeRedaction() {
        val storage = InMemoryKeyValueStorage()
        val tempLog = java.io.File.createTempFile("openkey-test-sec", ".log")
        tempLog.deleteOnExit()
        OpenKeyLogger.shared.configureLogFile(tempLog)
        OpenKeyLogger.shared.clearLog()

        val store = OpenKeySettingsStore(storage)
        assertFalse(store.debugModeEnabled)
        assertFalse(OpenKeyLogger.shared.isDebugEnabled)

        // Sync settings store -> logger
        store.debugModeEnabled = true
        assertTrue(OpenKeyLogger.shared.isDebugEnabled)
        val afterEnableLog = OpenKeyLogger.shared.readRecentLogs()
        assertTrue(afterEnableLog.contains("[SETTINGS] debugModeEnabled = true"))

        // Secure field keystroke redaction in KeyboardSessionController
        val secureController = KeyboardSessionController(
            settingsStore = store,
            committer = mockCommitter,
            logger = OpenKeyLogger.shared
        )
        val secureContext = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = InputContextEvaluator.IME_ACTION_DONE,
            packageName = "com.banking.app"
        )
        secureController.onStartInput(secureContext)
        assertTrue(secureController.editorUiState.isSecureField)

        // Type 'p' in secure field
        val res = secureController.onKeyPress("p")
        assertTrue(res.isBypassed)
        assertEquals("p", res.text)

        val logText = OpenKeyLogger.shared.readRecentLogs()
        assertTrue(logText.contains("[SESSION] Secure input active — keystroke redacted, bypassing IME"))

        val sessionLines = logText.lines().filter { it.contains("[SESSION]") }
        assertTrue(sessionLines.isNotEmpty())
        for (line in sessionLines) {
            val msgOnly = line.substringAfter("[SESSION] ")
            assertEquals("Secure input active — keystroke redacted, bypassing IME", msgOnly)
        }

        // Test that confidential keystrokes like "secret123" are not in log
        secureController.onKeyPress("secret123")
        val updatedLogText = OpenKeyLogger.shared.readRecentLogs()
        assertFalse(updatedLogText.contains("secret123"), "Secure field must never log raw input text")

        // Clean up
        store.debugModeEnabled = false
        assertFalse(OpenKeyLogger.shared.isDebugEnabled)
    }
}
