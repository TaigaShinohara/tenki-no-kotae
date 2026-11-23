#!/usr/bin/env python3
"""v0.1.34 step 4: render all 8 combos x rain/parasol with
mirror_v0134 (the new assets + wiring) and put each composite
side by side with the v0.1.33 snapshot (cur_*): 現行 | 新.
Also emits a head-zone crop pair per combo for the close look,
and re-runs the geometry measurement on the new composites
(canopy/hair width ratio, apex offset from the face centre,
frame margins)."""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit33"))
import mirror_v0134 as M  # noqa: E402
from render33 import COMBOS, build  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit34")
BG = (24, 24, 32, 255)


SKY = None


def on_bg(im):
    return im.convert("RGBA")


def measure(im, label, tag):
    global SKY
    if SKY is None:
        SKY = np.asarray(M.sky(760, 900).convert("RGB")).astype(int)
    a = np.asarray(im.convert("RGB")).astype(int)
    alpha = (np.abs(a - SKY).sum(axis=2) > 24)
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    skin = (lum > 150) & alpha & (a[:, :, 0] > a[:, :, 2] - 30)
    ys, xs = np.where(skin)
    face_cx = (xs.min() + xs.max()) / 2 if len(xs) else -1
    # hair: the dark slate mass around the face, rows above the eyes
    hair = alpha & (np.abs(a[:, :, 0].astype(int) - 52) < 26) & \
        (np.abs(a[:, :, 1].astype(int) - 75) < 26) & \
        (np.abs(a[:, :, 2].astype(int) - 134) < 30)
    hy, hx = np.where(hair)
    hair_w = (hx.max() - hx.min()) if len(hx) else -1
    hair_top = hy.min() if len(hy) else -1
    # canopy: opaque pixels above the hair top
    above = alpha.copy()
    above[hair_top:, :] = False
    cy, cx = np.where(above)
    can_w = (cx.max() - cx.min()) if len(cx) else -1
    apex_cx = cx[np.argmin(cy)] if len(cy) else -1
    left_m = cx.min() if len(cx) else -1
    right_m = (a.shape[1] - 1 - cx.max()) if len(cx) else -1
    print(f"{label:9s} {tag:7s} canopy/hair="
          f"{can_w / hair_w if hair_w > 0 else -1:.3f} "
          f"apex-face={apex_cx - face_cx:+.1f}px "
          f"margins L{left_m} R{right_m}")
    return can_w / hair_w if hair_w > 0 else -1


if __name__ == "__main__":
    for label, base, top, extra in COMBOS:
        for held, tag in (("OPEN", "rain"), ("PARASOL", "parasol")):
            im = M.render(build(base, top, held, extra))
            im.save(os.path.join(OUT, f"new_{label}_{tag}.png"))
            cur = Image.open(os.path.join(
                OUT, f"cur_{label}_{tag}.png")).convert("RGBA")
            pair = Image.new("RGBA", (760 * 2 + 24, 900), BG)
            pair.alpha_composite(on_bg(cur), (0, 0))
            pair.alpha_composite(on_bg(im), (760 + 24, 0))
            d = ImageDraw.Draw(pair)
            d.rectangle([760, 0, 783, 899], fill=(90, 90, 110, 255))
            pair.save(os.path.join(OUT, f"cmp_{label}_{tag}.png"))
            crop = pair.crop((180, 0, 760 * 2 + 24 - 180, 470))
            crop = crop.resize((crop.size[0], crop.size[1] * 1), Image.LANCZOS)
            crop.save(os.path.join(OUT, f"cmpcrop_{label}_{tag}.png"))
        print("compared", label)
