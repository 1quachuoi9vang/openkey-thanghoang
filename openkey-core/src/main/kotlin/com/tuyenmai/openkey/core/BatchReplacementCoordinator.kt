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
    var isCursorAnchorKnown: Boolean = true
        private set

    private val pendingExpectedCursors = ArrayDeque<Int>(32)
    private var unanchoredPendingOps: Int = 0

    private fun enqueueExpectedCursor(pos: Int) {
        if (pendingExpectedCursors.lastOrNull() == pos) return
        if (pendingExpectedCursors.size >= 32) {
            pendingExpectedCursors.removeFirst()
        }
        pendingExpectedCursors.addLast(pos)
    }

    fun resetExpectedCursor(position: Int = 0, knownSelection: Boolean = false) {
        pendingExpectedCursors.clear()
        unanchoredPendingOps = 0
        if (position < 0) {
            isCursorAnchorKnown = false
            expectedCursorPosition = 0
            currentSelEnd = 0
            hasKnownSelection = false
        } else {
            isCursorAnchorKnown = true
            expectedCursorPosition = position
            currentSelEnd = position
            hasKnownSelection = knownSelection
        }
    }

    fun recordSelfDelete(deletedCount: Int) {
        if (deletedCount <= 0) return
        hasKnownSelection = false
        if (!isCursorAnchorKnown) {
            unanchoredPendingOps++
            return
        }
        expectedCursorPosition = (expectedCursorPosition - deletedCount).coerceAtLeast(0)
        currentSelEnd = expectedCursorPosition
        enqueueExpectedCursor(expectedCursorPosition)
    }

    fun onCursorMovedByTrackpad(deltaChars: Int): Int {
        if (!isCursorAnchorKnown) {
            unanchoredPendingOps++
            return 0
        }
        expectedCursorPosition = (expectedCursorPosition + deltaChars).coerceAtLeast(0)
        currentSelEnd = expectedCursorPosition
        enqueueExpectedCursor(expectedCursorPosition)
        return expectedCursorPosition
    }

    fun commitBatch(
        target: TextCommitterTarget,
        backspaceCount: Int,
        replacement: CharSequence
    ): Boolean {
        target.beginBatchEdit()
        var success = true
        val prevCursor = expectedCursorPosition
        if (backspaceCount > 0) {
            success = success && target.deleteSurroundingText(backspaceCount, 0)
            if (isCursorAnchorKnown) {
                expectedCursorPosition = (expectedCursorPosition - backspaceCount).coerceAtLeast(0)
            }
        }
        val postDeleteCursor = expectedCursorPosition
        if (replacement.isNotEmpty()) {
            success = success && target.commitText(replacement, 1)
            if (isCursorAnchorKnown) {
                expectedCursorPosition += replacement.length
            }
        }
        currentSelEnd = expectedCursorPosition
        hasKnownSelection = false
        if (!isCursorAnchorKnown) {
            if (backspaceCount > 0 || replacement.isNotEmpty()) {
                unanchoredPendingOps++
            }
        } else if (backspaceCount > 0 || replacement.isNotEmpty()) {
            if (backspaceCount > 0 && replacement.isNotEmpty() &&
                postDeleteCursor != prevCursor && postDeleteCursor != expectedCursorPosition
            ) {
                enqueueExpectedCursor(postDeleteCursor)
            }
            enqueueExpectedCursor(expectedCursorPosition)
        }
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
        if (newSelStart != newSelEnd) {
            hasKnownSelection = true
            isCursorAnchorKnown = true
            pendingExpectedCursors.clear()
            unanchoredPendingOps = 0
            expectedCursorPosition = newSelStart
            currentSelEnd = newSelEnd
            VietnameseEngine.resetSession()
            return true
        }

        hasKnownSelection = false
        currentSelEnd = newSelEnd

        if (!isCursorAnchorKnown) {
            if (newSelStart < 0) return false
            isCursorAnchorKnown = true
            expectedCursorPosition = newSelStart
            if (unanchoredPendingOps > 0) {
                unanchoredPendingOps--
            }
            return false
        }

        val matchIdx = pendingExpectedCursors.indexOf(newSelStart)
        if (matchIdx >= 0) {
            repeat(matchIdx + 1) {
                pendingExpectedCursors.removeFirst()
            }
            return false
        }

        if (unanchoredPendingOps > 0 &&
            newSelStart >= expectedCursorPosition &&
            (newSelStart - expectedCursorPosition) <= unanchoredPendingOps
        ) {
            val consumed = (newSelStart - expectedCursorPosition).coerceAtLeast(1)
            unanchoredPendingOps = (unanchoredPendingOps - consumed).coerceAtLeast(0)
            expectedCursorPosition = newSelStart
            return false
        }

        if (pendingExpectedCursors.isEmpty() && newSelStart == expectedCursorPosition) {
            return false
        }

        // External cursor jump
        pendingExpectedCursors.clear()
        unanchoredPendingOps = 0
        expectedCursorPosition = newSelStart
        VietnameseEngine.resetSession()
        return true
    }
}

