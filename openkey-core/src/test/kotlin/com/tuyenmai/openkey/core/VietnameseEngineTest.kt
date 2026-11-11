package com.tuyenmai.openkey.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class VietnameseEngineTest {

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
    }

    // Helper: Virtual Screen simulating user screen buffer
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

    // REQ-001: Direct-Boot readiness and zero internet permission in AndroidManifest.xml
    @Test
    fun testManifestDirectBootAndZeroInternetPermission() {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("Sources/OpenKey/android/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidates.firstOrNull { it.exists() }
        assertNotNull(manifestFile, "AndroidManifest.xml must exist")
        val content = manifestFile!!.readText()

        assertTrue(
            content.contains("android:directBootAware=\"true\""),
            "AndroidManifest.xml must declare android:directBootAware=\"true\""
        )
        assertTrue(
            content.contains("android.permission.BIND_INPUT_METHOD"),
            "AndroidManifest.xml must declare android.permission.BIND_INPUT_METHOD"
        )
        assertFalse(
            content.contains("android.permission.INTERNET"),
            "AndroidManifest.xml strictly MUST NOT contain android.permission.INTERNET (REQ-001)"
        )
        assertTrue(
            content.contains("android:allowBackup=\"false\""),
            "AndroidManifest.xml must declare android:allowBackup=\"false\" for privacy"
        )
    }

    // REQ-002: Telex syllable composition
    @Test
    fun testTelexComposition() {
        val screen = VirtualScreen()

        // "tiengs" -> "tiếng"
        screen.typeASCII("tieng")
        val snapS = screen.typeChar('s')
        assertEquals(HookCodeState.WILL_PROCESS, snapS.code, "Typing 's' on 'tieng' must trigger WILL_PROCESS")
        assertEquals(4, snapS.backspaceCount, "backspaceCount must be 4 on 's' in 'tiengs' (replacing 'ieng')")
        assertEquals(4, snapS.newCharCount, "newCharCount must be 4 ('iếng')")
        assertEquals("tiếng", screen.text)

        // "vietej" -> "việt"
        val screenViet = VirtualScreen()
        screenViet.typeASCII("vietej")
        assertEquals("việt", screenViet.text)

        // "truowngf" -> "trường"
        val screenTruong = VirtualScreen()
        screenTruong.typeASCII("truowngf")
        assertEquals("trường", screenTruong.text)

        // "nguowif" -> "người"
        val screenNguoi = VirtualScreen()
        screenNguoi.typeASCII("nguowif")
        assertEquals("người", screenNguoi.text)

        // "dd" -> "đ"
        val screenD = VirtualScreen()
        screenD.typeASCII("dd")
        assertEquals("đ", screenD.text)

        // Complex sentence simulation: "kiểm tra tiếng v, "
        val screenSentence = VirtualScreen()
        screenSentence.typeASCII("kiểm tra tiếng v, ")
        assertEquals("kiểm tra tiếng v, ", screenSentence.text)
        assertFalse(screenSentence.text.contains("tiiếng"), "Screen must never suffer from 'tiiếng' vowel duplication")
    }

    // REQ-002: VNI syllable composition
    @Test
    fun testVniComposition() {
        VietnameseEngine.syncConfig(inputType = 1) // 1 = VNI

        // "tieng61" -> "tiếng"
        val screenTieng = VirtualScreen()
        screenTieng.typeASCII("tieng61")
        assertEquals("tiếng", screenTieng.text)

        // "viet65" -> "việt"
        val screenViet = VirtualScreen()
        screenViet.typeASCII("viet65")
        assertEquals("việt", screenViet.text)

        // "gio71i" -> "giới"
        val screenGioi = VirtualScreen()
        screenGioi.typeASCII("gio71i")
        assertEquals("giới", screenGioi.text)

        // "co81" -> must not desynchronize and produce "coó"
        val screenCo81 = VirtualScreen()
        screenCo81.typeASCII("co81")
        assertNotEquals("coó", screenCo81.text, "VNI 'co81' must not desynchronize and produce 'coó'")
        assertEquals("co81", screenCo81.text)
    }

    // REQ-002, ADR-002: "giới" and gi- / qu- backward vowel boundary
    @Test
    fun testGioiAndGiQuVowelBoundary() {
        val screen = VirtualScreen()

        // "giowis" produces "giới", final 's' emits backspaceCount == 2, newCharCount == 2, "ới"
        screen.typeASCII("giowi")
        val snapS = screen.typeChar('s')
        assertEquals(HookCodeState.WILL_PROCESS, snapS.code)
        assertEquals(2, snapS.backspaceCount, "backspaceCount must be 2, deleting 'ơi'")
        assertEquals(2, snapS.newCharCount, "newCharCount must be 2, replacing with 'ới'")
        assertEquals("ới", snapS.decodedString)
        assertEquals("giới", screen.text)

        // Additional gi- / qu- syllables
        val cases = listOf(
            "giow" to "giơ",
            "gieof" to "gièo",
            "giuwowngf" to "giường",
            "quoocs" to "quốc",
            "quas" to "quá"
        )
        for ((input, expected) in cases) {
            val s = VirtualScreen()
            s.typeASCII(input)
            assertEquals(expected, s.text, "Typing '$input' must produce '$expected'")
        }
    }

    // REQ-002: Modern vs Traditional orthography (vUseModernOrthography)
    @Test
    fun testModernVsTraditionalOrthography() {
        // Modern orthography (vUseModernOrthography = 1): "hoà", "thuỷ"
        VietnameseEngine.syncConfig(useModernOrthography = 1)
        val screenModernHoa = VirtualScreen()
        screenModernHoa.typeASCII("hoaf")
        assertEquals("hoà", screenModernHoa.text, "Modern orthography must produce 'hoà'")

        val screenModernThuy = VirtualScreen()
        screenModernThuy.typeASCII("thuyr")
        assertEquals("thuỷ", screenModernThuy.text, "Modern orthography must produce 'thuỷ'")

        // Traditional orthography (vUseModernOrthography = 0): "hòa", "thủy"
        VietnameseEngine.syncConfig(useModernOrthography = 0)
        val screenTradHoa = VirtualScreen()
        screenTradHoa.typeASCII("hoaf")
        assertEquals("hòa", screenTradHoa.text, "Traditional orthography must produce 'hòa'")

        val screenTradThuy = VirtualScreen()
        screenTradThuy.typeASCII("thuyr")
        assertEquals("thủy", screenTradThuy.text, "Traditional orthography must produce 'thủy'")
    }

    // REQ-002: Multi-delete and subsequent character entry state integrity
    @Test
    fun testMultiDeleteAndStateIntegrity() {
        // Typing toned words and pressing KEY_DELETE once passes through cleanly (code == 0, bs == 0, new == 0)
        for (word in listOf("nhất", "tiếng", "trường")) {
            val s = VirtualScreen()
            s.typeASCII(word)
            val snapDel = s.typeKey(VietnameseData.KEY_DELETE.toInt())
            assertEquals(HookCodeState.DO_NOTHING, snapDel.code, "Single delete on '$word' must pass through cleanly")
            assertEquals(0, snapDel.backspaceCount)
            assertEquals(0, snapDel.newCharCount)
        }

        // Typing "tiếng" (t-i-e-e-n-g-s), pressing KEY_DELETE 3 times (leaving "ti"), and typing "ếp" -> "tiếp"
        val sTiep = VirtualScreen()
        sTiep.typeASCII("tiengs")
        assertEquals("tiếng", sTiep.text)
        sTiep.typeKey(VietnameseData.KEY_DELETE.toInt()) // removes 'g'
        sTiep.typeKey(VietnameseData.KEY_DELETE.toInt()) // removes 'n'
        sTiep.typeKey(VietnameseData.KEY_DELETE.toInt()) // removes 'ế' -> leaves "ti"
        assertEquals("ti", sTiep.text)

        sTiep.typeASCII("eepj") // "ếp"
        assertEquals("tiệp", sTiep.text, "Must compose into 'tiệp' without resurrected 'e' from previous 'tiếng'")

        // Typing "tiếng" (t-i-e-e-n-g-s), pressing KEY_DELETE 3 times (leaving "ti"), and typing "me " -> "time "
        val sTime = VirtualScreen()
        sTime.typeASCII("tiengs")
        assertEquals("tiếng", sTime.text)
        sTime.typeKey(VietnameseData.KEY_DELETE.toInt())
        sTime.typeKey(VietnameseData.KEY_DELETE.toInt())
        sTime.typeKey(VietnameseData.KEY_DELETE.toInt())
        assertEquals("ti", sTime.text)

        sTime.typeASCII("me ")
        assertEquals("time ", sTime.text, "Typing 'me ' after deleting back to 'ti' must produce 'time '")
    }

    // REQ-002: Invalid syllable ASCII auto-restore (vCheckSpelling = 1, vRestoreIfWrongSpelling = 1)
    @Test
    fun testInvalidWordAsciiAutoRestore() {
        VietnameseEngine.syncConfig(checkSpelling = 1, restoreIfWrongSpelling = 1)

        val invalidWords = listOf(
            "dead ",
            "added ",
            "window "
        )
        for (word in invalidWords) {
            val screen = VirtualScreen()
            screen.typeASCII(word)
            assertEquals(word, screen.text, "Invalid word '$word' must restore raw ASCII keystrokes on word break")
        }
    }

    // REQ-021, ADR-022: Live Mid-Word Restoration for Post-Tone E and Invalid Coda Consonants
    @Test
    fun testLiveMidWordRestorationBeforeSpace() {
        VietnameseEngine.syncConfig(checkSpelling = 1, restoreIfWrongSpelling = 1)

        // Post-Tone E words restored BEFORE Space is pressed
        val postToneEWords = listOf(
            "more", "core", "store", "care", "share", "fire", "sure", "case", "nose", "use", "house", "life"
        )
        for (w in postToneEWords) {
            val screen = VirtualScreen()
            screen.typeASCII(w)
            assertEquals(w, screen.text, "Typing '$w' must restore live to '$w' before Space is pressed")
        }

        // Invalid Post-Vowel Coda Consonant words restored BEFORE Space is pressed
        val invalidCodaWords = listOf(
            "need", "needed", "good", "book", "feel", "work", "word", "part", "start", "sort", "soft", "left", "text", "next"
        )
        for (w in invalidCodaWords) {
            val screen = VirtualScreen()
            screen.typeASCII(w)
            assertEquals(w, screen.text, "Typing '$w' must restore live to '$w' before Space is pressed")
        }
    }

    // REQ-021, ADR-022: Live Double-Consonant Restoration & Compound Telex Escape
    @Test
    fun testLiveDoubleConsonantAndCompoundTelexEscape() {
        VietnameseEngine.syncConfig(checkSpelling = 1, restoreIfWrongSpelling = 1)

        // Live mid-word double-consonant restoration of "correct" and "errors"
        val screenCorrect = VirtualScreen()
        screenCorrect.typeASCII("correct")
        assertEquals("correct", screenCorrect.text, "Typing 'correct' must restore live before Space")

        val screenErrors = VirtualScreen()
        screenErrors.typeASCII("errors")
        assertEquals("errors", screenErrors.text, "Typing 'errors' must restore live before Space")

        // Preserving "serrvice," -> "service,"
        val screenService = VirtualScreen()
        screenService.typeASCII("serrvice,")
        assertEquals("service,", screenService.text, "Typing 'serrvice,' must produce 'service,'")

        // Compound Telex escape: t-h-e-s-e + s -> "these", and t-h-e-s-e + e -> "these"
        val screenTheseS = VirtualScreen()
        screenTheseS.typeASCII("theses")
        assertEquals("these", screenTheseS.text, "Typing 'theses' (these + s escape) must produce 'these'")

        val screenTheseE = VirtualScreen()
        screenTheseE.typeASCII("thesee")
        assertEquals("these", screenTheseE.text, "Typing 'thesee' (these + e escape) must produce 'these'")
    }

    // REQ-027, ADR-027: Escape Keystroke Consumption (redundant trailing 'i')
    @Test
    fun testEscapeKeystrokeConsumption() {
        VietnameseEngine.syncConfig(checkSpelling = 1, restoreIfWrongSpelling = 1)
        val screen = VirtualScreen()
        screen.typeASCII("loai") // "loại" / "loai"
        val currentText = screen.text
        screen.typeASCII("i")
        assertEquals(currentText, screen.text, "Redundant trailing 'i' on valid syllable must be consumed without altering text")
    }
}
