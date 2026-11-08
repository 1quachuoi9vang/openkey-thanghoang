package com.tuyenmai.openkey.core

class SmartSwitchStore(private val storage: KeyValueStorage) {

    companion object {
        private const val PREFIX = "smart_switch_"
        private const val SELF_PACKAGE = "com.tuyenmai.openkey.android"
    }

    fun getAppLanguage(packageName: String, defaultLanguage: Int = 1): Int {
        if (packageName.isBlank() || packageName == SELF_PACKAGE) {
            return defaultLanguage
        }
        val key = "$PREFIX$packageName"
        val saved = storage.getInt(key, -1)
        return if (saved != -1) saved else defaultLanguage
    }

    fun setAppLanguage(packageName: String, language: Int) {
        if (packageName.isBlank() || packageName == SELF_PACKAGE) {
            return
        }
        val key = "$PREFIX$packageName"
        storage.putInt(key, language)
    }

    fun onAppSwitched(
        packageName: String,
        currentGlobalLanguage: Int,
        useSmartSwitchKey: Boolean
    ): Int {
        if (!useSmartSwitchKey || packageName.isBlank() || packageName == SELF_PACKAGE) {
            return currentGlobalLanguage
        }

        val key = "$PREFIX$packageName"
        val saved = storage.getInt(key, -1)
        return if (saved != -1) {
            saved
        } else {
            storage.putInt(key, currentGlobalLanguage)
            currentGlobalLanguage
        }
    }

    fun clear() {
        storage.clear()
    }
}
