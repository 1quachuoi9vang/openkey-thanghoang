package com.tuyenmai.openkey.android.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tuyenmai.openkey.android.SystemSpellCheckerBridge
import com.tuyenmai.openkey.android.settings.SettingsActivity
import com.tuyenmai.openkey.android.ui.KeyboardScreen
import com.tuyenmai.openkey.core.AutoWordingEngine
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.BilingualAutoSwitchEngine
import com.tuyenmai.openkey.core.EditorContext
import com.tuyenmai.openkey.core.HookCodeState
import com.tuyenmai.openkey.core.InputContextEvaluator
import com.tuyenmai.openkey.core.InputEvaluation
import com.tuyenmai.openkey.core.KeyboardSessionController
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.SmartSwitchStore
import com.tuyenmai.openkey.core.VietnameseEngine
import com.tuyenmai.openkey.core.VietnameseEngineState

/**
 * OpenKey Android Input Method Service (REQ-001, REQ-005, REQ-006, REQ-009, ADR-005..ADR-010).
 * Fully Direct-Boot aware, zero network permissions, hosting Samsung One UI Jetpack Compose Keyboard.
 */
class OpenKeyImeService : InputMethodService() {

    val lifecycleOwner = ImeComposeLifecycleOwner()

    lateinit var settingsStore: OpenKeySettingsStore
        internal set
    lateinit var smartSwitchStore: SmartSwitchStore
        internal set
    lateinit var committer: InputConnectionCommitter
        internal set
    lateinit var batchCoordinator: BatchReplacementCoordinator
        internal set
    lateinit var autoWordingEngine: AutoWordingEngine
        internal set
    lateinit var sessionController: KeyboardSessionController
        internal set

    private var spellCheckerBridge: SystemSpellCheckerBridge? = null

    var currentEvaluation: InputEvaluation = InputEvaluation(
        bypassVietnamese = false,
        isSecureField = false,
        enterActionLabel = "↵"
    )
        internal set

    var currentPackageName: String = ""
        private set

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()

        com.tuyenmai.openkey.core.VietnameseDictionary.prewarmAsync()

        val storage = SharedPreferencesKeyValueStorage(this)
        settingsStore = SharedPreferencesKeyValueStorage.sharedSettingsStore(this)
        smartSwitchStore = SmartSwitchStore(storage)
        batchCoordinator = BatchReplacementCoordinator()
        committer = InputConnectionCommitter(
            inputConnectionProvider = { currentInputConnection },
            keyEventSender = { sendDownUpKeyEvents(it.keyCode) },
            batchCoordinator = batchCoordinator
        )
        autoWordingEngine = AutoWordingEngine.shared
        autoWordingEngine.asyncCandidateExecutionEnabled = true

        sessionController = KeyboardSessionController(
            settingsStore = settingsStore,
            smartSwitchStore = smartSwitchStore,
            committer = committer,
            batchCoordinator = batchCoordinator,
            autoWordingEngine = autoWordingEngine
        )

        val bridge = SystemSpellCheckerBridge(this)
        spellCheckerBridge = bridge
        autoWordingEngine.spellCheckerBridge = bridge

        syncSettingsToEngine()

        settingsStore.addListener { changedKey ->
            syncSettingsToEngine(changedKey)
        }
    }

    internal fun syncSettingsToEngine(changedKey: String? = null) {
        val state = VietnameseEngineState.shared
        if (changedKey == null || changedKey == OpenKeySettingsStore.KEY_LANGUAGE) {
            state.vLanguage = settingsStore.language
        }
        state.vInputType = settingsStore.inputType
        state.vUseModernOrthography = if (settingsStore.useModernOrthography) 1 else 0
        state.vCheckSpelling = if (settingsStore.checkSpelling) 1 else 0
        state.vRestoreIfWrongSpelling = if (settingsStore.restoreIfWrongSpelling) 1 else 0
        state.vBilingualAutoRestore = if (settingsStore.bilingualAutoRestore) 1 else 0
        state.vUseSmartSwitchKey = if (settingsStore.useSmartSwitchKey) 1 else 0

        autoWordingEngine.syncConfig(
            isEnabled = settingsStore.autoWordingEnabled,
            grammarRepair = settingsStore.grammarRepairEnabled,
            shorthandExpansion = settingsStore.shorthandExpansionEnabled,
            candidateSuggestions = settingsStore.candidateSuggestionsEnabled,
            doubleSpacePeriod = settingsStore.doubleSpacePeriodEnabled
        )
        autoWordingEngine.customShorthands = settingsStore.getCustomShorthands().toMutableMap()
    }

    override fun onCreateInputView(): View {
        val decorView = window?.window?.decorView
        lifecycleOwner.attachToDecorView(decorView)

        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                KeyboardScreen(
                    settingsStore = settingsStore,
                    committer = committer,
                    batchCoordinator = batchCoordinator,
                    autoWordingEngine = autoWordingEngine,
                    sessionController = sessionController,
                    enterActionLabel = currentEvaluation.enterActionLabel,
                    onOpenSettings = {
                        val intent = Intent(this@OpenKeyImeService, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()

        if (info != null) {
            currentPackageName = info.packageName ?: ""
            val context = EditorContext(
                inputType = info.inputType,
                imeOptions = info.imeOptions,
                packageName = currentPackageName
            )
            sessionController.onStartInput(
                context = context,
                initialSelStart = info.initialSelStart,
                initialSelEnd = info.initialSelEnd
            )
            currentEvaluation = InputContextEvaluator.evaluate(context)
        } else {
            VietnameseEngine.resetSession()
            autoWordingEngine.clearCandidates()
            batchCoordinator.resetExpectedCursor(0)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleOwner.onPause()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        VietnameseEngine.resetSession()
        autoWordingEngine.clearCandidates()
        super.onFinishInput()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        sessionController.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
    }

    override fun onDestroy() {
        spellCheckerBridge?.close()
        spellCheckerBridge = null
        autoWordingEngine.spellCheckerBridge = null
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
