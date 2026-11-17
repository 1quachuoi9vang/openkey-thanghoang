package com.tuyenmai.openkey.android

import android.content.Context
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import com.tuyenmai.openkey.core.AutoWordingEngine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Android System TextServicesManager / SpellCheckerSession bridge (REQ-024, ADR-025).
 * Caches local system dictionary suggestions for English words with zero network permissions.
 */
class SystemSpellCheckerBridge(
    private val context: Context,
    private val onSuggestionsAvailable: ((String, List<String>) -> Unit)? = null
) : AutoWordingEngine.SpellCheckerBridge, SpellCheckerSession.SpellCheckerSessionListener {

    private data class InFlightRequest(val generation: Long, val word: String)

    private val cache = ConcurrentHashMap<String, List<String>>()
    private var session: SpellCheckerSession? = null
    private val requestCounter = AtomicLong(0L)
    private val inFlightRequest = AtomicReference<InFlightRequest?>(null)

    init {
        runCatching {
            val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            session = tsm?.newSpellCheckerSession(null, Locale.US, this, false)
        }
    }

    override fun suggest(word: String, maxCount: Int): List<String> {
        val lower = word.lowercase()
        val cached = cache[lower]
        if (cached != null) {
            return cached.take(maxCount)
        }

        // Request local system spellchecker suggestions
        runCatching {
            val gen = requestCounter.incrementAndGet()
            inFlightRequest.set(InFlightRequest(gen, lower))
            session?.getSuggestions(TextInfo(word), maxCount)
        }

        return emptyList()
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
        if (results == null) return
        val req = inFlightRequest.getAndSet(null) ?: return
        if (req.generation != requestCounter.get()) return
        val list = mutableListOf<String>()
        for (info in results) {
            val count = info.suggestionsCount
            for (i in 0 until count) {
                val s = info.getSuggestionAt(i)
                if (!s.isNullOrBlank() && !list.contains(s)) {
                    list.add(s)
                }
            }
        }
        if (list.isNotEmpty()) {
            cache[req.word] = list
            onSuggestionsAvailable?.invoke(req.word, list)
        }
    }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        if (results == null) return
        val req = inFlightRequest.getAndSet(null) ?: return
        if (req.generation != requestCounter.get()) return
        val list = mutableListOf<String>()
        for (sentenceInfo in results) {
            val length = sentenceInfo.suggestionsCount
            for (i in 0 until length) {
                val info = sentenceInfo.getSuggestionsInfoAt(i)
                val count = info.suggestionsCount
                for (j in 0 until count) {
                    val s = info.getSuggestionAt(j)
                    if (!s.isNullOrBlank() && !list.contains(s)) {
                        list.add(s)
                    }
                }
            }
        }
        if (list.isNotEmpty()) {
            cache[req.word] = list
            onSuggestionsAvailable?.invoke(req.word, list)
        }
    }

    fun cacheSuggestions(word: String, suggestions: List<String>) {
        cache[word.lowercase()] = suggestions
    }

    fun close() {
        runCatching {
            session?.close()
            session = null
            cache.clear()
        }
    }
}
