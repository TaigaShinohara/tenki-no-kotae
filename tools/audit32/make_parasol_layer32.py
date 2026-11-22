#!/usr/bin/env python3
"""v0.1.32: regenerate the polo parasol layer from the NEW polo
rain layer (v0.1.31 procedure): recolor ONLY the canopy cloth /
ink / shaft pixels toward the pale parasol palette; skin, sleeve
and outline pixels stay bit-identical, alpha untouched — the
silhouette stays pixel-identical to the rain layer."""
import os

import numpy as np
from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
RES = os.path.join(BASE, "app/src/main/res/drawable-nodpi")
OUT = os.path.join(BASE, "tools/audit32")

INK = np.array([1, 23, 82])
CLOTH_A = np.array([32, 44, 97])
CLOTH_B = np.array([36, 49, 103])
SHAFT = np.array([0, 10, 81])


def recolor(rgb):
    r, g, b = (int(rgb[0]), int(rgb[1]), int(rgb[2]))
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    navy = b > r + 25 and b >= g + 15 and lum < 150
    if not navy:
        return None
    d_ink = np.abs(np.array([r, g, b]) - INK).sum()
    d_a = np.abs(np.array([r, g, b]) - CLOTH_A).sum()
    d_b = np.abs(np.array([r, g, b]) - CLOTH_B).sum()
    d_s = np.abs(np.array([r, g, b]) - SHAFT).sum()
    m = min(d_ink, d_a, d_b, d_s)
    if m == d_ink or m == d_s:
        t = np.array([70, 95, 150])    # lines / shaft -> soft steel blue
    elif m == d_a:
        t = np.array([225, 233, 245])  # cloth A -> near-white
    else:
        t = np.array([198, 214, 235])  # cloth B -> pale blue
    # keep the pixel's own shading offset from its anchor
    anchor = INK if m == d_ink else SHAFT if m == d_s else \
        CLOTH_A if m == d_a else CLOTH_B
    off = np.array([r, g, b]) - anchor
    return tuple(int(min(255, max(0, v))) for v in (t + off))


def main():
    src = Image.open(os.path.join(RES, "arm_polo_umbrella_open.png")) \
        .convert("RGBA")
    a = np.asarray(src).copy()
    rgb = a[:, :, :3].astype(int)
    alpha = a[:, :, 3]
    out = a.copy()
    ys, xs = np.where(alpha > 0)
    changed = np.zeros(alpha.shape, dtype=bool)
    for y, x in zip(ys, xs):
        nc = recolor(rgb[y, x])
        if nc is not None and nc != tuple(rgb[y, x]):
            out[y, x, :3] = nc
            changed[y, x] = True
    assert (out[:, :, 3] == alpha).all(), "alpha changed!"
    # every changed pixel must sit inside the old canopy / shaft
    # mask (dark navy in the source)
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    navy_src = (rgb[:, :, 2] > rgb[:, :, 0] + 25) & \
        (rgb[:, :, 2] >= rgb[:, :, 1] + 15) & (lum < 150) & (alpha > 0)
    assert (changed <= navy_src).all(), "recolor leaked outside navy!"
    # skin / sleeve untouched: pixels outside the navy mask identical
    same = ~changed
    assert (out[same] == a[same]).all()
    Image.fromarray(out, "RGBA").save(
        os.path.join(RES, "arm_polo_parasol.png"))
    print("parasol saved; recolored px:", int(changed.sum()),
          "| silhouette identical:",
          bool((out[:, :, 3] == alpha).all()))


if __name__ == "__main__":
    main()
