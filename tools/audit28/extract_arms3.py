#!/usr/bin/env python3
"""v0.1.28 arm extraction, step 3: generous geometric cuts.
Keep everything right of the arm's inner ink line (the sleeve
stays whole), plus canopy/fan and the color-discriminated shaft.
Forearm regions drawn shirt-blue are recolored to skin."""
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

GENS = {
    "parasol": "/home/hatch/workspace/imagine_media/media-generation-tenki-v28-parasol-side-0-aedd76e2-f0b5-41cd-bb5a-297e6646cf1f.webp",
    "uchiwa": "/home/hatch/workspace/imagine_media/media-generation-tenki-v28-uchiwa-side-0-00cfa713-afb5-433c-8f8b-ceb40b01da21.webp",
}


def build(name, keep_fn, recolor_ids):
    rgba, _, _ = key_image(GENS[name])
    a = np.asarray(rgba).astype(float)
    ih, iw = a.shape[:2]
    alpha = a[..., 3] > 128
    d_ink = np.sqrt(((a[..., :3] - INK) ** 2).sum(-1))
    d_hair = np.sqrt(((a[..., :3] - HAIR) ** 2).sum(-1))
    ink = (d_ink < 85) & alpha
    lab, _ = ndimage.label(alpha & ~ink)
    yy, xx = np.mgrid[0:ih, 0:iw]
    keep = keep_fn(a, lab, ink, d_ink, d_hair, xx, yy, iw, ih)
    out = a.copy()
    rec = np.isin(lab, recolor_ids)
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
    dst = os.path.join(KEYED, f"arm_polo_{name}_v28.png")
    im.save(dst)
    print(name, "->", dst, im.size, "gen bbox:", bbox)
    bg = Image.new("RGBA", im.size, (70, 80, 95, 255))
    bg.alpha_composite(im)
    bg.convert("RGB").save(os.path.join(OUT, f"layer_{name}_v28.png"))


def parasol_keep(a, lab, ink, d_ink, d_hair, xx, yy, iw, ih):
    keep = np.isin(lab, [1, 2, 3, 4, 5])  # canopy panels
    keep |= ndimage.binary_dilation(keep, iterations=3) & ink
    # arm zone: right of the inner ink line; below y .64 require
    # x >= .605 so no shorts slice sneaks in
    zone = (xx >= 0.585 * iw) & (yy >= 0.355 * ih) & (yy <= 0.70 * ih)
    zone |= (xx >= 0.605 * iw) & (yy > 0.70 * ih) & (yy <= 0.72 * ih)
    keep |= zone & (a[..., 3] > 128)
    # shaft: ink closer to INK than HAIR, tight band around the
    # shaft's main column (found from the fist-height run)
    shaft_ink = ink & (d_ink < d_hair)
    band = shaft_ink & (yy > 0.30 * ih) & (yy < 0.60 * ih)
    colsum = band.sum(axis=0)
    cx = int(np.argmax(colsum))
    keep |= shaft_ink & (np.abs(xx - cx) <= 7) & (yy >= 0.145 * ih) & \
        (yy <= 0.66 * ih)
    keep |= shaft_ink & (xx >= cx - 7) & (xx <= 0.675 * iw) & \
        (yy > 0.66 * ih) & (yy <= 0.72 * ih)
    print("  shaft cx px:", cx, "=", cx / iw)
    return keep


def uchiwa_keep(a, lab, ink, d_ink, d_hair, xx, yy, iw, ih):
    keep = (lab == 85)  # fan disc
    keep |= ndimage.binary_dilation(keep, iterations=3) & ink
    zone = (xx >= 0.600 * iw) & (yy >= 0.335 * ih) & (yy <= 0.66 * ih)
    keep |= zone & (a[..., 3] > 128)
    return keep


build("parasol", parasol_keep, [51, 52, 54])
build("uchiwa", uchiwa_keep, [90])
