package com.tuyenmai.openkey.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BilingualAutoSwitchTest {

    @BeforeEach
    fun setUp() {
        VietnameseEngine.initEngine()
        VietnameseEngine.resetSession()
        VietnameseEngine.syncConfig(
            language = 1,
            inputType = 0, // Telex
            checkSpelling = 1,
            useModernOrthography = 1,
            restoreIfWrongSpelling = 1,
            fixRecommendBrowser = 1,
            useSmartSwitchKey = 1,
            bilingualAutoRestore = 1
        )
    }

    class VirtualScreen(var text: String = "") {
        init {
            VietnameseEngine.resetSession()
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
            val snap = VietnameseEngine.handleKeyEvent(keyCode, caps, false)
            applySnapshot(snap, keyCode, caps, ch)
            return snap
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

    // 1. droppedKeys reconstruction for English words with doubled Telex modifier letters (REQ-003)
    @Test
    fun testDroppedKeysReconstructionForDoubledTelexEnglishWords() {
        val doubledModifierWords = listOf(
            "cheese ", "seemed ", "needed ", "deepseek ", "google ", "choose ",
            "process ", "success ", "message ", "issue ", "office ", "coffee ",
            "error ", "array ", "current ", "correct ", "tomorrow ", "sorry ",
            "address ", "middle ", "reddit "
        )

        for (word in doubledModifierWords) {
            val screen = VirtualScreen()
            screen.typeASCII(word)
            assertEquals(word, screen.text, "Typing '$word' must restore raw English word without Telex corruption")
        }
    }

    // 2. Doubled-tone escape repair while preserving true double-letter English words (REQ-003)
    @Test
    fun testDoubledToneEscapeRepairAndDoubleLetterPreservation() {
        val repairCases = listOf(
            "mixx " to "mix ",
            "fixx " to "fix ",
            "boxx " to "box "
        )
        for ((input, expected) in repairCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Escape sequence '$input' must collapse to '$expected'")
        }

        val trueDoubleLetterWords = listOf(
            "boss ", "class ", "pass "
        )
        for (word in trueDoubleLetterWords) {
            val screen = VirtualScreen()
            screen.typeASCII(word)
            assertEquals(word, screen.text, "True double-letter English word '$word' must retain both letters")
        }
    }

    // 3. Standalone English/developer collision words restored in mixed sentences while genuine Vietnamese words remain (REQ-003)
    @Test
    fun testStandaloneEnglishCollisionsAndMixedSentences() {
        val collisionWords = listOf(
            "test ", "list ", "this ", "best ", "more ", "core ", "view ", "show ", "flow ",
            "user ", "vars ", "chars ", "docs ", "work ", "word ", "tree ", "free ", "see ",
            "too ", "add ", "mix ", "fix ", "box "
        )
        for (w in collisionWords) {
            val screen = VirtualScreen()
            screen.typeASCII(w)
            assertEquals(w, screen.text, "Collision word '$w' must restore to English on word break")
        }

        // Mixed sentences
        val screenSentence1 = VirtualScreen()
        screenSentence1.typeASCII("anh đang test cái list này ")
        assertEquals("anh đang test cái list này ", screenSentence1.text)

        val screenSentence2 = VirtualScreen()
        screenSentence2.typeASCII("mình dùng deepseek và google ở office ")
        assertEquals("mình dùng deepseek và google ở office ", screenSentence2.text)

        // Genuine Vietnamese words must NOT be restored to English
        val vietnameseWords = listOf(
            "có ", "bạn ", "anh ", "ăn ", "ở ", "ạ "
        )
        for (vw in vietnameseWords) {
            val screenVn = VirtualScreen()
            screenVn.typeASCII(vw)
            assertEquals(vw, screenVn.text, "Genuine Vietnamese word '$vw' must remain Vietnamese")
        }
    }

    // 4. 1-2 word English phrase look-back restoration (REQ-003)
    @Test
    fun testTwoWordLookbackRestoration() {
        val lookbackPhrases = listOf(
            "is good " to "is good ",
            "in case " to "in case ",
            "for sure " to "for sure ",
            "of course " to "of course ",
            "it is good for sure " to "it is good for sure "
        )
        for ((input, expected) in lookbackPhrases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Lookback phrase '$input' must produce '$expected'")
        }
    }

    // 5. Contractions, hyphenated w- words, and multi-capital acronyms (REQ-003)
    @Test
    fun testContractionsHyphensAndAcronyms() {
        // Contractions
        val screenDoesn = VirtualScreen()
        screenDoesn.typeASCII("doesn't ")
        assertEquals("doesn't ", screenDoesn.text, "Contraction doesn't must be preserved")

        val screenIsnt = VirtualScreen()
        screenIsnt.typeASCII("isn't ")
        assertEquals("isn't ", screenIsnt.text, "Contraction isn't must be preserved")

        // Hyphenated w- words
        val screenWifi = VirtualScreen()
        screenWifi.typeASCII("Wi-Fi ")
        assertEquals("Wi-Fi ", screenWifi.text, "Hyphenated word Wi-Fi must be preserved")

        // Multi-capital acronyms
        val screenNas = VirtualScreen()
        screenNas.typeASCII("NAS ")
        assertEquals("NAS ", screenNas.text, "Acronym NAS must be preserved")

        val screenGoPro = VirtualScreen()
        screenGoPro.typeASCII("GoPro ")
        assertEquals("GoPro ", screenGoPro.text, "Brand GoPro must be preserved")

        // Vietnamese Đ-acronyms must NEVER be restored to ASCII
        val screenVdv = VirtualScreen()
        screenVdv.typeASCII("VĐV ")
        assertEquals("VĐV ", screenVdv.text, "Vietnamese acronym VĐV must be preserved")

        val screenBds = VirtualScreen()
        screenBds.typeASCII("BĐS ")
        assertEquals("BĐS ", screenBds.text, "Vietnamese acronym BĐS must be preserved")
    }

    // 6. Context-Ordered V / E Homograph Disambiguation (REQ-018, ADR-019)
    @Test
    fun testContextOrderedHomographDisambiguation() {
        // (a) V (VIETNAMESE_FIRST) context preserves composed Vietnamese words
        val vietnameseFirstCases = listOf(
            "sex " to "sẽ ",
            "anh sex lamf " to "anh sẽ làm ",
            "max nguoonf " to "mã nguồn ",
            "bacs six " to "bác sĩ ",
            "banr vex " to "bản vẽ "
        )
        for ((input, expected) in vietnameseFirstCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "VIETNAMESE_FIRST context for '$input' must produce '$expected'")
        }

        // (b) E (ENGLISH_FIRST) context restores English words
        val englishFirstCases = listOf(
            "have sex " to "have sex ",
            "at max " to "at max ",
            "number six " to "number six ",
            "income tax " to "income tax ",
            "full moon " to "full moon "
        )
        for ((input, expected) in englishFirstCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "ENGLISH_FIRST context for '$input' must produce '$expected'")
        }

        // (c) 2-word look-back restores English homographs
        val lookbackCases = listOf(
            "sex is " to "sex is ",
            "max value " to "max value ",
            "tax rate " to "tax rate "
        )
        for ((input, expected) in lookbackCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Lookback restoration for '$input' must produce '$expected'")
        }
    }

    // 7. Vietnamese syllable protection ('thể' vs 'there') and Standalone-W escape ('Why' vs 'Wwhy') (REQ-019, ADR-020)
    @Test
    fun testVietnameseSyllableProtectionAndStandaloneWEscape() {
        // (a) 'theer ' -> 'thể ', 'there ' -> 'thể ', 'cos theer ' -> 'có thể ', 'cos there ' -> 'có thể ', 'con theer ' -> 'con thể ', 'con there ' -> 'con thể ', 'hay theer ' -> 'hay thể ', 'nhuw thees ' -> 'như thế ', 'nhuw these ' -> 'như thế ', 'thees ' -> 'thế ', 'these ' -> 'thế '
        val vietnameseSyllables = listOf(
            "theer " to "thể ",
            "there " to "thể ",
            "cos theer " to "có thể ",
            "cos there " to "có thể ",
            "con theer " to "con thể ",
            "con there " to "con thể ",
            "hay theer " to "hay thể ",
            "nhuw thees " to "như thế ",
            "nhuw these " to "như thế ",
            "thees " to "thế ",
            "these " to "thế "
        )
        for ((input, expected) in vietnameseSyllables) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must produce '$expected'")
        }

        // (b) English phrases 'there is ' -> 'there is ', 'there are ' -> 'there are ', 'over there ' -> 'over there ', 'these are ' -> 'these are '
        val englishPhrases = listOf(
            "there is " to "there is ",
            "there are " to "there are ",
            "over there " to "over there ",
            "these are " to "these are "
        )
        for ((input, expected) in englishPhrases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must produce '$expected'")
        }

        // (c) Standalone-W escape and capitalization: 'Why ' -> 'Why ', 'Wwhy ' -> 'Why ', 'why ' -> 'why ', 'wwhy ' -> 'why ', and with AutoWordingEngine.shared.syncConfig(isEnabled = true, grammarRepair = true, shorthandExpansion = true, candidateSuggestions = true, doubleSpacePeriod = true), typing 'cos there. wwhy also wrong ' -> 'có thể. Why also wrong ' and 'cos theer. Wwhy also wrong ' -> 'có thể. Why also wrong '.
        val wEscapeCases = listOf(
            "Why " to "Why ",
            "Wwhy " to "Why ",
            "why " to "why ",
            "wwhy " to "why "
        )
        for ((input, expected) in wEscapeCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must produce '$expected'")
        }

        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = true,
            shorthandExpansion = true,
            candidateSuggestions = true,
            doubleSpacePeriod = true
        )
        try {
            val screen1 = VirtualScreen()
            screen1.typeASCII("cos there. wwhy also wrong ")
            assertEquals("có thể. Why also wrong ", screen1.text)

            val screen2 = VirtualScreen()
            screen2.typeASCII("cos theer. Wwhy also wrong ")
            assertEquals("có thể. Why also wrong ", screen2.text)
        } finally {
            AutoWordingEngine.shared.syncConfig(
                isEnabled = false,
                grammarRepair = false,
                shorthandExpansion = false,
                candidateSuggestions = false,
                doubleSpacePeriod = false
            )
        }
    }

    // REQ-021, ADR-022: Fake combinatorial *oe* syllables filtered & "so goes with "
    @Test
    fun testReq021Adr022FakeOeFilteringAndGoes() {
        assertFalse(VietnameseDictionary.isValidVietnameseSyllable("goé"), "goé must be filtered out as fake combinatorial oe")
        assertFalse(VietnameseDictionary.isValidVietnameseSyllable("doé"), "doé must be filtered out as fake combinatorial oe")
        assertTrue(VietnameseDictionary.isValidVietnameseSyllable("khoẻ"), "khoẻ must remain a valid Vietnamese syllable")

        val screen = VirtualScreen()
        screen.typeASCII("so goes with ")
        assertEquals("so goes with ", screen.text, "Typing 'so goes with ' must produce 'so goes with '")
    }

    // REQ-021, ADR-022: Sentences with mid-word restoration & comma English context
    @Test
    fun testReq021Adr022EnglishSentencesAndPunctuation() {
        val s1 = VirtualScreen()
        s1.typeASCII("here is still more errors ")
        assertEquals("here is still more errors ", s1.text)

        val s2 = VirtualScreen()
        s2.typeASCII("so the python code is not needed? ")
        assertEquals("so the python code is not needed? ", s2.text)

        val s3 = VirtualScreen()
        s3.typeASCII("I think Tony is correct, ")
        assertEquals("I think Tony is correct, ", s3.text)

        val s4 = VirtualScreen()
        s4.typeASCII("@Shi @To, these ")
        assertEquals("@Shi @To, these ", s4.text)
    }

    // REQ-021, ADR-022: Morphological isEnglishWord Inflection Recognition
    @Test
    fun testReq021Adr022MorphologicalIsEnglishWord() {
        val inflections = listOf(
            "errors", "goes", "does", "files", "lines", "codes", "checks",
            "working", "updated", "deleted", "passes", "processes", "messages"
        )
        for (w in inflections) {
            assertTrue(VietnameseDictionary.isEnglishWord(w), "Morphological inflection '$w' must be recognized as English word")
        }
    }

    // REQ-021, ADR-022: Vietnamese Preservation Invariant
    @Test
    fun testReq021Adr022VietnamesePreservation() {
        val sThere = VirtualScreen()
        sThere.typeASCII("there ")
        assertEquals("thể ", sThere.text, "Under VIETNAMESE_FIRST, 'there ' must remain 'thể '")

        val sThese = VirtualScreen()
        sThese.typeASCII("these ")
        assertEquals("thế ", sThese.text, "Under VIETNAMESE_FIRST, 'these ' must remain 'thế '")

        val sOf = VirtualScreen()
        sOf.typeASCII("of ")
        assertEquals("ò ", sOf.text, "Under VIETNAMESE_FIRST, 'of ' must remain 'ò '")

        val sVnSentence = VirtualScreen()
        sVnSentence.typeASCII("lúc nhận chạy kiểm tra lỗi chính tả sai ")
        assertEquals("lúc nhận chạy kiểm tra lỗi chính tả sai ", sVnSentence.text, "Vietnamese sentence must remain completely unchanged")
    }

    // REQ-024, AC-024.2: Mixed sentence typing with Vietnamese and English fat-finger/typo auto-correction on comma and space
    @Test
    fun testVietnameseModeMixedSentenceWithTyposAndPunctuation() {
        val screen = VirtualScreen()
        val input = "thử tiếng vietek, maybe we should have an auto correct featuere, like auto correct 2 wrongly words in this sentence "
        val expected = "thử tiếng việt, maybe we should have an auto correct feature, like auto correct 2 wrongly words in this sentence "

        screen.typeASCII(input)
        assertEquals(expected, screen.text)
    }

    // REQ-024, AC-024.2: English mode auto-correct and contractions via KeyboardSessionController
    @Test
    fun testEnglishModeAutoCorrectionAndContractions() {
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
        controller.onKeyAction(KeyAction.SwitchLanguage) // English mode (language = 0)
        assertEquals(0, controller.editorUiState.language)

        fun typeSession(text: String) {
            for (ch in text) {
                if (ch == ' ') {
                    controller.onKeyAction(KeyAction.Space)
                } else {
                    controller.onKeyAction(KeyAction.CommitChar(ch.toString()))
                }
            }
        }

        typeSession("auto correct featuere,")
        assertEquals("auto correct feature,", committer.committedText)

        committer.committedText = ""
        typeSession("im ")
        assertEquals("I'm ", committer.committedText)

        committer.committedText = ""
        typeSession("dont ")
        assertEquals("don't ", committer.committedText)
    }

    // 14. Vietnamese Spatial Typo & Transposition Auto-Correction (REQ-026, ADR-027, AC-026.2)
    @Test
    fun testVietnameseSpatialTypoAndTranspositionCorrection() {
        val testCases = listOf(
            "nfhiax " to "nghĩa ",
            "nfhiax, " to "nghĩa, ",
            "ý nfhiax " to "ý nghĩa ",
            "ngjiax " to "nghĩa ",
            "gnhiax " to "nghĩa ",
            "ngfhiax " to "nghĩa ",
            "nfayf " to "ngày ",
            "nfuowif " to "người ",
            "nfuoif " to "người ",
            "kgoong " to "không ",
            "tguwr " to "thử ",
            "dsuwowcj " to "được ",
            "dsuocj " to "được ",
            "cguwa " to "chưa ",
            "pjair " to "phải ",
            "tiewngs " to "tiếng ",
            "nhueng " to "nhưng ",
            "nhuwnfx " to "những ",
            "hoafnf " to "hoàng ",
            "tiengs " to "tiếng ",
            "vietj " to "việt ",
            "nguoif " to "người ",
            "duocj " to "được ",
            "muons " to "muốn ",
            "buonf " to "buồn ",
            "truocs " to "trước "
        )

        for ((input, expected) in testCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must auto-correct to '$expected'")
        }

        // Non-regression verification: genuine English words must remain untouched
        val nonRegressions = listOf("test ", "feature ", "more ", "need ")
        for (w in nonRegressions) {
            val screen = VirtualScreen()
            screen.typeASCII(w)
            assertEquals(w, screen.text, "English word '$w' must not be corrupted")
        }
    }

    // 15. Vietnamese Corpus Phonotactic Sanitization & Pure Bucket Invariants (REQ-027, ADR-028, AC-027.1, AC-027.2)
    @Test
    fun testVietnameseCorpusSanitizationAndPureBuckets() {
        val dict = VietnameseDictionary.shared

        // AC-027.1: Every (stem, words) satisfies strip(word) == stem
        for ((stem, words) in dict.stemToWords) {
            assertTrue(
                words.all { VietnameseDictionary.shared.stripVietnameseDiacritics(it) == stem },
                "Every word in stem bucket '$stem' must have strip(word) == stem"
            )
        }
        val vietWords = dict.stemToWords["viet"] ?: emptyList()
        assertFalse(vietWords.contains("viên"), "stemToWords['viet'] must not contain 'viên'")
        val tiengWords = dict.stemToWords["tieng"] ?: emptyList()
        assertFalse(tiengWords.contains("tiện"), "stemToWords['tieng'] must not contain 'tiện'")
        val thanhWords = dict.stemToWords["thanh"] ?: emptyList()
        assertFalse(thanhWords.contains("thẳng"), "stemToWords['thanh'] must not contain 'thẳng'")

        // Every (sig, words) in telexSignatureToWords satisfies sorted(strip(word)) == sig
        for ((sig, words) in dict.telexSignatureToWords) {
            assertTrue(
                words.all { VietnameseDictionary.shared.stripVietnameseDiacritics(it).toCharArray().sorted().joinToString("") == sig },
                "Every word in signature bucket '$sig' must match signature"
            )
        }

        // AC-027.2: Phonotactically invalid syllables must return false
        val invalidSyllables = listOf(
            // Half-horned *uơ+coda strings
            "duờng", "luờng", "huờng", "cuờng", "tuờng", "phuờng", "thuờng", "chuờng", "vuờn", "suờn",
            // Orthographic non-words
            "ghuy", "kuy", "byên", "bơng", "bưn", "tưn", "bưm", "bíc", "bẹc", "buển",
            // Fake d- shadows
            "dất", "dến", "diểm", "dồng", "dứng", "dể"
        )
        for (w in invalidSyllables) {
            assertFalse(
                dict.isValidVietnameseSyllable(w),
                "Invalid syllable '$w' must not be considered valid Vietnamese syllable"
            )
        }
    }

    // 16. Half-horned, Single-d, and Class 5 Insertion Auto-Correction (REQ-027, ADR-028, AC-027.3)
    @Test
    fun testHalfHornedSingleDAndClass5InsertionAutoCorrect() {
        val testCases = listOf(
            "luongf " to "lường ",
            "huongf " to "hường ",
            "cuongf " to "cường ",
            "tuongf " to "tường ",
            "phuongf " to "phường ",
            "thuongf " to "thường ",
            "vuonf " to "vườn ",
            "suonf " to "sườn ",
            "daats " to "đất ",
            "deens " to "đến ",
            "dieemr " to "điểm ",
            "doongf " to "đồng ",
            "duwngs " to "đứng ",
            "deer " to "để ",
            "ngiax " to "nghĩa ",
            "ngiengs " to "nghiếng "
        )

        for ((input, expected) in testCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must produce '$expected'")
        }

        val tuwnfxScreen = VirtualScreen()
        tuwnfxScreen.typeASCII("tuwnfx ")
        assertTrue(
            tuwnfxScreen.text == "tững " || tuwnfxScreen.text == "từng ",
            "Typing 'tuwnfx ' must produce 'tững ' or 'từng ', got '${tuwnfxScreen.text}'"
        )
        assertFalse(
            tuwnfxScreen.text.contains("tưn"),
            "Typing 'tuwnfx ' must never produce 'tưn'"
        )
    }

    // 17. Issue #1 Regression: Missing Syllable Families ('xoá', 'kiểu', 'nguyệt', 'thuở', 'gì') & Spatial Over-Triggering Guard
    @Test
    fun testIssue1MissingSyllablesAndSpatialOverTriggering() {
        val dict = VietnameseDictionary.shared

        // 1. Fake k+ê and toned giu- non-words must be rejected
        val fakeWords = listOf(
            "kểu", "kễu", "kếu", "kệu", "kểm", "kển", "kểng", "kếm", "kến", "kếng",
            "kếc", "kệp", "kệt", "kệc", "kệng", "kễm", "kễn", "kễng",
            "giuý", "giuỳ", "giuấn", "giuần", "giuế", "giuối", "giuốn"
        )
        for (fake in fakeWords) {
            assertFalse(
                dict.isValidVietnameseSyllable(fake),
                "Fake non-word '$fake' must not be a valid Vietnamese syllable"
            )
        }

        // 2. Typing valid words must never be corrupted by spatial auto-correct
        val validTypingCases = listOf(
            "xoas " to "xoá ",
            "xoa " to "xoa ",
            "kieeur " to "kiểu ",
            "kieur " to "kiểu ",
            "nguyeetj " to "nguyệt ",
            "nguyeenx " to "nguyễn ",
            "thuowr " to "thuở ",
            "gif " to "gì ",
            "khoas " to "khoá ",
            "thoar " to "thoả ",
            "cuwuf " to "cừu "
        )
        for ((input, expected) in validTypingCases) {
            val screen = VirtualScreen()
            screen.typeASCII(input)
            assertEquals(expected, screen.text, "Typing '$input' must produce '$expected' (Issue #1)")
        }
    }

    // 18. Never Auto-Capitalize Lowercase 'i' & Preserve Vietnamese Context
    @Test
    fun testNeverAutoCapitalizeLowercaseI() {
        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = true,
            shorthandExpansion = true,
            candidateSuggestions = true,
            doubleSpacePeriod = false
        )
        try {
            val cases = listOf(
                "i " to "i ",
                "ăn cơm i " to "ăn cơm i ",
                "thôi ngủ i " to "thôi ngủ i ",
                "i is " to "i í ",
                "vào game i " to "vào game i ",
                "test i " to "test i ",
                "for i in " to "for i in ",
                "i think " to "i think ",
                "2i " to "2i ",
                "a/i " to "a/i ",
                "x-i " to "x-i ",
                "ăn cơm. i " to "ăn cơm. i ",
                "anh sex đi i " to "anh sẽ đi i ",
                "sex i " to "sẽ i ",
                "sex i think " to "sex i think ",
                "sex I think " to "sex I think ",
                "I " to "I ",
                "I think " to "I think "
            )
            for ((input, expected) in cases) {
                val screen = VirtualScreen()
                screen.typeASCII(input)
                assertEquals(expected, screen.text, "Typing '$input' must produce '$expected' without auto-capitalizing lowercase 'i'")
            }
        } finally {
            AutoWordingEngine.shared.syncConfig(
                isEnabled = false,
                grammarRepair = false,
                shorthandExpansion = false,
                candidateSuggestions = false,
                doubleSpacePeriod = false
            )
        }
    }

    // 19. Post-Dot Backspace Clears Stale Sentence Capitalization Flag
    @Test
    fun testPostDotBackspaceClearsSentenceCapitalization() {
        AutoWordingEngine.shared.syncConfig(
            isEnabled = true,
            grammarRepair = true,
            shorthandExpansion = true,
            candidateSuggestions = true,
            doubleSpacePeriod = false
        )
        try {
            val screen = VirtualScreen()
            screen.typeASCII("xin chaof. ")
            assertEquals("xin chào. ", screen.text)
            screen.typeKey(VietnameseData.KEY_DELETE.toInt()) // deletes trailing space -> "xin chào."
            screen.typeASCII("vn ")
            assertEquals("xin chào.vn ", screen.text, "Deleting space after '.' must cancel sentence-start capitalization")
        } finally {
            AutoWordingEngine.shared.syncConfig(
                isEnabled = false,
                grammarRepair = false,
                shorthandExpansion = false,
                candidateSuggestions = false,
                doubleSpacePeriod = false
            )
        }
    }

    // 20. Bilingual Auto-Restore Toggle Off Preserves Pure Vietnamese Composition
    @Test
    fun testBilingualAutoRestoreToggleOffPreservesVietnameseWithoutEnglishRestore() {
        VietnameseEngine.syncConfig(bilingualAutoRestore = 0)
        try {
            val moreScreen = VirtualScreen()
            moreScreen.typeASCII("more ")
            assertEquals("moẻ ", moreScreen.text, "When bilingualAutoRestore=0, 'more ' must stay Vietnamese 'moẻ '")

            val testScreen = VirtualScreen()
            testScreen.typeASCII("test ")
            assertEquals("tét ", testScreen.text, "When bilingualAutoRestore=0, 'test ' must stay Vietnamese 'tét '")

            val streakScreen = VirtualScreen()
            val snaps = streakScreen.typeASCII("one two three ")
            assertTrue(
                snaps.all { it.languageSwitchRequest == -1 },
                "When bilingualAutoRestore=0, typing English words must not emit auto languageSwitchRequest"
            )
        } finally {
            VietnameseEngine.syncConfig(bilingualAutoRestore = 1)
        }
    }

    // 21. Phase A (A1): Backspace into previous word never deletes earlier text & multi-space preserved
    @Test
    fun testBackspaceIntoPreviousWordNeverDeletesEarlierTextAndMultiSpacePreserved() {
        val s1 = VirtualScreen()
        s1.typeASCII("I love the ")
        s1.typeKey(VietnameseData.KEY_DELETE.toInt())
        s1.typeChar(' ')
        assertEquals("I love the ", s1.text, "Backspace after 'I love the ' then Space must not delete 'ove '")

        val s2 = VirtualScreen()
        s2.typeASCII("to the ")
        s2.typeKey(VietnameseData.KEY_DELETE.toInt())
        s2.typeChar(' ')
        assertEquals("to the ", s2.text, "Backspace after 'to the ' then Space must not delete 'to '")

        val s3 = VirtualScreen()
        s3.typeASCII("I love the")
        s3.typeKey(VietnameseData.KEY_DELETE.toInt())
        s3.typeChar(' ')
        assertEquals("I love th ", s3.text, "Backspace on 'the' -> 'th' then Space must produce 'I love th '")

        val s4 = VirtualScreen()
        s4.typeASCII("the  the ")
        assertEquals("the  the ", s4.text, "Two spaces between 'the  the ' must not corrupt into 'tthe '")
    }

    // 22. Phase B (B1-B8, B10): ALL-CAPS/DD Vietnamese, ky/gi/quo/gin corpus, shifted digits, undo & homographs
    @Test
    fun testPhaseBEngineCorrectnessFixes() {
        // B1: ALL CAPS & Title-case DD Vietnamese words
        val capsCases = listOf(
            "TOOI " to "TÔI ",
            "VIEETJ NAM " to "VIỆT NAM ",
            "DDAATS " to "ĐẤT ",
            "Ddaats " to "Đất ",
            "DDi " to "Đi ",
            "DDaf Nawngx " to "Đà Nẵng "
        )
        for ((input, expected) in capsCases) {
            val s = VirtualScreen()
            s.typeASCII(input)
            assertEquals(expected, s.text, "Caps Vietnamese '$input' must produce '$expected'")
        }

        VietnameseEngine.syncConfig(inputType = 1)
        try {
            val sVniCaps = VirtualScreen()
            sVniCaps.typeASCII("VIE65T NAM ")
            assertEquals("VIỆT NAM ", sVniCaps.text, "VNI ALL-CAPS 'VIE65T NAM ' must produce 'VIỆT NAM '")
        } finally {
            VietnameseEngine.syncConfig(inputType = 0)
        }

        // B2 & B3: ky/gi/quo/gin phonotactics & corpus
        val kyGiCases = listOf(
            "kyx " to "kỹ ",
            "kyx thuaatj " to "kỹ thuật ",
            "kys " to "ký ",
            "kyf " to "kỳ ",
            "kyr " to "kỷ ",
            "kyj " to "kỵ ",
            "gis " to "gí ",
            "gif " to "gì ",
            "gir " to "gỉ ",
            "gix " to "gĩ ",
            "gij " to "gị ",
            "gifn " to "gìn ",
            "gifn giuwx " to "gìn giữ ",
            "quowr " to "quở "
        )
        for ((input, expected) in kyGiCases) {
            val s = VirtualScreen()
            s.typeASCII(input)
            assertEquals(expected, s.text, "Syllable '$input' must compose to '$expected'")
        }

        // B4: Shifted-digit symbols trigger word-break restore
        val shiftedDigitCases = listOf(
            "test! " to "test! ",
            "test@gmail.com " to "test@gmail.com "
        )
        for ((input, expected) in shiftedDigitCases) {
            val s = VirtualScreen()
            s.typeASCII(input)
            assertEquals(expected, s.text, "Shifted-digit word break '$input' must produce '$expected'")
        }

        // B5, B6, B7, B8, B10: Double-key undo, Roman numerals, English words, homographs & English context
        val otherCases = listOf(
            "Harry " to "Harry ",
            "password " to "password ",
            "www.facebook.com " to "www.facebook.com ",
            "ddd " to "dd ",
            "tieengss " to "tieengs ",
            "VII) " to "VII) ",
            "hello " to "hello ",
            "wow " to "wow ",
            "camr own " to "cảm ơn ",
            "mootj its " to "một ít ",
            "its " to "ít ",
            "this is my own " to "this is my own ",
            "this is my room " to "this is my room "
        )
        for ((input, expected) in otherCases) {
            val s = VirtualScreen()
            s.typeASCII(input)
            assertEquals(expected, s.text, "Input '$input' must produce '$expected'")
        }
    }
}


