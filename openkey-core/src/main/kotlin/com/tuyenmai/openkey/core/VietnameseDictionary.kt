package com.tuyenmai.openkey.core

enum class LanguagePriority { VIETNAMESE_FIRST, ENGLISH_FIRST }

object SpatialScoringWeights {
    const val COMPOUND_BIGRAM_BONUS = 1000
    const val DIRECT_ZERO_EDIT_BONUS = 600
    const val ONSET_REPAIR_BONUS = 500
    const val EXACT_MATCH_BONUS = 400
    const val FULL_TELEX_BONUS = 300
    const val TONE_MODIFIER_BONUS = 200
    const val INSERTION_PENALTY = -100
    const val DELETION_PENALTY = -150
    const val HIGH_FREQUENCY_EVIDENCE_THRESHOLD = 95
    const val DEFAULT_PRIORITY = 50
}

class VietnameseDictionary private constructor() {
    companion object {
        private val DIACRITIC_STRIP_MAP = mapOf(
            'á' to 'a', 'à' to 'a', 'ả' to 'a', 'ã' to 'a', 'ạ' to 'a', 'ă' to 'a', 'ắ' to 'a', 'ằ' to 'a', 'ẳ' to 'a', 'ẵ' to 'a', 'ặ' to 'a', 'â' to 'a', 'ấ' to 'a', 'ầ' to 'a', 'ẩ' to 'a', 'ẫ' to 'a', 'ậ' to 'a',
            'é' to 'e', 'è' to 'e', 'ẻ' to 'e', 'ẽ' to 'e', 'ẹ' to 'e', 'ê' to 'e', 'ế' to 'e', 'ề' to 'e', 'ể' to 'e', 'ễ' to 'e', 'ệ' to 'e', 'í' to 'i', 'ì' to 'i', 'ỉ' to 'i', 'ĩ' to 'i', 'ị' to 'i',
            'ó' to 'o', 'ò' to 'o', 'ỏ' to 'o', 'õ' to 'o', 'ọ' to 'o', 'ô' to 'o', 'ố' to 'o', 'ồ' to 'o', 'ổ' to 'o', 'ỗ' to 'o', 'ộ' to 'o', 'ơ' to 'o', 'ớ' to 'o', 'ờ' to 'o', 'ở' to 'o', 'ỡ' to 'o', 'ợ' to 'o',
            'ú' to 'u', 'ù' to 'u', 'ủ' to 'u', 'ũ' to 'u', 'ụ' to 'u', 'ư' to 'u', 'ứ' to 'u', 'ừ' to 'u', 'ử' to 'u', 'ữ' to 'u', 'ự' to 'u', 'ý' to 'y', 'ỳ' to 'y', 'ỷ' to 'y', 'ỹ' to 'y', 'ỵ' to 'y'
        )

        private val TELEX_BASE_MOD_MAP = mapOf(
            'a' to Pair("a", ""), 'á' to Pair("a", "s"), 'à' to Pair("a", "f"), 'ả' to Pair("a", "r"), 'ã' to Pair("a", "x"), 'ạ' to Pair("a", "j"), 'ă' to Pair("aw", ""), 'ắ' to Pair("aw", "s"), 'ằ' to Pair("aw", "f"), 'ẳ' to Pair("aw", "r"), 'ẵ' to Pair("aw", "x"), 'ặ' to Pair("aw", "j"),
            'â' to Pair("aa", ""), 'ấ' to Pair("aa", "s"), 'ầ' to Pair("aa", "f"), 'ẩ' to Pair("aa", "r"), 'ẫ' to Pair("aa", "x"), 'ậ' to Pair("aa", "j"), 'e' to Pair("e", ""), 'é' to Pair("e", "s"), 'è' to Pair("e", "f"), 'ẻ' to Pair("e", "r"), 'ẽ' to Pair("e", "x"), 'ẹ' to Pair("e", "j"),
            'ê' to Pair("ee", ""), 'ế' to Pair("ee", "s"), 'ề' to Pair("ee", "f"), 'ể' to Pair("ee", "r"), 'ễ' to Pair("ee", "x"), 'ệ' to Pair("ee", "j"), 'i' to Pair("i", ""), 'í' to Pair("i", "s"), 'ì' to Pair("i", "f"), 'ỉ' to Pair("i", "r"), 'ĩ' to Pair("i", "x"), 'ị' to Pair("i", "j"),
            'o' to Pair("o", ""), 'ó' to Pair("o", "s"), 'ò' to Pair("o", "f"), 'ỏ' to Pair("o", "r"), 'õ' to Pair("o", "x"), 'ọ' to Pair("o", "j"), 'ô' to Pair("oo", ""), 'ố' to Pair("oo", "s"), 'ồ' to Pair("oo", "f"), 'ổ' to Pair("oo", "r"), 'ỗ' to Pair("oo", "x"), 'ộ' to Pair("oo", "j"),
            'ơ' to Pair("ow", ""), 'ớ' to Pair("ow", "s"), 'ờ' to Pair("ow", "f"), 'ở' to Pair("ow", "r"), 'ỡ' to Pair("ow", "x"), 'ợ' to Pair("ow", "j"), 'u' to Pair("u", ""), 'ú' to Pair("u", "s"), 'ù' to Pair("u", "f"), 'ủ' to Pair("u", "r"), 'ũ' to Pair("u", "x"), 'ụ' to Pair("u", "j"),
            'ư' to Pair("uw", ""), 'ứ' to Pair("uw", "s"), 'ừ' to Pair("uw", "f"), 'ử' to Pair("uw", "r"), 'ữ' to Pair("uw", "x"), 'ự' to Pair("uw", "j"), 'y' to Pair("y", ""), 'ý' to Pair("y", "s"), 'ỳ' to Pair("y", "f"), 'ỷ' to Pair("y", "r"), 'ỹ' to Pair("y", "x"), 'ỵ' to Pair("y", "j"), 'đ' to Pair("dd", "")
        )

        private val U_HORN_CHARS = setOf('ư', 'ứ', 'ừ', 'ử', 'ữ', 'ự')
        private val O_HORN_CHARS = setOf('ơ', 'ớ', 'ờ', 'ở', 'ỡ', 'ợ')
        private val FAKE_D_SHADOWS = setOf("dất", "dến", "diểm", "dồng", "dứng", "dể", "dữa")
        private val HALF_HORN_UO_REGEX = Regex("u[ơớờởỡợ](ng|nh|ch|[cmnptui])")
        private val UNHORNED_UO_REGEX = Regex("^(?!(?:qu|th|kh|h)u[ơớờởỡợ]$).*?u[ơớờởỡợ]")
        private val GH_NGH_INVALID = Regex("^(gh|ngh)[aăâoôơuưyáắấóốớúứýàằầòồờùừỳảẳẩỏổởủửỷãẵẫõỗỡũữỹạặậọộợụựỵ]")
        private val G_NG_INVALID = Regex("^(g(?!h|i)|ng(?!h))[eêièềìéếíẻểỉẽễĩẹệị]")
        private val K_INVALID = Regex("^k[aăâoôơuưáắấóốớúứýàằầòồờùừỳảẳẩỏổởủửỷãẵẫõỗỡũữỹạặậọộợụựỵ]|^ky[a-zà-ỹ]")
        private val C_INVALID = Regex("^c(?!h)[eêiyèềìỳéếíýẻểỉỷẽễĩỹẹệịỵ]|^chy[a-zà-ỹ]")
        private val YE_INVALID = Regex("^(?:b|ch|d|đ|g|gh|gi|h|k|kh|l|m|n|ng|ngh|nh|p|ph|r|s|t|th|tr|v|x)y[êếềểễệ]")
        private val INVALID_RHYMES = Regex("(?<![ưứừửữự])[ơớờởỡợ]ng$|[ưứừửữự][nm]$|[iíìỉĩị]c$|[eéèẻẽẹ]c$|^(?:b|ch|c|d|đ|l|m|n|t|v)u[ểễềếệ]n$")
        private val LABIAL_MEDIALS = Regex("^(?:b|m|p|ph|v)(?:oa|oă|oe|uê|uy|uâ)|^giu[ôốồổỗộâấầẩẫậêếềểễệyýỳỷỹỵ]")

        val shared by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { VietnameseDictionary() }
        @Volatile var isPrewarmed: Boolean = false

        fun prewarmAsync(onComplete: (() -> Unit)? = null) {
            if (isPrewarmed) { onComplete?.invoke(); return }
            Thread({ shared; isPrewarmed = true; onComplete?.invoke() }, "openkey-dict-prewarm").apply { isDaemon = true; start() }
        }

        fun stripVietnameseDiacritics(text: String): String {
            val result = text.lowercase().replace("đ", "d")
            val sb = StringBuilder(result.length)
            for (ch in result) sb.append(DIACRITIC_STRIP_MAP[ch] ?: ch)
            return sb.toString()
        }

        fun extractVietnameseOnset(asciiWord: String): String {
            val lower = asciiWord.lowercase()
            for (onset in listOf("ngh", "ng", "nh", "ch", "gh", "gi", "kh", "ph", "qu", "th", "tr")) {
                if (lower.startsWith(onset)) return onset
            }
            return if (lower.isNotEmpty() && "bcdghklmnpqrstvx".contains(lower[0])) lower.substring(0, 1) else ""
        }

        fun canonicalTelexKey(word: String): String {
            val lower = word.lowercase()
            val out = StringBuilder()
            var toneSuffix = ""
            var i = 0
            while (i < lower.length) {
                val ch = lower[i]
                if (U_HORN_CHARS.contains(ch) && i + 1 < lower.length && O_HORN_CHARS.contains(lower[i + 1])) {
                    val p = TELEX_BASE_MOD_MAP[ch]
                    if (p != null && p.second.isNotEmpty()) toneSuffix = p.second
                    out.append("u"); i++; continue
                }
                val p = TELEX_BASE_MOD_MAP[ch]
                if (p != null) {
                    out.append(p.first)
                    if (p.second.isNotEmpty()) toneSuffix = p.second
                } else out.append(ch)
                i++
            }
            out.append(toneSuffix)
            return out.toString()
        }

        val QWERTY_NEIGHBORS: Map<Char, String> = mapOf(
            'q' to "wa", 'w' to "qase", 'e' to "wsdr", 'r' to "edft", 't' to "rfgy",
            'y' to "tghu", 'u' to "yhji", 'i' to "ujko", 'o' to "iklp", 'p' to "ol",
            'a' to "qwsz", 's' to "weadzx", 'd' to "erfcxs", 'f' to "rtgvcd", 'g' to "tyhbvf",
            'h' to "yujnbg", 'j' to "uikmnh", 'k' to "iojlm", 'l' to "opk",
            'z' to "asx", 'x' to "sdzc", 'c' to "dfxv", 'v' to "fgcb", 'b' to "ghvn",
            'n' to "hjbm", 'm' to "jkn"
        )

        fun isQwertyAdjacent(c1: Char, c2: Char): Boolean = QWERTY_NEIGHBORS[c1]?.contains(c2) == true

        fun extractToneKey(word: String): String {
            for (ch in word.lowercase()) {
                if (ch in "áắấéếíóốớúứý") return "s"
                if (ch in "àằầèềìòồờùừỳ") return "f"
                if (ch in "ảẳẩẻểỉỏổởủửỷ") return "r"
                if (ch in "ãẵẫẽễĩõỗỡũữỹ") return "x"
                if (ch in "ạặậẹệịọộợụựỵ") return "j"
            }
            return ""
        }

        fun endModifierTelexKeys(word: String): List<String> {
            val asciiBase = stripVietnameseDiacritics(word)
            val toneKey = extractToneKey(word)
            val lower = word.lowercase()
            val mod = when {
                lower.contains("ă") || lower.contains("ơ") || lower.contains("ư") -> "w"
                lower.contains("â") -> "a"
                lower.contains("ê") -> "e"
                lower.contains("ô") -> "o"
                else -> null
            } ?: return emptyList()
            val variants = mutableListOf(asciiBase + mod + toneKey)
            if (toneKey.isNotEmpty()) variants.add(asciiBase + toneKey + mod)
            if (lower.startsWith("đ")) {
                variants.add("d" + asciiBase + mod + toneKey)
                if (toneKey.isNotEmpty()) variants.add("d" + asciiBase + toneKey + mod)
            }
            return variants
        }

        fun inlineToneTelexKeys(word: String): List<String> {
            val canon = canonicalTelexKey(word)
            val toneChar = canon.lastOrNull() ?: return emptyList()
            if (!"sfrxj".contains(toneChar)) return emptyList()
            val tone = toneChar.toString()
            val body = canon.dropLast(1)
            val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y', 'w')
            val variants = mutableListOf<String>()
            for (i in body.indices) {
                if (vowels.contains(body[i]) && i + 1 < body.length) {
                    val cand = body.substring(0, i + 1) + tone + body.substring(i + 1)
                    if (!variants.contains(cand)) variants.add(cand)
                }
            }
            if (body.endsWith("ng")) {
                val withoutN = body.dropLast(2) + tone + "g"
                if (!variants.contains(withoutN)) variants.add(withoutN)
            }
            return variants
        }

        fun canonicalizeTelexKeystrokes(raw: String): String {
            val lower = raw.lowercase()
            val vowels = setOf('a', 'e', 'i', 'o', 'u', 'y')
            val tones = setOf('s', 'f', 'r', 'x', 'j')
            if (lower.length < 3) return lower
            val firstVowelIdx = lower.indexOfFirst { vowels.contains(it) }
            if (firstVowelIdx < 0 || firstVowelIdx + 1 >= lower.length - 1) return lower
            for (i in (firstVowelIdx + 1) until (lower.length - 1)) {
                if (tones.contains(lower[i])) return lower.removeRange(i, i + 1) + lower[i]
            }
            return lower
        }

        val VALID_VIETNAMESE_ONSETS: Set<String> = setOf("", "b", "c", "ch", "d", "dd", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh", "nh", "p", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x")
        val ORTHO_ONSET_PAIRS: Map<String, String> = mapOf("ng" to "ngh", "g" to "gh", "gh" to "g", "ngh" to "ng", "c" to "k", "k" to "c")

        fun hasValidVietnameseOnset(rawLower: String): Boolean {
            val onset = rawLower.takeWhile { it !in "aeiouy" }
            val rest = rawLower.drop(onset.length)
            if (onset == "ng" && (rest.startsWith("i") || rest.startsWith("e"))) return false
            if (onset == "g" && (rest.startsWith("i") || rest.startsWith("e"))) return false
            if ((onset == "gh" || onset == "ngh") && (rest.startsWith("a") || rest.startsWith("o") || rest.startsWith("u"))) return false
            if (onset == "c" && (rest.startsWith("i") || rest.startsWith("e") || rest.startsWith("y"))) return false
            if (onset == "k" && (rest.startsWith("a") || rest.startsWith("o") || rest.startsWith("u"))) return false
            return onset in VALID_VIETNAMESE_ONSETS
        }

        private val validOeOnsets: Set<String> = setOf("", "ch", "h", "kh", "l", "ng", "nh", "t", "x", "gi", "qu")
        private fun isValidOeSyllable(word: String): Boolean {
            val stem = stripVietnameseDiacritics(word)
            if (!stem.contains("oe")) return true
            val onset = stem.takeWhile { it !in "aeiouy" }
            return validOeOnsets.contains(onset)
        }

        fun isPhonotacticallyValidVietnameseSyllable(word: String): Boolean {
            val lower = word.lowercase()
            if (lower.isEmpty() || FAKE_D_SHADOWS.contains(lower)) return false
            if (HALF_HORN_UO_REGEX.containsMatchIn(lower) || UNHORNED_UO_REGEX.containsMatchIn(lower)) return false
            if (GH_NGH_INVALID.containsMatchIn(lower) || G_NG_INVALID.containsMatchIn(lower)) return false
            if (K_INVALID.containsMatchIn(lower) || C_INVALID.containsMatchIn(lower)) return false
            if (YE_INVALID.containsMatchIn(lower) || INVALID_RHYMES.containsMatchIn(lower) || LABIAL_MEDIALS.containsMatchIn(lower)) return false
            if (lower.matches(Regex("^nh[iíìỉĩị][aáàảãạ]$|^nhĩa$"))) return false
            val lastCoda = lower.last()
            if (lastCoda == 'c' || lastCoda == 'p' || lastCoda == 't' || lower.endsWith("ch")) {
                if (!lower.any { it in "áắấéếíóốớúứýạặậẹệịọộợụựỵ" }) return false
            }
            return true
        }

        fun isValidVietnameseSyllable(word: String): Boolean = shared.isValidVietnameseSyllable(word)
        fun isEnglishWord(lower: String): Boolean = shared.isEnglishWord(lower)
    }

    fun stripVietnameseDiacritics(text: String): String = VietnameseDictionary.stripVietnameseDiacritics(text)

    val syllableSet: Set<String>
    val stemToWords: Map<String, List<String>>
    val telexSignatureToWords: Map<String, List<String>>
    val compoundBigrams: Map<String, List<String>> = VietnameseDictionaryCorpus.rawCompoundBigrams
    val telexKeyToSyllable: Map<String, String>
    val stemToneToSyllable: Map<String, String>

    val englishWords: Set<String> = VietnameseDictionaryCorpus.englishWords
    val englishPriorityWords: Set<String> = VietnameseDictionaryCorpus.englishPriorityWords
    val englishLookbackStems: Set<String> = VietnameseDictionaryCorpus.englishLookbackStems
    val keepWords: Set<String> = VietnameseDictionaryCorpus.keepWords
    val validTelexKeys: Set<String>
    val englishFrequencyRanked: List<String> = VietnameseDictionaryCorpus.freqRankedList
    val englishPrefixMap: Map<Char, List<String>>
    val englishNextWordBigrams: Map<String, List<String>> = VietnameseDictionaryCorpus.englishNextWordBigrams
    val englishTypoCorrections: Map<String, String> = VietnameseDictionaryCorpus.englishTypoCorrections
    val vietnameseTypoCorrections: Map<String, String> = VietnameseDictionaryCorpus.vietnameseTypoCorrections
    val vietnameseBigramGrammar: Map<String, String> = VietnameseDictionaryCorpus.vietnameseBigramGrammar
    val englishContractionGrammar: Map<String, String> = VietnameseDictionaryCorpus.englishContractionGrammar
    val englishBigramGrammar: Map<String, String> = VietnameseDictionaryCorpus.englishBigramGrammar
    val englishDuplicateCollapseWords: Set<String> = VietnameseDictionaryCorpus.englishDuplicateCollapseWords

    init {
        val rawSyllables = VietnameseDictionaryCorpus.rawSyllables
            .filter { isValidOeSyllable(it) && isPhonotacticallyValidVietnameseSyllable(it) }
            .toSet()
        this.syllableSet = rawSyllables
        this.validTelexKeys = rawSyllables.map { canonicalTelexKey(it) }.toSet()
        val priorityMap = VietnameseDictionaryCorpus.priorityMap

        val stems = mutableMapOf<String, MutableList<String>>()
        val signatures = mutableMapOf<String, MutableList<String>>()
        for (word in rawSyllables) {
            val stem = stripVietnameseDiacritics(word)
            stems.getOrPut(stem) { mutableListOf() }.add(word)
            val sig = stem.toCharArray().sorted().joinToString("")
            signatures.getOrPut(sig) { mutableListOf() }.add(word)
        }
        for ((_, list) in stems) list.sortWith(compareByDescending { priorityMap[it] ?: SpatialScoringWeights.DEFAULT_PRIORITY })
        for ((_, list) in signatures) list.sortWith(compareByDescending { priorityMap[it] ?: SpatialScoringWeights.DEFAULT_PRIORITY })
        this.stemToWords = stems
        this.telexSignatureToWords = signatures

        val telexMap = mutableMapOf<String, String>()
        fun putCandidate(map: MutableMap<String, String>, key: String, word: String) {
            if (key.length < 2) return
            val wordPrio = priorityMap[word] ?: SpatialScoringWeights.DEFAULT_PRIORITY
            if (wordPrio > (priorityMap[map[key]] ?: -1)) map[key] = word
        }

        for (word in rawSyllables) {
            val canon = canonicalTelexKey(word)
            putCandidate(telexMap, canon, word)
            if (canon.contains("uow")) putCandidate(telexMap, canon.replaceFirst("uow", "uwow"), word)
            for (endMod in endModifierTelexKeys(word)) putCandidate(telexMap, endMod, word)
            for (inlineTone in inlineToneTelexKeys(word)) putCandidate(telexMap, inlineTone, word)
        }
        this.telexKeyToSyllable = telexMap

        val stemMap = mutableMapOf<String, String>()
        for (word in rawSyllables) {
            val hasTone = word.any { it in "áắấéếíóốớúứýàằầèềìòồờùừỳảẳẩẻểỉỏổởủửỷãẵẫẽễĩõỗỡũữỹạặậẹệịọộợụựỵ" }
            val hasHatOrHorn = word.any { it in "ăắằẳẵặâấầẩẫậêếềểễệôốồổỗộơớờởỡợưứừửữựđ" }
            if (hasTone && hasHatOrHorn) {
                val toneKey = extractToneKey(word)
                if (toneKey.isNotEmpty()) {
                    val asciiBase = stripVietnameseDiacritics(word)
                    val stemKey1 = asciiBase + toneKey
                    if (telexMap[stemKey1] == null) putCandidate(stemMap, stemKey1, word)
                    if (word.startsWith("đ")) {
                        val stemKey2 = "d" + asciiBase + toneKey
                        if (telexMap[stemKey2] == null) putCandidate(stemMap, stemKey2, word)
                        val singleDKey = canonicalTelexKey(word).let { if (it.startsWith("dd")) it.substring(1) else it }
                        if (telexMap[singleDKey] == null) putCandidate(stemMap, singleDKey, word)
                    }
                }
            }
        }
        this.stemToneToSyllable = stemMap

        val prefixMap = mutableMapOf<Char, MutableList<String>>()
        val rankedList = (englishFrequencyRanked + englishPriorityWords + englishLookbackStems + englishWords).distinct()
        for (w in rankedList) if (w.isNotEmpty()) prefixMap.getOrPut(w[0]) { mutableListOf() }.add(w)
        this.englishPrefixMap = prefixMap
    }

    fun isQwertyAdjacent(c1: Char, c2: Char): Boolean = VietnameseDictionary.isQwertyAdjacent(c1, c2)

    fun findEnglishTranspositionMatch(rawLower: String): String? {
        if (rawLower.length < 4 || isEnglishWord(rawLower) || englishWords.contains(rawLower) || isValidVietnameseSyllable(rawLower) || keepWords.contains(rawLower) || validTelexKeys.contains(rawLower)) {
            return null
        }
        englishTypoCorrections[rawLower]?.let { return it }

        val chars = rawLower.toCharArray()
        for (i in 0 until (chars.size - 1)) {
            val tmp = chars[i]; chars[i] = chars[i + 1]; chars[i + 1] = tmp
            val candidate = String(chars)
            if (englishPriorityWords.contains(candidate) || englishWords.contains(candidate)) return candidate
            chars[i + 1] = chars[i]; chars[i] = tmp
        }

        if (rawLower.length >= 5) {
            for (i in 0 until rawLower.length) {
                val candidate = rawLower.removeRange(i, i + 1)
                if (candidate.length >= 4 && (englishPriorityWords.contains(candidate) || englishWords.contains(candidate))) {
                    val ch = rawLower[i]
                    val isDuplicate = (i > 0 && ch == rawLower[i - 1]) || (i + 1 < rawLower.length && ch == rawLower[i + 1])
                    val isNeighbor = (i > 0 && isQwertyAdjacent(ch, rawLower[i - 1])) || (i + 1 < rawLower.length && isQwertyAdjacent(ch, rawLower[i + 1]))
                    if (isDuplicate || isNeighbor || candidate.contains(ch)) return candidate
                }
            }
        }
        return null
    }

    fun findVietnameseSpatialMatch(rawLower: String, composedLower: String, previousWord: String? = null): String? =
        findVietnameseSpatialCandidates(rawLower, composedLower, previousWord, 1).firstOrNull()

    fun findVietnameseSpatialCandidates(rawLower: String, composedLower: String, previousWord: String? = null, limit: Int = 3): List<String> {
        if (rawLower.length < 3 || isValidVietnameseSyllable(composedLower) || isEnglishWord(rawLower) || isEnglishWord(composedLower) || keepWords.contains(rawLower)) return emptyList()

        data class CandidateMatch(val syllable: String, val isFullTelex: Boolean, val isExactMatch: Boolean, val isOnsetRepaired: Boolean, val isDirectZeroEdit: Boolean, val isDeletion: Boolean, val isInsertion: Boolean = false)

        val candidateMap = mutableMapOf<String, CandidateMatch>()
        fun lookupTelexCandidate(candKey: String): Pair<String, Boolean>? {
            telexKeyToSyllable[candKey]?.let { return Pair(it, true) }
            val canonKey = canonicalizeTelexKeystrokes(candKey)
            if (canonKey != candKey) telexKeyToSyllable[canonKey]?.let { return Pair(it, true) }
            stemToneToSyllable[candKey]?.let { return Pair(it, false) }
            if (canonKey != candKey) stemToneToSyllable[canonKey]?.let { return Pair(it, false) }
            return null
        }

        fun addCandidate(candKey: String, isExact: Boolean, isOnsetRepaired: Boolean, isDirectZeroEdit: Boolean = false, isDeletion: Boolean = false, isInsertion: Boolean = false) {
            val (syllable, isFull) = lookupTelexCandidate(candKey) ?: return
            if (syllable.none { it.code > 127 }) return
            val ex = candidateMap[syllable]
            candidateMap[syllable] = CandidateMatch(
                syllable = syllable,
                isFullTelex = ex?.isFullTelex ?: isFull || isFull,
                isExactMatch = ex?.isExactMatch ?: isExact || isExact,
                isOnsetRepaired = ex?.isOnsetRepaired ?: isOnsetRepaired || isOnsetRepaired,
                isDirectZeroEdit = ex?.isDirectZeroEdit ?: isDirectZeroEdit || isDirectZeroEdit,
                isDeletion = (ex?.isDeletion ?: true) && isDeletion,
                isInsertion = (ex?.isInsertion ?: true) && isInsertion
            )
        }

        val canonRaw = canonicalizeTelexKeystrokes(rawLower)
        val rawOnset = rawLower.takeWhile { it !in "aeiouy" }
        val hadInvalidOnset = !hasValidVietnameseOnset(rawLower)
        fun checkRepairedOnset(candKey: String): Boolean = hadInvalidOnset && hasValidVietnameseOnset(candKey) &&
            candKey.takeWhile { it !in "aeiouy" }.let { if (rawOnset !in VALID_VIETNAMESE_ONSETS) it in VALID_VIETNAMESE_ONSETS else it == ORTHO_ONSET_PAIRS[rawOnset] }

        // Class 1: Exact Omitted Modifier / Fast Telex & Stored Typo
        addCandidate(rawLower, isExact = true, isOnsetRepaired = false, isDirectZeroEdit = true)
        if (canonRaw != rawLower) addCandidate(canonRaw, isExact = true, isOnsetRepaired = false, isDirectZeroEdit = true)
        (vietnameseTypoCorrections[composedLower] ?: vietnameseTypoCorrections[rawLower])?.let { typo ->
            candidateMap[typo] = CandidateMatch(syllable = typo, isFullTelex = true, isExactMatch = true, isOnsetRepaired = false, isDirectZeroEdit = true, isDeletion = false)
        }

        // Class 2: 1-Edit QWERTY-Neighbor Substitution (Any Position)
        val keysToMutate = if (canonRaw != rawLower) listOf(rawLower, canonRaw) else listOf(rawLower)
        for (baseStr in keysToMutate) {
            val chars = baseStr.toCharArray()
            for (i in chars.indices) {
                for (nb in (QWERTY_NEIGHBORS[chars[i]] ?: continue)) {
                    val mutated = chars.clone(); mutated[i] = nb
                    val candKey = String(mutated)
                    addCandidate(candKey, isExact = false, isOnsetRepaired = checkRepairedOnset(candKey))
                }
            }
        }

        // Class 3: Adjacent Key Transposition (Any Position)
        for (baseStr in keysToMutate) {
            val chars = baseStr.toCharArray()
            for (i in 0 until (chars.size - 1)) {
                val tmp = chars[i]; chars[i] = chars[i + 1]; chars[i + 1] = tmp
                val candKey = String(chars)
                addCandidate(candKey, isExact = false, isOnsetRepaired = checkRepairedOnset(candKey))
                val tmp2 = chars[i]; chars[i] = chars[i + 1]; chars[i + 1] = tmp2
            }
        }

        // Class 4: 1-Extra-Key Deletion (for rawLower.length >= 5)
        if (rawLower.length >= 5) {
            for (baseStr in keysToMutate) {
                val chars = baseStr.toCharArray()
                for (i in 1 until chars.size) {
                    if (i == 1 && chars[0] == chars[1] && (chars[0] == 'd' || chars[0] == 'a' || chars[0] == 'e' || chars[0] == 'o')) continue
                    val deletedChar = chars[i]
                    val isDuplicate = (deletedChar == chars[i - 1]) || (i + 1 < chars.size && deletedChar == chars[i + 1])
                    val isNeighbor = isQwertyAdjacent(deletedChar, chars[i - 1]) || (i + 1 < chars.size && isQwertyAdjacent(deletedChar, chars[i + 1]))
                    if (isDuplicate || isNeighbor) {
                        val candKey = StringBuilder(baseStr).deleteCharAt(i).toString()
                        addCandidate(candKey, isExact = false, isOnsetRepaired = checkRepairedOnset(candKey), isDeletion = true)
                    }
                }
            }
        }

        // Class 5: 1-Missing-Key Insertion (for rawLower.length in 4..7)
        if (rawLower.length in 4..7) {
            val tones = setOf('s', 'f', 'r', 'x', 'j')
            val insertionKeys = "hgnrtcpmuoiaeywd"
            for (baseStr in keysToMutate) {
                val (stem, extractedTone) = if (baseStr.isNotEmpty() && baseStr.last() in tones) Pair(baseStr.dropLast(1), baseStr.last().toString()) else Pair(baseStr, "")
                for (pos in 1..stem.length) {
                    for (insChar in insertionKeys) {
                        val candKey = stem.substring(0, pos) + insChar + stem.substring(pos) + extractedTone
                        addCandidate(candKey = candKey, isExact = false, isOnsetRepaired = checkRepairedOnset(candKey), isInsertion = true)
                    }
                }
            }
        }

        val prevClean = previousWord?.trim()?.lowercase() ?: ""
        val priorityMap = VietnameseDictionaryCorpus.priorityMap
        val rawHasToneMod = rawLower.any { it in "sfrxjw" }
        val composedInvalid = !isPhonotacticallyValidVietnameseSyllable(composedLower)

        val scoredCandidates = mutableListOf<Pair<String, Int>>()
        for ((syllable, match) in candidateMap) {
            val hasEvidence = match.isDirectZeroEdit || match.isExactMatch || match.isOnsetRepaired || match.isInsertion ||
                    (prevClean.isNotEmpty() && compoundBigrams[prevClean]?.contains(syllable) == true) || (rawHasToneMod && composedInvalid) ||
                    (priorityMap[syllable] ?: SpatialScoringWeights.DEFAULT_PRIORITY) >= SpatialScoringWeights.HIGH_FREQUENCY_EVIDENCE_THRESHOLD
            if (!hasEvidence) continue

            var score = priorityMap[syllable] ?: SpatialScoringWeights.DEFAULT_PRIORITY
            if (prevClean.isNotEmpty() && compoundBigrams[prevClean]?.contains(syllable) == true) score += SpatialScoringWeights.COMPOUND_BIGRAM_BONUS
            if (match.isDirectZeroEdit) score += SpatialScoringWeights.DIRECT_ZERO_EDIT_BONUS
            if (match.isDeletion) score += SpatialScoringWeights.DELETION_PENALTY
            if (match.isInsertion) score += SpatialScoringWeights.INSERTION_PENALTY
            if (match.isExactMatch) score += SpatialScoringWeights.EXACT_MATCH_BONUS
            if (match.isOnsetRepaired) score += SpatialScoringWeights.ONSET_REPAIR_BONUS
            if (match.isFullTelex) score += SpatialScoringWeights.FULL_TELEX_BONUS
            if (rawHasToneMod && syllable != stripVietnameseDiacritics(syllable)) score += SpatialScoringWeights.TONE_MODIFIER_BONUS
            scoredCandidates.add(Pair(syllable, score))
        }

        scoredCandidates.sortWith(compareByDescending { it.second })
        return scoredCandidates.take(limit).map { it.first }
    }

    fun computeEnglishCandidates(rawStem: String, limit: Int = 3): List<String> {
        val clean = rawStem.lowercase().trim()
        if (clean.isEmpty()) return emptyList()
        val matches = mutableListOf<String>()

        if (clean == "i") matches.add("I")
        englishContractionGrammar[clean]?.let { if (!matches.contains(it)) matches.add(it) }
        englishTypoCorrections[clean]?.let { if (!matches.contains(it)) matches.add(it) }
        if (englishWords.contains(clean) && !matches.any { it.equals(clean, ignoreCase = true) }) matches.add(clean)

        for (w in (englishPrefixMap[clean[0]] ?: emptyList())) {
            if (w.startsWith(clean) && !matches.any { it.equals(w, ignoreCase = true) }) {
                matches.add(w)
                if (matches.size >= limit) break
            }
        }
        if (matches.size < limit) {
            findEnglishTranspositionMatch(clean)?.let { if (!matches.any { m -> m.equals(it, ignoreCase = true) }) matches.add(it) }
        }

        val result = matches.take(limit)
        return when {
            rawStem.length >= 2 && rawStem.all { it.isUpperCase() } -> result.map { it.uppercase() }
            rawStem.isNotEmpty() && rawStem.first().isUpperCase() -> result.map { if (it == "I" || it == "I'm") it else it.replaceFirstChar { c -> c.uppercase() } }
            else -> result
        }
    }

    fun computeEnglishNextWordCandidates(previousWord: String, maxCount: Int = 3): List<String> =
        previousWord.trim().lowercase().let { if (it.isEmpty()) emptyList() else (englishNextWordBigrams[it] ?: emptyList()).take(maxCount) }

    fun isValidVietnameseSyllable(word: String): Boolean =
        word.lowercase().let { isPhonotacticallyValidVietnameseSyllable(it) && syllableSet.contains(it) }

    fun isEnglishPriorityWord(rawAscii: String): Boolean = englishPriorityWords.contains(rawAscii.lowercase())

    fun isEnglishWord(lower: String): Boolean {
        if (englishWords.contains(lower)) return true
        if (lower.length < 3 || isValidVietnameseSyllable(lower) || validTelexKeys.contains(lower)) return false
        fun checkStem(s: String) = englishWords.contains(s) || (s.length >= 3 && s[s.length - 1] == s[s.length - 2] && englishWords.contains(s.dropLast(1)))
        if (lower.endsWith("ies") && lower.length > 4 && englishWords.contains(lower.dropLast(3) + "y")) return true
        if (lower.endsWith("es") && lower.length > 3 && (englishWords.contains(lower.dropLast(2)) || englishWords.contains(lower.dropLast(1)))) return true
        if (lower.endsWith("s") && !lower.endsWith("ss") && lower.length > 3 && englishWords.contains(lower.dropLast(1))) return true
        if (lower.endsWith("ied") && lower.length > 4 && englishWords.contains(lower.dropLast(3) + "y")) return true
        if (lower.endsWith("ed") && lower.length > 3 && (englishWords.contains(lower.dropLast(2)) || checkStem(lower.dropLast(2)) || englishWords.contains(lower.dropLast(1)))) return true
        if (lower.endsWith("d") && lower.length > 3 && englishWords.contains(lower.dropLast(1))) return true
        if (lower.endsWith("ing") && lower.length > 4 && (englishWords.contains(lower.dropLast(3)) || checkStem(lower.dropLast(3)) || englishWords.contains(lower.dropLast(3) + "e"))) return true
        if ((lower.endsWith("ers") || lower.endsWith("ors")) && lower.length > 4 && (englishWords.contains(lower.dropLast(3)) || englishWords.contains(lower.dropLast(2)) || englishWords.contains(lower.dropLast(3) + "e"))) return true
        if ((lower.endsWith("er") || lower.endsWith("or")) && lower.length > 3 && (englishWords.contains(lower.dropLast(2)) || checkStem(lower.dropLast(2)) || englishWords.contains(lower.dropLast(1)) || englishWords.contains(lower.dropLast(2) + "e"))) return true
        if (lower.endsWith("ly") && lower.length > 4 && (englishWords.contains(lower.dropLast(2)) || englishWords.contains(lower.dropLast(2) + "e"))) return true
        return false
    }
}
