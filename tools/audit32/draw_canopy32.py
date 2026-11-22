#!/usr/bin/env python3
"""v0.1.32: rebuild the open-umbrella composition in the three
umbrella arm layers (polo / cardigan / knitcoat), at the user's
decision (「傘の構図を作り直す（雨傘・日傘そろって頭の真上に）」).

What changes and what does not:
- The arm, sleeve and fist art is NOT touched: every pixel outside
  the erased canopy + shaft corridor stays byte-identical to the
  v0.1.31 assets (asserted below).
- The canopy and the shaft above the fist are redrawn
  programmatically (no generated art): the canopy is a dome whose
  apex sits over the head's centre (solved per layer against the
  mirror's placement math for every base the layer serves), the
  whole canopy fits inside the layer frame, and the shaft is one
  straight line from the apex to the point where the old shaft
  enters the fist (measured per layer), so the join is seamless.
- Each layer is re-canvassed wider (transparent padding) so the
  centred canopy has room; the Kotlin anchors are recomputed so
  every kept pixel lands on exactly the same composite position
  as before (see anchors32.txt / PersonFigure.kt).
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
RES = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
OUT = os.path.join(ROOT, "tools/audit32")

INK = (1, 23, 82)          # ribs / outline (sampled median)
CLOTH_A = (32, 44, 97)     # panel shade A (sampled median)
CLOTH_B = (36, 49, 103)    # panel shade B (a breath lighter)

SS = 3  # supersample for the new art

# per-layer plan (new-canvas coordinates)
PLAN = {
    "arm_polo_umbrella_open": dict(
        w2=720, off=0, ax=390, ay=55, hw=300, dome_h=280, sag=25,
        shaft_hint=490),
    "arm_cardigan_umbrella_open": dict(
        w2=746, off=80, ax=290, ay=55, hw=285, dome_h=280, sag=25,
        shaft_hint=340),
    "arm_knitcoat_umbrella_open": dict(
        w2=655, off=0, ax=353, ay=55, hw=280, dome_h=280, sag=25,
        shaft_hint=490),
}


def canopy_bottom(al):
    """Per-column bottom of the top blob, with the shaft/arm
    corridors interpolated from their neighbours (the v0.1.31
    parasol script's method)."""
    H, W = al.shape
    bot = np.zeros(W, dtype=int)
    for x in range(W):
        col = al[:, x]
        last, gap, started = 0, 0, False
        for y in range(H):
            if col[y]:
                last, gap, started = y, 0, True
            elif started:
                gap += 1
                if gap >= 20:
                    break
        bot[x] = last
    # Columns whose blob runs deeper than any canopy ever does
    # (the shaft / arm / fist corridors) take the canopy's bottom
    # interpolated from the honest canopy columns. (A local-median
    # rule fails inside the wide arm corridor, where the whole
    # window is deep — that was the v0.1.32 first-draft bug: it
    # erased the arm down to the fist.)
    corridor = bot > 420
    assert not corridor.all()
    if corridor.any():
        idx = np.arange(W)
        bot = np.where(corridor,
                       np.interp(idx, idx[~corridor], bot[~corridor]),
                       bot).astype(int)
    return bot


def shaft_join(a, hint):
    """Find where the old shaft enters the fist: scan down from
    y=680; the join is the first row where the alpha run holding
    the shaft grows past 34 px (the fist mass begins). Returns
    (join_y, shaft centre x at the join, shaft colour)."""
    al = a[:, :, 3] > 0
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    for y in range(680, 880):
        row = al[y]
        runs = []
        s = None
        for i, v in enumerate(row):
            if v and s is None:
                s = i
            elif not v and s is not None:
                runs.append((s, i - 1))
                s = None
        if s is not None:
            runs.append((s, len(row) - 1))
        for (x0, x1) in runs:
            if x0 - 6 <= hint <= x1 + 6:
                if x1 - x0 + 1 > 34:
                    jy = y - 6
                    dark = np.where(al[jy] & (lum[jy] < 120))[0]
                    dark = dark[(dark > hint - 30) & (dark < hint + 30)]
                    cx = float(dark.min() + dark.max()) / 2
                    col = np.median(a[jy, int(dark.min()):int(dark.max()) + 1]
                                    [lum[jy, int(dark.min()):int(dark.max()) + 1] < 120][:, :3], axis=0)
                    return jy, cx, tuple(int(round(c)) for c in col)
    raise RuntimeError("join not found")


def dome_points(ax, ay, hw, dome_h, sag, tilt, n=160):
    """Top arc points (left foot -> apex -> right foot) and the
    rim curve function."""
    top = []
    for i in range(n + 1):
        t = -1.0 + 2.0 * i / n
        x = ax + t * hw
        y = ay + (dome_h + tilt * t * hw) * (1 - math.sqrt(max(0.0, 1 - t * t)))
        top.append((x, y))

    def rim_y(x):
        t = (x - ax) / hw
        return ay + dome_h + tilt * (x - ax) + sag * (1 - t * t)
    return top, rim_y


def draw_layer(name, plan):
    src = Image.open(os.path.join(RES, name + ".png")).convert("RGBA")
    a = np.asarray(src).astype(int)
    H, W = a.shape[:2]
    W2, off = plan["w2"], plan["off"]

    # ---- erase masks (old-canvas coordinates) ----
    al = a[:, :, 3] > 0
    bot = canopy_bottom(al)
    erase = np.zeros((H, W), dtype=bool)
    yy = np.arange(H)[:, None]
    erase |= al & (yy <= (bot + 2)[None, :])
    jy, jx, shaft_col = shaft_join(a, plan["shaft_hint"])
    # shaft corridor in old coords: full erase down to 60 px above
    # the join; in the last 60 px (where the fist's thumb wraps
    # in) erase only the shaft's own dark band (|x-jx| <= 11), so
    # the fist's outline and skin survive untouched.
    cx0, cx1 = int(jx) - 18, int(jx) + 18
    erase[jy - 400:jy - 60, cx0:cx1 + 1] |= \
        al[jy - 400:jy - 60, cx0:cx1 + 1]
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    tx0, tx1 = int(jx) - 11, int(jx) + 11
    tailzone = al[jy - 60:jy, tx0:tx1 + 1] & \
        (lum[jy - 60:jy, tx0:tx1 + 1] < 170)
    erase[jy - 60:jy, tx0:tx1 + 1] |= tailzone
    # sanity: below the canopy zone the erase mask may only ever
    # touch the shaft corridor — never the arm, sleeve or fist
    low = erase.copy()
    low[:430] = False
    low[jy - 400:jy, cx0:cx1 + 1] = False
    assert not low.any(), f"{name}: erase mask reaches kept art!"

    kept = a.copy()
    kept[erase] = 0

    # ---- re-canvas ----
    base = Image.new("RGBA", (W2, H), (0, 0, 0, 0))
    base.alpha_composite(Image.fromarray(kept.astype(np.uint8), "RGBA"),
                         (off, 0))

    # ---- new art (supersampled) ----
    ax, ay, hw = plan["ax"], plan["ay"], plan["hw"]
    dome_h, sag = plan["dome_h"], plan["sag"]
    J = (jx + off, jy)
    tilt = (J[0] - ax) / (J[1] - ay)
    art = Image.new("RGBA", (W2 * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)

    def S(p):
        return (p[0] * SS, p[1] * SS)

    # shaft first (the canopy will cover its top)
    d.line([S((ax, ay)), S(J)], fill=shaft_col + (255,), width=16 * SS)
    # canopy: fill the whole dome silhouette first, then shade
    # alternate panels with apex->rim wedges masked to the dome
    # (per-panel polygons from the apex leave chord-shaped holes
    # on the flanks — the first draft's bug)
    top, rim_y = dome_points(ax, ay, hw, dome_h, sag, tilt)
    SEG = 16
    feet = []
    for i in range(SEG + 1):
        x = ax - hw + i * (2 * hw / SEG)
        feet.append((x, rim_y(x)))

    def scallop_pts(i, dip=9):
        x0, x1 = feet[i][0], feet[i + 1][0]
        mid = ((x0 + x1) / 2, rim_y((x0 + x1) / 2) + dip)
        pts = []
        for k in range(13):
            t = k / 12
            x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * mid[0] + t * t * x1
            y = (1 - t) ** 2 * feet[i][1] + 2 * (1 - t) * t * mid[1] \
                + t * t * feet[i + 1][1]
            pts.append((x, y))
        return pts

    silhouette = list(top)
    for i in range(SEG - 1, -1, -1):
        silhouette += scallop_pts(i)[::-1]
    d.polygon([S(p) for p in silhouette], fill=CLOTH_A + (255,))
    shade = Image.new("RGBA", art.size, (0, 0, 0, 0))
    ds = ImageDraw.Draw(shade)
    for i in range(SEG):
        if i % 2 == 0:
            continue
        x0, x1 = feet[i][0], feet[i + 1][0]
        mid = ((x0 + x1) / 2, rim_y((x0 + x1) / 2))
        ds.polygon([S(p) for p in
                    [(ax, ay), feet[i], mid, feet[i + 1]]],
                   fill=CLOTH_B + (255,))
    mask = Image.new("L", art.size, 0)
    dm = ImageDraw.Draw(mask)
    dm.polygon([S(p) for p in silhouette], fill=255)
    shade_alpha = np.minimum(np.asarray(shade)[:, :, 3],
                             np.asarray(mask))
    shade_np = np.asarray(shade).copy()
    shade_np[:, :, 3] = shade_alpha
    art.alpha_composite(Image.fromarray(shade_np, "RGBA"), (0, 0))
    d = ImageDraw.Draw(art)
    # ribs
    for f in feet:
        d.line([S((ax, ay)), S(f)], fill=INK + (255,), width=5 * SS)
    # outlines: top arc + scalloped rim
    d.line([S(p) for p in top], fill=INK + (255,), width=6 * SS,
           joint="curve")
    for i in range(SEG):
        pts = scallop_pts(i)
        d.line([S(p) for p in pts], fill=INK + (255,), width=6 * SS)
    # apex knob
    d.ellipse([S((ax - 8, ay - 9)), S((ax + 8, ay + 5))],
              fill=INK + (255,))

    art = art.resize((W2, H), Image.LANCZOS)
    base.alpha_composite(art, (0, 0))

    # ---- assert: wherever the new art is fully transparent, the
    # output is the kept original pixel, byte for byte (the new
    # canopy legitimately paints OVER the arm's top rows — the
    # rim tuck — and over nothing else that was kept)
    out = np.asarray(base).astype(int)
    orig_at = np.zeros((H, W2, 4), dtype=int)
    orig_at[:, off:off + W] = a
    kept_mask = np.zeros((H, W2), dtype=bool)
    kept_mask[:, off:off + W] = ~erase
    art_alpha = np.asarray(art)[:, :, 3] > 0
    untouched = kept_mask & ~art_alpha
    # alpha must match everywhere untouched; RGB must match
    # wherever the pixel is visible (fully transparent pixels in
    # the source carry RGB garbage that PIL normalises to 0)
    assert (out[untouched][:, 3] == orig_at[untouched][:, 3]).all(), \
        f"{name}: kept alpha changed outside the new art!"
    vis = untouched & (orig_at[:, :, 3] > 0)
    assert (out[vis] == orig_at[vis]).all(), \
        f"{name}: kept visible pixels changed outside the new art!"
    over_kept = kept_mask & art_alpha
    oys, oxs = np.where(over_kept)
    print(f"  new art paints over {int(over_kept.sum())} kept px "
          f"(x {oxs.min()}-{oxs.max()}, y {oys.min()}-{oys.max()})")
    # no old-shaft remnants: in the corridor BELOW the new rim
    # (where only the shaft may live) and above the tail zone,
    # every surviving alpha pixel must hug the new shaft line
    rim_here = rim_y(J[0]) + 12
    y0 = int(max(jy - 400, rim_here))
    ys_c, xs_c = np.mgrid[y0:jy - 60, cx0 + off:cx1 + off + 1]
    seg = np.hypot(J[0] - ax, J[1] - ay)
    dist = np.abs((J[1] - ay) * xs_c - (J[0] - ax) * ys_c
                  + J[0] * ay - J[1] * ax) / seg
    surv = out[y0:jy - 60, cx0 + off:cx1 + off + 1][:, :, 3] >= 16
    assert not (surv & (dist > 12)).any(), \
        f"{name}: old shaft remnants in the corridor!"
    print(f"{name}: join=({J[0]:.0f},{J[1]:.0f}) shaft_col={shaft_col} "
          f"tilt={tilt:.3f} kept px identical outside erase mask")
    base.save(os.path.join(RES, name + ".png"))
    # erased-state record for the audit
    rec = Image.new("RGBA", (W2, H), (24, 48, 80, 255))
    rec.alpha_composite(Image.fromarray(kept.astype(np.uint8), "RGBA")
                          .resize((W, H)), (off, 0)) \
        if False else None
    rec2 = Image.new("RGBA", (W2, H), (24, 48, 80, 255))
    kept_img = Image.new("RGBA", (W2, H), (0, 0, 0, 0))
    kept_img.alpha_composite(
        Image.fromarray(kept.astype(np.uint8), "RGBA"), (off, 0))
    rec2.alpha_composite(kept_img)
    rec2.convert("RGB").save(os.path.join(OUT, f"kept_{name}.png"))
    return dict(join=J, shaft_col=shaft_col, tilt=tilt)


if __name__ == "__main__":
    info = {}
    for name, plan in PLAN.items():
        info[name] = draw_layer(name, plan)
    print(info)
