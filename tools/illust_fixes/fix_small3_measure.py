#!/usr/bin/env python3
"""Key the regenerated umbrella portraits and measure torso core
widths against the bases (same metric as the drift audit)."""
import os, sys
import numpy as np
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402
from key_assets import key_image  # noqa: E402

K = os.path.join(TOOLS, "sleeve_arms", "keyed")
SRC = os.path.expanduser("~/workspace/imagine_media")
NEW = {
    "rank5_umbrella_open_fixed":
        "media-generation-tenki-sleeve-rank5-umbrella-op-0-815734b9-fdec-4767-b0e8-e30f01c0b88b.webp",
    "rank6_umbrella_open_fixed":
        "media-generation-tenki-sleeve-rank6-umbrella-op-0-cb80d0cb-27e7-4457-9dbd-46effd888a6d.webp",
}
for name, fn in NEW.items():
    keyed, bbox, key = key_image(os.path.join(SRC, fn))
    layer = keyed.crop(bbox)
    sc = min(1.0, 1024 / max(layer.size))
    if sc < 1.0:
        layer = layer.resize((round(layer.size[0] * sc),
                              round(layer.size[1] * sc)), Image.LANCZOS)
    dst = os.path.join(K, name + ".png")
    layer.save(dst, optimize=True)
    print("keyed", name, layer.size)


def core_widths(im):
    a = np.asarray(im)
    al = a[..., 3] > 128
    H, W = al.shape
    band = al[int(0.78 * H):int(0.88 * H)]
    xs = np.nonzero(band.any(0))[0]
    cx = (xs.min() + xs.max()) / 2
    res = {}
    for fy in (0.45, 0.52, 0.60):
        y = int(fy * H)
        row = al[y]
        x = int(cx)
        if not row[x]:
            cand = np.nonzero(row)[0]
            if len(cand) == 0:
                res[fy] = None
                continue
            x = cand[np.argmin(np.abs(cand - cx))]
        l = x
        while l > 0 and row[l - 1]:
            l -= 1
        r = x
        while r < W - 1 and row[r + 1]:
            r += 1
        res[fy] = round((r - l + 1) / H, 3)
    return res


def load(name, from_mirror=False):
    if from_mirror:
        return M.img(name).convert("RGBA")
    return Image.open(os.path.join(K, name + ".png")).convert("RGBA")


print("BASE rank5      :", core_widths(load("person_rank5", True)))
print("rank5 open OLD  :", core_widths(load("rank5_umbrella_open")))
print("rank5 open FIXED:", core_widths(load("rank5_umbrella_open_fixed")))
print("BASE rank6      :", core_widths(load("person_rank6", True)))
print("rank6 open OLD (proto2):",
      core_widths(Image.open(os.path.join(
          TOOLS, "prototypes", "rank6_umbrella_sleeve_proto2.png")).convert("RGBA")))
print("rank6 open FIXED:", core_widths(load("rank6_umbrella_open_fixed")))
