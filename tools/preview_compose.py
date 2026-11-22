#!/usr/bin/env python3
"""Composite previews for v0.1.16 「今日の人」 — mirrors the math that
PersonComposer (Kotlin) will use, so the anchor constants tuned here
port over 1:1.

Canvas W x H (W = H/2 in-app). Person: height = canvas height,
bottom-aligned, centered. Anchors are fractions of the drawn person
rect. Items are drawn over the person.
"""
import os
import sys

from PIL import Image, ImageDraw

RES = os.path.expanduser("~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/previews")
W, H = 300, 600

RANKS = {i: Image.open(f"{RES}/person_rank{i}.png").convert("RGBA") for i in range(7)}
UMB_OPEN = Image.open(f"{RES}/item_umbrella_open.png").convert("RGBA")
UMB_FOLD = Image.open(f"{RES}/item_umbrella_folding.png").convert("RGBA")
HAT = Image.open(f"{RES}/item_hat.png").convert("RGBA")

# Right-hand anchor per garment (fractions of the person rect),
# measured from the generated art (skin/mitten centroids).
HAND_R = {
    0: (0.74, 0.63), 1: (0.731, 0.633), 2: (0.733, 0.634),
    3: (0.726, 0.614), 4: (0.767, 0.627), 5: (0.756, 0.629),
    6: (0.90, 0.66),
}
HEAD_CX = 0.50

# --- item anchors (tuned by eye from these previews) ---
HAT_W_FRAC = 0.60        # hat width / person width
HAT_BOTTOM_Y = 0.16      # hat bottom (brim) / person height
FOLD_H_FRAC = 0.24       # folding umbrella height / person height
FOLD_DX = 0.13           # its center x = hand x + this * person width
FOLD_BOTTOM_DY = 0.03    # its bottom = hand y + this * person height
# Open umbrella: the asset is canopy + shaft stub (see key_assets.py);
# the compositor draws the rest of the shaft as a line to the hand.
UMB_W_FRAC = float(os.environ.get("UMB_W_FRAC", "1.12"))  # width / person width
UMB_TOP_Y = 0.005        # canopy top / canvas height
UMB_SHAFT_X = 0.507      # shaft center inside the umbrella image
UMB_SHAFT_AT = 0.68      # shaft lands at this fraction of person width
UMB_SHAFT_W = 0.030      # drawn shaft width / umbrella width
SHAFT_RGB = (0, 27, 68)


def sky(w, h, top=(13, 32, 64), bottom=(38, 84, 124)):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(
            round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.convert("RGBA")


def paste_rot(base, img, pivot_xy, angle_deg, place_xy):
    """Rotate img about pivot_xy (px in img coords) and paste so the
    pivot lands on place_xy (px in base coords)."""
    rot = img.rotate(angle_deg, resample=Image.BICUBIC, expand=True)
    # where the pivot went after expand-rotation: recompute via a marker
    marker = Image.new("L", img.size, 0)
    md = ImageDraw.Draw(marker)
    md.ellipse([pivot_xy[0] - 2, pivot_xy[1] - 2, pivot_xy[0] + 2, pivot_xy[1] + 2], fill=255)
    mrot = marker.rotate(angle_deg, resample=Image.NEAREST, expand=True)
    bbox = mrot.getbbox()
    px, py = (bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2
    base.alpha_composite(rot, (round(place_xy[0] - px), round(place_xy[1] - py)))


def compose(rank, umbrella="NONE", hat=False):
    canvas = sky(W, H)
    person = RANKS[rank]
    ph = H
    pw = ph * person.size[0] / person.size[1]
    pl, pt = (W - pw) / 2, H - ph
    canvas.alpha_composite(person.resize((round(pw), round(ph)), Image.LANCZOS),
                           (round(pl), round(pt)))
    hx, hy = HAND_R[rank]
    hand = (pl + hx * pw, pt + hy * ph)
    if hat:
        hw = HAT_W_FRAC * pw
        hh = hw * HAT.size[1] / HAT.size[0]
        canvas.alpha_composite(
            HAT.resize((round(hw), round(hh)), Image.LANCZOS),
            (round(pl + HEAD_CX * pw - hw / 2), round(pt + HAT_BOTTOM_Y * ph - hh)))
    if umbrella == "FOLDED":
        fh = FOLD_H_FRAC * ph
        fw = fh * UMB_FOLD.size[0] / UMB_FOLD.size[1]
        canvas.alpha_composite(
            UMB_FOLD.resize((round(fw), round(fh)), Image.LANCZOS),
            (round(hand[0] + FOLD_DX * pw - fw / 2),
             round(hand[1] + FOLD_BOTTOM_DY * ph - fh)))
    if umbrella == "OPEN":
        uw = UMB_W_FRAC * pw
        uh = uw * UMB_OPEN.size[1] / UMB_OPEN.size[0]
        ul = pl + UMB_SHAFT_AT * pw - UMB_SHAFT_X * uw
        ut = UMB_TOP_Y * H
        # shaft extension first (the image's stub overlaps its top)
        d = ImageDraw.Draw(canvas)
        d.line([(ul + UMB_SHAFT_X * uw, ut + uh), hand],
               fill=SHAFT_RGB + (255,), width=max(2, round(UMB_SHAFT_W * uw)))
        canvas.alpha_composite(
            UMB_OPEN.resize((round(uw), round(uh)), Image.LANCZOS),
            (round(ul), round(ut)))
    return canvas.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = [
        ("preview_rank6_umbrella", compose(6, "OPEN")),
        ("preview_rank0_hat", compose(0, "NONE", hat=True)),
        ("preview_rank1_folding", compose(1, "FOLDED")),
        ("preview_rank3_umbrella", compose(3, "OPEN")),
        ("preview_rank2_hat", compose(2, "NONE", hat=True)),
    ]
    for name, img in jobs:
        p = f"{OUT}/{name}.png"
        img.save(p)
        print(p)


if __name__ == "__main__":
    main()
