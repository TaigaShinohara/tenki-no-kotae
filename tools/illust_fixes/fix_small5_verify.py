#!/usr/bin/env python3
"""Raincoat + head: before (fix-1 layer) vs after (rain variant)."""
import os, sys
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

LK = os.path.join(TOOLS, "layers_keyed")
OUT = os.path.join(TOOLS, "illust_fixes", "fix_small5")
W, H = 760, 900

FIXED = {n: Image.open(os.path.join(LK, n + "_fixed.png")).convert("RGBA")
         for n in ("layer_head_cap", "layer_head_knitcap")}
RAIN = {n: Image.open(os.path.join(LK, n + "_rain_fixed.png")).convert("RGBA")
        for n in ("layer_head_cap", "layer_head_knitcap")}

for head, art in (("CAP", "layer_head_cap"), ("KNITCAP", "layer_head_knitcap")):
    spec = M.spec_for(base="RAINCOAT", top=None, head=head)
    M.IMG.pop(art, None); M.IMG[art] = FIXED[art]
    before = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    M.IMG[art] = RAIN[art]
    after = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    pair = Image.new("RGB", (W * 2 + 12, H), (10, 14, 26))
    pair.paste(before, (0, 0)); pair.paste(after, (W + 12, 0))
    pair.save(os.path.join(OUT, f"pair_raincoat_{head.lower()}.png"))
    crop_box = (150, 40, 620, 430)
    cp = Image.new("RGB", (940 + 8, 780), (10, 14, 26))
    for i, im in enumerate((before, after)):
        c = im.crop(crop_box)
        c = c.resize((470, 390), Image.LANCZOS)
        cp.paste(c, (i * 474, 0))
        c2 = im.crop((240, 90, 530, 380)).resize((470, 390), Image.LANCZOS)
        cp.paste(c2, (i * 474, 390))
    cp.save(os.path.join(OUT, f"pair_raincoat_{head.lower()}_crop.png"))
    print("saved", head)
