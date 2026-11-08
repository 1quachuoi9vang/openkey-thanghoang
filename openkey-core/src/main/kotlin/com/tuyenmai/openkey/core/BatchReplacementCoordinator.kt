package com.tuyenmai.openkey.core

interface TextCommitterTarget {
    fun beginBatchEdit(): Boolean
    fun endBatchEdit(): Boolean
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    fun commitText(text: CharSequence, newCursorPosition: Int): Boolean
    fun sendKeyChar(char: Char): Boolean
    fun performEditorAction(actionId: Int): Boolean
    fun moveCursorBy(deltaChars: Int): Boolean
    fun deletePreviousWord(): Boolean
}

class BatchReplacementCoordinator {
    var expectedCursorPosition: Int = 0
        private set
    var currentSelEnd: Int = 0
        private set
    var hasKnownSelection: Boolean = false
        private set

    fun resetExpectedCursor(position: Int = 0, knownSelection: Boolean = false) {
        expectedCursorPosition = position
        currentSelEnd = position
        hasKnownSelection = knownSelection
    }

    fun onCursorMovedByTrackpad(deltaChars: Int): Int {
        expectedCursorPosition = (expectedCursorPosition + deltaChars).coerceAtLeast(0)
        currentSelEnd = expectedCursorPosition
        return expectedCursorPosition
    }

    fun commitBatch(
        target: TextCommitterTarget,
        backspaceCount: Int,
        replacement: CharSequence
    ): Boolean {
        target.beginBatchEdit()
        var success = true
        if (backspaceCount > 0) {
            success = success && target.deleteSurroundingText(backspaceCount, 0)
            expectedCursorPosition = (expectedCursorPosition - backspaceCount).coerceAtLeast(0)
        }
        if (replacement.isNotEmpty()) {
            success = success && target.commitText(replacement, 1)
            expectedCursorPosition += replacement.length
        }
        currentSelEnd = expectedCursorPosition
        hasKnownSelection = false
        target.endBatchEdit()
        return success
    }

    fun applySnapshot(target: TextCommitterTarget, snapshot: HookSnapshot): Boolean {
        return when (snapshot.code) {
            HookCodeState.WILL_PROCESS,
            HookCodeState.RESTORE,
            HookCodeState.RESTORE_AND_START_NEW_SESSION -> {
                commitBatch(target, snapshot.backspaceCount, snapshot.decodedString)
            }
            else -> false
        }
    }

    /**
     * Called when the framework reports cursor/selection updates.
     * Returns true if an external jump occurred and the engine session was reset.
     */
    fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        hasKnownSelection = (newSelStart != newSelEnd)
        currentSelEnd = newSelEnd

        // If cursor position matches our expected self-committed position and is not a selection
        if (newSelStart == expectedCursorPosition && newSelStart == newSelEnd) {
            return false
        }

        // External cursor jump or selection range change
        expectedCursorPosition = newSelStart
        VietnameseEngine.resetSession()
        return true
    }
}
