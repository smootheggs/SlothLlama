package app.localmind.assistant.domain.home

/** A word of the day: how it's written, read and meant. Cantonese words are written traditional. */
data class DailyWord(
    val simplified: String,
    val traditional: String,
    val reading: String,
    val meaning: String,
    val cantonese: Boolean
) {
    fun written(traditionalScript: Boolean) = if (cantonese || traditionalScript) traditional else simplified
}

/** An idiom (成语, or an old saying), with its literal sense, its meaning and, where it has one, its story. */
data class Idiom(
    val simplified: String,
    val traditional: String,
    val pinyin: String,
    val literal: String,
    val meaning: String,
    val story: String = ""
) {
    fun written(traditionalScript: Boolean) = if (traditionalScript) traditional else simplified
}

enum class WordLanguage(val label: String) { ALTERNATE("Alternate"), MANDARIN("Mandarin"), CANTONESE("Cantonese") }

/**
 * Small hand-picked lists, so a word a day comes with no download and no model: everyday Mandarin
 * (with a few modern expressions), colloquial Cantonese and well-loved idioms. Picked by the day,
 * so it's the same word all day and a new one tomorrow.
 */
object DailyWords {

    val MANDARIN: List<DailyWord> = table(
        """
        慢慢来|慢慢來|màn màn lái|take your time
        散步|散步|sàn bù|to take a stroll
        加油|加油|jiā yóu|keep going, you can do it
        热闹|熱鬧|rè nao|lively, bustling
        舒服|舒服|shū fu|comfortable
        习惯|習慣|xí guàn|habit; to be used to
        机会|機會|jī huì|opportunity
        经验|經驗|jīng yàn|experience
        安静|安靜|ān jìng|quiet, calm
        好奇|好奇|hào qí|curious
        耐心|耐心|nài xīn|patience
        放松|放鬆|fàng sōng|to relax
        风景|風景|fēng jǐng|scenery
        味道|味道|wèi dào|taste, flavour
        打算|打算|dǎ suàn|to plan, to intend
        认真|認真|rèn zhēn|earnest, conscientious
        随便|隨便|suí biàn|as you like; casual
        幸福|幸福|xìng fú|happiness, well-being
        熟悉|熟悉|shú xī|familiar
        陌生|陌生|mò shēng|unfamiliar
        坚持|堅持|jiān chí|to keep at it, persevere
        进步|進步|jìn bù|progress
        交流|交流|jiāo liú|exchange; to communicate
        文化|文化|wén huà|culture
        旅行|旅行|lǚ xíng|to travel
        记得|記得|jì de|to remember
        忘记|忘記|wàng jì|to forget
        发现|發現|fā xiàn|to discover
        选择|選擇|xuǎn zé|choice; to choose
        简单|簡單|jiǎn dān|simple
        复杂|複雜|fù zá|complicated
        方便|方便|fāng biàn|convenient
        麻烦|麻煩|má fan|trouble; to bother someone
        当然|當然|dāng rán|of course
        其实|其實|qí shí|actually, in fact
        终于|終於|zhōng yú|at last
        突然|突然|tū rán|suddenly
        难得|難得|nán dé|rare, hard to come by
        适合|適合|shì hé|to suit, to fit
        温暖|溫暖|wēn nuǎn|warm (and kind)
        凉快|涼快|liáng kuai|pleasantly cool
        月亮|月亮|yuè liang|the moon
        星星|星星|xīng xing|a star
        节日|節日|jié rì|festival, holiday
        团圆|團圓|tuán yuán|a family reunion
        缘分|緣分|yuán fèn|the fate that brings people together
        默契|默契|mò qì|an unspoken understanding
        偶尔|偶爾|ǒu ěr|now and then
        悠闲|悠閒|yōu xián|leisurely, unhurried
        懒洋洋|懶洋洋|lǎn yáng yáng|lazily, languidly
        打招呼|打招呼|dǎ zhāo hu|to say hello
        聊天|聊天|liáo tiān|to chat
        开玩笑|開玩笑|kāi wán xiào|to joke
        吃货|吃貨|chī huò|a foodie
        宵夜|宵夜|xiāo yè|a late-night snack
        点心|點心|diǎn xin|dim sum; a snack
        茶馆|茶館|chá guǎn|a teahouse
        麻将|麻將|má jiàng|mahjong
        音乐|音樂|yīn yuè|music
        游戏|遊戲|yóu xì|a game
        跑步|跑步|pǎo bù|to run, go jogging
        地铁|地鐵|dì tiě|the subway
        天气|天氣|tiān qì|the weather
        海边|海邊|hǎi biān|the seaside
        灵感|靈感|líng gǎn|inspiration
        梦想|夢想|mèng xiǎng|a dream, an aspiration
        目标|目標|mù biāo|a goal
        效率|效率|xiào lǜ|efficiency
        靠谱|靠譜|kào pǔ|reliable, trustworthy (casual)
        佛系|佛系|fó xì|easy-going, zen about it (slang)
        摸鱼|摸魚|mō yú|to slack off at work ("touch fish")
        躺平|躺平|tǎng píng|to "lie flat", opt out of the rat race
        种草|種草|zhòng cǎo|to make someone want something ("plant grass")
        打卡|打卡|dǎ kǎ|to check in: at a famous spot, or on a habit
        学霸|學霸|xué bà|a star student
        拖延|拖延|tuō yán|to procrastinate
        惊喜|驚喜|jīng xǐ|a pleasant surprise
        """, cantonese = false
    )

    val CANTONESE: List<DailyWord> = table(
        """
        早晨|早晨|zou2 san4|good morning
        唔該|唔該|m4 goi1|thanks (for a service); excuse me
        多謝|多謝|do1 ze6|thank you (for a gift)
        唔使客氣|唔使客氣|m4 sai2 haak3 hei3|you're welcome
        食咗飯未呀|食咗飯未呀|sik6 zo2 faan6 mei6 aa3|have you eaten? (a friendly hello)
        飲茶|飲茶|jam2 caa4|to go for dim sum ("drink tea")
        埋單|埋單|maai4 daan1|the bill, please
        好味|好味|hou2 mei6|tasty
        好正|好正|hou2 zeng3|awesome, great
        犀利|犀利|sai1 lei6|impressive, formidable
        搞掂|搞掂|gaau2 dim6|done, sorted
        冇問題|冇問題|mou5 man6 tai4|no problem
        慢慢嚟|慢慢嚟|maan6 maan2 lai4|take your time
        頂唔順|頂唔順|ding2 m4 seon6|can't stand it any more
        好攰|好攰|hou2 gui6|so tired
        返工|返工|faan1 gung1|to go to work
        放工|放工|fong3 gung1|to finish work for the day
        收工|收工|sau1 gung1|to wrap up, call it a day
        傾偈|傾偈|king1 gai2|to chat
        行街|行街|haang4 gaai1|to go out shopping, stroll the streets
        揾食|揾食|wan2 sik6|to make a living
        落雨|落雨|lok6 jyu5|to rain
        好熱|好熱|hou2 jit6|so hot
        好凍|好凍|hou2 dung3|so cold
        靚|靚|leng3|pretty, good-looking; good quality
        叻|叻|lek1|clever, capable
        得閒|得閒|dak1 haan4|free, not busy
        係咪|係咪|hai6 mai6|is it? isn't it?
        點解|點解|dim2 gaai2|why
        幾多錢|幾多錢|gei2 do1 cin2|how much is it?
        邊度|邊度|bin1 dou6|where
        而家|而家|ji4 gaa1|now
        聽日|聽日|ting1 jat6|tomorrow
        琴日|琴日|kam4 jat6|yesterday
        今朝|今朝|gam1 ziu1|this morning
        早唞|早唞|zou2 tau2|good night
        拜拜|拜拜|baai1 baai3|bye
        一齊|一齊|jat1 cai4|together
        開心|開心|hoi1 sam1|happy
        心急|心急|sam1 gap1|impatient
        冇所謂|冇所謂|mou5 so2 wai6|it doesn't matter, either way
        好耐冇見|好耐冇見|hou2 noi6 mou5 gin3|long time no see
        你好嘢|你好嘢|nei5 hou2 je5|well done, you!
        慳錢|慳錢|haan1 cin2|to save money
        打邊爐|打邊爐|daa2 bin1 lou4|to have hot pot
        糖水|糖水|tong4 seoi2|sweet soup (dessert)
        茶餐廳|茶餐廳|caa4 caan1 teng1|a Hong Kong diner
        叉燒|叉燒|caa1 siu1|barbecued pork
        菠蘿包|菠蘿包|bo1 lo4 baau1|pineapple bun (no pineapple in it)
        碼頭|碼頭|maa5 tau4|a pier
        小巴|小巴|siu2 baa1|a minibus
        有落|有落|jau5 lok6|"getting off!" (called out on a minibus)
        搭的士|搭的士|daap3 dik1 si2|to take a taxi
        打麻雀|打麻雀|daa2 maa4 zoek3|to play mahjong ("hit sparrows")
        食糊|食糊|sik6 wu2|to win a mahjong hand
        """, cantonese = true
    )

    val IDIOMS: List<Idiom> = listOf(
        Idiom("画蛇添足", "畫蛇添足", "huà shé tiān zú", "draw a snake, add feet", "to spoil something by overdoing it",
            "In a race to draw a snake, the first to finish kept going and added feet. The wine went to someone else."),
        Idiom("守株待兔", "守株待兔", "shǒu zhū dài tù", "guard the stump, wait for hares", "to wait for luck instead of working for it",
            "A farmer saw a hare run into a tree stump and break its neck, so he gave up farming to wait by the stump for another."),
        Idiom("塞翁失马", "塞翁失馬", "sài wēng shī mǎ", "the old frontiersman loses his horse", "a loss can turn out to be a blessing",
            "His horse ran off; it came back leading a fine wild horse. His son broke a leg riding it, and so was spared from war."),
        Idiom("笨鸟先飞", "笨鳥先飛", "bèn niǎo xiān fēi", "the slow bird sets off first", "start early to make up for being slow",
            "A modest thing to say about yourself. A sloth's motto, too."),
        Idiom("慢工出细活", "慢工出細活", "màn gōng chū xì huó", "slow work makes fine work", "good things take time"),
        Idiom("马到成功", "馬到成功", "mǎ dào chéng gōng", "success as the horses arrive", "instant success; a wish of good luck"),
        Idiom("龙马精神", "龍馬精神", "lóng mǎ jīng shén", "the spirit of a dragon-horse", "vigour and good health; a New Year wish"),
        Idiom("未雨绸缪", "未雨綢繆", "wèi yǔ chóu móu", "bind the nest before the rain", "to prepare in good time",
            "From the Book of Songs: a bird mends its nest with mulberry roots before the rain comes."),
        Idiom("知己知彼", "知己知彼", "zhī jǐ zhī bǐ", "know yourself, know the other", "understand both sides before you act",
            "Sun Tzu: know your enemy and yourself, and a hundred battles hold no danger."),
        Idiom("对牛弹琴", "對牛彈琴", "duì niú tán qín", "play the zither to a cow", "to waste your words on the wrong audience",
            "A musician played his finest piece to an ox, which went on grazing."),
        Idiom("井底之蛙", "井底之蛙", "jǐng dǐ zhī wā", "a frog at the bottom of a well", "someone with a narrow view of the world",
            "From Zhuangzi: the frog thought its well was the whole world, until a turtle told it about the sea."),
        Idiom("亡羊补牢", "亡羊補牢", "wáng yáng bǔ láo", "mend the pen after losing a sheep", "it's not too late to fix things"),
        Idiom("一举两得", "一舉兩得", "yī jǔ liǎng dé", "one move, two gains", "to kill two birds with one stone"),
        Idiom("半途而废", "半途而廢", "bàn tú ér fèi", "give up halfway", "to abandon something half done",
            "A scholar came home early from his studies; his wife cut through the cloth on her loom to show him what quitting wastes."),
        Idiom("熟能生巧", "熟能生巧", "shú néng shēng qiǎo", "practice gives skill", "practice makes perfect",
            "An old oil seller poured oil through the hole of a coin without wetting it: nothing but practice, he said."),
        Idiom("温故知新", "溫故知新", "wēn gù zhī xīn", "review the old, learn the new", "revisit what you know to learn more",
            "From the Analects: one who does this can be a teacher."),
        Idiom("滴水穿石", "滴水穿石", "dī shuǐ chuān shí", "dripping water bores through stone", "persistence wins in the end"),
        Idiom("入乡随俗", "入鄉隨俗", "rù xiāng suí sú", "enter a village, follow its customs", "when in Rome, do as the Romans do"),
        Idiom("一见钟情", "一見鍾情", "yī jiàn zhōng qíng", "one look, and feelings settle", "love at first sight"),
        Idiom("一路顺风", "一路順風", "yī lù shùn fēng", "a following wind all the way", "have a good trip"),
        Idiom("心想事成", "心想事成", "xīn xiǎng shì chéng", "the heart wishes, the thing happens", "may your wishes come true"),
        Idiom("万事如意", "萬事如意", "wàn shì rú yì", "ten thousand things as you wish", "may everything go your way"),
        Idiom("年年有余", "年年有餘", "nián nián yǒu yú", "a surplus every year", "may you have plenty, year after year",
            "余 sounds like 鱼, fish: which is why a whole fish is served at New Year, and some is left over."),
        Idiom("画龙点睛", "畫龍點睛", "huà lóng diǎn jīng", "paint the dragon, dot its eyes", "the finishing touch that brings it to life",
            "A painter left out his dragons' eyes. Asked to add them, he dotted two, and those dragons flew off the wall."),
        Idiom("叶公好龙", "葉公好龍", "yè gōng hào lóng", "Lord Ye loves dragons", "to claim to love what you actually fear",
            "Lord Ye covered his house in dragons. When a real one came to visit, he ran."),
        Idiom("狐假虎威", "狐假虎威", "hú jiǎ hǔ wēi", "the fox borrows the tiger's might", "to bully with someone else's power",
            "A fox walked ahead of a tiger; every animal fled, and the tiger believed they feared the fox."),
        Idiom("杯弓蛇影", "杯弓蛇影", "bēi gōng shé yǐng", "a bow in the cup, taken for a snake", "to fear something that isn't there",
            "A guest saw a snake in his wine and fell ill; it was the reflection of a bow on the wall."),
        Idiom("掩耳盗铃", "掩耳盜鈴", "yǎn ěr dào líng", "cover your ears to steal a bell", "to fool only yourself"),
        Idiom("刻舟求剑", "刻舟求劍", "kè zhōu qiú jiàn", "mark the boat to find the sword", "to cling to a method after things have changed",
            "A man dropped his sword in a river and marked the side of the moving boat to show where to dive for it."),
        Idiom("自相矛盾", "自相矛盾", "zì xiāng máo dùn", "one's own spear and shield", "to contradict yourself",
            "A man sold a spear that could pierce anything and a shield nothing could pierce. Someone asked about the two together."),
        Idiom("拔苗助长", "拔苗助長", "bá miáo zhù zhǎng", "pull up sprouts to help them grow", "to spoil things by rushing them",
            "A farmer tugged his seedlings a little taller each day. By morning they had withered."),
        Idiom("胸有成竹", "胸有成竹", "xiōng yǒu chéng zhú", "a finished bamboo in the chest", "to have a plan already worked out",
            "The painter Wen Tong saw the whole bamboo in his mind before his brush touched the paper."),
        Idiom("百发百中", "百發百中", "bǎi fā bǎi zhòng", "a hundred shots, a hundred hits", "never missing; spot on",
            "The archer Yang Youji could hit a chosen willow leaf from a hundred paces."),
        Idiom("九牛一毛", "九牛一毛", "jiǔ niú yī máo", "one hair from nine oxen", "a drop in the ocean"),
        Idiom("鸡飞狗跳", "雞飛狗跳", "jī fēi gǒu tiào", "chickens fly, dogs jump", "total commotion"),
        Idiom("鹤立鸡群", "鶴立雞群", "hè lì jī qún", "a crane among chickens", "to stand out from the crowd"),
        Idiom("如鱼得水", "如魚得水", "rú yú dé shuǐ", "like a fish finding water", "in your element",
            "Liu Bei said it of finding his strategist Zhuge Liang."),
        Idiom("三顾茅庐", "三顧茅廬", "sān gù máo lú", "three visits to the thatched hut", "to keep asking until the right person says yes",
            "Liu Bei called on Zhuge Liang three times before he agreed to serve."),
        Idiom("毛遂自荐", "毛遂自薦", "máo suì zì jiàn", "Mao Sui recommends himself", "to put yourself forward",
            "When his lord needed twenty men for a mission, the unknown Mao Sui stepped up, and saved it."),
        Idiom("破釜沉舟", "破釜沉舟", "pò fǔ chén zhōu", "break the pots, sink the boats", "to commit with no way back",
            "Xiang Yu had his army's cooking pots smashed and boats sunk after crossing the river, then won."),
        Idiom("卧薪尝胆", "臥薪嘗膽", "wò xīn cháng dǎn", "sleep on brushwood, taste gall", "to endure hardship while working to rise again",
            "King Goujian of Yue, defeated, kept himself uncomfortable for years so he'd never forget, and came back."),
        Idiom("东山再起", "東山再起", "dōng shān zài qǐ", "rise again from East Mountain", "to make a comeback",
            "Xie An lived quietly on East Mountain for years, then returned to public life and rose to the top."),
        Idiom("柳暗花明", "柳暗花明", "liǔ àn huā míng", "dark willows, bright flowers", "a way through appears when you least expect it",
            "From Lu You: just when there seems no road ahead, past the willows and flowers, another village."),
        Idiom("苦尽甘来", "苦盡甘來", "kǔ jìn gān lái", "the bitter ends, the sweet comes", "good times follow hard ones"),
        Idiom("一帆风顺", "一帆風順", "yī fān fēng shùn", "one sail, a fair wind", "plain sailing"),
        Idiom("水到渠成", "水到渠成", "shuǐ dào qú chéng", "where water flows, a channel forms", "things fall into place in their own time"),
        Idiom("车水马龙", "車水馬龍", "chē shuǐ mǎ lóng", "carts like water, horses like dragons", "busy streets, heavy traffic"),
        Idiom("人山人海", "人山人海", "rén shān rén hǎi", "mountains and seas of people", "huge crowds"),
        Idiom("津津有味", "津津有味", "jīn jīn yǒu wèi", "full of flavour", "with great relish"),
        Idiom("废寝忘食", "廢寢忘食", "fèi qǐn wàng shí", "skip sleep, forget meals", "completely absorbed in something"),
        Idiom("一心一意", "一心一意", "yī xīn yī yì", "one heart, one mind", "wholeheartedly"),
        Idiom("三心二意", "三心二意", "sān xīn èr yì", "three hearts, two minds", "half-hearted, can't decide"),
        Idiom("七上八下", "七上八下", "qī shàng bā xià", "seven up, eight down", "on tenterhooks"),
        Idiom("乱七八糟", "亂七八糟", "luàn qī bā zāo", "messy sevens, rotten eights", "a total mess"),
        Idiom("马马虎虎", "馬馬虎虎", "mǎ mǎ hū hū", "horse horse, tiger tiger", "so-so; careless",
            "A painter, asked what his half-horse, half-tiger was, shrugged: horse-ish, tiger-ish."),
        Idiom("有备无患", "有備無患", "yǒu bèi wú huàn", "prepared, so no worries", "better safe than sorry"),
        Idiom("举一反三", "舉一反三", "jǔ yī fǎn sān", "shown one corner, find the other three", "to learn by working out the rest",
            "From the Analects: Confucius moved on from students who couldn't."),
        Idiom("青出于蓝", "青出於藍", "qīng chū yú lán", "blue comes from the indigo plant", "the student surpasses the teacher",
            "From Xunzi: the dye is bluer than the plant it comes from."),
        Idiom("精益求精", "精益求精", "jīng yì qiú jīng", "fine, and seek finer", "to keep improving what's already good"),
        Idiom("一日千里", "一日千里", "yī rì qiān lǐ", "a thousand li in a day", "to progress by leaps and bounds")
    )

    /** The word for [epochDay]: Mandarin and Cantonese take turns unless one is chosen. */
    fun wordFor(epochDay: Long, language: WordLanguage): DailyWord = when (language) {
        WordLanguage.MANDARIN -> MANDARIN[pick(epochDay, MANDARIN.size)]
        WordLanguage.CANTONESE -> CANTONESE[pick(epochDay, CANTONESE.size)]
        WordLanguage.ALTERNATE ->
            if (Math.floorMod(epochDay, 2L) == 0L) MANDARIN[pick(Math.floorDiv(epochDay, 2L), MANDARIN.size)]
            else CANTONESE[pick(Math.floorDiv(epochDay, 2L), CANTONESE.size)]
    }

    fun idiomFor(epochDay: Long): Idiom = IDIOMS[pick(epochDay, IDIOMS.size)]

    /** A steady walk through the list: consecutive days land far apart, and every entry comes round. */
    private fun pick(n: Long, size: Int): Int {
        var step = 7
        while (gcd(step, size) != 1) step++
        return Math.floorMod(n * step, size.toLong()).toInt()
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun table(text: String, cantonese: Boolean): List<DailyWord> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
            val f = line.split('|')
            require(f.size == 4) { "Bad word line: $line" }
            DailyWord(f[0], f[1], f[2], f[3], cantonese)
        }
}
