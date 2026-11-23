#!/usr/bin/env python3
"""v0.1.33: build the PARASOL variants of the three whole-picture
umbrella portraits (rank 5 / rank 6 / raincoat) from their approved
OPEN variants — the same rule as the arm layers, one level up:
the figure, arm, fist/mitten and clothes keep every pixel; only
the umbrella itself (canopy + shaft + hook) is repainted in the
parasol family's colours.

The umbrella is masked spatially, per picture:
- canopy: per-column rim curve — the bottom of the top alpha blob
  where the canopy floats free of the figure (most columns),
  hand-set points where it touches the hair (rank 5's left tip,
  rank 6's shaft column), interpolated between;
- shaft: a tight corridor around the measured shaft line; where
  the shaft runs in front of the hair (ranks 5/6) only ink-dark
  pixels (lum < 55) are taken — the shaft is ink, the hair is
  lighter slate, so the hair survives;
- raincoat hook: a small box right of the fist.

Colour: the portraits are painterly (mottled shading), so the
arm layers' nearest-anchor repaint flips between anchors and
speckles. The variants use a smooth luminance ramp through the
parasol family's measured stops (ink (70,95,150) at lum 20 up to
near-white at lum 100), keeping 30% of each pixel's own deviation
from grey so the painted shading survives as shading.

Asserts: alpha identical; every changed pixel passed the navy
test; the figure's face sample points unchanged.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res/drawable-nodpi"
OUT = ROOT / "tools/audit33"

STOPS_LUM = np.array([20, 38, 52, 68, 100], dtype=float)
STOPS_RGB = np.array([
    [70, 95, 150],
    [120, 155, 205],
    [190, 215, 240],
    [225, 233, 245],
    [238, 244, 251],
], dtype=float)


def variant_recolor(px):
    r, g, b = px
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    base = np.array([np.interp(lum, STOPS_LUM, STOPS_RGB[:, c]) for c in range(3)])
    dev = (np.array([r, g, b], dtype=float) - lum) * 0.30
    return tuple(int(min(255, max(0, round(v)))) for v in base + dev)


def load(name):
    return np.asarray(Image.open(RES / f"{name}.png").convert("RGBA")).astype(int)


def navy_and_lum(a):
    rgb = a[:, :, :3]
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    navy = (
        (rgb[:, :, 2] > rgb[:, :, 0] + 25)
        & (rgb[:, :, 2] >= rgb[:, :, 1] + 15)
        & (lum < 150)
        & (a[:, :, 3] > 0)
    )
    return navy, lum


def top_blob_bottoms(a):
    H, W, _ = a.shape
    solid = a[:, :, 3] >= 128
    tops = np.full(W, -1)
    bots = np.full(W, -1)
    for x in range(W):
        ys = np.where(solid[:, x])[0]
        if len(ys) == 0:
            continue
        y0 = ys[0]
        y1 = y0
        for y in ys[1:]:
            if y <= y1 + 2:
                y1 = y
            else:
                break
        tops[x] = y0
        bots[x] = y1
    return tops, bots


def rim_curve(a, x_range, hand):
    W = a.shape[1]
    tops, bots = top_blob_bottoms(a)
    xs_known, ys_known = [], []
    for x in range(x_range[0], x_range[1] + 1):
        if tops[x] >= 0 and tops[x] <= 150 and bots[x] - tops[x] <= 200:
            xs_known.append(x)
            ys_known.append(bots[x])
    for x, y in hand:
        xs_known.append(x)
        ys_known.append(y)
    order = np.argsort(xs_known)
    xs_known = np.array(xs_known)[order]
    ys_known = np.array(ys_known)[order]
    rim = np.full(W, -1)
    rim[x_range[0]:x_range[1] + 1] = np.interp(
        np.arange(x_range[0], x_range[1] + 1), xs_known, ys_known)
    return rim


def corridor(H, W, p0, p1, half):
    yy, xx = np.mgrid[0:H, 0:W]
    x0, y0 = p0
    x1, y1 = p1
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / L2, 0, 1)
    px, py = x0 + t * dx, y0 + t * dy
    return np.hypot(xx - px, yy - py) <= half


def build(src_name, out_name, x_range, hand, shafts, boxes, face_pts,
          deep_tip=None):
    a = load(src_name)
    H, W, _ = a.shape
    navy, lum = navy_and_lum(a)
    rim = rim_curve(a, x_range, hand)
    yy, xx = np.mgrid[0:H, 0:W]
    mask = navy & (rim[xx] >= 0) & (yy <= rim[xx] + 3)
    if deep_tip is not None:
        tx0, tx1, extra = deep_tip
        mask |= navy & (lum < 45) & (xx >= tx0) & (xx <= tx1) \
            & (rim[xx] >= 0) & (yy <= rim[xx] + extra)
    for p0, p1, half, lum_cap in shafts:
        c = corridor(H, W, p0, p1, half) & navy
        if lum_cap is not None:
            c &= lum < lum_cap
        mask |= c
    for (bx0, bx1, by0, by1) in boxes:
        box = np.zeros_like(navy)
        box[by0:by1 + 1, bx0:bx1 + 1] = True
        mask |= box & navy
    out = a.copy()
    ys, xs = np.where(mask)
    for y, x in zip(ys, xs):
        out[y, x, :3] = variant_recolor(tuple(int(v) for v in a[y, x, :3]))
    assert np.array_equal(a[:, :, 3], out[:, :, 3]), f"{out_name}: alpha changed"
    changed = np.any(a[:, :, :3] != out[:, :, :3], axis=2)
    assert not np.any(changed & ~navy), f"{out_name}: non-navy pixel changed"
    for (fx, fy) in face_pts:
        assert np.array_equal(a[fy, fx], out[fy, fx]), \
            f"{out_name}: face pixel {(fx, fy)} changed"
    print(f"{out_name}: {int(changed.sum())} px repainted, alpha identical, "
          f"face samples unchanged")
    ov = a.copy()
    ov[mask] = [255, 0, 0, 255]
    Image.fromarray(ov.astype(np.uint8), "RGBA").save(OUT / f"mask_{out_name}.png")
    Image.fromarray(out.astype(np.uint8), "RGBA").save(RES / f"{out_name}.png")
    print(f"saved {out_name}.png {W}x{H}")


# rank 5: canopy floats right, its left tip reaching x≈250; hand
# rim points along the tip where the blob merges with the hair.
# The shaft is the VERTICAL ink bar at x 309-317 (measured: dark
# run continuous y 1-280 at x=313, colour (19,29,96)); it passes
# through the fist (skin blob x 303-353, y 282-329) and stubs out
# below it to y≈347. The drifting dark run at x 342-363 is hair /
# arm outline — NOT the shaft — and stays.
build(
    "rank5_umbrella_open", "rank5_umbrella_parasol",
    x_range=(228, 452),
    hand=[(228, 105), (233, 112), (238, 118), (246, 128), (252, 134),
          (258, 136), (264, 128), (270, 112), (276, 120), (286, 141),
          (294, 133)],
    shafts=[((313, 128), (313, 300), 7, 55)],
    boxes=[(305, 323, 300, 350)],
    face_pts=[(200, 300), (226, 330)],
    deep_tip=(252, 278, 16),
)
# rank 6: canopy spans the top; the shaft column (x≈280) merges
# with the figure blob — hand rim there. Shaft (271,125)->(276,255)
# in front of the hair, ink-dark only; the mitten (lum ≈ 77) and
# hair (lum ≈ 74) stay.
build(
    "rank6_umbrella_open", "rank6_umbrella_parasol",
    x_range=(108, 446),
    hand=[(108, 140), (280, 126)],
    shafts=[((271, 122), (277, 256), 8, 55)],
    boxes=[],
    face_pts=[(190, 320), (223, 350)],
)
# raincoat: canopy floats free in every column. Shaft measured per
# row: (304,200)->(317,320), then the grip; hook box right of the
# fist, capped above the raised sleeve.
build(
    "raincoat_umbrella_open", "raincoat_umbrella_parasol",
    x_range=(0, 394),
    hand=[],
    shafts=[((303, 160), (318, 348), 8, None)],
    boxes=[(298, 348, 330, 375), (346, 380, 328, 382)],
    face_pts=[(160, 330), (197, 360)],
)
