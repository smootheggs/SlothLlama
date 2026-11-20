"""Builds the Cantonese core list from HKCanCor counts (spoken Hong Kong Cantonese, 1997-98)."""
import sys, re, glob, collections
import hkcancor, cedict, paths

CORPUS = paths.HKCANCOR

# HKCanCor writes some particles and words with rare or private characters; these are the
# spellings Hong Kong writers use now.
NORM = {"𡃉": "㗎", "囖": "囉", "嚹": "喇", "𠻺": "啊", "𠸏": "嘅", "𠺢": "㗎", "𡁵": "緊", "𠻹": "添", "𡃓": "噃",
        "鐘意": "鍾意", "哩個": "呢個", "哩啲": "呢啲", "哩度": "呢度", "哩樣": "呢樣", "哩排": "呢排", "哩次": "呢次",
        "哩種": "呢種", "哩間": "呢間", "哩位": "呢位", "哩": "呢", "乜嘢": "乜嘢",
        "𠵱家": "而家", "唓": "唓", "嘞": "嘞", "D": "啲"}
# HKCanCor writes both laa1 and laa3 as 喇; writers now keep 啦 for laa1.
JP_NORM = {("喇", "laa1"): "啦"}
POS = {"v": "v", "vu": "v", "v1": "v", "vk": "v", "Vg": "v", "vn": "n", "r": "pron", "d": "adv", "d1": "adv",
       "y": "part", "y1": "part", "u": "part", "n": "n", "Ng": "n", "t": "n", "s": "n", "c": "conj", "a": "adj",
       "an": "adj", "Ag": "adj", "b": "adj", "z": "adj", "q": "cl", "e": "interj", "o": "interj", "m": "num",
       "p": "prep", "f": "loc", "l": "expr", "l1": "expr", "i": "expr"}
DROP_POS = {"nr", "ns", "nt", "nz", "nx", "xnr", "xns", "xnt", "xnz", "xjn", "xjnt", "jn", "jnt", "jnz", "NZg", "JNTg", "NTg", "h", "k", "g1", "g2"}
HAN = re.compile(r"^[㐀-鿿\U00020000-\U0002ffff]+$")
NUMCH = set("一二三四五六七八九十百千萬兩零幾")
FILLER = {"誒", "喀", "嗯", "呃", "咦", "嘩", "哎", "唔", "啊"}


def norm(w):
    if w in NORM:
        return NORM[w]
    return "".join(NORM.get(ch, ch) for ch in w)


def tidy(d):
    """A CEDICT sense, cut down for a Cantonese card: its Mandarin measure words and
    Mandarin-equivalent notes don't apply here."""
    d = re.sub(r"\s*\[[^\]]*\]", "", d)
    if d.startswith("CL:") or d.startswith("Taiwan pr") or d.startswith("also pr"):
        return ""
    d = re.sub(r"\s*\((Cantonese|Cant\.|Tw|dialect|coll\.)\)\s*", " ", d)
    d = re.sub(r";?\s*Mandarin equivalent:.*$", "", d)
    d = re.sub(r"\bsb\b", "someone", d)
    d = re.sub(r"\bsth\b", "something", d)
    return d.strip(" ;,")


def read_overrides(path):
    """word|jyutping ('-' keeps the corpus's)|pos list ('-' keeps)|gloss ('-' keeps CEDICT's)."""
    out = {}
    try:
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                w, jp, p, g = line.split("|", 3)
                out[w] = (jp, p, g)
    except FileNotFoundError:
        pass
    return out


def build(cap=1500, min_count=3, over_path=paths.HERE + "/yue_over.txt"):
    raw = hkcancor.counts(CORPUS)
    total = sum(raw.values())
    by = collections.defaultdict(lambda: dict(n=0, pos=collections.Counter(), jp=collections.Counter()))
    for (w, p, jp), n in raw.items():
        w = JP_NORM.get((w, jp)) or norm(w)
        if not HAN.match(w) or p in DROP_POS or p not in POS:
            continue
        if len(w) > 1 and all(ch in NUMCH for ch in w):
            continue  # 三十, 一百: a number, not a word to learn
        e = by[w]
        e["n"] += n
        e["pos"][POS[p]] += n
        e["jp"][jp] += n
    ce = cedict.load()
    trad = collections.defaultdict(list)
    for s, ents in ce.items():
        for t, s_, py, defs in ents:
            trad[t].append((py, defs))
    over = read_overrides(over_path)
    rows = []
    for w, e in by.items():
        if e["n"] < min_count and w not in over:
            continue
        cats = [p for p, n in e["pos"].most_common() if n >= max(2, e["n"] * 0.1)] or [e["pos"].most_common(1)[0][0]]
        if cats == ["interj"] and w in FILLER:
            continue
        jp = e["jp"].most_common(1)[0][0]
        g = ""
        cand = trad.get(w, [])
        if cand:
            defs = [d for py, ds in cand for d in ds if not re.match(r"^(surname|variant of|old variant|used in|see |abbr)", d)]
            g = "; ".join(x for x in (tidy(d) for d in defs) if x)
            g = "; ".join(g.split("; ")[:3])
        src = "cedict" if g else None
        if w in over:
            o = over[w]
            if o[0] != "-":
                jp = o[0]
            if o[1] != "-":
                cats = o[1].split(",")
            if o[2] != "-":
                g, src = o[2], "own"
        if g == "x":
            continue  # an override of "x" leaves the word out
        rows.append(dict(w=w, jp=jp, p=cats, g=g, src=src, s=e["n"] / total, n=e["n"]))
    rows.sort(key=lambda r: -r["n"])
    return rows[:cap], total


if __name__ == "__main__":
    rows, total = build()
    print(len(rows), "coverage", round(sum(r["s"] for r in rows), 3), "no gloss", sum(1 for r in rows if not r["g"]))
    lo, hi = (int(sys.argv[1]), int(sys.argv[2])) if len(sys.argv) > 2 else (0, 200)
    for r in rows[lo:hi]:
        print(r["w"], r["jp"], ",".join(r["p"]), r["n"], "|", (r["src"] or "-"), r["g"][:50])
