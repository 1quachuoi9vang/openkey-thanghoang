package com.tuyenmai.openkey.core

data class EditorContext(
    val inputType: Int = InputContextEvaluator.TYPE_CLASS_TEXT,
    val imeOptions: Int = InputContextEvaluator.IME_ACTION_DONE,
    val packageName: String
)

data class InputEvaluation(
    val bypassVietnamese: Boolean,
    val isSecureField: Boolean,
    val enterActionLabel: String,
    val imeActionId: Int = InputContextEvaluator.IME_ACTION_UNSPECIFIED,
    val shouldSendNewlineOnEnter: Boolean = false,
    val isNumericField: Boolean = false
)

object InputContextEvaluator {
    const val TYPE_MASK_CLASS = 0x0000000f
    const val TYPE_MASK_VARIATION = 0x00000ff0
    const val TYPE_MASK_FLAGS = 0x00fff000

    const val TYPE_CLASS_TEXT = 1
    const val TYPE_CLASS_NUMBER = 2
    const val TYPE_CLASS_PHONE = 3
    const val TYPE_CLASS_DATETIME = 4

    const val TYPE_TEXT_VARIATION_PASSWORD = 0x80
    const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x90
    const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0xE0
    const val TYPE_NUMBER_VARIATION_PASSWORD = 0x10

    const val TYPE_TEXT_VARIATION_URI = 0x10
    const val TYPE_TEXT_VARIATION_EMAIL_ADDRESS = 0x20
    const val TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS = 0xD0
    const val TYPE_TEXT_VARIATION_FILTER = 0xB0

    const val TYPE_TEXT_FLAG_NO_SUGGESTIONS = 0x80000
    const val TYPE_TEXT_FLAG_MULTI_LINE = 0x20000
    const val IME_FLAG_NO_ENTER_ACTION = 0x40000000

    const val IME_MASK_ACTION = 0x000000ff
    const val IME_ACTION_UNSPECIFIED = 0
    const val IME_ACTION_NONE = 1
    const val IME_ACTION_GO = 2
    const val IME_ACTION_SEARCH = 3
    const val IME_ACTION_SEND = 4
    const val IME_ACTION_NEXT = 5
    const val IME_ACTION_DONE = 6

    val TERMINAL_PACKAGES = setOf(
        "com.termux",
        "com.sonelli.juicessh",
        "com.server.auditor.ssh.client",
        "com.microsoft.rdc.androidx",
        "com.realvnc.viewer.android"
    )

    fun evaluate(context: EditorContext): InputEvaluation {
        val inputClass = context.inputType and TYPE_MASK_CLASS
        val variation = context.inputType and TYPE_MASK_VARIATION

        val isTextPassword = (inputClass == TYPE_CLASS_TEXT) && (
            variation == TYPE_TEXT_VARIATION_PASSWORD ||
            variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumberPassword = (inputClass == TYPE_CLASS_NUMBER) && (variation == TYPE_NUMBER_VARIATION_PASSWORD)
        val isSecure = isTextPassword || isNumberPassword

        var bypass = isSecure
        if (!bypass) {
            val isNonText = inputClass == TYPE_CLASS_NUMBER ||
                inputClass == TYPE_CLASS_PHONE ||
                inputClass == TYPE_CLASS_DATETIME
            val isUri = (inputClass == TYPE_CLASS_TEXT) && (variation == TYPE_TEXT_VARIATION_URI)
            val isEmail = (inputClass == TYPE_CLASS_TEXT) && (
                variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            )
            val isFilter = (inputClass == TYPE_CLASS_TEXT) && (variation == TYPE_TEXT_VARIATION_FILTER)
            val hasNoSuggestions = (context.inputType and TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
            val isTerminal = context.packageName.lowercase() in TERMINAL_PACKAGES

            bypass = isNonText || isUri || isEmail || isFilter || hasNoSuggestions || isTerminal
        }

        val isMultiline = (inputClass == TYPE_CLASS_TEXT) && ((context.inputType and TYPE_TEXT_FLAG_MULTI_LINE) != 0)
        val hasNoEnterAction = (context.imeOptions and IME_FLAG_NO_ENTER_ACTION) != 0
        val action = context.imeOptions and IME_MASK_ACTION

        val shouldSendNewline = isMultiline && (hasNoEnterAction || action == IME_ACTION_UNSPECIFIED || action == IME_ACTION_NONE)

        val enterActionLabel = if (shouldSendNewline) {
            "↵"
        } else {
            when (action) {
                IME_ACTION_SEARCH -> "Search"
                IME_ACTION_SEND -> "Send"
                IME_ACTION_GO -> "Go"
                IME_ACTION_DONE -> "Done"
                IME_ACTION_NEXT -> "Next"
                else -> "↵"
            }
        }

        val isNumericField = (inputClass == TYPE_CLASS_NUMBER || inputClass == TYPE_CLASS_PHONE || inputClass == TYPE_CLASS_DATETIME)

        return InputEvaluation(
            bypassVietnamese = bypass,
            isSecureField = isSecure,
            enterActionLabel = enterActionLabel,
            imeActionId = action,
            shouldSendNewlineOnEnter = shouldSendNewline,
            isNumericField = isNumericField
        )
    }
}
