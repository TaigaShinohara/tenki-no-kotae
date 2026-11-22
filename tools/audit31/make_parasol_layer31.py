#!/usr/bin/env python3
"""v0.1.31: rebuild the parasol arm layer from the APPROVED rain
umbrella arm layer (arm_polo_umbrella_open), changing ONLY the
canopy's colours — never its pixels' positions.

The rule this script exists to keep: the parasol canopy's
silhouette, ribs and shaft position are the rain umbrella's,
pixel for pixel. Only the colour values inside the canopy mask
change (ink -> parasol ink (49,73,138); cloth -> the parasol
family's light blues mapped on the source luminance). Alpha is
never written. The script asserts the result's alpha equals the
source's alpha exactly, and emits the silhouette overlay that
proves the outlines coincide.
"""
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"
src = Image.open(RES + "arm_polo_umbrella_open.png").convert("RGBA")
a = np.asarray(src).astype(int)
H, W, _ = a.shape
alpha = a[:, :, 3]
lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]

# per-column bottom of the canopy blob (first gap >= 20 ends it);
# the shaft corridor's bottom is interpolated from its neighbours
bot = np.zeros(W, dtype=int)
for x in range(W):
    col = alpha[:, x] > 0
    last, gap, started = 0, 0, False
    for y in range(H):
        if col[y]:
            last, gap, started = y, 0, True
        elif started:
            gap += 1
            if gap >= 20:
                break
    bot[x] = last
med = np.array([np.median(bot[max(0, x - 25):x + 26]) for x in range(W)])
corridor = bot > med + 30
if corridor.any():
    idx = np.arange(W)
    bot = np.where(corridor,
                   np.interp(idx, idx[~corridor], bot[~corridor]),
                   bot).astype(int)
bot = np.minimum(bot + 2, 400)

yy = np.arange(H)[:, None]
dark = (alpha > 0) & (lum < 115) & (a[:, :, 2] > a[:, :, 0] + 15)
mask = dark & (yy <= bot[None, :])

INK = np.array([49, 73, 138])
SHADE = np.array([153, 200, 234])
LIGHT = np.array([186, 233, 251])
out = a.copy()
ink_m = mask & (lum <= 40)
t = np.clip((lum - 40) / 6.5, 0, 1)[..., None]
cloth = SHADE[None, None, :] * (1 - t) + LIGHT[None, None, :] * t
cloth_m = mask & (lum > 40)
out[cloth_m, :3] = cloth[cloth_m].astype(int)
out[ink_m, :3] = INK

# --- the proof obligations ---
assert (out[:, :, 3] == alpha).all(), "alpha changed!"
diff = (out != a).any(axis=2)
assert not (diff & ~mask).any(), "diff outside the canopy mask!"
print("alpha identical to rain umbrella: True; changed px:",
      int(diff.sum()), "(canopy mask only)")

Image.fromarray(out.astype(np.uint8), "RGBA").save(
    RES + "arm_polo_parasol.png")
print("saved arm_polo_parasol.png")

# --- silhouette overlay: rain silhouette in red, parasol in
# blue, over white — coincident outlines show as a single
# purple-edged shape with no red/blue fringe sticking out ---
ov = np.full((H, W, 3), 255, dtype=np.uint8)
ov[alpha > 0] = (230, 40, 40)
par_alpha = np.asarray(
    Image.open(RES + "arm_polo_parasol.png").convert("RGBA"))[:, :, 3]
both = (alpha > 0) & (par_alpha > 0)
ov[both] = (150, 60, 180)
only_par = (par_alpha > 0) & ~(alpha > 0)
ov[only_par] = (40, 80, 255)
print("parasol-only alpha px:", int(only_par.sum()),
      "| rain-only alpha px:",
      int(((alpha > 0) & ~(par_alpha > 0)).sum()))
Image.fromarray(ov, "RGB").save(
    "tools/audit31/parasol_silhouette_overlay.png")
print("saved parasol_silhouette_overlay.png")
