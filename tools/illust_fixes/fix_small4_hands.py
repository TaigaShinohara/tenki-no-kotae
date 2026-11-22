#!/usr/bin/env python3
"""Small-4: why do BODY_B's hands vanish under the T-shirt?
Measure hand visibility with/without the top, locate the shirt's
sleeve bottom vs the hand, then test TOPCAL interventions."""
import os, sys
import numpy as np
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

OUT = os.path.join(TOOLS, "illust_fixes", "fix_small4")
os.makedirs(OUT, exist_ok=True)
W, H = 760, 900
SKIN = np.array([192, 234, 252])

def skin_mask(img):
    a = np.asarray(img.convert("RGB")).astype(int)
    return np.abs(a - SKIN).sum(-1) < 80

def body_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    box = M.BOX[M.BODY_ART[spec["base"]]]
    scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
    bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph

spec_bare = M.spec_for(rank=0, top=None)
spec_top = M.spec_for(rank=0, top="TSHIRT")
bl, pt, bw, ph = body_rect(spec_bare)
rig = M.RIGS["BODY_B"]
bare = M.draw(M.sky(W, H), spec_bare, W, H).convert("RGB")
withtop = M.draw(M.sky(W, H), spec_top, W, H).convert("RGB")
bare.save(os.path.join(OUT, "bodyb_bare.png"))
withtop.save(os.path.join(OUT, "bodyb_tshirt.png"))

for side, (hx, hy) in (("right", (rig[5], rig[6])), ("left", (rig[7], rig[8]))):
    cx, cy = bl + hx * bw, pt + hy * ph
    x0, x1, y0, y1 = int(cx - 0.09 * bw), int(cx + 0.09 * bw), \
        int(cy - 0.10 * ph), int(cy + 0.06 * ph)
    b = skin_mask(bare)[y0:y1, x0:x1]
    t = skin_mask(withtop)[y0:y1, x0:x1]
    print(f"{side} hand @({hx},{hy}): skin px bare={int(b.sum())} "
          f"with T-shirt={int(t.sum())} ({100.0 * t.sum() / max(1, b.sum()):.0f}% visible)")
    if b.sum():
        ys, xs = np.nonzero(b)
        print(f"   bare hand skin extent: fy [{(y0 + ys.min() - pt) / ph:.3f},"
              f"{(y0 + ys.max() - pt) / ph:.3f}]")

# where is the shirt's sleeve bottom? scan the with-top render at the
# hand's x column for the shirt fabric colour
a = np.asarray(withtop).astype(int)
fab = (np.abs(a - np.array([168, 211, 242])).sum(-1) < 90)
hx = rig[5]
for dx in (-0.03, 0.0, 0.03):
    x = int(bl + (hx + dx) * bw)
    ys = np.nonzero(fab[:, x])[0]
    if len(ys):
        print(f"shirt fabric at fx={hx + dx:.3f}: fy "
              f"[{(ys.min() - pt) / ph:.3f}, {(ys.max() - pt) / ph:.3f}]")
print("TOPCAL[TSHIRT]:", M.TOPCAL["top_tshirt"], " BOX:", M.BOX["top_tshirt"])

# ---- intervention test: TOPCAL variants ----
print("\nTOPCAL variants (hand visibility + renders):")
orig_cal = M.TOPCAL["top_tshirt"]
for tag, cal in (("A_current", (0.70, 0.875, 0.0, 0.028)),
                 ("B_raise", (0.70, 0.875, 0.0, -0.012)),
                 ("C_shorter", (0.70, 0.80, 0.0, 0.002))):
    M.TOPCAL["top_tshirt"] = cal
    img = M.draw(M.sky(W, H), spec_top, W, H).convert("RGB")
    img.save(os.path.join(OUT, f"bodyb_tshirt_{tag}.png"))
    tot_b = tot_t = 0
    for hx, hy in ((rig[5], rig[6]), (rig[7], rig[8])):
        cx, cy = bl + hx * bw, pt + hy * ph
        x0, x1, y0, y1 = int(cx - 0.09 * bw), int(cx + 0.09 * bw), \
            int(cy - 0.10 * ph), int(cy + 0.06 * ph)
        tot_b += int(skin_mask(bare)[y0:y1, x0:x1].sum())
        tot_t += int(skin_mask(img)[y0:y1, x0:x1].sum())
    print(f" {tag} cal={cal}: hands visible {100.0 * tot_t / tot_b:.0f}%")
M.TOPCAL["top_tshirt"] = orig_cal

# side-by-side sheet of the three variants (torso crop)
sheet = Image.new("RGB", (3 * 380 + 16, 560), (10, 14, 26))
for i, tag in enumerate(("A_current", "B_raise", "C_shorter")):
    im = Image.open(os.path.join(OUT, f"bodyb_tshirt_{tag}.png"))
    c = im.crop((150, 130, 610, 660)).resize((380, 439), Image.LANCZOS)
    sheet.paste(c, (i * 388, 0))
    c2 = im.crop((330, 380, 610, 660)).resize((380, 232), Image.LANCZOS)
sheet.save(os.path.join(OUT, "pair_topcal.png"))
print("saved pair_topcal.png (A current | B raise | C shorter)")

print("\nWidth variants:")
for tag, cal in (("D_w064", (0.64, 0.875, 0.0, 0.028)),
                 ("E_w060", (0.60, 0.875, 0.0, 0.028)),
                 ("F_w064_h082", (0.64, 0.82, 0.0, 0.014))):
    M.TOPCAL["top_tshirt"] = cal
    img = M.draw(M.sky(W, H), spec_top, W, H).convert("RGB")
    img.save(os.path.join(OUT, f"bodyb_tshirt_{tag}.png"))
    tot_b = tot_t = 0
    for hx, hy in ((rig[5], rig[6]), (rig[7], rig[8])):
        cx, cy = bl + hx * bw, pt + hy * ph
        x0, x1, y0, y1 = int(cx - 0.09 * bw), int(cx + 0.09 * bw), \
            int(cy - 0.10 * ph), int(cy + 0.06 * ph)
        tot_b += int(skin_mask(bare)[y0:y1, x0:x1].sum())
        tot_t += int(skin_mask(img)[y0:y1, x0:x1].sum())
    print(f" {tag} cal={cal}: hands visible {100.0 * tot_t / tot_b:.0f}%")
M.TOPCAL["top_tshirt"] = orig_cal
sheet = Image.new("RGB", (3 * 380 + 16, 439), (10, 14, 26))
for i, tag in enumerate(("A_current", "D_w064", "E_w060")):
    im = Image.open(os.path.join(OUT, f"bodyb_tshirt_{tag}.png"))
    c = im.crop((150, 130, 610, 660)).resize((380, 439), Image.LANCZOS)
    sheet.paste(c, (i * 388, 0))
sheet.save(os.path.join(OUT, "pair_topcal_width.png"))
print("saved pair_topcal_width.png (A current | D w0.64 | E w0.60)")
