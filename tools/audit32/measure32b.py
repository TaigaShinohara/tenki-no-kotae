#!/usr/bin/env python3
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"

def runs(row):
    out = []
    s = None
    for i, v in enumerate(row):
        if v and s is None:
            s = i
        elif not v and s is not None:
            out.append((s, i - 1)); s = None
    if s is not None:
        out.append((s, len(row) - 1))
    return out

for name in ["arm_polo_umbrella_open", "arm_cardigan_umbrella_open",
             "arm_knitcoat_umbrella_open"]:
    a = np.asarray(Image.open(RES + name + ".png").convert("RGBA")).astype(int)
    al = a[:, :, 3] > 0
    H, W = al.shape
    print(f"== {name} {W}x{H}")
    for y in [200, 400, 450, 550, 700, 780, 820, 860, 900, 960, 1000]:
        print(f"  y={y}: {runs(al[y])}")
    # where does alpha start in the shaft corridor columns (polo/knit ~496, cardigan ~346)?
    print()
