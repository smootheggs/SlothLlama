package app.localmind.assistant.domain.home

enum class GreetingLanguage(val label: String) {
    OFF("Off"), ALTERNATE("Alternate"), MANDARIN("Mandarin"), CANTONESE("Cantonese")
}

/** A small touch on the sloth: a coffee in the morning, sleepy at night, a lantern on festival days. */
enum class SlothMood { NONE, COFFEE, SLEEPY, LANTERN, CATCH, BIRTHDAY }

/** A greeting in Chinese and how to read it, e.g. 早晨 · zou2 san4. */
data class ChineseHello(val text: String, val reading: String) {
    /** Jyutping carries tone numbers; pinyin carries tone marks. */
    val cantonese: Boolean get() = reading.any { it.isDigit() }
}

data class Greeting(val hello: String, val sub: String, val chinese: ChineseHello?, val mood: SlothMood)

/** What the greeting can draw on. Everything here is already on the tablet. */
data class GreetingFacts(
    val hour: Int,
    val epochDay: Long,
    /** The plain hello and its line for this part of the day ("Good morning." / "Slow and steady today?"). */
    val base: Pair<String, String>,
    val name: String = "",
    val birthday: Boolean = false,
    /** A festival or solar term that is today, if any. */
    val today: SkyNote? = null,
    val caught: Int = 0,
    val due: Int = 0
)

data class GreetingOptions(
    val context: Boolean = true,
    val language: GreetingLanguage = GreetingLanguage.ALTERNATE,
    val traditional: Boolean = false,
    val moods: Boolean = true
)

/**
 * The hello at the top of Home: your name when you've given one, a line that notices the day
 * (a birthday, a festival, the season turning),
 * and a hello in Mandarin or Cantonese. Pure, so it can be tested.
 */
object HomeGreeting {

    fun compose(f: GreetingFacts, o: GreetingOptions): Greeting {
        val festivalToday = f.today?.takeIf { it.daysAway == 0 }
        val hello = withName(f.base.first, f.name.trim())
        val sub = if (!o.context) f.base.second else when {
            f.birthday -> "Happy birthday. Take today slowly. 🎂"
            festivalToday != null && festivalToday.custom.isNotBlank() -> festivalToday.custom
            festivalToday != null && !festivalToday.isFestival -> "The season turns: ${festivalToday.english} begins today."
            festivalToday != null -> "Today is the ${festivalToday.english}."
            // What the Lobster brought and cards due light up their own tiles; the hello doesn't repeat them.
            else -> f.base.second
        }
        val language = when (o.language) {
            GreetingLanguage.OFF -> null
            GreetingLanguage.MANDARIN -> false
            GreetingLanguage.CANTONESE -> true
            GreetingLanguage.ALTERNATE -> Math.floorMod(f.epochDay, 2L) == 1L
        }
        val chinese = language?.let { cantonese ->
            val occasion = when {
                f.birthday -> "birthday"
                festivalToday != null -> festivalToday.zh
                else -> null
            }
            hello(cantonese, f.hour, occasion, o.traditional)
        }
        val mood = if (!o.moods) SlothMood.NONE else when {
            f.birthday -> SlothMood.BIRTHDAY
            festivalToday?.isFestival == true -> SlothMood.LANTERN
            f.caught > 0 && f.hour in 5..22 -> SlothMood.CATCH
            f.hour >= 23 || f.hour < 5 -> SlothMood.SLEEPY
            f.hour in 5..10 -> SlothMood.COFFEE
            else -> SlothMood.NONE
        }
        return Greeting(hello, sub, chinese, mood)
    }

    /** "Good morning." + Sam → "Good morning, Sam."; "Still up?" → "Still up, Sam?". */
    fun withName(hello: String, name: String): String {
        if (name.isEmpty()) return hello
        val end = hello.lastOrNull()?.takeIf { it == '.' || it == '?' || it == '!' }
        val body = if (end != null) hello.dropLast(1) else hello
        return "$body, $name${end ?: ""}"
    }

    /** The hello in Chinese for the hour, or for the occasion (a festival's key, or "birthday"). */
    fun hello(cantonese: Boolean, hour: Int, occasion: String?, traditional: Boolean): ChineseHello {
        occasion?.let { OCCASIONS[it] }?.let { (mandarin, yue) ->
            return if (cantonese) yue else if (traditional) mandarin.second else mandarin.first
        }
        val part = when {
            hour >= 23 || hour < 5 -> 3
            hour < 12 -> 0
            hour < 18 -> 1
            else -> 2
        }
        return if (cantonese) CANTONESE_HOURS[part] else MANDARIN_HOURS[part]
    }

    private val MANDARIN_HOURS = listOf(
        ChineseHello("早上好", "zǎo shang hǎo"), ChineseHello("下午好", "xià wǔ hǎo"),
        ChineseHello("晚上好", "wǎn shang hǎo"), ChineseHello("晚安", "wǎn ān")
    )
    private val CANTONESE_HOURS = listOf(
        ChineseHello("早晨", "zou2 san4"), ChineseHello("午安", "ng5 on1"),
        ChineseHello("食咗飯未呀？", "sik6 zo2 faan6 mei6 aa3"), ChineseHello("早唞", "zou2 tau2")
    )

    /** Occasion → ((Mandarin simplified, Mandarin traditional), Cantonese). */
    private fun m(s: String, t: String, py: String) = ChineseHello(s, py) to ChineseHello(t, py)
    private val OCCASIONS: Map<String, Pair<Pair<ChineseHello, ChineseHello>, ChineseHello>> = mapOf(
        "birthday" to (m("生日快乐", "生日快樂", "shēng rì kuài lè") to ChineseHello("生日快樂", "saang1 jat6 faai3 lok6")),
        "春节" to (m("新年快乐", "新年快樂", "xīn nián kuài lè") to ChineseHello("恭喜發財", "gung1 hei2 faat3 coi4")),
        "除夕" to (m("除夕快乐", "除夕快樂", "chú xī kuài lè") to ChineseHello("新年快樂", "san1 nin4 faai3 lok6")),
        "元宵节" to (m("元宵快乐", "元宵快樂", "yuán xiāo kuài lè") to ChineseHello("元宵節快樂", "jyun4 siu1 zit3 faai3 lok6")),
        "端午节" to (m("端午安康", "端午安康", "duān wǔ ān kāng") to ChineseHello("端午節快樂", "dyun1 ng5 zit3 faai3 lok6")),
        "七夕" to (m("七夕快乐", "七夕快樂", "qī xī kuài lè") to ChineseHello("七夕快樂", "cat1 zik6 faai3 lok6")),
        "中秋节" to (m("中秋快乐", "中秋快樂", "zhōng qiū kuài lè") to ChineseHello("中秋節快樂", "zung1 cau1 zit3 faai3 lok6")),
        "重阳节" to (m("重阳节快乐", "重陽節快樂", "chóng yáng jié kuài lè") to ChineseHello("重陽節快樂", "cung4 joeng4 zit3 faai3 lok6")),
        "冬至" to (m("冬至快乐", "冬至快樂", "dōng zhì kuài lè") to ChineseHello("冬至快樂", "dung1 zi3 faai3 lok6"))
    )
}
