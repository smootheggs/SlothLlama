package app.localmind.assistant.data.dictionary

import app.localmind.assistant.domain.persona.SpeechLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tidies a finished tutor reply before it's saved.
 *
 * Small on-device models follow the tutor format well but often get romanization wrong (a 4B
 * model will happily write "ngo5 lou5" for 你好). The dictionary knows the real readings, so:
 *  - every "Jyutping:" / "Pinyin:" line is rewritten from the Chinese line just above it;
 *  - word-list items ("时间 / 時間 – si4 gaan3 – time") and glosses ("嘅 (ge3)") get their
 *    reading from the dictionary too;
 *  - a 繁 / 简 pair that is the same text twice becomes one "繁/简:" line.
 * Only readings in the persona's language are touched, and only when the dictionary can read
 * every character; anything else is left exactly as the model wrote it.
 */
@Singleton
class ReplyPolisher @Inject constructor(private val dictionary: ChineseDictionary) {

    suspend fun polish(reply: String, language: SpeechLanguage?): String {
        if (language == null || !dictionary.isAvailable || reply.none(ChineseDictionary::isHan)) return reply
        return runCatching { polishLines(reply, language) }.getOrDefault(reply)
    }

    private suspend fun polishLines(reply: String, language: SpeechLanguage): String {
        val label = if (language == SpeechLanguage.CANTONESE) "Jyutping" else "Pinyin"
        val romanLine = Regex("^(\\s*(?:[*_]{0,2})$label(?:[*_]{0,2})\\s*[:：]\\s*)(.+)$", RegexOption.IGNORE_CASE)
        val out = mutableListOf<String>()
        var lastChinese: String? = null
        for (raw in reply.lines()) {
            val roman = romanLine.find(raw)
            when {
                roman != null -> {
                    val source = lastChinese
                    val fixed = source?.let { readingOf(it, language) }
                    out += if (fixed != null) roman.groupValues[1] + fixed else raw
                }
                isChineseLine(raw) -> {
                    val text = withoutScriptLabel(raw)
                    val previous = out.lastOrNull()
                    // "繁: 你好！" followed by "简: 你好！": say it once.
                    if (previous != null && scriptLabel(raw) != null && scriptLabel(previous) != null &&
                        scriptLabel(raw) != scriptLabel(previous) && withoutScriptLabel(previous) == text
                    ) {
                        out[out.size - 1] = "繁/简: $text"
                    } else {
                        out += fixGlosses(raw, language)
                    }
                    lastChinese = text
                }
                else -> {
                    out += fixGlosses(raw, language)
                    if (raw.isNotBlank() && raw.count(ChineseDictionary::isHan) == 0) lastChinese = null
                }
            }
        }
        return out.joinToString("\n")
    }

    /** "嘅 (gei3)" and "时间 / 時間 – shíjiān – time": the reading after a Chinese word. */
    private suspend fun fixGlosses(line: String, language: SpeechLanguage): String {
        var result = line
        // Word lists: characters – reading – meaning
        WORD_ITEM.find(result)?.let { m ->
            val word = m.groupValues[2].split('/', '／').first().trim()
            val reading = readingOf(word, language)
            if (reading != null && looksLikeReading(m.groupValues[4], language)) {
                result = result.replaceRange(m.groups[4]!!.range, reading)
            }
        }
        // Glosses: 汉字 (reading), replaced from the end so earlier positions stay valid.
        val glosses = GLOSS.findAll(result).toList().asReversed()
        for (m in glosses) {
            if (!looksLikeReading(m.groupValues[2], language)) continue
            val reading = readingOf(m.groupValues[1], language) ?: continue
            result = result.replaceRange(m.groups[2]!!.range, reading)
        }
        return result
    }

    private suspend fun readingOf(text: String, language: SpeechLanguage): String? {
        val chinese = withoutScriptLabel(text)
        val han = chinese.filter(ChineseDictionary::isHan)
        if (han.isEmpty()) return null
        val reading = dictionary.romanize(chinese, language).trim()
        // Give up if any character couldn't be read (it would come back as itself).
        return reading.takeIf { r -> r.isNotBlank() && r.none(ChineseDictionary::isHan) }
    }

    private fun isChineseLine(line: String): Boolean {
        val text = withoutScriptLabel(line).filterNot { it.isWhitespace() || it in PUNCTUATION }
        if (text.isEmpty()) return false
        val han = text.count(ChineseDictionary::isHan)
        return han * 2 >= text.length
    }

    private fun scriptLabel(line: String): String? = SCRIPT_LABEL.find(line)?.groupValues?.get(1)

    private fun withoutScriptLabel(line: String): String = line.replace(SCRIPT_LABEL, "").trim()

    private fun looksLikeReading(text: String, language: SpeechLanguage): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 60) return false
        return if (language == SpeechLanguage.CANTONESE) JYUTPING.matches(t)
        // Pinyin must carry a tone (mark or number), so an English gloss like "(hello)" is left alone.
        else PINYIN.matches(t) && t.any { it in TONED }
    }

    private companion object {
        val SCRIPT_LABEL = Regex("^\\s*([简繁簡]|繁/简)\\s*[:：]\\s*")
        const val TONED = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ12345"
        const val PUNCTUATION = "，。！？、；：,.!?;:“”\"'（）()「」《》…—-–~～"
        val JYUTPING = Regex("^[a-z]+[1-6](?:[\\s-]+[a-z]+[1-6])*[.!?]?$", RegexOption.IGNORE_CASE)
        val PINYIN = Regex("^[a-zA-Züāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ'1-5\\s-]+[.!?]?$")
        /** 汉字 (reading): the word right before the parentheses. */
        val GLOSS = Regex("([\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]+)\\s*[（(]([^()（）]{1,40})[)）]")
        /** prefix, characters (maybe "简 / 繁"), dash, reading, dash, meaning */
        val WORD_ITEM = Regex(
            "^(\\s*(?:[-*•⭐]\\s*)?)([\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]+(?:\\s*[/／]\\s*[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]+)?)(\\s+[–—-]\\s+)([^–—]+?)(?=\\s+[–—-]\\s+)"
        )
    }
}
