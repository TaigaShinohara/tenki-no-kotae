#!/usr/bin/env python3
"""v0.1.24 measurements: the neck towel, the mask, neck/chin/shoulder
geometry of every base, and the drawn shoulder line of every top.

All coordinates are BODY fractions (fractions of the drawn body
rect), matching PersonRigs / PersonCanvas in PersonFigure.kt.
Canvas-family parts are measured through the shared 1120x2240
canvas (measure23 helpers); portraits in their own fractions.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure23 as MS  # noqa: E402
import mirror_v0123 as M  # noqa: E402

RES = MS.RES
SKIN = MS.SKIN
HAIR = MS.HAIR


def spans(row_alpha):
    """Painted spans (x0, x1) of a boolean row, pixel coords."""
    xs = np.where(row_alpha)[0]
    if len(xs) == 0:
        return []
    out = []
    start = prev = xs[0]
    for x in xs[1:]:
        if x > prev + 1:
            out.append((start, prev))
            start = x
        prev = x
    out.append((start, prev))
    return out


# ---------- prop_towel ----------

def towel_report():
    im = MS.load("prop_towel")
    a = np.asarray(im).astype(float)
    alpha = a[..., 3] > 128
    H, W = alpha.shape
    ys, xs = np.where(alpha)
    print(f"== prop_towel asset {im.size} ==")
    print(f" alpha bbox: x {xs.min()/W:.3f}..{xs.max()/W:.3f}"
          f" y {ys.min()/H:.3f}..{ys.max()/H:.3f}"
          f" aspect h/w={H/W:.3f}")
    print(" row spans (asset fractions), every 0.04 of height:")
    for fy in np.arange(0.0, 1.001, 0.04):
        y = int(min(H - 1, fy * H))
        band = alpha[max(0, y - 4):y + 5].any(0)
        ss = spans(band)
        txt = " ".join(f"{s0/W:.3f}..{s1/W:.3f}" for s0, s1 in ss)
        print(f"   y={fy:.2f}: {txt}")
    # where does the centre gap open? scan rows for a centre column
    # (x 0.46..0.54) going empty while the sides stay painted.
    cx0, cx1 = int(0.44 * W), int(0.56 * W)
    gap_open = None
    for y in range(H):
        row = alpha[y]
        if row[cx0:cx1].mean() < 0.05 and row[:int(0.3 * W)].mean() > 0.5 \
                and row[int(0.7 * W):].mean() > 0.5:
            gap_open = y / H
            break
    print(f" centre gap opens at y={gap_open}")
    # panel bottoms: lowest painted row in left / right thirds
    for side, (fx0, fx1) in (("L", (0.02, 0.40)), ("R", (0.60, 0.98))):
        sub = alpha[:, int(fx0 * W):int(fx1 * W)]
        rows = np.where(sub.any(1))[0]
        print(f" panel {side}: bottom y={rows.max()/H:.3f}")
    print()


# ---------- prop_mask ----------

def mask_report():
    im = MS.load("prop_mask")
    a = np.asarray(im).astype(float)
    alpha = a[..., 3] > 128
    H, W = alpha.shape
    ys, xs = np.where(alpha)
    print(f"== prop_mask asset {im.size} ==")
    print(f" alpha bbox: x {xs.min()/W:.3f}..{xs.max()/W:.3f}"
          f" y {ys.min()/H:.3f}..{ys.max()/H:.3f}"
          f" aspect h/w={H/W:.3f}")
    # cloth = the big near-white component
    white = (a[..., 0] > 200) & (a[..., 1] > 200) & (a[..., 2] > 200) & alpha
    cls = MS.clusters(white, 500)
    if cls:
        n, x0, y0, x1, y1, cx, cy = cls[0]
        print(f" cloth (white) bbox: x {x0/W:.3f}..{x1/W:.3f}"
              f" y {y0/H:.3f}..{y1/H:.3f} npix={n}")
        print(f" cloth width share of asset: {(x1-x0)/W:.3f},"
              f" height share: {(y1-y0)/H:.3f}")
    # ear loops: alpha outside the cloth bbox, at mid height
    if cls:
        _, x0, y0, x1, y1, _, _ = cls[0]
        for side, sl in (("L", slice(0, x0)), ("R", slice(x1, W))):
            sub = alpha[:, sl]
            rows = np.where(sub.any(1))[0]
            cols = np.where(sub.any(0))[0]
            if len(rows):
                off = 0 if side == "L" else x1
                print(f" loop {side}: x {cols.min()+off if side=='R' else cols.min()}"
                      f"..{(cols.max()+off) if side=='R' else cols.max()}"
                      f" (frac {(cols.min()+(off if side=='R' else 0))/W:.3f}"
                      f"..{(cols.max()+(off if side=='R' else 0))/W:.3f})"
                      f" y {rows.min()/H:.3f}..{rows.max()/H:.3f}")
    # row spans at a few heights to see the cloth outline
    for fy in (0.08, 0.30, 0.55, 0.80, 0.97):
        y = int(min(H - 1, fy * H))
        band = alpha[max(0, y - 3):y + 4].any(0)
        ss = spans(band)
        txt = " ".join(f"{s0/W:.3f}..{s1/W:.3f}" for s0, s1 in ss)
        print(f"   y={fy:.2f}: {txt}")
    print()


# ---------- base neck / chin / shoulder geometry ----------

def center_skin_profile(alpha_skin, to_frac, label):
    """Skin rows at the centre columns: report the top cluster
    (face) bottom = chin, and where skin ends lower (neck bottom)."""
    H, W = alpha_skin.shape
    # centre columns in pixel coords come from the caller via mask
    col = alpha_skin
    rows = np.where(col.any(1))[0]
    if len(rows) == 0:
        print(f" {label}: no skin")
        return
    # clusters of rows
    groups = []
    start = prev = rows[0]
    for r in rows[1:]:
        if r > prev + 3:
            groups.append((start, prev))
            start = r
        prev = r
    groups.append((start, prev))
    print(f" {label} skin row groups (frac): " +
          " ".join(f"{to_frac(0, a)[1]:.3f}..{to_frac(0, b)[1]:.3f}"
                   for a, b in groups[:4]))


def body_neck_report(body):
    a = MS.canvas_arr(body)
    H, W = a.shape[:2]
    alpha = a[..., 3] > 128
    skin = (MS.dist(a, SKIN) < 60) & alpha
    b = MS.BOX[body]

    def bf(x, y):
        return MS.to_body((x / W, y / H), body)

    print(f"== {body} neck/chin/shoulder ==")
    # centre columns: body-frac x 0.47..0.53
    x0 = int((b[0] + 0.47 * (b[2] - b[0])) * W)
    x1 = int((b[0] + 0.53 * (b[2] - b[0])) * W)
    center_skin_profile(skin[:, x0:x1], bf, "centre")
    # shoulder tops: topmost alpha at body-frac x bands 0.26..0.34
    # and 0.66..0.74 (the shoulder points beside the neck)
    for side, (fx0, fx1) in (("L", (0.24, 0.36)), ("R", (0.64, 0.76))):
        sx0 = int((b[0] + fx0 * (b[2] - b[0])) * W)
        sx1 = int((b[0] + fx1 * (b[2] - b[0])) * W)
        sub = alpha[:, sx0:sx1]
        rows = np.where(sub.any(1))[0]
        # the shoulder = topmost alpha BELOW the hair: take the
        # second cluster of rows (hair first, torso second) — report
        # both cluster tops.
        groups = []
        if len(rows):
            start = prev = rows[0]
            for r in rows[1:]:
                if r > prev + 3:
                    groups.append((start, prev))
                    start = r
                prev = r
            groups.append((start, prev))
        print(f" shoulder {side} row groups: " +
              " ".join(f"{bf(0, g0)[1]:.3f}..{bf(0, g1)[1]:.3f}"
                       for g0, g1 in groups[:3]))
    # skin width at cheek / neck heights (for mask + towel widths)
    for fy in (0.27, 0.30, 0.33):
        y = int((b[1] + fy * (b[3] - b[1])) * H)
        row = np.where(skin[max(0, y - 3):y + 4].any(0))[0]
        if len(row):
            print(f" skin width at y={fy:.2f}: x {bf(row.min(),0)[0]:.3f}"
                  f"..{bf(row.max(),0)[0]:.3f}")
    print()


def portrait_neck_report(name):
    a = MS.arr(name)
    H, W = a.shape[:2]
    alpha = a[..., 3] > 128
    skin = (MS.dist(a, SKIN) < 60) & alpha
    print(f"== {name} neck/chin/shoulder (own fractions) ==")
    center_skin_profile(skin[:, int(0.47 * W):int(0.53 * W)],
                        lambda x, y: (x / W, y / H), "centre")
    # what sits at the centre just below the chin: sample mean RGB
    # down the centre column
    cx = int(0.5 * W)
    for fy in np.arange(0.30, 0.46, 0.02):
        y = int(fy * H)
        patch = a[max(0, y - 3):y + 4, cx - 3:cx + 4]
        m = patch.reshape(-1, 4)
        m = m[m[:, 3] > 128]
        if len(m):
            print(f"   centre y={fy:.2f}: rgb=({m[:,0].mean():.0f},"
                  f"{m[:,1].mean():.0f},{m[:,2].mean():.0f})")
    for fy in (0.27, 0.30, 0.33):
        y = int(fy * H)
        row = np.where(skin[max(0, y - 3):y + 4].any(0))[0]
        if len(row):
            print(f" skin width at y={fy:.2f}: x {row.min()/W:.3f}"
                  f"..{row.max()/W:.3f}")
    print()


# ---------- drawn tops: shoulder line vs body shoulder ----------

def top_shoulder_report():
    """For each top x body: the drawn top's topmost painted y at
    the shoulder bands (body-frac x 0.26..0.36 / 0.64..0.74), and
    its collar top (silhouette top near the centre)."""
    import audit23_gen as A23
    pairs = [("top_tshirt", "body_b"), ("top_shirt", "body_a"),
             ("top_cardigan", "body_a"), ("top_lightouter", "body_a"),
             ("top_knitcoat", "body_a"), ("top_down", "body_c")]
    for top, body in pairs:
        alpha, inv, (Hc, Wc) = A23.drawn_top_alpha(top, body)
        bb = MS.BOX[body]

        def topmost(fx0, fx1):
            best = None
            for fx in np.arange(fx0, fx1, 0.01):
                for fy in np.arange(0.15, 0.60, 0.002):
                    px, py = inv(fx, fy)
                    if 0 <= int(px) < Wc and 0 <= int(py) < Hc and \
                            alpha[int(py), int(px)]:
                        best = fy
                        break
                # keep the MINIMUM over the band = the highest point
            return best

        def band_top(fx0, fx1):
            vals = []
            for fx in np.arange(fx0, fx1, 0.01):
                for fy in np.arange(0.15, 0.60, 0.002):
                    px, py = inv(fx, fy)
                    if 0 <= int(px) < Wc and 0 <= int(py) < Hc and \
                            alpha[int(py), int(px)]:
                        vals.append(fy)
                        break
            return min(vals) if vals else None

        print(f"== {top} on {body}: drawn top edge ==")
        print(f"   shoulder L band top: {band_top(0.26, 0.36)}")
        print(f"   shoulder R band top: {band_top(0.64, 0.74)}")
        print(f"   centre band top (0.44..0.56): {band_top(0.44, 0.56)}")
    print()


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "towel"):
        towel_report()
    if what in ("all", "mask"):
        mask_report()
    if what in ("all", "necks"):
        for b in ("body_a", "body_b", "body_c"):
            body_neck_report(b)
        portrait_neck_report("person_rank5")
        portrait_neck_report("person_rank6")
        portrait_neck_report("outfit_raincoat")
    if what in ("all", "shoulders"):
        top_shoulder_report()
