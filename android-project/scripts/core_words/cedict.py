"""CC-CEDICT reader: simplified -> [(traditional, simplified, pinyin, [senses])]."""
import gzip, re, collections
import paths
LINE = re.compile(r"^(\S+) (\S+) \[([^\]]*)\] /(.*)/\s*$")


def load():
    out = collections.defaultdict(list)
    for line in gzip.open(paths.CEDICT).read().decode("utf-8").splitlines():
        if line.startswith("#"):
            continue
        m = LINE.match(line)
        if m:
            t, s, p, d = m.groups()
            out[s].append((t, s, p, [x for x in d.split("/") if x]))
    return out
