#!/usr/bin/env python3
"""Small-5: raincoat variants of the fixed head layers.
Render the raincoat base alone, flood-fill the hood opening from the
face centre (hood fabric + outline = barrier), erode slightly, map
the opening into each head layer's coordinates via the mirror's own
head_layer_rect, and clip the fixed layers' alpha to it. The result
keeps hair/hat inside the hood instead of spilling over it."""
import os, sys
from collections import deque
import numpy as np
from PIL import Image, ImageFilter

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

LK = os.path.join(TOOLS, "layers_keyed")
OUT = os.path.join(TOOLS, "illust_fixes", "fix_small5")
os.makedirs(OUT, exist_ok=True)
W, H = 760, 900

FIXED = {n: Image.open(os.path.join(LK, n + "_fixed.png")).convert("RGBA")
         for n in ("layer_head_cap", "layer_head_knitcap")}
M.IMG.update(FIXED)

spec = M.spec_for(base="RAINCOAT", top=None)
base_img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
base_img.save(os.path.join(OUT, "raincoat_base.png"))

# body rect (same math as verify_fix1)
ph = H / (1.0 + M.pad_top_ratio(spec))
pt = H - ph
box = M.BOX[M.BODY_ART["RAINCOAT"]]
scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
bw = (box[2] - box[0]) * M.CANVAS_W * scale
bl = (W - bw) / 2
rig = M.RIGS["RAINCOAT"]
face_cx, eye_y = rig[0], M.FEATURES["RAINCOAT"][0]

a = np.asarray(base_img).astype(int)
# hood fabric colour: sample the hood at face height, far left/right
sy = int(pt + eye_y * ph)
row = a[sy]
samples = []
for sx in (int(bl + 0.06 * bw), int(bl + 0.94 * bw),
           int(bl + 0.10 * bw), int(bl + 0.90 * bw)):
    samples.append(row[sx])
fabric = np.median(samples, axis=0)
print("hood fabric sample:", fabric, "at y=", sy)
dist = np.abs(a - fabric).sum(-1)
lum = a.sum(-1)
barrier = (dist < 90) | (lum < 200)   # fabric or dark outline
# restrict the fill to the head neighbourhood
x0, x1 = int(bl - 0.05 * bw), int(bl + 1.05 * bw)
y0, y1 = int(pt - 0.05 * ph), int(pt + 0.42 * ph)
seed = (int(bl + face_cx * bw), int(pt + eye_y * ph))
mask = np.zeros((H, W), bool)
dq = deque([seed])
mask[seed[1], seed[0]] = True
while dq:
    x, y = dq.popleft()
    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
        if x0 <= nx < x1 and y0 <= ny < y1 and not mask[ny, nx] \
                and not barrier[ny, nx]:
            mask[ny, nx] = True
            dq.append((nx, ny))
ys, xs = np.nonzero(mask)
print(f"hood opening: {int(mask.sum())} px, canvas x[{xs.min()},{xs.max()}] "
      f"y[{ys.min()},{ys.max()}]")
mask_im = Image.fromarray((mask * 255).astype(np.uint8))
mask_im = mask_im.filter(ImageFilter.MinFilter(7))      # ~3px inset
mask_im = mask_im.filter(ImageFilter.GaussianBlur(1.2))  # feather
mask_im.save(os.path.join(OUT, "hood_opening_mask.png"))

for head, art in (("CAP", "layer_head_cap"), ("KNITCAP", "layer_head_knitcap")):
    layer = FIXED[art].copy()
    iw, ih = layer.size
    l, t, w, h = M.head_layer_rect(head, "RAINCOAT", bl, pt, bw, ph)
    # sample the canvas mask at each layer pixel's canvas position
    fx = (np.arange(iw) + 0.5) / iw
    fy = (np.arange(ih) + 0.5) / ih
    cx = np.clip((l + fx * w).astype(int), 0, W - 1)
    cy = np.clip((t + fy * h).astype(int), 0, H - 1)
    mvals = np.asarray(mask_im)[np.ix_(cy, cx)].astype(float) / 255.0
    arr = np.asarray(layer).astype(float)
    arr[..., 3] *= mvals
    out = Image.fromarray(arr.astype(np.uint8))
    dst = os.path.join(LK, art + "_rain_fixed.png")
    out.save(dst, optimize=True)
    print("saved", dst, out.size)
print("done")
