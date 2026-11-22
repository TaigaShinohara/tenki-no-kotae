import numpy as np, os
from PIL import Image
import sys
sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools/illust_fixes"))
from fix1_head import shirt_candidates, wide_runs, ZONE
D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
O = os.path.expanduser("~/workspace/tenki-no-kotae/tools/illust_fixes")
for name in ("layer_head_cap","layer_head_knitcap"):
    im = Image.open(os.path.join(D, name+"_fixed.png")).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    H,W,_ = a.shape
    cand = shirt_candidates(a[...,:3], a[...,3])
    pts = []
    for y in range(int(H*ZONE), H):
        wr = wide_runs(cand[y])
        ys, xs = np.nonzero(wr), np.nonzero(wr)[0]
        for x in np.nonzero(wr)[0]:
            pts.append((int(x), y, tuple(int(v) for v in a[y, x, :3])))
    print(name, "leftover fabric px:", len(pts))
    for p in pts[:12]: print("   x=%d y=%d (y/H=%.3f) rgb=%s" % (p[0], p[1], p[1]/H, p[2]))
    # bottom zoom of fixed version
    bg = Image.new("RGBA", im.size, (24,40,80,255)); bg.alpha_composite(im)
    crop = bg.crop((int(W*0.10), int(H*0.80), int(W*0.90), H))
    crop = crop.resize((crop.size[0]*2, crop.size[1]*3), Image.NEAREST)
    crop.convert("RGB").save(os.path.join(O, f"zoom_{name}_fixed_bottom.png"))
