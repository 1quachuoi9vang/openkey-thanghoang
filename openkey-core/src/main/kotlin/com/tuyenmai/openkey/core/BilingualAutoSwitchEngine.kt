package com.tuyenmai.openkey.core

data class PreviousWordEntry(
    var rawString: String,
    var composedString: String,
    var rawKeys: List<UInt>,
    var composedWord: List<UInt>,
    var wasRestoredToEnglish: Boolean,
    var trailingSpaces: Int = 1
)

data class AutoRestoreUndoSnapshot(
    var originalComposedString: String,
    var originalTypingWord: List<UInt>,
    var originalIndex: Int,
    var originalKeyStates: List<UInt>,
    var originalStateIndex: Int,
    var replacedLengthOnScreen: Int,
    var rawLowerWord: String
)

object BilingualAutoSwitchEngine {

    fun stringToTargetKeys(str: String): List<UInt> {
        val keys = mutableListOf<UInt>()
        for (ch in str) {
            val mapping = VietnameseData.charToKeyCode(ch)
            val keyCode = mapping.first
            val caps = mapping.second
            if (keyCode != KEY_EMPTY.toInt()) {
                keys.add(keyCode.toUInt() or (if (caps != 0) CAPS_MASK else 0u))
            } else {
                keys.add(ch.code.toUInt() or PURE_CHARACTER_MASK)
            }
        }
        return keys
    }

    private fun keysToString(keys: List<UInt>): String {
        val sb = StringBuilder()
        for (k in keys) {
            val c = VietnameseData.keyCodeToCharacter(k)
            if (c != 0.toUShort()) sb.append(c.toInt().toChar())
        }
        return sb.toString()
    }

    private fun applyCasing(word: String, isCap: Boolean): String =
        if (isCap) word.replaceFirstChar { it.uppercase() } else word

    fun effectiveLanguagePriority(state: VietnameseEngineState): LanguagePriority {
        val last = state.previousWords.lastOrNull()
        if (last != null) {
            return if (last.wasRestoredToEnglish) LanguagePriority.ENGLISH_FIRST else LanguagePriority.VIETNAMESE_FIRST
        }
        if (state.consecutiveEnglishWordCount > 0) return LanguagePriority.ENGLISH_FIRST
        return if (state.vLanguage == 1) LanguagePriority.VIETNAMESE_FIRST else LanguagePriority.ENGLISH_FIRST
    }

    fun evaluateWordBreakRestore(
        state: VietnameseEngineState,
        breakKeyCode: UShort,
        handleCode: Int
    ): Boolean {
        if (state.index <= 0 || state.stateIndex <= 0) return false
        val dict = VietnameseDictionary.shared

        val fullKeys = state.rawKeyStates.slice(0 until state.rawStateIndex)
        val fullRawStr = keysToString(fullKeys)
        val fullRawLower = fullRawStr.lowercase()

        val stdRawKeys = state.keyStates.slice(0 until state.stateIndex)
        val stdRawLower = keysToString(stdRawKeys).lowercase()

        var hasAnyDiacritic = false
        var capitalCount = 0
        val compChars = StringBuilder()
        for (i in 0 until state.index) {
            if (state.typingWord[i].hasAnyDiacritic) hasAnyDiacritic = true
            if (state.typingWord[i].isCaps) capitalCount++
            val code = state.GET(state.typingWord[i])
            val valChar: UShort = if ((code and CHAR_CODE_MASK) != 0u || (code and PURE_CHARACTER_MASK) != 0u) {
                (code and 0xFFFFu).toUShort()
            } else {
                VietnameseData.keyCodeToCharacter(code)
            }
            if (valChar != 0.toUShort()) compChars.append(valChar.toInt().toChar())
        }
        val composed = compChars.toString()
        val composedLower = composed.lowercase()

        val vowelKeySet = setOf(KEY_A, KEY_E, KEY_I, KEY_O, KEY_U, KEY_Y)
        val hasStrokeD = (0 until state.index).any { state.CHR(it) == KEY_D && state.typingWord[it].hasCircumflexOrStroke }
        val hasNoVowel = (0 until state.index).none { vowelKeySet.contains(state.CHR(it)) }
        if (hasStrokeD && hasNoVowel && capitalCount >= 2) {
            state.consecutiveEnglishWordCount = 0
            state.previousWords.clear()
            return false
        }

        state.undoneRawWord?.let { locked ->
            if (locked == fullRawLower || locked == stdRawLower) {
                if (breakKeyCode == KEY_SPACE) state.undoneRawWord = null
                return false
            }
            state.undoneRawWord = null
        }

        val singleToneLower = if (fullRawLower.isNotEmpty()) fullRawLower.dropLast(1) else ""
        val isTrailingDoubledToneEscape = checkTrailingDoubledToneEscape(state, fullKeys, fullRawLower, singleToneLower, dict)
        if (isTrailingDoubledToneEscape && composedLower == singleToneLower) {
            if (breakKeyCode == KEY_SPACE) recordTrailingEscape(state, composed, fullKeys)
            return false
        }

        if (tryCollapseDuplicateEnglishWord(state, breakKeyCode, handleCode, fullRawLower, dict)) return true

        val isWordBreakKey = isWordBreakKey(breakKeyCode)
        val (bigramWord, rewroteBigram) = tryApplyBigramGrammar(state, breakKeyCode, handleCode, fullRawLower, composedLower, fullRawStr, isWordBreakKey, dict)
        if (rewroteBigram) return true

        val priority = effectiveLanguagePriority(state)
        val replacementWord = resolveWordReplacement(state, breakKeyCode, priority, fullRawStr, fullRawLower, stdRawLower, composed, composedLower, fullKeys, hasAnyDiacritic, bigramWord, dict)

        val isTransformed = hasAnyDiacritic || composed != fullRawStr || isTrailingDoubledToneEscape
        val (shouldRestore, targetKeys) = shouldRestoreCurrentWord(state, breakKeyCode, priority, fullRawLower, stdRawLower, composedLower, fullRawStr, hasAnyDiacritic, isTransformed, isTrailingDoubledToneEscape, fullKeys, replacementWord, dict)

        val currentIsEnglish = isCurrentWordEnglish(shouldRestore, replacementWord, fullRawLower, composedLower, dict)
        if (tryRewriteTwoWordLookback(state, breakKeyCode, currentIsEnglish, shouldRestore, targetKeys, fullKeys, handleCode, dict)) return true

        if (shouldRestore) {
            return emitHookReplacement(state, handleCode, targetKeys, replacementWord, composed, composedLower, fullRawStr, fullRawLower, breakKeyCode, dict)
        } else {
            recordWordInSlidingWindow(state, breakKeyCode, composed, fullRawStr, stdRawKeys, fullKeys, hasAnyDiacritic, composedLower, fullRawLower, dict)
            return false
        }
    }

    private fun checkTrailingDoubledToneEscape(
        state: VietnameseEngineState, fullKeys: List<UInt>, fullRawLower: String, singleToneLower: String, dict: VietnameseDictionary
    ): Boolean {
        if (fullKeys.size < 3) return false
        val lastKey = (fullKeys[fullKeys.size - 1] and CHAR_MASK).toUShort()
        val prevKey = (fullKeys[fullKeys.size - 2] and CHAR_MASK).toUShort()
        return lastKey == prevKey && state.isMarkKey(lastKey) && !dict.isEnglishWord(fullRawLower) && dict.isEnglishWord(singleToneLower)
    }

    private fun recordTrailingEscape(state: VietnameseEngineState, composed: String, fullKeys: List<UInt>) {
        state.previousWords.add(
            PreviousWordEntry(
                rawString = composed, composedString = composed,
                rawKeys = fullKeys.dropLast(1), composedWord = state.typingWord.slice(0 until state.index),
                wasRestoredToEnglish = true
            )
        )
        if (state.previousWords.size > 2) state.previousWords.removeAt(0)
        state.consecutiveEnglishWordCount++
    }

    private fun tryCollapseDuplicateEnglishWord(
        state: VietnameseEngineState, breakKeyCode: UShort, handleCode: Int, fullRawLower: String, dict: VietnameseDictionary
    ): Boolean {
        if (state.vBilingualAutoRestore == 0 || breakKeyCode != KEY_SPACE || state.previousWords.isEmpty()) return false
        val prev = state.previousWords.last()
        if (prev.trailingSpaces == 1 &&
            (prev.composedString.lowercase() == fullRawLower || prev.rawString.lowercase() == fullRawLower) &&
            dict.englishDuplicateCollapseWords.contains(fullRawLower)
        ) {
            val targetWord = prev.rawString
            val totalBs = prev.composedString.length + 1 + state.index
            val keys = stringToTargetKeys(targetWord)
            state.hookOutput.code = handleCode
            state.hookOutput.backspaceCount = totalBs.coerceAtMost(255)
            state.hookOutput.newCharCount = keys.size.coerceAtMost(state.maxBuff)
            for (i in 0 until state.hookOutput.newCharCount) {
                val k = keys[i]
                state.hookOutput.charData[state.hookOutput.newCharCount - 1 - i] = if ((k and PURE_CHARACTER_MASK) != 0u) k else state.GET(k)
                state.typingWord[i] = k
                state.keyStates[i] = k
            }
            state.index = state.hookOutput.newCharCount
            state.stateIndex = state.index
            state.previousWords[state.previousWords.size - 1].composedString = targetWord
            state.previousWords[state.previousWords.size - 1].wasRestoredToEnglish = true
            state.tempDisableKey = false
            return true
        }
        return false
    }

    private fun isWordBreakKey(breakKeyCode: UShort): Boolean =
        breakKeyCode == KEY_SPACE || breakKeyCode == VietnameseData.KEY_COMMA ||
                breakKeyCode == VietnameseData.KEY_DOT || breakKeyCode == VietnameseData.KEY_SLASH ||
                breakKeyCode == VietnameseData.KEY_SEMICOLON || breakKeyCode == VietnameseData.KEY_QUOTE

    private fun tryApplyBigramGrammar(
        state: VietnameseEngineState, breakKeyCode: UShort, handleCode: Int,
        fullRawLower: String, composedLower: String, fullRawStr: String,
        isWordBreakKey: Boolean, dict: VietnameseDictionary
    ): Pair<String?, Boolean> {
        if (!isWordBreakKey || state.previousWords.isEmpty()) return Pair(null, false)
        if (AutoWordingEngine.shared.isEnabled && !AutoWordingEngine.shared.grammarRepairEnabled) return Pair(null, false)
        val prevEntry = state.previousWords.last()
        if (prevEntry.trailingSpaces != 1) return Pair(null, false)
        val prevWord = prevEntry.composedString
        val cand1 = "${prevWord.lowercase()} $fullRawLower"
        val cand2 = "${prevWord.lowercase()} $composedLower"
        val engMatch = if (state.vBilingualAutoRestore != 0) {
            dict.englishBigramGrammar[cand1] ?: dict.englishBigramGrammar[cand2]
        } else null
        val matched = engMatch ?: dict.vietnameseBigramGrammar[cand1] ?: dict.vietnameseBigramGrammar[cand2] ?: return Pair(null, false)

        val parts = matched.split(" ")
        if (parts.size != 2) return Pair(null, false)
        val (newPrev, newCur) = parts
        if (newPrev.lowercase() == prevWord.lowercase()) {
            val replacementWord = applyCasing(newCur, fullRawStr.firstOrNull()?.isUpperCase() == true)
            return Pair(replacementWord, false)
        } else if (breakKeyCode == KEY_SPACE) {
            val finalPrev = applyCasing(newPrev, prevWord.firstOrNull()?.isUpperCase() == true)
            val finalCur = applyCasing(newCur, fullRawStr.firstOrNull()?.isUpperCase() == true)
            val combined = "$finalPrev $finalCur"
            val totalBs = state.index + 1 + prevWord.length
            val keys = stringToTargetKeys(combined)

            state.hookOutput.code = handleCode
            state.hookOutput.backspaceCount = totalBs.coerceAtMost(255)
            state.hookOutput.newCharCount = keys.size.coerceAtMost(state.maxBuff)
            for (i in 0 until state.hookOutput.newCharCount) {
                val k = keys[i]
                state.hookOutput.charData[state.hookOutput.newCharCount - 1 - i] = if ((k and PURE_CHARACTER_MASK) != 0u) k else state.GET(k)
            }
            state.previousWords[state.previousWords.size - 1].composedString = finalPrev
            state.previousWords[state.previousWords.size - 1].rawString = finalPrev
            state.previousWords.add(
                PreviousWordEntry(
                    rawString = finalCur, composedString = finalCur,
                    rawKeys = stringToTargetKeys(finalCur), composedWord = stringToTargetKeys(finalCur),
                    wasRestoredToEnglish = true
                )
            )
            if (state.previousWords.size > 2) state.previousWords.removeAt(0)
            state.consecutiveEnglishWordCount++
            state.startNewSessionInternal()
            state.tempDisableKey = false
            return Pair(null, true)
        }
        return Pair(null, false)
    }

    private fun resolveWordReplacement(
        state: VietnameseEngineState, breakKeyCode: UShort, priority: LanguagePriority,
        fullRawStr: String, fullRawLower: String, stdRawLower: String,
        composed: String, composedLower: String, fullKeys: List<UInt>,
        hasAnyDiacritic: Boolean, bigramWord: String?, dict: VietnameseDictionary
    ): String? {
        val bilingualOn = state.vBilingualAutoRestore != 0
        var rw = bigramWord
        if (rw == null && bilingualOn) {
            dict.englishContractionGrammar[fullRawLower]?.let { contraction ->
                if (fullRawLower != "im" || priority == LanguagePriority.ENGLISH_FIRST) {
                    rw = applyCasing(contraction, fullRawStr.firstOrNull()?.isUpperCase() == true)
                }
            }
        }
        val rawCapCount = fullKeys.count { (it and CAPS_MASK) != 0u }
        val hasTrailingUndo = state.droppedKeys.isNotEmpty() && fullRawLower.length >= 2 && fullRawLower.last() == fullRawLower[fullRawLower.length - 2]
        if (rw == null && rawCapCount < 2 && !hasTrailingUndo && !(breakKeyCode == KEY_QUOTE && fullRawLower.endsWith("n"))) {
            val prevWord = state.previousWords.lastOrNull()?.composedString
            val isCap = composed.firstOrNull()?.isUpperCase() == true || fullRawStr.firstOrNull()?.isUpperCase() == true
            rw = if (bilingualOn && priority == LanguagePriority.ENGLISH_FIRST) {
                val engMatch = if (!dict.isValidVietnameseSyllable(composedLower)) {
                    dict.englishTypoCorrections[fullRawLower] ?: dict.findEnglishTranspositionMatch(fullRawLower)
                } else null
                engMatch?.let { applyCasing(it, fullRawStr.firstOrNull()?.isUpperCase() == true) }
                    ?: (dict.vietnameseTypoCorrections[composedLower] ?: dict.vietnameseTypoCorrections[fullRawLower])?.let { applyCasing(it, isCap) }
                    ?: run {
                        if (!dict.isValidVietnameseSyllable(composedLower) && !dict.isEnglishWord(fullRawLower) && !dict.isEnglishWord(composedLower)) {
                            val sp = dict.findVietnameseSpatialMatch(fullRawLower, composedLower, prevWord)
                                ?: if (stdRawLower != fullRawLower && !dict.isEnglishWord(stdRawLower)) dict.findVietnameseSpatialMatch(stdRawLower, composedLower, prevWord) else null
                            sp?.let { applyCasing(it, isCap) }
                        } else null
                    }
            } else {
                (dict.vietnameseTypoCorrections[composedLower] ?: dict.vietnameseTypoCorrections[fullRawLower])?.let { applyCasing(it, isCap) }
                    ?: run {
                        if (!dict.isValidVietnameseSyllable(composedLower) && (!bilingualOn || (!dict.isEnglishWord(fullRawLower) && !dict.isEnglishWord(composedLower)))) {
                            val sp = dict.findVietnameseSpatialMatch(fullRawLower, composedLower, prevWord)
                                ?: if (stdRawLower != fullRawLower && (!bilingualOn || !dict.isEnglishWord(stdRawLower))) dict.findVietnameseSpatialMatch(stdRawLower, composedLower, prevWord) else null
                            sp?.let { applyCasing(it, isCap) }
                                ?: if (bilingualOn) {
                                    dict.englishTypoCorrections[fullRawLower]?.let { applyCasing(it, fullRawStr.firstOrNull()?.isUpperCase() == true) }
                                        ?: dict.findEnglishTranspositionMatch(fullRawLower)?.let { applyCasing(it, fullRawStr.firstOrNull()?.isUpperCase() == true) }
                                } else null
                        } else null
                    }
            }
        }
        val shouldCapSentence = state.sentenceEndedWithPunctuation && AutoWordingEngine.shared.isEnabled &&
                AutoWordingEngine.shared.grammarRepairEnabled && state.wordStartOk &&
                composedLower != "i" && fullRawLower != "i"
        if (breakKeyCode == KEY_SPACE) state.sentenceEndedWithPunctuation = false
        if (shouldCapSentence && breakKeyCode == KEY_SPACE) {
            rw = rw?.let { it.replaceFirstChar { c -> c.uppercase() } } ?: run {
                val wordToCap = if ((dict.isEnglishWord(composedLower) && !hasAnyDiacritic) || (priority != LanguagePriority.ENGLISH_FIRST && dict.isValidVietnameseSyllable(composedLower))) composed else fullRawStr
                if (wordToCap.firstOrNull()?.isLowerCase() == true) wordToCap.replaceFirstChar { c -> c.uppercase() } else null
            }
        }
        return if (rw == composed) null else rw
    }

    private fun shouldRestoreCurrentWord(
        state: VietnameseEngineState, breakKeyCode: UShort, priority: LanguagePriority,
        fullRawLower: String, stdRawLower: String, composedLower: String, fullRawStr: String,
        hasAnyDiacritic: Boolean, isTransformed: Boolean, isTrailingDoubledToneEscape: Boolean,
        fullKeys: List<UInt>, replacementWord: String?, dict: VietnameseDictionary
    ): Pair<Boolean, List<UInt>> {
        val rawCapCount = fullKeys.count { (it and CAPS_MASK) != 0u }
        val hasTrailingUndo = state.droppedKeys.isNotEmpty() && fullRawLower.length >= 2 && fullRawLower.last() == fullRawLower[fullRawLower.length - 2]
        if (replacementWord != null) {
            return Pair(true, stringToTargetKeys(replacementWord))
        } else if (state.vBilingualAutoRestore == 0) {
            if (isTransformed && (!state.wordStartOk || (state.tempDisableKey && (hasAnyDiacritic || (state.droppedKeys.isNotEmpty() && !hasTrailingUndo))))) {
                return Pair(true, state.keyStates.slice(0 until state.stateIndex))
            }
            return Pair(false, fullKeys)
        } else if (dict.isEnglishWord(composedLower) && !hasAnyDiacritic) {
            return Pair(false, fullKeys)
        } else if (isTransformed) {
            val isShortAllCapsToneOnlyAcronym = fullKeys.size <= 3 && state.vInputType == 0 &&
                (0 until state.index).none { state.typingWord[it].hasCircumflexOrStroke || state.typingWord[it].hasHornOrBreve } &&
                fullRawStr.all { it.isUpperCase() }
            val restoreKeys = when {
                isTrailingDoubledToneEscape -> fullKeys.dropLast(1)
                !state.wordStartOk -> fullKeys
                state.droppedKeys.isNotEmpty() && dict.isEnglishWord(fullRawLower) && !dict.isEnglishWord(composedLower) -> fullKeys
                dict.englishPriorityWords.contains(fullRawLower) -> fullKeys
                priority == LanguagePriority.ENGLISH_FIRST && dict.isEnglishWord(fullRawLower) -> fullKeys
                dict.isEnglishWord(fullRawLower) && !dict.isValidVietnameseSyllable(composedLower) -> fullKeys
                rawCapCount >= 2 && (!dict.isValidVietnameseSyllable(composedLower) || isShortAllCapsToneOnlyAcronym) -> fullKeys
                breakKeyCode == KEY_QUOTE && fullRawLower.endsWith("n") -> fullKeys
                breakKeyCode == KEY_MINUS && fullRawLower.startsWith("w") -> fullKeys
                state.hasDigitPrefix && !dict.isValidVietnameseSyllable(composedLower) -> fullKeys
                state.tempDisableKey && (hasAnyDiacritic || (state.droppedKeys.isNotEmpty() && !hasTrailingUndo)) -> if (dict.isEnglishWord(fullRawLower)) fullKeys else state.keyStates.slice(0 until state.stateIndex)
                else -> null
            }
            if (restoreKeys != null) return Pair(true, restoreKeys)
        }
        return Pair(false, fullKeys)
    }

    private fun isCurrentWordEnglish(
        shouldRestore: Boolean, replacementWord: String?, fullRawLower: String, composedLower: String, dict: VietnameseDictionary
    ): Boolean {
        val restoredIsEnglish = shouldRestore && (replacementWord == null || dict.isEnglishWord(replacementWord.lowercase()) || replacementWord == "I")
        return restoredIsEnglish || dict.englishPriorityWords.contains(fullRawLower) ||
                (dict.isEnglishWord(fullRawLower) && !dict.isValidVietnameseSyllable(composedLower)) ||
                fullRawLower in setOf("is", "as", "us", "if", "of", "or", "for", "his", "has", "was")
    }

    private fun tryRewriteTwoWordLookback(
        state: VietnameseEngineState, breakKeyCode: UShort, currentIsEnglish: Boolean,
        shouldRestore: Boolean, targetKeys: List<UInt>, fullKeys: List<UInt>,
        handleCode: Int, dict: VietnameseDictionary
    ): Boolean {
        if (state.vBilingualAutoRestore == 0 || breakKeyCode != KEY_SPACE || !currentIsEnglish || !state.wordStartOk || state.previousWords.isEmpty()) return false
        var firstRewriteIdx: Int? = null
        for (idx in state.previousWords.indices) {
            val prev = state.previousWords[idx]
            if (!prev.wasRestoredToEnglish && prev.trailingSpaces == 1 && dict.englishLookbackStems.contains(prev.rawString.lowercase())) {
                val allSubsequentEnglish = ((idx + 1) until state.previousWords.size).all { subIdx ->
                    val r = state.previousWords[subIdx].rawString.lowercase()
                    state.previousWords[subIdx].trailingSpaces == 1 && (state.previousWords[subIdx].wasRestoredToEnglish || dict.isEnglishWord(r) || dict.englishLookbackStems.contains(r) || r == "a" || r == "i")
                }
                if (allSubsequentEnglish) { firstRewriteIdx = idx; break }
            }
        }
        val startIdx = firstRewriteIdx ?: return false
        var totalBackspace = state.index
        val combinedRawKeys = mutableListOf<UInt>()
        for (idx in startIdx until state.previousWords.size) {
            totalBackspace += state.previousWords[idx].composedString.length + 1
            combinedRawKeys.addAll(state.previousWords[idx].rawKeys)
            combinedRawKeys.add(KEY_SPACE.toUInt())
            state.previousWords[idx].wasRestoredToEnglish = true
            state.previousWords[idx].composedString = state.previousWords[idx].rawString
        }
        val curKeys = if (shouldRestore) targetKeys else fullKeys
        combinedRawKeys.addAll(curKeys)
        if (combinedRawKeys.size > 31) return false

        state.hookOutput.code = handleCode
        state.hookOutput.backspaceCount = totalBackspace.coerceAtMost(255)
        state.hookOutput.newCharCount = combinedRawKeys.size
        for (i in 0 until combinedRawKeys.size) {
            val k = combinedRawKeys[i]
            state.hookOutput.charData[combinedRawKeys.size - 1 - i] = when {
                k == KEY_SPACE.toUInt() -> 0x20u or PURE_CHARACTER_MASK
                (k and PURE_CHARACTER_MASK) != 0u -> k
                else -> state.GET(k)
            }
        }
        for (i in 0 until curKeys.size.coerceAtMost(state.maxBuff)) {
            state.typingWord[i] = curKeys[i]; state.keyStates[i] = curKeys[i]
        }
        state.index = curKeys.size.coerceAtMost(state.maxBuff)
        state.stateIndex = state.index
        state.tempDisableKey = false

        val restoredStr = keysToString(curKeys)
        state.previousWords.add(PreviousWordEntry(rawString = restoredStr, composedString = restoredStr, rawKeys = curKeys, composedWord = curKeys, wasRestoredToEnglish = true))
        if (state.previousWords.size > 2) state.previousWords.removeAt(0)
        state.consecutiveEnglishWordCount += 2
        if (state.consecutiveEnglishWordCount >= 3) state.hookOutput.languageSwitchRequest = 0
        return true
    }

    private fun emitHookReplacement(
        state: VietnameseEngineState, handleCode: Int, targetKeys: List<UInt>,
        replacementWord: String?, composed: String, composedLower: String,
        fullRawStr: String, fullRawLower: String, breakKeyCode: UShort,
        dict: VietnameseDictionary
    ): Boolean {
        val origTypingWord = state.typingWord.slice(0 until state.index)
        val origKeyStates = state.keyStates.slice(0 until state.stateIndex)
        val origIndex = state.index
        val origStateIndex = state.stateIndex

        state.hookOutput.code = handleCode
        state.hookOutput.backspaceCount = state.index
        state.hookOutput.newCharCount = targetKeys.size.coerceAtMost(state.maxBuff)
        for (i in 0 until state.hookOutput.newCharCount) {
            val tk = targetKeys[i]
            state.typingWord[i] = tk
            state.keyStates[i] = tk
            state.hookOutput.charData[state.hookOutput.newCharCount - 1 - i] = if ((tk and PURE_CHARACTER_MASK) != 0u) tk else state.GET(tk)
        }
        state.index = state.hookOutput.newCharCount
        state.stateIndex = state.index
        state.tempDisableKey = false

        val targetStr = keysToString(targetKeys)
        val breakChar = VietnameseData.keyCodeToCharacter(breakKeyCode.toUInt() or (if (state.isCaps) CAPS_MASK else 0u))
        val trailingCount = if (breakChar != 0.toUShort()) 1 else 0
        val origComposed = if (replacementWord != null && dict.vietnameseTypoCorrections[composedLower] == null &&
            dict.vietnameseBigramGrammar.values.none { it.endsWith(targetStr.lowercase()) }
        ) fullRawStr else composed

        state.autoRestoreUndoState = AutoRestoreUndoSnapshot(
            originalComposedString = origComposed, originalTypingWord = origTypingWord,
            originalIndex = origIndex, originalKeyStates = origKeyStates,
            originalStateIndex = origStateIndex, replacedLengthOnScreen = state.hookOutput.newCharCount + trailingCount,
            rawLowerWord = fullRawLower
        )

        val isEng = dict.isEnglishWord(targetStr.lowercase()) || dict.englishContractionGrammar.values.contains(targetStr) || targetStr == "I"
        if (breakKeyCode == KEY_SPACE) {
            state.previousWords.add(
                PreviousWordEntry(rawString = targetStr, composedString = targetStr, rawKeys = targetKeys, composedWord = targetKeys, wasRestoredToEnglish = isEng && state.vBilingualAutoRestore != 0)
            )
            if (state.previousWords.size > 2) state.previousWords.removeAt(0)
            if (isEng && state.vBilingualAutoRestore != 0) {
                state.consecutiveEnglishWordCount++
                if (state.consecutiveEnglishWordCount >= 3) state.hookOutput.languageSwitchRequest = 0
            } else {
                state.consecutiveEnglishWordCount = 0
            }
        } else {
            state.previousWords.clear()
            if (state.vBilingualAutoRestore != 0 && breakKeyCode == VietnameseData.KEY_COMMA && (isEng || !state.wordStartOk)) {
                state.consecutiveEnglishWordCount += 1
            } else if (breakKeyCode != VietnameseData.KEY_COMMA || !isEng || state.vBilingualAutoRestore == 0) {
                state.consecutiveEnglishWordCount = 0
            }
        }
        return true
    }

    private fun recordWordInSlidingWindow(
        state: VietnameseEngineState, breakKeyCode: UShort,
        composed: String, fullRawStr: String, stdRawKeys: List<UInt>, fullKeys: List<UInt>,
        hasAnyDiacritic: Boolean, composedLower: String, fullRawLower: String, dict: VietnameseDictionary
    ) {
        val vietnameseShorthandSet = setOf("ko", "dc", "vs", "vk", "ck", "mk", "nt", "nc", "bt", "bh", "uk", "uh", "ak", "ah")
        val isUnaccentedNonVn = !hasAnyDiacritic && composedLower.length >= 2 && !dict.isValidVietnameseSyllable(composedLower) && !vietnameseShorthandSet.contains(composedLower)
        val alreadyInEng = state.previousWords.lastOrNull()?.wasRestoredToEnglish == true || state.consecutiveEnglishWordCount > 0
        val isComposedEng = (dict.isEnglishWord(composedLower) && !hasAnyDiacritic && (!dict.isValidVietnameseSyllable(composedLower) || dict.englishPriorityWords.contains(composedLower) || alreadyInEng)) || composed == "I" || isUnaccentedNonVn
        val isEng = isComposedEng || (dict.isEnglishWord(fullRawLower) && !hasAnyDiacritic && (!dict.isValidVietnameseSyllable(composedLower) || dict.englishPriorityWords.contains(fullRawLower) || alreadyInEng))
        if (breakKeyCode == KEY_SPACE) {
            state.previousWords.add(
                PreviousWordEntry(
                    rawString = if (isComposedEng) composed else fullRawStr,
                    composedString = composed,
                    rawKeys = if (isComposedEng) stdRawKeys else fullKeys,
                    composedWord = state.typingWord.slice(0 until state.index),
                    wasRestoredToEnglish = isEng && state.vBilingualAutoRestore != 0
                )
            )
            if (state.previousWords.size > 2) repeat(state.previousWords.size - 2) { state.previousWords.removeAt(0) }
            if (isEng && state.vBilingualAutoRestore != 0) {
                state.consecutiveEnglishWordCount += 1
                if (state.consecutiveEnglishWordCount >= 3) state.hookOutput.languageSwitchRequest = 0
            } else {
                state.consecutiveEnglishWordCount = 0
            }
        } else {
            state.previousWords.clear()
            if (state.vBilingualAutoRestore != 0 && breakKeyCode == VietnameseData.KEY_COMMA && (isEng || !state.wordStartOk)) {
                state.consecutiveEnglishWordCount += 1
            } else {
                state.consecutiveEnglishWordCount = 0
            }
        }
    }
}
