"""
Builds the Atlas's shaded relief layer: app/src/main/assets/atlas/relief.webp.

Input: Natural Earth's 1:50m "Shaded Relief Basic" (SR_50M.tif, 10,800 x 5,400, public domain),
from naturalearthdata.com or github.com/nvkelso/natural-earth-raster (50m_rasters/SR_50M).

It's reprojected from plate carree to Equal Earth (the map's projection, the same formulas as
EqualEarth in Atlas.kt) and turned into a light-and-shadow layer that works over any of the map's
looks: white with alpha where slopes catch the light, black with alpha where they're in shadow,
clear where the land is flat and on the water (the raster's flat grey, 206). Saved as lossless
WebP, 5120 x 2492, about 3 MB.

Usage: python3 build_relief.py SR_50M.tif relief.webp [width]
Needs numpy and Pillow. Reads the raster as data only.
"""
import math
import sys

import numpy as np
from PIL import Image

Image.MAX_IMAGE_PIXELS = None

A1, A2, A3, A4 = 1.340264, -0.081106, 0.000893, 0.003796
M = math.sqrt(3) / 2
HALF_WIDTH = 2.7066297
FLAT = 206.0  # the raster's grey for flat land and water


def main(src, out, width):
    th90 = math.asin(M)
    y_max = th90 * (A1 + A2 * th90 ** 2 + th90 ** 6 * (A3 + A4 * th90 ** 2))
    height = round(width * y_max / HALF_WIDTH)
    # Half the source's resolution is still sharper than the output; it keeps memory sensible.
    a = np.asarray(Image.open(src).reduce(2)).astype(np.float32)
    sh, sw = a.shape
    xs = ((np.arange(width) + 0.5) / width * 2 - 1) * HALF_WIDTH
    ys = ((np.arange(height) + 0.5) / height * 2 - 1) * y_max
    x, y = np.meshgrid(xs, -ys)  # y upward for the formulas
    # Equal Earth's inverse: Newton's method for the parametric latitude, then longitude.
    th = y / A1
    for _ in range(12):
        t2 = th * th
        t6 = t2 ** 3
        th = th - (th * (A1 + A2 * t2 + t6 * (A3 + A4 * t2)) - y) / (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2))
    t2 = th * th
    t6 = t2 ** 3
    lam = x * M * (A1 + 3 * A2 * t2 + t6 * (7 * A3 + 9 * A4 * t2)) / np.cos(th)
    lat = np.arcsin(np.clip(np.sin(th) / M, -1, 1))
    inside = np.abs(lam) <= math.pi
    u = np.clip((lam / math.pi + 1) / 2 * sw - 0.5, 0, sw - 1.001)
    v = np.clip((0.5 - lat / math.pi) * sh - 0.5, 0, sh - 1.001)
    u0 = u.astype(int)
    v0 = v.astype(int)
    fu = u - u0
    fv = v - v0
    val = (a[v0, u0] * (1 - fu) * (1 - fv) + a[v0, u0 + 1] * fu * (1 - fv)
           + a[v0 + 1, u0] * (1 - fu) * fv + a[v0 + 1, u0 + 1] * fu * fv)
    d = val - FLAT
    lit = d > 0
    alpha = np.where(lit, np.clip(d / 40.0, 0, 1) * 0.75, np.clip(-d / 110.0, 0, 1) ** 0.85 * 0.9)
    alpha[~inside] = 0
    rgba = np.zeros((height, width, 4), np.uint8)
    rgba[..., 0:3] = np.where(lit[..., None], 255, 0)
    rgba[..., 3] = (alpha * 255).round().astype(np.uint8)
    Image.fromarray(rgba, 'RGBA').save(out, lossless=True, method=6)
    print(f'{out}: {width} x {height}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 5120)
