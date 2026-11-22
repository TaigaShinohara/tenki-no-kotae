#!/usr/bin/env python3
"""v0.1.30: build the parasol arm layer from the APPROVED rain
umbrella arm layer (arm_polo_umbrella_open) by repainting ONLY the
canopy region in the parasol family's colours. Arm, sleeve, shaft
and fist pixels are never touched: the change mask is the canopy
cloth + ribs + outline, bounded per column by the canopy's own
scalloped bottom edge (the shaft corridor's bottom is interpolated
from its neighbours so the shaft's dark core below the scallop
stays out). Colours: ink -> the parasol prop's ink (49,73,138);
cloth -> lerp(parasol shade (153,200,234), parasol light
(186,233,251)) on the source luminance, anchored so the flat cloth
tone (32,44,97, lum 46.5) lands on the light fill."""
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"
src = Image.open(RES + "arm_polo_umbrella_open.png").convert("RGBA")
a = np.asarray(src).astype(int)
H, W, _ = a.shape
alpha = a[:, :, 3]
lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]

# per-column bottom of the top opaque blob (first gap >= 20 ends it)
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

# the shaft corridor: columns whose blob runs far below the local
# (median-51) canopy bottom — replace their bottom by interpolation
med = np.array([np.median(bot[max(0, x - 25):x + 26]) for x in range(W)])
corridor = bot > med + 30
if corridor.any():
    idx = np.arange(W)
    bot = np.where(corridor,
                   np.interp(idx, idx[~corridor], bot[~corridor]),
                   bot).astype(int)
bot = np.minimum(bot + 2, 400)  # +2 AA slack, hard cap above the pole

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
Image.fromarray(out.astype(np.uint8), "RGBA").save(
    "tools/audit30/arm_polo_parasol_new.png")

# --- verification artefacts ---
diff = (out != a).any(axis=2)
assert not (diff & ~mask).any(), "diff outside mask!"
print("changed px:", int(diff.sum()), "mask px:", int(mask.sum()),
      "corridor cols:", int(corridor.sum()))
ov = a.copy()
ov[mask, :3] = (255, 40, 40)
ov[mask, 3] = 255
Image.fromarray(ov.astype(np.uint8), "RGBA").save(
    "tools/audit30/parasol_mask_overlay.png")
dv = np.zeros((H, W, 4), dtype=np.uint8)
dv[diff] = (255, 40, 40, 255)
Image.fromarray(dv, "RGBA").save("tools/audit30/parasol_diff.png")
# side-by-side of the two layers on the sky colour
bg = Image.new("RGBA", (W * 2 + 8, H), (13, 32, 64, 255))
bg.alpha_composite(src, (0, 0))
bg.alpha_composite(Image.open("tools/audit30/arm_polo_parasol_new.png"), (W + 8, 0))
bg.convert("RGB").save("tools/audit30/parasol_layer_compare.png")
print("ok")
