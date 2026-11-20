#!/usr/bin/env python3
"""
Builds the Atlas's assets (app/src/main/assets/atlas) from Natural Earth, which is public domain.

Inputs (downloaded by hand, read here as data only):
  ne_50m_admin_0_countries.geojson, ne_10m_admin_1_states_provinces.geojson,
  ne_50m_populated_places.geojson    (github.com/nvkelso/natural-earth-vector, geojson/)
  countries-50m.json                 (npm world-atlas 2.x: the same 50m countries as TopoJSON,
                                      used only to find which countries share a border)
  flag-icons/flags/4x3/*.svg         (npm flag-icons, MIT; drawn to PNG with rsvg-convert)

Outputs:
  countries.json   one entry per country or territory: names, capital, region, Natural Earth's
                   population estimate and its year, neighbours, the box to frame it in
  countries.bin    their outlines, in the same order
  subdivisions.json, subdivisions.bin   first-level subdivisions (provinces, states…), by country
  places.json      Natural Earth's 50m populated places: capitals and major cities
  flags/xx.png     96×72 flags

Outlines are simplified (Douglas–Peucker) and stored as rings of points at 0.01° steps, each a
zigzag varint delta from the one before (see AtlasShapes.kt). Borders are de facto: who holds
the ground, as Natural Earth draws them.

Usage: build_atlas.py <dir with the four data files> <flag-icons dir> <assets/atlas out dir>
"""
import json, math, os, subprocess, sys

src, flags_dir, out = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(os.path.join(out, "flags"), exist_ok=True)
LANGS = ["zh", "zht", "ja", "ko", "fr", "de", "es", "it"]
TONE = set("āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ")

def load(name):
    with open(os.path.join(src, name), encoding="utf-8") as f:
        return json.load(f)

def dp(points, tol):
    if len(points) < 4:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = points[a]; bx, by = points[b]
        dx, dy = bx - ax, by - ay
        length = math.hypot(dx, dy) or 1e-12
        best, at = -1.0, -1
        for i in range(a + 1, b):
            px, py = points[i]
            d = abs(dy * px - dx * py + bx * ay - by * ax) / length
            if d > best:
                best, at = d, i
        if best > tol:
            keep[at] = True
            stack += [(a, at), (at, b)]
    return [p for p, k in zip(points, keep) if k]

def simplify_ring(r, tol):
    if len(r) > 1 and r[0] == r[-1]:
        r = r[:-1]
    if len(r) < 4:
        return r
    x0, y0 = r[0]
    far = max(range(len(r)), key=lambda i: (r[i][0] - x0) ** 2 + (r[i][1] - y0) ** 2)
    a = dp(r[:far + 1], tol)
    b = dp(r[far:] + [r[0]], tol)
    return a[:-1] + b[:-1]

def area(r):
    return abs(sum(x1 * y2 - x2 * y1 for (x1, y1), (x2, y2) in zip(r, r[1:] + r[:1]))) / 2

def rings_of(geom):
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    return [r for poly in polys for r in poly]

def shape(geom, tol, min_area):
    rings = [simplify_ring([(float(x), float(y)) for x, y in r], tol) for r in rings_of(geom)]
    rings = [r for r in rings if len(r) >= 3]
    if not rings:
        # Too small to survive simplifying (a tiny island state): keep its biggest ring as it is.
        raw = max(([(float(x), float(y)) for x, y in r] for r in rings_of(geom)), key=area)
        return [raw[:-1] if raw[0] == raw[-1] else raw]
    biggest = max(area(r) for r in rings)
    return [r for r in rings if area(r) >= min_area or area(r) == biggest]

def varint(n, buf):
    n = (n << 1) ^ (n >> 63)  # zigzag
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            buf.append(b | 0x80)
        else:
            buf.append(b)
            return

def encode(shapes):
    buf = bytearray(b"ATL1")
    varint(len(shapes), buf)
    for rings in shapes:
        varint(len(rings), buf)
        for r in rings:
            varint(len(r), buf)
            px = py = 0
            for x, y in r:
                qx, qy = round(x * 100), round(y * 100)
                varint(qx - px, buf); varint(qy - py, buf)
                px, py = qx, qy
    return bytes(buf)

def view_box(rings):
    """The box to frame a country in: its biggest piece and the pieces near it in size and place."""
    big = max(rings, key=area)
    cx = sum(x for x, _ in big) / len(big)
    keep = [r for r in rings if area(r) >= area(big) * 0.2 and abs(sum(x for x, _ in r) / len(r) - cx) < 60]
    xs = [x for r in keep for x, _ in r]; ys = [y for r in keep for _, y in r]
    return [round(min(xs), 2), round(min(ys), 2), round(max(xs), 2), round(max(ys), 2)]

def names(p, upper):
    out = {}
    for l in LANGS:
        v = p.get(("NAME_" + l.upper()) if upper else ("name_" + l))
        if v:
            out[l] = v
    return out

flag_codes = {f[:-4] for f in os.listdir(flags_dir) if f.endswith(".svg")}

# --- neighbours, from the TopoJSON's shared arcs ---
topo = load("countries-50m.json")
geoms = topo["objects"]["countries"]["geometries"]
def polys(g):
    return [g["arcs"]] if g["type"] == "Polygon" else g["arcs"] if g["type"] == "MultiPolygon" else []
owners = {}
for gi, g in enumerate(geoms):
    for poly in polys(g):
        for r in poly:
            for i in r:
                owners.setdefault(i if i >= 0 else ~i, set()).add(gi)
topo_adj = {gi: set() for gi in range(len(geoms))}
for o in owners.values():
    for a in o:
        topo_adj[a] |= o - {a}
ALIAS = {"Macedonia": "North Macedonia"}
topo_name = {gi: ALIAS.get(g["properties"]["name"], g["properties"]["name"]) for gi, g in enumerate(geoms)}

# --- countries ---
countries = load("ne_50m_admin_0_countries.geojson")["features"]
countries.sort(key=lambda f: f["properties"]["NAME"])
by_name = {f["properties"]["NAME"]: f["properties"]["ADM0_A3"] for f in countries}
neigh = {}
for gi, ns in topo_adj.items():
    a3 = by_name.get(topo_name[gi])
    if a3:
        neigh[a3] = sorted({by_name[topo_name[n]] for n in ns if topo_name[n] in by_name})

places = load("ne_50m_populated_places.geojson")["features"]
caps = {}
place_out = []
for f in places:
    p = f["properties"]
    kind = p["FEATURECLA"]
    entry = {
        "name": " ".join(p["NAME"].split()), "n": names(p, True), "a3": p["ADM0_A3"],
        "lon": round(p["LONGITUDE"], 3), "lat": round(p["LATITUDE"], 3),
        "rank": p["SCALERANK"], "tz": p.get("TIMEZONE") or "",
        "kind": "capital" if kind == "Admin-0 capital" else "capital-alt" if kind == "Admin-0 capital alt" else
                "region-capital" if "Admin-1 capital" in kind else "city",
    }
    place_out.append(entry)
    if entry["kind"] == "capital" and p["ADM0_A3"] not in caps:
        caps[p["ADM0_A3"]] = entry

# Where a territory has no capital (Hong Kong, Greenland…), its biggest place stands in, marked as such.
main_city = {}
for e in sorted(place_out, key=lambda e: e["rank"]):
    main_city.setdefault(e["a3"], e)
# A country's time zone: its capital's, else the one most of its places use.
zones = {}
for e in place_out:
    if e["tz"]:
        zones.setdefault(e["a3"], []).append(e["tz"])

country_out, country_shapes = [], []
for f in countries:
    p = f["properties"]
    rings = shape(f["geometry"], 0.03, 0.004)
    a2 = p["ISO_A2_EH"] if p["ISO_A2_EH"] != "-99" else ""
    cap = caps.get(p["ADM0_A3"])
    seat = "capital" if cap else "main city"
    cap = cap or main_city.get(p["ADM0_A3"])
    tz = (cap or {}).get("tz") or max(set(zones.get(p["ADM0_A3"], [""])), key=zones.get(p["ADM0_A3"], [""]).count)
    country_out.append({
        "a3": p["ADM0_A3"], "a2": a2, "name": p["NAME"], "long": p["NAME_LONG"], "formal": p.get("FORMAL_EN") or "",
        "n": names(p, True), "type": p["TYPE"], "sov": p["SOVEREIGNT"],
        "continent": p["CONTINENT"], "region": p["REGION_UN"], "subregion": p["SUBREGION"],
        "pop": int(round(p["POP_EST"] or 0)), "popYear": int(p["POP_YEAR"] or 0), "income": (p.get("INCOME_GRP") or "")[3:],
        "label": [round(p["LABEL_X"], 2), round(p["LABEL_Y"], 2)], "labelRank": int(p["LABELRANK"]), "color": int(p["MAPCOLOR7"]),
        "flag": a2.lower() if a2.lower() in flag_codes else "",
        "capital": {"name": cap["name"], "n": cap["n"], "lon": cap["lon"], "lat": cap["lat"], "seat": seat} if cap else None,
        "tz": tz,
        "neighbours": neigh.get(p["ADM0_A3"], []),
        "box": view_box(rings),
        "wikidata": p.get("WIKIDATAID") or "",
    })
    country_shapes.append(rings)

# --- subdivisions ---
adm = load("ne_10m_admin_1_states_provinces.geojson")["features"]
known = {c["a3"] for c in country_out}
subs, sub_shapes = [], []
for f in sorted(adm, key=lambda f: (f["properties"]["adm0_a3"], f["properties"]["name"] or "")):
    p = f["properties"]
    if p["adm0_a3"] not in known or not p.get("name") or f["geometry"] is None:
        continue
    alt = p.get("name_alt") or ""
    subs.append({
        "a3": p["adm0_a3"], "name": p["name"], "en": p.get("name_en") or p["name"], "n": names(p, False),
        "type": p.get("type_en") or p.get("type") or "", "iso": p.get("iso_3166_2") or "",
        "reading": alt if any(c in TONE for c in alt) else "",
        "label": [round(p["longitude"], 2), round(p["latitude"], 2)] if p.get("longitude") is not None else None,
    })
    sub_shapes.append(shape(f["geometry"], 0.025, 0.002))

with open(os.path.join(out, "countries.json"), "w", encoding="utf-8") as fh:
    json.dump({"format": "slothatlas-countries", "v": 1, "source": "Natural Earth 5.1 (public domain)", "countries": country_out}, fh, ensure_ascii=False, separators=(",", ":"))
with open(os.path.join(out, "countries.bin"), "wb") as fh:
    fh.write(encode(country_shapes))
with open(os.path.join(out, "subdivisions.json"), "w", encoding="utf-8") as fh:
    json.dump({"format": "slothatlas-subdivisions", "v": 1, "subdivisions": subs}, fh, ensure_ascii=False, separators=(",", ":"))
with open(os.path.join(out, "subdivisions.bin"), "wb") as fh:
    fh.write(encode(sub_shapes))
with open(os.path.join(out, "places.json"), "w", encoding="utf-8") as fh:
    json.dump({"format": "slothatlas-places", "v": 1, "places": place_out}, fh, ensure_ascii=False, separators=(",", ":"))

drawn = 0
for c in country_out:
    if c["flag"]:
        target = os.path.join(out, "flags", c["flag"] + ".png")
        subprocess.run(["rsvg-convert", "-w", "96", "-h", "72", "-o", target, os.path.join(flags_dir, c["flag"] + ".svg")], check=True)
        drawn += 1
print(len(country_out), "countries,", len(subs), "subdivisions,", len(place_out), "places,", drawn, "flags")
