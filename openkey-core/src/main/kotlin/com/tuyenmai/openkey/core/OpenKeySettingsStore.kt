package com.tuyenmai.openkey.core

data class OpenKeySettingsSnapshot(
    val language: Int = 1,
    val inputType: Int = 0,
    val useModernOrthography: Boolean = true,
    val checkSpelling: Boolean = true,
    val restoreIfWrongSpelling: Boolean = true,
    val bilingualAutoRestore: Boolean = true,
    val useSmartSwitchKey: Boolean = true,
    val showNumberRow: Boolean = true,
    val heightScalePercent: Int = 100,
    val bottomChinPaddingDp: Int = 14,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val hapticFeedbackEnabled: Boolean = true,
    val keyPopupPreviewEnabled: Boolean = true,
    val grammarRepairEnabled: Boolean = true,
    val shorthandExpansionEnabled: Boolean = true,
    val candidateSuggestionsEnabled: Boolean = true,
    val doubleSpacePeriodEnabled: Boolean = false,
    val autoWordingEnabled: Boolean = true,
    val debugModeEnabled: Boolean = false
)

class OpenKeySettingsStore(private val storage: KeyValueStorage) {

    companion object Keys {
        const val KEY_LANGUAGE = "setting_language"
        const val KEY_INPUT_TYPE = "setting_input_type"
        const val KEY_USE_MODERN_ORTHOGRAPHY = "setting_use_modern_orthography"
        const val KEY_CHECK_SPELLING = "setting_check_spelling"
        const val KEY_RESTORE_IF_WRONG_SPELLING = "setting_restore_if_wrong_spelling"
        const val KEY_BILINGUAL_AUTO_RESTORE = "setting_bilingual_auto_restore"
        const val KEY_USE_SMART_SWITCH_KEY = "setting_use_smart_switch_key"
        const val KEY_SHOW_NUMBER_ROW = "setting_show_number_row"
        const val KEY_HEIGHT_SCALE_PERCENT = "setting_height_scale_percent"
        const val KEY_BOTTOM_CHIN_PADDING_DP = "setting_bottom_chin_padding_dp"
        const val KEY_ONE_HANDED_MODE = "setting_one_handed_mode"
        const val KEY_HAPTIC_FEEDBACK_ENABLED = "setting_haptic_feedback_enabled"
        const val KEY_KEY_POPUP_PREVIEW_ENABLED = "setting_key_popup_preview_enabled"
        const val KEY_AUTO_WORDING_ENABLED = "setting_auto_wording_enabled"
        const val KEY_GRAMMAR_REPAIR_ENABLED = "setting_grammar_repair_enabled"
        const val KEY_SHORTHAND_EXPANSION_ENABLED = "setting_shorthand_expansion_enabled"
        const val KEY_CANDIDATE_SUGGESTIONS_ENABLED = "setting_candidate_suggestions_enabled"
        const val KEY_DOUBLE_SPACE_PERIOD_ENABLED = "setting_double_space_period_enabled"
        const val KEY_CUSTOM_SHORTHANDS = "setting_custom_shorthands"
        const val KEY_DEBUG_MODE_ENABLED = "openkey.debugModeEnabled"
    }

    private val listeners = mutableListOf<(String) -> Unit>()

    init {
        val initialDebug = storage.getBoolean(Keys.KEY_DEBUG_MODE_ENABLED, false)
        OpenKeyLogger.shared.isDebugEnabled = initialDebug
    }

    @Synchronized
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }

    fun notifyChanged(key: String = Keys.KEY_DEBUG_MODE_ENABLED) {
        val currentListeners = synchronized(this) { listeners.toList() }
        for (listener in currentListeners) {
            listener(key)
        }
    }

    var language: Int
        get() = storage.getInt(KEY_LANGUAGE, 1)
        set(value) {
            storage.putInt(KEY_LANGUAGE, value)
            notifyChanged(KEY_LANGUAGE)
        }

    var inputType: Int
        get() = storage.getInt(KEY_INPUT_TYPE, 0)
        set(value) {
            val clamped = value.coerceIn(0, 1)
            storage.putInt(KEY_INPUT_TYPE, clamped)
            notifyChanged(KEY_INPUT_TYPE)
        }

    var useModernOrthography: Boolean
        get() = storage.getBoolean(KEY_USE_MODERN_ORTHOGRAPHY, true)
        set(value) {
            storage.putBoolean(KEY_USE_MODERN_ORTHOGRAPHY, value)
            notifyChanged(KEY_USE_MODERN_ORTHOGRAPHY)
        }

    var checkSpelling: Boolean
        get() = storage.getBoolean(KEY_CHECK_SPELLING, true)
        set(value) {
            storage.putBoolean(KEY_CHECK_SPELLING, value)
            notifyChanged(KEY_CHECK_SPELLING)
        }

    var restoreIfWrongSpelling: Boolean
        get() = storage.getBoolean(KEY_RESTORE_IF_WRONG_SPELLING, true)
        set(value) {
            storage.putBoolean(KEY_RESTORE_IF_WRONG_SPELLING, value)
            notifyChanged(KEY_RESTORE_IF_WRONG_SPELLING)
        }

    var bilingualAutoRestore: Boolean
        get() = storage.getBoolean(KEY_BILINGUAL_AUTO_RESTORE, true)
        set(value) {
            storage.putBoolean(KEY_BILINGUAL_AUTO_RESTORE, value)
            notifyChanged(KEY_BILINGUAL_AUTO_RESTORE)
        }

    var useSmartSwitchKey: Boolean
        get() = storage.getBoolean(KEY_USE_SMART_SWITCH_KEY, true)
        set(value) {
            storage.putBoolean(KEY_USE_SMART_SWITCH_KEY, value)
            notifyChanged(KEY_USE_SMART_SWITCH_KEY)
        }

    var showNumberRow: Boolean
        get() = storage.getBoolean(KEY_SHOW_NUMBER_ROW, true)
        set(value) {
            storage.putBoolean(KEY_SHOW_NUMBER_ROW, value)
            notifyChanged(KEY_SHOW_NUMBER_ROW)
        }

    var heightScalePercent: Int
        get() = storage.getInt(KEY_HEIGHT_SCALE_PERCENT, 100)
        set(value) {
            val clamped = value.coerceIn(85, 125)
            storage.putInt(KEY_HEIGHT_SCALE_PERCENT, clamped)
            notifyChanged(KEY_HEIGHT_SCALE_PERCENT)
        }

    var bottomChinPaddingDp: Int
        get() = storage.getInt(KEY_BOTTOM_CHIN_PADDING_DP, 14)
        set(value) {
            val clamped = value.coerceIn(0, 48)
            storage.putInt(KEY_BOTTOM_CHIN_PADDING_DP, clamped)
            notifyChanged(KEY_BOTTOM_CHIN_PADDING_DP)
        }

    var oneHandedMode: OneHandedMode
        get() {
            val name = storage.getString(KEY_ONE_HANDED_MODE, OneHandedMode.OFF.name)
            return try {
                OneHandedMode.valueOf(name)
            } catch (e: Exception) {
                OneHandedMode.OFF
            }
        }
        set(value) {
            storage.putString(KEY_ONE_HANDED_MODE, value.name)
            notifyChanged(KEY_ONE_HANDED_MODE)
        }

    var hapticFeedbackEnabled: Boolean
        get() = storage.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, value)
            notifyChanged(KEY_HAPTIC_FEEDBACK_ENABLED)
        }

    var keyPopupPreviewEnabled: Boolean
        get() = storage.getBoolean(KEY_KEY_POPUP_PREVIEW_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_KEY_POPUP_PREVIEW_ENABLED, value)
            notifyChanged(KEY_KEY_POPUP_PREVIEW_ENABLED)
        }

    var grammarRepairEnabled: Boolean
        get() = storage.getBoolean(KEY_GRAMMAR_REPAIR_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_GRAMMAR_REPAIR_ENABLED, value)
            notifyChanged(KEY_GRAMMAR_REPAIR_ENABLED)
        }

    var shorthandExpansionEnabled: Boolean
        get() = storage.getBoolean(KEY_SHORTHAND_EXPANSION_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_SHORTHAND_EXPANSION_ENABLED, value)
            notifyChanged(KEY_SHORTHAND_EXPANSION_ENABLED)
        }

    var candidateSuggestionsEnabled: Boolean
        get() = storage.getBoolean(KEY_CANDIDATE_SUGGESTIONS_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_CANDIDATE_SUGGESTIONS_ENABLED, value)
            notifyChanged(KEY_CANDIDATE_SUGGESTIONS_ENABLED)
        }

    var doubleSpacePeriodEnabled: Boolean
        get() = storage.getBoolean(KEY_DOUBLE_SPACE_PERIOD_ENABLED, false)
        set(value) {
            storage.putBoolean(KEY_DOUBLE_SPACE_PERIOD_ENABLED, value)
            notifyChanged(KEY_DOUBLE_SPACE_PERIOD_ENABLED)
        }

    var autoWordingEnabled: Boolean
        get() = storage.getBoolean(KEY_AUTO_WORDING_ENABLED, true)
        set(value) {
            storage.putBoolean(KEY_AUTO_WORDING_ENABLED, value)
            if (value) {
                if (!grammarRepairEnabled && !shorthandExpansionEnabled && !candidateSuggestionsEnabled) {
                    grammarRepairEnabled = true
                    shorthandExpansionEnabled = true
                    candidateSuggestionsEnabled = true
                }
            }
            notifyChanged(KEY_AUTO_WORDING_ENABLED)
        }

    var debugModeEnabled: Boolean
        get() = storage.getBoolean(Keys.KEY_DEBUG_MODE_ENABLED, false)
        set(value) {
            storage.putBoolean(Keys.KEY_DEBUG_MODE_ENABLED, value)
            OpenKeyLogger.shared.isDebugEnabled = value
            OpenKeyLogger.shared.log(LogSubsystem.SETTINGS) { "debugModeEnabled = $value" }
            notifyChanged(Keys.KEY_DEBUG_MODE_ENABLED)
        }

    fun snapshot(): OpenKeySettingsSnapshot = OpenKeySettingsSnapshot(
        language = language,
        inputType = inputType,
        useModernOrthography = useModernOrthography,
        checkSpelling = checkSpelling,
        restoreIfWrongSpelling = restoreIfWrongSpelling,
        bilingualAutoRestore = bilingualAutoRestore,
        useSmartSwitchKey = useSmartSwitchKey,
        showNumberRow = showNumberRow,
        heightScalePercent = heightScalePercent,
        bottomChinPaddingDp = bottomChinPaddingDp,
        oneHandedMode = oneHandedMode,
        hapticFeedbackEnabled = hapticFeedbackEnabled,
        keyPopupPreviewEnabled = keyPopupPreviewEnabled,
        grammarRepairEnabled = grammarRepairEnabled,
        shorthandExpansionEnabled = shorthandExpansionEnabled,
        candidateSuggestionsEnabled = candidateSuggestionsEnabled,
        doubleSpacePeriodEnabled = doubleSpacePeriodEnabled,
        autoWordingEnabled = autoWordingEnabled,
        debugModeEnabled = debugModeEnabled
    )

    // Custom Shorthands
    fun getCustomShorthands(): Map<String, String> {
        val raw = storage.getString(KEY_CUSTOM_SHORTHANDS, "")
        if (raw.isBlank()) return emptyMap()

        val map = mutableMapOf<String, String>()
        val lines = raw.split("\n")
        for (line in lines) {
            val parts = line.split("\t", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                if (key.isNotEmpty()) {
                    map[key] = parts[1]
                }
            }
        }
        return map
    }

    fun setCustomShorthand(shortcut: String, expansion: String) {
        val key = shortcut.trim().lowercase()
        if (key.isEmpty()) return
        val current = getCustomShorthands().toMutableMap()
        current[key] = expansion
        persistShorthands(current)
    }

    fun removeCustomShorthand(shortcut: String) {
        val key = shortcut.trim().lowercase()
        val current = getCustomShorthands().toMutableMap()
        current.remove(key)
        persistShorthands(current)
    }

    fun clearCustomShorthands() {
        storage.remove(KEY_CUSTOM_SHORTHANDS)
        notifyChanged(KEY_CUSTOM_SHORTHANDS)
    }

    private fun persistShorthands(map: Map<String, String>) {
        val serialized = map.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        storage.putString(KEY_CUSTOM_SHORTHANDS, serialized)
        notifyChanged(KEY_CUSTOM_SHORTHANDS)
    }
}
