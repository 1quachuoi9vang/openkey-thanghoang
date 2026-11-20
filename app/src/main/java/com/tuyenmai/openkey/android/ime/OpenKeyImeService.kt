package com.tuyenmai.openkey.android.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputMethodSubtype
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tuyenmai.openkey.android.SystemSpellCheckerBridge
import com.tuyenmai.openkey.android.settings.SettingsActivity
import com.tuyenmai.openkey.android.ui.KeyboardScreen
import com.tuyenmai.openkey.core.AutoWordingEngine
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.EditorContext
import com.tuyenmai.openkey.core.InputContextEvaluator
import com.tuyenmai.openkey.core.InputEvaluation
import com.tuyenmai.openkey.core.KeyboardSessionController
import com.tuyenmai.openkey.core.OpenKeyLogger
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.SmartSwitchStore
import com.tuyenmai.openkey.core.VietnameseEngine
import java.io.File

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
    private var settingsListener: ((String) -> Unit)? = null
    private var inlineSuggestionViews by mutableStateOf<List<View>>(emptyList())

    var currentEvaluation: InputEvaluation = InputEvaluation(
        bypassVietnamese = false,
        isSecureField = false,
        enterActionLabel = "↵"
    )
        internal set

    var currentPackageName: String = ""
        private set

    private var currentInputType: Int = InputContextEvaluator.TYPE_CLASS_TEXT

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()

        val deviceProtectedContext = try {
            createDeviceProtectedStorageContext() ?: this
        } catch (_: Exception) {
            this
        }
        val baseCacheDir = deviceProtectedContext.cacheDir ?: cacheDir
        if (baseCacheDir != null) {
            OpenKeyLogger.shared.configureLogFile(File(baseCacheDir, "OpenKeyAndroid/openkey-debug.log"))
            OpenKeyLogger.shared.pruneOutdatedLogsIfNeeded()
        }

        com.tuyenmai.openkey.core.VietnameseDictionary.prewarmAsync()

        val storage = SharedPreferencesKeyValueStorage(this)
        settingsStore = SharedPreferencesKeyValueStorage.sharedSettingsStore(this)
        smartSwitchStore = SmartSwitchStore(storage)
        batchCoordinator = BatchReplacementCoordinator()
        committer = InputConnectionCommitter(
            inputConnectionProvider = { currentInputConnection },
            batchCoordinator = batchCoordinator,
            keyCodeSender = { sendDownUpKeyEvents(it) }
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

        val bridge = SystemSpellCheckerBridge(this) { word, _ ->
            if (::sessionController.isInitialized &&
                sessionController.editorUiState.language == 0 &&
                !sessionController.editorUiState.suppressSuggestions &&
                sessionController.composingWord.equals(word, ignoreCase = true)
            ) {
                autoWordingEngine.didProcessEnglishKeyEvent(
                    charCode = 0,
                    isBackspace = false,
                    composingWord = sessionController.composingWord,
                    previousWord = sessionController.previousWord
                )
            }
        }
        spellCheckerBridge = bridge
        autoWordingEngine.spellCheckerBridge = bridge

        syncSettingsToEngine()

        val listener: (String) -> Unit = { changedKey ->
            syncSettingsToEngine(changedKey)
        }
        settingsListener = listener
        settingsStore.addListener(listener)
    }

    internal fun syncSettingsToEngine(changedKey: String? = null) {
        val activeLang = if (changedKey == OpenKeySettingsStore.KEY_LANGUAGE) {
            settingsStore.language
        } else if (
            ::sessionController.isInitialized &&
            settingsStore.useSmartSwitchKey &&
            sessionController.editorUiState.packageName.isNotEmpty()
        ) {
            sessionController.editorUiState.language
        } else {
            settingsStore.language
        }
        VietnameseEngine.syncConfig(
            language = if (changedKey == null || changedKey == OpenKeySettingsStore.KEY_LANGUAGE) activeLang else null,
            inputType = settingsStore.inputType,
            useModernOrthography = if (settingsStore.useModernOrthography) 1 else 0,
            checkSpelling = if (settingsStore.checkSpelling) 1 else 0,
            restoreIfWrongSpelling = if (settingsStore.restoreIfWrongSpelling) 1 else 0,
            useSmartSwitchKey = if (settingsStore.useSmartSwitchKey) 1 else 0,
            bilingualAutoRestore = if (settingsStore.bilingualAutoRestore) 1 else 0,
            standaloneW = if (settingsStore.standaloneWEnabled) 1 else 0,
            quickTelex = if (settingsStore.quickTelexEnabled) 1 else 0,
            quickConsonant = if (settingsStore.quickConsonantEnabled) 1 else 0
        )

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
                    inlineSuggestionViews = inlineSuggestionViews,
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

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val heightPx = (40f * density).toInt()
        val minWidthPx = (80f * density).toInt()
        val maxWidthPx = (resources.displayMetrics.widthPixels * 0.75f).toInt().coerceAtLeast(minWidthPx)
        val style = InlineSuggestionUi.newStyleBuilder().build()
        val stylesBundle = UiVersions.newStylesBuilder().addStyle(style).build()
        val spec = InlinePresentationSpec.Builder(
            Size(minWidthPx, heightPx),
            Size(maxWidthPx, heightPx)
        ).setStyle(stylesBundle).build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(3)
            .build()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) {
            inlineSuggestionViews = emptyList()
            return false
        }
        val density = resources.displayMetrics.density.coerceAtLeast(1f)
        val heightPx = (40f * density).toInt()
        val total = suggestions.size.coerceAtMost(3)
        val inflated = arrayOfNulls<View>(total)
        var remaining = total
        for (i in 0 until total) {
            suggestions[i].inflate(
                this,
                Size(ViewGroup.LayoutParams.WRAP_CONTENT, heightPx),
                mainExecutor
            ) { view ->
                inflated[i] = view
                remaining--
                if (remaining == 0) {
                    inlineSuggestionViews = inflated.filterNotNull()
                }
            }
        }
        return true
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        if (::sessionController.isInitialized) {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                newSubtype.languageTag.ifEmpty { @Suppress("DEPRECATION") newSubtype.locale }
            } else {
                @Suppress("DEPRECATION") newSubtype.locale
            }
            sessionController.onSystemSubtypeChanged(tag)
        }
    }

    private fun applyEditorInfo(info: EditorInfo?) {
        if (info != null) {
            currentPackageName = info.packageName ?: ""
            currentInputType = info.inputType
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
            val capsMode = currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
            sessionController.syncCursorCapsMode(capsMode)
        } else {
            currentInputType = InputContextEvaluator.TYPE_CLASS_TEXT
            VietnameseEngine.resetSession()
            autoWordingEngine.clearCandidates()
            batchCoordinator.resetExpectedCursor(0)
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) {
            inlineSuggestionViews = emptyList()
        }
        applyEditorInfo(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleOwner.onResume()
        applyEditorInfo(info)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inlineSuggestionViews = emptyList()
        lifecycleOwner.onPause()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        inlineSuggestionViews = emptyList()
        VietnameseEngine.resetSession()
        autoWordingEngine.clearCandidates()
        batchCoordinator.resetExpectedCursor(0)
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
        val capsMode = currentInputConnection?.getCursorCapsMode(currentInputType) ?: 0
        sessionController.syncCursorCapsMode(capsMode)
    }

    override fun onDestroy() {
        inlineSuggestionViews = emptyList()
        settingsListener?.let { listener ->
            if (::settingsStore.isInitialized) {
                settingsStore.removeListener(listener)
            }
        }
        settingsListener = null
        if (::sessionController.isInitialized) {
            sessionController.dispose()
        }
        spellCheckerBridge?.close()
        spellCheckerBridge = null
        if (::autoWordingEngine.isInitialized) {
            autoWordingEngine.spellCheckerBridge = null
        }
        lifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
