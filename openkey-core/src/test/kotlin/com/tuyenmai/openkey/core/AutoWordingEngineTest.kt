package com.tuyenmai.openkey.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AutoWordingEngineTest {

    @BeforeEach
    fun setUp() {
        VietnameseEngine.initEngine()
        VietnameseEngine.resetSession()
        VietnameseEngine.syncConfig(
            language = 1,
            inputType = 0,
            checkSpelling = 1,
            useModernOrthography = 1,
            restoreIfWrongSpelling = 1,
            fixRecommendBrowser = 1,
            useSmartSwitchKey = 1
        )
        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = true,
            shorthandExpansion = true,
            candidateSuggestions = true,
            doubleSpacePeriod = false
        )
        AutoWordingEngine.shared.customShorthands = mutableMapOf()
        AutoWordingEngine.shared.resetLearnedFrequencies()
        AutoWordingEngine.shared.clearCandidates()
    }

    class VirtualScreen(var text: String = "") {
        init {
            VietnameseEngine.resetSession()
            AutoWordingEngine.shared.clearCandidates()
        }

        fun typeASCII(s: String): List<HookSnapshot> {
            val snapshots = mutableListOf<HookSnapshot>()
            for (ch in s) {
                snapshots.add(typeChar(ch))
            }
            return snapshots
        }

        fun typeKey(keyCode: Int, caps: Int = 0, isOtherControl: Boolean = false): HookSnapshot {
            val snap = VietnameseEngine.handleKeyEvent(keyCode, caps, isOtherControl)
            applySnapshot(snap, keyCode, caps)
            return snap
        }

        fun typeChar(ch: Char): HookSnapshot {
            val mapping = VietnameseData.charToKeyCode(ch)
            val keyCode = mapping.first
            val caps = mapping.second
            return typeKey(keyCode, caps, false)
        }

        private fun applySnapshot(snap: HookSnapshot, keyCode: Int, caps: Int, fallbackChar: Char? = null) {
            if (snap.code == HookCodeState.WILL_PROCESS ||
                snap.code == HookCodeState.RESTORE ||
                snap.code == HookCodeState.RESTORE_AND_START_NEW_SESSION
            ) {
                val bs = snap.backspaceCount
                if (bs > 0) {
                    val toRemove = bs.coerceAtMost(text.length)
                    text = text.dropLast(toRemove)
                }
                text += snap.decodedString
            } else {
                if (keyCode == VietnameseData.KEY_DELETE.toInt()) {
                    if (text.isNotEmpty()) {
                        text = text.dropLast(1)
                    }
                } else if (fallbackChar != null) {
                    text += fallbackChar
                } else {
                    val c = VietnameseData.keyCodeToCharacter(
                        keyCode.toUInt() or (if (caps != 0) VietnameseData.CAPS_MASK else 0u)
                    )
                    if (c != 0.toUShort()) {
                        text += c.toInt().toChar()
                    }
                }
            }
        }
    }

    // 1. Live candidate generation (.CORRECTION, .STEM_COMPLETION, .NEXT_WORD) after every keystroke including DO_NOTHING and KEY_DELETE (REQ-004)
    @Test
    fun testLiveCandidateGenerationAndNextWord() {
        val screen = VirtualScreen()

        // Typing "thanh" populates stem completions ("thành", "thánh")
        screen.typeASCII("thanh")
        val cands = AutoWordingEngine.shared.activeCandidates
        assertTrue(cands.isNotEmpty(), "Candidates must not be empty after typing 'thanh'")
        val candTexts = cands.map { it.text }
        assertTrue(candTexts.contains("thành"), "Candidates must include 'thành', got $candTexts")
        assertTrue(candTexts.contains("thánh"), "Candidates must include 'thánh', got $candTexts")
        assertTrue(
            cands.any { it.kind == CandidateKind.STEM_COMPLETION },
            "Candidates should have STEM_COMPLETION kind"
        )

        // Typing Space after "thành" or word break populates NEXT_WORD continuations
        val screenComp = VirtualScreen()
        screenComp.typeASCII("thanhf") // "thành"
        screenComp.typeChar(' ')
        val nextCands = AutoWordingEngine.shared.activeCandidates
        assertTrue(nextCands.isNotEmpty(), "Next-word candidates must be populated after Space on 'thành '")
        assertTrue(
            nextCands.all { it.kind == CandidateKind.NEXT_WORD },
            "Candidates after space must be of kind NEXT_WORD"
        )
        val nextTexts = nextCands.map { it.text }
        assertTrue(
            nextTexts.contains("phố") || nextTexts.contains("công"),
            "Next-word candidates for 'thành' should include 'phố' or 'công', got $nextTexts"
        )

        // Test delete: typing "thanh", then KEY_DELETE updates candidates for "than"
        val screenDel = VirtualScreen()
        screenDel.typeASCII("thanh")
        screenDel.typeKey(VietnameseData.KEY_DELETE.toInt())
        val delCands = AutoWordingEngine.shared.activeCandidates
        assertTrue(delCands.isNotEmpty(), "Candidates must remain active after KEY_DELETE")
    }

    // 2. Diacritic-preserving stem ranking (REQ-004)
    @Test
    fun testDiacriticPreservingStemRanking() {
        // Typing "giơi" (contains vowel modifier ơ) ranks "giới" #1
        val candsGioi1 = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "gioi",
            currentSyllable = "giơi",
            isWrongSpelling = false
        )
        assertTrue(candsGioi1.isNotEmpty())
        assertEquals("giới", candsGioi1.first().text, "'giơi' must rank 'giới' #1 preserving horn on ơ")

        // Typing "gioi" includes both "giới" and "giỏi"
        val candsGioi2 = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "gioi",
            currentSyllable = "gioi",
            isWrongSpelling = false
        )
        val texts = candsGioi2.map { it.text }
        assertTrue(texts.contains("giới"), "'gioi' candidates must include 'giới', got $texts")
        assertTrue(texts.contains("giỏi"), "'gioi' candidates must include 'giỏi', got $texts")
    }

    // 3. User acceptance frequency learning (REQ-004)
    @Test
    fun testUserAcceptanceFrequencyLearning() {
        // Initially, "thánh" is #2 in stem "thanh" (after "thành")
        val initialCands = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "thanh",
            currentSyllable = "thanh",
            isWrongSpelling = false
        )
        assertEquals("thành", initialCands[0].text)

        // Record candidate acceptance for "thánh" multiple times
        AutoWordingEngine.shared.recordCandidateAcceptance("thánh")
        AutoWordingEngine.shared.recordCandidateAcceptance("thánh")

        // Now "thánh" should be promoted to #1
        val promotedCands = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "thanh",
            currentSyllable = "thanh",
            isWrongSpelling = false
        )
        assertEquals("thánh", promotedCands[0].text, "Accepted candidate 'thánh' must be promoted to #1")

        // Reset learned frequencies
        AutoWordingEngine.shared.resetLearnedFrequencies()
        val resetCands = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "thanh",
            currentSyllable = "thanh",
            isWrongSpelling = false
        )
        assertEquals("thành", resetCands[0].text, "After reset, default dictionary ranking must be restored")
    }

    // 4. Phonotactic cluster repair (tineg -> "tiếng") when grammarRepairEnabled == true (REQ-004)
    @Test
    fun testPhonotacticClusterRepair() {
        AutoWordingEngine.shared.grammarRepairEnabled = true

        val cands = AutoWordingEngine.shared.computeCurrentWordCandidates(
            rawStem = "tineg",
            currentSyllable = "tineg",
            isWrongSpelling = false
        )
        assertTrue(cands.isNotEmpty(), "Transposed 'tineg' must yield repair candidates")
        assertEquals("tiếng", cands[0].text, "'tineg' must repair to 'tiếng' as #1 candidate")
    }

    // 5. Custom-only shorthand expansion on Space (REQ-004)
    @Test
    fun testCustomOnlyShorthandExpansion() {
        AutoWordingEngine.shared.shorthandExpansionEnabled = true
        AutoWordingEngine.shared.customShorthands = mutableMapOf("cty" to "công ty")

        val screen = VirtualScreen()
        screen.typeASCII("cty ")
        assertEquals("công ty ", screen.text, "Typing 'cty ' must expand to 'công ty '")

        // Built-in teencode like "ko " or "dc " must NOT expand when customShorthands is empty
        AutoWordingEngine.shared.customShorthands = mutableMapOf()
        val screenKo = VirtualScreen()
        screenKo.typeASCII("ko ")
        assertEquals("ko ", screenKo.text, "Built-in teencode 'ko ' must NOT expand when customShorthands is empty")

        val screenDc = VirtualScreen()
        screenDc.typeASCII("dc ")
        assertEquals("dc ", screenDc.text, "Built-in teencode 'dc ' must NOT expand when customShorthands is empty")
    }

    // 6. Context-Ordered Homograph Disambiguation Candidate Ranking (REQ-018, ADR-019)
    @Test
    fun testHomographDisambiguationCandidateRanking() {
        val engine = AutoWordingEngine.shared

        // In VIETNAMESE_FIRST priority:
        // "sẽ" must rank first, and "sex" must be included in candidates
        val vnCandidates = engine.computeCandidates(
            currentSyllable = "sẽ",
            rawStem = "sex",
            previousWord = null,
            languagePriority = LanguagePriority.VIETNAMESE_FIRST
        )
        assertTrue(vnCandidates.isNotEmpty(), "Candidates must not be empty")
        assertEquals("sẽ", vnCandidates[0].text, "VIETNAMESE_FIRST must rank 'sẽ' first")
        assertTrue(vnCandidates.any { it.text == "sex" }, "VIETNAMESE_FIRST must include 'sex' candidate")

        // In ENGLISH_FIRST priority:
        // "sex" must rank first, and "sẽ" must rank second
        val engCandidates = engine.computeCandidates(
            currentSyllable = "sẽ",
            rawStem = "sex",
            previousWord = null,
            languagePriority = LanguagePriority.ENGLISH_FIRST
        )
        assertTrue(engCandidates.size >= 2, "Candidates must have at least 2 entries")
        assertEquals("sex", engCandidates[0].text, "ENGLISH_FIRST must rank 'sex' first")
        assertEquals("sẽ", engCandidates[1].text, "ENGLISH_FIRST must rank 'sẽ' second")
    }

    // 7. English mode live 3-chip predictions from 1st character and bigrams (REQ-024, AC-024.1)
    @Test
    fun testEnglishModeLiveThreeChipPredictionsAndCandidateSelection() {
        class MockCommitter : TextCommitterTarget {
            var committedText = ""
            var deletedBefore = 0
            override fun beginBatchEdit(): Boolean = true
            override fun endBatchEdit(): Boolean = true
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                committedText += text.toString()
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                deletedBefore += beforeLength
                if (committedText.length >= beforeLength) {
                    committedText = committedText.dropLast(beforeLength)
                }
                return true
            }
            override fun sendKeyChar(char: Char): Boolean {
                committedText += char
                return true
            }
            override fun performEditorAction(actionId: Int): Boolean = true
            override fun moveCursorBy(deltaChars: Int): Boolean = true
            override fun deletePreviousWord(): Boolean = true
        }

        val committer = MockCommitter()
        val controller = KeyboardSessionController(committer = committer)
        val textCtx = EditorContext(
            inputType = InputContextEvaluator.TYPE_CLASS_TEXT,
            imeOptions = InputContextEvaluator.IME_ACTION_DONE,
            packageName = "com.test.app"
        )
        controller.onStartInput(textCtx)
        controller.onKeyAction(KeyAction.SwitchLanguage) // switch to English (language = 0)
        assertEquals(0, controller.editorUiState.language)

        // Type "t" -> 3 predictions starting with "t", e.g. ["the", "to", "that"]
        controller.onKeyAction(KeyAction.CommitChar("t"))
        val tCands = controller.autoWordingEngine.activeCandidates
        assertEquals(3, tCands.size, "Typing 't' must yield 3 candidates")
        assertTrue(tCands.all { it.text.lowercase().startsWith("t") }, "All candidates must start with 't'")

        // Backspace to reset
        controller.onKeyAction(KeyAction.Backspace)
        assertTrue(controller.autoWordingEngine.activeCandidates.isEmpty())

        // Type "i" -> includes "I"
        controller.onKeyAction(KeyAction.CommitChar("i"))
        val iCands = controller.autoWordingEngine.activeCandidates
        assertEquals(3, iCands.size)
        assertTrue(iCands.any { it.text == "I" || it.text == "in" || it.text == "is" })

        // Type "m" -> "im" -> includes "I'm"
        controller.onKeyAction(KeyAction.CommitChar("m"))
        val imCands = controller.autoWordingEngine.activeCandidates
        assertEquals(3, imCands.size)
        assertTrue(imCands.any { it.text == "I'm" }, "Candidates for 'im' must include 'I'm'")

        // Backspace twice
        controller.onKeyAction(KeyAction.Backspace)
        controller.onKeyAction(KeyAction.Backspace)

        // Type "w", "o" -> 3 English candidates
        controller.onKeyAction(KeyAction.CommitChar("w"))
        controller.onKeyAction(KeyAction.CommitChar("o"))
        val woCands = controller.autoWordingEngine.activeCandidates
        assertEquals(3, woCands.size, "Candidates for 'wo' must be 3 English candidates")
        assertTrue(woCands.all { it.text.lowercase().startsWith("wo") })

        // Clear
        controller.onKeyAction(KeyAction.Backspace)
        controller.onKeyAction(KeyAction.Backspace)

        // Type "andro" -> includes "android"
        for (c in "andro") {
            controller.onKeyAction(KeyAction.CommitChar(c.toString()))
        }
        val androCands = controller.autoWordingEngine.activeCandidates
        assertTrue(androCands.any { it.text.equals("android", ignoreCase = true) }, "Candidates for 'andro' must include 'android'")

        // Select "android"
        val androidIdx = androCands.indexOfFirst { it.text.equals("android", ignoreCase = true) }
        assertTrue(androidIdx >= 0)
        controller.onCandidateSelected(androidIdx)
        assertEquals("android ", committer.committedText)

        // Selecting candidate populates next-word predictions
        val nextCands = controller.autoWordingEngine.activeCandidates
        assertEquals(3, nextCands.size, "Next word candidates for 'android' must be 3")
    }

    // 8. SpellCheckerBridge hook merges external suggestions (REQ-024, AC-024.3)
    @Test
    fun testExternalSpellCheckerBridgeHook() {
        val engine = AutoWordingEngine.shared
        val mockBridge = AutoWordingEngine.SpellCheckerBridge { word, maxCount ->
            listOf("customsuggestion1", "customsuggestion2")
        }
        engine.spellCheckerBridge = mockBridge

        // For a rare prefix with 0 or few dictionary matches
        val cands = VietnameseDictionary.shared.computeEnglishCandidates("xyz", 3)
        // If computeEnglishCandidates or AutoWordingEngine merges spellCheckerBridge
        val merged = engine.computeCurrentWordCandidates("xyz", "xyz", false, LanguagePriority.ENGLISH_FIRST)
        assertTrue(merged.any { it.text.startsWith("customsuggestion") }, "SpellCheckerBridge suggestions must be merged")

        engine.spellCheckerBridge = null
    }

    // 9. REQ-026, AC-026.3: Live spatial typo candidate ranking
    @Test
    fun testLiveVietnameseSpatialTypoCandidates() {
        val engine = AutoWordingEngine.shared

        // nfhiax -> nghĩa
        val nfhiaxCands = engine.computeCurrentWordCandidates("nfhiax", "nfhĩa", false, LanguagePriority.VIETNAMESE_FIRST)
        assertTrue(nfhiaxCands.isNotEmpty(), "Candidates for 'nfhiax' must not be empty")
        assertEquals("nghĩa", nfhiaxCands[0].text, "Slot 1 candidate for 'nfhiax' must be 'nghĩa'")
        assertEquals(CandidateKind.CORRECTION, nfhiaxCands[0].kind)

        // kgoong -> không
        val kgoongCands = engine.computeCurrentWordCandidates("kgoong", "kgoong", false, LanguagePriority.VIETNAMESE_FIRST)
        assertTrue(kgoongCands.isNotEmpty(), "Candidates for 'kgoong' must not be empty")
        assertEquals("không", kgoongCands[0].text, "Slot 1 candidate for 'kgoong' must be 'không'")
        assertEquals(CandidateKind.CORRECTION, kgoongCands[0].kind)

        // tiewngs -> tiếng
        val tiewngsCands = engine.computeCurrentWordCandidates("tiewngs", "tiewngs", false, LanguagePriority.VIETNAMESE_FIRST)
        assertTrue(tiewngsCands.isNotEmpty(), "Candidates for 'tiewngs' must not be empty")
        assertEquals("tiếng", tiewngsCands[0].text, "Slot 1 candidate for 'tiewngs' must be 'tiếng'")
        assertEquals(CandidateKind.CORRECTION, tiewngsCands[0].kind)

        // nguoif -> người
        val nguoifCands = engine.computeCurrentWordCandidates("nguoif", "nguòi", false, LanguagePriority.VIETNAMESE_FIRST)
        assertTrue(nguoifCands.isNotEmpty(), "Candidates for 'nguoif' must not be empty")
        assertEquals("người", nguoifCands[0].text, "Slot 1 candidate for 'nguoif' must be 'người'")
        assertEquals(CandidateKind.CORRECTION, nguoifCands[0].kind)
    }
}
