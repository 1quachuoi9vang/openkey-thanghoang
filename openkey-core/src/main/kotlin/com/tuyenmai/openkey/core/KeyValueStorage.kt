package com.tuyenmai.openkey.core

interface KeyValueStorage {
    fun getString(key: String, defaultValue: String = ""): String
    fun putString(key: String, value: String)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
    fun clear()
}

class InMemoryKeyValueStorage : KeyValueStorage {
    private val data = mutableMapOf<String, Any>()

    @Synchronized
    override fun getString(key: String, defaultValue: String): String {
        return (data[key] as? String) ?: defaultValue
    }

    @Synchronized
    override fun putString(key: String, value: String) {
        data[key] = value
    }

    @Synchronized
    override fun getInt(key: String, defaultValue: Int): Int {
        return (data[key] as? Int) ?: defaultValue
    }

    @Synchronized
    override fun putInt(key: String, value: Int) {
        data[key] = value
    }

    @Synchronized
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return (data[key] as? Boolean) ?: defaultValue
    }

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        data[key] = value
    }

    @Synchronized
    override fun remove(key: String) {
        data.remove(key)
    }

    @Synchronized
    override fun clear() {
        data.clear()
    }
}
