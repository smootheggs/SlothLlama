"""Builds the Mandarin core list from complete-hsk-vocabulary, ranked by wordfreq."""
import sys, re, json, collections
import mp, paths

HSK = paths.HSK
WF = paths.WF_ZH

# complete-hsk part-of-speech tags (ICTCLAS style) -> the app's categories.
POS = {"n": "n", "t": "n", "s": "n", "vn": "v", "v": "v", "a": "adj", "an": "adj", "ad": "adj", "b": "adj",
       "z": "adj", "d": "adv", "q": "cl", "qv": "cl", "qt": "cl", "mq": "cl", "c": "conj", "cc": "conj",
       "r": "pron", "p": "prep", "m": "num", "u": "part", "y": "part", "e": "interj", "o": "interj",
       "l": "expr", "f": "loc"}
PROPER = {"nr", "ns", "nt", "nz"}
JUNK = re.compile(r"^(surname |variant of|old variant|archaic variant|Japanese variant|used in |abbr\. for|see |also written|"
                  r"\(old\)|\(archaic\)|erhua variant|\(literary\) variant)", re.I)


def form_score(f):
    m = f["meanings"]
    good = [x for x in m if not JUNK.match(x) and not x.startswith("(variant of")]
    s = len(good) * 2 + (1 if m and not JUNK.match(m[0]) else 0)
    py = f["transcriptions"]["pinyin"]
    if py[:1].isupper():
        s -= 20
    if not good:
        s -= 10
    # In a word of two or more syllables a neutral-tone reading is usually the everyday one
    # (告诉 gàosu "to tell", against gàosù "to press charges").
    if " " in f["transcriptions"]["numeric"] and "5" in f["transcriptions"]["numeric"]:
        s += 4
    return s


def level(e, kind="new"):
    ls = [l for l in e["level"] if l.startswith(kind + "-")]
    return min(int(l.split("-")[1]) for l in ls) if ls else None


def clean(meanings, n=3):
    out = []
    for x in meanings:
        if JUNK.match(x) or x.startswith("(variant of"):
            continue
        x = re.sub(r"\s*\[[^\]]*\]", "", x).strip()
        x = re.sub(r"[;,]?\s*\(?also pr\.\)?$", "", x).strip()
        if not x or x.startswith("also pr"):
            continue
        x = re.sub(r"\bsb\b", "someone", x)
        x = re.sub(r"\bsth\b", "something", x)
        if x and x not in out:
            out.append(x)
        if len(out) >= n:
            break
    return "; ".join(out)


def read_overrides(path):
    """word|pinyin-numeric|gloss  (gloss '-' keeps the data's; pinyin '-' keeps the chosen reading)."""
    out = {}
    try:
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                w, py, g = line.split("|", 2)
                out[w] = (py, g)
    except FileNotFoundError:
        pass
    return out


def build(cap=2500, over_path=paths.HERE + "/zh_over.txt"):
    data = json.load(open(HSK, encoding="utf-8"))
    wf = mp.freqs(WF)
    over = read_overrides(over_path)
    total = sum(wf.values())
    rows = []
    for e in data:
        lv = level(e)
        if lv is None or lv > 6:
            continue
        tags = [t for t in e["pos"] if t in POS]
        if not tags or all(t in PROPER for t in e["pos"]):
            continue
        forms = sorted(e["forms"], key=form_score, reverse=True)
        main = forms[0]
        w = e["simplified"]
        if w in over and over[w][0] != "-":
            want = over[w][0].replace(" ", "").lower()
            pick = [f for f in e["forms"] if f["transcriptions"]["numeric"].replace(" ", "").lower() == want]
            if pick:
                main = max(pick, key=form_score)
        gloss = clean(main["meanings"])
        if w in over and over[w][1] != "-":
            gloss = over[w][1]
        cats = []
        for t in e["pos"]:
            c = POS.get(t)
            if c and c not in cats:
                cats.append(c)
        alts = []
        for f in e["forms"]:
            if f is main or form_score(f) < 1:
                continue
            py = f["transcriptions"]["pinyin"]
            if py == main["transcriptions"]["pinyin"] or any(a[0] == py for a in alts):
                continue
            g = clean(f["meanings"], 2)
            if g:
                alts.append([py, f["transcriptions"]["numeric"], g])
        cls = []
        for f in [main] + e["forms"]:
            for c in f["classifiers"]:
                if c not in cls:
                    cls.append(c)
        trad = main["traditional"]
        rows.append(dict(w=w, t=trad if trad != w else None, py=main["transcriptions"]["pinyin"],
                         pn=main["transcriptions"]["numeric"], p=cats, g=gloss, hsk=lv, cl=cls[:4] or None,
                         alt=alts[:3] or None, s=wf.get(w, 0), hr=e["frequency"]))
    # complete-hsk's rank reads like speech (的 了 我 是 你…); wordfreq leans on news, so it only
    # breaks ties and gives each word its share of running text.
    rows.sort(key=lambda r: (r["hr"], -r["s"]))
    rows = rows[:cap]
    return rows, total


if __name__ == "__main__":
    rows, total = build()
    print(len(rows), "coverage", round(sum(r["s"] for r in rows) / total, 3))
    print(collections.Counter(c for r in rows for c in r["p"][:1]))
    for r in rows[:int(sys.argv[1]) if len(sys.argv) > 1 else 150]:
        if len(sys.argv) > 2 and len(r["w"]) > 1:
            continue
        print(r["w"], r["py"], r["p"], "|", r["g"][:70], "| alt:", [a[0] for a in r["alt"] or []], r["cl"])
