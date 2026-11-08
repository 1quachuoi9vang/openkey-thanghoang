package com.tuyenmai.openkey.core

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class VietnameseEngineState {
    companion object {
        val shared = VietnameseEngineState()

        val doubleConsonantEnglishStems: Set<String> = setOf(
            "err", "corr", "curr", "sorr", "morr", "tomorr", "borr", "carr", "marr", "hurr", "worr",
            "arr", "narr", "mirr", "terr", "horr", "occurr", "recurr", "off", "eff", "diff", "buff",
            "suff", "coff", "traff", "aff", "stiff", "cliff", "staff", "stuff", "mess", "iss", "tiss",
            "sess", "less", "poss", "pass", "ass", "press", "express", "impress", "compress", "address",
            "process", "success", "access", "assess", "discuss", "miss", "loss", "cross", "boss", "class",
            "glass", "grass", "mass", "dress", "stress", "guess"
        )
    }

    val lock = ReentrantLock()

    // Configuration
    var vLanguage: Int = 1
    var vInputType: Int = 0 // 0: Telex, 1: VNI
    var vCheckSpelling: Int = 1
    var vUseModernOrthography: Int = 1
    var vRestoreIfWrongSpelling: Int = 1
    var vFixRecommendBrowser: Int = 1
    var vUseSmartSwitchKey: Int = 1

    var phonotacticRepairHook: ((UIntArray, Int, Int, Int, Int, Boolean) -> Boolean)? = null

    val charKeyCode = ushortArrayOf(
        KEY_BACKQUOTE, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9, KEY_0,
        KEY_MINUS, KEY_EQUALS, KEY_LEFT_BRACKET, KEY_RIGHT_BRACKET, KEY_BACK_SLASH, KEY_SEMICOLON,
        KEY_QUOTE, KEY_COMMA, KEY_DOT, KEY_SLASH
    )
    val breakCode = ushortArrayOf(
        KEY_ESC, KEY_TAB, KEY_ENTER, KEY_RETURN, KEY_LEFT, KEY_RIGHT, KEY_DOWN, KEY_UP,
        KEY_COMMA, KEY_DOT, KEY_SLASH, KEY_SEMICOLON, KEY_QUOTE, KEY_BACK_SLASH, KEY_MINUS,
        KEY_EQUALS, KEY_BACKQUOTE, KEY_TAB
    )
    val processingChar = arrayOf(
        ushortArrayOf(KEY_S, KEY_F, KEY_R, KEY_X, KEY_J, KEY_A, KEY_O, KEY_E, KEY_W, KEY_D, KEY_Z),
        ushortArrayOf(KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_6, KEY_7, KEY_8, KEY_9, KEY_0)
    )

    val maxBuff = 32
    val typingWord = UIntArray(32) { KEY_EMPTY.toUInt() }
    var index: Int = 0
    val longWordHelper = mutableListOf<UInt>()
    val typingStates = mutableListOf<List<UInt>>()
    val typingStatesData = mutableListOf<UInt>()

    val keyStates = UIntArray(32) { KEY_EMPTY.toUInt() }
    var stateIndex: Int = 0

    val rawKeyStates = UIntArray(64) { KEY_EMPTY.toUInt() }
    var rawStateIndex: Int = 0
    val droppedKeys = mutableListOf<Pair<UInt, Int>>()

    var hasDigitPrefix: Boolean = false
    var wordStartOk: Boolean = true
    var tempDisableKey: Boolean = false
    var isCorrect: Boolean = false
    var isChanged: Boolean = false
    var vowelCount: Int = 0
    var vowelStartIndex: Int = 0
    var vowelEndIndex: Int = 0
    var vowelWillSetMark: Int = 0
    var isRestoredW: Boolean = false
    var keyForAEO: UShort = 0u
    var isCheckedGrammar: Boolean = false
    var isCaps: Boolean = false
    var spaceCount: Int = 0
    var isCharKeyCode: Boolean = false
    val specialChar = mutableListOf<UInt>()
    var useSpellCheckingBefore: Int = 1
    var willTempOffEngine: Boolean = false

    var spellingOK: Boolean = false
    var spellingFlag: Boolean = false
    var spellingVowelOK: Boolean = false
    var spellingEndIndex: Int = 0

    val previousWords = mutableListOf<PreviousWordEntry>()
    var consecutiveEnglishWordCount: Int = 0
    var autoRestoreUndoState: AutoRestoreUndoSnapshot? = null
    var undoneRawWord: String? = null
    var lastSpaceUptime: Double = 0.0
    var sentenceEndedWithPunctuation: Boolean = false

    fun effectiveLanguagePriority(): LanguagePriority = BilingualAutoSwitchEngine.effectiveLanguagePriority(this)
    fun reconstructedRawKeys(): List<UInt> = rawKeyStates.slice(0 until rawStateIndex)

    class HookOutput {
        var code: Int = 0
        var backspaceCount: Int = 0
        var newCharCount: Int = 0
        var extCode: Int = 0
        val charData = UIntArray(32)
        var languageSwitchRequest: Int = -1
    }
    val hookOutput = HookOutput()

    fun isKeyZ(key: UShort) = processingChar[vInputType][10] == key
    fun isKeyD(key: UShort) = processingChar[vInputType][9] == key
    fun isKeyW(key: UShort): Boolean =
        if (vInputType != 1) processingChar[vInputType][8] == key else (processingChar[vInputType][8] == key || processingChar[vInputType][7] == key)
    fun isKeyDouble(key: UShort): Boolean =
        if (vInputType != 1) (processingChar[vInputType][5] == key || processingChar[vInputType][6] == key || processingChar[vInputType][7] == key) else processingChar[vInputType][6] == key
    fun isKeyS(key: UShort) = processingChar[vInputType][0] == key
    fun isKeyF(key: UShort) = processingChar[vInputType][1] == key
    fun isKeyR(key: UShort) = processingChar[vInputType][2] == key
    fun isKeyX(key: UShort) = processingChar[vInputType][3] == key
    fun isKeyJ(key: UShort) = processingChar[vInputType][4] == key

    fun isMarkKey(keyCode: UShort): Boolean =
        if (vInputType != 1) listOf(KEY_S, KEY_F, KEY_R, KEY_J, KEY_X).contains(keyCode) else listOf(KEY_1, KEY_2, KEY_3, KEY_5, KEY_4).contains(keyCode)
    fun isBracketKey(key: UShort) = key == KEY_LEFT_BRACKET || key == KEY_RIGHT_BRACKET
    fun isSpecialKey(keyCode: UShort): Boolean =
        if (vInputType == 0) listOf(KEY_W, KEY_E, KEY_R, KEY_O, KEY_LEFT_BRACKET, KEY_RIGHT_BRACKET, KEY_A, KEY_S, KEY_D, KEY_F, KEY_J, KEY_Z, KEY_X).contains(keyCode) else VietnameseData.isNumberKey(keyCode)

    fun CHR(idx: Int): UShort {
        if (idx in 0 until index && idx < maxBuff) {
            return (typingWord[idx] and CHAR_MASK).toUShort()
        }
        return KEY_EMPTY
    }

    fun GET(data: UInt): UInt = getCharacterCode(data)

    fun isWordBreak(isMouse: Boolean, data: UShort) = isMouse || breakCode.contains(data)

    fun setKeyData(idx: Int, keyCode: UShort, caps: Boolean) {
        typingWord[idx] = keyCode.toUInt() or (if (caps) CAPS_MASK else 0u)
    }

    fun clearBuffersInternal() {
        index = 0
        stateIndex = 0
        rawStateIndex = 0
        droppedKeys.clear()
        hasDigitPrefix = false
        tempDisableKey = false
        longWordHelper.clear()
        for (i in 0 until maxBuff) {
            typingWord[i] = KEY_EMPTY.toUInt()
            keyStates[i] = KEY_EMPTY.toUInt()
        }
        for (i in rawKeyStates.indices) {
            rawKeyStates[i] = KEY_EMPTY.toUInt()
        }
    }

    fun initEngine() {
        lock.withLock {
            clearBuffersInternal()
            isCorrect = false
            isChanged = false
            vowelCount = 0
            vowelStartIndex = 0
            vowelEndIndex = 0
            vowelWillSetMark = 0
            isRestoredW = false
            keyForAEO = 0u
            isCheckedGrammar = false
            isCaps = false
            spaceCount = 0
            isCharKeyCode = false
            specialChar.clear()
            useSpellCheckingBefore = vCheckSpelling
            willTempOffEngine = false
            hasDigitPrefix = false
            wordStartOk = true

            previousWords.clear()
            consecutiveEnglishWordCount = 0
            autoRestoreUndoState = null
            undoneRawWord = null
            sentenceEndedWithPunctuation = false

            if (phonotacticRepairHook == null) {
                phonotacticRepairHook = { tw, idx, vsi, vei, vc, isIns ->
                    AutoWordingEngine.shared.repairPhonotacticCluster(tw, idx, vsi, vei, vc, isIns)
                }
            }
        }
    }

    fun getWordContext(forKeyCode: UShort? = null): OKWordContext = lock.withLock {
        getWordContextUnlocked(forKeyCode)
    }

    fun getWordContextUnlocked(forKeyCode: UShort? = null): OKWordContext {
        val wordChars = StringBuilder()
        for (i in 0 until index) {
            val code = GET(typingWord[i])
            val utf16Val: UShort = if ((code and CHAR_CODE_MASK) != 0u || (code and PURE_CHARACTER_MASK) != 0u) {
                (code and 0xFFFFu).toUShort()
            } else {
                VietnameseData.keyCodeToCharacter(code)
            }
            if (utf16Val != 0.toUShort()) {
                wordChars.append(utf16Val.toInt().toChar())
            }
        }
        val rawChars = StringBuilder()
        for (i in 0 until stateIndex) {
            val rawVal = VietnameseData.keyCodeToCharacter(keyStates[i])
            if (rawVal != 0.toUShort()) {
                rawChars.append(rawVal.toInt().toChar())
            }
        }
        val isBreak = forKeyCode?.let {
            isWordBreak(false, it) || it == KEY_SPACE || it == KEY_RETURN || it == KEY_ENTER
        } ?: false

        return OKWordContext(
            composedWord = wordChars.toString(),
            rawKeystrokes = rawChars.toString(),
            charCount = index,
            spaceCount = spaceCount,
            isWrongSpelling = tempDisableKey,
            isBreakKey = isBreak
        )
    }

    fun commitExternalExpansion() {
        lock.withLock {
            clearBuffersInternal()
            spaceCount = 0
            specialChar.clear()
            typingStates.clear()
            typingStatesData.clear()
        }
    }

    fun resetSession(): Boolean = lock.withLock {
        val wasDirty = index > 0 || stateIndex > 0 || rawStateIndex > 0 ||
                specialChar.isNotEmpty() || typingStates.isNotEmpty() || spaceCount > 0 ||
                hasDigitPrefix || !wordStartOk || willTempOffEngine
        clearBuffersInternal()
        specialChar.clear()
        typingStates.clear()
        typingStatesData.clear()
        spaceCount = 0
        willTempOffEngine = false
        hasDigitPrefix = false
        wordStartOk = true

        previousWords.clear()
        consecutiveEnglishWordCount = 0
        autoRestoreUndoState = null
        undoneRawWord = null
        sentenceEndedWithPunctuation = false
        AutoWordingEngine.shared.clearCandidates()

        wasDirty
    }

    fun startNewSessionInternal() {
        clearBuffersInternal()
    }

    fun getComposedWordInternal(): String {
        val sb = StringBuilder()
        for (i in 0 until index) {
            val code = GET(typingWord[i])
            val valCode: UShort = if ((code and CHAR_CODE_MASK) != 0u || (code and PURE_CHARACTER_MASK) != 0u) {
                (code and 0xFFFFu).toUShort()
            } else {
                VietnameseData.keyCodeToCharacter(code)
            }
            if (valCode != 0.toUShort()) {
                sb.append(valCode.toInt().toChar())
            }
        }
        return sb.toString()
    }

    fun getComposedWord(): String = lock.withLock {
        getComposedWordInternal()
    }

    fun insertKey(keyCode: UShort, caps: Boolean, isCheckSpellingFlag: Boolean = true) {
        if (index >= maxBuff) {
            longWordHelper.add(typingWord[0])
            for (iii in 0 until (maxBuff - 1)) {
                typingWord[iii] = typingWord[iii + 1]
            }
            setKeyData(index - 1, keyCode, caps)
        } else {
            setKeyData(index, keyCode, caps)
            index++
        }
        if (vCheckSpelling != 0 && isCheckSpellingFlag) {
            checkSpelling()
        }
        if (keyCode == KEY_D && index - 2 >= 0 && VietnameseData.isConsonant(CHR(index - 2))) {
            tempDisableKey = false
        }
    }

    fun insertState(keyCode: UShort, caps: Boolean) {
        val entry = keyCode.toUInt() or (if (caps) CAPS_MASK else 0u)
        if (stateIndex >= maxBuff) {
            for (iii in 0 until (maxBuff - 1)) {
                keyStates[iii] = keyStates[iii + 1]
            }
            keyStates[stateIndex - 1] = entry
        } else {
            keyStates[stateIndex] = entry
            stateIndex++
        }
        if (rawStateIndex >= rawKeyStates.size) {
            for (iii in 0 until (rawKeyStates.size - 1)) {
                rawKeyStates[iii] = rawKeyStates[iii + 1]
            }
            rawKeyStates[rawStateIndex - 1] = entry
        } else {
            rawKeyStates[rawStateIndex] = entry
            rawStateIndex++
        }
    }

    fun saveWord() {
        if (index <= 0) return
        if (longWordHelper.isNotEmpty()) {
            typingStatesData.clear()
            for ((i, valData) in longWordHelper.withIndex()) {
                if (i != 0 && i % maxBuff == 0) {
                    typingStates.add(typingStatesData.toList())
                    typingStatesData.clear()
                }
                typingStatesData.add(valData)
            }
            typingStates.add(typingStatesData.toList())
            longWordHelper.clear()
        }
        typingStates.add(typingWord.slice(0 until index))
    }

    fun saveWord(keyCode: UInt, count: Int) {
        typingStates.add(List(count) { keyCode })
    }

    fun saveSpecialChar() {
        typingStates.add(specialChar.toList())
        specialChar.clear()
    }

    fun restoreLastTypingState() {
        if (typingStates.isEmpty()) return
        val restored = typingStates.removeLast()
        if (restored.isEmpty()) return
        if (restored[0] == KEY_SPACE.toUInt()) {
            spaceCount = restored.size
            index = 0
        } else if (charKeyCode.contains((restored[0] and CHAR_MASK).toUShort())) {
            index = 0
            specialChar.clear()
            specialChar.addAll(restored)
            checkSpelling()
        } else {
            for (i in restored.indices) {
                typingWord[i] = restored[i]
                keyStates[i] = restored[i]
                rawKeyStates[i] = restored[i]
            }
            index = restored.size
            stateIndex = index
            rawStateIndex = index
            droppedKeys.clear()
            for (i in index until maxBuff) {
                typingWord[i] = KEY_EMPTY.toUInt()
                keyStates[i] = KEY_EMPTY.toUInt()
            }
            for (i in index until rawKeyStates.size) {
                rawKeyStates[i] = KEY_EMPTY.toUInt()
            }
            if (vCheckSpelling != 0) {
                checkSpelling()
            }
        }
    }

    fun restoreWordFromRawKeys(oldScreenLength: Int) {
        val targetLen = minOf(rawStateIndex, maxBuff)
        var diffIdx = 0
        while (diffIdx < oldScreenLength && diffIdx < targetLen) {
            if (GET(typingWord[diffIdx]) != GET(rawKeyStates[diffIdx])) {
                break
            }
            diffIdx++
        }
        for (i in 0 until targetLen) {
            typingWord[i] = rawKeyStates[i]
            keyStates[i] = rawKeyStates[i]
        }
        for (i in targetLen until maxBuff) {
            typingWord[i] = KEY_EMPTY.toUInt()
            keyStates[i] = KEY_EMPTY.toUInt()
        }
        index = targetLen
        stateIndex = targetLen
        droppedKeys.clear()
        tempDisableKey = true
        if (vCheckSpelling != 0) {
            checkSpelling(forceCheckVowel = true)
        }
        val bsCount = maxOf(0, oldScreenLength - diffIdx)
        val newCount = maxOf(0, targetLen - diffIdx)
        hookOutput.code = HookCodeState.WILL_PROCESS
        hookOutput.backspaceCount = bsCount
        hookOutput.newCharCount = newCount
        hookOutput.extCode = 3
        for (i in diffIdx until targetLen) {
            hookOutput.charData[targetLen - 1 - i] = GET(typingWord[i])
        }
    }

    fun checkLiveMidWordAutoEscape(keyCode: UShort): Boolean {
        if (vInputType != 0) return false

        // 1. Double-Consonant rr/ff/ss + Vowel Live Mid-Word Restoration
        // ("correct", "error", "errors", "sorry", "current", "office", "coffee", "message", "issue")
        if (tempDisableKey && droppedKeys.isNotEmpty() && rawStateIndex >= 4) {
            val vowelSet = setOf(KEY_A, KEY_E, KEY_I, KEY_O, KEY_U, KEY_Y)
            if (vowelSet.contains(keyCode)) {
                val rawBeforeLen = rawStateIndex - 1
                if (rawBeforeLen >= 2) {
                    val c1 = (rawKeyStates[rawBeforeLen - 1] and CHAR_MASK).toUShort()
                    val c2 = (rawKeyStates[rawBeforeLen - 2] and CHAR_MASK).toUShort()
                    if (c1 == c2 && setOf(KEY_R, KEY_F, KEY_S, KEY_J, KEY_X).contains(c1)) {
                        val sb = StringBuilder()
                        for (i in 0 until rawBeforeLen) {
                            val c = VietnameseData.keyCodeToCharacter(rawKeyStates[i])
                            if (c != 0.toUShort()) sb.append(c.toInt().toChar())
                        }
                        val stemLower = sb.toString().lowercase()
                        if (doubleConsonantEnglishStems.contains(stemLower)) {
                            restoreWordFromRawKeys(oldScreenLength = index)
                            return true
                        }
                    }
                }
            }
        }

        // 2. Compound Interleaved Tone + Circumflex Telex Escape (t-h-e-s-e + s/e -> "these")
        if (!tempDisableKey && index >= 2 && rawStateIndex - 1 > index) {
            findAndCalculateVowel()
            if (vowelCount > 0) {
                var matchingVi: Int? = null
                for (vi in vowelStartIndex..vowelEndIndex) {
                    if ((typingWord[vi] and TONE_MASK) != 0u && (typingWord[vi] and MARK_MASK) != 0u) {
                        val matches = keyCode == CHR(vi) ||
                                ((typingWord[vi] and MARK1_MASK) != 0u && keyCode == KEY_S) ||
                                ((typingWord[vi] and MARK2_MASK) != 0u && keyCode == KEY_F) ||
                                ((typingWord[vi] and MARK3_MASK) != 0u && keyCode == KEY_R) ||
                                ((typingWord[vi] and MARK4_MASK) != 0u && keyCode == KEY_X) ||
                                ((typingWord[vi] and MARK5_MASK) != 0u && keyCode == KEY_J)
                        if (matches) {
                            matchingVi = vi
                            break
                        }
                    }
                }
                if (matchingVi != null) {
                    if (rawStateIndex - 1 >= 3) {
                        val prevKey = (rawKeyStates[rawStateIndex - 2] and CHAR_MASK).toUShort()
                        val prevPrevKey = (rawKeyStates[rawStateIndex - 3] and CHAR_MASK).toUShort()
                        if (prevKey == CHR(matchingVi) && isMarkKey(prevPrevKey)) {
                            if (rawStateIndex > 0) rawStateIndex--
                            if (stateIndex > 0) stateIndex--
                            restoreWordFromRawKeys(oldScreenLength = index)
                            return true
                        }
                    }
                }
            }
        }

        // 3. Post-Tone KEY_E Mid-Word Auto-Restore ("more", "core", "store", "care", "share", "fire", "sure", "case", "nose", "use", "house", "life")
        if (!tempDisableKey && keyCode == KEY_E && index >= 1) {
            findAndCalculateVowel()
            if (vowelCount > 0 && vowelEndIndex == index - 1) {
                val vRange = vowelStartIndex..vowelEndIndex
                val noEorY = vRange.none { CHR(it) == KEY_E || CHR(it) == KEY_Y }
                val noTone = vRange.none { (typingWord[it] and (TONE_MASK or TONEW_MASK)) != 0u }
                val hasMark = vRange.any { (typingWord[it] and MARK_MASK) != 0u }
                val prevIsMark = rawStateIndex >= 2 && isMarkKey((rawKeyStates[rawStateIndex - 2] and CHAR_MASK).toUShort())
                if (noEorY && noTone && hasMark && prevIsMark) {
                    restoreWordFromRawKeys(oldScreenLength = index)
                    return true
                }
            }
        }

        // 4. Invalid Post-Vowel Coda Consonant Mid-Word Auto-Restore ("needed", "good", "book", "feel", "work", "word", "part", "start", "sort", "soft", "left", "text", "next")
        if (!tempDisableKey && index >= 2) {
            findAndCalculateVowel()
            if (vowelCount > 0 && vowelEndIndex == index - 1) {
                val vRange = vowelStartIndex..vowelEndIndex
                val hasVowelMod = vRange.any { (typingWord[it] and (TONE_MASK or TONEW_MASK or MARK_MASK)) != 0u }
                if (hasVowelMod) {
                    val isImpossibleCoda = setOf(VietnameseData.KEY_B, KEY_K, KEY_L, KEY_Q, VietnameseData.KEY_V).contains(keyCode) || (keyCode == KEY_D && CHR(0) != KEY_D)
                    val hasNonEnteringMark = vRange.any { (typingWord[it] and (MARK2_MASK or MARK3_MASK or MARK4_MASK)) != 0u }
                    val isIncompatibleStopCoda = setOf(KEY_C, KEY_P, KEY_T).contains(keyCode) && hasNonEnteringMark
                    if (isImpossibleCoda || isIncompatibleStopCoda) {
                        restoreWordFromRawKeys(oldScreenLength = index)
                        return true
                    }
                }
            }
        }

        return false
    }

    fun syncConfig(
        language: Int? = null,
        inputType: Int? = null,
        checkSpelling: Int? = null,
        useModernOrthography: Int? = null,
        restoreIfWrongSpelling: Int? = null,
        fixRecommendBrowser: Int? = null,
        useSmartSwitchKey: Int? = null
    ) {
        lock.withLock {
            if (language != null) vLanguage = language
            if (inputType != null) vInputType = inputType
            if (checkSpelling != null) {
                vCheckSpelling = checkSpelling
                useSpellCheckingBefore = checkSpelling
            }
            if (useModernOrthography != null) vUseModernOrthography = useModernOrthography
            if (restoreIfWrongSpelling != null) vRestoreIfWrongSpelling = restoreIfWrongSpelling
            if (fixRecommendBrowser != null) vFixRecommendBrowser = fixRecommendBrowser
            if (useSmartSwitchKey != null) vUseSmartSwitchKey = useSmartSwitchKey
        }
    }

    fun handleKeyEvent(keyCode: UShort, capsStatus: Int, isOtherControlKey: Boolean = false): HookSnapshot {
        val awEngine = AutoWordingEngine.shared
        val needsContext = awEngine.isEnabled && (awEngine.shorthandExpansionEnabled || awEngine.candidateSuggestionsEnabled)

        val preContext = if (needsContext) lock.withLock { getWordContextUnlocked(keyCode) } else OKWordContext()
        val intercept = if (needsContext) awEngine.interceptBeforeKeyEvent(keyCode, preContext) else null
        if (intercept != null) {
            lock.withLock { commitExternalExpansion() }
            return intercept
        }

        val snapshot = lock.withLock {
            hookOutput.code = HookCodeState.DO_NOTHING
            hookOutput.backspaceCount = 0
            hookOutput.newCharCount = 0
            hookOutput.extCode = 0
            hookOutput.charData[0] = 0u
            hookOutput.languageSwitchRequest = -1

            val isAlphaKey = !VietnameseData.isNumberKey(keyCode) && !charKeyCode.contains(keyCode)
            isCaps = (capsStatus == 1) || (capsStatus == 2 && isAlphaKey)

            val isWordBreakCondition = (VietnameseData.isNumberKey(keyCode) && capsStatus == 1) ||
                    isOtherControlKey ||
                    isWordBreak(false, keyCode) ||
                    (index == 0 && VietnameseData.isNumberKey(keyCode))

            if (isWordBreakCondition) {
                handleWordBreakOrControlKey(keyCode, capsStatus)
            } else if (keyCode == KEY_SPACE) {
                handleSpaceKey()
            } else if (keyCode == KEY_DELETE) {
                handleDeleteKey()
            } else {
                handleCharacterKey(keyCode)
            }

            buildOutputSnapshot(keyCode)
        }

        if (needsContext) {
            val postContext = lock.withLock { getWordContextUnlocked(keyCode) }
            awEngine.didProcessKeyEvent(keyCode, snapshot, preContext, postContext)
        }
        return snapshot
    }

    fun handleKeyEventCommitFirst(
        keyCode: UShort,
        capsStatus: Int = 0,
        isOtherControlKey: Boolean = false,
        commitCallback: (HookSnapshot) -> Unit
    ): HookSnapshot {
        val awEngine = AutoWordingEngine.shared
        val needsContext = awEngine.isEnabled && (awEngine.shorthandExpansionEnabled || awEngine.candidateSuggestionsEnabled)

        val preContext = if (needsContext) lock.withLock { getWordContextUnlocked(keyCode) } else OKWordContext()
        val intercept = if (needsContext) awEngine.interceptBeforeKeyEvent(keyCode, preContext) else null
        if (intercept != null) {
            lock.withLock { commitExternalExpansion() }
            commitCallback(intercept)
            return intercept
        }

        val snapshot = lock.withLock {
            hookOutput.code = HookCodeState.DO_NOTHING
            hookOutput.backspaceCount = 0
            hookOutput.newCharCount = 0
            hookOutput.extCode = 0
            hookOutput.charData[0] = 0u
            hookOutput.languageSwitchRequest = -1

            val isAlphaKey = !VietnameseData.isNumberKey(keyCode) && !charKeyCode.contains(keyCode)
            isCaps = (capsStatus == 1) || (capsStatus == 2 && isAlphaKey)

            val isWordBreakCondition = (VietnameseData.isNumberKey(keyCode) && capsStatus == 1) ||
                    isOtherControlKey ||
                    isWordBreak(false, keyCode) ||
                    (index == 0 && VietnameseData.isNumberKey(keyCode))

            if (isWordBreakCondition) {
                handleWordBreakOrControlKey(keyCode, capsStatus)
            } else if (keyCode == KEY_SPACE) {
                handleSpaceKey()
            } else if (keyCode == KEY_DELETE) {
                handleDeleteKey()
            } else {
                handleCharacterKey(keyCode)
            }

            buildOutputSnapshot(keyCode)
        }

        // Commit FIRST outside engine lock
        commitCallback(snapshot)

        // Then process candidates
        if (needsContext) {
            val postContext = lock.withLock { getWordContextUnlocked(keyCode) }
            awEngine.didProcessKeyEvent(keyCode, snapshot, preContext, postContext)
        }

        return snapshot
    }

    fun handleKeyEventCommitFirst(
        keyCode: Int,
        capsStatus: Int = 0,
        isOtherControlKey: Boolean = false,
        commitCallback: (HookSnapshot) -> Unit
    ): HookSnapshot {
        return handleKeyEventCommitFirst(keyCode.toUShort(), capsStatus, isOtherControlKey, commitCallback)
    }

    fun handleWordBreakOrControlKey(keyCode: UShort, capsStatus: Int) {
        sentenceEndedWithPunctuation = false
        hookOutput.code = HookCodeState.DO_NOTHING
        hookOutput.backspaceCount = 0
        hookOutput.newCharCount = 0
        hookOutput.extCode = 1 // Word break

        if (index == 0 && VietnameseData.isNumberKey(keyCode) && capsStatus == 0) {
            if (spaceCount > 0) {
                saveWord(KEY_SPACE.toUInt(), spaceCount)
                spaceCount = 0
            }
            specialChar.add(keyCode.toUInt())
            hookOutput.extCode = 3
            startNewSessionInternal()
            hasDigitPrefix = true
            vCheckSpelling = useSpellCheckingBefore
            willTempOffEngine = false
            return
        }

        val isCompoundSeparator = (keyCode == KEY_DOT || (keyCode == KEY_MINUS && capsStatus == 1) || keyCode == KEY_SLASH || (keyCode == KEY_2 && capsStatus == 1) || (keyCode == KEY_SEMICOLON && capsStatus == 1))

        if (vRestoreIfWrongSpelling != 0 && isWordBreak(false, keyCode)) {
            if (!tempDisableKey && vCheckSpelling != 0) {
                checkSpelling(forceCheckVowel = true)
            }
            if (!evaluateWordBreakRestore(breakKeyCode = keyCode, handleCode = HookCodeState.RESTORE_AND_START_NEW_SESSION)) {
                hookOutput.code = HookCodeState.DO_NOTHING
            }
        }

        isCharKeyCode = charKeyCode.contains(keyCode)
        if (!isCharKeyCode) {
            specialChar.clear()
            typingStates.clear()
            wordStartOk = true
        } else {
            if (spaceCount > 0) {
                saveWord(KEY_SPACE.toUInt(), spaceCount)
                spaceCount = 0
            } else {
                saveWord()
            }
            specialChar.add(keyCode.toUInt() or (if (isCaps) CAPS_MASK else 0u))
            hookOutput.extCode = 3
            if (isCompoundSeparator) {
                wordStartOk = false
            } else {
                wordStartOk = true
            }
        }

        if (hookOutput.code == HookCodeState.DO_NOTHING) {
            startNewSessionInternal()
            vCheckSpelling = useSpellCheckingBefore
            willTempOffEngine = false
        }
    }

    fun handleSpaceKey() {
        if (spaceCount == 0) {
            if (!tempDisableKey && vCheckSpelling != 0) {
                checkSpelling(forceCheckVowel = true)
            }
            if (vRestoreIfWrongSpelling != 0) {
                if (!evaluateWordBreakRestore(breakKeyCode = KEY_SPACE, handleCode = HookCodeState.RESTORE)) {
                    hookOutput.code = HookCodeState.DO_NOTHING
                }
                spaceCount++
            } else {
                hookOutput.code = HookCodeState.DO_NOTHING
                spaceCount++
            }
        } else {
            hookOutput.code = HookCodeState.DO_NOTHING
            spaceCount++
        }

        if (spaceCount == 1) {
            if (specialChar.isNotEmpty()) {
                val lastSpecial = specialChar.last()
                val baseKey = (lastSpecial and CHAR_MASK).toUShort()
                val isCapsKey = (lastSpecial and CAPS_MASK) != 0u
                if (baseKey == KEY_DOT || (baseKey == KEY_1 && isCapsKey) || (baseKey == KEY_SLASH && isCapsKey)) {
                    sentenceEndedWithPunctuation = true
                }
                saveSpecialChar()
            } else {
                saveWord()
            }
        }
        wordStartOk = true
        hasDigitPrefix = false
        vCheckSpelling = useSpellCheckingBefore
        willTempOffEngine = false
    }

    fun handleDeleteKey() {
        hookOutput.code = HookCodeState.DO_NOTHING
        hookOutput.extCode = 2 // Delete
        if (specialChar.isNotEmpty()) {
            specialChar.removeLast()
            if (specialChar.isEmpty()) {
                restoreLastTypingState()
            }
        } else if (spaceCount > 0) {
            spaceCount--
            if (spaceCount == 0) {
                restoreLastTypingState()
            }
        } else {
            if (stateIndex > 0) stateIndex--
            if (rawStateIndex > 0) rawStateIndex--
            if (index > 0) {
                index--
                typingWord[index] = KEY_EMPTY.toUInt()
                val currentIdx = index
                droppedKeys.removeAll { it.second >= currentIdx }
                if (longWordHelper.isNotEmpty()) {
                    for (i in (maxBuff - 1) downTo 1) {
                        typingWord[i] = typingWord[i - 1]
                    }
                    typingWord[0] = longWordHelper.removeLast()
                    index++
                }
                if (vCheckSpelling != 0) {
                    checkSpelling()
                }
                val anyRemainingModified = (0 until index).any { (typingWord[it] and (MARK_MASK or TONE_MASK or TONEW_MASK)) != 0u }
                if (!anyRemainingModified) {
                    stateIndex = index
                    rawStateIndex = index
                    droppedKeys.clear()
                    for (i in 0 until index) {
                        keyStates[i] = typingWord[i]
                        rawKeyStates[i] = typingWord[i]
                    }
                    for (i in index until maxBuff) {
                        keyStates[i] = KEY_EMPTY.toUInt()
                    }
                    for (i in index until rawKeyStates.size) {
                        rawKeyStates[i] = KEY_EMPTY.toUInt()
                    }
                }
            }

            hookOutput.backspaceCount = 0
            hookOutput.newCharCount = 0
            hookOutput.extCode = 2
            if (index == 0) {
                stateIndex = 0
                rawStateIndex = 0
                droppedKeys.clear()
                longWordHelper.clear()
                startNewSessionInternal()
                specialChar.clear()
                restoreLastTypingState()
            } else {
                checkGrammar(deltaBackSpace = 1)
            }
        }
    }

    fun handleCharacterKey(keyCode: UShort) {
        if (willTempOffEngine) {
            hookOutput.code = HookCodeState.DO_NOTHING
            hookOutput.extCode = 3
            return
        }

        if (spaceCount > 0) {
            hookOutput.backspaceCount = 0
            hookOutput.newCharCount = 0
            hookOutput.extCode = 0
            startNewSessionInternal()
            saveWord(KEY_SPACE.toUInt(), spaceCount)
            spaceCount = 0
        } else if (specialChar.isNotEmpty()) {
            val preserveDigitPrefix = hasDigitPrefix
            saveSpecialChar()
            hasDigitPrefix = preserveDigitPrefix
        }

        insertState(keyCode, isCaps)

        if (hasDigitPrefix && index == 0 && keyCode == KEY_X) {
            tempDisableKey = true
        } else if (vInputType == 0 && index > 0 && VietnameseData.isNumberKey(keyCode)) {
            tempDisableKey = true
        }

        val nonInitialUFollowers = setOf(
            KEY_E, KEY_I, KEY_H, KEY_Y, KEY_L, VietnameseData.KEY_B,
            KEY_D, KEY_G, KEY_K, KEY_Q, VietnameseData.KEY_V
        )
        if (vInputType == 0 && index == 1 && CHR(0) == KEY_U && typingWord[0].isStandaloneW && nonInitialUFollowers.contains(keyCode)) {
            val wCaps = typingWord[0].isCaps
            typingWord[0] = KEY_W.toUInt() or (if (wCaps) CAPS_MASK else 0u)
            keyStates[0] = typingWord[0]
            rawKeyStates[0] = typingWord[0]
            tempDisableKey = true
            insertKey(keyCode, isCaps, isCheckSpellingFlag = false)
            hookOutput.code = HookCodeState.WILL_PROCESS
            hookOutput.backspaceCount = 1
            hookOutput.newCharCount = 2
            hookOutput.extCode = 3
            hookOutput.charData[1] = GET(typingWord[0])
            hookOutput.charData[0] = GET(typingWord[1])
            return
        }

        if (checkLiveMidWordAutoEscape(keyCode)) return

        // REQ-027, ADR-027: Escape Keystroke Consumption
        if (vInputType == 0 && index > 0 && keyCode == KEY_I && CHR(index - 1) == KEY_I) {
            val curComposed = getComposedWordInternal().lowercase()
            if (VietnameseDictionary.isValidVietnameseSyllable(curComposed)) {
                hookOutput.code = HookCodeState.WILL_PROCESS
                hookOutput.backspaceCount = 0
                hookOutput.newCharCount = 0
                hookOutput.extCode = 3
                return
            }
        }

        if (!isSpecialKey(keyCode) || tempDisableKey) {
            hookOutput.code = HookCodeState.DO_NOTHING
            hookOutput.backspaceCount = 0
            hookOutput.newCharCount = 0
            hookOutput.extCode = 3
            insertKey(keyCode, isCaps)
        } else {
            hookOutput.code = HookCodeState.DO_NOTHING
            hookOutput.extCode = 3
            handleMainKey(keyCode, isCaps)
        }

        if (!isKeyD(keyCode)) {
            if (hookOutput.code == HookCodeState.DO_NOTHING || (hookOutput.extCode == 4 && hookOutput.backspaceCount == 0 && hookOutput.newCharCount == 1)) {
                checkGrammar(deltaBackSpace = -1)
            } else {
                checkGrammar(deltaBackSpace = 0)
            }
        }

        if (hookOutput.code == HookCodeState.RESTORE) {
            val swallowedEntry = keyCode.toUInt() or (if (isCaps) CAPS_MASK else 0u)
            droppedKeys.add(Pair(swallowedEntry, index))
            insertKey(keyCode, isCaps)
            if (stateIndex > 0) stateIndex--
        }

        if (isBracketKey(keyCode) && (hookOutput.code == HookCodeState.DO_NOTHING || isBracketKey((hookOutput.charData[0] and CHAR_MASK).toUShort()))) {
            val deduction = if (hookOutput.code == HookCodeState.WILL_PROCESS) hookOutput.backspaceCount else 0
            if (index - deduction > 0) {
                index--
                saveWord()
            }
            index = 0
            tempDisableKey = false
            stateIndex = 0
            rawStateIndex = 0
            droppedKeys.clear()
            hookOutput.extCode = 3
            specialChar.add(keyCode.toUInt() or (if (isCaps) CAPS_MASK else 0u))
        }
    }

    fun evaluateWordBreakRestore(breakKeyCode: UShort, handleCode: Int): Boolean {
        return BilingualAutoSwitchEngine.evaluateWordBreakRestore(this, breakKeyCode, handleCode)
    }

    fun buildOutputSnapshot(forKeyCode: UShort): HookSnapshot {
        if (hookOutput.code == HookCodeState.DO_NOTHING) {
            return HookSnapshot(
                code = HookCodeState.DO_NOTHING,
                backspaceCount = 0,
                newCharCount = 0,
                extCode = hookOutput.extCode,
                shouldSendControlKey = false,
                utf16Chars = emptyList(),
                languageSwitchRequest = hookOutput.languageSwitchRequest
            )
        }

        val chars = mutableListOf<UShort>()
        if (hookOutput.newCharCount > 0) {
            for (k in (hookOutput.newCharCount - 1) downTo 0) {
                val tempChar = hookOutput.charData[k]
                if ((tempChar and PURE_CHARACTER_MASK) != 0u) {
                    chars.add((tempChar and 0xFFFFu).toUShort())
                } else if ((tempChar and CHAR_CODE_MASK) == 0u) {
                    chars.add(VietnameseData.keyCodeToCharacter(tempChar))
                } else {
                    chars.add((tempChar and 0xFFFFu).toUShort())
                }
            }
        }

        var shouldSendControl = false
        if (hookOutput.code == HookCodeState.RESTORE || hookOutput.code == HookCodeState.RESTORE_AND_START_NEW_SESSION) {
            val charVal = VietnameseData.keyCodeToCharacter(forKeyCode.toUInt() or (if (isCaps) CAPS_MASK else 0u))
            if (charVal != 0.toUShort()) {
                chars.add(charVal)
            } else {
                shouldSendControl = true
            }
            if (hookOutput.code == HookCodeState.RESTORE_AND_START_NEW_SESSION) {
                startNewSessionInternal()
            }
        }

        return HookSnapshot(
            code = hookOutput.code,
            backspaceCount = hookOutput.backspaceCount,
            newCharCount = chars.size,
            extCode = hookOutput.extCode,
            shouldSendControlKey = shouldSendControl,
            utf16Chars = chars,
            languageSwitchRequest = hookOutput.languageSwitchRequest
        )
    }
}

object VietnameseEngine {
    fun initEngine() {
        VietnameseEngineState.shared.initEngine()
    }

    fun syncConfig(
        language: Int? = null,
        inputType: Int? = null,
        checkSpelling: Int? = null,
        useModernOrthography: Int? = null,
        restoreIfWrongSpelling: Int? = null,
        fixRecommendBrowser: Int? = null,
        useSmartSwitchKey: Int? = null
    ) {
        VietnameseEngineState.shared.syncConfig(
            language, inputType, checkSpelling, useModernOrthography,
            restoreIfWrongSpelling, fixRecommendBrowser, useSmartSwitchKey
        )
    }

    fun resetSession(): Boolean {
        return VietnameseEngineState.shared.resetSession()
    }

    fun handleKeyEvent(keyCode: Int, capsStatus: Int = 0, otherControlKey: Boolean = false): HookSnapshot {
        return VietnameseEngineState.shared.handleKeyEvent(keyCode.toUShort(), capsStatus, otherControlKey)
    }

    fun handleKeyEventCommitFirst(
        keyCode: Int,
        capsStatus: Int = 0,
        isOtherControlKey: Boolean = false,
        commitCallback: (HookSnapshot) -> Unit
    ): HookSnapshot {
        return VietnameseEngineState.shared.handleKeyEventCommitFirst(
            keyCode.toUShort(),
            capsStatus,
            isOtherControlKey,
            commitCallback
        )
    }

    fun getComposedWord(): String {
        return VietnameseEngineState.shared.getComposedWord()
    }

    fun getWordContext(keyCode: Int? = null): OKWordContext {
        return VietnameseEngineState.shared.getWordContext(keyCode?.toUShort())
    }

    fun effectiveLanguagePriority(): LanguagePriority {
        return VietnameseEngineState.shared.effectiveLanguagePriority()
    }

    fun reconstructedRawKeys(): List<UInt> {
        return VietnameseEngineState.shared.reconstructedRawKeys()
    }

    fun lastPreviousWord(): String? = VietnameseEngineState.shared.lock.withLock {
        VietnameseEngineState.shared.previousWords.lastOrNull()?.composedString
    }
}
