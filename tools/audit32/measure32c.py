#!/usr/bin/env python3
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"

for name in ["arm_polo_umbrella_open", "arm_cardigan_umbrella_open",
             "arm_knitcoat_umbrella_open"]:
    a = np.asarray(Image.open(RES + name + ".png").convert("RGBA")).astype(int)
    al = a[:, :, 3] > 0
    lum = 0.299*a[:,:,0]+0.587*a[:,:,1]+0.114*a[:,:,2]
    print(f"== {name}")
    # horizontal scan at y=200 through the canopy: color runs
    row = a[200]
    runs = []
    s = None
    for i in range(len(row)):
        key = tuple(row[i][:3]) if al[200, i] else None
        if s is None or s[2] != key:
            if s: runs.append(s)
            s = [i, i, key]
        else:
            s[1] = i
    if s: runs.append(s)
    print("  y=200 color runs (x0,x1,color):",
          [(r[0], r[1], r[2]) for r in runs if r[1]-r[0] >= 0][:24])
    # shaft core color: darkest column cluster near shaft at y=600
    for y in [600]:
        dark = np.where(al[y] & (lum[y] < 120))[0]
        print(f"  y={y} dark px runs:", end=" ")
        if len(dark):
            groups = np.split(dark, np.where(np.diff(dark) > 1)[0]+1)
            print([(int(g.min()), int(g.max())) for g in groups])
        else:
            print("none")
    # shaft pixel colors sample (y 500..700, the shaft corridor center)
    # arm top: for arm columns, topmost alpha y that is NOT canopy-dark:
    # print topmost alpha per column block
    for x0, x1, label in [(530, 600, "arm(polo-ish)"), (370, 450, "arm(card)"),
                          (505, 585, "arm(knit)")]:
        tops = []
        for x in range(x0, x1, 10):
            col = np.where(al[:, x])[0]
            # topmost alpha below y=340 (below canopy top region)
            below = col[col >= 340]
            tops.append(int(below.min()) if len(below) else None)
        print(f"  arm cols {label} x{x0}-{x1}: topmost alpha y>=340 per 10px: {tops}")
    # canopy fill colors: median of canopy cloth (lum 35..70), ink (lum<30)
    can = al & (np.arange(al.shape[0])[:, None] < 380)
    cloth = can & (lum >= 35) & (lum <= 75)
    ink = can & (lum < 32)
    if cloth.any():
        print("  cloth median:", np.median(a[cloth][:, :3], axis=0))
    if ink.any():
        print("  ink median:", np.median(a[ink][:, :3], axis=0),
              "ink px:", int(ink.sum()))
    print()
