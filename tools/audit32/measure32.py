#!/usr/bin/env python3
"""Measure the open-umbrella arm layers and the bases' heads."""
import numpy as np
from PIL import Image

RES = "app/src/main/res/drawable-nodpi/"

def load(n):
    return np.asarray(Image.open(RES + n + ".png").convert("RGBA")).astype(int)

for name in ["arm_polo_umbrella_open", "arm_cardigan_umbrella_open",
             "arm_knitcoat_umbrella_open"]:
    a = load(name)
    H, W, _ = a.shape
    al = a[:, :, 3] > 0
    print(f"== {name} {W}x{H}")
    # per-column bottom of the top blob (canopy), gap>=20 ends it
    bot = np.zeros(W, dtype=int)
    for x in range(W):
        col = al[:, x]
        last, gap, started = 0, 0, False
        for y in range(H):
            if col[y]:
                last, gap, started = y, 0, True
            elif started:
                gap += 1
                if gap >= 20:
                    break
        bot[x] = last
    print("  canopy bottom: min", bot.min(), "max", bot.max(),
          "at x=10:", bot[10], "x=W//2:", bot[W//2], "x=W-10:", bot[W-11])
    # columns whose blob runs deep (shaft / arm corridors)
    deep = np.where(bot > 500)[0]
    if len(deep):
        # group consecutive
        groups = []
        s = deep[0]; p = deep[0]
        for x in deep[1:]:
            if x > p + 1:
                groups.append((s, p)); s = x
            p = x
        groups.append((s, p))
        print("  deep columns (bot>500):", groups,
              "bottoms:", [int(bot[g[0]:g[1]+1].max()) for g in groups])
    # fist region: alpha mass in y 750..950
    sub = al[750:950, :]
    ys, xs = np.where(sub)
    if len(xs):
        print("  alpha bbox y750-950: x", xs.min(), xs.max(),
              "y", ys.min()+750, ys.max()+750)
    # topmost alpha per region: where is the arm column?
    # sample colors: canopy fill / rib / ink / shaft
    lum = 0.299*a[:,:,0]+0.587*a[:,:,1]+0.114*a[:,:,2]
    can = al & (np.arange(H)[:,None] < 380)
    cols, counts = np.unique(a[can][:,:3].reshape(-1,3), axis=0, return_counts=True)
    top = np.argsort(-counts)[:6]
    print("  canopy top colors:", [(tuple(cols[i]), int(counts[i])) for i in top])
    # shaft color: column of max depth near the deep group
    print()

# heads: hair extent in the base canvases (1120x2240 canvas space)
BOX = {
    "body_a": (0.1589, 0.0768, 0.8438, 0.9411),
    "body_b": (0.1580, 0.0763, 0.8446, 0.9397),
}
for name in ["body_a", "body_b", "person_rank5", "person_rank6"]:
    a = load(name)
    H, W, _ = a.shape
    al = a[:, :, 3] > 0
    # hair = dark blue pixels in the top 30% of the figure bbox
    ys, xs = np.where(al)
    top, bot = ys.min(), ys.max()
    head_zone = al.copy()
    head_zone[int(top + 0.30*(bot-top)):, :] = False
    # dark pixels only (hair, not face skin): lum < 120 and blue-ish
    lum = 0.299*a[:,:,0]+0.587*a[:,:,1]+0.114*a[:,:,2]
    hair = head_zone & (lum < 130)
    ys2, xs2 = np.where(hair)
    print(f"== {name} {W}x{H} fig y {top}..{bot}; hair x {xs2.min()}..{xs2.max()} "
          f"y {ys2.min()}..{ys2.max()} (frac of fig w: "
          f"{(xs2.min()-xs.min())/(xs.max()-xs.min()):.3f}.."
          f"{(xs2.max()-xs.min())/(xs.max()-xs.min()):.3f})")
