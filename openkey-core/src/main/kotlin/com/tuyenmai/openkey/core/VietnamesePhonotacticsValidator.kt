package com.tuyenmai.openkey.core

/**
 * Extracted phonotactic and onset validation rules for Vietnamese syllables.
 */
internal object VietnamesePhonotacticsValidator {
    private val FAKE_D_SHADOWS = setOf("dất", "dến", "diểm", "dồng", "dứng", "dể", "dữa")
    private val NON_VN_CHARS_REGEX = Regex("[wfjz]")
    private val NON_QU_BARE_UE_REGEX = Regex("^(?!qu).*?[uúùủũụ][eéèẻẽẹ]$")
    private val GIOA_E_GIY_REGEX = Regex("^gi[oóòỏõọ][aáàảãạăắằẳẵặeéèẻẽẹ]|^gi[yýỳỷỹỵ]")
    private val GIU_INVALID_REGEX = Regex("^gi[uúùủũụ][ôốồổỗộâấầẩẫậêếềểễệyýỳỷỹỵoóòỏõọăắằẳẵặ]")
    private val HALF_HORN_UO_REGEX = Regex("(?:u[ơớờởỡợ]|[ưứừửữự][oóòỏõọ])(ng|nh|ch|[cmnptui])|[ưứừửữự][oóòỏõọ]$")
    private val UNHORNED_UO_REGEX = Regex("^(?!(?:q|th|h|kh)u[ơớờởỡợ]$).*?u[ơớờởỡợ]")
    private val GH_NGH_INVALID = Regex("^(gh|ngh)[aăâoôơuưyáắấóốớúứýàằầòồờùừỳảẳẩỏổởủửỷãẵẫõỗỡũữỹạặậọộợụựỵ]")
    private val G_NG_INVALID = Regex("^(g(?!h|[iíìỉĩị])|ng(?!h))[eéèẻẽẹêếềểễệiíìỉĩị]")
    private val K_INVALID = Regex("^k[aăâoôơuưáắấóốớúứàằầòồờùừảẳẩỏổởủửãẵẫõỗỡũữạặậọộợụự]|^k[yýỳỷỹỵ].+")
    private val C_INVALID = Regex("^c(?!h)[eéèẻẽẹêếềểễệiíìỉĩịyýỳỷỹỵ]|^ch[yýỳỷỹỵ].+")
    private val YE_INVALID = Regex("^(?:b|ch|d|đ|g|gh|gi|h|k|kh|l|m|n|ng|ngh|nh|p|ph|r|s|t|th|tr|v|x)y[êếềểễệ]")
    private val INVALID_RHYMES = Regex("(?<![ưứừửữự])[ơớờởỡợ]ng$|[ưứừửữự][nm]$|[iíìỉĩị]c$|[eéèẻẽẹ]c$|^(?:b|ch|c|d|đ|l|m|n|t|v)u[ểễềếệ]n$|ãu$|uổn$")
    private val LABIAL_MEDIALS = Regex("^(?:b|m|p|ph|v)(?:[oóòỏõọ][aáàảãạăắằẳẵặeéèẻẽẹ]|[uúùủũụ][êếềểễệyýỳỷỹỵâấầẩẫậ])")
    private val NHIA_CHECK = Regex("^nh[iíìỉĩị][aáàảãạ]$|^nhĩa$")
    private val UNATTESTED_RHYMES = Regex("^(?:c|g|h|k|kh|n|ng|nh|p|t|x)[ưứừửữự][ơớờởỡợ]n$|^b[ưứừửữự][ớờởỡợ]n$|^ph[ưứừửữự][ơớờởợ]n$|^(?!khuya$).*u[yýỳỷỹỵ][aáàảãạ]$|^(?!khu[yỷỵ]u$).*u[yýỳỷỹỵ][uúùủũụ]$|^g[uúùủũụ][aáàảãạ]$|^g[uúùủũụ]n$")
    private val VALID_OE_ONSETS: Set<String> = setOf("", "ch", "h", "kh", "l", "ng", "nh", "t", "x", "gi", "qu")

    val VALID_VIETNAMESE_ONSETS: Set<String> = setOf(
        "", "b", "c", "ch", "d", "dd", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n",
        "ng", "ngh", "nh", "p", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x"
    )
    val ORTHO_ONSET_PAIRS: Map<String, String> = mapOf(
        "ng" to "ngh", "g" to "gh", "gh" to "g", "ngh" to "ng", "c" to "k", "k" to "c"
    )

    fun isValidOeSyllable(word: String): Boolean {
        val stem = VietnameseDictionary.stripVietnameseDiacritics(word)
        if (!stem.contains("oe")) return true
        val onset = if (stem.startsWith("qu")) "qu" else stem.takeWhile { it !in "aeiouy" }
        return VALID_OE_ONSETS.contains(onset)
    }

    fun isPhonotacticallyValidVietnameseSyllable(word: String): Boolean {
        val lower = word.lowercase()
        if (lower.isEmpty() || FAKE_D_SHADOWS.contains(lower)) return false
        if (NON_VN_CHARS_REGEX.containsMatchIn(lower)) return false
        if (lower == "buýt" || lower == "ưm" || lower == "ừm") return true
        if (NON_QU_BARE_UE_REGEX.containsMatchIn(lower)) return false
        if (GIOA_E_GIY_REGEX.containsMatchIn(lower) || GIU_INVALID_REGEX.containsMatchIn(lower)) return false
        if (HALF_HORN_UO_REGEX.containsMatchIn(lower) || UNHORNED_UO_REGEX.containsMatchIn(lower)) return false
        if (GH_NGH_INVALID.containsMatchIn(lower) || G_NG_INVALID.containsMatchIn(lower)) return false
        if (K_INVALID.containsMatchIn(lower) || C_INVALID.containsMatchIn(lower)) return false
        if (YE_INVALID.containsMatchIn(lower) || INVALID_RHYMES.containsMatchIn(lower) || LABIAL_MEDIALS.containsMatchIn(lower)) return false
        if (NHIA_CHECK.containsMatchIn(lower) || UNATTESTED_RHYMES.containsMatchIn(lower)) return false
        val lastCoda = lower.last()
        if (lastCoda == 'c' || lastCoda == 'p' || lastCoda == 't' || lower.endsWith("ch")) {
            if (!lower.any { it in "áắấéếíóốớúứýạặậẹệịọộợụựỵ" }) return false
        }
        return true
    }

    fun hasValidVietnameseOnset(rawLower: String): Boolean {
        val stripped = VietnameseDictionary.stripVietnameseDiacritics(rawLower)
        val onset = if (stripped.startsWith("qu")) "qu" else stripped.takeWhile { it !in "aeiouy" }
        val rest = stripped.drop(onset.length)
        if (onset == "ng" && (rest.startsWith("i") || rest.startsWith("e"))) return false
        if (onset == "g" && rest.startsWith("e")) return false
        if ((onset == "gh" || onset == "ngh") && (rest.startsWith("a") || rest.startsWith("o") || rest.startsWith("u"))) return false
        if (onset == "c" && (rest.startsWith("i") || rest.startsWith("e") || rest.startsWith("y"))) return false
        if (onset == "k" && (rest.startsWith("a") || rest.startsWith("o") || rest.startsWith("u"))) return false
        return onset in VALID_VIETNAMESE_ONSETS
    }
}
