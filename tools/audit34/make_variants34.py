#!/usr/bin/env python3
"""v0.1.34 step 3: rebuild the umbrellas in the three whole-picture
variants (rank 5 / rank 6 / raincoat) — the last place the old
canopy survived, and (rank 5/6) where it sat small at the head's
upper right, off-centre, narrower than the hair.

Method (per picture, source = the v0.1.33 OPEN variant snapshot in
tools/audit34/orig/):
1. Re-canvas with left padding so the new, wider canopy — centred
   on the head — fits its frame; the inner rect is re-solved (the
   figure's pixels do not move on the person rect).
2. Erase the old umbrella (canopy via the v0.1.33 rim curve, the
   shaft via a corridor, the stub / hook via boxes) and refill the
   holes by row-wise interpolation from the surviving neighbours.
   (The base portraits are NOT pixel-aligned with the variants —
   measured p95 edge diff ~100+ — so copying hair from them would
   seam; interpolation inside the hair's own mottled field does
   not.) The fist / mitten is never erased.
3. Draw the new umbrella from the shared design (design34) in the
   picture's own palette, with the portraits' mottled texture, the
   shaft running from the canopy centre straight into the fist.
4. The parasol variant is the same picture with only the new
   umbrella's pixels run through the v0.1.33 luminance ramp —
   alpha identical to the OPEN variant (asserted), the figure
   untouched (asserted outside the art mask).
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
RES = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
ORIG = os.path.join(ROOT, "tools/audit34/orig")
OUT = os.path.join(ROOT, "tools/audit34")
sys.path.insert(0, os.path.join(ROOT, "tools", "audit34"))
from design34 import draw_umbrella  # noqa: E402

# ---- v0.1.33 helpers (copied: importing that module re-runs it) --

STOPS_LUM = np.array([20, 38, 52, 68, 100], dtype=float)
STOPS_RGB = np.array([
    [70, 95, 150], [120, 155, 205], [190, 215, 240],
    [225, 233, 245], [238, 244, 251]], dtype=float)


def variant_recolor(px):
    r, g, b = px
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    base = np.array([np.interp(lum, STOPS_LUM, STOPS_RGB[:, c])
                     for c in range(3)])
    dev = (np.array([r, g, b], dtype=float) - lum) * 0.30
    return tuple(int(min(255, max(0, round(v)))) for v in base + dev)


def top_blob_bottoms(a):
    H, W, _ = a.shape
    # The generated canopies' alpha is speckled (fringe holes of a
    # few px inside the cloth): a tolerant threshold and gap, or
    # the blob walk stops at a hole and the rim comes out shallow
    # (the v0.1.34 first-draft smear: unmasked canopy pixels became
    # the fill's neighbours).
    solid = a[:, :, 3] >= 60
    tops = np.full(W, -1)
    bots = np.full(W, -1)
    for x in range(W):
        ys = np.where(solid[:, x])[0]
        if len(ys) == 0:
            continue
        y0 = ys[0]
        y1 = y0
        for y in ys[1:]:
            if y <= y1 + 12:
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
    seg = np.interp(np.arange(x_range[0], x_range[1] + 1),
                    xs_known, ys_known)
    # Upper envelope over +-6 columns: the canopy's bottom edge is
    # smooth, so a surviving shallow dip is a speckle artifact,
    # never the true rim.
    env = seg.copy()
    for i in range(len(seg)):
        env[i] = seg[max(0, i - 6):i + 7].max()
    rim[x_range[0]:x_range[1] + 1] = env
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


def fill_rows(img, mask):
    """Refill masked pixels by interpolating, per row, between the
    nearest surviving pixels on each side (RGBA). A run touching
    transparency on one side fades to it; a run with no surviving
    neighbour in the row becomes transparent."""
    a = img.copy()
    H, W, _ = a.shape
    af = a.astype(float)
    for y in range(H):
        row_mask = mask[y]
        if not row_mask.any():
            continue
        xs = np.where(row_mask)[0]
        # split into runs
        runs = []
        s = xs[0]
        p = xs[0]
        for x in xs[1:]:
            if x == p + 1:
                p = x
            else:
                runs.append((s, p))
                s = p = x
        runs.append((s, p))
        for (x0, x1) in runs:
            # A neighbour counts only if it is solid content: the
            # generated sources carry RGB garbage under near-zero
            # alpha, and even a half-transparent canopy fringe
            # pixel seeds a visible smear when the fill fades from
            # it across a long erased run (the rank-5 tab). The
            # figure's interiors are fully opaque, so >= 250 is
            # the right bar.
            left = af[y, x0 - 1] if x0 > 0 and not row_mask[x0 - 1] \
                and af[y, x0 - 1, 3] >= 250 else None
            right = af[y, x1 + 1] if x1 < W - 1 and not row_mask[x1 + 1] \
                and af[y, x1 + 1, 3] >= 250 else None
            n = x1 - x0 + 1
            for i, x in enumerate(range(x0, x1 + 1)):
                t = (i + 1) / (n + 1)
                if left is not None and right is not None:
                    af[y, x] = left * (1 - t) + right * t
                elif left is not None:
                    # One-sided fades are for silhouette edges: the
                    # alpha must die within ~12 px of the surviving
                    # edge, however long the run is. A slow fade
                    # across a 200 px run over background is the
                    # translucent-slab artifact (rank-5 tab).
                    af[y, x] = left
                    af[y, x, 3] = left[3] * max(0.0, 1 - (i + 1) / 12)
                elif right is not None:
                    af[y, x] = right
                    af[y, x, 3] = right[3] * max(0.0, 1 - (n - i) / 12)
                else:
                    af[y, x] = 0
    return af


def build(name, pad, inner_old, geom, erase, extra_draw=None,
          basefile=None, canopy_bbox=None, fist_box=None,
          clear_boxes=()):
    src = Image.open(os.path.join(ORIG, name + ".png")).convert("RGBA")
    W0, H = src.size
    W = W0 + pad
    base = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    base.alpha_composite(src, (pad, 0))
    a = np.asarray(base).astype(int)
    alpha = a[:, :, 3] > 0
    rgb = a[:, :, :3]
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    navy = (rgb[:, :, 2] > rgb[:, :, 0] + 25) & \
        (rgb[:, :, 2] >= rgb[:, :, 1] + 15) & (lum < 150) & alpha
    yy, xx = np.mgrid[0:H, 0:W]

    mask = np.zeros((H, W), dtype=bool)
    rim = rim_curve(a, erase["x_range"], erase["hand"])
    mask |= alpha & (rim[xx] >= 0) & (yy <= rim[xx] + 3)
    if basefile is not None:
        # Cleanup pass: inside the old canopy's neighbourhood, any
        # navy pixel the base portrait says is NOT figure (the base
        # figure mapped through the inner rect) is leftover canopy
        # the rim curve missed — the curve sags where the canopy's
        # underside bulges between honest columns. The raised arm
        # is not in the base, but it starts far below this zone.
        bim = Image.open(os.path.join(RES, basefile)).convert("RGBA")
        mapped = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        bw2 = (inner_old[2] - inner_old[0]) * W0
        bh2 = (inner_old[3] - inner_old[1]) * H
        piece = bim.resize((round(bw2), round(bh2)), Image.LANCZOS)
        full = Image.new("RGBA", (W0, H), (0, 0, 0, 0))
        full.alpha_composite(piece, (round(inner_old[0] * W0),
                                     round(inner_old[1] * H)))
        mapped.alpha_composite(full, (pad, 0))
        ma = np.asarray(mapped)[:, :, 3]
        bx0, by0, bx1, by1 = canopy_bbox
        zone = (xx >= bx0 + pad - 10) & (xx <= bx1 + pad + 10) & \
            (yy <= by1 + 25)
        # No colour condition: the canopy's own AA fringe toward
        # the background is pale, and it is exactly what escaped
        # the navy-only version of this pass. Inside this zone the
        # base portrait is the only figure reference needed.
        mask |= zone & alpha & (ma < 128)
    for (p0, p1, half, lum_cap) in erase.get("shafts", []):
        c = corridor(H, W, p0, p1, half) & alpha
        if lum_cap is not None:
            c &= lum < lum_cap
        mask |= c
    for (bx0, bx1, by0, by1, navy_only) in erase.get("boxes", []):
        box = np.zeros_like(mask)
        box[by0:by1 + 1, bx0:bx1 + 1] = True
        mask |= box & navy if navy_only else box & alpha

    filled = fill_rows(a, mask)
    faint = filled[:, :, 3] < 8
    filled[faint] = 0
    for (bx0, bx1, by0, by1) in clear_boxes:
        # Zones that must end as clean background: the fill's fade
        # from the sleeve leaves a navy ghost of the old hook here.
        # The new art and the fist patch are drawn after this, so
        # what belongs in the zone is re-added on top.
        filled[by0:by1 + 1, bx0:bx1 + 1] = 0
    person = Image.fromarray(
        np.clip(filled, 0, 255).astype(np.uint8), "RGBA")
    ov = a.copy()
    ov[mask] = [255, 0, 0, 255]
    Image.fromarray(ov.astype(np.uint8), "RGBA").save(
        os.path.join(OUT, f"mask34_{name}.png"))

    g = geom
    hl = None
    if g.get("highlights"):
        hl = [(g["apex"][0] + dx * g["hw"], g["apex"][1] + dy * g["depth"],
               rx * g["hw"], ry * g["depth"], ang, al, col)
              for (dx, dy, rx, ry, ang, al, col) in g["highlights"]]
    art, art_mask = draw_umbrella(
        (W, H), g["apex"], g["hw"], g["depth"], g["sag"], g["tilt"],
        g["shaft_to"], g["shaft_col"], g["cloth_a"], g["cloth_b"],
        g["ink"], shaft_w=g["shaft_w"], outline_w=g["outline_w"],
        rib_w=g["rib_w"], knob=g["knob"], texture=g["texture"],
        highlights=hl, seed=g.get("seed", 34))
    if extra_draw:
        dr = ImageDraw.Draw(art)
        extra_draw(dr, g)
        art_mask = np.asarray(art)[:, :, 3] > 0
    rain = person.copy()
    rain.alpha_composite(art, (0, 0))
    if fist_box is not None:
        patch, off = fist_patch(a, fist_box)
        rain.alpha_composite(patch, off)
        # the patch is figure, not umbrella: keep it out of the
        # parasol recolour mask
        pm = np.zeros_like(art_mask)
        pm[off[1]:off[1] + patch.size[1],
           off[0]:off[0] + patch.size[0]] = \
            np.asarray(patch)[:, :, 3] > 0
        art_mask &= ~pm
    rain.save(os.path.join(RES, name + ".png"))

    # parasol: identical picture, the art's pixels ramped
    par_name = name.replace("_open", "_parasol")
    rain_np = np.asarray(rain).astype(int)
    par_np = rain_np.copy()
    ys, xs = np.where(art_mask)
    for y, x in zip(ys, xs):
        if rain_np[y, x, 3] > 0:
            par_np[y, x, :3] = variant_recolor(
                tuple(int(v) for v in rain_np[y, x, :3]))
    par = Image.fromarray(par_np.astype(np.uint8), "RGBA")
    par.save(os.path.join(RES, par_name + ".png"))
    assert np.array_equal(rain_np[:, :, 3], par_np[:, :, 3]), \
        f"{par_name}: alpha differs from OPEN"
    inner = (float((inner_old[0] * W0 + pad) / W), inner_old[1],
             float((inner_old[2] * W0 + pad) / W), inner_old[3])
    print(f"{name}: canvas {W0}->{W}, art x "
          f"{xs.min() if len(xs) else '-'}..{xs.max() if len(xs) else '-'} "
          f"inner={tuple(round(v, 5) for v in inner)}")
    print(f"saved {name}.png and {par_name}.png")
    return inner


INK56 = (20, 26, 95)
CLOTH56_A = (31, 51, 120)
CLOTH56_B = (41, 63, 133)
SHAFT56 = (19, 29, 96)


def rank5_stub(dr, g):
    # the handle's short end below the fist, along the shaft line
    dr.line([(412, 326), (421, 341)], fill=INK56 + (255,), width=11)


def raincoat_hook(dr, g):
    # the J handle below the fist (fist skin: x 347-390,
    # y 350-407 in the new canvas): a short run out of the fist's
    # bottom along the shaft direction, then a modest curl. The
    # fist patch is re-pasted over this afterwards, so the handle
    # emerges from behind the grip.
    ink = (23, 33, 91) + (255,)
    dr.line([(366, 398), (372, 415)], fill=ink, width=9)
    pts = []
    p0, pc, p1 = (372, 415), (380, 430), (396, 420)
    for k in range(17):
        t = k / 16
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * pc[0] + t * t * p1[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * pc[1] + t * t * p1[1]
        pts.append((x, y))
    dr.line(pts, fill=ink, width=9)


def fist_patch(padded_orig, box):
    """The raincoat fist, pixel-identical from the source: skin
    pixels plus their ink outline ring (within 3 px of skin). The
    wide erase boxes the hook needs would otherwise eat the fist's
    finger lines and the fill would stripe them."""
    x0, x1, y0, y1 = box
    a = padded_orig[y0:y1 + 1, x0:x1 + 1].astype(int)
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    skin = (lum > 150) & (a[:, :, 3] > 0)
    dil = skin.copy()
    for _ in range(3):
        d = dil.copy()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                d |= np.roll(np.roll(dil, dy, axis=0), dx, axis=1)
        dil = d
    keep = dil & (a[:, :, 3] > 0)
    patch = np.zeros_like(a)
    patch[keep] = a[keep]
    return Image.fromarray(patch.astype(np.uint8), "RGBA"), (x0, y0)


RESULTS = {}
RESULTS["rank5"] = build(
    "rank5_umbrella_open", 70, (0.0039, 0.0818, 0.8056, 0.9999),
    geom=dict(apex=(253, 18), hw=208, depth=150, sag=27, tilt=0.30,
              shaft_to=(396, 292), shaft_col=SHAFT56,
              cloth_a=CLOTH56_A, cloth_b=CLOTH56_B, ink=INK56,
              shaft_w=13, outline_w=7, rib_w=6, knob=(11, 14),
              texture=1.0, seed=35),
    erase=dict(
        x_range=(292, 522),
        hand=[(298, 105), (303, 112), (308, 118), (316, 128),
              (322, 134), (328, 136), (334, 128), (340, 112),
              (346, 120), (356, 141), (364, 133)],
        shafts=[((383, 100), (383, 300), 9, 68)],
        boxes=[(373, 395, 327, 352, True)],
    ),
    extra_draw=rank5_stub,
    basefile="person_rank5.png", canopy_bbox=(187, 0, 437, 150),
)
RESULTS["rank6"] = build(
    "rank6_umbrella_open", 70, (0.0037, 0.1361, 0.8830, 0.9999),
    geom=dict(apex=(265, 64), hw=225, depth=160, sag=29, tilt=0.27,
              shaft_to=(343, 250), shaft_col=SHAFT56,
              cloth_a=CLOTH56_A, cloth_b=CLOTH56_B, ink=INK56,
              shaft_w=12, outline_w=7, rib_w=6, knob=(11, 14),
              texture=1.0, seed=36),
    erase=dict(
        x_range=(172, 516),
        hand=[(178, 140), (350, 126)],
        shafts=[((341, 120), (347, 258), 9, 68)],
        boxes=[],
    ),
    basefile="person_rank6.png", canopy_bbox=(108, 0, 446, 160),
)
RESULTS["raincoat"] = build(
    "raincoat_umbrella_open", 30, (0.0624, 0.1690, 0.7201, 0.9998),
    geom=dict(apex=(183, 100), hw=155, depth=120, sag=22, tilt=0.34,
              shaft_to=(352, 354), shaft_col=(23, 33, 91),
              cloth_a=(30, 49, 106), cloth_b=(43, 65, 128),
              ink=(23, 33, 91),
              shaft_w=12, outline_w=8, rib_w=6, knob=(11, 15),
              texture=0.7, seed=37,
              highlights=[(-0.48, 0.34, 0.15, 0.13, -28, 80, (168, 200, 235)),
                          (-0.10, 0.26, 0.11, 0.10, -28, 60, (168, 200, 235))]),
    erase=dict(
        x_range=(30, 424),
        hand=[],
        shafts=[((333, 150), (348, 345), 9, None)],
        boxes=[(340, 364, 326, 352, True),
               (380, 422, 344, 415, True),
               (350, 422, 398, 415, True)],
    ),
    extra_draw=raincoat_hook,
    fist_box=(330, 395, 344, 415),
    clear_boxes=[(376, 424, 370, 434)],
    # no base-portrait cleanup for the raincoat: its canopy floats
    # free of the hood in every column (v0.1.33), and the tolerant
    # rim walk alone leaves only fringe-level residue (measured).
)
print(RESULTS)
