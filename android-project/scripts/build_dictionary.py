#!/usr/bin/env python3
"""
Builds SlothLlama's offline Chinese dictionary (an SQLite file the app ships as an asset).

  build_dictionary.py <pycccedict wheel> <ToJyutping wheel> <output dir>

Sources, read as data straight out of the two checksum-pinned wheels (no code from them runs):
  * CC-CEDICT (pycccedict 1.2.0): headwords, Mandarin pinyin, English definitions.
    CC BY-SA 4.0, (c) MDBG and the CC-CEDICT contributors.
  * ToJyutping 3.2.0 (CanCLID): Cantonese readings, used to add Jyutping to every entry.
    BSD 2-Clause.

The database therefore carries CC BY-SA 4.0; the app shows both notices (licenses.txt).
"""
import gzip
import re
import sqlite3
import sys
import unicodedata
import zipfile
from pathlib import Path

SCHEMA_VERSION = 2

# ---------------------------------------------------------------- CC-CEDICT

LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$")


def read_cedict(wheel: Path):
    with zipfile.ZipFile(wheel) as z:
        name = next(n for n in z.namelist() if n.endswith("cedict_1_0_ts_utf-8_mdbg.txt.gz"))
        raw = gzip.decompress(z.read(name)).decode("utf-8")
    header, entries = [], []
    for line in raw.splitlines():
        if line.startswith("#"):
            header.append(line)
            continue
        m = LINE.match(line)
        if not m:
            continue
        trad, simp, pinyin, defs = m.groups()
        entries.append((trad, simp, pinyin.strip(), [d for d in defs.split("/") if d]))
    return header, entries

# ---------------------------------------------------------------- ToJyutping data
# trie.txt is a compact trie. Ported from ToJyutping/Trie.py (BSD 2-Clause): characters >= U+0100
# walk down the trie; each reading is two chars encoding an id; '~' joins alternatives for one
# syllable; '{' opens children of the current node and '}' closes them.

ONSET = ['', 'b', 'p', 'm', 'f', 'd', 't', 'n', 'l', 'g', 'k', 'ng', 'gw', 'kw', 'w', 'h', 'z', 'c', 's', 'j']
NUCLEUS = ['aa', 'a', 'e', 'i', 'o', 'u']
RHYME = ['oe', 'oen', 'oeng', 'oet', 'oek', 'eoi', 'eon', 'eot', 'yu', 'yun', 'yut', 'm', 'ng']
CODA = ['', 'i', 'u', 'm', 'n', 'ng', 'p', 't', 'k']


def jyutping_of(code: int) -> str:
    onset = ONSET[code // 402]
    rid = (code % 402) // 6
    rhyme = RHYME[rid - 54] if rid >= 54 else NUCLEUS[rid // 9] + CODA[rid % 9]
    return f"{onset}{rhyme}{code % 6 + 1}"


class Node:
    __slots__ = ("children", "readings")

    def __init__(self):
        self.children = {}
        self.readings = None  # list of readings; each reading = list of syllables, one per character


def read_jyutping_trie(wheel: Path) -> Node:
    with zipfile.ZipFile(wheel) as z:
        s = z.read("ToJyutping/trie.txt").decode("utf-8")
    root = Node()
    stack, depths = [root], [0]
    i = 1
    while stack:
        node, depth = stack[-1], depths[-1]
        while ord(s[i]) >= 256:
            node = node.children.setdefault(s[i], Node())
            i += 1
            depth += 1
        readings = []
        while ord(s[i]) < 123:
            syllables, alternatives, count = [], [], 0
            while count < depth:
                alternatives.append(jyutping_of((ord(s[i]) - 33) * 90 + (ord(s[i + 1]) - 33)))
                i += 2
                if s[i] == "~":
                    i += 1
                else:
                    syllables.append(alternatives[0])  # first alternative is the usual one
                    alternatives = []
                    count += 1
            readings.append(syllables)
        if readings:
            node.readings = readings
        if s[i] == "{":
            i += 1
            stack.append(node)
            depths.append(depth)
        elif s[i] == "}":
            i += 1
            stack.pop()
            depths.pop()
    return root


def trie_words(root: Node):
    """Every word stored in the trie with its usual reading (iterative: the trie is deep)."""
    stack = [(root, "")]
    while stack:
        node, prefix = stack.pop()
        if node.readings and len(node.readings[0]) == len(prefix):
            yield prefix, " ".join(node.readings[0])
        for c, child in node.children.items():
            stack.append((child, prefix + c))


def jyutping_for(word: str, root: Node) -> str:
    """Longest-match readings across the word, like ToJyutping's get(). '' if any character is unknown."""
    out, i = [], 0
    while i < len(word):
        node, best, best_end = root, None, i
        for j in range(i, len(word)):
            node = node.children.get(word[j])
            if node is None:
                break
            if node.readings:
                best, best_end = node.readings[0], j
        if best is None or len(best) != best_end - i + 1:
            if is_han(word[i]):
                return ""
            i += 1  # punctuation, Latin letters: no syllable
            continue
        out.extend(best)
        i = best_end + 1
    return " ".join(out)


def is_han(c: str) -> bool:
    o = ord(c)
    return 0x3400 <= o <= 0x9FFF or 0xF900 <= o <= 0xFAFF or 0x20000 <= o <= 0x3134F

# ---------------------------------------------------------------- pinyin search key


def pinyin_key(pinyin: str) -> str:
    """'Ni3 hao3' -> 'nihao', 'lu:4' -> 'lv'. What people type when searching by pinyin."""
    p = pinyin.lower().replace("u:", "v").replace("ü", "v")
    p = unicodedata.normalize("NFKD", p)
    return re.sub(r"[^a-z]", "", p)

# ---------------------------------------------------------------- build


def build(cedict_wheel: Path, jyutping_wheel: Path, out_dir: Path):
    header, entries = read_cedict(cedict_wheel)
    trie = read_jyutping_trie(jyutping_wheel)
    out_dir.mkdir(parents=True, exist_ok=True)
    db_path = out_dir / "dictionary.db"
    tmp = db_path.with_suffix(".tmp")
    tmp.unlink(missing_ok=True)

    con = sqlite3.connect(tmp)
    con.executescript(
        """
        PRAGMA page_size = 4096;
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY,
            trad TEXT NOT NULL,
            simp TEXT NOT NULL,
            pinyin TEXT NOT NULL,
            jyutping TEXT NOT NULL,
            definitions TEXT NOT NULL,
            pinyin_key TEXT NOT NULL,
            -- How productive the word's rarest character is (how many headwords use it): a cheap
            -- commonness signal so English and pinyin searches list 吃 before 餔.
            rank INTEGER NOT NULL
        );
        -- Cantonese words that CC-CEDICT doesn't have (係咪, 唔使…): reading only.
        CREATE TABLE cantonese_words (word TEXT PRIMARY KEY, jyutping TEXT NOT NULL) WITHOUT ROWID;
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )
    uses = {}
    for trad, simp, _, _ in entries:
        for ch in set(trad) | set(simp):
            uses[ch] = uses.get(ch, 0) + 1
    with_jyutping = 0
    rows = []
    for n, (trad, simp, pinyin, defs) in enumerate(entries, start=1):
        jp = jyutping_for(trad, trie)
        if jp:
            with_jyutping += 1
        rank = min(uses[ch] for ch in simp)
        rows.append((n, trad, simp, pinyin, jp, "\n".join(defs), pinyin_key(pinyin), rank))
    con.executemany("INSERT INTO entries VALUES (?, ?, ?, ?, ?, ?, ?, ?)", rows)
    headwords = {e[0] for e in entries} | {e[1] for e in entries}
    extra = [(w, jp) for w, jp in trie_words(trie) if len(w) >= 2 and w not in headwords]
    con.executemany("INSERT OR IGNORE INTO cantonese_words VALUES (?, ?)", extra)
    con.executescript(
        """
        CREATE INDEX entries_trad ON entries(trad);
        CREATE INDEX entries_simp ON entries(simp);
        CREATE INDEX entries_pinyin_key ON entries(pinyin_key);
        """
    )
    cedict_date = next((h.split("=", 1)[1] for h in header if h.startswith("#! date=")), "")
    con.executemany(
        "INSERT INTO meta VALUES (?, ?)",
        [
            ("schema", str(SCHEMA_VERSION)),
            ("entries", str(len(rows))),
            ("cedict_date", cedict_date),
            ("with_jyutping", str(with_jyutping)),
            ("cantonese_words", str(len(extra))),
        ],
    )
    con.commit()
    con.execute("VACUUM")
    con.close()
    tmp.replace(db_path)

    with zipfile.ZipFile(jyutping_wheel) as z:
        bsd = z.read(next(n for n in z.namelist() if n.endswith("dist-info/LICENSE"))).decode("utf-8")
    (out_dir / "licenses.txt").write_text(
        "SlothLlama's offline dictionary is built from:\n\n"
        "CC-CEDICT (" + cedict_date[:10] + ")\n"
        "Community-maintained Chinese-English dictionary, published by MDBG.\n"
        "Licensed under Creative Commons Attribution-ShareAlike 4.0 International\n"
        "https://creativecommons.org/licenses/by-sa/4.0/\n"
        "Based on CEDICT, Copyright (C) 1997, 1998 Paul Andrew Denisowski.\n"
        "https://cc-cedict.org/wiki/\n\n"
        "The dictionary file in this app adds Cantonese readings to CC-CEDICT and is shared\n"
        "under the same CC BY-SA 4.0 license.\n\n"
        "Cantonese (Jyutping) readings: ToJyutping 3.2.0, https://github.com/CanCLID/ToJyutping\n\n"
        + bsd.strip() + "\n",
        encoding="utf-8",
    )
    print(f"dictionary.db: {len(rows)} entries, {with_jyutping} with Jyutping, "
          f"{len(extra)} extra Cantonese words, CC-CEDICT {cedict_date}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    build(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
