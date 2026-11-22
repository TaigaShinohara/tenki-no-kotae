#!/usr/bin/env python3
"""Small-2 proof: raincoat + mask with reduced cheek_w candidates.
The mirror reads cheek_w from RIGS[base][9]; patch it in-memory
(tools-side only) and render the face crop for each candidate."""
import os, sys
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

OUT = os.path.join(TOOLS, "illust_fixes", "fix_small2")
os.makedirs(OUT, exist_ok=True)
W, H = 760, 900
orig = M.RIGS["RAINCOAT"]
spec = M.spec_for(base="RAINCOAT", top=None, mask=True)

renders = []
for cw in (0.464, 0.40, 0.38):
    rig = list(orig); rig[9] = cw
    M.RIGS["RAINCOAT"] = tuple(rig)
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    img.save(os.path.join(OUT, f"raincoat_mask_cheek{int(cw*1000)}.png"))
    renders.append((cw, img))
    print("rendered cheek_w", cw, "-> cloth frac", round(M.ANCH["MASK_W"] * cw, 3))
M.RIGS["RAINCOAT"] = orig

box = (215, 120, 560, 430)
sheet = Image.new("RGB", (3 * 460 + 16, 413), (10, 14, 26))
for i, (cw, img) in enumerate(renders):
    c = img.crop(box).resize((460, 413), Image.LANCZOS)
    sheet.paste(c, (i * 468, 0))
sheet.save(os.path.join(OUT, "pair_mask_cheek.png"))
print("saved sheet (left->right: 0.464 current, 0.40, 0.38)")
