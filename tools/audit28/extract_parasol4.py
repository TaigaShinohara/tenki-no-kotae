#!/usr/bin/env python3
"""v0.1.28 parasol extraction, step 4: seam-following cut.
The sleeve is continuous with the shirt fill (no ink barrier),
so the arm is cut along the armhole seam ink line: per row, the
seam x is the ink run nearest 0.575 in the band 0.53..0.63,
smoothed; everything right of it (rows 0.36..0.70) is the arm."""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
from key_assets import key_image  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
KEYED = os.path.expanduser("~/workspace/tenki-no-kotae/tools/sleeve_arms/keyed")
INK = np.array([21, 30, 101], float)
HAIR = np.array([52, 75, 134], float)
SHIRT = np.array([158, 200, 234], float)
SKIN = np.array([192, 234, 252], float)
GEN = "/home/hatch/workspace/imagine_media/media-generation-tenki-v28-parasol-side-0-aedd76e2-f0b5-41cd-bb5a-297e6646cf1f.webp"

rgba, _, _ = key_image(GEN)
a = np.asarray(rgba).astype(float)
ih, iw = a.shape[:2]
alpha = a[..., 3] > 128
d_ink = np.sqrt(((a[..., :3] - INK) ** 2).sum(-1))
d_hair = np.sqrt(((a[..., :3] - HAIR) ** 2).sum(-1))
ink = (d_ink < 85) & alpha
lab, _ = ndimage.label(alpha & ~ink)
yy, xx = np.mgrid[0:ih, 0:iw]

keep = np.isin(lab, [1, 2, 3, 4, 5])
keep |= ndimage.binary_dilation(keep, iterations=3) & ink

# seam x per row
y0, y1 = int(0.36 * ih), int(0.70 * ih)
seam = np.full(ih, np.nan)
for y in range(y0, y1):
    xs = np.where(ink[y, int(0.53 * iw):int(0.63 * iw)])[0]
    if len(xs):
        # runs of ink; choose the run whose center is nearest 0.575*iw
        xs = xs + int(0.53 * iw)
        splits = np.where(np.diff(xs) > 3)[0]
        runs = np.split(xs, splits + 1)
        centers = [r.mean() for r in runs if len(r) >= 2]
        if centers:
            seam[y] = min(centers, key=lambda c: abs(c - 0.575 * iw))
# fill gaps by interpolation, then median-smooth
idx = np.arange(ih)
ok = ~np.isnan(seam)
seam = np.interp(idx, idx[ok], seam[ok])
seam = ndimage.median_filter(seam, size=25, mode="nearest")
arm = (yy >= y0) & (yy <= y1) & (xx >= (seam - 2)[yy]) & alpha
keep |= arm
# fist/hook zone below the arm rows
keep |= (xx >= 0.60 * iw) & (yy > y1) & (yy <= 0.72 * ih) & alpha
# shaft
shaft_ink = ink & (d_ink < d_hair)
band = shaft_ink & (yy > 0.30 * ih) & (yy < 0.60 * ih)
cx = int(np.argmax(band.sum(axis=0)))
keep |= shaft_ink & (np.abs(xx - cx) <= 7) & (yy >= 0.145 * ih) & \
    (yy <= 0.66 * ih)
keep |= shaft_ink & (xx >= cx - 7) & (xx <= 0.675 * iw) & \
    (yy > 0.66 * ih) & (yy <= 0.72 * ih)

out = a.copy()
rec = np.isin(lab, [51, 52, 54])
out[..., :3] = np.where(rec[..., None],
                        np.clip(out[..., :3] - SHIRT + SKIN, 0, 255),
                        out[..., :3])
out[..., 3] = np.where(keep, a[..., 3], 0)
im = Image.fromarray(out.astype(np.uint8), "RGBA")
bbox = im.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
im = im.crop(bbox)
le = max(im.size)
if le > 1024:
    s = 1024 / le
    im = im.resize((round(im.size[0] * s), round(im.size[1] * s)),
                   Image.LANCZOS)
dst = os.path.join(KEYED, "arm_polo_parasol_v28.png")
im.save(dst)
print("parasol ->", dst, im.size, "gen bbox:", bbox, "shaft cx:", cx / iw)
bg = Image.new("RGBA", im.size, (70, 80, 95, 255))
bg.alpha_composite(im)
bg.convert("RGB").save(os.path.join(OUT, "layer_parasol_v28.png"))
