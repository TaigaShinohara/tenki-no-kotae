#!/usr/bin/env python3
"""v0.1.28 arm extraction, step 2: build the two arm layers from
the region maps. Forearm regions were drawn shirt-blue by the
generator (long-sleeve look) -> recolored to the skin tone with a
luminance-preserving shift. Ink outlines come from dilating the
kept fills and intersecting with the ink mask, plus explicit
shaft/handle ink strips."""
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


def regions_of(a):
    ih, iw = a.shape[:2]
    alpha = a[..., 3] > 128
    dink = np.sqrt(((a[..., :3] - INK) ** 2).sum(-1))
    ink = (dink < 85) & alpha
    lab, _ = ndimage.label(alpha & ~ink)
    return lab, ink, alpha


def build(name, keep_ids, extra_masks, recolor_ids, shaft_boxes):
    rgba, _, _ = key_image(GENS[name])
    a = np.asarray(rgba).astype(float)
    ih, iw = a.shape[:2]
    lab, ink, alpha = regions_of(a)
    keep = np.isin(lab, keep_ids)
    for m in extra_masks(a, lab, iw, ih):
        keep |= m
    # ink: dilation of keep (3px) intersected with ink mask
    dil = ndimage.binary_dilation(keep, iterations=3)
    keep |= dil & ink
    yy, xx = np.mgrid[0:ih, 0:iw]
    d_ink = np.sqrt(((a[..., :3] - INK) ** 2).sum(-1))
    d_hair = np.sqrt(((a[..., :3] - HAIR) ** 2).sum(-1))
    shaft_ink = ink & (d_ink < d_hair)
    for (x0, y0, x1, y1) in shaft_boxes:
        keep |= shaft_ink & (xx >= x0 * iw) & (xx < x1 * iw) & \
            (yy >= y0 * ih) & (yy < y1 * ih)
    out = a.copy()
    rec = np.isin(lab, recolor_ids)
    out[..., :3] = np.where(rec[..., None],
                            np.clip(out[..., :3] - SHIRT + SKIN, 0, 255),
                            out[..., :3])
    out[..., 3] = np.where(keep, a[..., 3], 0)
    im = Image.fromarray(out.astype(np.uint8), "RGBA")
    bbox = im.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    im = im.crop(bbox)
    long_edge = max(im.size)
    if long_edge > 1024:
        s = 1024 / long_edge
        im = im.resize((round(im.size[0] * s), round(im.size[1] * s)),
                       Image.LANCZOS)
    dst = os.path.join(KEYED, f"arm_polo_{name}_v28.png")
    im.save(dst)
    print(name, "->", dst, im.size, "bbox in gen:", bbox)
    return im


def parasol_extra(a, lab, iw, ih):
    # sleeve: part of the torso region R43 right of the arm's inner
    # ink line (x >= 0.592) and above the cuff (y <= 0.520)
    yy, xx = np.mgrid[0:ih, 0:iw]
    return [((lab == 43) & (xx >= 0.592 * iw) & (yy <= 0.520 * ih))]


def uchiwa_extra(a, lab, iw, ih):
    # sleeve: part of the shirt region R81 right of x >= 0.598,
    # above the cuff (y <= 0.478)
    yy, xx = np.mgrid[0:ih, 0:iw]
    return [((lab == 81) & (xx >= 0.598 * iw) & (yy <= 0.478 * ih))]


# parasol: canopy R1-5, shoulder strip R45, forearm R51+R52,
# fist R57; shaft ink strip x 0.608..0.640 from canopy to hook end
build("parasol", [1, 2, 3, 4, 5, 45, 51, 52, 57], parasol_extra,
      [51, 52], [(0.610, 0.145, 0.645, 0.660),
                 (0.610, 0.655, 0.675, 0.720)])
# uchiwa: fan R85, sleeve strip R83, forearm R90, fist bits
# R93/96/97/99/100; handle ink diagonal box
build("uchiwa", [85, 83, 90, 96], uchiwa_extra,
      [90], [(0.640, 0.55, 0.700, 0.645)])
