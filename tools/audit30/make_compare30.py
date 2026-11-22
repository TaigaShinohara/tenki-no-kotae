#!/usr/bin/env python3
"""v0.1.30 comparison sheets: base | v0.1.29 | v0.1.30.

The v0.1.29 panels are the predecessor's before_* renders
(mirror_v0129); the v0.1.30 panels are rendered here with
mirror_v0130 under the same specs (parasol = the auto UV day:
cap + sunglasses; fan = plain, plus the towel case)."""
import sys

sys.path.insert(0, "tools")
import mirror_v0129 as M29
import mirror_v0130 as M30
from PIL import Image, ImageDraw

OUT = "tools/audit30/"


def load(name):
    return Image.open(OUT + name + ".png").convert("RGB")


def save(im, name):
    im.save(OUT + name + ".png")
    print(name, im.size)


def strip(panels, name, labels=("土台", "v0.1.29", "v0.1.30")):
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


# ---- parasol (auto UV day: cap + sunglasses) ----
for rank in (0, 1):
    base = load(f"before_base_rank{rank}")
    old = load(f"before_parasol_rank{rank}")
    spec = M30.spec_for(rank, held="PARASOL", head="CAP",
                        sunglasses=True)
    new = M30.render(spec)
    save(new, f"after_parasol_rank{rank}")
    strip([base, old, new], f"pair30_parasol_rank{rank}")
    # canopy + shaft + fist zoom (right half, top to hip)
    crop3([base, old, new], (300, 0, 760, 700),
          f"crop30_parasol_rank{rank}")

# ---- fan (plain; rank 0 is the auto case, rank 1 the manual
#      one — its v0.1.29 panel is rendered here from the retired
#      portrait via mirror_v0129) ----
for rank in (0, 1):
    base = load(f"before_base_rank{rank}")
    if rank == 0:
        old = load("before_uchiwa_rank0")
    else:
        old = M29.render(M29.spec_for(1, held="UCHIWA"))
        save(old, "before_uchiwa_rank1")
    new = M30.render(M30.spec_for(rank, held="UCHIWA"))
    save(new, f"after_uchiwa_rank{rank}")
    strip([base, old, new], f"pair30_uchiwa_rank{rank}")
    # grip zoom
    crop3([base, old, new], (400, 430, 760, 720),
          f"crop30_uchiwa_rank{rank}")

# ---- fan + neck towel (rank 0) ----
base = load("before_base_rank0")
old = load("before_uchiwa_rank0_towel")
new = M30.render(M30.spec_for(0, held="UCHIWA", neck="TOWEL"))
save(new, "after_uchiwa_rank0_towel")
strip([base, old, new], "pair30_uchiwa_rank0_towel")
print("done")
