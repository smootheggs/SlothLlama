"""French conjugation by rules plus a table of irregular models (prototype for the Kotlin port)."""

VOWELS = "aâàäeéèêëiîïoôöuûùüyœæh"
H_ASPIRE = {"haïr", "hurler", "heurter", "hausser", "hâter", "hanter", "harceler", "hisser", "hacher", "haleter", "hennir", "hérisser", "huer"}

# Irregular models. Each key is an infinitive ending; a verb takes the longest key it ends with,
# and its forms are its prefix + the key's forms. Forms are written for the key itself.
# pres: 6 présent forms; ps: (type, stem) with type i/u/in, or 6 forms; fut: futur stem;
# pp: past participle (m sg); impf: imparfait stem if not from "nous"; subj: 6 forms if not by
# rule; imp: 3 impératif forms if not from présent; ppr: participe présent; aux: "être" if so.
M = {
    "être": dict(pres="suis es est sommes êtes sont", ps=("u", "f"), fut="ser", pp="été", impf="ét",
                 subj="sois sois soit soyons soyez soient", imp="sois soyons soyez", ppr="étant"),
    "avoir": dict(pres="ai as a avons avez ont", ps=("u", "e"), fut="aur", pp="eu",
                  subj="aie aies ait ayons ayez aient", imp="aie ayons ayez", ppr="ayant"),
    "aller": dict(pres="vais vas va allons allez vont", ps="allai allas alla allâmes allâtes allèrent", fut="ir", pp="allé",
                  subj="aille ailles aille allions alliez aillent", imp="va allons allez", aux="être"),
    "faire": dict(pres="fais fais fait faisons faites font", ps=("i", "f"), fut="fer", pp="fait",
                  subj="fasse fasses fasse fassions fassiez fassent"),
    "dire": dict(pres="dis dis dit disons dites disent", ps=("i", "d"), fut="dir", pp="dit"),
    "dédire": dict(pres="dédis dédis dédit dédisons dédisez dédisent", ps=("i", "déd"), fut="dédir", pp="dédit"),
    "interdire": dict(pres="interdis interdis interdit interdisons interdisez interdisent", ps=("i", "interd"), fut="interdir", pp="interdit"),
    "prédire": dict(pres="prédis prédis prédit prédisons prédisez prédisent", ps=("i", "préd"), fut="prédir", pp="prédit"),
    "contredire": dict(pres="contredis contredis contredit contredisons contredisez contredisent", ps=("i", "contred"), fut="contredir", pp="contredit"),
    "médire": dict(pres="médis médis médit médisons médisez médisent", ps=("i", "méd"), fut="médir", pp="médit"),
    "maudire": dict(pres="maudis maudis maudit maudissons maudissez maudissent", ps=("i", "maud"), fut="maudir", pp="maudit"),
    "suffire": dict(pres="suffis suffis suffit suffisons suffisez suffisent", ps=("i", "suff"), fut="suffir", pp="suffi"),
    "lire": dict(pres="lis lis lit lisons lisez lisent", ps=("u", "l"), fut="lir", pp="lu"),
    "rire": dict(pres="ris ris rit rions riez rient", ps=("i", "r"), fut="rir", pp="ri"),
    "crire": dict(pres="cris cris crit crivons crivez crivent", ps=("i", "criv"), fut="crir", pp="crit"),
    "uire": dict(pres="uis uis uit uisons uisez uisent", ps=("i", "uis"), fut="uir", pp="uit"),
    "luire": dict(pres="luis luis luit luisons luisez luisent", ps=("i", "luis"), fut="luir", pp="lui"),
    "nuire": dict(pres="nuis nuis nuit nuisons nuisez nuisent", ps=("i", "nuis"), fut="nuir", pp="nui"),
    "plaire": dict(pres="plais plais plaît plaisons plaisez plaisent", ps=("u", "pl"), fut="plair", pp="plu"),
    "taire": dict(pres="tais tais tait taisons taisez taisent", ps=("u", "t"), fut="tair", pp="tu"),
    "boire": dict(pres="bois bois boit buvons buvez boivent", ps=("u", "b"), fut="boir", pp="bu"),
    "croire": dict(pres="crois crois croit croyons croyez croient", ps=("u", "cr"), fut="croir", pp="cru"),
    "voir": dict(pres="vois vois voit voyons voyez voient", ps=("i", "v"), fut="verr", pp="vu"),
    "prévoir": dict(pres="prévois prévois prévoit prévoyons prévoyez prévoient", ps=("i", "prév"), fut="prévoir", pp="prévu"),
    "pourvoir": dict(pres="pourvois pourvois pourvoit pourvoyons pourvoyez pourvoient", ps=("u", "pourv"), fut="pourvoir", pp="pourvu"),
    "devoir": dict(pres="dois dois doit devons devez doivent", ps=("u", "d"), fut="devr", pp="dû", ppf="due dus dues"),
    "pouvoir": dict(pres="peux peux peut pouvons pouvez peuvent", ps=("u", "p"), fut="pourr", pp="pu",
                    subj="puisse puisses puisse puissions puissiez puissent", imp=""),
    "savoir": dict(pres="sais sais sait savons savez savent", ps=("u", "s"), fut="saur", pp="su", ppr="sachant",
                   subj="sache saches sache sachions sachiez sachent", imp="sache sachons sachez"),
    "vouloir": dict(pres="veux veux veut voulons voulez veulent", ps=("u", "voul"), fut="voudr", pp="voulu",
                    subj="veuille veuilles veuille voulions vouliez veuillent", imp="veuille veuillons veuillez"),
    "valoir": dict(pres="vaux vaux vaut valons valez valent", ps=("u", "val"), fut="vaudr", pp="valu",
                   subj="vaille vailles vaille valions valiez vaillent"),
    "falloir": dict(pres="- - faut - - -", ps="- - fallut - - -", fut="faudr", pp="fallu", impf="fall",
                    subj="- - faille - - -", imp="", ppr="-", impersonal=True),
    "pleuvoir": dict(pres="- - pleut - - -", ps="- - plut - - -", fut="pleuvr", pp="plu", impf="pleuv",
                     subj="- - pleuve - - -", imp="", ppr="pleuvant", impersonal=True),
    "cevoir": dict(pres="çois çois çoit cevons cevez çoivent", ps=("u", "ç"), fut="cevr", pp="çu"),
    "mouvoir": dict(pres="meus meus meut mouvons mouvez meuvent", ps=("u", "m"), fut="mouvr", pp="mû", ppf="mue mus mues"),
    "émouvoir": dict(pres="émeus émeus émeut émouvons émouvez émeuvent", ps=("u", "ém"), fut="émouvr", pp="ému"),
    "promouvoir": dict(pres="promeus promeus promeut promouvons promouvez promeuvent", ps=("u", "prom"), fut="promouvr", pp="promu"),
    "asseoir": dict(pres="assieds assieds assied asseyons asseyez asseyent", ps=("i", "ass"), fut="assiér", pp="assis"),
    "venir": dict(pres="viens viens vient venons venez viennent", ps=("in", "v"), fut="viendr", pp="venu", aux="être"),
    "tenir": dict(pres="tiens tiens tient tenons tenez tiennent", ps=("in", "t"), fut="tiendr", pp="tenu"),
    "mettre": dict(pres="mets mets met mettons mettez mettent", ps=("i", "m"), fut="mettr", pp="mis"),
    "prendre": dict(pres="prends prends prend prenons prenez prennent", ps=("i", "pr"), fut="prendr", pp="pris"),
    "battre": dict(pres="bats bats bat battons battez battent", ps=("i", "batt"), fut="battr", pp="battu"),
    "rompre": dict(pres="romps romps rompt rompons rompez rompent", ps=("i", "romp"), fut="rompr", pp="rompu"),
    "vaincre": dict(pres="vaincs vaincs vainc vainquons vainquez vainquent", ps=("i", "vainqu"), fut="vaincr", pp="vaincu"),
    "suivre": dict(pres="suis suis suit suivons suivez suivent", ps=("i", "suiv"), fut="suivr", pp="suivi"),
    "vivre": dict(pres="vis vis vit vivons vivez vivent", ps=("u", "véc"), fut="vivr", pp="vécu"),
    "aître": dict(pres="ais ais aît aissons aissez aissent", ps=("u", ""), fut="aîtr", pp="u"),
    "naître": dict(pres="nais nais naît naissons naissez naissent", ps=("i", "naqu"), fut="naîtr", pp="né", aux="être"),
    "eindre": dict(pres="eins eins eint eignons eignez eignent", ps=("i", "eign"), fut="eindr", pp="eint"),
    "aindre": dict(pres="ains ains aint aignons aignez aignent", ps=("i", "aign"), fut="aindr", pp="aint"),
    "oindre": dict(pres="oins oins oint oignons oignez oignent", ps=("i", "oign"), fut="oindr", pp="oint"),
    "clure": dict(pres="clus clus clut cluons cluez cluent", ps=("u", "cl"), fut="clur", pp="clu"),
    "inclure": dict(pres="inclus inclus inclut incluons incluez incluent", ps=("u", "incl"), fut="inclur", pp="inclus"),
    "soudre": dict(pres="sous sous sout solvons solvez solvent", ps=("u", "sol"), fut="soudr", pp="sous", ppf="soute sous soutes"),
    "résoudre": dict(pres="résous résous résout résolvons résolvez résolvent", ps=("u", "résol"), fut="résoudr", pp="résolu"),
    "coudre": dict(pres="couds couds coud cousons cousez cousent", ps=("i", "cous"), fut="coudr", pp="cousu"),
    "moudre": dict(pres="mouds mouds moud moulons moulez moulent", ps=("u", "moul"), fut="moudr", pp="moulu"),
    "partir": dict(pres="pars pars part partons partez partent", ps=("i", "part"), fut="partir", pp="parti", aux="être"),
    "sortir": dict(pres="sors sors sort sortons sortez sortent", ps=("i", "sort"), fut="sortir", pp="sorti", aux="être"),
    "sentir": dict(pres="sens sens sent sentons sentez sentent", ps=("i", "sent"), fut="sentir", pp="senti"),
    "mentir": dict(pres="mens mens ment mentons mentez mentent", ps=("i", "ment"), fut="mentir", pp="menti"),
    "servir": dict(pres="sers sers sert servons servez servent", ps=("i", "serv"), fut="servir", pp="servi"),
    "dormir": dict(pres="dors dors dort dormons dormez dorment", ps=("i", "dorm"), fut="dormir", pp="dormi"),
    "vêtir": dict(pres="vêts vêts vêt vêtons vêtez vêtent", ps=("i", "vêt"), fut="vêtir", pp="vêtu"),
    "ouvrir": dict(pres="ouvre ouvres ouvre ouvrons ouvrez ouvrent", ps=("i", "ouvr"), fut="ouvrir", pp="ouvert", imp="ouvre ouvrons ouvrez"),
    "ffrir": dict(pres="ffre ffres ffre ffrons ffrez ffrent", ps=("i", "ffr"), fut="ffrir", pp="ffert", imp="ffre ffrons ffrez"),
    "cueillir": dict(pres="cueille cueilles cueille cueillons cueillez cueillent", ps=("i", "cueill"), fut="cueiller", pp="cueilli",
                     imp="cueille cueillons cueillez"),
    "courir": dict(pres="cours cours court courons courez courent", ps=("u", "cour"), fut="courr", pp="couru"),
    "mourir": dict(pres="meurs meurs meurt mourons mourez meurent", ps=("u", "mour"), fut="mourr", pp="mort", aux="être"),
    "quérir": dict(pres="quiers quiers quiert quérons quérez quièrent", ps=("i", "qu"), fut="querr", pp="quis"),
    "fuir": dict(pres="fuis fuis fuit fuyons fuyez fuient", ps=("i", "fu"), fut="fuir", pp="fui"),
    "bouillir": dict(pres="bous bous bout bouillons bouillez bouillent", ps=("i", "bouill"), fut="bouillir", pp="bouilli"),
    "accroître": dict(pres="accrois accrois accroît accroissons accroissez accroissent", ps=("u", "accr"), fut="accroîtr", pp="accru"),
    "croître": dict(pres="croîs croîs croît croissons croissez croissent", ps="crûs crûs crût crûmes crûtes crûrent", fut="croîtr", pp="crû", ppf="crue crus crues", imp="crois croissons croissez"),
    "traire": dict(pres="trais trais trait trayons trayez traient", ps="- - - - - -", fut="trair", pp="trait"),
    "assaillir": dict(pres="assaille assailles assaille assaillons assaillez assaillent", ps=("i", "assaill"), fut="assaillir", pp="assailli",
                      imp="assaille assaillons assaillez"),
    "tressaillir": dict(pres="tressaille tressailles tressaille tressaillons tressaillez tressaillent", ps=("i", "tressaill"), fut="tressaillir",
                        pp="tressailli", imp="tressaille tressaillons tressaillez"),
    "clore": dict(pres="clos clos clôt - - closent", ps="- - - - - -", fut="clor", pp="clos", impf="-",
                  subj="close closes close closions closiez closent", imp="clos - -", ppr="closant"),
    "haïr": dict(pres="hais hais hait haïssons haïssez haïssent", ps="haïs haïs haït haïmes haïtes haïrent", fut="haïr", pp="haï"),
    "envoyer": dict(pres="envoie envoies envoie envoyons envoyez envoient", ps=("er", "envoy"), fut="enverr", pp="envoyé",
                    imp="envoie envoyons envoyez"),
}
# Models that apply only to these verbs (others merely end the same way: baller isn't aller).
EXACT = {"aller": set(), "être": set(), "avoir": {"ravoir"}, "taire": set(), "rire": {"sourire"},
         "lire": {"élire", "relire", "réélire"}, "boire": set(), "croire": {"accroire"}, "dire": {"redire"},
         "faire": {"défaire", "refaire", "satisfaire", "contrefaire", "surfaire", "parfaire"}, "suffire": set(),
         "plaire": {"déplaire", "complaire"}, "voir": {"revoir", "entrevoir"}, "fuir": {"enfuir", "s'enfuir"}}
# Verbs that look like a model above but conjugate like "finir".
SECOND_GROUP = {"répartir", "assortir", "impartir", "asservir", "désassortir", "ressortir_"}
# Verbs taking "être" in compound tenses (with their family where it applies).
ETRE = {"aller", "arriver", "partir", "repartir", "sortir", "entrer", "rentrer", "monter", "remonter", "descendre",
        "redescendre", "naître", "renaître", "mourir", "rester", "retourner", "tomber", "retomber", "venir", "devenir",
        "revenir", "parvenir", "intervenir", "survenir", "provenir", "advenir", "décéder", "passer", "apparaître"}
# Of those, the ones that take "avoir" when they have a direct object.
BOTH = {"monter", "descendre", "sortir", "passer", "rentrer", "retourner", "remonter", "redescendre"}
DOUBLING = {"appeler", "rappeler", "jeter", "rejeter", "projeter", "épeler", "renouveler", "feuilleter", "ficeler",
            "étinceler", "interpeller_", "épousseter", "chanceler", "niveler", "rejeter", "souffleter", "cacheter", "empaqueter", "étiqueter"}

PS_END = {"i": "is is it îmes îtes irent", "u": "us us ut ûmes ûtes urent", "in": "ins ins int înmes întes inrent",
          "er": "ai as a âmes âtes èrent"}
IMPF = "ais ais ait ions iez aient".split()
FUT = "ai as a ons ez ont".split()
COND = IMPF
SUBJ = "e es e ions iez ent".split()


def split(s):
    return s.split() if isinstance(s, str) else s


def model_for(inf):
    if inf in SECOND_GROUP:
        return None, None
    best = None
    for k in M:
        if inf.endswith(k) and (best is None or len(k) > len(best)):
            best = k
    if best is None:
        return None, None
    if best == "naître" and inf not in ("naître", "renaître"):
        best = "aître"
    if best in EXACT and inf != best and inf not in EXACT[best]:
        return None, None
    # "-aître" only for -aître verbs that aren't naître; "-uire" only real -uire verbs.
    return best, inf[: len(inf) - len(best)]


CONS = "bcdfghjklmnpqrstvwxzç"


# -eler/-eter verbs that take è instead of doubling (the rest double: appelle, jette).
E_GRAVE = {"acheter", "racheter", "geler", "dégeler", "congeler", "surgeler", "peler", "modeler", "remodeler", "haleter",
           "ciseler", "démanteler", "écarteler", "marteler", "déceler", "receler", "celer", "crocheter", "fileter", "corseter",
           "harceler", "lever", "décongeler", "regeler", "embreler", "encasteler"}


def mute_stem(inf, stem, futur=False):
    """The -er stem before a silent e: appeler → appell, acheter → achèt, préférer → préfèr, nettoyer → nettoi."""
    import re
    if inf.endswith(("eler", "eter")) and inf not in E_GRAVE and not inf.endswith(("éler", "éter")):
        return stem + stem[-1]
    if stem.endswith(("oy", "uy", "ay")):
        return stem[:-1] + "i"
    m = re.match(r"^(.*[^aeiouéèêy])e([" + CONS + r"])$", stem)
    if m and m.group(2) != "x":
        return m.group(1) + "è" + m.group(2)
    if not futur:
        m = re.match(r"^(.*)é([" + CONS + r"]{1,2}|gu|qu)$", stem)
        if m:
            return m.group(1) + "è" + m.group(2)
    return stem


def first_group(inf):
    r = inf[:-2]

    def soft(ending):
        if ending[:1] in ("a", "o", "â"):
            if r.endswith("c"):
                return r[:-1] + "ç"
            if r.endswith("g"):
                return r + "e"
        return r

    mute = mute_stem(inf, r)
    pres_end = "e es e ons ez ent".split()
    pres = [(mute if i in (0, 1, 2, 5) else soft(e)) + e for i, e in enumerate(pres_end)]
    impf = [soft(e) + e for e in IMPF]
    ps = [soft(e) + e for e in split(PS_END["er"])]
    fs = mute_stem(inf, r, futur=True) + "er"
    subj = [(mute if i in (0, 1, 2, 5) else r) + e for i, e in enumerate(SUBJ)]
    return dict(present=pres, imparfait=impf, passe_simple=ps, futur=[fs + e for e in FUT], conditionnel=[fs + e for e in COND],
                subjonctif=subj, imperatif=[pres[1][:-1], pres[3], pres[4]], ppr=soft("ant") + "ant", pp=r + "é")


def second_group(inf):
    r = inf[:-2]
    pres = [r + e for e in "is is it issons issez issent".split()]
    return dict(present=pres, imparfait=[r + "iss" + e for e in IMPF], passe_simple=[r + e for e in split(PS_END["i"])],
                futur=[inf + e for e in FUT], conditionnel=[inf + e for e in COND],
                subjonctif=[r + "iss" + e for e in SUBJ], imperatif=[pres[1], pres[3], pres[4]], ppr=r + "issant", pp=r + "i")


def regular_re(inf):
    r = inf[:-2]  # rendre → rend
    third = r if r.endswith(("d",)) else r + "t"
    pres = [r + "s", r + "s", third, r + "ons", r + "ez", r + "ent"]
    return dict(present=pres, imparfait=[r + e for e in IMPF], passe_simple=[r + e for e in split(PS_END["i"])],
                futur=[r + "r" + e for e in FUT], conditionnel=[r + "r" + e for e in COND],
                subjonctif=[r + e for e in SUBJ], imperatif=[pres[1], pres[3], pres[4]], ppr=r + "ant", pp=r + "u")


def from_model(inf):
    key, prefix = model_for(inf)
    m = M[key]
    P = lambda f: "-" if f == "-" else prefix + f
    pres = [P(f) for f in split(m["pres"])]
    nous = pres[3]
    impf_stem = prefix + m["impf"] if "impf" in m else (nous[:-3] if nous != "-" else None)
    if m.get("impersonal"):
        impf = ["-", "-", impf_stem + "ait", "-", "-", "-"]
    else:
        impf = [impf_stem + e for e in IMPF]
    if isinstance(m["ps"], tuple):
        t, st = m["ps"]
        ps = [prefix + st + e for e in split(PS_END[t])]
    else:
        ps = [P(f) for f in split(m["ps"])]
    fs = prefix + m["fut"]
    if m.get("impersonal"):
        fut = ["-", "-", fs + "a", "-", "-", "-"]; cond = ["-", "-", fs + "ait", "-", "-", "-"]
    else:
        fut = [fs + e for e in FUT]; cond = [fs + e for e in COND]
    if "subj" in m:
        subj = [P(f) for f in split(m["subj"])]
    else:
        ils = pres[5]
        st = ils[:-3] if ils.endswith("ent") else ils[:-2]
        subj = [st + e if i in (0, 1, 2, 5) else impf_stem + e for i, e in enumerate(SUBJ)]
    if "imp" in m:
        imp = [P(f) for f in split(m["imp"])] if m["imp"] else ["-", "-", "-"]
    else:
        imp = [pres[1], pres[3], pres[4]]
    ppr = P(m["ppr"]) if "ppr" in m else impf_stem + "ant"
    pp = prefix + m["pp"]
    out = dict(present=pres, imparfait=impf, passe_simple=ps, futur=fut, conditionnel=cond, subjonctif=subj,
               imperatif=imp, ppr=ppr, pp=pp)
    if "ppf" in m:
        out["ppf"] = [prefix + f for f in split(m["ppf"])]
    return out


def simple(inf):
    if model_for(inf)[0]:
        return from_model(inf)
    if inf.endswith("er"):
        return first_group(inf)
    if inf.endswith("ir"):
        return second_group(inf)
    if inf.endswith("re"):
        return regular_re(inf)
    raise ValueError(inf)


def participles(c):
    pp = c["pp"]
    if "ppf" in c:
        return [pp] + c["ppf"]
    if pp.endswith("s"):
        return [pp, pp + "e", pp, pp + "es"]
    if pp.endswith("x"):
        return [pp, pp[:-1] + "se", pp, pp[:-1] + "ses"]
    return [pp, pp + "e", pp + "s", pp + "es"]


def aux_of(inf):
    base = inf
    return "être" if base in ETRE else "avoir"


def conjugate(verb):
    """All the tables for a verb ("se laver" and "s'appeler" are pronominal)."""
    refl = verb.startswith("se ") or verb.startswith("s'")
    inf = verb[3:] if verb.startswith("se ") else (verb[2:] if refl else verb)
    c = simple(inf)
    c["inf"] = inf
    c["reflexive"] = refl
    c["aux"] = "être" if refl else aux_of(inf)
    c["both"] = (not refl) and inf in BOTH
    c["pps"] = participles(c)
    c["impersonal"] = c["present"][0] == "-"
    av = simple("avoir") if c["aux"] == "avoir" else simple("être")
    c["passe_compose"] = ["-" if c["present"][i] == "-" else av["present"][i] + " " + c["pp"] for i in range(6)]
    c["plus_que_parfait"] = ["-" if c["present"][i] == "-" else av["imparfait"][i] + " " + c["pp"] for i in range(6)]
    return c


SUBJ_PRON = ["je", "tu", "il", "nous", "vous", "ils"]
REFL = ["me", "te", "se", "nous", "vous", "se"]


def starts_vowel(w, inf):
    return w[:1] in VOWELS and not (w[:1] == "h" and inf in H_ASPIRE) and not (w[:1] == "y" and False)


def with_pronoun(i, form, inf, reflexive):
    if form == "-":
        return "-"
    if reflexive:
        r = REFL[i]
        r = (r[0] + "'") if r in ("me", "te", "se") and starts_vowel(form, inf) else r + " "
        form = r + form
        p = SUBJ_PRON[i]
        return ("j'" if False else p + " ") + form if i != 0 else "je " + form
    p = SUBJ_PRON[i]
    if i == 0 and starts_vowel(form, inf):
        return "j'" + form
    return p + " " + form
