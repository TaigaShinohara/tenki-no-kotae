import numpy as np, os
from PIL import Image
D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
O = os.path.expanduser("~/workspace/tenki-no-kotae/tools/illust_fixes")
for name in ("layer_head_cap","layer_head_knitcap"):
    im = Image.open(os.path.join(D, name+"_fixed.png")).convert("RGBA")
    a = np.asarray(im)
    H,W,_ = a.shape
    y0 = int(H*0.88)
    sub = a[y0:]
    op = sub[...,3] > 24
    # column stats
    cols = []
    for x in range(W):
        ys = np.nonzero(op[:,x])[0]
        if len(ys):
            cols.append((x, ys.min()+y0, ys.max()+y0, len(ys)))
    print(name, "columns with opaque below 0.88H:", len(cols))
    # summarize by topmost-y bands
    from collections import Counter
    print("  topmost y/H hist:", Counter(round(c[1]/H,2) for c in cols).most_common(8))
    vis = np.zeros((sub.shape[0], W, 3), np.uint8)
    vis[op] = (255,80,80)
    # mark skin-colored survivors pale, hair navy
    rgb = sub[...,:3].astype(int)
    lum = rgb.mean(axis=2)
    vis[op & (lum>180)] = (160,220,255)
    vis[op & (lum>=70) & (lum<150)] = (60,90,200)
    img = Image.fromarray(vis).resize((W, sub.shape[0]*4), Image.NEAREST)
    img.save(os.path.join(O, f"remnants_{name}.png"))
    print("  saved remnant map")
