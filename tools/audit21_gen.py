#!/usr/bin/env python3
"""v0.1.21 audit: contact sheets of the whole slot matrix.

Uses mirror_v0121 (exact PersonComposer mirror). Outfits are the
ranks 0-6 and the raincoat; slots are the test screen's: head
(CAP/KNITCAP), face (mask/sunglasses), neck (MUFFLER/TOWEL),
held (OPEN/FOLDED/PARASOL/UCHIWA). Auto scenarios follow
PersonFigure.spec's rules.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0121 as M

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit21")
os.makedirs(OUT, exist_ok=True)
CELL_W, CELL_H = 300, 356
try:
    FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 20)
except Exception:
    FONT = ImageFont.load_default()


def sheet(name, cells, cols=6):
    rows = (len(cells) + cols - 1) // cols
    img = Image.new("RGB", (cols * CELL_W, rows * CELL_H), (24, 24, 32))
    d = ImageDraw.Draw(img)
    for i, (label, spec) in enumerate(cells):
        x, y = (i % cols) * CELL_W, (i // cols) * CELL_H
        render = M.render(spec, CELL_W, CELL_H - 26)
        img.paste(render, (x, y + 26))
        d.text((x + 8, y + 4), label, fill=(255, 220, 120), font=FONT)
    p = os.path.join(OUT, name + ".png")
    img.save(p)
    print(p)


def outfit(rank):
    if rank == "R":
        return {"base": "RAINCOAT", "top": None}
    b, t = M.OUTFIT_FOR[rank]
    return {"base": b, "top": t}


OUTFITS = [0, 1, 2, 3, 4, 5, 6, "R"]
ONAME = {0: "r0", 1: "r1", 2: "r2", 3: "r3", 4: "r4", 5: "r5", 6: "r6", "R": "rain"}


def auto(rank, **kw):
    """PersonFigure.spec's auto outfit-linked elements."""
    s = outfit(rank)
    if rank != "R":
        r = rank
        s.update({
            "neck": "MUFFLER" if r >= 5 else None,
            "mittens": r == 6,
            "shiver": r >= 5,
            "sweat": r == 0,
        })
    s.update(kw)
    return s


def main():
    # ---- sheet 1: plain auto outfits + weather scenarios ----
    cells = [(f"{ONAME[o]} plain", auto(o)) for o in OUTFITS]
    cells += [
        ("r0 uv8 cap+sung+uchiwa", auto(0, head="CAP", sunglasses=True, held="UCHIWA")),
        ("r0 uv8 parasol", auto(0, head="CAP", sunglasses=True, held="PARASOL")),
        ("r0 towel", auto(0, neck="TOWEL")),
        ("r1 uv8 parasol", auto(1, head="CAP", sunglasses=True, held="PARASOL")),
        ("r2 open", auto(2, held="OPEN")),
        ("r3 open", auto(3, held="OPEN")),
        ("r3 folded", auto(3, held="FOLDED")),
        ("r4 mask", auto(4, mask=True)),
        ("r4 snow knitcap", auto(4, head="KNITCAP")),
        ("r5 mask", auto(5, mask=True)),
        ("r5 snow knitcap", auto(5, head="KNITCAP")),
        ("r6 mask", auto(6, mask=True)),
        ("r6 snow knitcap", auto(6, head="KNITCAP")),
        ("rain open", auto("R", held="OPEN")),
        ("rain folded", auto("R", held="FOLDED")),
    ]
    sheet("sheet1_auto", cells)

    # ---- sheet 2: head slot on every outfit ----
    cells = []
    for o in OUTFITS:
        for h in ("CAP", "KNITCAP"):
            cells.append((f"{ONAME[o]} {h.lower()}", auto(o, head=h)))
    sheet("sheet2_head", cells)

    # ---- sheet 3: face slot on every outfit ----
    cells = []
    for o in OUTFITS:
        cells.append((f"{ONAME[o]} mask", auto(o, mask=True)))
        cells.append((f"{ONAME[o]} sung", auto(o, sunglasses=True)))
    sheet("sheet3_face", cells)

    # ---- sheet 4: neck slot on every outfit ----
    cells = []
    for o in OUTFITS:
        cells.append((f"{ONAME[o]} muffler", auto(o, neck="MUFFLER")))
        cells.append((f"{ONAME[o]} towel", auto(o, neck="TOWEL")))
    sheet("sheet4_neck", cells)

    # ---- sheet 5: held slot on every outfit ----
    cells = []
    for o in OUTFITS:
        for hd in ("OPEN", "FOLDED", "PARASOL", "UCHIWA"):
            cells.append((f"{ONAME[o]} {hd.lower()}", auto(o, held=hd)))
    sheet("sheet5_held", cells, cols=8)


if __name__ == "__main__":
    main()
