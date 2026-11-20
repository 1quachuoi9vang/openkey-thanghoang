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
}
