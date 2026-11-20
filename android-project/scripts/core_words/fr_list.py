"""Builds the French core list: the curated words, ranked by how often their forms occur (wordfreq)."""
import sys, re, json, collections
import mp, paths, fr_conj as F

NOUN_SHARE = {"partie": 0.8, "part": 0.8, "est": 0.01, "été": 0.15, "souvenir": 0.6, "pas": 0.05, "son": 0.15,
              "devoir": 0.15, "pouvoir": 0.15, "rire": 0.3, "dîner": 0.4, "or": 0.3, "fils": 0.6, "sens": 0.6, "note": 0.5,
              "personne": 0.7, "parti": 0.4, "tour": 1.0, "fin": 0.9, "suite": 0.9, "place": 0.9, "compte": 0.6, "marche": 0.4,
              "vol": 0.6, "plan": 0.9, "danse": 0.5, "pose": 0.2, "garde": 0.4, "aide": 0.5, "demande": 0.6, "offre": 0.6,
              "vente": 1.0, "rêve": 0.5, "regard": 0.9, "retour": 0.9, "départ": 1.0, "voyage": 0.7, "travail": 0.8,
              "conseil": 0.9, "appel": 0.8, "contrôle": 0.7, "échange": 0.7, "respect": 0.8, "soutien": 0.8, "début": 1.0}
NOUN_SHARE.update({"reste": 0.3, "passe": 0.2, "pense": 0.05, "sort": 0.4, "marche": 0.4})
MONTHS = {"janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"}
POS_W = {"det": 10, "pron": 10, "prep": 10, "conj": 10, "adv": 4, "v": 4, "num": 4, "interj": 3, "expr": 3, "adj": 2, "n": 2}


def read_curated(base=paths.HERE):
    seen = set(); out = []
    for line in open(base + "/fr_verbs.txt", encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        w, g = line.split("|", 1)
        if g == "-" or w in seen:
            continue
        seen.add(w); out.append(dict(w=w, p="v", g=g))
    for line in open(base + "/fr_nouns.txt", encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        w, gender, g = parts[0], parts[1], parts[2]
        if g == "-" or ("n", w, gender) in seen:
            continue
        seen.add(("n", w, gender))
        e = dict(w=w, p="n", gen=gender, g=g)
        if len(parts) > 3 and parts[3].startswith("pl="):
            e["pl"] = parts[3][3:]
        out.append(e)
    for line in open(base + "/fr_other.txt", encoding="utf-8"):
        line = line.rstrip("\n")
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        w, p, g = parts[0], parts[1], parts[2]
        if g == "-" or (p, w) in seen:
            continue
        seen.add((p, w))
        e = dict(w=w, p=p, g=g)
        if len(parts) > 3:
            for kv in parts[3].split(";"):
                k, v = kv.split("=", 1)
                e[k] = v
        out.append(e)
    return out


def noun_plural(w):
    if w.endswith(("s", "x", "z")):
        return w
    if w.endswith(("eau", "au", "eu")) and w not in ("pneu", "bleu"):
        return w + "x"
    if w.endswith("al") and w not in ("bal", "festival", "carnaval", "récital", "chacal", "régal"):
        return w[:-2] + "aux"
    return w + "s"


def adj_forms(e):
    w = e["w"]
    if "f" in e:
        f = e["f"]
    elif w.endswith("e"):
        f = w
    elif w.endswith("eux"):
        f = w[:-1] + "se"
    elif w.endswith("if"):
        f = w[:-1] + "ve"
    elif w.endswith("er"):
        f = w[:-2] + "ère"
    elif w.endswith(("el", "eil", "ien", "on")):
        f = w + w[-1] + "e"
    else:
        f = w + "e"
    if "mpl" in e:
        mpl = e["mpl"]
    elif w.endswith(("s", "x")):
        mpl = w
    elif w.endswith("al"):
        mpl = w[:-2] + "aux"
    elif w.endswith("eau"):
        mpl = w + "x"
    else:
        mpl = w + "s"
    fpl = e.get("fpl", f + "s")
    forms = [w, f, mpl, fpl]
    if "alt" in e:
        forms.append(e["alt"])
    return [x for x in forms if x != "-"]


def verb_forms(e):
    c = F.conjugate(e["w"])
    fs = {c["inf"]}
    # The passé simple is left out: its forms are rare in speech and collide with common words (plus, fis).
    for k in ("present", "imparfait", "futur", "conditionnel", "subjonctif", "imperatif"):
        fs.update(x for x in c[k] if x != "-")
    fs.update(c["pps"]); fs.add(c["ppr"])
    return fs


def forms_of(e):
    p = e["p"]
    if p == "v":
        return verb_forms(e)
    if p == "n":
        return {e["w"], e.get("pl") or noun_plural(e["w"])}
    if p == "adj":
        return set(adj_forms(e))
    fs = {e["w"]}
    if "forms" in e:
        fs.update(e["forms"].split(","))
    return fs


def token(form):
    return form.replace("'", "") if form.endswith("'") else form


def multi_score(w, alpha):
    """A phrase isn't one token in the counts: a hyphenated word takes half its rarer part; a
    phrase takes a tenth of its rarest word, or that word whole when it lives only in this phrase."""
    parts = [p for p in re.split(r"[ '-]", w) if p]
    rare = min(parts, key=lambda p: alpha.get(p, 0))
    if "-" in w and " " not in w:
        return 0.5 * alpha.get(rare, 0)
    if rare in ("parce", "tandis", "afin", "quant", "travers", "sein", "abord", "accord", "lieu", "cause", "face", "côté"):
        return alpha.get(rare, 0) * (0.9 if rare in ("parce", "tandis", "afin", "quant", "abord") else 0.4)
    return 0.1 * alpha.get(rare, 0)


def build():
    words = read_curated()
    freq = mp.freqs(paths.WF_FR)
    alpha = {w: f for w, f in freq.items() if re.match(r"^[a-zàâäçéèêëîïôöùûüÿœæ'-]+$", w)}
    total = sum(alpha.values())
    owners = collections.defaultdict(list)
    multi = []
    for i, e in enumerate(words):
        if (" " in e["w"] or "-" in e["w"] or ("'" in e["w"][1:-1] and e["w"] not in ("aujourd'hui", "quelqu'un"))) and e["p"] != "v":
            multi.append(i)
            continue
        if e["p"] == "v" and (e["w"].startswith("se ") or e["w"].startswith("s'")):
            continue  # pronominal verbs share the plain verb's forms
        for f in forms_of(e):
            for t in (token(f),) if f.endswith("'") else (f,):
                owners[t.lower()].append(i)
    score = collections.Counter()
    claimed = set()
    for t, idx in owners.items():
        if t not in alpha:
            continue
        # A form several words share is split: little words and verbs by how common their own
        # base form is ("suis" goes mostly to être); a noun as below, or by a fixed share
        # ("partie" is mostly the noun, "est" almost never is).
        nouns = [i for i in idx if words[i]["p"] == "n"]
        others = [i for i in idx if words[i]["p"] != "n"]
        if nouns and others:
            bw = lambda i: alpha.get(token(words[i]["w"].split()[0]), 0) + 1e-7
            if t in NOUN_SHARE:
                take = {i: NOUN_SHARE[t] / len(nouns) for i in nouns}
                rest = 1 - sum(take.values())
                ow = [POS_W.get(words[i]["p"], 2) * bw(i) for i in others]
                for i, w_ in zip(others, ow):
                    take[i] = rest * w_ / sum(ow)
                ws = [take[i] for i in idx]
            else:
                # Otherwise the noun weighs about a third of the shared form, the others their own
                # base form: "cuisine" is mostly the noun, as "cuisiner" is rare.
                ws = [0.3 * alpha[t] if i in nouns else bw(i) for i in idx]
        elif others:
            ws = [POS_W.get(words[i]["p"], 2) * (alpha.get(token(words[i]["w"].split()[0]), 0) + 1e-7) for i in idx]
        else:
            ws = [1.0 for _ in idx]
        tot = sum(ws)
        for i, wgt in zip(idx, ws):
            score[i] += alpha[t] * wgt / tot
        claimed.add(t)
    # wordfreq leans on news, where dates make the months look far commoner than in talk.
    for i, e in enumerate(words):
        if e["w"] in MONTHS:
            score[i] *= 0.25
    for i in multi:
        score[i] = multi_score(words[i]["w"], alpha)
    for i, e in enumerate(words):
        if e["p"] == "v" and (e["w"].startswith("se ") or e["w"].startswith("s'")):
            base = e["w"][3:] if e["w"].startswith("se ") else e["w"][2:]
            j = next((k for k, x in enumerate(words) if x["w"] == base and x["p"] == "v"), None)
            score[i] = 0.3 * score[j] if j is not None else alpha.get(base, 0) * 0.3
    order = sorted(range(len(words)), key=lambda i: -score[i])
    unclaimed = sorted(((f, t) for t, f in alpha.items() if t not in claimed), reverse=True)
    return words, score, order, total, unclaimed


if __name__ == "__main__":
    words, score, order, total, unclaimed = build()
    print(len(words), "words")
    print("top 60:", [words[i]["w"] for i in order[:60]])
    print("zero:", [words[i]["w"] for i in order if score[i] == 0][:80])
    cov = sum(score[i] for i in order if " " not in words[i]["w"]) / total
    print("coverage of all", round(cov, 3))
    print("unclaimed top 300:", [t for f, t in unclaimed[:300]])
