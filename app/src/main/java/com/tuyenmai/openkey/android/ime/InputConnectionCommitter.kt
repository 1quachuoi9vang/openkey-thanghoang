package com.tuyenmai.openkey.android.ime

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.KeyValueStorage
import com.tuyenmai.openkey.core.TextCommitterTarget
import kotlin.math.abs

/**
 * Wraps Android [InputConnection] providing atomic batch operations without
 * setComposingText calls, eliminating composing underline artifacts (REQ-005, ADR-005).
 */
class InputConnectionCommitter(
    private val inputConnectionProvider: () -> InputConnection?,
    private val keyEventSender: ((KeyEvent) -> Unit)? = null,
    private val batchCoordinator: BatchReplacementCoordinator? = null
) : TextCommitterTarget {

    constructor(inputConnection: InputConnection?) : this({ inputConnection }, null, null)

    constructor(inputConnection: InputConnection?, batchCoordinator: BatchReplacementCoordinator?) : this(
        { inputConnection },
        null,
        batchCoordinator
    )

    override fun beginBatchEdit(): Boolean {
        return inputConnectionProvider()?.beginBatchEdit() ?: false
    }

    override fun endBatchEdit(): Boolean {
        return inputConnectionProvider()?.endBatchEdit() ?: false
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        return inputConnectionProvider()?.deleteSurroundingText(beforeLength, afterLength) ?: false
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        return inputConnectionProvider()?.commitText(text, newCursorPosition) ?: false
    }

    override fun sendKeyChar(char: Char): Boolean {
        val ic = inputConnectionProvider() ?: return false
        return ic.commitText(char.toString(), 1)
    }

    override fun performEditorAction(actionId: Int): Boolean {
        return inputConnectionProvider()?.performEditorAction(actionId) ?: false
    }

    override fun moveCursorBy(deltaChars: Int): Boolean {
        if (deltaChars == 0) return true
        val ic = inputConnectionProvider() ?: return false

        if (batchCoordinator != null && batchCoordinator.hasKnownSelection) {
            val target = batchCoordinator.onCursorMovedByTrackpad(deltaChars)
            return ic.setSelection(target, target)
        }

        // Fallback: send DPAD left / right events
        val dpadKey = if (deltaChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val steps = abs(deltaChars)
        var success = true
        ic.beginBatchEdit()
        for (i in 0 until steps) {
            if (keyEventSender != null) {
                keyEventSender.invoke(KeyEvent(KeyEvent.ACTION_DOWN, dpadKey))
                keyEventSender.invoke(KeyEvent(KeyEvent.ACTION_UP, dpadKey))
            } else {
                success = success && ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, dpadKey))
                success = success && ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, dpadKey))
            }
        }
        ic.endBatchEdit()
        return success
    }

    override fun deletePreviousWord(): Boolean {
        val ic = inputConnectionProvider() ?: return false
        val textBefore = ic.getTextBeforeCursor(64, 0)?.toString() ?: return false
        if (textBefore.isEmpty()) return false

        var i = textBefore.length - 1
        var trailingSpaces = 0
        while (i >= 0 && textBefore[i].isWhitespace()) {
            trailingSpaces++
            i--
        }

        var wordChars = 0
        while (i >= 0 && !textBefore[i].isWhitespace()) {
            wordChars++
            i--
        }

        val toDelete = (trailingSpaces + wordChars).coerceAtLeast(1).coerceAtMost(textBefore.length)
        beginBatchEdit()
        val success = deleteSurroundingText(toDelete, 0)
        endBatchEdit()
        return success
    }
}

/**
 * Direct Boot-compatible [KeyValueStorage] backed by Android Device Protected SharedPreferences (REQ-001, REQ-010).
 */
class SharedPreferencesKeyValueStorage(context: Context) : KeyValueStorage {

    private val prefs: SharedPreferences = run {
        val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.createDeviceProtectedStorageContext()
            } catch (e: Exception) {
                context
            }
        } else {
            context
        }
        storageContext.getSharedPreferences("openkey_android_prefs", Context.MODE_PRIVATE)
    }

    override fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
