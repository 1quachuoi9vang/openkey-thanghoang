package com.tuyenmai.openkey.core

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class VietnameseImeImprovementsTest {

    private class BufferCommitter : TextCommitterTarget {
        val text = StringBuilder()
        override fun beginBatchEdit() = true
        override fun endBatchEdit() = true
        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (beforeLength > 0 && text.isNotEmpty()) {
                val drop = beforeLength.coerceAtMost(text.length)
                text.setLength(text.length - drop)
            }
            return true
        }
        override fun commitText(t: CharSequence, newCursorPosition: Int): Boolean {
            text.append(t)
            return true
        }
        override fun sendKeyChar(char: Char): Boolean {
            text.append(char)
            return true
        }
        override fun performEditorAction(actionId: Int) = true
        override fun moveCursorBy(deltaChars: Int) = true
        override fun deletePreviousWord(): Boolean = true
    }

    @BeforeEach
    fun setUp() {
        resetEngineDefaults(enableAutoWording = true)
    }

    @AfterEach
    fun tearDown() {
        resetEngineDefaults(enableAutoWording = false)
    }

    private fun resetEngineDefaults(enableAutoWording: Boolean) {
        VietnameseEngine.initEngine()
        VietnameseEngine.resetSession()
        VietnameseEngine.syncConfig(
            language = 1,
            inputType = 0,
            checkSpelling = 1,
            useModernOrthography = 1,
            restoreIfWrongSpelling = 1,
            fixRecommendBrowser = 1,
            useSmartSwitchKey = 1,
            bilingualAutoRestore = 1
        )
        AutoWordingEngine.shared.asyncCandidateExecutionEnabled = false
        AutoWordingEngine.shared.syncConfig(
            isEnabled = enableAutoWording,
            grammarRepair = enableAutoWording,
            shorthandExpansion = enableAutoWording,
            candidateSuggestions = enableAutoWording,
            doubleSpacePeriod = false
        )
        AutoWordingEngine.shared.customShorthands = mutableMapOf()
        AutoWordingEngine.shared.clearCandidates()
    }

    // -------------------------------------------------------------------------
    // Phase 1.1: OpenKeyLogger Direct-Boot IOException Resilience & UTF-8 Tail
    // -------------------------------------------------------------------------
    @Test
    fun `test OpenKeyLogger survives IOException on unwritable path and aligns UTF8 tail`() {
        // Passing an existing directory as logFile causes FileInputStream/FileOutputStream to throw IOException
        val unwritableTargetAsDir = File(System.getProperty("java.io.tmpdir"), "openkey-dir-as-log-${System.nanoTime()}")
        unwritableTargetAsDir.mkdirs()
        unwritableTargetAsDir.deleteOnExit()

        val resilientLogger = OpenKeyLogger(logFile = unwritableTargetAsDir)
        resilientLogger.isDebugEnabled = true

        assertDoesNotThrow {
            resilientLogger.log(LogSubsystem.LIFECYCLE) { "Direct boot lock-screen keystroke check" }
        }
        val fallbackLogs = resilientLogger.readRecentLogs()
        assertTrue(
            fallbackLogs.contains("Direct boot lock-screen keystroke check"),
            "In-memory ring buffer must preserve recent logs when disk target is unavailable"
        )

        // Verify UTF-8 multi-byte tail slicing never produces U+FFFD replacement chars
        val utf8File = File.createTempFile("openkey-utf8-tail", ".log")
        utf8File.deleteOnExit()
        val utf8Logger = OpenKeyLogger(logFile = utf8File)
        utf8Logger.isDebugEnabled = true
        repeat(25) { idx ->
            utf8Logger.log(LogSubsystem.ENGINE) { "Dòng kiểm tra tiếng Việt số $idx: trường học, ngưỡng cửa, phụ nữ" }
        }
        for (sliceBytes in listOf(97, 98, 99, 100, 101, 137)) {
            val tail = utf8Logger.readRecentLogs(maxBytes = sliceBytes)
            assertFalse(
                tail.contains('\uFFFD'),
                "UTF-8 tail at maxBytes=$sliceBytes must not contain broken multi-byte replacement char: $tail"
            )
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1.2: English Mode Config Gating & Custom Shorthand Expansion
    // -------------------------------------------------------------------------
    @Test
    fun `test English mode honors AutoWordingConfig flags and expands custom shorthands`() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage).apply { language = 0 }
        val committer = BufferCommitter()
        val controller = KeyboardSessionController(
            settingsStore = store,
            committer = committer,
            autoWordingEngine = AutoWordingEngine.shared
        )
        val ctx = EditorContext(packageName = "com.slack")
        controller.onStartInput(ctx)
        assertEquals(0, controller.editorUiState.language)

        // 1. Disable grammarRepair & candidateSuggestions -> English mode must NOT rewrite "teh " or emit candidates
        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = false,
            shorthandExpansion = true,
            candidateSuggestions = false,
            doubleSpacePeriod = false
        )
        for (ch in "teh") {
            controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        assertTrue(
            AutoWordingEngine.shared.activeCandidates.isEmpty(),
            "English mode must not emit candidates when candidateSuggestionsEnabled == false"
        )
        controller.onKeyAction(KeyAction.Space)
        assertEquals(
            "teh ",
            committer.text.toString(),
            "English mode must not rewrite 'teh ' when grammarRepairEnabled == false"
        )

        // 2. Enable shorthandExpansion in English mode -> "brb " -> "be right back ", "Brb " -> "Be right back "
        committer.text.setLength(0)
        AutoWordingEngine.shared.customShorthands = mutableMapOf("brb" to "be right back")
        for (ch in "Brb") {
            controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        controller.onKeyAction(KeyAction.Space)
        assertEquals(
            "Be right back ",
            committer.text.toString(),
            "English mode must expand custom shorthand preserving Title Case"
        )
    }

    // -------------------------------------------------------------------------
    // Phase 1.3: KeyboardSessionController.dispose() & languageSwitchRequest
    // -------------------------------------------------------------------------
    @Test
    fun `test KeyboardSessionController dispose unregisters listener and honors languageSwitchRequest`() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage).apply {
            language = 1
            useSmartSwitchKey = false
        }
        val committer = BufferCommitter()
        val controller = KeyboardSessionController(
            settingsStore = store,
            committer = committer,
            autoWordingEngine = AutoWordingEngine.shared
        )
        controller.onStartInput(EditorContext(packageName = "com.zalo"))
        assertEquals(1, controller.editorUiState.language)

        // Typing 3 consecutive English words in VI mode triggers HookSnapshot.languageSwitchRequest = 0
        for (ch in "this is good ") {
            if (ch == ' ') controller.onKeyAction(KeyAction.Space)
            else controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        assertEquals("this is good ", committer.text.toString())
        assertEquals(
            0,
            controller.editorUiState.language,
            "Session language must auto-switch to English (0) after 3 consecutive English words"
        )
        assertEquals(
            1,
            store.language,
            "Global settingsStore.language must remain untouched (1) on session auto-switch (AC-028.1)"
        )

        // Dispose controller -> subsequent settingsStore.language changes must not mutate disposed controller
        controller.dispose()
        store.language = 1
        assertEquals(
            0,
            controller.editorUiState.language,
            "Disposed KeyboardSessionController must not receive further settingsStore listener callbacks"
        )
    }

    // -------------------------------------------------------------------------
    // Phase 2.1: Mid-Word Double-Consonant + Consonant Escape & -ness/-less Morphology
    // -------------------------------------------------------------------------
    @Test
    fun `test bilingual mid-word consonant escape and ness less morphology`() {
        val committer = BufferCommitter()
        val controller = KeyboardSessionController(
            committer = committer,
            autoWordingEngine = AutoWordingEngine.shared
        )
        controller.onStartInput(EditorContext(packageName = "com.chrome"))

        val cases = listOf(
            "offset " to "offset ",
            "offline " to "offline ",
            "offload " to "offload ",
            "offshore " to "offshore ",
            "passport " to "passport ",
            "awareness " to "awareness ",
            "politeness " to "politeness ",
            "happiness " to "happiness ",
            "homeless " to "homeless ",
            "priceless " to "priceless "
        )

        for ((typed, expected) in cases) {
            committer.text.setLength(0)
            controller.onStartInput(EditorContext(packageName = "com.chrome"))
            for (ch in typed) {
                if (ch == ' ') controller.onKeyAction(KeyAction.Space)
                else controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
            }
            assertEquals(expected, committer.text.toString(), "Typing '$typed' in VI Telex mode must output '$expected'")
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2.2: Pruned Combinatorial Non-Words, Priority Ranking & Conversational Bigrams
    // -------------------------------------------------------------------------
    @Test
    fun `test pruned fake syllables spatial repair stem priorities and conversational bigrams`() {
        val dict = VietnameseDictionary.shared

        // 1. Unattested combinatorial non-words must not be accepted as valid Vietnamese syllables
        for (fake in listOf("duỳa", "nhuỵu", "gụa", "cườn", "gun", "gún")) {
            assertFalse(dict.isValidVietnameseSyllable(fake), "'$fake' must be rejected as an unattested non-word")
        }

        // 2. Live typing: "guns " stays "guns " (not "gún "), and "cuownf " repairs to "cường " (not "cườn ")
        val committer = BufferCommitter()
        val controller = KeyboardSessionController(
            committer = committer,
            autoWordingEngine = AutoWordingEngine.shared
        )
        controller.onStartInput(EditorContext(packageName = "com.zalo"))
        for (ch in "guns ") {
            if (ch == ' ') controller.onKeyAction(KeyAction.Space)
            else controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        assertEquals("guns ", committer.text.toString(), "'guns ' must restore to English 'guns ' instead of fake 'gún '")

        committer.text.setLength(0)
        controller.onStartInput(EditorContext(packageName = "com.zalo"))
        for (ch in "cuownf ") {
            if (ch == ' ') controller.onKeyAction(KeyAction.Space)
            else controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        assertEquals("vườn ", committer.text.toString(), "'cuownf ' must repair to real 'vườn ' (c/v neighbor) instead of fake 'cườn '")
        assertTrue(
            dict.findVietnameseSpatialCandidates("cuownf", "cườn", limit = 6).contains("cường"),
            "Spatial candidates for 'cuownf' must include 'cường'"
        )
        assertEquals(
            "cường",
            dict.findVietnameseSpatialCandidates("cuownf", "cườn", previousWord = "tăng", limit = 3).firstOrNull(),
            "Contextual spatial candidate for 'cuownf' after 'tăng' must rank 'cường' #1"
        )

        committer.text.setLength(0)
        controller.onStartInput(EditorContext(packageName = "com.zalo"))
        for (ch in "tawng cuownf ") {
            if (ch == ' ') controller.onKeyAction(KeyAction.Space)
            else controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
        }
        assertEquals("tăng cường ", committer.text.toString(), "'tawng cuownf ' must repair contextually to 'tăng cường '")

        // 3. Top-3 stem priority ranking for high-frequency Vietnamese stems
        assertTrue(dict.stemToWords["anh"].orEmpty().take(3).contains("ảnh"), "Top 3 for 'anh' must include 'ảnh'")
        assertTrue(dict.stemToWords["nam"].orEmpty().take(3).contains("nằm"), "Top 3 for 'nam' must include 'nằm'")
        assertTrue(dict.stemToWords["truong"].orEmpty().take(3).contains("trưởng"), "Top 3 for 'truong' must include 'trưởng'")
        assertTrue(dict.stemToWords["cong"].orEmpty().take(3).contains("cộng"), "Top 3 for 'cong' must include 'cộng'")

        // 4. Conversational compound bigrams
        assertTrue(dict.compoundBigrams["cảm"].orEmpty().take(3).contains("ơn"), "Next-word for 'cảm' must include 'ơn'")
        assertTrue(dict.compoundBigrams["xin"].orEmpty().take(3).contains("chào"), "Next-word for 'xin' must include 'chào'")
        assertTrue(dict.compoundBigrams["hôm"].orEmpty().take(3).contains("nay"), "Next-word for 'hôm' must include 'nay'")
        assertTrue(dict.compoundBigrams["chúc"].orEmpty().take(3).contains("mừng"), "Next-word for 'chúc' must include 'mừng'")
        assertTrue(dict.compoundBigrams["việt"].orEmpty().take(3).contains("nam"), "Next-word for 'việt' must include 'nam'")
        val khongTop3 = dict.compoundBigrams["không"].orEmpty().take(3)
        assertTrue(khongTop3.contains("có") && khongTop3.contains("thể"), "Top 3 next-words for 'không' must prioritize 'có' and 'thể', got $khongTop3")
    }

    // -------------------------------------------------------------------------
    // Phase 3.1: 120Hz Two-Thumb Rollover, Slide-Out Cancel & Accelerated Backspace
    // -------------------------------------------------------------------------
    @Test
    fun `test two-thumb touch rollover flushes held key in down order without duplicate commit`() {
        val gesture = KeyboardGestureController()
        val committed = mutableListOf<String>()

        // Simulate overlapping two-thumb chord: 't' DOWN -> 'h' DOWN -> 'h' UP -> 't' UP
        val tokenT = gesture.onCharacterKeyDown("t") { committed.add("t") }
        val tokenH = gesture.onCharacterKeyDown("h") { committed.add("h") }
        assertEquals(listOf("t"), committed, "Pressing 'h' while 't' is held must immediately flush 't'")

        if (gesture.onCharacterKeyUp(tokenH)) {
            committed.add("h")
        }
        if (gesture.onCharacterKeyUp(tokenT)) {
            committed.add("t")
        }
        assertEquals(listOf("t", "h"), committed, "Releasing 'h' then 't' must yield 'th' with zero duplicate 't'")

        // Verify long-press consumption prevents rollover flush
        committed.clear()
        val tokenA = gesture.onCharacterKeyDown("a") { committed.add("a") }
        gesture.markCharacterLongPressConsumed(tokenA)
        val tokenN = gesture.onCharacterKeyDown("n") { committed.add("n") }
        assertTrue(committed.isEmpty(), "Long-press-consumed key must not flush raw base char on next key down")
        assertFalse(gesture.onCharacterKeyUp(tokenA))
        assertTrue(gesture.onCharacterKeyUp(tokenN))

        // Slide-out cancel bounds
        assertFalse(gesture.shouldCancelTapOnDragOut(offsetX = 40f, offsetY = 50f, keyWidthPx = 90f, keyHeightPx = 110f))
        assertTrue(gesture.shouldCancelTapOnDragOut(offsetX = 40f, offsetY = -200f, keyWidthPx = 90f, keyHeightPx = 110f))

        // Accelerated Backspace repeat escalation
        assertEquals(GestureAction.DeleteSingleChar, gesture.resolveBackspaceRepeatAction(1))
        assertEquals(GestureAction.DeleteSingleChar, gesture.resolveBackspaceRepeatAction(15))
        assertEquals(GestureAction.DeletePreviousWord, gesture.resolveBackspaceRepeatAction(16))
    }

    // -------------------------------------------------------------------------
    // Phase 3.2: Vietnamese Đồng (₫), Paired ')' Variant & Comma Quick-Actions
    // -------------------------------------------------------------------------
    @Test
    fun `test Vietnamese Dong symbol paired parenthesis and comma quick action variants`() {
        val gesture = KeyboardGestureController()

        // 1. SYMBOLS_2 contains ₫
        val sym2 = KeyboardLayoutDefinition.getLayer(KeyboardLayer.SYMBOLS_2)
        val sym2Labels = sym2.rows.flatMap { r -> r.keys.map { it.primaryLabel } }
        assertTrue(sym2Labels.contains("₫"), "SYMBOLS_2 layer must contain Vietnamese Đồng symbol '₫'")

        // 2. '$' long-press variants include '₫', while 'd' remains strictly ["đ"]
        assertEquals(listOf("đ"), gesture.resolveLongPressVariants('d'))
        assertTrue(gesture.resolveLongPressVariants('$').contains("₫"), "'$' long-press variants must include '₫'")

        // 3. QWERTY 'f' ($ sublabel) popup includes '$' and '₫', and QWERTY 'l' (( sublabel) includes '(' and ')'
        val qwerty = KeyboardLayoutDefinition.getLayer(KeyboardLayer.QWERTY, isShifted = false, language = 1, imeActionLabel = "↵")
        val fKey = qwerty.rows[2].keys.first { it.primaryLabel == "f" }
        val lKey = qwerty.rows[2].keys.first { it.primaryLabel == "l" }
        val commaKey = qwerty.rows[4].keys.first { it.primaryLabel == "," }

        assertTrue(gesture.resolveLongPressPopupItems(fKey).containsAll(listOf("$", "₫")))
        assertEquals(listOf("(", ")"), gesture.resolveLongPressPopupItems(lKey))
        assertEquals(KeyboardGestureController.COMMA_QUICK_ACTION_VARIANTS, gesture.resolveLongPressPopupItems(commaKey))
    }
}
