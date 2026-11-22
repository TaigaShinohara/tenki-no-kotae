#!/usr/bin/env python3
"""v0.1.22 audit: grip-point zooms for every held item x base, and
neck zooms for every layered top, rendered with the Python mirror of
PersonComposer (tools/mirror_v0121.py). Crops are taken around body-
rect fractions and upscaled ~3x so sub-cell misalignment is visible —
the v0.1.21 contact sheets were too small to catch the fan's grip
offset the user later spotted on device.

Usage: python3 audit22_gen.py [before|after]  (default: before)
Anchor / topCal trials are applied by patching the mirror's ANCH /
TOPCAL before drawing; keep the AFTER values in sync with
PersonFigure.kt (the values are printed at the end).
"""
import os
import sys

from PIL import Image

import importlib
import os

m = importlib.import_module(os.environ.get("MIRROR", "mirror_v0121"))

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit22")
os.makedirs(OUT, exist_ok=True)

W, H = 1140, 1350


def geom(spec):
    """(bl, pt, bw, ph) of the drawn body rect for a render at W x H."""
    ph = H / (1.0 + m.pad_top_ratio(spec))
    pt = H - ph
    base = spec["base"]
    if base in m.PORTRAITS:
        person = m.img(m.BODY_ART[base])
        bw = ph * person.size[0] / person.size[1]
    else:
        box = m.BOX[m.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * m.CANVAS_H)
        bw = (box[2] - box[0]) * m.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def render(spec):
    return m.render(spec, W=W, H=H)


def crop_frac(img, g, cx, cy, wf, hf, scale=3):
    bl, pt, bw, ph = g
    x0 = round(bl + (cx - wf / 2) * bw)
    x1 = round(bl + (cx + wf / 2) * bw)
    y0 = round(pt + (cy - hf / 2) * ph)
    y1 = round(pt + (cy + hf / 2) * ph)
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(img.size[0], x1), min(img.size[1], y1)
    c = img.crop((x0, y0, x1, y1))
    return c.resize((c.size[0] * scale, c.size[1] * scale), Image.LANCZOS)


def sheet(images, cols, path):
    cw = max(i.size[0] for i in images)
    ch = max(i.size[1] for i in images)
    rows = (len(images) + cols - 1) // cols
    s = Image.new("RGB", (cw * cols, ch * rows), (24, 24, 32))
    for idx, im in enumerate(images):
        s.paste(im, ((idx % cols) * cw, (idx // cols) * ch))
    s.save(path)
    print("sheet:", path, s.size)


BASES = ["BODY_A", "BODY_B", "BODY_C", "RANK5_PORTRAIT", "RANK6_PORTRAIT",
         "RAINCOAT"]
TOP_OF_BASE = {"BODY_A": "SHIRT", "BODY_B": "TSHIRT", "BODY_C": None}


def grip_crops(tag):
    """One zoom per held item x base, centered on the rig hand."""
    out = []
    for held in ("UCHIWA", "FOLDED", "OPEN", "PARASOL"):
        for base in BASES:
            spec = {"base": base, "top": TOP_OF_BASE.get(base), "held": held}
            img = render(spec)
            rig = m.RIGS[base]
            crop = crop_frac(img, geom(spec), rig[5], rig[6], 0.52, 0.46)
            name = f"{tag}_grip_{held.lower()}_{base.lower()}.png"
            crop.save(os.path.join(OUT, name))
            out.append(crop)
        sheet(out[-len(BASES):], 3,
              os.path.join(OUT, f"{tag}_grip_{held.lower()}_sheet.png"))
    return out


def neck_crops(tag):
    """One zoom per layered top at the neck, plus the down top on
    BODY_C (unused since v0.1.21, checked for the record)."""
    combos = [(0, None), (1, None), (2, None), (3, None), (4, None),
              (None, ("BODY_C", "DOWN"))]
    out = []
    for rank, forced in combos:
        if forced:
            spec = {"base": forced[0], "top": forced[1]}
            label = "down_bodyc"
        else:
            spec = m.spec_for(rank=rank)
            label = f"rank{rank}"
        img = render(spec)
        crop = crop_frac(img, geom(spec), 0.5, 0.26, 0.66, 0.40)
        crop.save(os.path.join(OUT, f"{tag}_neck_{label}.png"))
        out.append(crop)
    sheet(out, 3, os.path.join(OUT, f"{tag}_neck_sheet.png"))
    return out


def sweat_crop(tag):
    spec = m.spec_for(rank=0, held="UCHIWA", sweat=True)
    img = render(spec)
    crop = crop_frac(img, geom(spec), 0.5, 0.24, 0.95, 0.36)
    crop.save(os.path.join(OUT, f"{tag}_sweat_rank0.png"))
    return crop


if __name__ == "__main__":
    tag = sys.argv[1] if len(sys.argv) > 1 else "before"
    grip_crops(tag)
    neck_crops(tag)
    sweat_crop(tag)
    print("ANCH:", {k: m.ANCH[k] for k in sorted(m.ANCH)})
    print("TOPCAL:", m.TOPCAL)
