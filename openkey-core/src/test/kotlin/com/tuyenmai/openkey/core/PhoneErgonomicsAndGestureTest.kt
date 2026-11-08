package com.tuyenmai.openkey.core

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PhoneErgonomicsAndGestureTest {

    // -------------------------------------------------------------------------
    // 1. PhoneLayoutMetrics & KeyboardLayoutDefinition Tests (REQ-007, ADR-007)
    // -------------------------------------------------------------------------

    @Test
    fun `test Galaxy A37 portrait base metrics at 100 percent scale`() {
        // Galaxy A37 FHD+ portrait: 360dp x 780dp (and 411dp x 891dp)
        val config = PhoneLayoutConfig(
            screenWidthDp = 360,
            screenHeightDp = 780,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0, // Telex
            heightScalePercent = 100,
            bottomChinPaddingDp = 14,
            oneHandedMode = OneHandedMode.OFF
        )

        val metrics = PhoneLayoutMetrics.compute(config)

        assertEquals(44f, metrics.candidateStripHeightDp)
        assertEquals(42f, metrics.numberRowHeightDp)
        assertEquals(52f, metrics.alphaRowHeightDp)
        assertEquals(50f, metrics.bottomRowHeightDp)
        assertEquals(0.05f, metrics.homeRowSideInsetFraction, 0.001f)
        assertEquals(14f, metrics.effectiveBottomChinDp)
        assertEquals(7f, metrics.keyCornerRadiusDp)
        assertEquals(4f, metrics.keyHorizontalGapDp)
        assertEquals(5f, metrics.keyVerticalGapDp)
        assertEquals(360f, metrics.activeGridWidthDp)
        assertEquals(0f, metrics.sideGutterWidthDp)
        assertEquals(OneHandedMode.OFF, metrics.effectiveOneHandedMode)
        assertTrue(metrics.isNumberRowVisible)

        // Total height: 44 (strip) + 42 (number) + 3 * 52 (alpha rows) + 50 (bottom) + 14 (chin) = 306dp
        val expectedTotal = 44f + 42f + (3 * 52f) + 50f + 14f
        assertEquals(expectedTotal, metrics.totalHeightDp, 0.1f)
        assertEquals(306f, metrics.totalHeightDp, 0.1f)
    }

    @Test
    fun `test Galaxy A37 scaling and clamping of heightScalePercent and bottomChinPadding`() {
        // Test scale up: 120%
        val configScaled = PhoneLayoutConfig(
            screenWidthDp = 411,
            screenHeightDp = 891,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0,
            heightScalePercent = 120,
            bottomChinPaddingDp = 20,
            oneHandedMode = OneHandedMode.OFF
        )
        val metricsScaled = PhoneLayoutMetrics.compute(configScaled)
        assertEquals(44f * 1.2f, metricsScaled.candidateStripHeightDp, 0.01f)
        assertEquals(42f * 1.2f, metricsScaled.numberRowHeightDp, 0.01f)
        assertEquals(52f * 1.2f, metricsScaled.alphaRowHeightDp, 0.01f)
        assertEquals(50f * 1.2f, metricsScaled.bottomRowHeightDp, 0.01f)
        assertEquals(20f, metricsScaled.effectiveBottomChinDp)

        // Test clamping below min (70% -> 85%, -5 chin -> 0)
        val configUnder = PhoneLayoutConfig(
            screenWidthDp = 360,
            screenHeightDp = 780,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0,
            heightScalePercent = 70,
            bottomChinPaddingDp = -5
        )
        val metricsUnder = PhoneLayoutMetrics.compute(configUnder)
        assertEquals(44f * 0.85f, metricsUnder.candidateStripHeightDp, 0.01f)
        assertEquals(0f, metricsUnder.effectiveBottomChinDp)

        // Test clamping above max (150% -> 125%, 60 chin -> 48)
        val configOver = PhoneLayoutConfig(
            screenWidthDp = 360,
            screenHeightDp = 780,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0,
            heightScalePercent = 150,
            bottomChinPaddingDp = 60
        )
        val metricsOver = PhoneLayoutMetrics.compute(configOver)
        assertEquals(44f * 1.25f, metricsOver.candidateStripHeightDp, 0.01f)
        assertEquals(48f, metricsOver.effectiveBottomChinDp)
    }

    @Test
    fun `test VNI inputType forces number row visible even if showNumberRow is false`() {
        val configVniHidden = PhoneLayoutConfig(
            screenWidthDp = 360,
            screenHeightDp = 780,
            isLandscape = false,
            showNumberRow = false,
            inputType = 1 // VNI
        )
        val metricsVni = PhoneLayoutMetrics.compute(configVniHidden)
        assertTrue(metricsVni.isNumberRowVisible, "VNI inputType must force number row visible")
        assertEquals(42f, metricsVni.numberRowHeightDp)

        val configTelexHidden = PhoneLayoutConfig(
            screenWidthDp = 360,
            screenHeightDp = 780,
            isLandscape = false,
            showNumberRow = false,
            inputType = 0 // Telex
        )
        val metricsTelex = PhoneLayoutMetrics.compute(configTelexHidden)
        assertFalse(metricsTelex.isNumberRowVisible, "Telex with showNumberRow=false should hide number row")
        assertEquals(0f, metricsTelex.numberRowHeightDp)
    }

    @Test
    fun `test OneHandedMode on portrait large screen sets 82 percent grid and 18 percent gutter`() {
        val configLeft = PhoneLayoutConfig(
            screenWidthDp = 411,
            screenHeightDp = 891,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0,
            oneHandedMode = OneHandedMode.LEFT
        )
        val metricsLeft = PhoneLayoutMetrics.compute(configLeft)
        assertEquals(OneHandedMode.LEFT, metricsLeft.effectiveOneHandedMode)
        assertEquals(411f * 0.82f, metricsLeft.activeGridWidthDp, 0.01f)
        assertEquals(411f * 0.18f, metricsLeft.sideGutterWidthDp, 0.01f)

        // When screen width < 360, OneHandedMode should be forced to OFF
        val configSmall = PhoneLayoutConfig(
            screenWidthDp = 320,
            screenHeightDp = 640,
            isLandscape = false,
            showNumberRow = true,
            inputType = 0,
            oneHandedMode = OneHandedMode.RIGHT
        )
        val metricsSmall = PhoneLayoutMetrics.compute(configSmall)
        assertEquals(OneHandedMode.OFF, metricsSmall.effectiveOneHandedMode)
        assertEquals(320f, metricsSmall.activeGridWidthDp)
        assertEquals(0f, metricsSmall.sideGutterWidthDp)
    }

    @Test
    fun `test landscape auto compact mode caps totalHeight at 42 percent screen height`() {
        val configLandscape = PhoneLayoutConfig(
            screenWidthDp = 780,
            screenHeightDp = 360,
            isLandscape = true,
            showNumberRow = true,
            inputType = 0
        )
        val metrics = PhoneLayoutMetrics.compute(configLandscape)

        assertEquals(0f, metrics.effectiveBottomChinDp, "Landscape should have 0dp chin")
        assertEquals(OneHandedMode.OFF, metrics.effectiveOneHandedMode, "Landscape forces OneHandedMode.OFF")
        val maxAllowedHeight = 360f * 0.42f // 151.2 dp
        assertTrue(metrics.totalHeightDp <= maxAllowedHeight + 0.1f, "Total height ${metrics.totalHeightDp} must be <= $maxAllowedHeight")
    }

    @Test
    fun `test KeyboardLayoutDefinition One UI 5-row QWERTY weights and symbol layers`() {
        // QWERTY Layer
        val qwerty = KeyboardLayoutDefinition.getLayer(KeyboardLayer.QWERTY, isShifted = false, language = 1, imeActionLabel = "Done")
        assertEquals(5, qwerty.rows.size, "Samsung One UI layout must have 5 rows")

        // Row 1: 10 number keys, each 0.10f
        val row1 = qwerty.rows[0]
        assertEquals(10, row1.keys.size)
        assertTrue(row1.keys.all { it.weight == 0.10f })
        assertEquals("1", row1.keys.first().primaryLabel)
        assertEquals("0", row1.keys.last().primaryLabel)

        // Row 2: 10 letter keys (q..p), each 0.10f
        val row2 = qwerty.rows[1]
        assertEquals(10, row2.keys.size)
        assertEquals("q", row2.keys.first().primaryLabel)
        assertEquals("p", row2.keys.last().primaryLabel)

        // Row 3: 9 keys (a..l), each 0.10f with 5% side inset
        val row3 = qwerty.rows[2]
        assertEquals(9, row3.keys.size)
        assertEquals(0.05f, row3.sideInsetFraction, 0.001f)
        assertEquals("a", row3.keys.first().primaryLabel)
        assertEquals("l", row3.keys.last().primaryLabel)

        // Row 4: Shift (0.15f) + 7 keys (0.10f) + Backspace (0.15f)
        val row4 = qwerty.rows[3]
        assertEquals(9, row4.keys.size)
        assertEquals(0.15f, row4.keys.first().weight)
        assertEquals("⇧", row4.keys.first().primaryLabel)
        assertEquals(0.15f, row4.keys.last().weight)
        assertEquals("⌫", row4.keys.last().primaryLabel)

        // Row 5: !#1 (0.14f), 🌐 (0.10f), comma (0.09f), space (0.42f), dot (0.09f), enter (0.16f)
        val row5 = qwerty.rows[4]
        assertEquals(6, row5.keys.size)
        assertEquals("!#1", row5.keys[0].primaryLabel)
        assertEquals(0.14f, row5.keys[0].weight)
        assertTrue(row5.keys[0].isFunctional)

        assertEquals("🌐", row5.keys[1].primaryLabel)
        assertEquals(0.10f, row5.keys[1].weight)
        assertTrue(row5.keys[1].isFunctional)
        assertEquals("VI", row5.keys[1].secondaryLabel)
        assertEquals(KeyAction.SwitchLanguage, row5.keys[1].action)

        assertEquals(",", row5.keys[2].primaryLabel)
        assertEquals(0.09f, row5.keys[2].weight)

        assertEquals("Tiếng Việt", row5.keys[3].primaryLabel)
        assertEquals(0.42f, row5.keys[3].weight)

        assertEquals(".", row5.keys[4].primaryLabel)
        assertEquals(0.09f, row5.keys[4].weight)

        assertEquals("Done", row5.keys[5].primaryLabel)
        assertEquals(0.16f, row5.keys[5].weight)
        assertEquals(KeyAction.Enter, row5.keys[5].action)

        // English spacebar label & language badge
        val qwertyEn = KeyboardLayoutDefinition.getLayer(KeyboardLayer.QWERTY, isShifted = false, language = 0, imeActionLabel = "↵")
        assertEquals("English (US)", qwertyEn.rows[4].keys[3].primaryLabel)
        assertEquals("EN", qwertyEn.rows[4].keys[1].secondaryLabel)

        // SYMBOLS_1 & SYMBOLS_2 layers: still 5 keys with ABC at keys[0]
        val sym1 = KeyboardLayoutDefinition.getLayer(KeyboardLayer.SYMBOLS_1)
        val sym2 = KeyboardLayoutDefinition.getLayer(KeyboardLayer.SYMBOLS_2)
        assertEquals(5, sym1.rows.size)
        assertEquals(5, sym2.rows.size)
        assertEquals(5, sym1.rows[4].keys.size)
        assertEquals(5, sym2.rows[4].keys.size)
        assertEquals("ABC", sym1.rows[4].keys[0].primaryLabel, "Symbols layer must provide ABC return key")
        assertEquals("ABC", sym2.rows[4].keys[0].primaryLabel)
    }

    @Test
    fun `test One UI Theme palettes for Light and Dark modes`() {
        val light = KeyboardLayoutDefinition.OneUiLightTheme
        assertEquals(0xFFE8EAED, light.backgroundColor)
        assertEquals(0xFFFFFFFF, light.keycapColor)
        assertEquals(0xFFD6D9E0, light.functionalKeycapColor)
        assertEquals(0xFFDCE0E8, light.pressedKeycapColor)
        assertEquals(0xFFC4C9D4, light.pressedFunctionalKeycapColor)

        val dark = KeyboardLayoutDefinition.OneUiDarkTheme
        assertEquals(0xFF1B1B1F, dark.backgroundColor)
        assertEquals(0xFF2C2C32, dark.keycapColor)
        assertEquals(0xFF222227, dark.functionalKeycapColor)
        assertEquals(0xFF3E3E47, dark.pressedKeycapColor)
        assertEquals(0xFF34343D, dark.pressedFunctionalKeycapColor)
    }

    // -------------------------------------------------------------------------
    // 2. KeyboardGestureController Tests (REQ-008, ADR-008)
    // -------------------------------------------------------------------------

    @Test
    fun `test Spacebar horizontal trackpad drag and release`() {
        val controller = KeyboardGestureController()

        controller.onSpacebarDown()

        // Drag less than 10dp threshold -> no action
        val smallDrag = controller.onSpacebarDrag(8f)
        assertTrue(smallDrag.isEmpty())

        // Tap release without reaching 10dp -> commits Space
        val releaseTap = controller.onSpacebarUp()
        assertTrue(releaseTap is GestureAction.CommitSpace)

        // Now test full trackpad drag >= 10dp
        controller.onSpacebarDown()
        // Drag +14dp right (crosses 10dp threshold, crosses 12dp step)
        val dragRight = controller.onSpacebarDrag(14f)
        assertEquals(1, dragRight.size)
        assertTrue(dragRight.first() is GestureAction.CursorMove)
        assertEquals(1, (dragRight.first() as GestureAction.CursorMove).steps)

        // Drag further right by +24dp (2 steps)
        val dragFurther = controller.onSpacebarDrag(24f)
        assertEquals(2, dragFurther.size)

        // Release when trackpad active -> FinishTrackpad without space insertion
        val releaseTrackpad = controller.onSpacebarUp()
        assertTrue(releaseTrackpad is GestureAction.FinishTrackpad)
    }

    @Test
    fun `test Backspace left-swipe whole-word delete and tap single delete`() {
        val controller = KeyboardGestureController()

        // Tap Backspace (< 24dp drag) -> DeleteSingleChar
        controller.onBackspaceDown()
        val tapRelease = controller.onBackspaceUp()
        assertTrue(tapRelease is GestureAction.DeleteSingleChar)

        // Leftward swipe: -25dp (crosses 24dp threshold) -> DeletePreviousWord
        controller.onBackspaceDown()
        val swipeLeft = controller.onBackspaceDrag(-25f)
        assertEquals(1, swipeLeft.size)
        assertTrue(swipeLeft.first() is GestureAction.DeletePreviousWord)

        // Additional -24dp -> another word delete
        val swipeMore = controller.onBackspaceDrag(-24f)
        assertEquals(1, swipeMore.size)
        assertTrue(swipeMore.first() is GestureAction.DeletePreviousWord)

        // Release after swipe -> FinishSwipeDelete (suppresses single delete)
        val releaseSwipe = controller.onBackspaceUp()
        assertTrue(releaseSwipe is GestureAction.FinishSwipeDelete)
    }

    @Test
    fun `test Long-press popups for Vietnamese vowels and secondary symbols`() {
        val controller = KeyboardGestureController()

        // Long press threshold constant
        assertEquals(220L, KeyboardGestureController.LONG_PRESS_TIMEOUT_MS)

        // Vietnamese vowel variants
        val aVariants = controller.resolveLongPressVariants('a')
        assertEquals(listOf("ă", "â", "á", "à", "ả", "ã", "ạ"), aVariants)

        val dVariants = controller.resolveLongPressVariants('d')
        assertEquals(listOf("đ"), dVariants)

        val eVariants = controller.resolveLongPressVariants('e')
        assertEquals(listOf("ê", "é", "è", "ẻ", "ẽ", "ẹ"), eVariants)

        val oVariants = controller.resolveLongPressVariants('o')
        assertEquals(listOf("ô", "ơ", "ó", "ò", "ỏ", "õ", "ọ"), oVariants)

        val uVariants = controller.resolveLongPressVariants('u')
        assertEquals(listOf("ư", "ú", "ù", "ủ", "ũ", "ụ"), uVariants)
    }

    // -------------------------------------------------------------------------
    // 3. OpenKeySettingsStore & OpenKeyL10n Tests (REQ-010, ADR-010)
    // -------------------------------------------------------------------------

    @Test
    fun `test OpenKeySettingsStore value clamping and defaults`() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage)

        // Defaults
        assertEquals(1, store.language)
        assertEquals(0, store.inputType)
        assertTrue(store.useModernOrthography)
        assertTrue(store.checkSpelling)
        assertTrue(store.restoreIfWrongSpelling)
        assertTrue(store.bilingualAutoRestore)
        assertTrue(store.useSmartSwitchKey)
        assertTrue(store.showNumberRow)
        assertEquals(100, store.heightScalePercent)
        assertEquals(14, store.bottomChinPaddingDp)
        assertEquals(OneHandedMode.OFF, store.oneHandedMode)
        assertTrue(store.hapticFeedbackEnabled)
        assertTrue(store.keyPopupPreviewEnabled)
        assertTrue(store.autoWordingEnabled)

        // Clamping tests
        store.inputType = 5
        assertEquals(1, store.inputType, "inputType must be clamped to 1")
        store.inputType = -2
        assertEquals(0, store.inputType, "inputType must be clamped to 0")

        store.heightScalePercent = 150
        assertEquals(125, store.heightScalePercent, "heightScalePercent must be clamped to 125")
        store.heightScalePercent = 50
        assertEquals(85, store.heightScalePercent, "heightScalePercent must be clamped to 85")

        store.bottomChinPaddingDp = 100
        assertEquals(48, store.bottomChinPaddingDp, "bottomChinPaddingDp must be clamped to 48")
        store.bottomChinPaddingDp = -10
        assertEquals(0, store.bottomChinPaddingDp, "bottomChinPaddingDp must be clamped to 0")
    }

    @Test
    fun `test master autoWordingEnabled cascade activates sub-toggles when all three are false`() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage)

        // Disable all three sub-toggles
        store.grammarRepairEnabled = false
        store.shorthandExpansionEnabled = false
        store.candidateSuggestionsEnabled = false
        store.autoWordingEnabled = false

        assertFalse(store.grammarRepairEnabled)
        assertFalse(store.shorthandExpansionEnabled)
        assertFalse(store.candidateSuggestionsEnabled)

        // Enabling master toggle must cascade to all three
        store.autoWordingEnabled = true

        assertTrue(store.grammarRepairEnabled, "Enabling master should cascade to grammar repair")
        assertTrue(store.shorthandExpansionEnabled, "Enabling master should cascade to shorthands")
        assertTrue(store.candidateSuggestionsEnabled, "Enabling master should cascade to candidate suggestions")
    }

    @Test
    fun `test Custom Shorthand CRUD operations`() {
        val storage = InMemoryKeyValueStorage()
        val store = OpenKeySettingsStore(storage)

        store.setCustomShorthand("cty", "công ty")
        store.setCustomShorthand("dc", "được")

        val shorthands = store.getCustomShorthands()
        assertEquals("công ty", shorthands["cty"])
        assertEquals("được", shorthands["dc"])

        store.removeCustomShorthand("dc")
        val afterRemove = store.getCustomShorthands()
        assertNull(afterRemove["dc"])
        assertEquals("công ty", afterRemove["cty"])

        store.clearCustomShorthands()
        assertTrue(store.getCustomShorthands().isEmpty())
    }

    @Test
    fun `test OpenKeyL10n contains non-empty VI and EN strings across all sections`() {
        val sections = listOf(
            OpenKeyL10n.Onboarding,
            OpenKeyL10n.Ergonomics,
            OpenKeyL10n.Engine,
            OpenKeyL10n.AutoWording,
            OpenKeyL10n.About
        )

        for (section in sections) {
            val viMap = section.getStrings(OpenKeyL10n.Language.VI)
            val enMap = section.getStrings(OpenKeyL10n.Language.EN)

            assertFalse(viMap.isEmpty(), "VI map must not be empty for section $section")
            assertFalse(enMap.isEmpty(), "EN map must not be empty for section $section")

            for ((key, viStr) in viMap) {
                assertTrue(viStr.isNotBlank(), "Key $key in VI must not be blank")
                val enStr = enMap[key]
                assertNotNull(enStr, "Key $key must exist in EN")
                assertTrue(enStr!!.isNotBlank(), "Key $key in EN must not be blank")
            }
        }
    }

    // REQ-025, AC-025.2: Dedicated Samsung One UI 4x4 Numeric Keypad
    @Test
    fun testDedicatedNumpadLayerGeometryAndWeights() {
        val layerDef = KeyboardLayoutDefinition.getLayer(KeyboardLayer.NUMPAD, imeActionLabel = "Done")
        assertEquals(4, layerDef.rows.size, "NUMPAD layer must have exactly 4 rows")

        for (row in layerDef.rows) {
            assertEquals(4, row.keys.size, "Each NUMPAD row must have exactly 4 keys")
            val sumWeights = row.keys.map { it.weight }.sum()
            assertEquals(1.00f, sumWeights, 0.001f, "Row weights must sum to 1.00f")
            assertEquals(0.26f, row.keys[0].weight, 0.001f)
            assertEquals(0.26f, row.keys[1].weight, 0.001f)
            assertEquals(0.26f, row.keys[2].weight, 0.001f)
            assertEquals(0.22f, row.keys[3].weight, 0.001f)
        }

        // Row 1: 1 2 3 -
        val r1 = layerDef.rows[0].keys
        assertEquals("1", r1[0].primaryLabel)
        assertEquals("2", r1[1].primaryLabel)
        assertEquals("3", r1[2].primaryLabel)
        assertEquals("-", r1[3].primaryLabel)
        assertTrue(r1[3].isFunctional)

        // Row 2: 4 5 6 Space
        val r2 = layerDef.rows[1].keys
        assertEquals("4", r2[0].primaryLabel)
        assertEquals("5", r2[1].primaryLabel)
        assertEquals("6", r2[2].primaryLabel)
        assertTrue(r2[3].action is KeyAction.Space)
        assertTrue(r2[3].isFunctional)

        // Row 3: 7 8 9 ⌫
        val r3 = layerDef.rows[2].keys
        assertEquals("7", r3[0].primaryLabel)
        assertEquals("8", r3[1].primaryLabel)
        assertEquals("9", r3[2].primaryLabel)
        assertEquals("⌫", r3[3].primaryLabel)
        assertTrue(r3[3].action is KeyAction.Backspace)
        assertTrue(r3[3].isFunctional)

        // Row 4: , 0 . Enter
        val r4 = layerDef.rows[3].keys
        assertEquals(",", r4[0].primaryLabel)
        assertTrue(r4[0].isFunctional)
        assertEquals("0", r4[1].primaryLabel)
        assertEquals(".", r4[2].primaryLabel)
        assertTrue(r4[2].isFunctional)
        assertEquals("Done", r4[3].primaryLabel)
        assertTrue(r4[3].action is KeyAction.Enter)
        assertTrue(r4[3].isFunctional)
    }
}
