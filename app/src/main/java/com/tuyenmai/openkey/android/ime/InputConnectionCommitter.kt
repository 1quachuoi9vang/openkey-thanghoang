package com.tuyenmai.openkey.android.ime

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.tuyenmai.openkey.core.BatchReplacementCoordinator
import com.tuyenmai.openkey.core.KeyValueStorage
import com.tuyenmai.openkey.core.OpenKeySettingsStore
import com.tuyenmai.openkey.core.TextCommitterTarget
import kotlin.math.abs

/**
 * Wraps Android [InputConnection] providing atomic batch operations without
 * setComposingText calls, eliminating composing underline artifacts (REQ-005, ADR-005).
 */
class InputConnectionCommitter(
    private val inputConnectionProvider: () -> InputConnection?,
    private val keyEventSender: ((KeyEvent) -> Unit)? = null,
    private val batchCoordinator: BatchReplacementCoordinator? = null,
    private val keyCodeSender: ((Int) -> Unit)? = null
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
        val ic = inputConnectionProvider() ?: return false
        if (beforeLength >= 5 && afterLength == 0) {
            val actualBefore = ic.getTextBeforeCursor(beforeLength, 0)
            if (!actualBefore.isNullOrEmpty() && actualBefore.length < beforeLength) {
                return ic.deleteSurroundingText(actualBefore.length, 0)
            }
        }
        return ic.deleteSurroundingText(beforeLength, afterLength)
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

        if (batchCoordinator != null && (batchCoordinator.hasKnownSelection || batchCoordinator.isCursorAnchorKnown)) {
            val target = batchCoordinator.onCursorMovedByTrackpad(deltaChars)
            return ic.setSelection(target, target)
        }

        // Fallback: send DPAD left / right events
        batchCoordinator?.onCursorMovedByTrackpad(deltaChars)
        val dpadKey = if (deltaChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val steps = abs(deltaChars)
        var success = true
        ic.beginBatchEdit()
        for (i in 0 until steps) {
            success = success && dispatchHardwareKeyCode(dpadKey, ic)
        }
        ic.endBatchEdit()
        return success
    }

    private fun dispatchHardwareKeyCode(keyCode: Int, ic: InputConnection): Boolean {
        if (keyCodeSender != null) {
            keyCodeSender.invoke(keyCode)
            return true
        }
        if (keyEventSender != null) {
            keyEventSender.invoke(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            keyEventSender.invoke(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            return true
        }
        val downSent = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        val upSent = ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return downSent && upSent
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
        val success = try {
            deleteSurroundingText(toDelete, 0)
        } finally {
            endBatchEdit()
        }
        if (success) {
            batchCoordinator?.recordSelfDelete(toDelete)
        }
        return success
    }

    override fun deleteSelectionIfAny(): Boolean {
        val ic = inputConnectionProvider() ?: return false
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) return false
        ic.beginBatchEdit()
        val committed = try {
            ic.commitText("", 1)
        } finally {
            ic.endBatchEdit()
        }
        if (committed) {
            batchCoordinator?.onSelectionDeleted()
        }
        return committed
    }

    override fun deleteSingleBackward(): Int {
        val ic = inputConnectionProvider() ?: return 0
        val before = ic.getTextBeforeCursor(2, 0)
        val deleteCount = if (
            before != null &&
            before.length >= 2 &&
            Character.isSurrogatePair(before[before.length - 2], before[before.length - 1])
        ) {
            2
        } else {
            1
        }
        return if (ic.deleteSurroundingText(deleteCount, 0)) deleteCount else 0
    }

    override fun sendEnterKey(): Boolean {
        val ic = inputConnectionProvider() ?: return false
        return if (dispatchHardwareKeyCode(KeyEvent.KEYCODE_ENTER, ic)) {
            true
        } else {
            ic.commitText("\n", 1)
        }
    }
}

/**
 * Direct Boot-compatible [KeyValueStorage] backed by Android Device Protected SharedPreferences (REQ-001, REQ-010).
 */
class SharedPreferencesKeyValueStorage(context: Context) : KeyValueStorage {

    companion object {
        @Volatile
        private var sharedStoreInstance: OpenKeySettingsStore? = null
        private var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

        @Synchronized
        fun sharedSettingsStore(context: Context): OpenKeySettingsStore {
            val existing = sharedStoreInstance
            if (existing != null) return existing
            val storage = SharedPreferencesKeyValueStorage(context.applicationContext ?: context)
            val store = OpenKeySettingsStore(storage)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null) {
                    store.notifyChanged(key)
                }
            }
            prefChangeListener = listener
            storage.prefs.registerOnSharedPreferenceChangeListener(listener)
            sharedStoreInstance = store
            return store
        }
    }

    internal val prefs: SharedPreferences = run {
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
