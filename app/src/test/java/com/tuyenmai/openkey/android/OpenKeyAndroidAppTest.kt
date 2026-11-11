package com.tuyenmai.openkey.android

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import org.junit.Rule
import com.tuyenmai.openkey.android.ime.ImeComposeLifecycleOwner
import com.tuyenmai.openkey.android.ime.InputConnectionCommitter
import com.tuyenmai.openkey.core.AutoWordingEngine
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.EditorContext
import com.tuyenmai.openkey.android.ime.OpenKeyImeService
import com.tuyenmai.openkey.core.InMemoryKeyValueStorage
import com.tuyenmai.openkey.core.InputContextEvaluator
import com.tuyenmai.openkey.core.KeyAction
import com.tuyenmai.openkey.core.KeyboardSessionController
import com.tuyenmai.openkey.core.LogSubsystem
import com.tuyenmai.openkey.core.OneHandedMode
import com.tuyenmai.openkey.core.OpenKeyL10n
import com.tuyenmai.openkey.core.OpenKeyLogger
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.SmartSwitchStore
import com.tuyenmai.openkey.core.VietnameseEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executor

class OpenKeyAndroidAppTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // --- Mock InputConnection for testing InputConnectionCommitter ---
    private open class MockInputConnection : InputConnection {
        var batchEditDepth = 0
        var committedText = ""
        var deletedBefore = 0
        var deletedAfter = 0
        var selectionStart = 0
        var selectionEnd = 0
        var textBeforeCursor = ""
        var extractedText: ExtractedText? = null
        var getExtractedTextCallCount = 0
        val sentKeyEvents = mutableListOf<KeyEvent>()

        override fun beginBatchEdit(): Boolean {
            batchEditDepth++
            return true
        }

        override fun endBatchEdit(): Boolean {
            batchEditDepth = (batchEditDepth - 1).coerceAtLeast(0)
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletedBefore += beforeLength
            deletedAfter += afterLength
            if (beforeLength > 0 && committedText.length >= beforeLength) {
                committedText = committedText.dropLast(beforeLength)
            }
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committedText += text?.toString() ?: ""
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            return textBeforeCursor
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? {
            getExtractedTextCallCount++
            return extractedText
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            selectionStart = start
            selectionEnd = end
            extractedText?.selectionStart = start
            extractedText?.selectionEnd = end
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event != null) sentKeyEvents.add(event)
            return true
        }

        override fun performEditorAction(editorAction: Int): Boolean = true
        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = null
        override fun getSelectedText(flags: Int): CharSequence? = null
        override fun getCursorCapsMode(reqModes: Int): Int = 0
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true
        override fun setComposingRegion(start: Int, end: Int): Boolean = true
        override fun finishComposingText(): Boolean = true
        override fun commitCompletion(text: CompletionInfo?): Boolean = true
        override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = true
        override fun performContextMenuAction(id: Int): Boolean = true
        override fun clearMetaKeyStates(states: Int): Boolean = true
        override fun reportFullscreenMode(enabled: Boolean): Boolean = true
        override fun performSpellCheck(): Boolean = true
        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = true
        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = true
        override fun getHandler(): Handler? = null
        override fun closeConnection() {}
        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = true
    }

    // 1. AndroidManifest & Direct Boot Privacy Verification (REQ-001)
    @Test
    fun testManifestDeclaresDirectBootAndZeroInternetPermission() {
        // Find AndroidManifest.xml from workspace root or project root
        val possiblePaths = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("Sources/OpenKey/android/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = possiblePaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)

        val manifestContent = manifestFile!!.readText()

        // REQ-001: Zero Internet permission
        assertFalse("Manifest MUST NOT declare INTERNET permission", manifestContent.contains("android.permission.INTERNET"))
        assertFalse("Manifest MUST NOT declare ACCESS_NETWORK_STATE", manifestContent.contains("android.permission.ACCESS_NETWORK_STATE"))

        // REQ-001: IME Service declaration & Direct Boot
        assertTrue("IME service must be directBootAware", manifestContent.contains("android:directBootAware=\"true\""))
        assertTrue("IME service must declare BIND_INPUT_METHOD", manifestContent.contains("android.permission.BIND_INPUT_METHOD"))
        assertTrue("IME service class must be OpenKeyImeService", manifestContent.contains(".ime.OpenKeyImeService"))
        assertTrue("Settings activity must be declared", manifestContent.contains(".settings.SettingsActivity"))
    }

    // 2. InputConnectionCommitter Batch Direct-Replacement (REQ-005, ADR-005)
    @Test
    fun testInputConnectionCommitterBatchOperations() {
        val mockIc = MockInputConnection()
        val committer = InputConnectionCommitter(mockIc)

        // beginBatchEdit and commitText
        committer.beginBatchEdit()
        assertEquals(1, mockIc.batchEditDepth)
        committer.commitText("tiếng", 1)
        assertEquals("tiếng", mockIc.committedText)
        committer.endBatchEdit()
        assertEquals(0, mockIc.batchEditDepth)

        // deleteSurroundingText
        committer.deleteSurroundingText(2, 0)
        assertEquals(2, mockIc.deletedBefore)
        assertEquals("tiế", mockIc.committedText)
    }

    // 3. InputConnectionCommitter Cursor Movement & Word Deletion (REQ-005, REQ-008, REQ-013, ADR-013)
    @Test
    fun testInputConnectionCommitterCursorAndWordDelete() {
        val mockIc = MockInputConnection()
        val coordinator = BatchReplacementCoordinator()
        coordinator.resetExpectedCursor(16, knownSelection = true)
        val committerWithCoord = InputConnectionCommitter(mockIc, coordinator)

        // Move cursor 5 steps left with tracked selection coordinator (zero getExtractedText calls)
        committerWithCoord.moveCursorBy(-5)
        assertEquals(11, mockIc.selectionStart)
        assertEquals(11, mockIc.selectionEnd)
        assertEquals(0, mockIc.getExtractedTextCallCount)

        // Move cursor 3 steps right with tracked selection coordinator
        committerWithCoord.moveCursorBy(3)
        assertEquals(14, mockIc.selectionStart)
        assertEquals(14, mockIc.selectionEnd)
        assertEquals(0, mockIc.getExtractedTextCallCount)

        // Fallback DPAD cursor movement (no coordinator)
        val mockIcDpad = MockInputConnection()
        val committerDpad = InputConnectionCommitter(mockIcDpad)
        committerDpad.moveCursorBy(-2)
        assertEquals(0, mockIcDpad.getExtractedTextCallCount)
        assertEquals(4, mockIcDpad.sentKeyEvents.size) // 2 down + 2 up

        // deletePreviousWord on "hello world" -> deletes "world" (5 chars)
        mockIc.textBeforeCursor = "hello world"
        mockIc.deletedBefore = 0
        val del1 = committerWithCoord.deletePreviousWord()
        assertTrue(del1)
        assertEquals(5, mockIc.deletedBefore)

        // deletePreviousWord on "test list   " -> deletes trailing spaces (3) + "list" (4) = 7 chars
        mockIc.textBeforeCursor = "test list   "
        mockIc.deletedBefore = 0
        val del2 = committerWithCoord.deletePreviousWord()
        assertTrue(del2)
        assertEquals(7, mockIc.deletedBefore)

        // Delayed selection callback for the self-deleted position (14 - 5 - 7 = 2) must not trigger reset
        assertFalse(coordinator.onUpdateSelection(9, 9, 2, 2))

        // Post-commitBatch trackpad scrub still uses setSelection via isCursorAnchorKnown (zero IPC, zero DPAD fallback)
        coordinator.commitBatch(committerWithCoord, 0, "abc")
        assertFalse(coordinator.hasKnownSelection)
        assertTrue(coordinator.isCursorAnchorKnown)
        committerWithCoord.moveCursorBy(-2)
        assertEquals(3, mockIc.selectionStart)
        assertEquals(3, mockIc.selectionEnd)
        assertEquals(0, mockIc.getExtractedTextCallCount)
        assertEquals(0, mockIc.sentKeyEvents.size)
    }

    // 4. Phone Touch-Only Architecture Gating (REQ-009, ADR-015)
    @Test
    fun testPhoneTouchOnlyScopeNoHardwareKeyInterception() {
        val methodNames = OpenKeyImeService::class.java.declaredMethods.map { it.name }.toSet()
        assertFalse("OpenKeyImeService must not declare processHardwareKey", methodNames.contains("processHardwareKey"))
        assertFalse("OpenKeyImeService must not declare onKeyDown", methodNames.contains("onKeyDown"))
        assertFalse("OpenKeyImeService must not declare onKeyUp", methodNames.contains("onKeyUp"))
    }

    // 6. ImeComposeLifecycleOwner Lifecycle State Transitions (REQ-010, ADR-010)
    @Test
    fun testImeComposeLifecycleOwnerTransitions() {
        val owner = ImeComposeLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.onCreate()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onResume()
        assertEquals(Lifecycle.State.RESUMED, owner.lifecycle.currentState)

        owner.onPause()
        assertEquals(Lifecycle.State.CREATED, owner.lifecycle.currentState)

        owner.onDestroy()
        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
    }

    // 7. SettingsStore & SmartSwitchStore Wiring (REQ-006, REQ-010)
    @Test
    fun testSettingsStoreDefaultsAndSmartSwitchWiring() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage)
        val smartSwitch = SmartSwitchStore(storage)

        // Defaults
        assertEquals(1, store.language) // Vietnamese by default
        assertEquals(0, store.inputType) // Telex
        assertTrue(store.useModernOrthography)
        assertTrue(store.checkSpelling)
        assertTrue(store.restoreIfWrongSpelling)
        assertTrue(store.bilingualAutoRestore)
        assertTrue(store.useSmartSwitchKey)
        assertTrue(store.showNumberRow)
        assertEquals(100, store.heightScalePercent)
        assertEquals(14, store.bottomChinPaddingDp)
        assertEquals(OneHandedMode.OFF, store.oneHandedMode)
        assertTrue(store.hapticFeedbackEnabled)
        assertTrue(store.keyPopupPreviewEnabled)

        // Per-app smart switch memory
        smartSwitch.setAppLanguage("com.slack", 0) // English in Slack
        smartSwitch.setAppLanguage("com.zing.zalo", 1) // Vietnamese in Zalo

        assertEquals(0, smartSwitch.getAppLanguage("com.slack"))
        assertEquals(1, smartSwitch.getAppLanguage("com.zing.zalo"))

        // Switching apps
        val resolvedLang = smartSwitch.onAppSwitched("com.slack", store.language, true)
        assertEquals(0, resolvedLang)
    }

    // 8. Zero getExtractedText Calls Across 50 Trackpad Drag Steps (REQ-013, ADR-013)
    @Test
    fun testZeroGetExtractedTextCallsAcross50TrackpadDragSteps() {
        val mockIc = MockInputConnection()
        val coordinator = BatchReplacementCoordinator()
        coordinator.resetExpectedCursor(50, knownSelection = true)
        val committer = InputConnectionCommitter(inputConnection = mockIc, batchCoordinator = coordinator)
        val sessionController = KeyboardSessionController(
            committer = committer,
            batchCoordinator = coordinator
        )

        // 50 drag steps left (-1 each)
        for (step in 1..50) {
            val success = sessionController.onMoveCursor(-1)
            assertTrue("Step -$step should succeed", success)
            val expectedPos = 50 - step
            assertEquals(expectedPos, mockIc.selectionStart)
            assertEquals(expectedPos, mockIc.selectionEnd)
        }
        assertEquals(0, mockIc.selectionStart)
        assertEquals(0, mockIc.getExtractedTextCallCount)

        // 50 drag steps right (+1 each)
        for (step in 1..50) {
            val success = sessionController.onMoveCursor(1)
            assertTrue("Step +$step should succeed", success)
            val expectedPos = step
            assertEquals(expectedPos, mockIc.selectionStart)
            assertEquals(expectedPos, mockIc.selectionEnd)
        }
        assertEquals(50, mockIc.selectionStart)
        assertEquals(0, mockIc.getExtractedTextCallCount)
    }

    // 9. SessionController End-to-End Touch and Hardware with EditorContext (REQ-011, REQ-012)
    @Test
    fun testSessionControllerEndToEndTouchAndHardwareWithEditorContext() {
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

        var lastActionId = -1
        val trackingIc = object : MockInputConnection() {
            override fun performEditorAction(editorAction: Int): Boolean {
                lastActionId = editorAction
                return true
            }
        }
        val coordinator = BatchReplacementCoordinator()
        val committer = InputConnectionCommitter(inputConnection = trackingIc, batchCoordinator = coordinator)
        val storage = InMemoryKeyValueStorage()
        val settingsStore = OpenKeySettingsStore(storage)
        val sessionController = KeyboardSessionController(
            settingsStore = settingsStore,
            committer = committer,
            batchCoordinator = coordinator
        )

        // Case 1: Password field in banking app (Bypass Vietnamese)
        val passwordContext = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT or InputContextEvaluator.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = InputContextEvaluator.IME_ACTION_DONE,
            packageName = "com.banking.app"
        )
        sessionController.onStartInput(passwordContext, initialSelStart = 0, initialSelEnd = 0)
        assertTrue(sessionController.editorUiState.bypassVietnamese)
        assertTrue(sessionController.editorUiState.isSecureField)

        // Type "tiengs" via touch in password field -> commits raw "tiengs"
        val chars = listOf("t", "i", "e", "n", "g", "s")
        for (c in chars) {
            sessionController.onKeyAction(KeyAction.CommitChar(c))
        }
        assertEquals("tiengs", trackingIc.committedText)

        // Case 2: Zalo Chat field (Normal Vietnamese Telex) with Search action
        trackingIc.committedText = ""
        val zaloSearchContext = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_SEARCH,
            packageName = "com.zing.zalo"
        )
        sessionController.onStartInput(zaloSearchContext, initialSelStart = 0, initialSelEnd = 0)
        assertFalse(sessionController.editorUiState.bypassVietnamese)
        assertFalse(sessionController.editorUiState.isSecureField)
        assertEquals(InputContextEvaluator.IME_ACTION_SEARCH, sessionController.editorUiState.imeActionId)
        assertEquals("Search", sessionController.editorUiState.enterActionLabel)

        // Type "tiengs" via touch -> converted to "tiếng"
        for (c in chars) {
            sessionController.onKeyAction(KeyAction.CommitChar(c))
        }
        assertEquals("tiếng", trackingIc.committedText)

        // Press Enter -> triggers IME_ACTION_SEARCH on InputConnection
        sessionController.onKeyAction(KeyAction.Enter)
        assertEquals(InputContextEvaluator.IME_ACTION_SEARCH, lastActionId)

        // Teardown
        AutoWordingEngine.shared.asyncCandidateExecutionEnabled = false
        AutoWordingEngine.shared.clearCandidates()
        VietnameseEngine.resetSession()
    }

    // 10. OpenKeyL10n.Debug strings & SettingsActivity Logger integration (REQ-017, ADR-017)
    @Test
    fun testOpenKeyL10nDebugStringsAndSettingsActivityLoggerIntegration() {
        // (a) Verify localized strings for VI and EN
        for (lang in listOf(OpenKeyL10n.Language.VI, OpenKeyL10n.Language.EN)) {
            val section = OpenKeyL10n.Debug.sectionTitle(lang)
            val toggle = OpenKeyL10n.Debug.toggleTitle(lang)
            val subtitle = OpenKeyL10n.Debug.toggleSubtitle(lang)
            val copy = OpenKeyL10n.Debug.copyLogs(lang)
            val clear = OpenKeyL10n.Debug.clearLogs(lang)
            val empty = OpenKeyL10n.Debug.emptyLogs(lang)

            assertTrue("sectionTitle must not be blank for $lang", section.isNotBlank())
            assertTrue("toggleTitle must not be blank for $lang", toggle.isNotBlank())
            assertTrue("toggleSubtitle must not be blank for $lang", subtitle.isNotBlank())
            assertTrue("copyLogs must not be blank for $lang", copy.isNotBlank())
            assertTrue("clearLogs must not be blank for $lang", clear.isNotBlank())
            assertTrue("emptyLogs must not be blank for $lang", empty.isNotBlank())

            val map = OpenKeyL10n.Debug.getStrings(lang)
            assertEquals(6, map.size)
        }

        // (b) OpenKeyLogger clear and clipboard export test
        val tempLog = File.createTempFile("openkey-app-test", ".log")
        tempLog.deleteOnExit()
        OpenKeyLogger.shared.configureLogFile(tempLog)
        OpenKeyLogger.shared.clearLog()
        OpenKeyLogger.shared.isDebugEnabled = true

        OpenKeyLogger.shared.log(LogSubsystem.LIFECYCLE) { "App test lifecycle event" }
        val logs = OpenKeyLogger.shared.readRecentLogs()
        assertTrue(logs.contains("App test lifecycle event"))

        // Simulate ClipboardManager action
        assertTrue("Recent logs text for clipboard must not be empty", logs.isNotBlank())
        val clipData = try {
            android.content.ClipData.newPlainText("OpenKey Logs", logs)
        } catch (_: Exception) {
            null
        }
        if (clipData != null) {
            assertEquals(1, clipData.itemCount)
            assertEquals(logs, clipData.getItemAt(0).text?.toString())
        }

        // Verify SettingsActivity has ClipboardManager and OpenKeyLogger integration
        val possibleSettingsPaths = listOf(
            File("src/main/java/com/tuyenmai/openkey/android/settings/SettingsActivity.kt"),
            File("app/src/main/java/com/tuyenmai/openkey/android/settings/SettingsActivity.kt"),
            File("../app/src/main/java/com/tuyenmai/openkey/android/settings/SettingsActivity.kt"),
            File("Sources/OpenKey/android/app/src/main/java/com/tuyenmai/openkey/android/settings/SettingsActivity.kt")
        )
        val settingsFile = possibleSettingsPaths.firstOrNull { it.exists() }
        if (settingsFile != null) {
            val content = settingsFile.readText()
            assertTrue("SettingsActivity must reference ClipboardManager", content.contains("ClipboardManager"))
            assertTrue("SettingsActivity must reference OpenKeyLogger", content.contains("OpenKeyLogger"))
            assertTrue("SettingsActivity must reference SettingsSectionCard", content.contains("SettingsSectionCard"))
            assertTrue("SettingsActivity must reference SettingSwitchRow", content.contains("SettingSwitchRow"))
        }

        // Clear log
        OpenKeyLogger.shared.clearLog()
        val clearedLogs = OpenKeyLogger.shared.readRecentLogs()
        assertFalse(clearedLogs.contains("App test lifecycle event"))
        assertTrue(clearedLogs.startsWith("# OpenKeyAndroidVersionStamp:"))

        // Teardown
        OpenKeyLogger.shared.isDebugEnabled = false
    }

    // 11. Samsung Galaxy A37 One UI 6/7 Visual Polish and Vector Icons (REQ-020, ADR-021)
    @Test
    fun testSamsungOneUi6VisualPolishAndVectorIcons() {
        val possibleKeyButtonPaths = listOf(
            File("src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("../app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("Sources/OpenKey/android/app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt")
        )
        val keyButtonFile = possibleKeyButtonPaths.firstOrNull { it.exists() }
        assertNotNull("KeyButton.kt must exist", keyButtonFile)
        val keyButtonContent = keyButtonFile!!.readText()

        assertTrue("KeyButton must contain 23.sp", keyButtonContent.contains("23.sp"))
        assertTrue("KeyButton must contain 21.sp", keyButtonContent.contains("21.sp"))
        assertTrue("KeyButton must contain 20.sp", keyButtonContent.contains("20.sp"))
        assertTrue("KeyButton must contain KeyboardVectorIcons", keyButtonContent.contains("KeyboardVectorIcons"))
        assertTrue("KeyButton must contain pressedKeycapColor", keyButtonContent.contains("pressedKeycapColor"))
        assertTrue("KeyButton must contain pressedFunctionalKeycapColor", keyButtonContent.contains("pressedFunctionalKeycapColor"))

        val possibleCandidatePaths = listOf(
            File("src/main/java/com/tuyenmai/openkey/android/ui/CandidateStripView.kt"),
            File("app/src/main/java/com/tuyenmai/openkey/android/ui/CandidateStripView.kt"),
            File("../app/src/main/java/com/tuyenmai/openkey/android/ui/CandidateStripView.kt"),
            File("Sources/OpenKey/android/app/src/main/java/com/tuyenmai/openkey/android/ui/CandidateStripView.kt")
        )
        val candidateFile = possibleCandidatePaths.firstOrNull { it.exists() }
        assertNotNull("CandidateStripView.kt must exist", candidateFile)
        val candidateContent = candidateFile!!.readText()

        assertTrue("CandidateStripView must contain 17.sp", candidateContent.contains("17.sp"))
        assertFalse("CandidateStripView must not contain SettingsGearIcon", candidateContent.contains("SettingsGearIcon"))
        assertFalse("CandidateStripView must not contain OneHandedModeIcon", candidateContent.contains("OneHandedModeIcon"))
        assertFalse("CandidateStripView must not contain 'English (US)'", candidateContent.contains("\"English (US)\""))
        assertFalse("CandidateStripView must not contain 'Tiếng Việt •'", candidateContent.contains("\"Tiếng Việt •"))
        assertFalse("CandidateStripView must not contain 'Left 82%'", candidateContent.contains("\"Left 82%\""))
        assertFalse("CandidateStripView must not contain 'Right 82%'", candidateContent.contains("\"Right 82%\""))
    }

    // 12. Persistent APK Signing & Monotonic Versioning (REQ-022, ADR-023)
    @Test
    fun testKeystoreAndVersionCodeConfig() {
        val possibleKeystorePaths = listOf(
            File("openkey-debug.keystore"),
            File("app/openkey-debug.keystore"),
            File("../app/openkey-debug.keystore"),
            File("Sources/OpenKey/android/app/openkey-debug.keystore")
        )
        val keystoreFile = possibleKeystorePaths.firstOrNull { it.exists() }
        assertNotNull("openkey-debug.keystore must exist in app module", keystoreFile)
        assertTrue("openkey-debug.keystore must be non-empty", keystoreFile!!.length() > 0)

        val possibleBuildGradlePaths = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts"),
            File("Sources/OpenKey/android/app/build.gradle.kts")
        )
        val buildGradleFile = possibleBuildGradlePaths.firstOrNull { it.exists() }
        assertNotNull("app/build.gradle.kts must exist", buildGradleFile)
        val buildGradleContent = buildGradleFile!!.readText()

        assertTrue("build.gradle.kts must contain resolveGitVersionCode", buildGradleContent.contains("resolveGitVersionCode"))
        assertTrue("build.gradle.kts must configure openkey-debug.keystore", buildGradleContent.contains("openkey-debug.keystore"))
        assertTrue("build.gradle.kts must configure signingConfigs", buildGradleContent.contains("signingConfigs"))
    }

    // 13. SystemSpellCheckerBridge & Manifest Permissions (REQ-024, ADR-025)
    @Test
    fun testSystemSpellCheckerBridgeExistsAndZeroInternetPermissions() {
        val possibleBridgePaths = listOf(
            File("src/main/java/com/tuyenmai/openkey/android/SystemSpellCheckerBridge.kt"),
            File("app/src/main/java/com/tuyenmai/openkey/android/SystemSpellCheckerBridge.kt"),
            File("../app/src/main/java/com/tuyenmai/openkey/android/SystemSpellCheckerBridge.kt"),
            File("Sources/OpenKey/android/app/src/main/java/com/tuyenmai/openkey/android/SystemSpellCheckerBridge.kt")
        )
        val bridgeFile = possibleBridgePaths.firstOrNull { it.exists() }
        assertNotNull("SystemSpellCheckerBridge.kt must exist", bridgeFile)

        val possibleManifestPaths = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("Sources/OpenKey/android/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = possibleManifestPaths.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)
        val manifestContent = manifestFile!!.readText()

        assertFalse("AndroidManifest.xml must not declare INTERNET permission", manifestContent.contains("android.permission.INTERNET"))
        assertFalse("AndroidManifest.xml must not declare ACCESS_NETWORK_STATE permission", manifestContent.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    // 14. Voronoi Touch Hit-Box Seam Elimination (REQ-026, ADR-027, AC-026.1)
    @Test
    fun testKeyButtonPointerInputBeforePadding() {
        val possiblePaths = listOf(
            File("src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("../app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt"),
            File("Sources/OpenKey/android/app/src/main/java/com/tuyenmai/openkey/android/ui/KeyButton.kt")
        )
        val file = possiblePaths.firstOrNull { it.exists() }
        assertNotNull("KeyButton.kt must exist", file)
        val content = file!!.readText()
        val pointerIndex = content.indexOf(".pointerInput(key)")
        val paddingIndex = content.indexOf(".padding(horizontal = gapH, vertical = gapV)")
        assertTrue("pointerInput must exist in KeyButton.kt", pointerIndex != -1)
        assertTrue("padding must exist in KeyButton.kt", paddingIndex != -1)
        assertTrue("pointerInput must appear before padding in KeyButton.kt", pointerIndex < paddingIndex)
    }

    // 15. Codebase Modularity & Shared Lexicon Invariants (REQ-028, ADR-029, AC-028.2–AC-028.4)
    @Test
    fun testCodebaseModularityAndSharedLexiconInvariants() {
        val dictFile = listOf(
            File("../openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseDictionary.kt"),
            File("openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseDictionary.kt"),
            File("Sources/OpenKey/android/openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseDictionary.kt")
        ).firstOrNull { it.exists() }
        assertNotNull("VietnameseDictionary.kt must exist", dictFile)
        val dictLines = dictFile!!.readLines()
        assertTrue("VietnameseDictionary.kt must have < 550 lines, got ${dictLines.size}", dictLines.size < 550)
        assertFalse("VietnameseDictionary.kt must not contain countVowelGroups", dictFile.readText().contains("countVowelGroups"))

        val autoSwitchFile = listOf(
            File("../openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/BilingualAutoSwitchEngine.kt"),
            File("openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/BilingualAutoSwitchEngine.kt"),
            File("Sources/OpenKey/android/openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/BilingualAutoSwitchEngine.kt")
        ).firstOrNull { it.exists() }
        assertNotNull("BilingualAutoSwitchEngine.kt must exist", autoSwitchFile)
        val autoSwitchLines = autoSwitchFile!!.readLines()
        val fnStartIndex = autoSwitchLines.indexOfFirst { it.contains("fun evaluateWordBreakRestore") }
        assertTrue("evaluateWordBreakRestore must exist", fnStartIndex != -1)
        var fnEndIndex = autoSwitchLines.subList(fnStartIndex + 1, autoSwitchLines.size)
            .indexOfFirst { it.matches(Regex("^    (private )?fun .*")) }
        if (fnEndIndex == -1) {
            fnEndIndex = autoSwitchLines.size
        } else {
            fnEndIndex += fnStartIndex + 1
        }
        val fnLength = fnEndIndex - fnStartIndex
        assertTrue("evaluateWordBreakRestore must be < 120 lines, got $fnLength", fnLength < 120)

        val engineFile = listOf(
            File("../openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseEngine.kt"),
            File("openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseEngine.kt"),
            File("Sources/OpenKey/android/openkey-core/src/main/kotlin/com/tuyenmai/openkey/core/VietnameseEngine.kt")
        ).firstOrNull { it.exists() }
        assertNotNull("VietnameseEngine.kt must exist", engineFile)
        assertFalse("VietnameseEngine.kt must not contain NavSavedSnapshot", engineFile!!.readText().contains("NavSavedSnapshot"))

        val syllablesFile = listOf(
            File("../../shared-data/vietnamese_syllables.txt"),
            File("../shared-data/vietnamese_syllables.txt"),
            File("shared-data/vietnamese_syllables.txt"),
            File("Sources/OpenKey/shared-data/vietnamese_syllables.txt")
        ).firstOrNull { it.exists() }
        assertNotNull("Sources/OpenKey/shared-data/vietnamese_syllables.txt must exist", syllablesFile)
        assertTrue("vietnamese_syllables.txt must be non-empty", syllablesFile!!.length() > 0)
    }
}
