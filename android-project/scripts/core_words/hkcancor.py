"""Word counts with part of speech and Jyutping from HKCanCor (CHAT files, read as data)."""
import glob, re, collections
def counts(directory):
    c = collections.Counter()
    for path in sorted(glob.glob(directory + "/*.cha")):
        lines = open(path, encoding="utf-8").read().split("\n")
        # join continuation lines (start with a tab)
        joined = []
        for ln in lines:
            if ln.startswith("\t") and joined:
                joined[-1] += " " + ln.strip()
            else:
                joined.append(ln)
        words = None
        for ln in joined:
            if ln.startswith("*"):
                words = ln.split("\t", 1)[1].split() if "\t" in ln else []
            elif ln.startswith("%mor:") and words is not None:
                mors = ln.split("\t", 1)[1].split()
                ws = [w for w in words if w not in "?.,!-" and not re.match(r"^[\W_]+$", w)]
                ms = [m for m in mors if "|" in m]
                if len(ws) == len(ms):
                    for w, m in zip(ws, ms):
                        pos, jp = m.split("|", 1)
                        c[(w, pos, jp)] += 1
                words = None
    return c
