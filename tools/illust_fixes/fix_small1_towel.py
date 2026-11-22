#!/usr/bin/env python3
"""Small-1: reshape the neck towel's bottom edge.
Alpha-only reshape of prop_towel: each hanging panel's bottom
corners are lifted along a smooth quarter-arc (different amounts
per panel for a natural unevenness), the middle keeps its length,
and a navy hem line is redrawn along the new edge so the panels
stop reading as one straight bib-like bar."""
import os, sys
import numpy as np
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

LK = os.path.join(TOOLS, "layers_keyed")
OUT = os.path.join(TOOLS, "illust_fixes", "fix_small1")
os.makedirs(OUT, exist_ok=True)

im = M.img("prop_towel").convert("RGBA")
arr = np.asarray(im).astype(float)
H, W = arr.shape[:2]
alpha = arr[..., 3]

bot = np.full(W, -1)
for x in range(W):
    ys = np.nonzero(alpha[:, x] > 128)[0]
    if len(ys):
        bot[x] = ys.max()
panel_cols = bot > 0.9 * H
# contiguous panel ranges
ranges = []
x = 0
while x < W:
    if panel_cols[x]:
        x0 = x
        while x < W and panel_cols[x]:
            x += 1
        ranges.append((x0, x - 1))
    else:
        x += 1
print("panels:", ranges)

# outline colour: median of the towel's navy outline pixels
_raw = np.asarray(im).astype(int)
_m = (_raw[..., 3] > 200) & (_raw[..., 2] > 55) & \
     ((_raw[..., 2] - _raw[..., 0]) > 28) & (_raw[..., :3].sum(-1) < 330)
OUTLINE = tuple(int(v) for v in np.median(_raw[..., :3][_m], axis=0))
print("outline colour:", OUTLINE)

new_alpha = alpha.copy()
rgb = arr[..., :3].copy()
LIFTS = [0.052, 0.066]      # max corner lift (frac of H) per panel
SHIFTS = [0.0, 0.012]       # whole-panel lift for unevenness
for pi, (x0, x1) in enumerate(ranges):
    pw = x1 - x0
    lmax = LIFTS[pi % len(LIFTS)] * H
    shift = SHIFTS[pi % len(SHIFTS)] * H
    for x in range(x0, x1 + 1):
        u = (x - x0) / pw
        d = min(u, 1 - u)
        lift = shift
        if d < 0.24:
            lift += lmax * (1 - np.sin(d / 0.24 * np.pi / 2)) ** 1.15
        yb = (H - 1) - lift
        col = new_alpha[:, x]
        yy = np.arange(H)
        fade = np.clip((yb - yy) / 2.0 + 0.5, 0, 1)
        new_alpha[:, x] = col * fade
        # redraw the hem outline along the new edge
        for k in range(0, 6):
            yy2 = int(round(yb)) - k
            if 0 <= yy2 < H and alpha[yy2, x] > 40:
                rgb[yy2, x] = OUTLINE
                new_alpha[yy2, x] = 255

out = np.dstack([rgb, new_alpha]).astype(np.uint8)
res = Image.fromarray(out)
dst = os.path.join(LK, "prop_towel_fixed.png")
res.save(dst, optimize=True)
print("saved", dst, res.size)
bg = Image.new("RGBA", res.size, (16, 28, 52, 255))
bg.alpha_composite(res)
bg.convert("RGB").save(os.path.join(OUT, "towel_fixed_on_dark.png"))
