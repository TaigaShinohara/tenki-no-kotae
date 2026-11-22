#!/usr/bin/env python3
"""v0.1.30 hand patches: crops of the base body bitmaps around the
right hand (bitmap fractions == person-rect fractions), with the
alpha masked to where the day's top does NOT cover the body — so
pasting the patch over the fan's handle repaints only pixels the
composite already shows, plus the fist over the handle. Coverage is
computed in the shared 1120x2240 canvas space with the shipped
boxes and topCal values."""
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"
CW, CH = 1120.0, 2240.0
BOX = {
    "body_a": (0.1589, 0.0768, 0.8438, 0.9411),
    "body_b": (0.1580, 0.0763, 0.8446, 0.9397),
    "top_tshirt": (0.1795, 0.3031, 0.8214, 0.6741),
    "top_shirt": (0.2420, 0.3125, 0.7580, 0.6263),
}
TOPCAL = {"top_tshirt": (0.64, 0.875, 0.0, 0.028),
          "top_shirt": (0.80, 0.80, 0.0, 0.014)}
JOBS = [("body_b", "top_tshirt", (0.685, 0.575, 0.800, 0.670),
         "patch_hand_b"),
        ("body_a", "top_shirt", (0.685, 0.575, 0.800, 0.680),
         "patch_hand_a")]

for body_name, top_name, rect, out_name in JOBS:
    body = Image.open(RES + body_name + ".png").convert("RGBA")
    top = Image.open(RES + top_name + ".png").convert("RGBA")
    bb, tb = BOX[body_name], BOX[top_name]
    # top's drawn rect in canvas fractions (partRect + topCal math)
    cx, cy = (tb[0] + tb[2]) / 2, (tb[1] + tb[3]) / 2
    w = (tb[2] - tb[0]) * TOPCAL[top_name][0]
    h = (tb[3] - tb[1]) * TOPCAL[top_name][1]
    dx = TOPCAL[top_name][2] * (bb[2] - bb[0])
    dy = TOPCAL[top_name][3] * (bb[3] - bb[1])
    drawn = (cx - w / 2 + dx, cy - h / 2 + dy, cx + w / 2 + dx, cy + h / 2 + dy)
    # top alpha in canvas space
    cov = Image.new("L", (1120, 2240), 0)
    t = top.resize((max(1, round((drawn[2] - drawn[0]) * 1120)),
                    max(1, round((drawn[3] - drawn[1]) * 2240))))
    cov.paste(t.split()[3], (round(drawn[0] * 1120), round(drawn[1] * 2240)))
    cov = np.asarray(cov) > 0
    # body pixel (i,j) -> canvas point -> covered?
    ba = np.asarray(body).copy()
    Hb, Wb = ba.shape[:2]
    xs = bb[0] + (np.arange(Wb) + 0.5) / Wb * (bb[2] - bb[0])
    ys = bb[1] + (np.arange(Hb) + 0.5) / Hb * (bb[3] - bb[1])
    cxi = np.clip((xs * 1120).astype(int), 0, 1119)
    cyi = np.clip((ys * 2240).astype(int), 0, 2239)
    covered = cov[np.ix_(cyi, cxi)]
    ba[covered, 3] = 0
    x0, y0 = int(rect[0] * Wb), int(rect[1] * Hb)
    x1, y1 = int(rect[2] * Wb), int(rect[3] * Hb)
    patch = Image.fromarray(ba, "RGBA").crop((x0, y0, x1, y1))
    patch.save(RES + out_name + ".png")
    kept = int((np.asarray(patch)[:, :, 3] > 0).sum())
    print(out_name, patch.size, "kept px:", kept, "rect:", rect)
print("ok")
