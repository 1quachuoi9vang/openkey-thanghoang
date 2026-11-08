package com.tuyenmai.openkey.core

enum class OneHandedMode {
    OFF,
    LEFT,
    RIGHT
}

enum class KeyboardLayer {
    QWERTY,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMPAD
}

data class PhoneLayoutConfig(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape: Boolean,
    val showNumberRow: Boolean,
    val inputType: Int, // 0 = Telex, 1 = VNI (forces number row visible)
    val heightScalePercent: Int = 100, // 85..125
    val bottomChinPaddingDp: Int = 14, // 0..48, default 14dp on Galaxy A37 One UI
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF
)

data class ComputedKeyboardMetrics(
    val candidateStripHeightDp: Float,
    val numberRowHeightDp: Float,
    val alphaRowHeightDp: Float,
    val bottomRowHeightDp: Float,
    val homeRowSideInsetFraction: Float, // 0.05f (5% left/right inset on A..L row)
    val effectiveBottomChinDp: Float,
    val totalHeightDp: Float,
    val activeGridWidthDp: Float,
    val sideGutterWidthDp: Float,
    val effectiveOneHandedMode: OneHandedMode,
    val isNumberRowVisible: Boolean,
    val keyCornerRadiusDp: Float, // 6f (Samsung One UI keycap corner radius)
    val keyHorizontalGapDp: Float,
    val keyVerticalGapDp: Float
)

object PhoneLayoutMetrics {

    const val PORTRAIT_CANDIDATE_STRIP_DP = 44f
    const val PORTRAIT_NUMBER_ROW_DP = 42f
    const val PORTRAIT_ALPHA_ROW_DP = 52f
    const val PORTRAIT_BOTTOM_ROW_DP = 50f
    const val KEY_CORNER_RADIUS_DP = 7f
    const val KEY_HORIZONTAL_GAP_DP = 4f
    const val KEY_VERTICAL_GAP_DP = 5f

    fun compute(config: PhoneLayoutConfig): ComputedKeyboardMetrics {
        val isNumberRowVisible = config.showNumberRow || config.inputType == 1

        if (config.isLandscape) {
            var candidateStripHeightDp = 36f
            var numberRowHeightDp = if (isNumberRowVisible) 32f else 0f
            var alphaRowHeightDp = 38f
            var bottomRowHeightDp = 38f
            val homeRowSideInsetFraction = 0.05f
            val effectiveBottomChinDp = 0f
            val effectiveOneHandedMode = OneHandedMode.OFF
            val activeGridWidthDp = config.screenWidthDp.toFloat()
            val sideGutterWidthDp = 0f
            val keyCornerRadiusDp = 6f
            val keyHorizontalGapDp = 4f
            val keyVerticalGapDp = 4f

            var totalHeightDp = candidateStripHeightDp + numberRowHeightDp + (3f * alphaRowHeightDp) + bottomRowHeightDp + effectiveBottomChinDp
            val maxLandscapeHeight = config.screenHeightDp * 0.42f
            if (totalHeightDp > maxLandscapeHeight) {
                val landscapeScale = (maxLandscapeHeight / totalHeightDp).coerceAtMost(1.0f)
                candidateStripHeightDp *= landscapeScale
                numberRowHeightDp *= landscapeScale
                alphaRowHeightDp *= landscapeScale
                bottomRowHeightDp *= landscapeScale
                totalHeightDp = candidateStripHeightDp + numberRowHeightDp + (3f * alphaRowHeightDp) + bottomRowHeightDp + effectiveBottomChinDp
            }

            return ComputedKeyboardMetrics(
                candidateStripHeightDp = candidateStripHeightDp,
                numberRowHeightDp = numberRowHeightDp,
                alphaRowHeightDp = alphaRowHeightDp,
                bottomRowHeightDp = bottomRowHeightDp,
                homeRowSideInsetFraction = homeRowSideInsetFraction,
                effectiveBottomChinDp = effectiveBottomChinDp,
                totalHeightDp = totalHeightDp,
                activeGridWidthDp = activeGridWidthDp,
                sideGutterWidthDp = sideGutterWidthDp,
                effectiveOneHandedMode = effectiveOneHandedMode,
                isNumberRowVisible = isNumberRowVisible,
                keyCornerRadiusDp = keyCornerRadiusDp,
                keyHorizontalGapDp = keyHorizontalGapDp,
                keyVerticalGapDp = keyVerticalGapDp
            )
        } else {
            val scale = config.heightScalePercent.coerceIn(85, 125) / 100f
            val candidateStripHeightDp = PORTRAIT_CANDIDATE_STRIP_DP * scale
            val numberRowHeightDp = if (isNumberRowVisible) PORTRAIT_NUMBER_ROW_DP * scale else 0f
            val alphaRowHeightDp = PORTRAIT_ALPHA_ROW_DP * scale
            val bottomRowHeightDp = PORTRAIT_BOTTOM_ROW_DP * scale
            val homeRowSideInsetFraction = 0.05f
            val effectiveBottomChinDp = config.bottomChinPaddingDp.coerceIn(0, 48).toFloat()
            val effectiveOneHandedMode = if (config.screenWidthDp >= 360) config.oneHandedMode else OneHandedMode.OFF
            val activeGridWidthDp = if (effectiveOneHandedMode == OneHandedMode.OFF) {
                config.screenWidthDp.toFloat()
            } else {
                config.screenWidthDp * 0.82f
            }
            val sideGutterWidthDp = config.screenWidthDp - activeGridWidthDp
            val keyCornerRadiusDp = KEY_CORNER_RADIUS_DP
            val keyHorizontalGapDp = KEY_HORIZONTAL_GAP_DP
            val keyVerticalGapDp = KEY_VERTICAL_GAP_DP

            val totalHeightDp = candidateStripHeightDp + numberRowHeightDp + (3f * alphaRowHeightDp) + bottomRowHeightDp + effectiveBottomChinDp

            return ComputedKeyboardMetrics(
                candidateStripHeightDp = candidateStripHeightDp,
                numberRowHeightDp = numberRowHeightDp,
                alphaRowHeightDp = alphaRowHeightDp,
                bottomRowHeightDp = bottomRowHeightDp,
                homeRowSideInsetFraction = homeRowSideInsetFraction,
                effectiveBottomChinDp = effectiveBottomChinDp,
                totalHeightDp = totalHeightDp,
                activeGridWidthDp = activeGridWidthDp,
                sideGutterWidthDp = sideGutterWidthDp,
                effectiveOneHandedMode = effectiveOneHandedMode,
                isNumberRowVisible = isNumberRowVisible,
                keyCornerRadiusDp = keyCornerRadiusDp,
                keyHorizontalGapDp = keyHorizontalGapDp,
                keyVerticalGapDp = keyVerticalGapDp
            )
        }
    }
}
