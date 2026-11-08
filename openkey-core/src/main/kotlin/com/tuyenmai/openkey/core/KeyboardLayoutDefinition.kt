package com.tuyenmai.openkey.core

sealed class KeyAction {
    data class CommitChar(val char: String) : KeyAction()
    object Backspace : KeyAction()
    object Shift : KeyAction()
    object SwitchLayer : KeyAction()
    object SwitchLanguage : KeyAction()
    object Enter : KeyAction()
    object Space : KeyAction()
}

data class KeyModel(
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val weight: Float = 0.10f,
    val isFunctional: Boolean = false,
    val action: KeyAction = KeyAction.CommitChar(primaryLabel)
)

data class KeyRow(
    val keys: List<KeyModel>,
    val sideInsetFraction: Float = 0f
)

data class KeyboardLayerDef(
    val rows: List<KeyRow>
)

data class ThemePalette(
    val backgroundColor: Long,
    val keycapColor: Long,
    val functionalKeycapColor: Long,
    val primaryTextColor: Long,
    val secondaryTextColor: Long,
    val borderColor: Long,
    val pressedKeycapColor: Long = 0xFFDCE0E8,
    val pressedFunctionalKeycapColor: Long = 0xFFC4C9D4,
    val accentColor: Long = 0xFF0072DE
) {
    val keyBorderColor: Long get() = borderColor
}

object KeyboardLayoutDefinition {

    val OneUiLightTheme = ThemePalette(
        backgroundColor = 0xFFE8EAED,
        keycapColor = 0xFFFFFFFF,
        functionalKeycapColor = 0xFFD6D9E0,
        primaryTextColor = 0xFF252528,
        secondaryTextColor = 0xFF7A7E87,
        borderColor = 0xFFC4C7CC,
        pressedKeycapColor = 0xFFDCE0E8,
        pressedFunctionalKeycapColor = 0xFFC4C9D4,
        accentColor = 0xFF0072DE
    )

    val OneUiDarkTheme = ThemePalette(
        backgroundColor = 0xFF1B1B1F,
        keycapColor = 0xFF2C2C32,
        functionalKeycapColor = 0xFF222227,
        primaryTextColor = 0xFFF2F2F7,
        secondaryTextColor = 0xFF9494A0,
        borderColor = 0xFF222227,
        pressedKeycapColor = 0xFF3E3E47,
        pressedFunctionalKeycapColor = 0xFF34343D,
        accentColor = 0xFF387AFF
    )

    private data class LayerCacheKey(
        val layer: KeyboardLayer,
        val isShifted: Boolean,
        val language: Int,
        val imeActionLabel: String
    )

    private val layerCache = java.util.concurrent.ConcurrentHashMap<LayerCacheKey, KeyboardLayerDef>()

    fun getLayer(
        layer: KeyboardLayer,
        isShifted: Boolean = false,
        language: Int = 1,
        imeActionLabel: String = "↵"
    ): KeyboardLayerDef {
        val cacheKey = LayerCacheKey(layer, isShifted, language, imeActionLabel)
        return layerCache.computeIfAbsent(cacheKey) {
            buildLayer(layer, isShifted, language, imeActionLabel)
        }
    }

    private fun buildLayer(
        layer: KeyboardLayer,
        isShifted: Boolean = false,
        language: Int = 1,
        imeActionLabel: String = "↵"
    ): KeyboardLayerDef {
        val spaceLabel = if (language == 1) "Tiếng Việt" else "English (US)"

        return when (layer) {
            KeyboardLayer.QWERTY -> {
                val row1Keys = (1..9).map { it.toString() } + "0"
                val row1 = KeyRow(
                    row1Keys.map { num ->
                        KeyModel(primaryLabel = num, weight = 0.10f, action = KeyAction.CommitChar(num))
                    }
                )

                val row2Letters = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
                val row2Symbols = listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]")
                val row2 = KeyRow(
                    row2Letters.zip(row2Symbols).map { (ch, sym) ->
                        val label = if (isShifted) ch.uppercase() else ch
                        KeyModel(
                            primaryLabel = label,
                            secondaryLabel = sym,
                            weight = 0.10f,
                            action = KeyAction.CommitChar(label)
                        )
                    }
                )

                val row3Letters = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
                val row3Symbols = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(")
                val row3 = KeyRow(
                    keys = row3Letters.zip(row3Symbols).map { (ch, sym) ->
                        val label = if (isShifted) ch.uppercase() else ch
                        KeyModel(
                            primaryLabel = label,
                            secondaryLabel = sym,
                            weight = 0.10f,
                            action = KeyAction.CommitChar(label)
                        )
                    },
                    sideInsetFraction = 0.05f
                )

                val row4Letters = listOf("z", "x", "c", "v", "b", "n", "m")
                val row4Symbols = listOf("-", "'", "\"", ":", ";", ",", "?")
                val row4LetterKeys = row4Letters.zip(row4Symbols).map { (ch, sym) ->
                    val label = if (isShifted) ch.uppercase() else ch
                    KeyModel(
                        primaryLabel = label,
                        secondaryLabel = sym,
                        weight = 0.10f,
                        action = KeyAction.CommitChar(label)
                    )
                }
                val row4 = KeyRow(
                    keys = listOf(
                        KeyModel(primaryLabel = "⇧", weight = 0.15f, isFunctional = true, action = KeyAction.Shift)
                    ) + row4LetterKeys + listOf(
                        KeyModel(primaryLabel = "⌫", weight = 0.15f, isFunctional = true, action = KeyAction.Backspace)
                    )
                )

                val row5 = buildBottomRow(returnToQwerty = false, language = language, imeActionLabel = imeActionLabel)

                KeyboardLayerDef(listOf(row1, row2, row3, row4, row5))
            }

            KeyboardLayer.SYMBOLS_1 -> {
                val row1Keys = (1..9).map { it.toString() } + "0"
                val row1 = KeyRow(
                    row1Keys.map { num ->
                        KeyModel(primaryLabel = num, weight = 0.10f, action = KeyAction.CommitChar(num))
                    }
                )

                val row2Symbols = listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]")
                val row2 = KeyRow(
                    row2Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    }
                )

                val row3Symbols = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
                val row3 = KeyRow(
                    row3Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    }
                )

                val row4Symbols = listOf("-", "'", "\"", ":", ";", ",", "?")
                val row4 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = "1/2", weight = 0.15f, isFunctional = true, action = KeyAction.SwitchLayer)
                    ) + row4Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    } + listOf(
                        KeyModel(primaryLabel = "⌫", weight = 0.15f, isFunctional = true, action = KeyAction.Backspace)
                    )
                )

                val row5 = buildBottomRow(returnToQwerty = true, language = language, imeActionLabel = imeActionLabel)

                KeyboardLayerDef(listOf(row1, row2, row3, row4, row5))
            }

            KeyboardLayer.SYMBOLS_2 -> {
                val row1Keys = (1..9).map { it.toString() } + "0"
                val row1 = KeyRow(
                    row1Keys.map { num ->
                        KeyModel(primaryLabel = num, weight = 0.10f, action = KeyAction.CommitChar(num))
                    }
                )

                val row2Symbols = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ")
                val row2 = KeyRow(
                    row2Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    }
                )

                val row3Symbols = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
                val row3 = KeyRow(
                    row3Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    }
                )

                val row4Symbols = listOf("%", "©", "®", "™", "✓", "[", "]")
                val row4 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = "2/2", weight = 0.15f, isFunctional = true, action = KeyAction.SwitchLayer)
                    ) + row4Symbols.map { sym ->
                        KeyModel(primaryLabel = sym, weight = 0.10f, action = KeyAction.CommitChar(sym))
                    } + listOf(
                        KeyModel(primaryLabel = "⌫", weight = 0.15f, isFunctional = true, action = KeyAction.Backspace)
                    )
                )

                val row5 = buildBottomRow(returnToQwerty = true, language = language, imeActionLabel = imeActionLabel)

                KeyboardLayerDef(listOf(row1, row2, row3, row4, row5))
            }

            KeyboardLayer.NUMPAD -> {
                val row1 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = "1", weight = 0.26f, action = KeyAction.CommitChar("1")),
                        KeyModel(primaryLabel = "2", weight = 0.26f, action = KeyAction.CommitChar("2")),
                        KeyModel(primaryLabel = "3", weight = 0.26f, action = KeyAction.CommitChar("3")),
                        KeyModel(primaryLabel = "-", weight = 0.22f, isFunctional = true, action = KeyAction.CommitChar("-"))
                    )
                )
                val row2 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = "4", weight = 0.26f, action = KeyAction.CommitChar("4")),
                        KeyModel(primaryLabel = "5", weight = 0.26f, action = KeyAction.CommitChar("5")),
                        KeyModel(primaryLabel = "6", weight = 0.26f, action = KeyAction.CommitChar("6")),
                        KeyModel(primaryLabel = "␣", weight = 0.22f, isFunctional = true, action = KeyAction.Space)
                    )
                )
                val row3 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = "7", weight = 0.26f, action = KeyAction.CommitChar("7")),
                        KeyModel(primaryLabel = "8", weight = 0.26f, action = KeyAction.CommitChar("8")),
                        KeyModel(primaryLabel = "9", weight = 0.26f, action = KeyAction.CommitChar("9")),
                        KeyModel(primaryLabel = "⌫", weight = 0.22f, isFunctional = true, action = KeyAction.Backspace)
                    )
                )
                val row4 = KeyRow(
                    listOf(
                        KeyModel(primaryLabel = ",", weight = 0.26f, isFunctional = true, action = KeyAction.CommitChar(",")),
                        KeyModel(primaryLabel = "0", weight = 0.26f, action = KeyAction.CommitChar("0")),
                        KeyModel(primaryLabel = ".", weight = 0.26f, isFunctional = true, action = KeyAction.CommitChar(".")),
                        KeyModel(primaryLabel = imeActionLabel, weight = 0.22f, isFunctional = true, action = KeyAction.Enter)
                    )
                )
                KeyboardLayerDef(listOf(row1, row2, row3, row4))
            }
        }
    }

    fun buildBottomRow(returnToQwerty: Boolean, language: Int, imeActionLabel: String): KeyRow {
        val spaceLabel = if (language == 1) "Tiếng Việt" else "English (US)"
        return if (!returnToQwerty) {
            KeyRow(
                keys = listOf(
                    KeyModel(primaryLabel = "!#1", weight = 0.14f, isFunctional = true, action = KeyAction.SwitchLayer),
                    KeyModel(
                        primaryLabel = "🌐",
                        secondaryLabel = if (language == 1) "VI" else "EN",
                        weight = 0.10f,
                        isFunctional = true,
                        action = KeyAction.SwitchLanguage
                    ),
                    KeyModel(primaryLabel = ",", weight = 0.09f, action = KeyAction.CommitChar(",")),
                    KeyModel(primaryLabel = spaceLabel, weight = 0.42f, action = KeyAction.Space),
                    KeyModel(primaryLabel = ".", weight = 0.09f, action = KeyAction.CommitChar(".")),
                    KeyModel(primaryLabel = imeActionLabel, weight = 0.16f, isFunctional = true, action = KeyAction.Enter)
                )
            )
        } else {
            KeyRow(
                keys = listOf(
                    KeyModel(primaryLabel = "ABC", weight = 0.15f, isFunctional = true, action = KeyAction.SwitchLayer),
                    KeyModel(primaryLabel = ",", weight = 0.10f, action = KeyAction.CommitChar(",")),
                    KeyModel(primaryLabel = spaceLabel, weight = 0.50f, action = KeyAction.Space),
                    KeyModel(primaryLabel = ".", weight = 0.10f, action = KeyAction.CommitChar(".")),
                    KeyModel(primaryLabel = imeActionLabel, weight = 0.15f, isFunctional = true, action = KeyAction.Enter)
                )
            )
        }
    }
}
