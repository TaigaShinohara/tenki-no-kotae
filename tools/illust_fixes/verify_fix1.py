#!/usr/bin/env python3
"""Composite verification for fix 1: render every base x both head
layers with the ORIGINAL head layers (before) and the FIXED ones
(after), using mirror_v0125 with the head-layer images substituted
in its cache. Outputs side-by-side pairs and head crops."""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M

FIX = os.path.join(TOOLS, "illust_fixes")
LK = os.path.join(TOOLS, "layers_keyed")
OUT = os.path.join(FIX, "fix1")
os.makedirs(OUT, exist_ok=True)
W, H = 1140, 1350

FIXED = {n: Image.open(os.path.join(LK, n + "_fixed.png")).convert("RGBA")
         for n in ("layer_head_cap", "layer_head_knitcap")}
ORIG = {n: Image.open(os.path.join(LK, n + ".png")).convert("RGBA")
        for n in ("layer_head_cap", "layer_head_knitcap")}

# numeric hair/face safety: alpha changes above 0.85H must be zero
for n in FIXED:
    a0 = np.asarray(ORIG[n])[..., 3].astype(int)
    a1 = np.asarray(FIXED[n])[..., 3].astype(int)
    h = a0.shape[0]
    print(f"{n}: alpha-changed px total={int((a0 != a1).sum())}, "
          f"above 0.85H={int((a0[:int(h*0.85)] != a1[:int(h*0.85)]).sum())}")


def body_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    base = spec["base"]
    if base in M.PORTRAITS:
        aspect = 405 / 1024 if base == "RANK5_PORTRAIT" else 448 / 1024
        bw = ph * aspect
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def head_crop(img, spec, out_w=900):
    bl, pt, bw, ph = body_rect(spec)
    box = (round(bl + 0.02 * bw), round(pt - 0.03 * ph),
           round(bl + 0.98 * bw), round(pt + 0.50 * ph))
    c = img.crop(box)
    r = out_w / c.size[0]
    return c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)


def render(spec, fixed):
    M.IMG.pop("layer_head_cap", None)
    M.IMG.pop("layer_head_knitcap", None)
    if fixed:
        M.IMG.update(FIXED)
    return M.draw(M.sky(W, H), spec, W, H).convert("RGB")


CASES = []
for r in range(7):
    for head, tag in (("CAP", "cap"), ("KNITCAP", "knit")):
        CASES.append((f"rank{r}_{tag}", M.spec_for(rank=r, head=head)))
for head, tag in (("CAP", "cap"), ("KNITCAP", "knit")):
    CASES.append((f"raincoat_{tag}",
                  M.spec_for(base="RAINCOAT", top=None, head=head)))

for name, spec in CASES:
    before = render(spec, False)
    after = render(spec, True)
    head_crop(after, spec).save(os.path.join(OUT, f"after_{name}_head.png"))
    if name in ("rank3_cap", "rank5_cap", "rank6_knit",
                "raincoat_cap", "raincoat_knit", "rank0_cap"):
        pair = Image.new("RGB", (W * 2 + 16, H), (10, 14, 26))
        pair.paste(before, (0, 0))
        pair.paste(after, (W + 16, 0))
        pair = pair.resize((1400, round(1400 * pair.size[1] / pair.size[0])),
                           Image.LANCZOS)
        pair.save(os.path.join(OUT, f"pair_{name}.png"))
        cp = Image.new("RGB", (1800 + 12, 470), (10, 14, 26))
        cp.paste(head_crop(before, spec), (0, 0))
        cp.paste(head_crop(after, spec), (912, 0))
        cp.save(os.path.join(OUT, f"pair_{name}_headcrop.png"))
    print("rendered", name)
print("done ->", OUT)
