#!/usr/bin/env python3
"""v0.1.33: build the cardigan / knit-coat PARASOL arm layers from
their rain-umbrella layers — the v0.1.30/31/32 rule extended to the
two sleeve groups that never got a parasol layer:

  the parasol picture IS the rain layer (same canvas, same arm,
  sleeve, fist and shaft — alpha identical, asserted), with only
  the umbrella's own navy repainted in the parasol family's
  colours by tools/audit32/make_parasol_layer32.py's recolor().

- cardigan: the sleeve cloth is pale, so the whole-picture navy
  repaint is safe — exactly the shipped polo parasol's method.
  Asserted: the sleeve cloth's interior does not change.
- knit coat: the sleeve cloth is itself navy (the rain layer's
  CLOTH_A family), so the repaint is MASKED to the umbrella:
    zone 1  the canopy — every navy pixel at or above the canopy's
            own bottom edge per column (draw_canopy32's
            canopy_bottom, corridor columns interpolated);
    zone 2  the shaft and its hook below the canopy — the left
            navy run per row down the shaft, the fist band, and
            the hook rows at the bottom;
    zone 3  true ink (lum < 60, nearest anchor ink/shaft)
            anywhere — the fist's and arm's outlines soften to
            steel blue like the polo parasol's do.
  Asserted: the sleeve cloth's interior does not change.

Also regenerates the polo parasol from the current rain layer and
checks it is pixel-identical to the shipped file (the method's
reproducibility proof).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "audit32"))
from make_parasol_layer32 import recolor  # noqa: E402
from draw_canopy32 import canopy_bottom  # noqa: E402

RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = ROOT / "tools/audit33"


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


def interior_unchanged(src, out, box, name):
    x0, x1, y0, y1 = box
    diff = np.any(src[y0:y1, x0:x1, :3] != out[y0:y1, x0:x1, :3], axis=2)
    print(f"{name}: sleeve interior {box} changed px = {int(diff.sum())}")
    assert diff.sum() == 0, f"{name}: sleeve cloth repainted!"


# ---- polo reproducibility -------------------------------------------
rain = load("arm_polo_umbrella_open")
navy, _ = navy_mask(rain)
regen = apply_masked(rain, navy)
shipped = load("arm_polo_parasol")
print("polo parasol regeneration identical to shipped:",
      bool(np.array_equal(regen, shipped)))
assert np.array_equal(regen, shipped), "polo regeneration differs!"

# ---- cardigan: whole-picture repaint (polo's method) ----------------
rain = load("arm_cardigan_umbrella_open")
navy, _ = navy_mask(rain)
out = apply_masked(rain, navy)
check(rain, out, "arm_cardigan_parasol")
# Sleeve interior: the pale sleeve hangs at x≈470-560, y≈430-800.
# Its cloth (median (157,199,235)) must not change at all; the only
# repaint allowed inside is the sleeve's ink outline (x≈520) and
# its antialiased fringe softening to steel blue, exactly as the
# polo parasol's outlines did.
sub = rain[480:760, 495:545, :3]
sub_out = out[480:760, 495:545, :3]
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

zone2 = np.zeros_like(navy)
for y in range(431, 771):
    row = navy[y]
    xs = np.where(row)[0]
    if len(xs) == 0:
        continue
    # leftmost run
    x0 = xs[0]
    x1 = x0
    for x in xs[1:]:
        if x <= x1 + 1:
            x1 = x
        else:
            break
    if x1 - x0 <= 34:
        zone2[y, x0:x1 + 5] = navy[y, x0:x1 + 5]
zone2[771:891, :] = navy[771:891, :] & (xx[771:891, :] < 505)
zone2[891:, :] = navy[891:, :]

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
# The navy sleeve hangs at x≈516-585, y≈410-850; its cloth interior
# (away from outlines and the zones above) must not change.
interior_unchanged(rain, out, (528, 572, 470, 760), "arm_knitcoat_parasol")
z1 = int((changed & zone1).sum()); z2 = int((changed & zone2).sum()); z3 = int((changed & zone3).sum())
print(f"knitcoat repainted by zone: canopy {z1}, shaft/hook {z2}, ink {z3}")
save(out, "arm_knitcoat_parasol")
print("saved arm_knitcoat_parasol.png", W, "x", H)
