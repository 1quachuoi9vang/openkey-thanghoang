package com.tuyenmai.openkey.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImePipelineAndContextTest {

    @BeforeEach
    fun setUp() {
        VietnameseEngine.initEngine()
        VietnameseEngine.resetSession()
        VietnameseEngine.syncConfig(
            language = 1,
            inputType = 0,
            checkSpelling = 1,
            useModernOrthography = 1,
            restoreIfWrongSpelling = 1,
            fixRecommendBrowser = 1,
            useSmartSwitchKey = 1
        )
    }

    // -------------------------------------------------------------------------
    // 1. InputContextEvaluator Tests (REQ-005, ADR-005)
    // -------------------------------------------------------------------------

    @Test
    fun `test password variations trigger bypassVietnamese and isSecureField`() {
        val variations = listOf(
            InputContextEvaluator.TYPE_TEXT_VARIATION_PASSWORD,
            InputContextEvaluator.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputContextEvaluator.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        for (v in variations) {
            val inputType = InputContextEvaluator.TYPE_CLASS_TEXT or v
            val ctx = EditorContext(inputType, InputContextEvaluator.IME_ACTION_DONE, "com.any.app")
            val eval = InputContextEvaluator.evaluate(ctx)

            assertTrue(eval.bypassVietnamese, "Should bypass Vietnamese for text password variation 0x${v.toString(16)}")
            assertTrue(eval.isSecureField, "Should mark text password variation 0x${v.toString(16)} as secure field")
        }

        // Numeric password variation
        val numPasswordType = InputContextEvaluator.TYPE_CLASS_NUMBER or InputContextEvaluator.TYPE_NUMBER_VARIATION_PASSWORD
        val numCtx = EditorContext(numPasswordType, InputContextEvaluator.IME_ACTION_DONE, "com.banking.app")
        val numEval = InputContextEvaluator.evaluate(numCtx)
        assertTrue(numEval.bypassVietnamese, "Should bypass Vietnamese for numeric password")
        assertTrue(numEval.isSecureField, "Should mark numeric password as secure field")
    }

    @Test
    fun `test URI, Email, Filter and NO_SUGGESTIONS trigger bypass without isSecureField`() {
        val nonSecureBypasses = listOf(
            InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_URI,
            InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_FILTER,
            InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )

        for (inputType in nonSecureBypasses) {
            val ctx = EditorContext(inputType, InputContextEvaluator.IME_ACTION_GO, "com.chrome.browser")
            val eval = InputContextEvaluator.evaluate(ctx)

            assertTrue(eval.bypassVietnamese, "Expected bypass for inputType 0x${inputType.toString(16)}")
            assertFalse(eval.isSecureField, "Should not be marked as secure field for 0x${inputType.toString(16)}")
        }
    }

    @Test
    fun `test non-text input classes trigger bypassVietnamese`() {
        val nonTextClasses = listOf(
            InputContextEvaluator.TYPE_CLASS_NUMBER,
            InputContextEvaluator.TYPE_CLASS_PHONE,
            InputContextEvaluator.TYPE_CLASS_DATETIME
        )

        for (cls in nonTextClasses) {
            val ctx = EditorContext(cls, InputContextEvaluator.IME_ACTION_NEXT, "com.phone.dialer")
            val eval = InputContextEvaluator.evaluate(ctx)

            assertTrue(eval.bypassVietnamese, "Non-text class $cls should trigger bypass")
            assertFalse(eval.isSecureField, "Non-text class $cls without password variation should not be secure")
        }
    }

    @Test
    fun `test terminal and remote desktop packages trigger bypassVietnamese`() {
        val terminalPackages = listOf(
            "com.termux",
            "com.sonelli.juicessh",
            "com.server.auditor.ssh.client",
            "com.microsoft.rdc.androidx",
            "com.realvnc.viewer.android"
        )

        for (pkg in terminalPackages) {
            val ctx = EditorContext(InputContextEvaluator.TYPE_CLASS_TEXT, InputContextEvaluator.IME_ACTION_UNSPECIFIED, pkg)
            val eval = InputContextEvaluator.evaluate(ctx)

            assertTrue(eval.bypassVietnamese, "Package $pkg should bypass Vietnamese")
            assertFalse(eval.isSecureField, "Terminal package $pkg is not necessarily a secure field")
        }
    }

    @Test
    fun `test standard text fields in normal apps enable Vietnamese and map IME action labels`() {
        val standardApps = listOf("com.zing.zalo", "com.samsung.android.app.notes")

        for (pkg in standardApps) {
            val ctx = EditorContext(InputContextEvaluator.TYPE_CLASS_TEXT, InputContextEvaluator.IME_ACTION_SEND, pkg)
            val eval = InputContextEvaluator.evaluate(ctx)

            assertFalse(eval.bypassVietnamese, "Standard text in $pkg should NOT bypass Vietnamese")
            assertFalse(eval.isSecureField, "Standard text in $pkg should not be secure")
            assertEquals("Send", eval.enterActionLabel)
        }

        // Test action labels mapping
        val actionMap = mapOf(
            InputContextEvaluator.IME_ACTION_SEARCH to "Search",
            InputContextEvaluator.IME_ACTION_SEND to "Send",
            InputContextEvaluator.IME_ACTION_GO to "Go",
            InputContextEvaluator.IME_ACTION_DONE to "Done",
            InputContextEvaluator.IME_ACTION_NEXT to "Next",
            InputContextEvaluator.IME_ACTION_NONE to "↵",
            InputContextEvaluator.IME_ACTION_UNSPECIFIED to "↵"
        )

        for ((action, expectedLabel) in actionMap) {
            val ctx = EditorContext(InputContextEvaluator.TYPE_CLASS_TEXT, action, "com.zing.zalo")
            val eval = InputContextEvaluator.evaluate(ctx)
            assertEquals(expectedLabel, eval.enterActionLabel, "Action $action should map to $expectedLabel")
        }
    }

    // -------------------------------------------------------------------------
    // 2. Batch Direct Replacement & Cursor Tracking Tests (REQ-005, ADR-005)
    // -------------------------------------------------------------------------

    class MockTextCommitterTarget : TextCommitterTarget {
        val callLog = mutableListOf<String>()
        var textBuffer = StringBuilder()
        var cursorPosition = 0
        var batchEditDepth = 0

        override fun beginBatchEdit(): Boolean {
            batchEditDepth++
            callLog.add("beginBatchEdit")
            return true
        }

        override fun endBatchEdit(): Boolean {
            batchEditDepth--
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

    @Test
    fun `test BatchReplacementCoordinator atomic direct replacement with zero setComposingText`() {
        val target = MockTextCommitterTarget()
        val coordinator = BatchReplacementCoordinator()

        // 1. Initial direct commit of "t"
        coordinator.commitBatch(target, backspaceCount = 0, replacement = "t")
        assertEquals("t", target.textBuffer.toString())
        assertEquals(listOf("beginBatchEdit", "commitText(\"t\", 1)", "endBatchEdit"), target.callLog)
        assertFalse(target.callLog.any { it.contains("setComposingText") })

        target.callLog.clear()

        // 2. Replacement: replace "t" with "ti" (backspaceCount = 0, replacement = "i")
        coordinator.commitBatch(target, backspaceCount = 0, replacement = "i")
        assertEquals("ti", target.textBuffer.toString())

        target.callLog.clear()

        // 3. Engine replacement: "ti" + 'e' -> "tiê" (backspaceCount = 1, replacement = "iê")
        coordinator.commitBatch(target, backspaceCount = 1, replacement = "iê")
        assertEquals("tiê", target.textBuffer.toString())
        assertEquals(
            listOf(
                "beginBatchEdit",
                "deleteSurroundingText(1, 0)",
                "commitText(\"iê\", 1)",
                "endBatchEdit"
            ),
            target.callLog
        )
    }

    @Test
    fun `test external cursor jump triggers engine session reset`() {
        val target = MockTextCommitterTarget()
        val coordinator = BatchReplacementCoordinator()

        // Type "tiếng"
        coordinator.commitBatch(target, backspaceCount = 0, replacement = "tiếng")
        assertEquals(5, coordinator.expectedCursorPosition)

        // Self-committed update matching expectedCursorPosition does NOT reset session
        VietnameseEngine.handleKeyEvent(KEY_T.toInt(), 0, false)
        val resetTriggeredOnSelfUpdate = coordinator.onUpdateSelection(
            oldSelStart = 0, oldSelEnd = 0,
            newSelStart = 5, newSelEnd = 5
        )
        assertFalse(resetTriggeredOnSelfUpdate, "Self update should not trigger reset")

        // External cursor jump (user tapped at position 2)
        val resetTriggeredOnJump = coordinator.onUpdateSelection(
            oldSelStart = 5, oldSelEnd = 5,
            newSelStart = 2, newSelEnd = 2
        )
        assertTrue(resetTriggeredOnJump, "External cursor jump should trigger reset")
        assertEquals(2, coordinator.expectedCursorPosition)
    }

    // -------------------------------------------------------------------------
    // 3. SmartSwitchStore Tests (REQ-006, ADR-006)
    // -------------------------------------------------------------------------

    @Test
    fun `test SmartSwitchStore per-package language persistence and toggle`() {
        val storage = InMemoryKeyValueStorage()
        val store = SmartSwitchStore(storage)

        // Initial state: com.slack uses EN (0), com.zing.zalo uses VI (1)
        store.setAppLanguage("com.slack", 0)
        store.setAppLanguage("com.zing.zalo", 1)

        // Switch to com.slack with useSmartSwitchKey = true -> restores 0 (EN)
        val slackLang = store.onAppSwitched("com.slack", currentGlobalLanguage = 1, useSmartSwitchKey = true)
        assertEquals(0, slackLang, "com.slack should restore English (0)")

        // Switch to com.zing.zalo -> restores 1 (VI)
        val zaloLang = store.onAppSwitched("com.zing.zalo", currentGlobalLanguage = 0, useSmartSwitchKey = true)
        assertEquals(1, zaloLang, "com.zing.zalo should restore Vietnamese (1)")

        // When useSmartSwitchKey = false, preserves currentGlobalLanguage across app switches
        val slackGlobalLang = store.onAppSwitched("com.slack", currentGlobalLanguage = 1, useSmartSwitchKey = false)
        assertEquals(1, slackGlobalLang, "With smart switch off, should preserve global language (1)")

        // Verify first-time package saves default language
        val newAppLang = store.onAppSwitched("com.twitter.android", currentGlobalLanguage = 0, useSmartSwitchKey = true)
        assertEquals(0, newAppLang)
        assertEquals(0, store.getAppLanguage("com.twitter.android", 1))
    }

    // -------------------------------------------------------------------------
    // 4. Phone Touch-Only Architecture Gating (REQ-009, ADR-015)
    // -------------------------------------------------------------------------

    @Test
    fun `testTouchOnlyPhoneScopeNoHardwareKeyMapper`() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("com.tuyenmai.openkey.core.HardwareKeyMapper")
        }

        val methodNames = KeyboardSessionController::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse(methodNames.contains("onHardwareKeyDown"), "KeyboardSessionController must not contain onHardwareKeyDown")
        assertFalse(methodNames.contains("onHardwareKeyUp"), "KeyboardSessionController must not contain onHardwareKeyUp")
    }

    // -------------------------------------------------------------------------
    // 5. Dedicated Samsung One UI 4x4 Numeric Keypad Context (REQ-025, AC-025.1)
    // -------------------------------------------------------------------------

    @Test
    fun testInputContextEvaluatorNumericFieldFlag() {
        val numericClasses = listOf(
            InputContextEvaluator.TYPE_CLASS_NUMBER,
            InputContextEvaluator.TYPE_CLASS_PHONE,
            InputContextEvaluator.TYPE_CLASS_DATETIME
        )
        for (cls in numericClasses) {
            val ctx = EditorContext(cls, InputContextEvaluator.IME_ACTION_DONE, "com.any.app")
            val eval = InputContextEvaluator.evaluate(ctx)
            assertTrue(eval.isNumericField, "Input class $cls must have isNumericField = true")
        }

        val textCtx = EditorContext(InputContextEvaluator.TYPE_CLASS_TEXT, InputContextEvaluator.IME_ACTION_DONE, "com.any.app")
        val textEval = InputContextEvaluator.evaluate(textCtx)
        assertFalse(textEval.isNumericField, "TYPE_CLASS_TEXT must have isNumericField = false")
    }

    // -------------------------------------------------------------------------
    // 6. Per-App SmartSwitch Language Isolation (REQ-028, ADR-029, AC-028.1)
    // -------------------------------------------------------------------------

    @Test
    fun testPerAppSmartSwitchDoesNotOverwriteGlobalLanguage() {
        val settingsStorage = InMemoryKeyValueStorage()
        val settingsStore = OpenKeySettingsStore(settingsStorage)
        settingsStore.language = 1
        settingsStore.useSmartSwitchKey = true

        val smartSwitchStorage = InMemoryKeyValueStorage()
        val smartSwitchStore = SmartSwitchStore(smartSwitchStorage)
        smartSwitchStore.setAppLanguage("com.slack", 0)

        val controller = KeyboardSessionController(
            settingsStore = settingsStore,
            smartSwitchStore = smartSwitchStore
        )

        // Focus com.slack (per-app language = 0 EN)
        controller.onStartInput(EditorContext(packageName = "com.slack"))
        assertEquals(0, controller.editorUiState.language, "editorUiState.language must be 0 for com.slack")
        assertEquals(1, settingsStore.language, "settingsStore.language must remain global default (1)")
        assertEquals(0, VietnameseEngineState.shared.vLanguage, "Engine vLanguage must sync to per-app language (0)")
        assertEquals(LanguagePriority.ENGLISH_FIRST, VietnameseEngine.effectiveLanguagePriority())

        // Focus brand new app without saved language -> must default to global language 1 (VI)
        controller.onStartInput(EditorContext(packageName = "com.unknown.newapp"))
        assertEquals(1, controller.editorUiState.language, "editorUiState.language must default to global setting (1)")
        assertEquals(1, settingsStore.language, "settingsStore.language must still remain 1")
        assertEquals(1, VietnameseEngineState.shared.vLanguage, "Engine vLanguage must sync back to global setting (1)")
        assertEquals(LanguagePriority.VIETNAMESE_FIRST, VietnameseEngine.effectiveLanguagePriority())
    }
}
