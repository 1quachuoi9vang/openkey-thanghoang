package com.tuyenmai.openkey.core

import kotlin.math.abs

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
        const val LONG_PRESS_TIMEOUT_MS = 220L
        const val SPACEBAR_TRACKPAD_ACTIVATION_DP = 10f
        const val SPACEBAR_STEP_DP = 12f
        const val BACKSPACE_WORD_DELETE_STEP_DP = 24f
    }

    var isTrackpadActive: Boolean = false
        private set
    private var spacebarAccumulatorX: Float = 0f
    private var emittedSteps: Int = 0

    var isSwipeDeleteActive: Boolean = false
        private set
    private var backspaceAccumulatorX: Float = 0f
    private var emittedWordDeletes: Int = 0

    fun onSpacebarDown() {
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
}
