// SPDX-License-Identifier: AGPL-3.0-or-later
package app.nijiyome.novel.tts.kitten

/**
 * KittenTTS text front end (pure): English clean-up, sentence chunks, the punctuation-preserving phrase split for `espeak-ng`, and
 * phonemes → model token ids. Port of Tatakai Next's `tk-app/src/tts/text.rs`, itself from the Apache-2.0 JS SDK
 * `@biwills/kittentts` and the symbol table of KittenTTS (`KittenML/KittenTTS`, Apache-2.0); see THIRD_PARTY_NOTICES.md.
 */
internal object KittenText {
    private const val PAD = '$'
    private const val PUNCTUATION = "\u003B\u003A\u002C\u002E\u0021\u003F\u00A1\u00BF\u2014\u2026\u0022\u00AB\u00BB\u201C\u201D\u0020"
    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val LETTERS_IPA =
        "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞ" +
            "ɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯ" +
            "ɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃ" +
            "ʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀ" +
            "ǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘" +
            "'̩'ᵻ"

    /** Punctuation kept around the phrases sent to `espeak-ng` (it drops punctuation from its IPA output). */
    private val PRESERVED = setOf(',', ':', ';', '.', '!', '?', '¡', '¿', '—', '…', '«', '»', '"', '“', '”')

    /** Abbreviations whose final dot is not a sentence boundary. */
    private val NON_BOUNDARY = setOf(
        "dr", "prof", "mr", "mrs", "ms", "fig", "figs", "pp", "p", "ch", "sec", "jan", "feb", "mar", "apr",
        "jun", "jul", "aug", "sep", "sept", "oct", "nov", "dec", "al", "vs",
    )

    /** Longest chunk the model gets at once (characters). */
    const val MAX_CHUNK: Int = 400

    /** Symbol → id, like the Python `{s: i for i, s in enumerate(symbols)}`: a repeated symbol keeps its last id. */
    val symbolIds: Map<Char, Long> by lazy {
        val m = HashMap<Char, Long>()
        (PAD + PUNCTUATION + LETTERS + LETTERS_IPA).forEachIndexed { i, c -> m[c] = i.toLong() }
        m
    }

    /** Runs of letters / digits / `_` are words, every other non-space character is a token of its own; joined by spaces. */
    fun tokenizePhonemes(phonemes: String): String {
        val tokens = ArrayList<String>()
        val word = StringBuilder()
        for (c in phonemes) {
            if (c.isLetterOrDigit() || c == '_') {
                word.append(c)
                continue
            }
            if (word.isNotEmpty()) {
                tokens += word.toString()
                word.clear()
            }
            if (!c.isWhitespace()) tokens += c.toString()
        }
        if (word.isNotEmpty()) tokens += word.toString()
        return tokens.joinToString(" ")
    }

    /** Model input ids of a phoneme string: `[0, ...symbols, 10, 0]` (unknown symbols skipped). */
    fun inputIds(phonemes: String): LongArray {
        val map = symbolIds
        val ids = ArrayList<Long>()
        ids += 0L
        for (c in tokenizePhonemes(phonemes)) map[c]?.let { ids += it }
        ids += 10L
        ids += 0L
        return ids.toLongArray()
    }

    /** Drops URLs / e-mails / markup, expands plain numbers to words, collapses whitespace. */
    fun preprocess(text: String): String {
        val words = ArrayList<String>()
        for (raw in text.split(Regex("\\s+"))) {
            if (raw.isEmpty()) continue
            val lower = raw.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) continue
            if (raw.contains('@') && raw.contains('.') && !raw.startsWith('@')) continue
            val cleaned = raw.filterNot { it in "<>*_#~`|" }
            if (cleaned.isEmpty()) continue
            words += expandNumberToken(cleaned)
        }
        return words.joinToString(" ")
    }

    private fun expandNumberToken(tok: String): String {
        val start = tok.indexOfFirst { it in '0'..'9' }
        if (start < 0) return tok
        val prefix = tok.substring(0, start)
        val rest = tok.substring(start)
        if (prefix.isNotEmpty() && prefix != "-" && prefix != "(" && prefix != "\"") return tok
        val end = rest.indexOfFirst { !(it in '0'..'9' || it == ',' || it == '.') }.let { if (it < 0) rest.length else it }
        val num = rest.substring(0, end)
        val suffix = rest.substring(end)
        val numTrim = num.trimEnd('.', ',')
        val tail = num.substring(numTrim.length)
        if (suffix.isNotEmpty() && suffix[0].isLetter()) return tok // `2nd`, `10km`: left to espeak
        val digits = numTrim.filter { it != ',' }
        val dot = digits.indexOf('.')
        val intPart = if (dot >= 0) digits.substring(0, dot) else digits
        val frac = if (dot >= 0) digits.substring(dot + 1) else null
        val n = intPart.toULongOrNull() ?: return tok
        val out = StringBuilder()
        out.append(if (prefix == "-") "minus " else prefix)
        out.append(numberWords(n.toLong()))
        if (frac != null && frac.isNotEmpty() && frac.all { it in '0'..'9' }) {
            out.append(" point")
            for (d in frac) out.append(' ').append(ONES[d - '0'])
        }
        out.append(tail).append(suffix)
        return out.toString()
    }

    private val ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val SCALE = listOf("", "thousand", "million", "billion", "trillion", "quadrillion", "quintillion")

    private fun belowThousand(n: Long): String {
        val parts = ArrayList<String>()
        val h = n / 100
        val r = (n % 100).toInt()
        if (h > 0) parts += "${ONES[h.toInt()]} hundred"
        if (r > 0) {
            parts += when {
                r < 20 -> ONES[r]
                r % 10 == 0 -> TENS[r / 10]
                else -> "${TENS[r / 10]}-${ONES[r % 10]}"
            }
        }
        return parts.joinToString(" ")
    }

    /** English words of [n] (short scale). */
    fun numberWords(n: Long): String {
        if (n == 0L) return "zero"
        val groups = ArrayList<Long>()
        var rest = n
        while (rest > 0) {
            groups += rest % 1000
            rest /= 1000
        }
        val parts = ArrayList<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0L) continue
            val w = belowThousand(g)
            parts += if (SCALE[i].isEmpty()) w else "$w ${SCALE[i]}"
        }
        return parts.joinToString(" ")
    }

    private fun isSentenceBoundary(chars: CharArray, i: Int): Boolean {
        val c = chars[i]
        if (c != '.' && c != '!' && c != '?') return false
        if (c == '.') {
            if (i > 0 && i + 1 < chars.size && chars[i - 1].isDigit() && chars[i + 1].isDigit()) return false
            var start = i
            while (start > 0 && chars[start - 1].let { it in 'a'..'z' || it in 'A'..'Z' }) start--
            val token = String(chars, start, i - start).lowercase()
            if (token in NON_BOUNDARY) return false
            if ((token == "a" || token == "p") && chars.getOrNull(i + 1)?.lowercaseChar() == 'm') return false
        }
        return chars.getOrNull(i + 1)?.isWhitespace() ?: true
    }

    /** A chunk always ends in punctuation (`,` added otherwise). */
    fun ensurePunctuation(text: String): String {
        val t = text.trim()
        return if (t.isEmpty() || t.last() in ".!?,;:") t else "$t,"
    }

    /** Sentences, each split at whitespace into pieces of at most [maxLen] characters. */
    fun chunkText(text: String, maxLen: Int = MAX_CHUNK): List<String> {
        val max = maxLen.coerceAtLeast(2)
        val chars = text.toCharArray()
        val sentences = ArrayList<String>()
        var start = 0
        for (i in chars.indices) {
            if (isSentenceBoundary(chars, i)) {
                sentences += String(chars, start, i + 1 - start)
                start = i + 1
            }
        }
        if (start < chars.size) sentences += String(chars, start, chars.size - start)
        val chunks = ArrayList<String>()
        for (sentence in sentences) {
            var remaining = sentence.trim()
            while (remaining.isNotEmpty()) {
                val punctuated = ensurePunctuation(remaining)
                if (punctuated.length <= max) {
                    chunks += punctuated
                    break
                }
                val end = (max - 1).coerceAtMost(remaining.length)
                var boundary = end
                while (boundary > 0 && !(remaining.getOrNull(boundary)?.isWhitespace() ?: false)) boundary--
                if (boundary == 0) boundary = end
                val part = remaining.substring(0, boundary).trim()
                if (part.isNotEmpty()) chunks += ensurePunctuation(part)
                remaining = remaining.substring(boundary).trim()
            }
        }
        return chunks
    }

    /** One piece of a chunk for the phonemizer: a phrase for `espeak-ng`, or punctuation (with its spacing) copied as is. */
    sealed interface Piece {
        data class Phrase(val text: String) : Piece

        data class Literal(val text: String) : Piece
    }

    fun splitPhrases(text: String): List<Piece> {
        val out = ArrayList<Piece>()
        var i = 0
        val phrase = StringBuilder()
        while (i < text.length) {
            if (text[i] in PRESERVED) {
                val trailing = phrase.reversed().takeWhile { it.isWhitespace() }.toString()
                val body = phrase.toString().trimEnd()
                if (body.isNotEmpty()) out += Piece.Phrase(body)
                val lit = StringBuilder(trailing)
                while (i < text.length && text[i] in PRESERVED) lit.append(text[i++])
                val spacing = StringBuilder()
                while (i < text.length && text[i].isWhitespace()) spacing.append(text[i++])
                if (spacing.isEmpty() && i == text.length) spacing.append(' ')
                lit.append(spacing)
                out += Piece.Literal(lit.toString())
                phrase.clear()
                continue
            }
            phrase.append(text[i++])
        }
        if (phrase.isNotBlank()) out += Piece.Phrase(phrase.toString())
        return out
    }

    /** Joins the IPA lines `espeak-ng --ipa` printed for one phrase. */
    fun cleanIpa(raw: String): String = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
