package com.tuyenmai.openkey.core

fun VietnameseEngineState.checkSpelling(forceCheckVowel: Boolean = false) {
    spellingOK = false
    spellingVowelOK = true
    spellingEndIndex = index

    if (index > 0 && CHR(index - 1) == KEY_RIGHT_BRACKET) {
        spellingEndIndex = index - 1
    }

    val endIdx = spellingEndIndex
    if (endIdx > 0) {
        var j = 0
        if (VietnameseData.isConsonant(CHR(0))) {
            for (i in VietnameseData._consonantTable.indices) {
                spellingFlag = false
                val cons = VietnameseData._consonantTable[i]
                if (endIdx < cons.size) {
                    spellingFlag = true
                }
                var matchCount = 0
                for (jElem in cons.indices) {
                    matchCount = jElem + 1
                    if (endIdx > jElem && cons[jElem] != CHR(jElem)) {
                        spellingFlag = true
                        break
                    }
                }
                if (spellingFlag) continue
                j = matchCount
                break
            }
        }

        if (j == endIdx) { // "d" case
            spellingOK = true
        }

        var k = j
        vowelStartIndex = k

        if (CHR(vowelStartIndex) == KEY_U && k > 0 && k < endIdx - 1 && CHR(vowelStartIndex - 1) == KEY_Q) {
            k++
            j = k
            vowelStartIndex = k
        } else if (index >= 2 && CHR(0) == KEY_G && CHR(1) == KEY_I && VietnameseData.isConsonant(CHR(2))) {
            k = 1
            j = 1
            vowelStartIndex = 1
        }

        for (step in 0 until 3) {
            if (k < endIdx && !VietnameseData.isConsonant(CHR(k))) {
                k++
                vowelEndIndex = k
            }
        }

        if (k > j) {
            spellingVowelOK = false
            if (k - j > 1 && forceCheckVowel) {
                val vowelSet = VietnameseData._vowelCombine[CHR(j)]
                if (vowelSet != null) {
                    for (l in vowelSet.indices) {
                        spellingFlag = false
                        var ii = 1
                        while (ii < vowelSet[l].size) {
                            val targetIdx = j + ii - 1
                            if (targetIdx < endIdx) {
                                val actual = CHR(targetIdx).toUInt() or typingWord[targetIdx].toneMark
                                if (vowelSet[l][ii] != actual) {
                                    spellingFlag = true
                                    break
                                }
                            }
                            ii++
                        }
                        if (spellingFlag || (k < endIdx && vowelSet[l][0] == 0u) || (j + ii - 1 < endIdx && !VietnameseData.isConsonant(CHR(j + ii - 1)))) {
                            continue
                        }
                        spellingVowelOK = true
                        break
                    }
                }
            } else if (!VietnameseData.isConsonant(CHR(j))) {
                spellingVowelOK = true
            }

            for (ii in VietnameseData._endConsonantTable.indices) {
                spellingFlag = false
                val endCons = VietnameseData._endConsonantTable[ii]
                for (jElem in endCons.indices) {
                    if (endIdx > k + jElem && endCons[jElem] != CHR(k + jElem)) {
                        spellingFlag = true
                        break
                    }
                }
                if (spellingFlag) continue
                if (k + endCons.size >= endIdx) {
                    spellingOK = true
                    break
                }
            }

            if (spellingOK) {
                if (index >= 3 && CHR(index - 1) == KEY_H && CHR(index - 2) == KEY_C) {
                    val wordTone = typingWord[index - 3]
                    val hasMark = (wordTone and MARK_MASK) != 0u
                    val is1 = (wordTone and MARK1_MASK) != 0u
                    val is5 = (wordTone and MARK5_MASK) != 0u
                    if (hasMark && !is1 && !is5) {
                        spellingOK = false
                    }
                } else if (index >= 2 && CHR(index - 1) == KEY_T) {
                    val wordTone = typingWord[index - 2]
                    val hasMark = (wordTone and MARK_MASK) != 0u
                    val is1 = (wordTone and MARK1_MASK) != 0u
                    val is5 = (wordTone and MARK5_MASK) != 0u
                    if (hasMark && !is1 && !is5) {
                        spellingOK = false
                    }
                }
            }
        }
    } else {
        spellingOK = true
    }

    tempDisableKey = !(spellingOK && spellingVowelOK)
}

fun VietnameseEngineState.repairPhonotacticClusterIfNeeded(isInsertingMark: Boolean = false): Boolean {
    if (AutoWordingEngine.shared.isEnabled && !AutoWordingEngine.shared.grammarRepairEnabled) return false
    val hook = phonotacticRepairHook
    if (hook != null) {
        if (index < 3) return false
        findAndCalculateVowel(forGrammar = true)
        if (vowelCount < 2) return false
        val hooked = hook(
            typingWord,
            index,
            vowelStartIndex,
            vowelEndIndex,
            vowelCount,
            isInsertingMark
        )
        if (hooked) return true
    }

    if (index < 3) return false
    findAndCalculateVowel(forGrammar = true)
    if (vowelCount < 2) return false

    var repaired = false
    val vsi = vowelStartIndex
    val vei = vowelEndIndex

    fun charAt(idx: Int): UShort = (typingWord[idx] and CHAR_MASK).toUShort()

    val hasFinalConsonant = (index - 1 > vei) && VietnameseData.isConsonant(charAt(vei + 1))
    if (!hasFinalConsonant) return false

    // Rule 1: ie/ye + final consonant -> iê/yê
    val prev = charAt(vei - 1)
    val curr = charAt(vei)
    if ((prev == KEY_I || prev == KEY_Y) && curr == KEY_E) {
        val prevIdx = vei - 1
        val currIdx = vei
        val hasToneOrInserting = isInsertingMark ||
                (typingWord[prevIdx] and MARK_MASK) != 0u ||
                (typingWord[currIdx] and MARK_MASK) != 0u
        if (hasToneOrInserting && (typingWord[vei] and TONE_MASK) == 0u) {
            typingWord[vei] = typingWord[vei] or (TONE_MASK or AUTO_TONE_MASK)
            repaired = true
        }
    }

    // Rule 2: uow -> ươ (only c1 == KEY_U && c2 == KEY_O, skip if preceded by Q)
    if (vei >= vsi + 1) {
        for (i in vsi until vei) {
            val c1 = charAt(i)
            val c2 = charAt(i + 1)
            if (c1 == KEY_U && c2 == KEY_O) {
                if (i > 0 && charAt(i - 1) == KEY_Q) continue
                val m1 = typingWord[i] and TONEW_MASK
                val m2 = typingWord[i + 1] and TONEW_MASK
                if ((m1 != 0u || m2 != 0u) && (m1 == 0u || m2 == 0u)) {
                    if (m1 == 0u) typingWord[i] = typingWord[i] or (TONEW_MASK or AUTO_TONE_MASK)
                    if (m2 == 0u) typingWord[i + 1] = typingWord[i + 1] or (TONEW_MASK or AUTO_TONE_MASK)
                    repaired = true
                }
            }
        }
    }

    return repaired
}

fun VietnameseEngineState.checkGrammar(deltaBackSpace: Int) {
    if (index <= 1 || index >= maxBuff) return

    isCheckedGrammar = if (deltaBackSpace <= 0) repairPhonotacticClusterIfNeeded(isInsertingMark = false) else false

    findAndCalculateVowel(forGrammar = true)
    if (vowelCount <= 0) return

    val l = vowelStartIndex

    if (index >= 3) {
        for (i in (index - 1) downTo 0) {
            val ch = CHR(i)
            if (ch == KEY_N || ch == KEY_C || ch == KEY_I || ch == KEY_M || ch == KEY_P || ch == KEY_T) {
                if (i - 2 >= 0 && CHR(i - 1) == KEY_O && CHR(i - 2) == KEY_U) {
                    val mask1 = typingWord[i - 1].hasHornOrBreve
                    val mask2 = typingWord[i - 2].hasHornOrBreve
                    if (mask1 != mask2) {
                        if (!typingWord[i - 2].hasHornOrBreve) { typingWord[i - 2] = typingWord[i - 2] or (TONEW_MASK or AUTO_TONE_MASK) }
                        if (!typingWord[i - 1].hasHornOrBreve) { typingWord[i - 1] = typingWord[i - 1] or (TONEW_MASK or AUTO_TONE_MASK) }
                        isCheckedGrammar = true
                        break
                    }
                }
            }
        }
    }

    if (index >= 2) {
        for (i in l..vowelEndIndex) {
            if ((typingWord[i] and MARK_MASK) != 0u) {
                val mark = typingWord[i] and MARK_MASK
                typingWord[i] = typingWord[i] and MARK_MASK.inv()
                insertMark(markMask = mark, canModifyFlag = false)
                if (i != vowelWillSetMark) {
                    isCheckedGrammar = true
                }
                break
            }
        }
    }

    if (isCheckedGrammar) {
        if (hookOutput.code == HookCodeState.DO_NOTHING) {
            hookOutput.code = HookCodeState.WILL_PROCESS
        }
        hookOutput.backspaceCount = 0

        for (i in (index - 1) downTo l) {
            hookOutput.backspaceCount++
            hookOutput.charData[index - 1 - i] = GET(typingWord[i])
        }
        hookOutput.newCharCount = hookOutput.backspaceCount
        hookOutput.backspaceCount += deltaBackSpace
        hookOutput.extCode = 4
    }
}

fun VietnameseEngineState.checkCorrectVowel(charset: List<UShortArray>, i: Int, markKey: UShort) {
    if (index >= 2 && CHR(index - 1) == KEY_U && CHR(index - 2) == KEY_Q) {
        isCorrect = false
        return
    }
    var k = index - 1
    for (j in (charset[i].size - 1) downTo 0) {
        val ch = charset[i][j]
        if ((ch.toInt() and END_CONSONANT_MASK.toInt()) != 0) {
            isCorrect = false
            return
        }
        if (ch != CHR(k)) {
            isCorrect = false
            return
        }
        k--
        if (k < 0) break
    }

    // limit mark for end consonant: "C", "T"
    if (isCorrect && charset[i].size > 1 && (isKeyF(markKey) || isKeyX(markKey) || isKeyR(markKey))) {
        if (charset[i][1] == KEY_C || charset[i][1] == KEY_T) {
            isCorrect = false
        } else if (charset[i].size > 2 && charset[i][2] == KEY_T) {
            isCorrect = false
        }
    }

    if (isCorrect && k >= 0) {
        if ((k > 0 && CHR(k) == CHR(k - 1)) || (k + 1 < index && CHR(k) == CHR(k + 1))) {
            isCorrect = false
        }
    }
}

fun VietnameseEngineState.findAndCalculateVowel(forGrammar: Boolean = false) {
    vowelCount = 0
    vowelStartIndex = 0
    vowelEndIndex = 0
    if (index <= 0) return

    for (iii in (index - 1) downTo 0) {
        if (VietnameseData.isConsonant(CHR(iii))) {
            if (vowelCount > 0) break
        } else {
            if (vowelCount == 0) {
                vowelEndIndex = iii
            }
            if (!forGrammar) {
                if ((iii - 1 >= 0 && (CHR(iii) == KEY_I && CHR(iii - 1) == KEY_G)) ||
                    (iii - 1 >= 0 && (CHR(iii) == KEY_U && CHR(iii - 1) == KEY_Q))
                ) {
                    break
                }
            }
            vowelStartIndex = iii
            vowelCount++
        }
    }

    if (vowelStartIndex > 0 && CHR(vowelStartIndex) == KEY_U && CHR(vowelStartIndex - 1) == KEY_Q) {
        vowelStartIndex++
        vowelCount--
    }
}

fun VietnameseEngineState.canHasEndConsonant(): Boolean {
    val vo = VietnameseData._vowelCombine[CHR(vowelStartIndex)] ?: return false
    for (ii in vo.indices) {
        var kk = vowelStartIndex
        var iii = 1
        while (iii < vo[ii].size) {
            if (kk > vowelEndIndex || ((CHR(kk).toUInt() or typingWord[kk].toneMark) != vo[ii][iii])) {
                break
            }
            kk++
            iii++
        }
        if (iii >= vo[ii].size) {
            return vo[ii][0] == 1u
        }
    }
    return false
}

fun VietnameseEngineState.getCharacterCode(data: UInt): UInt {
    val capsElem = if (data.isCaps) 0 else 1
    var key = data.baseKey.toUInt()

    if ((data and MARK_MASK) != 0u) {
        val markOffsets = mapOf(
            MARK1_MASK to 0,
            MARK2_MASK to 2,
            MARK3_MASK to 4,
            MARK4_MASK to 6,
            MARK5_MASK to 8
        )
        var markElem = (markOffsets[data and MARK_MASK] ?: -2) + capsElem
        val base = data.baseKey
        if ((base == KEY_A || base == KEY_O || base == KEY_U || base == KEY_E) &&
            !data.hasCircumflexOrStroke && !data.hasHornOrBreve
        ) {
            markElem += 4
        }
        if (data.hasCircumflexOrStroke) key = key or TONE_MASK
        else if (data.hasHornOrBreve) key = key or TONEW_MASK

        val table = VietnameseData._codeTable[key] ?: return data
        if (markElem in table.indices) {
            return table[markElem].toUInt() or CHAR_CODE_MASK
        }
        return data
    } else {
        val table = VietnameseData._codeTable[key] ?: return data
        if (data.hasCircumflexOrStroke) {
            if (capsElem in table.indices) {
                return table[capsElem].toUInt() or CHAR_CODE_MASK
            }
        } else if (data.hasHornOrBreve) {
            if (capsElem + 2 in table.indices) {
                return table[capsElem + 2].toUInt() or CHAR_CODE_MASK
            }
        }
        return data
    }
}

fun VietnameseEngineState.removeMark() {
    hookOutput.code = HookCodeState.DO_NOTHING
    if (index <= 0) return
    findAndCalculateVowel()
    val vsi = vowelStartIndex
    val vei = vowelEndIndex
    isChanged = false
    hookOutput.code = HookCodeState.WILL_PROCESS
    hookOutput.backspaceCount = 0

    for (ii in (index - 1) downTo 0) {
        hookOutput.backspaceCount++
        if ((typingWord[ii] and MARK_MASK) != 0u) {
            typingWord[ii] = typingWord[ii] and MARK_MASK.inv()
            isChanged = true
        }
        if (vsi <= vei && ii in vsi..vei && typingWord[ii].isAutoTone) {
            typingWord[ii] = typingWord[ii] and (TONE_MASK or TONEW_MASK or AUTO_TONE_MASK).inv()
            isChanged = true
        }
        hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
    }
    hookOutput.newCharCount = hookOutput.backspaceCount
    if (!isChanged) hookOutput.code = HookCodeState.DO_NOTHING
}

fun VietnameseEngineState.handleModernMark() {
    vowelWillSetMark = vowelEndIndex
    val vsi = vowelStartIndex
    val vei = vowelEndIndex
    val c0 = CHR(vsi)
    val c1 = if (vsi + 1 <= vei) CHR(vsi + 1) else KEY_EMPTY
    val c2 = if (vsi + 2 <= vei) CHR(vsi + 2) else KEY_EMPTY

    if (vowelCount == 3 && (
            (c0 == KEY_O && c1 == KEY_A && c2 == KEY_I) ||
            (c0 == KEY_U && c1 == KEY_Y && c2 == KEY_U) ||
            (c0 == KEY_O && c1 == KEY_E && c2 == KEY_O) ||
            (c0 == KEY_U && c1 == KEY_Y && c2 == KEY_A)
        )
    ) {
        vowelWillSetMark = vsi + 1
    } else if ((c0 == KEY_O && c1 == KEY_I) || (c0 == KEY_A && c1 == KEY_I) || (c0 == KEY_U && c1 == KEY_I)) {
        vowelWillSetMark = vowelStartIndex
    } else if (vei > 0 && CHR(vei - 1) == KEY_A && CHR(vei) == KEY_Y) {
        vowelWillSetMark = vei - 1
    } else if (c0 == KEY_U && c1 == KEY_O) {
        vowelWillSetMark = vsi + 1
    } else if (c1 == KEY_O || c1 == KEY_U) {
        vowelWillSetMark = vei - 1
    } else if (c0 == KEY_O || c0 == KEY_U) {
        vowelWillSetMark = vowelEndIndex
    }

    val isRule3_1: Boolean = run {
        if (vsi + 1 > vei) return@run false
        val w1 = typingWord[vsi + 1]
        if ((c0 == KEY_I || c0 == KEY_Y || c0 == KEY_U) && c1 == (if (c0 == KEY_U) KEY_O else KEY_E) && w1.hasCircumflexOrStroke) {
            return@run true
        }
        return@run c0 == KEY_U && typingWord[vsi].hasHornOrBreve && c1 == KEY_O && w1.hasHornOrBreve
    }

    if (isRule3_1) {
        val ch2 = if (vsi + 2 < index) CHR(vsi + 2) else KEY_EMPTY
        val ch3 = if (vsi + 3 < index) CHR(vsi + 3) else KEY_EMPTY
        val isEndMatch = listOf(KEY_P, KEY_T, KEY_M, KEY_N, KEY_O, KEY_U, KEY_I, KEY_C).contains(ch2) ||
            (ch2 == KEY_C && ch3 == KEY_H) || (ch2 == KEY_N && (ch3 == KEY_H || ch3 == KEY_G))
        vowelWillSetMark = if (vsi + 2 < index && isEndMatch) vsi + 1 else vowelStartIndex
    } else if ((c0 == KEY_I && c1 == KEY_A) || (c0 == KEY_Y && c1 == KEY_A) || (c0 == KEY_U && c1 == KEY_A) ||
        (c0 == KEY_U && (typingWord[vsi].hasHornOrBreve || typingWord[vsi + 1].hasHornOrBreve) && c1 == KEY_U)
    ) {
        vowelWillSetMark = vowelStartIndex
    }

    if (vowelCount == 2 && vsi + 1 <= vei) {
        if (c0 == KEY_I && (c1 == KEY_A || c1 == KEY_U || c1 == KEY_O)) {
            vowelWillSetMark = if (vsi == 0 || CHR(vsi - 1) != KEY_G) vowelStartIndex else vsi + 1
        } else if (c0 == KEY_U && c1 == KEY_A) {
            vowelWillSetMark = if (vsi != 0 && CHR(vsi - 1) == KEY_Q) vsi + 1 else if (vei + 1 >= index || !canHasEndConsonant()) vowelStartIndex else vowelEndIndex
        } else if (c0 == KEY_O && c1 == KEY_O) {
            vowelWillSetMark = vowelEndIndex
        }
    }
}

fun VietnameseEngineState.handleOldMark() {
    vowelWillSetMark = if (vowelCount == 0 && CHR(vowelEndIndex) == KEY_I) vowelEndIndex else vowelStartIndex
    val vsi = vowelStartIndex
    val vei = vowelEndIndex
    if (vowelCount == 3 || (vei + 1 < index && VietnameseData.isConsonant(CHR(vei + 1)) && canHasEndConsonant())) {
        vowelWillSetMark = vsi + 1
    }
    for (ii in vsi..vei) {
        if ((CHR(ii) == KEY_E && typingWord[ii].hasCircumflexOrStroke) ||
            (CHR(ii) == KEY_O && typingWord[ii].hasHornOrBreve)
        ) {
            vowelWillSetMark = ii
            break
        }
    }
}

fun VietnameseEngineState.insertMark(markMask: UInt, canModifyFlag: Boolean = true) {
    if (canModifyFlag) {
        repairPhonotacticClusterIfNeeded(isInsertingMark = true)
        hookOutput.code = HookCodeState.WILL_PROCESS
        hookOutput.backspaceCount = 0
        hookOutput.newCharCount = 0
    }
    vowelCount = 0
    findAndCalculateVowel()
    if (vowelCount == 0 && index >= 1 && (CHR(vowelEndIndex) == KEY_I || CHR(vowelEndIndex) == KEY_U)) {
        vowelWillSetMark = vowelEndIndex
        vowelStartIndex = vowelEndIndex
    } else {
        vowelWillSetMark = if (vowelCount == 1) vowelEndIndex else 0
        if (vowelCount > 1) {
            if (vUseModernOrthography == 0) handleOldMark() else handleModernMark()
            if (typingWord[vowelEndIndex].hasToneMark) vowelWillSetMark = vowelEndIndex
        }
    }
    val vsi = vowelStartIndex
    val vwsm = vowelWillSetMark
    var kk = index - 1 - vsi
    val hasSameMark = (typingWord[vwsm] and markMask) != 0u
    typingWord[vwsm] = typingWord[vwsm] and MARK_MASK.inv()
    if (!hasSameMark) typingWord[vwsm] = typingWord[vwsm] or markMask
    for (ii in vsi until index) {
        if (ii != vwsm) typingWord[ii] = typingWord[ii] and MARK_MASK.inv()
        if (hasSameMark && typingWord[ii].isAutoTone) typingWord[ii] = typingWord[ii] and (TONE_MASK or TONEW_MASK or AUTO_TONE_MASK).inv()
        if (canModifyFlag && kk >= 0) {
            hookOutput.charData[kk] = GET(typingWord[ii])
            kk--
        }
    }
    if (canModifyFlag) {
        hookOutput.code = if (hasSameMark) HookCodeState.RESTORE else HookCodeState.WILL_PROCESS
        tempDisableKey = hasSameMark
        hookOutput.backspaceCount = index - vsi
        hookOutput.newCharCount = hookOutput.backspaceCount
    }
}

fun VietnameseEngineState.insertD(data: UShort, caps: Boolean) {
    hookOutput.code = HookCodeState.WILL_PROCESS
    hookOutput.backspaceCount = 0
    if (index <= 0) return
    for (ii in (index - 1) downTo 0) {
        hookOutput.backspaceCount++
        if (CHR(ii) == KEY_D) {
            if (typingWord[ii].hasCircumflexOrStroke) {
                hookOutput.code = HookCodeState.RESTORE
                typingWord[ii] = typingWord[ii] and TONE_MASK.inv()
                tempDisableKey = true
            } else {
                typingWord[ii] = typingWord[ii] or TONE_MASK
            }
            hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
            break
        }
        hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
    }
    hookOutput.newCharCount = hookOutput.backspaceCount
}

fun VietnameseEngineState.insertAOE(data: UShort, caps: Boolean) {
    findAndCalculateVowel()
    val vsi = vowelStartIndex
    val vei = vowelEndIndex
    if (vsi <= vei) {
        for (ii in vsi..vei) typingWord[ii] = typingWord[ii] and TONEW_MASK.inv()
    }
    hookOutput.code = HookCodeState.WILL_PROCESS
    hookOutput.backspaceCount = 0
    if (index <= 0) return

    for (ii in (index - 1) downTo 0) {
        hookOutput.backspaceCount++
        if (CHR(ii) == data) {
            if (typingWord[ii].hasCircumflexOrStroke) {
                if (typingWord[ii].isAutoTone) {
                    typingWord[ii] = typingWord[ii] and AUTO_TONE_MASK.inv()
                } else {
                    hookOutput.code = HookCodeState.RESTORE
                    typingWord[ii] = typingWord[ii] and TONE_MASK.inv()
                    if (data != KEY_O) tempDisableKey = true
                }
            } else {
                typingWord[ii] = typingWord[ii] or TONE_MASK
                if (!isKeyD(data)) typingWord[ii] = typingWord[ii] and TONEW_MASK.inv()
            }
            hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
            break
        }
        hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
    }
    hookOutput.newCharCount = hookOutput.backspaceCount
}

fun VietnameseEngineState.insertW(data: UShort, caps: Boolean) {
    isRestoredW = false
    findAndCalculateVowel()
    val vsi = vowelStartIndex
    val vei = vowelEndIndex
    if (vsi <= vei) {
        for (ii in vsi..vei) typingWord[ii] = typingWord[ii] and TONE_MASK.inv()
    }

    if (vowelCount > 1) {
        hookOutput.backspaceCount = index - vsi
        hookOutput.newCharCount = hookOutput.backspaceCount

        val hasW0 = typingWord[vsi].hasHornOrBreve
        val hasW1 = vsi + 1 < maxBuff && typingWord[vsi + 1].hasHornOrBreve
        val ch1 = if (vsi + 1 < maxBuff) CHR(vsi + 1) else 0u.toUShort()

        if ((hasW0 && hasW1) || (hasW0 && (ch1 == KEY_I || ch1 == KEY_A)) || (hasW1 && (CHR(vsi) == KEY_I || CHR(vsi) == KEY_U || CHR(vsi) == KEY_O))) {
            val autoTone = typingWord[vsi].isAutoTone || (vsi + 1 < maxBuff && typingWord[vsi + 1].isAutoTone)
            hookOutput.code = if (autoTone) HookCodeState.WILL_PROCESS else HookCodeState.RESTORE
            for (ii in vsi until index) {
                if (autoTone) typingWord[ii] = typingWord[ii] and AUTO_TONE_MASK.inv()
                else typingWord[ii] = typingWord[ii] and TONEW_MASK.inv()
                val mask = if (autoTone) 0xFFFFFFFFu else STANDALONE_MASK.inv()
                hookOutput.charData[index - 1 - ii] = GET(typingWord[ii]) and mask
            }
            if (!autoTone) { isRestoredW = true; tempDisableKey = true }
        } else {
            hookOutput.code = HookCodeState.WILL_PROCESS
            val ch0 = CHR(vsi)
            if (ch0 == KEY_U && ch1 == KEY_O) {
                val hasTh = vsi >= 2 && CHR(vsi - 2) == KEY_T && CHR(vsi - 1) == KEY_H
                val hasQ = vsi >= 1 && CHR(vsi - 1) == KEY_Q
                if (hasTh) {
                    typingWord[vsi + 1] = typingWord[vsi + 1] or TONEW_MASK
                    if (vsi + 2 < index && CHR(vsi + 2) == KEY_N) typingWord[vsi] = typingWord[vsi] or TONEW_MASK
                } else if (hasQ) {
                    typingWord[vsi + 1] = typingWord[vsi + 1] or TONEW_MASK
                } else {
                    typingWord[vsi] = typingWord[vsi] or TONEW_MASK
                    typingWord[vsi + 1] = typingWord[vsi + 1] or TONEW_MASK
                }
            } else if ((ch0 == KEY_U && (ch1 == KEY_A || ch1 == KEY_I || ch1 == KEY_U)) || (ch0 == KEY_O && ch1 == KEY_I)) {
                typingWord[vsi] = typingWord[vsi] or TONEW_MASK
            } else if ((ch0 == KEY_I && ch1 == KEY_O) || (ch0 == KEY_O && ch1 == KEY_A)) {
                typingWord[vsi + 1] = typingWord[vsi + 1] or TONEW_MASK
            } else {
                tempDisableKey = true; isChanged = false; hookOutput.code = HookCodeState.DO_NOTHING
            }
            for (ii in vsi until index) { hookOutput.charData[index - 1 - ii] = GET(typingWord[ii]) }
        }
        return
    }

    hookOutput.code = HookCodeState.WILL_PROCESS
    hookOutput.backspaceCount = 0
    if (index <= 0) return

    for (ii in (index - 1) downTo 0) {
        if (ii < vsi) break
        hookOutput.backspaceCount++
        val ch = CHR(ii)
        if (ch == KEY_A || ch == KEY_U || ch == KEY_O) {
            if (typingWord[ii].hasHornOrBreve) {
                if (typingWord[ii].isAutoTone) {
                    typingWord[ii] = typingWord[ii] and AUTO_TONE_MASK.inv()
                    hookOutput.code = HookCodeState.WILL_PROCESS
                } else if (typingWord[ii].isStandaloneW) {
                    if (ch == KEY_U) {
                        if (stateIndex > 0) {
                            stateIndex--
                            keyStates[stateIndex] = KEY_EMPTY.toUInt()
                        }
                        if (rawStateIndex > 0) {
                            rawStateIndex--
                            rawKeyStates[rawStateIndex] = KEY_EMPTY.toUInt()
                        }
                    }
                    hookOutput.code = if (ch == KEY_U) HookCodeState.WILL_PROCESS else HookCodeState.RESTORE
                    typingWord[ii] = (if (ch == KEY_U) KEY_W else KEY_O).toUInt() or (if (typingWord[ii].isCaps) CAPS_MASK else 0u)
                    if (ch == KEY_O) isRestoredW = true
                } else {
                    hookOutput.code = HookCodeState.RESTORE
                    typingWord[ii] = typingWord[ii] and TONEW_MASK.inv()
                    isRestoredW = true
                }
                tempDisableKey = true
            } else {
                typingWord[ii] = (typingWord[ii] or TONEW_MASK) and TONE_MASK.inv()
            }
            hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
            break
        } else {
            hookOutput.charData[index - 1 - ii] = GET(typingWord[ii])
        }
    }
    hookOutput.newCharCount = hookOutput.backspaceCount
}

fun VietnameseEngineState.reverseLastStandaloneChar(keyCode: UInt, caps: Boolean) {
    hookOutput.code = HookCodeState.WILL_PROCESS
    hookOutput.backspaceCount = 0
    hookOutput.newCharCount = 1
    hookOutput.extCode = 4
    typingWord[index - 1] = keyCode or TONEW_MASK or STANDALONE_MASK or (if (caps) CAPS_MASK else 0u)
    hookOutput.charData[0] = GET(typingWord[index - 1])
}

fun VietnameseEngineState.checkForStandaloneChar(data: UShort, caps: Boolean, keyWillReverse: UInt) {
    if (index > 0 && CHR(index - 1) == keyWillReverse.toUShort() && typingWord[index - 1].hasHornOrBreve) {
        hookOutput.code = HookCodeState.WILL_PROCESS
        hookOutput.backspaceCount = 1
        hookOutput.newCharCount = 1
        typingWord[index - 1] = data.toUInt() or (if (caps) CAPS_MASK else 0u)
        hookOutput.charData[0] = GET(typingWord[index - 1])
        return
    }
    if (index > 0 && CHR(index - 1) == KEY_U && keyWillReverse == KEY_O.toUInt()) {
        insertKey(keyCode = keyWillReverse.toUShort(), caps = caps)
        reverseLastStandaloneChar(keyCode = keyWillReverse, caps = caps)
        return
    }
    val shouldReverse: Boolean = run {
        if (index == 0) return@run true
        if (index == 1) return@run !VietnameseData._standaloneWbad.contains(CHR(0))
        if (index == 2) return@run VietnameseData._doubleWAllowed.any { it[0] == CHR(0) && it[1] == CHR(1) }
        return@run false
    }
    if (shouldReverse) {
        insertKey(keyCode = data, caps = caps, isCheckSpellingFlag = false)
        reverseLastStandaloneChar(keyCode = keyWillReverse, caps = caps)
    } else {
        insertKey(keyCode = data, caps = caps)
    }
}

fun VietnameseEngineState.handleMainKey(data: UShort, caps: Boolean) {
    if (isKeyZ(data)) {
        removeMark()
        if (!isChanged) insertKey(keyCode = data, caps = caps)
        return
    }
    if (data == KEY_LEFT_BRACKET) { checkForStandaloneChar(data = data, caps = caps, keyWillReverse = KEY_O.toUInt()); return }
    if (data == KEY_RIGHT_BRACKET) { checkForStandaloneChar(data = data, caps = caps, keyWillReverse = KEY_U.toUInt()); return }

    if (isKeyD(data)) {
        isCorrect = false; isChanged = false
        for (i in VietnameseData._consonantD.indices) {
            if (index >= VietnameseData._consonantD[i].size) {
                isCorrect = true
                checkCorrectVowel(charset = VietnameseData._consonantD, i = i, markKey = data)
                if (!isCorrect && index >= 2 && CHR(index - 1) == KEY_D && VietnameseData.isConsonant(CHR(index - 2))) { isCorrect = true }
                if (isCorrect) { isChanged = true; insertD(data = data, caps = caps); break }
            }
        }
        if (!isChanged) insertKey(keyCode = data, caps = caps)
        return
    }

    if (isMarkKey(data)) {
        for ((_, charset) in VietnameseData._vowelForMark) {
            isCorrect = false; isChanged = false
            for (l in charset.indices) {
                if (index >= charset[l].size) {
                    isCorrect = true
                    checkCorrectVowel(charset = charset, i = l, markKey = data)
                    if (isCorrect) {
                        isChanged = true
                        val mask: UInt = if (isKeyS(data)) MARK1_MASK else (if (isKeyF(data)) MARK2_MASK else (if (isKeyR(data)) MARK3_MASK else (if (isKeyX(data)) MARK4_MASK else MARK5_MASK)))
                        insertMark(markMask = mask)
                        break
                    }
                }
            }
            if (isCorrect) break
        }
        if (!isChanged) insertKey(keyCode = data, caps = caps)
        return
    }

    if (vInputType == 1) {
        for (i in (index - 1) downTo 0) {
            val ch = CHR(i)
            if (ch == KEY_O || ch == KEY_A || ch == KEY_E) { vowelEndIndex = i; break }
        }
    }

    keyForAEO = if (vInputType != 1) data else (if (data == KEY_7 || data == KEY_8) KEY_W else (if (data == KEY_6) CHR(vowelEndIndex) else data))

    val charset = VietnameseData._vowel[keyForAEO]
    if (charset != null) {
        isCorrect = false; isChanged = false
        for (i in charset.indices) {
            if (index >= charset[i].size) {
                isCorrect = true
                checkCorrectVowel(charset = charset, i = i, markKey = data)
                if (isCorrect) {
                    isChanged = true
                    if (isKeyDouble(data)) {
                        insertAOE(data = keyForAEO, caps = caps)
                    } else if (isKeyW(data)) {
                        if (vInputType == 1) {
                            for (j in (index - 1) downTo 0) {
                                val ch = CHR(j)
                                if (ch == KEY_O || ch == KEY_U || ch == KEY_A || ch == KEY_E) { vowelEndIndex = j; break }
                            }
                            val vei = vowelEndIndex
                            val chVei = CHR(vei)
                            if ((data == KEY_7 && chVei == KEY_A && (if (vei - 1 >= 0) CHR(vei - 1) != KEY_U else true)) ||
                                (data == KEY_8 && (chVei == KEY_O || chVei == KEY_U))
                            ) {
                                isChanged = false
                                break
                            }
                        }
                        insertW(data = keyForAEO, caps = caps)
                    }
                    break
                }
            }
        }
    }

    if (!isChanged) {
        if (data == KEY_W && vInputType == 0) {
            checkForStandaloneChar(data = data, caps = caps, keyWillReverse = KEY_U.toUInt())
        } else {
            insertKey(keyCode = data, caps = caps)
        }
    }
}
