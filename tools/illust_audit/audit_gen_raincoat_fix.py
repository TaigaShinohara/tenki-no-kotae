#!/usr/bin/env python3
"""Regenerate the raincoat cases with the actual RAINCOAT base.

The first pass called spec_for(rank=3, base="RAINCOAT"), but
mirror_v0125.spec_for ignores `base` when `rank` is given, so those
files showed BODY_A bare. Here rank is omitted. Standalone copy of
audit_gen.py's render helpers (importing audit_gen would re-run it).
"""
import os
import sys

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

OUT = os.path.join(TOOLS, "illust_audit")
FULL = os.path.join(OUT, "full")
CROPS = os.path.join(OUT, "crops")
W, H = 1140, 1350


def body_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    box = M.BOX[M.BODY_ART[spec["base"]]]
    scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
    bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def crop_body(img, spec, name, fx0, fy0, fx1, fy1, out_w=1050):
    bl, pt, bw, ph = body_rect(spec)
    box = (round(bl + fx0 * bw), round(pt + fy0 * ph),
           round(bl + fx1 * bw), round(pt + fy1 * ph))
    c = img.crop(box)
    r = out_w / c.size[0]
    c = c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)
    c.convert("RGB").save(os.path.join(CROPS, name))


from PIL import Image  # noqa: E402


def render_current(name, spec, crop_kinds=()):
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    img.save(os.path.join(FULL, name + "_full.png"))
    for kind in crop_kinds:
        if kind == "head":
            crop_body(img, spec, name + "_head.png", 0.02, -0.03, 0.98, 0.50)
        elif kind == "face_neck":
            crop_body(img, spec, name + "_face_neck.png", 0.08, 0.08, 0.92, 0.60)
        elif kind == "upper":
            crop_body(img, spec, name + "_upper.png", 0.0, 0.10, 1.0, 0.72)
        elif kind == "hands":
            crop_body(img, spec, name + "_hands.png", 0.0, 0.48, 1.0, 0.76)
        elif kind == "right_grip":
            crop_body(img, spec, name + "_right_grip.png", 0.42, 0.34, 1.08, 0.78)


def spec_rain(**kw):
    return M.spec_for(base="RAINCOAT", top=None, **kw)


CASES = [
    ("base_raincoat", spec_rain(), ("upper", "hands")),
    ("head_raincoat_cap", spec_rain(head="CAP"), ("head",)),
    ("head_raincoat_knit", spec_rain(head="KNITCAP"), ("head",)),
    ("mask_raincoat", spec_rain(mask=True), ("face_neck",)),
    ("mask_knit_raincoat", spec_rain(mask=True, head="KNITCAP"), ("face_neck",)),
    ("sung_cap_raincoat", spec_rain(sunglasses=True, head="CAP"), ("head", "face_neck")),
    ("held_raincoat_open", spec_rain(held="OPEN"), ("right_grip", "upper")),
    ("held_raincoat_folded", spec_rain(held="FOLDED"), ("right_grip", "upper")),
    ("held_raincoat_parasol", spec_rain(held="PARASOL"), ("head", "right_grip")),
]

for name, spec, crops in CASES:
    render_current(name, spec, crops)
print("raincoat cases regenerated:", len(CASES))
