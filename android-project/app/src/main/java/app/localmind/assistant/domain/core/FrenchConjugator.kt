package app.localmind.assistant.domain.core

/**
 * French conjugation by rules plus a table of irregular models, for the Core words cards.
 *
 * A port of scripts/core_words/fr_conj.py; FrenchConjugatorUnitTests checks every verb in the
 * French list against that script's output. Spelling is the traditional one (préférerai, mû).
 */
object FrenchConjugator {

    data class Tables(
        val present: List<String>,
        val imparfait: List<String>,
        val passeSimple: List<String>,
        val futur: List<String>,
        val conditionnel: List<String>,
        val subjonctif: List<String>,
        /** tu, nous, vous; "-" where there is none. */
        val imperatif: List<String>,
        val ppr: String,
        val pp: String,
        /** Feminine, masculine plural and feminine plural participles, when not by rule (dû: due dus dues). */
        val ppf: List<String>? = null
    )

    data class Conjugation(
        val verb: String,
        val infinitive: String,
        val reflexive: Boolean,
        /** "avoir" or "être" in the compound tenses. */
        val aux: String,
        /** Also takes "avoir" with a direct object (sortir la poubelle). */
        val both: Boolean,
        /** Only the il form exists (falloir, pleuvoir). */
        val impersonal: Boolean,
        val tables: Tables,
        /** Past participle: masculine, feminine, masculine plural, feminine plural. */
        val participles: List<String>
    )

    private class Model(
        val pres: String,
        val ps: Any,
        val fut: String,
        val pp: String,
        val impf: String? = null,
        val subj: String? = null,
        val imp: String? = null,
        val ppr: String? = null,
        val ppf: String? = null,
        val aux: String? = null,
        val impersonal: Boolean = false
    )

    private fun ps(type: String, stem: String) = type to stem

    // Each key is an infinitive ending; a verb takes the longest key it ends with, and its forms
    // are its prefix + the key's forms.
    private val M: Map<String, Model> = linkedMapOf(
        "être" to Model("suis es est sommes êtes sont", ps("u", "f"), "ser", "été", impf = "ét",
            subj = "sois sois soit soyons soyez soient", imp = "sois soyons soyez", ppr = "étant"),
        "avoir" to Model("ai as a avons avez ont", ps("u", "e"), "aur", "eu",
            subj = "aie aies ait ayons ayez aient", imp = "aie ayons ayez", ppr = "ayant"),
        "aller" to Model("vais vas va allons allez vont", "allai allas alla allâmes allâtes allèrent", "ir", "allé",
            subj = "aille ailles aille allions alliez aillent", imp = "va allons allez", aux = "être"),
        "faire" to Model("fais fais fait faisons faites font", ps("i", "f"), "fer", "fait",
            subj = "fasse fasses fasse fassions fassiez fassent"),
        "dire" to Model("dis dis dit disons dites disent", ps("i", "d"), "dir", "dit"),
        "dédire" to Model("dédis dédis dédit dédisons dédisez dédisent", ps("i", "déd"), "dédir", "dédit"),
        "interdire" to Model("interdis interdis interdit interdisons interdisez interdisent", ps("i", "interd"), "interdir", "interdit"),
        "prédire" to Model("prédis prédis prédit prédisons prédisez prédisent", ps("i", "préd"), "prédir", "prédit"),
        "contredire" to Model("contredis contredis contredit contredisons contredisez contredisent", ps("i", "contred"), "contredir", "contredit"),
        "médire" to Model("médis médis médit médisons médisez médisent", ps("i", "méd"), "médir", "médit"),
        "maudire" to Model("maudis maudis maudit maudissons maudissez maudissent", ps("i", "maud"), "maudir", "maudit"),
        "suffire" to Model("suffis suffis suffit suffisons suffisez suffisent", ps("i", "suff"), "suffir", "suffi"),
        "lire" to Model("lis lis lit lisons lisez lisent", ps("u", "l"), "lir", "lu"),
        "rire" to Model("ris ris rit rions riez rient", ps("i", "r"), "rir", "ri"),
        "crire" to Model("cris cris crit crivons crivez crivent", ps("i", "criv"), "crir", "crit"),
        "uire" to Model("uis uis uit uisons uisez uisent", ps("i", "uis"), "uir", "uit"),
        "luire" to Model("luis luis luit luisons luisez luisent", ps("i", "luis"), "luir", "lui"),
        "nuire" to Model("nuis nuis nuit nuisons nuisez nuisent", ps("i", "nuis"), "nuir", "nui"),
        "plaire" to Model("plais plais plaît plaisons plaisez plaisent", ps("u", "pl"), "plair", "plu"),
        "taire" to Model("tais tais tait taisons taisez taisent", ps("u", "t"), "tair", "tu"),
        "boire" to Model("bois bois boit buvons buvez boivent", ps("u", "b"), "boir", "bu"),
        "croire" to Model("crois crois croit croyons croyez croient", ps("u", "cr"), "croir", "cru"),
        "voir" to Model("vois vois voit voyons voyez voient", ps("i", "v"), "verr", "vu"),
        "prévoir" to Model("prévois prévois prévoit prévoyons prévoyez prévoient", ps("i", "prév"), "prévoir", "prévu"),
        "pourvoir" to Model("pourvois pourvois pourvoit pourvoyons pourvoyez pourvoient", ps("u", "pourv"), "pourvoir", "pourvu"),
        "devoir" to Model("dois dois doit devons devez doivent", ps("u", "d"), "devr", "dû", ppf = "due dus dues"),
        "pouvoir" to Model("peux peux peut pouvons pouvez peuvent", ps("u", "p"), "pourr", "pu",
            subj = "puisse puisses puisse puissions puissiez puissent", imp = ""),
        "savoir" to Model("sais sais sait savons savez savent", ps("u", "s"), "saur", "su", ppr = "sachant",
            subj = "sache saches sache sachions sachiez sachent", imp = "sache sachons sachez"),
        "vouloir" to Model("veux veux veut voulons voulez veulent", ps("u", "voul"), "voudr", "voulu",
            subj = "veuille veuilles veuille voulions vouliez veuillent", imp = "veuille veuillons veuillez"),
        "valoir" to Model("vaux vaux vaut valons valez valent", ps("u", "val"), "vaudr", "valu",
            subj = "vaille vailles vaille valions valiez vaillent"),
        "falloir" to Model("- - faut - - -", "- - fallut - - -", "faudr", "fallu", impf = "fall",
            subj = "- - faille - - -", imp = "", ppr = "-", impersonal = true),
        "pleuvoir" to Model("- - pleut - - -", "- - plut - - -", "pleuvr", "plu", impf = "pleuv",
            subj = "- - pleuve - - -", imp = "", ppr = "pleuvant", impersonal = true),
        "cevoir" to Model("çois çois çoit cevons cevez çoivent", ps("u", "ç"), "cevr", "çu"),
        "mouvoir" to Model("meus meus meut mouvons mouvez meuvent", ps("u", "m"), "mouvr", "mû", ppf = "mue mus mues"),
        "émouvoir" to Model("émeus émeus émeut émouvons émouvez émeuvent", ps("u", "ém"), "émouvr", "ému"),
        "promouvoir" to Model("promeus promeus promeut promouvons promouvez promeuvent", ps("u", "prom"), "promouvr", "promu"),
        "asseoir" to Model("assieds assieds assied asseyons asseyez asseyent", ps("i", "ass"), "assiér", "assis"),
        "venir" to Model("viens viens vient venons venez viennent", ps("in", "v"), "viendr", "venu", aux = "être"),
        "tenir" to Model("tiens tiens tient tenons tenez tiennent", ps("in", "t"), "tiendr", "tenu"),
        "mettre" to Model("mets mets met mettons mettez mettent", ps("i", "m"), "mettr", "mis"),
        "prendre" to Model("prends prends prend prenons prenez prennent", ps("i", "pr"), "prendr", "pris"),
        "battre" to Model("bats bats bat battons battez battent", ps("i", "batt"), "battr", "battu"),
        "rompre" to Model("romps romps rompt rompons rompez rompent", ps("i", "romp"), "rompr", "rompu"),
        "vaincre" to Model("vaincs vaincs vainc vainquons vainquez vainquent", ps("i", "vainqu"), "vaincr", "vaincu"),
        "suivre" to Model("suis suis suit suivons suivez suivent", ps("i", "suiv"), "suivr", "suivi"),
        "vivre" to Model("vis vis vit vivons vivez vivent", ps("u", "véc"), "vivr", "vécu"),
        "aître" to Model("ais ais aît aissons aissez aissent", ps("u", ""), "aîtr", "u"),
        "naître" to Model("nais nais naît naissons naissez naissent", ps("i", "naqu"), "naîtr", "né", aux = "être"),
        "eindre" to Model("eins eins eint eignons eignez eignent", ps("i", "eign"), "eindr", "eint"),
        "aindre" to Model("ains ains aint aignons aignez aignent", ps("i", "aign"), "aindr", "aint"),
        "oindre" to Model("oins oins oint oignons oignez oignent", ps("i", "oign"), "oindr", "oint"),
        "clure" to Model("clus clus clut cluons cluez cluent", ps("u", "cl"), "clur", "clu"),
        "inclure" to Model("inclus inclus inclut incluons incluez incluent", ps("u", "incl"), "inclur", "inclus"),
        "soudre" to Model("sous sous sout solvons solvez solvent", ps("u", "sol"), "soudr", "sous", ppf = "soute sous soutes"),
        "résoudre" to Model("résous résous résout résolvons résolvez résolvent", ps("u", "résol"), "résoudr", "résolu"),
        "coudre" to Model("couds couds coud cousons cousez cousent", ps("i", "cous"), "coudr", "cousu"),
        "moudre" to Model("mouds mouds moud moulons moulez moulent", ps("u", "moul"), "moudr", "moulu"),
        "partir" to Model("pars pars part partons partez partent", ps("i", "part"), "partir", "parti", aux = "être"),
        "sortir" to Model("sors sors sort sortons sortez sortent", ps("i", "sort"), "sortir", "sorti", aux = "être"),
        "sentir" to Model("sens sens sent sentons sentez sentent", ps("i", "sent"), "sentir", "senti"),
        "mentir" to Model("mens mens ment mentons mentez mentent", ps("i", "ment"), "mentir", "menti"),
        "servir" to Model("sers sers sert servons servez servent", ps("i", "serv"), "servir", "servi"),
        "dormir" to Model("dors dors dort dormons dormez dorment", ps("i", "dorm"), "dormir", "dormi"),
        "vêtir" to Model("vêts vêts vêt vêtons vêtez vêtent", ps("i", "vêt"), "vêtir", "vêtu"),
        "ouvrir" to Model("ouvre ouvres ouvre ouvrons ouvrez ouvrent", ps("i", "ouvr"), "ouvrir", "ouvert", imp = "ouvre ouvrons ouvrez"),
        "ffrir" to Model("ffre ffres ffre ffrons ffrez ffrent", ps("i", "ffr"), "ffrir", "ffert", imp = "ffre ffrons ffrez"),
        "cueillir" to Model("cueille cueilles cueille cueillons cueillez cueillent", ps("i", "cueill"), "cueiller", "cueilli",
            imp = "cueille cueillons cueillez"),
        "courir" to Model("cours cours court courons courez courent", ps("u", "cour"), "courr", "couru"),
        "mourir" to Model("meurs meurs meurt mourons mourez meurent", ps("u", "mour"), "mourr", "mort", aux = "être"),
        "quérir" to Model("quiers quiers quiert quérons quérez quièrent", ps("i", "qu"), "querr", "quis"),
        "fuir" to Model("fuis fuis fuit fuyons fuyez fuient", ps("i", "fu"), "fuir", "fui"),
        "bouillir" to Model("bous bous bout bouillons bouillez bouillent", ps("i", "bouill"), "bouillir", "bouilli"),
        "accroître" to Model("accrois accrois accroît accroissons accroissez accroissent", ps("u", "accr"), "accroîtr", "accru"),
        "croître" to Model("croîs croîs croît croissons croissez croissent", "crûs crûs crût crûmes crûtes crûrent", "croîtr", "crû",
            ppf = "crue crus crues", imp = "crois croissons croissez"),
        "traire" to Model("trais trais trait trayons trayez traient", "- - - - - -", "trair", "trait"),
        "assaillir" to Model("assaille assailles assaille assaillons assaillez assaillent", ps("i", "assaill"), "assaillir", "assailli",
            imp = "assaille assaillons assaillez"),
        "tressaillir" to Model("tressaille tressailles tressaille tressaillons tressaillez tressaillent", ps("i", "tressaill"),
            "tressaillir", "tressailli", imp = "tressaille tressaillons tressaillez"),
        "clore" to Model("clos clos clôt - - closent", "- - - - - -", "clor", "clos", impf = "-",
            subj = "close closes close closions closiez closent", imp = "clos - -", ppr = "closant"),
        "haïr" to Model("hais hais hait haïssons haïssez haïssent", "haïs haïs haït haïmes haïtes haïrent", "haïr", "haï"),
        "envoyer" to Model("envoie envoies envoie envoyons envoyez envoient", ps("er", "envoy"), "enverr", "envoyé",
            imp = "envoie envoyons envoyez")
    )

    /** Models that apply only to these verbs (others merely end the same way: baller isn't aller). */
    private val EXACT: Map<String, Set<String>> = mapOf(
        "aller" to emptySet(), "être" to emptySet(), "avoir" to setOf("ravoir"), "taire" to emptySet(),
        "rire" to setOf("sourire"), "lire" to setOf("élire", "relire", "réélire"), "boire" to emptySet(),
        "croire" to setOf("accroire"), "dire" to setOf("redire"),
        "faire" to setOf("défaire", "refaire", "satisfaire", "contrefaire", "surfaire", "parfaire"), "suffire" to emptySet(),
        "plaire" to setOf("déplaire", "complaire"), "voir" to setOf("revoir", "entrevoir"), "fuir" to setOf("enfuir", "s'enfuir")
    )
    /** Verbs that look like a model above but conjugate like finir. */
    private val SECOND_GROUP = setOf("répartir", "assortir", "impartir", "asservir", "désassortir")
    private val ETRE = setOf(
        "aller", "arriver", "partir", "repartir", "sortir", "entrer", "rentrer", "monter", "remonter", "descendre",
        "redescendre", "naître", "renaître", "mourir", "rester", "retourner", "tomber", "retomber", "venir", "devenir",
        "revenir", "parvenir", "intervenir", "survenir", "provenir", "advenir", "décéder", "passer", "apparaître"
    )
    private val BOTH = setOf("monter", "descendre", "sortir", "passer", "rentrer", "retourner", "remonter", "redescendre")
    /** -eler/-eter verbs that take è instead of doubling (the rest double: appelle, jette). */
    private val E_GRAVE = setOf(
        "acheter", "racheter", "geler", "dégeler", "congeler", "surgeler", "peler", "modeler", "remodeler", "haleter",
        "ciseler", "démanteler", "écarteler", "marteler", "déceler", "receler", "celer", "crocheter", "fileter", "corseter",
        "harceler", "lever", "décongeler", "regeler", "embreler", "encasteler"
    )
    private val H_ASPIRE = setOf(
        "haïr", "hurler", "heurter", "hausser", "hâter", "hanter", "harceler", "hisser", "hacher", "haleter", "hennir",
        "hérisser", "huer"
    )
    private const val VOWELS = "aâàäeéèêëiîïoôöuûùüyœæh"
    private const val CONS = "bcdfghjklmnpqrstvwxzç"

    private val PS_END = mapOf(
        "i" to "is is it îmes îtes irent", "u" to "us us ut ûmes ûtes urent",
        "in" to "ins ins int înmes întes inrent", "er" to "ai as a âmes âtes èrent"
    )
    private val IMPF = listOf("ais", "ais", "ait", "ions", "iez", "aient")
    private val FUT = listOf("ai", "as", "a", "ons", "ez", "ont")
    private val SUBJ = listOf("e", "es", "e", "ions", "iez", "ent")
    private val MUTE_PERSONS = setOf(0, 1, 2, 5)

    val SUBJECTS = listOf("je", "tu", "il", "nous", "vous", "ils")
    private val REFLEXIVES = listOf("me", "te", "se", "nous", "vous", "se")

    private fun words(s: String): List<String> = s.split(' ').filter { it.isNotEmpty() }

    private fun modelFor(inf: String): Pair<String, String>? {
        if (inf in SECOND_GROUP) return null
        var best: String? = null
        for (k in M.keys) if (inf.endsWith(k) && (best == null || k.length > best.length)) best = k
        if (best == null) return null
        if (best == "naître" && inf != "naître" && inf != "renaître") best = "aître"
        val only = EXACT[best]
        if (only != null && inf != best && inf !in only) return null
        return best to inf.substring(0, inf.length - best.length)
    }

    private val doubleLe = Regex("^(.*[^aeiouéèêy])e([$CONS])$")
    private val acuteBefore = Regex("^(.*)é([$CONS]{1,2}|gu|qu)$")

    /** The -er stem before a silent e: appeler → appell, acheter → achèt, préférer → préfèr, nettoyer → nettoi. */
    private fun muteStem(inf: String, stem: String, futur: Boolean = false): String {
        if ((inf.endsWith("eler") || inf.endsWith("eter")) && inf !in E_GRAVE && !inf.endsWith("éler") && !inf.endsWith("éter")) {
            return stem + stem.last()
        }
        if (stem.endsWith("oy") || stem.endsWith("uy") || stem.endsWith("ay")) return stem.dropLast(1) + "i"
        doubleLe.matchEntire(stem)?.let { m ->
            if (m.groupValues[2] != "x") return m.groupValues[1] + "è" + m.groupValues[2]
        }
        if (!futur) acuteBefore.matchEntire(stem)?.let { m -> return m.groupValues[1] + "è" + m.groupValues[2] }
        return stem
    }

    private fun firstGroup(inf: String): Tables {
        val r = inf.dropLast(2)
        fun soft(ending: String): String {
            if (ending.startsWith("a") || ending.startsWith("o") || ending.startsWith("â")) {
                if (r.endsWith("c")) return r.dropLast(1) + "ç"
                if (r.endsWith("g")) return r + "e"
            }
            return r
        }
        val mute = muteStem(inf, r)
        val presEnd = listOf("e", "es", "e", "ons", "ez", "ent")
        val pres = presEnd.mapIndexed { i, e -> (if (i in MUTE_PERSONS) mute else soft(e)) + e }
        val fs = muteStem(inf, r, futur = true) + "er"
        return Tables(
            present = pres,
            imparfait = IMPF.map { soft(it) + it },
            passeSimple = words(PS_END.getValue("er")).map { soft(it) + it },
            futur = FUT.map { fs + it },
            conditionnel = IMPF.map { fs + it },
            subjonctif = SUBJ.mapIndexed { i, e -> (if (i in MUTE_PERSONS) mute else r) + e },
            imperatif = listOf(pres[1].dropLast(1), pres[3], pres[4]),
            ppr = soft("ant") + "ant",
            pp = r + "é"
        )
    }

    private fun secondGroup(inf: String): Tables {
        val r = inf.dropLast(2)
        val pres = words("is is it issons issez issent").map { r + it }
        return Tables(
            present = pres,
            imparfait = IMPF.map { r + "iss" + it },
            passeSimple = words(PS_END.getValue("i")).map { r + it },
            futur = FUT.map { inf + it },
            conditionnel = IMPF.map { inf + it },
            subjonctif = SUBJ.map { r + "iss" + it },
            imperatif = listOf(pres[1], pres[3], pres[4]),
            ppr = r + "issant",
            pp = r + "i"
        )
    }

    private fun regularRe(inf: String): Tables {
        val r = inf.dropLast(2)
        val third = if (r.endsWith("d")) r else r + "t"
        val pres = listOf(r + "s", r + "s", third, r + "ons", r + "ez", r + "ent")
        return Tables(
            present = pres,
            imparfait = IMPF.map { r + it },
            passeSimple = words(PS_END.getValue("i")).map { r + it },
            futur = FUT.map { r + "r" + it },
            conditionnel = IMPF.map { r + "r" + it },
            subjonctif = SUBJ.map { r + it },
            imperatif = listOf(pres[1], pres[3], pres[4]),
            ppr = r + "ant",
            pp = r + "u"
        )
    }

    private fun fromModel(inf: String, key: String, prefix: String): Tables {
        val m = M.getValue(key)
        fun p(f: String) = if (f == "-") "-" else prefix + f
        val pres = words(m.pres).map(::p)
        val nous = pres[3]
        val impfStem = when {
            m.impf != null -> prefix + m.impf
            nous != "-" -> nous.dropLast(3)
            else -> ""
        }
        val impf = if (m.impersonal) listOf("-", "-", impfStem + "ait", "-", "-", "-") else IMPF.map { impfStem + it }
        val passe = when (val s = m.ps) {
            is Pair<*, *> -> words(PS_END.getValue(s.first as String)).map { prefix + (s.second as String) + it }
            else -> words(s as String).map(::p)
        }
        val fs = prefix + m.fut
        val fut = if (m.impersonal) listOf("-", "-", fs + "a", "-", "-", "-") else FUT.map { fs + it }
        val cond = if (m.impersonal) listOf("-", "-", fs + "ait", "-", "-", "-") else IMPF.map { fs + it }
        val subj = if (m.subj != null) words(m.subj).map(::p) else {
            val ils = pres[5]
            val st = if (ils.endsWith("ent")) ils.dropLast(3) else ils.dropLast(2)
            SUBJ.mapIndexed { i, e -> if (i in MUTE_PERSONS) st + e else impfStem + e }
        }
        val imp = when {
            m.imp == null -> listOf(pres[1], pres[3], pres[4])
            m.imp.isEmpty() -> listOf("-", "-", "-")
            else -> words(m.imp).map(::p)
        }
        return Tables(
            present = pres, imparfait = impf, passeSimple = passe, futur = fut, conditionnel = cond,
            subjonctif = subj, imperatif = imp,
            ppr = m.ppr?.let(::p) ?: (impfStem + "ant"),
            pp = prefix + m.pp,
            ppf = m.ppf?.let { words(it).map { f -> prefix + f } }
        )
    }

    /** The simple tenses of a plain infinitive ("lever", not "se lever"); null if it isn't a French infinitive. */
    fun simple(inf: String): Tables? {
        modelFor(inf)?.let { (key, prefix) -> return fromModel(inf, key, prefix) }
        return when {
            inf.endsWith("er") -> firstGroup(inf)
            inf.endsWith("ir") -> secondGroup(inf)
            inf.endsWith("re") -> regularRe(inf)
            else -> null
        }
    }

    private fun participles(t: Tables): List<String> {
        val pp = t.pp
        t.ppf?.let { return listOf(pp) + it }
        return when {
            pp.endsWith("s") -> listOf(pp, pp + "e", pp, pp + "es")
            pp.endsWith("x") -> listOf(pp, pp.dropLast(1) + "se", pp, pp.dropLast(1) + "ses")
            else -> listOf(pp, pp + "e", pp + "s", pp + "es")
        }
    }

    /** All the tables for a verb; "se laver" and "s'appeler" are pronominal. */
    fun conjugate(verb: String): Conjugation? {
        val reflexive = verb.startsWith("se ") || verb.startsWith("s'")
        val inf = when {
            verb.startsWith("se ") -> verb.substring(3)
            reflexive -> verb.substring(2)
            else -> verb
        }
        val t = simple(inf) ?: return null
        return Conjugation(
            verb = verb,
            infinitive = inf,
            reflexive = reflexive,
            aux = if (reflexive || inf in ETRE) "être" else "avoir",
            both = !reflexive && inf in BOTH,
            impersonal = t.present[0] == "-",
            tables = t,
            participles = participles(t)
        )
    }

    private fun startsWithVowel(form: String, inf: String): Boolean {
        val c = form.firstOrNull() ?: return false
        return c in VOWELS && !(c == 'h' && inf in H_ASPIRE)
    }

    /** "j'aime", "tu aimes", "je me lève", "il s'appelle"; "-" stays "-". */
    fun withPronoun(person: Int, form: String, inf: String, reflexive: Boolean): String {
        if (form == "-") return "-"
        if (reflexive) {
            val r = REFLEXIVES[person]
            val joined = if ((r == "me" || r == "te" || r == "se") && startsWithVowel(form, inf)) r.first() + "'" + form else "$r $form"
            return SUBJECTS[person] + " " + joined
        }
        if (person == 0 && startsWithVowel(form, inf)) return "j'$form"
        return SUBJECTS[person] + " " + form
    }

    /** The subjonctif with its "que": "que j'aie", "qu'il soit", "que je me lève". */
    fun withQue(person: Int, form: String, inf: String, reflexive: Boolean): String {
        val p = withPronoun(person, form, inf, reflexive)
        if (p == "-") return p
        return if (p.startsWith("il")) "qu'$p" else "que $p"
    }

    /** The impératif with its pronoun after the verb: "lève-toi", "levons-nous", "levez-vous". */
    fun imperative(index: Int, form: String, reflexive: Boolean): String {
        if (form == "-" || !reflexive) return form
        return form + listOf("-toi", "-nous", "-vous")[index]
    }

    /** "suis allé(e)", "avons mangé", "nous sommes levé(e)s". */
    fun compound(c: Conjugation, person: Int, auxForms: List<String>): String {
        if (c.tables.present[person] == "-" && !c.impersonal) return "-"
        if (c.impersonal && person != 2) return "-"
        val aux = auxForms[person]
        if (c.aux == "avoir") return "$aux ${c.tables.pp}"
        val pp = c.tables.pp
        val plural = person >= 3
        val tail = when {
            pp.endsWith("s") -> if (plural) "(es)" else "(e)"
            else -> if (plural) "(e)s" else "(e)"
        }
        return "$aux $pp$tail"
    }

    val avoir: Tables by lazy { simple("avoir")!! }
    val etre: Tables by lazy { simple("être")!! }

    /** Every written form of [verb] (no passé simple, which is rare and clashes with common words). */
    fun forms(verb: String): Set<String> {
        val c = conjugate(verb) ?: return emptySet()
        val t = c.tables
        val out = linkedSetOf(c.infinitive)
        for (list in listOf(t.present, t.imparfait, t.futur, t.conditionnel, t.subjonctif, t.imperatif)) {
            list.filterTo(out) { it != "-" }
        }
        out.addAll(c.participles)
        if (t.ppr != "-") out.add(t.ppr)
        return out
    }
}
