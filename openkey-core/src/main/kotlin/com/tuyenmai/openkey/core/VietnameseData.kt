package com.tuyenmai.openkey.core

object HookCodeState {
    const val DO_NOTHING = 0
    const val WILL_PROCESS = 1
    const val BREAK_WORD = 2
    const val RESTORE = 3
    const val RESTORE_AND_START_NEW_SESSION = 4
}

data class HookSnapshot(
    val code: Int = HookCodeState.DO_NOTHING,
    val backspaceCount: Int = 0,
    val newCharCount: Int = 0,
    val extCode: Int = 0,
    val shouldSendControlKey: Boolean = false,
    val utf16Chars: List<UShort> = emptyList(),
    val languageSwitchRequest: Int = -1
) {
    val decodedString: String
        get() = utf16Chars.map { it.toInt().toChar() }.joinToString("")
}

data class OKWordContext(
    val composedWord: String = "",
    val rawKeystrokes: String = "",
    val charCount: Int = 0,
    val spaceCount: Int = 0,
    val isWrongSpelling: Boolean = false,
    val isBreakKey: Boolean = false
)

// Virtual Keycodes
const val KEY_ESC: UShort = 53u
const val KEY_DELETE: UShort = 51u
const val KEY_TAB: UShort = 48u
const val KEY_ENTER: UShort = 76u
const val KEY_RETURN: UShort = 36u
const val KEY_SPACE: UShort = 49u
const val KEY_LEFT: UShort = 123u
const val KEY_RIGHT: UShort = 124u
const val KEY_DOWN: UShort = 125u
const val KEY_UP: UShort = 126u

const val KEY_EMPTY: UShort = 256u
const val KEY_A: UShort = 0u
const val KEY_B: UShort = 11u
const val KEY_C: UShort = 8u
const val KEY_D: UShort = 2u
const val KEY_E: UShort = 14u
const val KEY_F: UShort = 3u
const val KEY_G: UShort = 5u
const val KEY_H: UShort = 4u
const val KEY_I: UShort = 34u
const val KEY_J: UShort = 38u
const val KEY_K: UShort = 40u
const val KEY_L: UShort = 37u
const val KEY_M: UShort = 46u
const val KEY_N: UShort = 45u
const val KEY_O: UShort = 31u
const val KEY_P: UShort = 35u
const val KEY_Q: UShort = 12u
const val KEY_R: UShort = 15u
const val KEY_S: UShort = 1u
const val KEY_T: UShort = 17u
const val KEY_U: UShort = 32u
const val KEY_V: UShort = 9u
const val KEY_W: UShort = 13u
const val KEY_X: UShort = 7u
const val KEY_Y: UShort = 16u
const val KEY_Z: UShort = 6u

const val KEY_1: UShort = 18u
const val KEY_2: UShort = 19u
const val KEY_3: UShort = 20u
const val KEY_4: UShort = 21u
const val KEY_5: UShort = 23u
const val KEY_6: UShort = 22u
const val KEY_7: UShort = 26u
const val KEY_8: UShort = 28u
const val KEY_9: UShort = 25u
const val KEY_0: UShort = 29u

const val KEY_LEFT_BRACKET: UShort = 33u
const val KEY_RIGHT_BRACKET: UShort = 30u

const val KEY_LEFT_SHIFT: UShort = 57u
const val KEY_RIGHT_SHIFT: UShort = 60u
const val KEY_DOT: UShort = 47u

const val KEY_BACKQUOTE: UShort = 50u
const val KEY_MINUS: UShort = 27u
const val KEY_EQUALS: UShort = 24u
const val KEY_BACK_SLASH: UShort = 42u
const val KEY_SEMICOLON: UShort = 41u
const val KEY_QUOTE: UShort = 39u
const val KEY_COMMA: UShort = 43u
const val KEY_SLASH: UShort = 44u

// Internal Engine Bitmasks
const val CAPS_MASK: UInt = 0x10000u
const val TONE_MASK: UInt = 0x20000u
const val TONEW_MASK: UInt = 0x40000u

const val MARK1_MASK: UInt = 0x80000u
const val MARK2_MASK: UInt = 0x100000u
const val MARK3_MASK: UInt = 0x200000u
const val MARK4_MASK: UInt = 0x400000u
const val MARK5_MASK: UInt = 0x800000u
const val MARK_MASK: UInt = 0xF80000u

const val CHAR_MASK: UInt = 0xFFFFu
const val STANDALONE_MASK: UInt = 0x1000000u
const val CHAR_CODE_MASK: UInt = 0x2000000u
const val AUTO_TONE_MASK: UInt = 0x4000000u
const val PURE_CHARACTER_MASK: UInt = 0x80000000u

const val END_CONSONANT_MASK: UShort = 0x4000u
const val CONSONANT_ALLOW_MASK: UShort = 0x8000u

fun isConsonant(keyCode: UShort): Boolean {
    return !(keyCode == KEY_A || keyCode == KEY_E || keyCode == KEY_U || keyCode == KEY_Y || keyCode == KEY_I || keyCode == KEY_O)
}

fun isNumberKey(code: UShort): Boolean {
    return code == KEY_1 || code == KEY_2 || code == KEY_3 || code == KEY_4 || code == KEY_5 ||
            code == KEY_6 || code == KEY_7 || code == KEY_8 || code == KEY_9 || code == KEY_0
}

object VietnameseData {
    const val KEY_ESC = com.tuyenmai.openkey.core.KEY_ESC
    const val KEY_DELETE = com.tuyenmai.openkey.core.KEY_DELETE
    const val KEY_TAB = com.tuyenmai.openkey.core.KEY_TAB
    const val KEY_ENTER = com.tuyenmai.openkey.core.KEY_ENTER
    const val KEY_RETURN = com.tuyenmai.openkey.core.KEY_RETURN
    const val KEY_SPACE = com.tuyenmai.openkey.core.KEY_SPACE
    const val KEY_LEFT = com.tuyenmai.openkey.core.KEY_LEFT
    const val KEY_RIGHT = com.tuyenmai.openkey.core.KEY_RIGHT
    const val KEY_DOWN = com.tuyenmai.openkey.core.KEY_DOWN
    const val KEY_UP = com.tuyenmai.openkey.core.KEY_UP
    const val KEY_EMPTY = com.tuyenmai.openkey.core.KEY_EMPTY
    const val KEY_A = com.tuyenmai.openkey.core.KEY_A
    const val KEY_B = com.tuyenmai.openkey.core.KEY_B
    const val KEY_C = com.tuyenmai.openkey.core.KEY_C
    const val KEY_D = com.tuyenmai.openkey.core.KEY_D
    const val KEY_E = com.tuyenmai.openkey.core.KEY_E
    const val KEY_F = com.tuyenmai.openkey.core.KEY_F
    const val KEY_G = com.tuyenmai.openkey.core.KEY_G
    const val KEY_H = com.tuyenmai.openkey.core.KEY_H
    const val KEY_I = com.tuyenmai.openkey.core.KEY_I
    const val KEY_J = com.tuyenmai.openkey.core.KEY_J
    const val KEY_K = com.tuyenmai.openkey.core.KEY_K
    const val KEY_L = com.tuyenmai.openkey.core.KEY_L
    const val KEY_M = com.tuyenmai.openkey.core.KEY_M
    const val KEY_N = com.tuyenmai.openkey.core.KEY_N
    const val KEY_O = com.tuyenmai.openkey.core.KEY_O
    const val KEY_P = com.tuyenmai.openkey.core.KEY_P
    const val KEY_Q = com.tuyenmai.openkey.core.KEY_Q
    const val KEY_R = com.tuyenmai.openkey.core.KEY_R
    const val KEY_S = com.tuyenmai.openkey.core.KEY_S
    const val KEY_T = com.tuyenmai.openkey.core.KEY_T
    const val KEY_U = com.tuyenmai.openkey.core.KEY_U
    const val KEY_V = com.tuyenmai.openkey.core.KEY_V
    const val KEY_W = com.tuyenmai.openkey.core.KEY_W
    const val KEY_X = com.tuyenmai.openkey.core.KEY_X
    const val KEY_Y = com.tuyenmai.openkey.core.KEY_Y
    const val KEY_Z = com.tuyenmai.openkey.core.KEY_Z
    const val KEY_1 = com.tuyenmai.openkey.core.KEY_1
    const val KEY_2 = com.tuyenmai.openkey.core.KEY_2
    const val KEY_3 = com.tuyenmai.openkey.core.KEY_3
    const val KEY_4 = com.tuyenmai.openkey.core.KEY_4
    const val KEY_5 = com.tuyenmai.openkey.core.KEY_5
    const val KEY_6 = com.tuyenmai.openkey.core.KEY_6
    const val KEY_7 = com.tuyenmai.openkey.core.KEY_7
    const val KEY_8 = com.tuyenmai.openkey.core.KEY_8
    const val KEY_9 = com.tuyenmai.openkey.core.KEY_9
    const val KEY_0 = com.tuyenmai.openkey.core.KEY_0
    const val KEY_LEFT_BRACKET = com.tuyenmai.openkey.core.KEY_LEFT_BRACKET
    const val KEY_RIGHT_BRACKET = com.tuyenmai.openkey.core.KEY_RIGHT_BRACKET
    const val KEY_LEFT_SHIFT = com.tuyenmai.openkey.core.KEY_LEFT_SHIFT
    const val KEY_RIGHT_SHIFT = com.tuyenmai.openkey.core.KEY_RIGHT_SHIFT
    const val KEY_DOT = com.tuyenmai.openkey.core.KEY_DOT
    const val KEY_BACKQUOTE = com.tuyenmai.openkey.core.KEY_BACKQUOTE
    const val KEY_MINUS = com.tuyenmai.openkey.core.KEY_MINUS
    const val KEY_EQUALS = com.tuyenmai.openkey.core.KEY_EQUALS
    const val KEY_BACK_SLASH = com.tuyenmai.openkey.core.KEY_BACK_SLASH
    const val KEY_SEMICOLON = com.tuyenmai.openkey.core.KEY_SEMICOLON
    const val KEY_QUOTE = com.tuyenmai.openkey.core.KEY_QUOTE
    const val KEY_COMMA = com.tuyenmai.openkey.core.KEY_COMMA
    const val KEY_SLASH = com.tuyenmai.openkey.core.KEY_SLASH

    const val CAPS_MASK = com.tuyenmai.openkey.core.CAPS_MASK
    const val TONE_MASK = com.tuyenmai.openkey.core.TONE_MASK
    const val TONEW_MASK = com.tuyenmai.openkey.core.TONEW_MASK
    const val MARK1_MASK = com.tuyenmai.openkey.core.MARK1_MASK
    const val MARK2_MASK = com.tuyenmai.openkey.core.MARK2_MASK
    const val MARK3_MASK = com.tuyenmai.openkey.core.MARK3_MASK
    const val MARK4_MASK = com.tuyenmai.openkey.core.MARK4_MASK
    const val MARK5_MASK = com.tuyenmai.openkey.core.MARK5_MASK
    const val MARK_MASK = com.tuyenmai.openkey.core.MARK_MASK
    const val CHAR_MASK = com.tuyenmai.openkey.core.CHAR_MASK
    const val STANDALONE_MASK = com.tuyenmai.openkey.core.STANDALONE_MASK
    const val CHAR_CODE_MASK = com.tuyenmai.openkey.core.CHAR_CODE_MASK
    const val AUTO_TONE_MASK = com.tuyenmai.openkey.core.AUTO_TONE_MASK
    const val PURE_CHARACTER_MASK = com.tuyenmai.openkey.core.PURE_CHARACTER_MASK
    const val END_CONSONANT_MASK = com.tuyenmai.openkey.core.END_CONSONANT_MASK
    const val CONSONANT_ALLOW_MASK = com.tuyenmai.openkey.core.CONSONANT_ALLOW_MASK

    fun isConsonant(keyCode: UShort): Boolean = com.tuyenmai.openkey.core.isConsonant(keyCode)
    fun isNumberKey(code: UShort): Boolean = com.tuyenmai.openkey.core.isNumberKey(code)

    val douKey: List<UShortArray> = listOf(
        ushortArrayOf(KEY_A, 0x00E2u), // a -> â
        ushortArrayOf(KEY_E, 0x00EAu)  // e -> ê
    )

    val _vowel: Map<UShort, List<UShortArray>> = mapOf(
        KEY_A to listOf(
            ushortArrayOf(KEY_A, KEY_N, KEY_G), ushortArrayOf(KEY_A, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_A, KEY_N), ushortArrayOf(KEY_A, KEY_M), ushortArrayOf(KEY_A, KEY_U), ushortArrayOf(KEY_A, KEY_Y),
            ushortArrayOf(KEY_A, KEY_T), ushortArrayOf(KEY_A, KEY_P), ushortArrayOf(KEY_A), ushortArrayOf(KEY_A, KEY_C)
        ),
        KEY_O to listOf(
            ushortArrayOf(KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_O, KEY_N), ushortArrayOf(KEY_O, KEY_M), ushortArrayOf(KEY_O, KEY_I),
            ushortArrayOf(KEY_O, KEY_C), ushortArrayOf(KEY_O, KEY_T), ushortArrayOf(KEY_O, KEY_P), ushortArrayOf(KEY_O)
        ),
        KEY_E to listOf(
            ushortArrayOf(KEY_E, KEY_N, KEY_H), ushortArrayOf(KEY_E, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_N, KEY_G), ushortArrayOf(KEY_E, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_C, KEY_H), ushortArrayOf(KEY_E, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_C), ushortArrayOf(KEY_E, KEY_T), ushortArrayOf(KEY_E, KEY_Y), ushortArrayOf(KEY_E, KEY_U),
            ushortArrayOf(KEY_E, KEY_P), ushortArrayOf(KEY_E, KEY_C), ushortArrayOf(KEY_E, KEY_N), ushortArrayOf(KEY_E, KEY_M), ushortArrayOf(KEY_E)
        ),
        KEY_W to listOf(
            ushortArrayOf(KEY_O, KEY_N),
            ushortArrayOf(KEY_U, KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_U, KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_U, KEY_O, KEY_N), ushortArrayOf(KEY_U, KEY_O, KEY_I), ushortArrayOf(KEY_U, KEY_O, KEY_C),
            ushortArrayOf(KEY_O, KEY_I), ushortArrayOf(KEY_O, KEY_P), ushortArrayOf(KEY_O, KEY_M), ushortArrayOf(KEY_O, KEY_A), ushortArrayOf(KEY_O, KEY_T),
            ushortArrayOf(KEY_U, KEY_N, KEY_G), ushortArrayOf(KEY_U, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_A, KEY_N, KEY_G), ushortArrayOf(KEY_A, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_U, KEY_N), ushortArrayOf(KEY_U, KEY_M), ushortArrayOf(KEY_U, KEY_C), ushortArrayOf(KEY_U, KEY_A),
            ushortArrayOf(KEY_U, KEY_I), ushortArrayOf(KEY_U, KEY_T), ushortArrayOf(KEY_U), ushortArrayOf(KEY_A, KEY_P),
            ushortArrayOf(KEY_A, KEY_T), ushortArrayOf(KEY_A, KEY_M), ushortArrayOf(KEY_A, KEY_N), ushortArrayOf(KEY_A), ushortArrayOf(KEY_A, KEY_C),
            ushortArrayOf(KEY_A, KEY_C, KEY_H), ushortArrayOf(KEY_A, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_O), ushortArrayOf(KEY_U, KEY_U)
        )
    )

    val _vowelCombine: Map<UShort, List<UIntArray>> = mapOf(
        KEY_A to listOf(
            uintArrayOf(0u, KEY_A.toUInt(), KEY_I.toUInt()), uintArrayOf(0u, KEY_A.toUInt(), KEY_O.toUInt()),
            uintArrayOf(0u, KEY_A.toUInt(), KEY_U.toUInt()), uintArrayOf(0u, KEY_A.toUInt() or TONE_MASK, KEY_U.toUInt()),
            uintArrayOf(0u, KEY_A.toUInt(), KEY_Y.toUInt()), uintArrayOf(0u, KEY_A.toUInt() or TONE_MASK, KEY_Y.toUInt())
        ),
        KEY_E to listOf(
            uintArrayOf(0u, KEY_E.toUInt(), KEY_O.toUInt()), uintArrayOf(0u, KEY_E.toUInt() or TONE_MASK, KEY_U.toUInt())
        ),
        KEY_I to listOf(
            uintArrayOf(1u, KEY_I.toUInt(), KEY_E.toUInt() or TONE_MASK, KEY_U.toUInt()), uintArrayOf(0u, KEY_I.toUInt(), KEY_A.toUInt()),
            uintArrayOf(1u, KEY_I.toUInt(), KEY_E.toUInt() or TONE_MASK), uintArrayOf(0u, KEY_I.toUInt(), KEY_U.toUInt())
        ),
        KEY_O to listOf(
            uintArrayOf(0u, KEY_O.toUInt(), KEY_A.toUInt(), KEY_I.toUInt()), uintArrayOf(0u, KEY_O.toUInt(), KEY_A.toUInt(), KEY_O.toUInt()),
            uintArrayOf(0u, KEY_O.toUInt(), KEY_A.toUInt(), KEY_Y.toUInt()), uintArrayOf(0u, KEY_O.toUInt(), KEY_E.toUInt(), KEY_O.toUInt()),
            uintArrayOf(1u, KEY_O.toUInt(), KEY_A.toUInt()), uintArrayOf(1u, KEY_O.toUInt(), KEY_A.toUInt() or TONEW_MASK),
            uintArrayOf(1u, KEY_O.toUInt(), KEY_E.toUInt()), uintArrayOf(0u, KEY_O.toUInt(), KEY_I.toUInt()),
            uintArrayOf(0u, KEY_O.toUInt() or TONE_MASK, KEY_I.toUInt()), uintArrayOf(0u, KEY_O.toUInt() or TONEW_MASK, KEY_I.toUInt()),
            uintArrayOf(1u, KEY_O.toUInt(), KEY_O.toUInt()), uintArrayOf(1u, KEY_O.toUInt() or TONE_MASK, KEY_O.toUInt() or TONE_MASK)
        ),
        KEY_U to listOf(
            uintArrayOf(0u, KEY_U.toUInt(), KEY_Y.toUInt(), KEY_U.toUInt()), uintArrayOf(1u, KEY_U.toUInt(), KEY_Y.toUInt(), KEY_E.toUInt() or TONE_MASK),
            uintArrayOf(0u, KEY_U.toUInt(), KEY_Y.toUInt(), KEY_A.toUInt()), uintArrayOf(0u, KEY_U.toUInt() or TONEW_MASK, KEY_O.toUInt() or TONEW_MASK, KEY_U.toUInt()),
            uintArrayOf(0u, KEY_U.toUInt() or TONEW_MASK, KEY_O.toUInt() or TONEW_MASK, KEY_I.toUInt()), uintArrayOf(0u, KEY_U.toUInt(), KEY_O.toUInt() or TONE_MASK, KEY_I.toUInt()),
            uintArrayOf(0u, KEY_U.toUInt(), KEY_A.toUInt() or TONE_MASK, KEY_Y.toUInt()), uintArrayOf(1u, KEY_U.toUInt(), KEY_A.toUInt(), KEY_O.toUInt()),
            uintArrayOf(1u, KEY_U.toUInt(), KEY_A.toUInt()), uintArrayOf(1u, KEY_U.toUInt(), KEY_A.toUInt() or TONEW_MASK),
            uintArrayOf(1u, KEY_U.toUInt(), KEY_A.toUInt() or TONE_MASK), uintArrayOf(0u, KEY_U.toUInt() or TONEW_MASK, KEY_A.toUInt()),
            uintArrayOf(1u, KEY_U.toUInt(), KEY_E.toUInt() or TONE_MASK), uintArrayOf(0u, KEY_U.toUInt(), KEY_I.toUInt()),
            uintArrayOf(0u, KEY_U.toUInt() or TONEW_MASK, KEY_I.toUInt()), uintArrayOf(1u, KEY_U.toUInt(), KEY_O.toUInt()),
            uintArrayOf(1u, KEY_U.toUInt(), KEY_O.toUInt() or TONE_MASK), uintArrayOf(0u, KEY_U.toUInt(), KEY_O.toUInt() or TONEW_MASK),
            uintArrayOf(1u, KEY_U.toUInt() or TONEW_MASK, KEY_O.toUInt() or TONEW_MASK), uintArrayOf(0u, KEY_U.toUInt() or TONEW_MASK, KEY_U.toUInt()),
            uintArrayOf(1u, KEY_U.toUInt(), KEY_Y.toUInt())
        ),
        KEY_Y to listOf(
            uintArrayOf(0u, KEY_Y.toUInt(), KEY_E.toUInt() or TONE_MASK, KEY_U.toUInt()),
            uintArrayOf(1u, KEY_Y.toUInt(), KEY_E.toUInt() or TONE_MASK)
        )
    )

    val _consonantD: List<UShortArray> = listOf(
        ushortArrayOf(KEY_D, KEY_E, KEY_N, KEY_H), ushortArrayOf(KEY_D, KEY_E, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_E, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_E, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_E, KEY_C, KEY_H), ushortArrayOf(KEY_D, KEY_E, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_E, KEY_N), ushortArrayOf(KEY_D, KEY_E, KEY_C), ushortArrayOf(KEY_D, KEY_E, KEY_M), ushortArrayOf(KEY_D, KEY_E),
        ushortArrayOf(KEY_D, KEY_E, KEY_T), ushortArrayOf(KEY_D, KEY_E, KEY_U), ushortArrayOf(KEY_D, KEY_E, KEY_O), ushortArrayOf(KEY_D, KEY_E, KEY_P),

        ushortArrayOf(KEY_D, KEY_U, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_U, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_U, KEY_N), ushortArrayOf(KEY_D, KEY_U, KEY_M), ushortArrayOf(KEY_D, KEY_U, KEY_C), ushortArrayOf(KEY_D, KEY_U, KEY_O), ushortArrayOf(KEY_D, KEY_U, KEY_A),
        ushortArrayOf(KEY_D, KEY_U, KEY_O, KEY_I), ushortArrayOf(KEY_D, KEY_U, KEY_O, KEY_C), ushortArrayOf(KEY_D, KEY_U, KEY_O, KEY_N),
        ushortArrayOf(KEY_D, KEY_U, KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_U, KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_U), ushortArrayOf(KEY_D, KEY_U, KEY_P), ushortArrayOf(KEY_D, KEY_U, KEY_T), ushortArrayOf(KEY_D, KEY_U, KEY_I),

        ushortArrayOf(KEY_D, KEY_I, KEY_C, KEY_H), ushortArrayOf(KEY_D, KEY_I, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_I, KEY_C), ushortArrayOf(KEY_D, KEY_I, KEY_N, KEY_H), ushortArrayOf(KEY_D, KEY_I, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_I, KEY_N), ushortArrayOf(KEY_D, KEY_I), ushortArrayOf(KEY_D, KEY_I, KEY_A), ushortArrayOf(KEY_D, KEY_I, KEY_E),
        ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_C), ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_U), ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_N),
        ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_M), ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_P), ushortArrayOf(KEY_D, KEY_I, KEY_E, KEY_T),

        ushortArrayOf(KEY_D, KEY_O), ushortArrayOf(KEY_D, KEY_O, KEY_A), ushortArrayOf(KEY_D, KEY_O, KEY_A, KEY_N),
        ushortArrayOf(KEY_D, KEY_O, KEY_A, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_O, KEY_A, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_O, KEY_A, KEY_N, KEY_H), ushortArrayOf(KEY_D, KEY_O, KEY_A, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_O, KEY_A, KEY_M), ushortArrayOf(KEY_D, KEY_O, KEY_E), ushortArrayOf(KEY_D, KEY_O, KEY_I), ushortArrayOf(KEY_D, KEY_O, KEY_P),
        ushortArrayOf(KEY_D, KEY_O, KEY_C), ushortArrayOf(KEY_D, KEY_O, KEY_N), ushortArrayOf(KEY_D, KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_O, KEY_M), ushortArrayOf(KEY_D, KEY_O, KEY_T),

        ushortArrayOf(KEY_D, KEY_A), ushortArrayOf(KEY_D, KEY_A, KEY_T), ushortArrayOf(KEY_D, KEY_A, KEY_Y), ushortArrayOf(KEY_D, KEY_A, KEY_U), ushortArrayOf(KEY_D, KEY_A, KEY_I),
        ushortArrayOf(KEY_D, KEY_A, KEY_O), ushortArrayOf(KEY_D, KEY_A, KEY_P), ushortArrayOf(KEY_D, KEY_A, KEY_C),
        ushortArrayOf(KEY_D, KEY_A, KEY_C, KEY_H), ushortArrayOf(KEY_D, KEY_A, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_A, KEY_N), ushortArrayOf(KEY_D, KEY_A, KEY_N, KEY_H), ushortArrayOf(KEY_D, KEY_A, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_A, KEY_N, KEY_G), ushortArrayOf(KEY_D, KEY_A, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
        ushortArrayOf(KEY_D, KEY_A, KEY_M), ushortArrayOf(KEY_D)
    )

    val _vowelForMark: Map<UShort, List<UShortArray>> = mapOf(
        KEY_A to listOf(
            ushortArrayOf(KEY_A, KEY_N, KEY_G), ushortArrayOf(KEY_A, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_A, KEY_N), ushortArrayOf(KEY_A, KEY_N, KEY_H), ushortArrayOf(KEY_A, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_A, KEY_M), ushortArrayOf(KEY_A, KEY_U), ushortArrayOf(KEY_A, KEY_Y), ushortArrayOf(KEY_A, KEY_T),
            ushortArrayOf(KEY_A, KEY_P), ushortArrayOf(KEY_A), ushortArrayOf(KEY_A, KEY_C), ushortArrayOf(KEY_A, KEY_I), ushortArrayOf(KEY_A, KEY_O),
            ushortArrayOf(KEY_A, KEY_C, KEY_H), ushortArrayOf(KEY_A, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort())
        ),
        KEY_O to listOf(
            ushortArrayOf(KEY_O, KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_O, KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_O, KEY_N, KEY_G), ushortArrayOf(KEY_O, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_O, KEY_O, KEY_N), ushortArrayOf(KEY_O, KEY_O, KEY_C), ushortArrayOf(KEY_O, KEY_O),
            ushortArrayOf(KEY_O, KEY_N), ushortArrayOf(KEY_O, KEY_M), ushortArrayOf(KEY_O, KEY_I), ushortArrayOf(KEY_O, KEY_C),
            ushortArrayOf(KEY_O, KEY_T), ushortArrayOf(KEY_O, KEY_P), ushortArrayOf(KEY_O)
        ),
        KEY_E to listOf(
            ushortArrayOf(KEY_E, KEY_N, KEY_H), ushortArrayOf(KEY_E, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_N, KEY_G), ushortArrayOf(KEY_E, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_C, KEY_H), ushortArrayOf(KEY_E, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_E, KEY_C), ushortArrayOf(KEY_E, KEY_T), ushortArrayOf(KEY_E, KEY_Y), ushortArrayOf(KEY_E, KEY_U),
            ushortArrayOf(KEY_E, KEY_P), ushortArrayOf(KEY_E, KEY_C), ushortArrayOf(KEY_E, KEY_N), ushortArrayOf(KEY_E, KEY_M), ushortArrayOf(KEY_E)
        ),
        KEY_I to listOf(
            ushortArrayOf(KEY_I, KEY_N, KEY_H), ushortArrayOf(KEY_I, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_I, KEY_C, KEY_H), ushortArrayOf(KEY_I, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_I, KEY_N), ushortArrayOf(KEY_I, KEY_T), ushortArrayOf(KEY_I, KEY_U), ushortArrayOf(KEY_I, KEY_U, KEY_P),
            ushortArrayOf(KEY_I, KEY_N), ushortArrayOf(KEY_I, KEY_M), ushortArrayOf(KEY_I, KEY_P), ushortArrayOf(KEY_I, KEY_A),
            ushortArrayOf(KEY_I, KEY_C), ushortArrayOf(KEY_I)
        ),
        KEY_U to listOf(
            ushortArrayOf(KEY_U, KEY_N, KEY_G), ushortArrayOf(KEY_U, (KEY_G.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_U, KEY_I), ushortArrayOf(KEY_U, KEY_O), ushortArrayOf(KEY_U, KEY_Y), ushortArrayOf(KEY_U, KEY_Y, KEY_N),
            ushortArrayOf(KEY_U, KEY_Y, KEY_T), ushortArrayOf(KEY_U, KEY_Y, KEY_P),
            ushortArrayOf(KEY_U, KEY_Y, KEY_C, KEY_H), ushortArrayOf(KEY_U, KEY_Y, (KEY_K.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_U, KEY_Y, KEY_N, KEY_H), ushortArrayOf(KEY_U, KEY_Y, (KEY_H.toInt() or END_CONSONANT_MASK.toInt()).toUShort()),
            ushortArrayOf(KEY_U, KEY_T), ushortArrayOf(KEY_U, KEY_U), ushortArrayOf(KEY_U, KEY_A), ushortArrayOf(KEY_U, KEY_I),
            ushortArrayOf(KEY_U, KEY_C), ushortArrayOf(KEY_U, KEY_N), ushortArrayOf(KEY_U, KEY_M), ushortArrayOf(KEY_U, KEY_P), ushortArrayOf(KEY_U)
        ),
        KEY_Y to listOf(
            ushortArrayOf(KEY_Y)
        )
    )

    val _consonantTable: List<UShortArray> = listOf(
        ushortArrayOf(KEY_N, KEY_G, KEY_H), ushortArrayOf(KEY_P, KEY_H), ushortArrayOf(KEY_T, KEY_H), ushortArrayOf(KEY_T, KEY_R),
        ushortArrayOf(KEY_G, KEY_I), ushortArrayOf(KEY_C, KEY_H), ushortArrayOf(KEY_N, KEY_H), ushortArrayOf(KEY_N, KEY_G),
        ushortArrayOf(KEY_K, KEY_H), ushortArrayOf(KEY_G, KEY_H), ushortArrayOf(KEY_G), ushortArrayOf(KEY_C), ushortArrayOf(KEY_Q), ushortArrayOf(KEY_K),
        ushortArrayOf(KEY_T), ushortArrayOf(KEY_R), ushortArrayOf(KEY_H), ushortArrayOf(KEY_B), ushortArrayOf(KEY_M), ushortArrayOf(KEY_V), ushortArrayOf(KEY_N),
        ushortArrayOf(KEY_L), ushortArrayOf(KEY_X), ushortArrayOf(KEY_P), ushortArrayOf(KEY_S), ushortArrayOf(KEY_D)
    )

    val _endConsonantTable: List<UShortArray> = listOf(
        ushortArrayOf(KEY_T), ushortArrayOf(KEY_P), ushortArrayOf(KEY_C), ushortArrayOf(KEY_N), ushortArrayOf(KEY_M),
        ushortArrayOf(KEY_C, KEY_H), ushortArrayOf(KEY_N, KEY_H), ushortArrayOf(KEY_N, KEY_G)
    )

    val _standaloneWbad: UShortArray = ushortArrayOf(
        KEY_W, KEY_E, KEY_Y, KEY_F, KEY_J, KEY_K, KEY_Z
    )

    val _doubleWAllowed: List<UShortArray> = listOf(
        ushortArrayOf(KEY_T, KEY_R),
        ushortArrayOf(KEY_T, KEY_H),
        ushortArrayOf(KEY_C, KEY_H),
        ushortArrayOf(KEY_N, KEY_H),
        ushortArrayOf(KEY_N, KEY_G),
        ushortArrayOf(KEY_K, KEY_H),
        ushortArrayOf(KEY_G, KEY_I),
        ushortArrayOf(KEY_P, KEY_H),
        ushortArrayOf(KEY_G, KEY_H)
    )

    val _codeTable: Map<UInt, UShortArray> = mapOf(
        // KEY_A: Â, â, Ă, ă, Á, á, À, à, Ả, ả, Ã, ã, Ạ, ạ
        KEY_A.toUInt() to ushortArrayOf(0x00C2u, 0x00E2u, 0x0102u, 0x0103u, 0x00C1u, 0x00E1u, 0x00C0u, 0x00E0u, 0x1EA2u, 0x1EA3u, 0x00C3u, 0x00E3u, 0x1EA0u, 0x1EA1u),
        // KEY_O: Ô, ô, Ơ, ơ, Ó, ó, O, o, Ỏ, ỏ, Õ, õ, Ọ, ọ
        KEY_O.toUInt() to ushortArrayOf(0x00D4u, 0x00F4u, 0x01A0u, 0x01A1u, 0x00D3u, 0x00F3u, 0x00D2u, 0x00F2u, 0x1ECEu, 0x1ECFu, 0x00D5u, 0x00F5u, 0x1ECCu, 0x1ECDu),
        // KEY_U: -, -, Ư, ư, Ú, ú, Ù, ù, Ủ, ủ, Ũ, ũ, Ụ, ụ
        KEY_U.toUInt() to ushortArrayOf(0x0000u, 0x0000u, 0x01AFu, 0x01B0u, 0x00DAu, 0x00FAu, 0x00D9u, 0x00F9u, 0x1EE6u, 0x1EE7u, 0x0168u, 0x0169u, 0x1EE4u, 0x1EE5u),
        // KEY_E: Ê, ê, -, -, É, é, È, è, Ẻ, ẻ, Ẽ, ẽ, Ẹ, ẹ
        KEY_E.toUInt() to ushortArrayOf(0x00CAu, 0x00EAu, 0x0000u, 0x0000u, 0x00C9u, 0x00E9u, 0x00C8u, 0x00E8u, 0x1EBAu, 0x1EBBu, 0x1EBCu, 0x1EBDu, 0x1EB8u, 0x1EB9u),
        // KEY_D: Đ, đ
        KEY_D.toUInt() to ushortArrayOf(0x0110u, 0x0111u),
        // KEY_A | TONE_MASK: Ấ, ấ, Ầ, ầ, Ẩ, ẩ, Ẫ, ẫ, Ậ, ậ
        KEY_A.toUInt() or TONE_MASK to ushortArrayOf(0x1EA4u, 0x1EA5u, 0x1EA6u, 0x1EA7u, 0x1EA8u, 0x1EA9u, 0x1EAAu, 0x1EABu, 0x1EACu, 0x1EADu),
        // KEY_A | TONEW_MASK: Ắ, ắ, Ằ, ằ, Ẳ, ẳ, Ẵ, ẵ, Ặ, ặ
        KEY_A.toUInt() or TONEW_MASK to ushortArrayOf(0x1EAEu, 0x1EAFu, 0x1EB0u, 0x1EB1u, 0x1EB2u, 0x1EB3u, 0x1EB4u, 0x1EB5u, 0x1EB6u, 0x1EB7u),
        // KEY_O | TONE_MASK: Ố, ố, Ồ, ồ, Ổ, ổ, Ỗ, ỗ, Ộ, ộ
        KEY_O.toUInt() or TONE_MASK to ushortArrayOf(0x1ED0u, 0x1ED1u, 0x1ED2u, 0x1ED3u, 0x1ED4u, 0x1ED5u, 0x1ED6u, 0x1ED7u, 0x1ED8u, 0x1ED9u),
        // KEY_O | TONEW_MASK: Ớ, ớ, Ờ, ờ, Ở, ở, Ỡ, ỡ, Ợ, ợ
        KEY_O.toUInt() or TONEW_MASK to ushortArrayOf(0x1EDAu, 0x1EDBu, 0x1EDCu, 0x1EDDu, 0x1EDEu, 0x1EDFu, 0x1EE0u, 0x1EE1u, 0x1EE2u, 0x1EE3u),
        // KEY_U | TONEW_MASK: Ứ, ứ, Ừ, ừ, Ử, ử, Ữ, ữ, Ự, ự
        KEY_U.toUInt() or TONEW_MASK to ushortArrayOf(0x1EE8u, 0x1EE9u, 0x1EEAu, 0x1EEBu, 0x1EECu, 0x1EEDu, 0x1EEEu, 0x1EEFu, 0x1EF0u, 0x1EF1u),
        // KEY_E | TONE_MASK: Ế, ế, Ề, ề, Ể, ể, Ễ, ễ, Ệ, ệ
        KEY_E.toUInt() or TONE_MASK to ushortArrayOf(0x1EBEu, 0x1EBFu, 0x1EC0u, 0x1EC1u, 0x1EC2u, 0x1EC3u, 0x1EC4u, 0x1EC5u, 0x1EC6u, 0x1EC7u),
        // KEY_I: Í, í, Ì, ì, Ỉ, ỉ, Ĩ, ĩ, Ị, ị
        KEY_I.toUInt() to ushortArrayOf(0x00CDu, 0x00EDu, 0x00CCu, 0x00ECu, 0x1EC8u, 0x1EC9u, 0x0128u, 0x0129u, 0x1ECAu, 0x1ECBu),
        // KEY_Y: Ý, ý, Ỳ, ỳ, Ỷ, ỷ, Ỹ, ỹ, Ỵ, ỵ
        KEY_Y.toUInt() to ushortArrayOf(0x00DDu, 0x00FDu, 0x1EF2u, 0x1EF3u, 0x1EF6u, 0x1EF7u, 0x1EF8u, 0x1EF9u, 0x1EF4u, 0x1EF5u)
    )

    val _characterMap: Map<UInt, UInt> = mapOf(
        'a'.code.toUInt() to KEY_A.toUInt(),
        'A'.code.toUInt() to (KEY_A.toUInt() or CAPS_MASK),
        'b'.code.toUInt() to KEY_B.toUInt(),
        'B'.code.toUInt() to (KEY_B.toUInt() or CAPS_MASK),
        'c'.code.toUInt() to KEY_C.toUInt(),
        'C'.code.toUInt() to (KEY_C.toUInt() or CAPS_MASK),
        'd'.code.toUInt() to KEY_D.toUInt(),
        'D'.code.toUInt() to (KEY_D.toUInt() or CAPS_MASK),
        'e'.code.toUInt() to KEY_E.toUInt(),
        'E'.code.toUInt() to (KEY_E.toUInt() or CAPS_MASK),
        'f'.code.toUInt() to KEY_F.toUInt(),
        'F'.code.toUInt() to (KEY_F.toUInt() or CAPS_MASK),
        'g'.code.toUInt() to KEY_G.toUInt(),
        'G'.code.toUInt() to (KEY_G.toUInt() or CAPS_MASK),
        'h'.code.toUInt() to KEY_H.toUInt(),
        'H'.code.toUInt() to (KEY_H.toUInt() or CAPS_MASK),
        'i'.code.toUInt() to KEY_I.toUInt(),
        'I'.code.toUInt() to (KEY_I.toUInt() or CAPS_MASK),
        'j'.code.toUInt() to KEY_J.toUInt(),
        'J'.code.toUInt() to (KEY_J.toUInt() or CAPS_MASK),
        'k'.code.toUInt() to KEY_K.toUInt(),
        'K'.code.toUInt() to (KEY_K.toUInt() or CAPS_MASK),
        'l'.code.toUInt() to KEY_L.toUInt(),
        'L'.code.toUInt() to (KEY_L.toUInt() or CAPS_MASK),
        'm'.code.toUInt() to KEY_M.toUInt(),
        'M'.code.toUInt() to (KEY_M.toUInt() or CAPS_MASK),
        'n'.code.toUInt() to KEY_N.toUInt(),
        'N'.code.toUInt() to (KEY_N.toUInt() or CAPS_MASK),
        'o'.code.toUInt() to KEY_O.toUInt(),
        'O'.code.toUInt() to (KEY_O.toUInt() or CAPS_MASK),
        'p'.code.toUInt() to KEY_P.toUInt(),
        'P'.code.toUInt() to (KEY_P.toUInt() or CAPS_MASK),
        'q'.code.toUInt() to KEY_Q.toUInt(),
        'Q'.code.toUInt() to (KEY_Q.toUInt() or CAPS_MASK),
        'r'.code.toUInt() to KEY_R.toUInt(),
        'R'.code.toUInt() to (KEY_R.toUInt() or CAPS_MASK),
        's'.code.toUInt() to KEY_S.toUInt(),
        'S'.code.toUInt() to (KEY_S.toUInt() or CAPS_MASK),
        't'.code.toUInt() to KEY_T.toUInt(),
        'T'.code.toUInt() to (KEY_T.toUInt() or CAPS_MASK),
        'u'.code.toUInt() to KEY_U.toUInt(),
        'U'.code.toUInt() to (KEY_U.toUInt() or CAPS_MASK),
        'v'.code.toUInt() to KEY_V.toUInt(),
        'V'.code.toUInt() to (KEY_V.toUInt() or CAPS_MASK),
        'w'.code.toUInt() to KEY_W.toUInt(),
        'W'.code.toUInt() to (KEY_W.toUInt() or CAPS_MASK),
        'x'.code.toUInt() to KEY_X.toUInt(),
        'X'.code.toUInt() to (KEY_X.toUInt() or CAPS_MASK),
        'y'.code.toUInt() to KEY_Y.toUInt(),
        'Y'.code.toUInt() to (KEY_Y.toUInt() or CAPS_MASK),
        'z'.code.toUInt() to KEY_Z.toUInt(),
        'Z'.code.toUInt() to (KEY_Z.toUInt() or CAPS_MASK),
        '1'.code.toUInt() to KEY_1.toUInt(),
        '!'.code.toUInt() to (KEY_1.toUInt() or CAPS_MASK),
        '2'.code.toUInt() to KEY_2.toUInt(),
        '@'.code.toUInt() to (KEY_2.toUInt() or CAPS_MASK),
        '3'.code.toUInt() to KEY_3.toUInt(),
        '#'.code.toUInt() to (KEY_3.toUInt() or CAPS_MASK),
        '4'.code.toUInt() to KEY_4.toUInt(),
        '$'.code.toUInt() to (KEY_4.toUInt() or CAPS_MASK),
        '5'.code.toUInt() to KEY_5.toUInt(),
        '%'.code.toUInt() to (KEY_5.toUInt() or CAPS_MASK),
        '6'.code.toUInt() to KEY_6.toUInt(),
        '^'.code.toUInt() to (KEY_6.toUInt() or CAPS_MASK),
        '7'.code.toUInt() to KEY_7.toUInt(),
        '&'.code.toUInt() to (KEY_7.toUInt() or CAPS_MASK),
        '8'.code.toUInt() to KEY_8.toUInt(),
        '*'.code.toUInt() to (KEY_8.toUInt() or CAPS_MASK),
        '9'.code.toUInt() to KEY_9.toUInt(),
        '('.code.toUInt() to (KEY_9.toUInt() or CAPS_MASK),
        '0'.code.toUInt() to KEY_0.toUInt(),
        ')'.code.toUInt() to (KEY_0.toUInt() or CAPS_MASK),
        '`'.code.toUInt() to KEY_BACKQUOTE.toUInt(),
        '~'.code.toUInt() to (KEY_BACKQUOTE.toUInt() or CAPS_MASK),
        '-'.code.toUInt() to KEY_MINUS.toUInt(),
        '_'.code.toUInt() to (KEY_MINUS.toUInt() or CAPS_MASK),
        '='.code.toUInt() to KEY_EQUALS.toUInt(),
        '+'.code.toUInt() to (KEY_EQUALS.toUInt() or CAPS_MASK),
        '['.code.toUInt() to KEY_LEFT_BRACKET.toUInt(),
        '{'.code.toUInt() to (KEY_LEFT_BRACKET.toUInt() or CAPS_MASK),
        ']'.code.toUInt() to KEY_RIGHT_BRACKET.toUInt(),
        '}'.code.toUInt() to (KEY_RIGHT_BRACKET.toUInt() or CAPS_MASK),
        '\\'.code.toUInt() to KEY_BACK_SLASH.toUInt(),
        '|'.code.toUInt() to (KEY_BACK_SLASH.toUInt() or CAPS_MASK),
        ';'.code.toUInt() to KEY_SEMICOLON.toUInt(),
        ':'.code.toUInt() to (KEY_SEMICOLON.toUInt() or CAPS_MASK),
        '\''.code.toUInt() to KEY_QUOTE.toUInt(),
        '"'.code.toUInt() to (KEY_QUOTE.toUInt() or CAPS_MASK),
        ','.code.toUInt() to KEY_COMMA.toUInt(),
        '<'.code.toUInt() to (KEY_COMMA.toUInt() or CAPS_MASK),
        '.'.code.toUInt() to KEY_DOT.toUInt(),
        '>'.code.toUInt() to (KEY_DOT.toUInt() or CAPS_MASK),
        '/'.code.toUInt() to KEY_SLASH.toUInt(),
        '?'.code.toUInt() to (KEY_SLASH.toUInt() or CAPS_MASK),
        ' '.code.toUInt() to KEY_SPACE.toUInt()
    )

    private val _keyCodeToCharMap: Map<UInt, UShort> = _characterMap.entries.associate { (charVal, keyVal) ->
        keyVal to charVal.toUShort()
    }

    fun keyCodeToCharacter(keyCode: UInt): UShort {
        return _keyCodeToCharMap[keyCode and (CHAR_MASK or CAPS_MASK)] ?: 0u
    }

    fun charToKeyCode(c: Char): Pair<Int, Int> {
        val entry = _characterMap[c.code.toUInt()]
        return if (entry != null) {
            val keyCode = (entry and CHAR_MASK).toInt()
            val caps = if ((entry and CAPS_MASK) != 0u) 1 else 0
            Pair(keyCode, caps)
        } else {
            Pair(KEY_EMPTY.toInt(), 0)
        }
    }
}

val UInt.baseKey: UShort get() = (this and CHAR_MASK).toUShort()
val UInt.isCaps: Boolean get() = (this and CAPS_MASK) != 0u
val UInt.hasToneMark: Boolean get() = (this and (TONE_MASK or TONEW_MASK)) != 0u
val UInt.toneMark: UInt get() = this and (TONE_MASK or TONEW_MASK)
val UInt.hasCircumflexOrStroke: Boolean get() = (this and TONE_MASK) != 0u
val UInt.hasHornOrBreve: Boolean get() = (this and TONEW_MASK) != 0u
val UInt.hasAnyDiacritic: Boolean get() = (this and (TONE_MASK or TONEW_MASK or MARK_MASK)) != 0u
val UInt.isAutoTone: Boolean get() = (this and AUTO_TONE_MASK) != 0u
val UInt.isStandaloneW: Boolean get() = (this and STANDALONE_MASK) != 0u
