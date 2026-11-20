"""Builds SlothLlama's Core words lists: app/src/main/assets/core/{fr,zh,yue}.json.

    CORE_SRC=/path/to/sources python3 -I build.py OUT_DIR [GOLDEN_JSON]

See paths.py for what CORE_SRC holds and SOURCES.md for where each source comes from.
The output is committed, so building the app never needs these sources.
"""
import sys, os, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import paths, fr_list, fr_conj, zh_list, yue_list


def notes(path):
    """word|note|src ~ roman ~ English|..."""
    out = {}
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        ex = []
        for p in parts[2:]:
            bits = [b.strip() for b in p.split("~")]
            if len(bits) == 3:
                ex.append(bits)
        out[parts[0]] = dict(n=parts[1], ex=ex)
    return out


def ppm(share):
    return round(share * 1e6, 2)


def french():
    words, score, order, total, _ = fr_list.build()
    nt = notes(paths.HERE + "/fr_notes.txt")
    out = []
    used = set()
    for r, i in enumerate(order, 1):
        e = words[i]
        row = dict(w=e["w"], p=[e["p"]], g=e["g"], r=r, s=ppm(score[i] / total))
        if e["p"] == "n":
            row["gen"] = e["gen"]
            pl = e.get("pl") or fr_list.noun_plural(e["w"])
            if pl != e["w"] + "s":
                row["pl"] = pl
        elif e["p"] == "adj":
            f = fr_list.adj_forms(e)
            row["af"] = f[1:4]
            if len(f) > 4:
                row["bv"] = f[4]
        elif e["p"] != "v" and "forms" in e:
            row["fm"] = e["forms"].split(",")
        if e["w"] in nt and e["w"] not in used and e["p"] in ("v", "det", "pron", "prep", "conj", "adv"):
            row.update(nt[e["w"]]); used.add(e["w"])
        out.append(row)
    return dict(lang="fr", words=out)


def mandarin():
    rows, total = zh_list.build()
    nt = notes(paths.HERE + "/zh_notes.txt")
    out = []
    for r, x in enumerate(rows, 1):
        row = dict(w=x["w"], p=x["p"], g=x["g"], r=r, s=ppm(x["s"] / total), py=x["py"], pn=x["pn"], hsk=x["hsk"])
        for k, key in (("t", "t"), ("cl", "cl"), ("alt", "or")):
            if x.get(k):
                row[key] = x[k]
        if x["w"] in nt:
            row.update(nt[x["w"]])
        out.append(row)
    missing = [w for w in nt if w not in {x["w"] for x in rows}]
    if missing:
        print("zh notes without a word:", missing, file=sys.stderr)
    return dict(lang="zh", words=out)


def cantonese():
    rows, total = yue_list.build()
    nt = notes(paths.HERE + "/yue_notes.txt")
    out = []
    for r, x in enumerate(rows, 1):
        row = dict(w=x["w"], p=x["p"], g=x["g"], r=r, s=ppm(x["s"]), jp=x["jp"])
        if x["w"] in nt:
            row.update(nt[x["w"]])
        out.append(row)
    missing = [w for w in nt if w not in {x["w"] for x in rows}]
    if missing:
        print("yue notes without a word:", missing, file=sys.stderr)
    return dict(lang="yue", words=out)


def golden(fr):
    """Every verb's tables from the Python engine, for the Kotlin port's unit test."""
    keys = ("present", "imparfait", "passe_simple", "futur", "conditionnel", "subjonctif", "imperatif")
    out = {}
    for row in fr["words"]:
        if row["p"] != ["v"]:
            continue
        c = fr_conj.conjugate(row["w"])
        g = {k: c[k] for k in keys}
        g.update(ppr=c["ppr"], pps=c["pps"], aux=c["aux"], both=c["both"], reflexive=c["reflexive"])
        g["persons"] = [fr_conj.with_pronoun(i, c["present"][i], c["inf"], c["reflexive"]) for i in range(6)]
        out[row["w"]] = g
    return out


def main():
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    fr = french()
    for name, data in (("fr", fr), ("zh", mandarin()), ("yue", cantonese())):
        with open(os.path.join(out_dir, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
            f.write("\n")
        print(name, len(data["words"]), "words")
    if len(sys.argv) > 2:
        with open(sys.argv[2], "w", encoding="utf-8") as f:
            json.dump(golden(fr), f, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
            f.write("\n")


if __name__ == "__main__":
    main()
