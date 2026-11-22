#!/usr/bin/env python3
"""v0.1.31 comparison sheets: base | v0.1.30 | v0.1.31 (fan only).

The v0.1.30 panels are the audit30 renders (mirror_v0130, the
light-blue disc); the v0.1.31 panels are rendered here with
mirror_v0131 and the redrawn prop — same specs, same placement
numbers, only the prop picture differs.
"""
import sys

sys.path.insert(0, "tools")
import mirror_v0131 as M31
from PIL import Image, ImageDraw

A30 = "tools/audit30/"
OUT = "tools/audit31/"


def load(path):
    return Image.open(path).convert("RGB")


def save(im, name):
    im.save(OUT + name + ".png")
    print(name, im.size)


def strip(panels, name, labels=("土台", "v0.1.30", "v0.1.31")):
    w, h = panels[0].size
    sheet = Image.new("RGB", (w * len(panels) + 8 * (len(panels) - 1),
                              h + 26), (10, 20, 40))
    d = ImageDraw.Draw(sheet)
    for i, (p, lab) in enumerate(zip(panels, labels)):
        x = i * (w + 8)
        sheet.paste(p, (x, 26))
        d.text((x + 10, 6), lab, fill=(230, 240, 250))
    save(sheet, name)


def crop3(panels, box, name, scale=2):
    w = box[2] - box[0]
    h = box[3] - box[1]
    sheet = Image.new("RGB", (w * len(panels) * scale +
                              8 * (len(panels) - 1),
                              h * scale), (10, 20, 40))
    for i, p in enumerate(panels):
        c = p.crop(box).resize((w * scale, h * scale), Image.LANCZOS)
        sheet.paste(c, (i * (w * scale + 8), 0))
    save(sheet, name)


# ---- fan, rank 0 plain ----
base = load(A30 + "before_base_rank0.png")
old = load(A30 + "after_uchiwa_rank0.png")
new = M31.render(M31.spec_for(0, held="UCHIWA"))
save(new, "after_uchiwa_rank0")
strip([base, old, new], "pair31_uchiwa_rank0")
crop3([base, old, new], (400, 430, 760, 720), "crop31_uchiwa_rank0")

# ---- fan, rank 0 with neck towel ----
base_t = M31.render(M31.spec_for(0, neck="TOWEL"))
old_t = load(A30 + "after_uchiwa_rank0_towel.png")
new_t = M31.render(M31.spec_for(0, held="UCHIWA", neck="TOWEL"))
save(new_t, "after_uchiwa_rank0_towel")
strip([base_t, old_t, new_t], "pair31_uchiwa_rank0_towel")
crop3([base_t, old_t, new_t], (400, 430, 760, 720),
      "crop31_uchiwa_rank0_towel")
