package com.tuyenmai.openkey.core

import kotlin.math.abs
import kotlin.math.roundToInt

data class ShiftTapResult(
    val isShifted: Boolean,
    val isCapsLocked: Boolean,
    val lastShiftTapMs: Long
)

sealed class GestureAction {
    data class CursorMove(val steps: Int) : GestureAction()
    object FinishTrackpad : GestureAction()
    object CommitSpace : GestureAction()
    object DeletePreviousWord : GestureAction()
    object DeleteSingleChar : GestureAction()
    object FinishSwipeDelete : GestureAction()
    data class LongPressPopup(val variants: List<String>) : GestureAction()
}

class KeyboardGestureController {

    companion object {
        const val LONG_PRESS_TIMEOUT_MS = 300L
        const val DOUBLE_TAP_CAPS_LOCK_WINDOW_MS = 350L
        const val SPACEBAR_TRACKPAD_ACTIVATION_DP = 10f
        const val SPACEBAR_STEP_DP = 12f
        const val BACKSPACE_WORD_DELETE_STEP_DP = 24f
        const val BACKSPACE_WORD_REPEAT_START_TICK = 16
        const val QUICK_ACTION_SETTINGS = "⚙"
        const val QUICK_ACTION_ONE_HANDED = "◧"
        val DOT_PUNCTUATION_VARIANTS = listOf("?", "!", ",", "@", "/", ":")
        val COMMA_QUICK_ACTION_VARIANTS = listOf(QUICK_ACTION_SETTINGS, QUICK_ACTION_ONE_HANDED)
        val CURRENCY_VARIANTS = listOf("₫", "€", "£", "¥", "¢")
    }

    private class HeldKeyEntry(
        val token: Long,
        val flushCallback: () -> Unit,
        var flushedOrConsumed: Boolean = false
    )

    private var nextRolloverToken: Long = 1L
    private var activeHeldKey: HeldKeyEntry? = null

    var isTrackpadActive: Boolean = false
        private set
    private var spacebarAccumulatorX: Float = 0f
    private var emittedSteps: Int = 0

    var isSwipeDeleteActive: Boolean = false
        private set
    private var backspaceAccumulatorX: Float = 0f
    private var emittedWordDeletes: Int = 0

    fun flushPendingCharacterKey() {
        val prev = activeHeldKey
        if (prev != null && !prev.flushedOrConsumed) {
            prev.flushedOrConsumed = true
            activeHeldKey = null
            prev.flushCallback()
        }
    }

    /**
     * Registers a character key DOWN event. If a previous character key is still held
     * and has not been consumed by long-press or cancelled, flushes it immediately so
     * fast two-thumb overlapping chords commit in exact DOWN order (120Hz rollover).
     */
    fun onCharacterKeyDown(label: String, onPreemptFlush: () -> Unit): Long {
        flushPendingCharacterKey()
        val token = nextRolloverToken++
        activeHeldKey = HeldKeyEntry(token = token, flushCallback = onPreemptFlush)
        return token
    }

    fun markCharacterLongPressConsumed(token: Long) {
        val held = activeHeldKey
        if (held != null && held.token == token) {
            held.flushedOrConsumed = true
        }
    }

    fun cancelCharacterKey(token: Long) {
        val held = activeHeldKey
        if (held != null && held.token == token) {
            held.flushedOrConsumed = true
            activeHeldKey = null
        }
    }

    /**
     * Returns true if the caller should commit the key on UP, or false if the key was
     * already committed via rollover preemption, consumed by long-press, or cancelled.
     */
    fun onCharacterKeyUp(token: Long): Boolean {
        val held = activeHeldKey
        if (held != null && held.token == token) {
            activeHeldKey = null
            if (!held.flushedOrConsumed) {
                held.flushedOrConsumed = true
                return true
            }
            return false
        }
        return false
    }

    fun shouldCancelTapOnDragOut(
        offsetX: Float,
        offsetY: Float,
        keyWidthPx: Float,
        keyHeightPx: Float
    ): Boolean {
        if (keyWidthPx <= 0f || keyHeightPx <= 0f) return false
        val minX = -0.75f * keyWidthPx
        val maxX = 1.75f * keyWidthPx
        val minY = -1.5f * keyHeightPx
        val maxY = 2.5f * keyHeightPx
        return offsetX < minX || offsetX > maxX || offsetY < minY || offsetY > maxY
    }

    fun resolveBackspaceRepeatAction(repeatTick: Int): GestureAction {
        return if (repeatTick >= BACKSPACE_WORD_REPEAT_START_TICK) {
            GestureAction.DeletePreviousWord
        } else {
            GestureAction.DeleteSingleChar
        }
    }

    fun onSpacebarDown() {
        flushPendingCharacterKey()
        isTrackpadActive = false
        spacebarAccumulatorX = 0f
        emittedSteps = 0
    }

    fun onSpacebarDrag(deltaDp: Float): List<GestureAction> {
        spacebarAccumulatorX += deltaDp
        val actions = mutableListOf<GestureAction>()

        if (!isTrackpadActive && abs(spacebarAccumulatorX) >= SPACEBAR_TRACKPAD_ACTIVATION_DP) {
            isTrackpadActive = true
        }

        if (isTrackpadActive) {
            val currentStep = (spacebarAccumulatorX / SPACEBAR_STEP_DP).toInt()
            val deltaSteps = currentStep - emittedSteps
            if (deltaSteps > 0) {
                for (i in 0 until deltaSteps) {
                    actions.add(GestureAction.CursorMove(1))
                }
                emittedSteps = currentStep
            } else if (deltaSteps < 0) {
                for (i in 0 until -deltaSteps) {
                    actions.add(GestureAction.CursorMove(-1))
                }
                emittedSteps = currentStep
            }
        }

        return actions
    }

    fun onSpacebarUp(): GestureAction {
        return if (isTrackpadActive) {
            isTrackpadActive = false
            GestureAction.FinishTrackpad
        } else {
            GestureAction.CommitSpace
        }
    }

    fun onBackspaceDown() {
        flushPendingCharacterKey()
        isSwipeDeleteActive = false
        backspaceAccumulatorX = 0f
        emittedWordDeletes = 0
    }

    fun onBackspaceDrag(deltaDp: Float): List<GestureAction> {
        backspaceAccumulatorX += deltaDp
        val actions = mutableListOf<GestureAction>()

        if (backspaceAccumulatorX <= -BACKSPACE_WORD_DELETE_STEP_DP) {
            isSwipeDeleteActive = true
            val words = (abs(backspaceAccumulatorX) / BACKSPACE_WORD_DELETE_STEP_DP).toInt()
            val deltaWords = words - emittedWordDeletes
            for (i in 0 until deltaWords) {
                actions.add(GestureAction.DeletePreviousWord)
            }
            emittedWordDeletes = words
        }

        return actions
    }

    fun onBackspaceUp(): GestureAction {
        return if (isSwipeDeleteActive) {
            isSwipeDeleteActive = false
            GestureAction.FinishSwipeDelete
        } else {
            GestureAction.DeleteSingleChar
        }
    }

    fun resolveLongPressVariants(key: Char): List<String> {
        val isUpper = key.isUpperCase()
        val lowerKey = key.lowercaseChar()

        val baseVariants = when (lowerKey) {
            'a' -> listOf("ă", "â", "á", "à", "ả", "ã", "ạ")
            'd' -> listOf("đ")
            'e' -> listOf("ê", "é", "è", "ẻ", "ẽ", "ẹ")
            'o' -> listOf("ô", "ơ", "ó", "ò", "ỏ", "õ", "ọ")
            'u' -> listOf("ư", "ú", "ù", "ủ", "ũ", "ụ")
            'i' -> listOf("í", "ì", "ỉ", "ĩ", "ị")
            'y' -> listOf("ý", "ỳ", "ỷ", "ỹ", "ỵ")
            '$' -> CURRENCY_VARIANTS
            '.' -> DOT_PUNCTUATION_VARIANTS
            ',' -> COMMA_QUICK_ACTION_VARIANTS
            else -> emptyList()
        }

        return if (isUpper) {
            baseVariants.map { it.uppercase() }
        } else {
            baseVariants
        }
    }

    fun resolveLongPressVariants(key: String): List<String> {
        if (key.length == 1) {
            return resolveLongPressVariants(key[0])
        }
        return emptyList()
    }

    fun resolveLongPressPopupItems(key: KeyModel): List<String> {
        if (key.primaryLabel == ".") return DOT_PUNCTUATION_VARIANTS
        if (key.primaryLabel == ",") return COMMA_QUICK_ACTION_VARIANTS
        if (key.primaryLabel.length == 1) {
            val variants = resolveLongPressVariants(key.primaryLabel[0])
            val secondary = key.secondaryLabel
            if (variants.isNotEmpty()) {
                return if (!secondary.isNullOrEmpty()) listOf(secondary) + variants else variants
            }
            if (!key.isFunctional && !secondary.isNullOrEmpty()) {
                return when (secondary) {
                    "$" -> listOf("$", "₫")
                    "(" -> listOf("(", ")")
                    else -> listOf(secondary)
                }
            }
        }
        return emptyList()
    }

    fun resolvePopupSlideIndex(
        dragOffsetX: Float,
        itemWidthPx: Float,
        itemCount: Int,
        initialIndex: Int = 0
    ): Int {
        if (itemCount <= 0) return 0
        if (itemWidthPx <= 0f) return initialIndex.coerceIn(0, itemCount - 1)
        val delta = (dragOffsetX / itemWidthPx).roundToInt()
        return (initialIndex + delta).coerceIn(0, itemCount - 1)
    }

    fun onShiftTap(
        isShifted: Boolean,
        isCapsLocked: Boolean,
        nowMs: Long,
        lastShiftTapMs: Long
    ): ShiftTapResult {
        if (isCapsLocked) {
            return ShiftTapResult(isShifted = false, isCapsLocked = false, lastShiftTapMs = 0L)
        }
        if (isShifted) {
            val isDoubleTap = lastShiftTapMs > 0L && (nowMs - lastShiftTapMs) in 0L..DOUBLE_TAP_CAPS_LOCK_WINDOW_MS
            return if (isDoubleTap) {
                ShiftTapResult(isShifted = true, isCapsLocked = true, lastShiftTapMs = 0L)
            } else {
                ShiftTapResult(isShifted = false, isCapsLocked = false, lastShiftTapMs = 0L)
            }
        }
        return ShiftTapResult(isShifted = true, isCapsLocked = false, lastShiftTapMs = nowMs)
    }
}
