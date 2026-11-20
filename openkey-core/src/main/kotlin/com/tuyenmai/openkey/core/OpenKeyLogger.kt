package com.tuyenmai.openkey.core

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

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
 * Direct-Boot IOException resilience with in-memory fallback ring buffering,
 * off-UI-thread async disk persistence, version-scoped retention, and UTF-8-safe tail reads.
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

    @Volatile
    var lastIoError: String? = null
        private set

    private val ringBuffer = ArrayDeque<String>(MAX_RING_ENTRIES)

    private val logExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "openkey-logger-io").apply { isDaemon = true }
    }

    private fun appendRingEntry(entry: String) {
        if (ringBuffer.size >= MAX_RING_ENTRIES) {
            ringBuffer.removeFirst()
        }
        ringBuffer.addLast(entry)
    }

    fun configureLogFile(file: File) {
        logExecutor.submit(Callable {
            this.logFile = file
            lastIoError = null
        }).get()
    }

    /**
     * Fast-path logger. Evaluates [message] only if [isDebugEnabled] is true,
     * and dispatches file I/O off the caller thread.
     */
    fun log(subsystem: LogSubsystem, message: () -> String) {
        if (!isDebugEnabled) return
        val evaluated = message()
        val entry = "[${Instant.now()}] [${subsystem.tag}] $evaluated\n"
        logExecutor.execute {
            appendRingEntry(entry)
            try {
                logFile.parentFile?.mkdirs()
                if (!logFile.exists() || logFile.length() == 0L) {
                    logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n")
                }
                if (logFile.length() >= maxFileSizeBytes) {
                    logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n[LOG_ROTATED_SIZE_CAP]\n")
                }
                logFile.appendText(entry)
                lastIoError = null
            } catch (ioe: IOException) {
                lastIoError = ioe.message ?: ioe.javaClass.simpleName
            }
        }
    }

    /**
     * Prunes and recreates log file when version stamp does not match [version].
     */
    fun pruneOutdatedLogsIfNeeded(version: String = currentVersion) {
        logExecutor.submit(Callable {
            try {
                if (logFile.exists() && logFile.isFile) {
                    val firstLine = try {
                        logFile.bufferedReader().use { it.readLine() }
                    } catch (ioe: IOException) {
                        lastIoError = ioe.message ?: ioe.javaClass.simpleName
                        null
                    }
                    if (firstLine != "# OpenKeyAndroidVersionStamp: $version") {
                        ringBuffer.clear()
                        logFile.writeText("# OpenKeyAndroidVersionStamp: $version\n")
                    }
                } else {
                    logFile.parentFile?.mkdirs()
                    logFile.writeText("# OpenKeyAndroidVersionStamp: $version\n")
                }
                lastIoError = null
            } catch (ioe: IOException) {
                lastIoError = ioe.message ?: ioe.javaClass.simpleName
            }
        }).get()
    }

    /**
     * Clears current logs and writes the clean version stamp header.
     */
    fun clearLog() {
        logExecutor.submit(Callable {
            ringBuffer.clear()
            try {
                logFile.parentFile?.mkdirs()
                logFile.writeText("# OpenKeyAndroidVersionStamp: $currentVersion\n")
                lastIoError = null
            } catch (ioe: IOException) {
                lastIoError = ioe.message ?: ioe.javaClass.simpleName
            }
        }).get()
    }

    /**
     * Reads up to the last [maxBytes] bytes as valid UTF-8 text without broken leading multi-byte sequences.
     */
    fun readRecentLogs(maxBytes: Int = 65_536): String {
        return logExecutor.submit(Callable {
            try {
                if (logFile.exists() && logFile.isFile) {
                    val len = logFile.length()
                    if (len <= 0L) return@Callable ""
                    if (len <= maxBytes) {
                        return@Callable logFile.readText(Charsets.UTF_8)
                    }
                    RandomAccessFile(logFile, "r").use { raf ->
                        raf.seek(len - maxBytes)
                        val buffer = ByteArray(maxBytes)
                        raf.readFully(buffer)
                        var startIdx = 0
                        while (startIdx < buffer.size && (buffer[startIdx].toInt() and 0xC0) == 0x80) {
                            startIdx++
                        }
                        val decoder = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.IGNORE)
                            .onUnmappableCharacter(CodingErrorAction.IGNORE)
                        val decoded = decoder.decode(ByteBuffer.wrap(buffer, startIdx, buffer.size - startIdx)).toString()
                        val firstNewline = decoded.indexOf('\n')
                        return@Callable if (firstNewline in 0 until decoded.length - 1) {
                            decoded.substring(firstNewline + 1)
                        } else {
                            decoded
                        }
                    }
                }
            } catch (ioe: IOException) {
                lastIoError = ioe.message ?: ioe.javaClass.simpleName
            }
            if (ringBuffer.isEmpty()) {
                ""
            } else {
                buildString {
                    append("# OpenKeyAndroidVersionStamp: $currentVersion\n")
                    for (line in ringBuffer) append(line)
                }
            }
        }).get()
    }

    companion object {
        private const val MAX_RING_ENTRIES = 128

        val shared: OpenKeyLogger = OpenKeyLogger()

        fun defaultLogFile(): File = File(System.getProperty("java.io.tmpdir"), "OpenKeyAndroid/openkey-debug.log")
    }
}
