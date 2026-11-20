"""Minimal msgpack reader for wordfreq's cBpack files (read as data, no third-party code)."""
import gzip, struct
def load(path):
    data = gzip.open(path).read()
    pos = 0
    def rd():
        nonlocal pos
        b = data[pos]; pos += 1
        if b <= 0x7f: return b
        if 0x80 <= b <= 0x8f: return {rd(): rd() for _ in range(b & 0x0f)}
        if 0x90 <= b <= 0x9f: return [rd() for _ in range(b & 0x0f)]
        if 0xa0 <= b <= 0xbf: n = b & 0x1f; s = data[pos:pos+n].decode(); pos += n; return s
        if b == 0xc0: return None
        if b == 0xc2: return False
        if b == 0xc3: return True
        if b in (0xd9, 0xda, 0xdb):
            n = {0xd9:1,0xda:2,0xdb:4}[b]; L = int.from_bytes(data[pos:pos+n],'big'); pos += n
            s = data[pos:pos+L].decode(); pos += L; return s
        if b in (0xdc, 0xdd):
            n = {0xdc:2,0xdd:4}[b]; L = int.from_bytes(data[pos:pos+n],'big'); pos += n
            return [rd() for _ in range(L)]
        if b in (0xde, 0xdf):
            n = {0xde:2,0xdf:4}[b]; L = int.from_bytes(data[pos:pos+n],'big'); pos += n
            return {rd(): rd() for _ in range(L)}
        if b in (0xcc,0xcd,0xce,0xcf):
            n = {0xcc:1,0xcd:2,0xce:4,0xcf:8}[b]; v = int.from_bytes(data[pos:pos+n],'big'); pos += n; return v
        if b >= 0xe0: return b - 256
        raise ValueError(hex(b))
    return rd()

def freqs(path):
    """word -> frequency (proportion of all words)."""
    lst = load(path)
    out = {}
    for i, bucket in enumerate(lst[1:]):
        f = 10 ** (-i / 100)
        for w in bucket:
            out.setdefault(w, f)
    return out
