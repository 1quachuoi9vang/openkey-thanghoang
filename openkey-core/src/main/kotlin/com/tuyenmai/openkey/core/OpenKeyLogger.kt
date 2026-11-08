package com.tuyenmai.openkey.core

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant

/**
 * Log subsystems for OpenKey diagnostic tracking (REQ-017, ADR-017).
 */
enum class LogSubsystem(val tag: String) {
    LIFECYCLE("LIFECYCLE"),
    SETTINGS("SETTINGS"),
    SESSION("SESSION"),
    ENGINE("ENGINE"),
    AUTO_WORDING("AUTO_WORDING"),
    SMART_SWITCH("SMART_SWITCH")
}

typealias OpenKeyLogCategory = LogSubsystem

/**
 * Diagnostic logger for OpenKey Android (REQ-017, ADR-017).
 * Provides zero-overhead lazy lambda execution when debug mode is disabled,
 * version-scoped retention, thread-safe synchronization, and size-cap rotation.
 */
class OpenKeyLogger(
    logFile: File = defaultLogFile(),
    val maxFileSizeBytes: Long = 5L * 1024L * 1024L,
    val currentVersion: String = "1.0.0 (1)"
) {
    @Volatile
    var isDebugEnabled: Boolean = false

    @Volatile
    var logFile: File = logFile

    private val lock = Any()

    fun configureLogFile(file: File) {
        synchronized(lock) {
            this.logFile = file
        }
    }

    /**
     * Fast-path logger. Guaranteed to evaluate [message] only if [isDebugEnabled] is true.
     */
    fun log(subsystem: LogSubsystem, message: () -> String) {
        if (!isDebugEnabled) return
        val evaluated = message()
        synchronized(lock) {
            logFile.parentFile?.mkdirs()
            if (!logFile.exists() || logFile.length() == 0L) {
                logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n")
            }
            if (logFile.length() >= maxFileSizeBytes) {
                logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n[LOG_ROTATED_SIZE_CAP]\n")
            }
            val entry = "[${Instant.now()}] [${subsystem.tag}] $evaluated\n"
            logFile.appendText(entry)
        }
    }

    /**
     * Prunes and recreates log file when version stamp does not match [version].
     */
    fun pruneOutdatedLogsIfNeeded(version: String = currentVersion) {
        synchronized(lock) {
            if (logFile.exists()) {
                val firstLine = try {
                    logFile.bufferedReader().use { it.readLine() }
                } catch (_: Exception) {
                    null
                }
                if (firstLine != "# OpenKeyAndroidVersionStamp: $version") {
                    logFile.writeText("# OpenKeyAndroidVersionStamp: $version\n")
                }
            } else {
                logFile.parentFile?.mkdirs()
                logFile.writeText("# OpenKeyAndroidVersionStamp: $version\n")
            }
        }
    }

    /**
     * Clears current logs and writes the clean version stamp header.
     */
    fun clearLog() {
        synchronized(lock) {
            logFile.parentFile?.mkdirs()
            logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n")
        }
    }

    /**
     * Reads up to the last [maxBytes] bytes as UTF-8 text.
     */
    fun readRecentLogs(maxBytes: Int = 65_536): String {
        synchronized(lock) {
            if (!logFile.exists()) return ""
            val len = logFile.length()
            if (len <= 0) return ""
            if (len <= maxBytes) {
                return logFile.readText(Charsets.UTF_8)
            }
            RandomAccessFile(logFile, "r").use { raf ->
                raf.seek(len - maxBytes)
                val buffer = ByteArray(maxBytes)
                raf.readFully(buffer)
                return String(buffer, Charsets.UTF_8)
            }
        }
    }

    companion object {
        val shared: OpenKeyLogger = OpenKeyLogger()

        fun defaultLogFile(): File = File(System.getProperty("java.io.tmpdir"), "OpenKeyAndroid/openkey-debug.log")
    }
}
