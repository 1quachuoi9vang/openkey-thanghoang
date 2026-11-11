package com.tuyenmai.openkey.android.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuyenmai.openkey.android.ime.SharedPreferencesKeyValueStorage
import com.tuyenmai.openkey.android.ui.KeyboardScreen
import com.tuyenmai.openkey.core.AutoWordingEngine
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.OneHandedMode
import com.tuyenmai.openkey.core.OpenKeyL10n
import com.tuyenmai.openkey.core.OpenKeyLogger
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.TextCommitterTarget

/**
 * Settings & Onboarding Activity for OpenKey Android (REQ-010, ADR-010).
 * Jetpack Compose Material 3 UI with One UI ergonomics, interactive layout preview,
 * engine controls, custom shorthand editor, and Direct-Boot persistence.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var settingsStore: OpenKeySettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = SharedPreferencesKeyValueStorage(this)
        settingsStore = OpenKeySettingsStore(storage)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(settingsStore = settingsStore)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settingsStore: OpenKeySettingsStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var uiLang by remember {
        mutableStateOf(if (settingsStore.language == 1) OpenKeyL10n.Language.VI else OpenKeyL10n.Language.EN)
    }

    val onboardingStrings = OpenKeyL10n.Onboarding.getStrings(uiLang)
    val ergoStrings = OpenKeyL10n.Ergonomics.getStrings(uiLang)
    val engineStrings = OpenKeyL10n.Engine.getStrings(uiLang)
    val autoWordingStrings = OpenKeyL10n.AutoWording.getStrings(uiLang)
    val aboutStrings = OpenKeyL10n.About.getStrings(uiLang)

    // Observables for settings
    var language by remember { mutableIntStateOf(settingsStore.language) }
    var inputType by remember { mutableIntStateOf(settingsStore.inputType) }
    var useModernOrthography by remember { mutableStateOf(settingsStore.useModernOrthography) }
    var checkSpelling by remember { mutableStateOf(settingsStore.checkSpelling) }
    var restoreIfWrongSpelling by remember { mutableStateOf(settingsStore.restoreIfWrongSpelling) }
    var bilingualAutoRestore by remember { mutableStateOf(settingsStore.bilingualAutoRestore) }
    var useSmartSwitchKey by remember { mutableStateOf(settingsStore.useSmartSwitchKey) }
    var showNumberRow by remember { mutableStateOf(settingsStore.showNumberRow) }
    var heightScalePercent by remember { mutableIntStateOf(settingsStore.heightScalePercent) }
    var bottomChinPaddingDp by remember { mutableIntStateOf(settingsStore.bottomChinPaddingDp) }
    var oneHandedMode by remember { mutableStateOf(settingsStore.oneHandedMode) }
    var hapticFeedbackEnabled by remember { mutableStateOf(settingsStore.hapticFeedbackEnabled) }
    var keyPopupPreviewEnabled by remember { mutableStateOf(settingsStore.keyPopupPreviewEnabled) }
    var autoWordingEnabled by remember { mutableStateOf(settingsStore.autoWordingEnabled) }
    var grammarRepairEnabled by remember { mutableStateOf(settingsStore.grammarRepairEnabled) }
    var shorthandExpansionEnabled by remember { mutableStateOf(settingsStore.shorthandExpansionEnabled) }
    var candidateSuggestionsEnabled by remember { mutableStateOf(settingsStore.candidateSuggestionsEnabled) }
    var customShorthands by remember { mutableStateOf(settingsStore.getCustomShorthands()) }

    DisposableEffect(settingsStore) {
        fun syncPreviewEngine(changedKey: String? = null) {
            com.tuyenmai.openkey.core.VietnameseEngine.syncConfig(
                language = if (changedKey == null || changedKey == OpenKeySettingsStore.KEY_LANGUAGE) settingsStore.language else null,
                inputType = settingsStore.inputType,
                checkSpelling = if (settingsStore.checkSpelling) 1 else 0,
                useModernOrthography = if (settingsStore.useModernOrthography) 1 else 0,
                restoreIfWrongSpelling = if (settingsStore.restoreIfWrongSpelling) 1 else 0,
                useSmartSwitchKey = if (settingsStore.useSmartSwitchKey) 1 else 0,
                bilingualAutoRestore = if (settingsStore.bilingualAutoRestore) 1 else 0
            )
            AutoWordingEngine.shared.syncConfig(
                isEnabled = settingsStore.autoWordingEnabled,
                grammarRepair = settingsStore.grammarRepairEnabled,
                shorthandExpansion = settingsStore.shorthandExpansionEnabled,
                candidateSuggestions = settingsStore.candidateSuggestionsEnabled,
                doubleSpacePeriod = settingsStore.doubleSpacePeriodEnabled
            )
            AutoWordingEngine.shared.customShorthands = settingsStore.getCustomShorthands().toMutableMap()
        }
        syncPreviewEngine()
        val listener: (String) -> Unit = { key ->
            when (key) {
                OpenKeySettingsStore.KEY_LANGUAGE -> language = settingsStore.language
                OpenKeySettingsStore.KEY_INPUT_TYPE -> inputType = settingsStore.inputType
                OpenKeySettingsStore.KEY_USE_MODERN_ORTHOGRAPHY -> useModernOrthography = settingsStore.useModernOrthography
                OpenKeySettingsStore.KEY_CHECK_SPELLING -> checkSpelling = settingsStore.checkSpelling
                OpenKeySettingsStore.KEY_RESTORE_IF_WRONG_SPELLING -> restoreIfWrongSpelling = settingsStore.restoreIfWrongSpelling
                OpenKeySettingsStore.KEY_BILINGUAL_AUTO_RESTORE -> bilingualAutoRestore = settingsStore.bilingualAutoRestore
                OpenKeySettingsStore.KEY_USE_SMART_SWITCH_KEY -> useSmartSwitchKey = settingsStore.useSmartSwitchKey
                OpenKeySettingsStore.KEY_SHOW_NUMBER_ROW -> showNumberRow = settingsStore.showNumberRow
                OpenKeySettingsStore.KEY_HEIGHT_SCALE_PERCENT -> heightScalePercent = settingsStore.heightScalePercent
                OpenKeySettingsStore.KEY_BOTTOM_CHIN_PADDING_DP -> bottomChinPaddingDp = settingsStore.bottomChinPaddingDp
                OpenKeySettingsStore.KEY_ONE_HANDED_MODE -> oneHandedMode = settingsStore.oneHandedMode
                OpenKeySettingsStore.KEY_HAPTIC_FEEDBACK_ENABLED -> hapticFeedbackEnabled = settingsStore.hapticFeedbackEnabled
                OpenKeySettingsStore.KEY_KEY_POPUP_PREVIEW_ENABLED -> keyPopupPreviewEnabled = settingsStore.keyPopupPreviewEnabled
                OpenKeySettingsStore.KEY_AUTO_WORDING_ENABLED -> autoWordingEnabled = settingsStore.autoWordingEnabled
                OpenKeySettingsStore.KEY_GRAMMAR_REPAIR_ENABLED -> grammarRepairEnabled = settingsStore.grammarRepairEnabled
                OpenKeySettingsStore.KEY_SHORTHAND_EXPANSION_ENABLED -> shorthandExpansionEnabled = settingsStore.shorthandExpansionEnabled
                OpenKeySettingsStore.KEY_CANDIDATE_SUGGESTIONS_ENABLED -> candidateSuggestionsEnabled = settingsStore.candidateSuggestionsEnabled
                OpenKeySettingsStore.KEY_CUSTOM_SHORTHANDS -> customShorthands = settingsStore.getCustomShorthands()
            }
            syncPreviewEngine(key)
        }
        settingsStore.addListener(listener)
        onDispose { settingsStore.removeListener(listener) }
    }

    // Check system IME status
    fun isImeEnabled(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    fun isImeSelected(): Boolean {
        val defaultIme = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return defaultIme?.contains(context.packageName) == true
    }

    var imeEnabled by remember { mutableStateOf(isImeEnabled()) }
    var imeSelected by remember { mutableStateOf(isImeSelected()) }

    // Live typing test field
    var testText by remember { mutableStateOf("") }
    val previewCoordinator = remember { BatchReplacementCoordinator() }
    val previewAutoWording = remember { AutoWordingEngine.shared }
    val previewCommitter = remember {
        object : TextCommitterTarget {
            override fun beginBatchEdit() = true
            override fun endBatchEdit() = true
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0 && testText.isNotEmpty()) {
                    val dropCount = beforeLength.coerceAtMost(testText.length)
                    testText = testText.dropLast(dropCount)
                }
                return true
            }
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                testText += text
                return true
            }
            override fun sendKeyChar(char: Char): Boolean {
                testText += char
                return true
            }
            override fun performEditorAction(actionId: Int) = true
            override fun moveCursorBy(deltaChars: Int) = true
            override fun deletePreviousWord(): Boolean {
                if (testText.isEmpty()) return false
                var i = testText.length - 1
                while (i >= 0 && testText[i].isWhitespace()) i--
                while (i >= 0 && !testText[i].isWhitespace()) i--
                testText = testText.substring(0, (i + 1).coerceAtLeast(0))
                return true
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with VI | EN Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "OpenKey",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Large-Screen One UI Edition",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // UI Language Toggle Pill
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clickable { uiLang = OpenKeyL10n.Language.VI }
                        .background(
                            if (uiLang == OpenKeyL10n.Language.VI) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VI",
                        color = if (uiLang == OpenKeyL10n.Language.VI) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clickable { uiLang = OpenKeyL10n.Language.EN }
                        .background(
                            if (uiLang == OpenKeyL10n.Language.EN) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "EN",
                        color = if (uiLang == OpenKeyL10n.Language.EN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 1. 2-Step Onboarding Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (imeEnabled && imeSelected) onboardingStrings["onboardingComplete"] ?: "Ready to use!" else "Setup OpenKey Keyboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = onboardingStrings["enableKeyboardTitle"] ?: "Enable OpenKey",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = onboardingStrings["enableKeyboardDesc"] ?: "Allow in system settings",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        enabled = !imeEnabled
                    ) {
                        Text(if (imeEnabled) "✓" else "Step 1")
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = onboardingStrings["selectKeyboardTitle"] ?: "Select OpenKey",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = onboardingStrings["selectKeyboardDesc"] ?: "Set as default keyboard",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showInputMethodPicker()
                        },
                        enabled = imeEnabled && !imeSelected
                    ) {
                        Text(if (imeSelected) "✓" else "Step 2")
                    }
                }
            }
        }

        // 2. Live Sandbox Typing Field & Interactive Galaxy A37 Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = ergoStrings["sectionTitle"] ?: "Large-Screen Size & Ergonomics",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                OutlinedTextField(
                    value = testText,
                    onValueChange = { testText = it },
                    label = { Text(onboardingStrings["testKeyboardHint"] ?: "Tap here to test typing...") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Interactive Live KeyboardScreen Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(vertical = 4.dp)
                ) {
                    KeyboardScreen(
                        settingsStore = settingsStore,
                        committer = previewCommitter,
                        batchCoordinator = previewCoordinator,
                        autoWordingEngine = previewAutoWording,
                        enterActionLabel = "↵"
                    )
                }

                // Ergonomic Sliders & Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = ergoStrings["heightScaleTitle"] ?: "Height Scale", fontSize = 13.sp)
                        Text(text = "$heightScalePercent%", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = heightScalePercent.toFloat(),
                        onValueChange = { settingsStore.heightScalePercent = it.toInt() },
                        valueRange = 85f..125f,
                        steps = 7
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = ergoStrings["bottomChinTitle"] ?: "Bottom Chin Padding", fontSize = 13.sp)
                        Text(text = "${bottomChinPaddingDp}dp", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bottomChinPaddingDp.toFloat(),
                        onValueChange = { settingsStore.bottomChinPaddingDp = it.toInt() },
                        valueRange = 0f..48f,
                        steps = 11
                    )
                }

                // One-Handed Mode selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ergoStrings["oneHandedTitle"] ?: "One-Handed Mode", fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                    ) {
                        listOf(
                            OneHandedMode.OFF to (ergoStrings["oneHandedOff"] ?: "Off"),
                            OneHandedMode.LEFT to (ergoStrings["oneHandedLeft"] ?: "Left"),
                            OneHandedMode.RIGHT to (ergoStrings["oneHandedRight"] ?: "Right")
                        ).forEach { (mode, title) ->
                            Box(
                                modifier = Modifier
                                    .clickable { settingsStore.oneHandedMode = mode }
                                    .background(
                                        if (oneHandedMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = title,
                                    color = if (oneHandedMode == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Dedicated Number Row switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ergoStrings["numberRowTitle"] ?: "Dedicated Number Row", fontSize = 14.sp)
                    Switch(
                        checked = showNumberRow,
                        onCheckedChange = { settingsStore.showNumberRow = it }
                    )
                }

                // Haptics switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ergoStrings["hapticsTitle"] ?: "Haptic Feedback", fontSize = 14.sp)
                    Switch(
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = { settingsStore.hapticFeedbackEnabled = it }
                    )
                }

                // Popup preview switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = ergoStrings["popupPreviewTitle"] ?: "Key Popup Preview", fontSize = 14.sp)
                    Switch(
                        checked = keyPopupPreviewEnabled,
                        onCheckedChange = { settingsStore.keyPopupPreviewEnabled = it }
                    )
                }
            }
        }

        // 3. Vietnamese Engine & Bilingual Intelligence Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = engineStrings["sectionTitle"] ?: "Vietnamese Engine & Bilingual",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Input Method: Telex / VNI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["inputMethodTitle"] ?: "Input Method", fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { settingsStore.inputType = 0 }
                                .background(
                                    if (inputType == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Telex",
                                color = if (inputType == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickable { settingsStore.inputType = 1 }
                                .background(
                                    if (inputType == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "VNI",
                                color = if (inputType == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Default Language: VI / EN
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Default Language", fontSize = 14.sp)
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clickable { settingsStore.language = 1 }
                                .background(
                                    if (language == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "VI",
                                color = if (language == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickable { settingsStore.language = 0 }
                                .background(
                                    if (language == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "EN",
                                color = if (language == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Modern Orthography
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["modernOrthographyTitle"] ?: "Modern Orthography", fontSize = 14.sp)
                    Switch(checked = useModernOrthography, onCheckedChange = { settingsStore.useModernOrthography = it })
                }

                // Check Spelling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["checkSpellingTitle"] ?: "Check Spelling", fontSize = 14.sp)
                    Switch(checked = checkSpelling, onCheckedChange = { settingsStore.checkSpelling = it })
                }

                // Auto-Restore Wrong Spelling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["restoreWrongSpellingTitle"] ?: "Auto-Restore Wrong Spelling", fontSize = 14.sp)
                    Switch(checked = restoreIfWrongSpelling, onCheckedChange = { settingsStore.restoreIfWrongSpelling = it })
                }

                // Bilingual English Auto-Restore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["bilingualTitle"] ?: "Bilingual Auto-Restore", fontSize = 14.sp)
                    Switch(checked = bilingualAutoRestore, onCheckedChange = { settingsStore.bilingualAutoRestore = it })
                }

                // Smart Switch per app
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = engineStrings["smartSwitchTitle"] ?: "Per-App Smart Switch", fontSize = 14.sp)
                    Switch(checked = useSmartSwitchKey, onCheckedChange = { settingsStore.useSmartSwitchKey = it })
                }
            }
        }

        // 4. Auto Wording, Candidate Bar & Custom Shorthands Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = autoWordingStrings["sectionTitle"] ?: "Auto Wording & Shorthands",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Master Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = autoWordingStrings["autoWordingMasterTitle"] ?: "Enable Smart Wording",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Switch(
                        checked = autoWordingEnabled,
                        onCheckedChange = { settingsStore.autoWordingEnabled = it }
                    )
                }

                // Grammar Repair
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = autoWordingStrings["grammarRepairTitle"] ?: "Repair Transposition", fontSize = 13.sp)
                    Switch(
                        checked = grammarRepairEnabled,
                        onCheckedChange = { settingsStore.grammarRepairEnabled = it },
                        enabled = autoWordingEnabled
                    )
                }

                // Shorthand Expansion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = autoWordingStrings["shorthandTitle"] ?: "Shorthand Expansion", fontSize = 13.sp)
                    Switch(
                        checked = shorthandExpansionEnabled,
                        onCheckedChange = { settingsStore.shorthandExpansionEnabled = it },
                        enabled = autoWordingEnabled
                    )
                }

                // Candidate Suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = autoWordingStrings["candidateStripTitle"] ?: "Candidate Strip", fontSize = 13.sp)
                    Switch(
                        checked = candidateSuggestionsEnabled,
                        onCheckedChange = { settingsStore.candidateSuggestionsEnabled = it },
                        enabled = autoWordingEnabled
                    )
                }

                HorizontalDivider()

                // Custom Shorthands Editor
                Text(
                    text = autoWordingStrings["customShorthandEditorTitle"] ?: "Custom Shorthands",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                var newShortcut by remember { mutableStateOf("") }
                var newExpansion by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newShortcut,
                        onValueChange = { newShortcut = it },
                        label = { Text("Shortcut") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newExpansion,
                        onValueChange = { newExpansion = it },
                        label = { Text("Expansion") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newShortcut.isNotBlank() && newExpansion.isNotBlank()) {
                                settingsStore.setCustomShorthand(newShortcut.trim(), newExpansion.trim())
                                newShortcut = ""
                                newExpansion = ""
                            }
                        }
                    ) {
                        Text("Add")
                    }
                }

                if (customShorthands.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        customShorthands.forEach { (shortcut, expansion) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$shortcut → $expansion",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedButton(
                                    onClick = { settingsStore.removeCustomShorthand(shortcut) }
                                ) {
                                    Text("✕", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                var resetSuccessMessage by remember { mutableStateOf<String?>(null) }
                OutlinedButton(
                    onClick = {
                        AutoWordingEngine.shared.resetLearnedFrequencies()
                        resetSuccessMessage = autoWordingStrings["resetFrequenciesSuccess"] ?: "Reset completed"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(autoWordingStrings["resetFrequenciesTitle"] ?: "Reset Learned Word Frequencies")
                }

                if (resetSuccessMessage != null) {
                    Text(
                        text = resetSuccessMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 5. Debug & Diagnostics Card (REQ-017, ADR-017)
        var debugLogsText by remember { mutableStateOf(OpenKeyLogger.shared.readRecentLogs()) }
        var debugModeEnabled by remember { mutableStateOf(settingsStore.debugModeEnabled) }

        SettingsSectionCard(title = OpenKeyL10n.Debug.sectionTitle(uiLang)) {
            SettingSwitchRow(
                title = OpenKeyL10n.Debug.toggleTitle(uiLang),
                subtitle = OpenKeyL10n.Debug.toggleSubtitle(uiLang),
                checked = debugModeEnabled,
                onCheckedChange = {
                    debugModeEnabled = it
                    settingsStore.debugModeEnabled = it
                    debugLogsText = OpenKeyLogger.shared.readRecentLogs()
                }
            )

            HorizontalDivider()

            val displayLogs = debugLogsText.ifBlank { OpenKeyL10n.Debug.emptyLogs(uiLang) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = displayLogs,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clip = ClipData.newPlainText("OpenKey Logs", OpenKeyLogger.shared.readRecentLogs())
                        clipboard?.setPrimaryClip(clip)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(OpenKeyL10n.Debug.copyLogs(uiLang), fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = {
                        OpenKeyLogger.shared.clearLog()
                        debugLogsText = OpenKeyLogger.shared.readRecentLogs()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(OpenKeyL10n.Debug.clearLogs(uiLang), fontSize = 13.sp)
                }
            }
        }

        // 6. About Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = aboutStrings["sectionTitle"] ?: "About",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${aboutStrings["appName"] ?: "OpenKey Android"} — Large-Screen One UI Edition",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = aboutStrings["appVersion"] ?: "Version 2.0.0",
                    fontSize = 13.sp
                )
                Text(
                    text = "Implementor: Antigravity — Gemini 4 Argon (High)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = aboutStrings["privacyNotice"] ?: "100% Offline • Zero Internet Permission",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
