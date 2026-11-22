#!/usr/bin/env python3
"""v0.1.28 face verification: for every base, render bare / cap /
knitcap with mirror_v0128 and measure the DRAWN face on each
composite (eye spacing, eye line, face width at the eye line,
mouth line, chin by the width-profile rule) in body-height units.
The three renders of a base must agree within ~1-2%."""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0128 as M  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
SKIN = np.array([0xC0, 0xEA, 0xFC], float)
INK = np.array([0x1B, 0x25, 0x66], float)
W, H = 1140, 1350


def person_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    bw = ph * M.body_aspect(spec["base"])
    bl = (W - bw) / 2
    return bl, pt, bw, ph


def measure(canvas, spec):
    bl, pt, bw, ph = person_rect(spec)
    a = np.asarray(canvas).astype(float)
    # head region crop
    x0, x1 = max(0, int(bl - 0.05 * bw)), int(bl + 1.05 * bw)
    y0, y1 = max(0, int(pt - 0.08 * ph)), int(pt + 0.48 * ph)
    sub = a[y0:y1, x0:x1, :3]
    alpha = np.ones(sub.shape[:2], bool)
    dskin = np.sqrt(((sub - SKIN) ** 2).sum(-1))
    skin = dskin < 46
    skin = ndimage.binary_closing(skin, structure=np.ones((5, 5)))
    skin = ndimage.binary_fill_holes(skin)
    dink = np.sqrt(((sub - INK) ** 2).sum(-1))
    ink = dink < 70
    lab, n = ndimage.label(ink)
    feats = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if 8 <= len(xs) <= 9000:
            feats.append((xs.mean(), ys.mean(), len(xs)))
    feats.sort(key=lambda f: f[1])
    eyes = feats[:2]
    mouth = feats[2] if len(feats) > 2 else (np.nan, np.nan, 0)
    eye_y = np.mean([e[1] for e in eyes])
    eye_sp = abs(eyes[1][0] - eyes[0][0])
    # face component: skin component containing the eye midpoint
    lab2, n2 = ndimage.label(skin)
    ex, ey = int(np.mean([e[0] for e in eyes])), int(eye_y)
    lid = lab2[ey, ex]
    comp = (lab2 == lid)
    ys, xs = np.where(comp)
    top = ys.min()
    # width at the eye row
    row = np.where(comp[int(eye_y)])[0]
    w_eye = (row.max() - row.min()) if len(row) else 0
    # width profile -> chin (0.68 rule below the mouth)
    widths = {}
    for y in range(ys.min(), ys.max() + 1):
        r = np.where(comp[y])[0]
        widths[y] = (r.max() - r.min()) if len(r) else 0
    wmax = max(v for y, v in widths.items() if y <= mouth[1])
    chin = None
    for y in sorted(widths):
        if y <= mouth[1]:
            continue
        if all(widths.get(y + k, 0) < 0.68 * wmax for k in range(3)):
            chin = y
            break
    f = lambda v: (v) / ph  # px -> body-height units (y from pt)
    return {
        "eye_sp": eye_sp / ph,
        "eye_y": (y0 + eye_y - pt) / ph,
        "mouth_y": (y0 + mouth[1] - pt) / ph,
        "face_top": (y0 + top - pt) / ph,
        "w_eye": w_eye / bw,
        "chin": (y0 + chin - pt) / ph if chin is not None else float("nan"),
        "nclusters": len(feats),
    }


BASES = ["BODY_A", "BODY_B", "BODY_C", "RANK5_PORTRAIT",
         "RANK6_PORTRAIT", "RAINCOAT"]
TOPS = {"BODY_A": "SHIRT", "BODY_B": "TSHIRT", "BODY_C": "DOWN"}

rows = []
for base in BASES:
    spec0 = {"base": base, "top": TOPS.get(base)}
    trio = []
    for head in (None, "CAP", "KNITCAP"):
        spec = dict(spec0)
        if head:
            spec["head"] = head
        canvas = M.draw(M.sky(W, H), spec, W, H)
        trio.append(canvas.convert("RGB"))
        m = measure(canvas, spec)
        m["base"], m["head"] = base, head or "BARE"
        rows.append(m)
    strip = Image.new("RGB", (W * 3 + 16, H), (24, 24, 32))
    for i, c in enumerate(trio):
        strip.paste(c, (i * (W + 8), 0))
    strip.save(os.path.join(OUT, f"face3_{base.lower()}.png"))
    # head crop of the trio (top 55%)
    crop = strip.crop((0, 0, strip.size[0], int(H * 0.58)))
    crop.save(os.path.join(OUT, f"face3_{base.lower()}_crop.png"))

print(f"{'base':<16}{'head':<9}{'eye_sp':>8}{'eye_y':>8}{'mouth':>8}"
      f"{'top':>8}{'w@eye':>8}{'chin':>8}{'ink#':>5}")
for m in rows:
    print(f"{m['base']:<16}{m['head']:<9}{m['eye_sp']:>8.4f}"
          f"{m['eye_y']:>8.3f}{m['mouth_y']:>8.3f}{m['face_top']:>8.3f}"
          f"{m['w_eye']:>8.3f}{m['chin']:>8.3f}{m['nclusters']:>5}")
print()
print("max deviation from BARE within each base:")
for base in BASES:
    rs = [m for m in rows if m["base"] == base]
    bare = rs[0]
    for m in rs[1:]:
        dev = {k: abs(m[k] - bare[k]) for k in
               ("eye_sp", "eye_y", "mouth_y", "face_top", "w_eye", "chin")}
        worst = max(dev, key=dev.get)
        print(f"  {base} {m['head']}: worst {worst} "
              f"dev {dev[worst]:.4f}  (all: " +
              ", ".join(f"{k} {v:.4f}" for k, v in dev.items()) + ")")
