#!/usr/bin/env python3
"""v0.1.34: rebuild the three PARASOL arm layers from the NEW
(v0.1.34) rain layers — the v0.1.33 rule unchanged: the parasol
picture IS its rain layer (same canvas, arm, sleeve, fist, shaft;
alpha identical, asserted) with only the navy repainted in the
parasol family's colours (audit32's recolor()).

Geometry notes vs the v0.1.33 script: the layers were re-canvassed
(polo 720->880 off 60, cardigan 746->940 off 140, knitcoat
655->880 off 110), so the cardigan sleeve box moved +60 in x from
the v0.1.33 frame, and the knitcoat zones are rebuilt on the new
shaft line (apex (463,55) -> join (607,762)):
  zone 1  the canopy — navy at/above the canopy's own per-column
          bottom (draw_canopy32's canopy_bottom);
  zone 2  the shaft corridor (apex->join, half 13), the fist band
          (rows 750-895, x < join_x + 8) and the hook rows (>= 895);
  zone 3  true ink (lum < 60, nearest anchor ink/shaft) anywhere.
Asserted: alpha identical; only navy pixels change; the knitcoat
sleeve cloth interior and the cardigan sleeve cloth do not change.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "audit32"))
sys.path.insert(0, str(ROOT / "tools" / "audit34"))
from make_parasol_layer32 import recolor  # noqa: E402
from draw_canopy32 import canopy_bottom  # noqa: E402


def corridor(H, W, p0, p1, half):
    """Pixels within `half` of the segment p0->p1 (the v0.1.33
    variant script's helper, copied — importing that module would
    re-run its builds)."""
    yy, xx = np.mgrid[0:H, 0:W]
    x0, y0 = p0
    x1, y1 = p1
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / L2, 0, 1)
    px, py = x0 + t * dx, y0 + t * dy
    return np.hypot(xx - px, yy - py) <= half

RES = ROOT / "app/src/main/res/drawable-nodpi"


def load(name):
    return np.asarray(Image.open(RES / f"{name}.png").convert("RGBA")).astype(int)


def navy_mask(a):
    rgb = a[:, :, :3]
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    return (
        (rgb[:, :, 2] > rgb[:, :, 0] + 25)
        & (rgb[:, :, 2] >= rgb[:, :, 1] + 15)
        & (lum < 150)
        & (a[:, :, 3] > 0)
    ), lum


def apply_masked(src, mask):
    out = src.copy()
    ys, xs = np.where(mask)
    for y, x in zip(ys, xs):
        out[y, x, :3] = recolor(tuple(int(v) for v in src[y, x, :3]))
    return out


def save(arr, name):
    Image.fromarray(arr.astype(np.uint8), "RGBA").save(RES / f"{name}.png")


def check(src, out, name):
    assert np.array_equal(src[:, :, 3], out[:, :, 3]), f"{name}: alpha changed"
    changed = np.any(src[:, :, :3] != out[:, :, :3], axis=2)
    navy, _ = navy_mask(src)
    assert not np.any(changed & ~navy), f"{name}: a non-navy pixel changed"
    print(f"{name}: alpha identical, {int(changed.sum())} px repainted (all navy)")
    return changed


# ---- polo: whole-picture repaint -------------------------------------
rain = load("arm_polo_umbrella_open")
navy, _ = navy_mask(rain)
out = apply_masked(rain, navy)
check(rain, out, "arm_polo_parasol")
save(out, "arm_polo_parasol")
print("saved arm_polo_parasol.png", out.shape[1], "x", out.shape[0])

# ---- cardigan: whole-picture repaint (polo's method) -----------------
rain = load("arm_cardigan_umbrella_open")
navy, _ = navy_mask(rain)
out = apply_masked(rain, navy)
check(rain, out, "arm_cardigan_parasol")
# Sleeve interior in the NEW canvas: the pale sleeve hangs at
# x≈555-605, y≈480-760 (the v0.1.33 box shifted +60 by the wider
# re-canvas). Its cloth (median (157,199,235)) must not change.
sub = rain[480:760, 555:605, :3]
sub_out = out[480:760, 555:605, :3]
cloth = np.all(np.abs(sub - np.array([157, 199, 235])) <= 12, axis=2)
cloth_changed = np.any(sub != sub_out, axis=2) & cloth
print("cardigan sleeve box: cloth px", int(cloth.sum()),
      "cloth changed", int(cloth_changed.sum()))
assert cloth_changed.sum() == 0, "cardigan sleeve cloth repainted!"
save(out, "arm_cardigan_parasol")
print("saved arm_cardigan_parasol.png", out.shape[1], "x", out.shape[0])

# ---- knit coat: masked repaint ---------------------------------------
rain = load("arm_knitcoat_umbrella_open")
H, W, _ = rain.shape
navy, lum = navy_mask(rain)
alpha = rain[:, :, 3] > 0
bot = canopy_bottom(alpha)
yy, xx = np.mgrid[0:H, 0:W]
zone1 = navy & (yy <= bot[xx] + 2)

APEX, JOIN = (463, 55), (607, 762)
zone2 = corridor(H, W, APEX, JOIN, 13) & navy
zone2[750:896, :] |= navy[750:896, :] & (xx[750:896, :] < JOIN[0] + 8)
zone2[895:, :] |= navy[895:, :]

INK = np.array([1, 23, 82])
SHAFT = np.array([0, 10, 81])
CLOTH_A = np.array([32, 44, 97])
CLOTH_B = np.array([36, 49, 103])
rgb = rain[:, :, :3]
d = np.stack([np.abs(rgb - a).sum(axis=2) for a in (INK, CLOTH_A, CLOTH_B, SHAFT)])
nearest = d.argmin(axis=0)
zone3 = navy & (lum < 60) & ((nearest == 0) | (nearest == 3))

mask = zone1 | zone2 | zone3
out = apply_masked(rain, mask)
changed = check(rain, out, "arm_knitcoat_parasol")
# The navy sleeve interior (v0.1.33's box shifted +110 in x) must
# not change.
x0, x1, y0, y1 = 638, 682, 470, 760
diff = np.any(rain[y0:y1, x0:x1, :3] != out[y0:y1, x0:x1, :3], axis=2)
print(f"arm_knitcoat_parasol: sleeve interior changed px = {int(diff.sum())}")
assert diff.sum() == 0, "knitcoat sleeve cloth repainted!"
z1 = int((changed & zone1).sum()); z2 = int((changed & zone2).sum()); z3 = int((changed & zone3).sum())
print(f"knitcoat repainted by zone: canopy {z1}, shaft/hook {z2}, ink {z3}")
save(out, "arm_knitcoat_parasol")
print("saved arm_knitcoat_parasol.png", W, "x", H)
