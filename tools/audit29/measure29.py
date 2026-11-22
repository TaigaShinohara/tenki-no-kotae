#!/usr/bin/env python3
"""v0.1.29 measurement for the rank0/1 parasol & uchiwa portraits.

Anchors are INK-based, because the pale shirt sits within skin-
detector distance of the face colour and breaks skin blobs on
these layered bases: the two eyes + mouth are small ink clusters
with fixed geometry. kx comes from the eye spacing (the variant's
face width is the base's, scaled by the spacing ratio — the same
character redrawn), dx from the eye centre (= face centre, the
v0.1.28 symmetry), ky/dy from the eye-to-boots span. The base
crops (tools/portraits29/base_rankN.png) ARE the person rects, so
their rig face widths pin the spacing ratio.

Also reports, for the parasols, the canopy centre and shaft x
against the face centre (body-width units), and writes red/cyan
silhouette overlays (base vs mapped variant) for the eye check.
"""
import os
from collections import deque

import numpy as np
from PIL import Image

P29 = os.path.expanduser("~/workspace/tenki-no-kotae/tools/portraits29")
OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit29")
INK = np.array([0x1B, 0x25, 0x66], float)
RIG_FACE_W = {"base_rank0.png": 0.689 - 0.307,   # BODY_B rig
              "base_rank1.png": 0.747 - 0.248}   # BODY_A rig


def components(mask):
    h, w = mask.shape
    seen = np.zeros_like(mask)
    out = []
    ys, xs = np.where(mask)
    for y0, x0 in zip(ys.tolist(), xs.tolist()):
        if seen[y0, x0]:
            continue
        q = deque([(y0, x0)])
        seen[y0, x0] = True
        n = sx = sy = 0
        mnx = mxx = x0
        mny = mxy = y0
        while q:
            cy, cx = q.popleft()
            n += 1; sx += cx; sy += cy
            mnx = min(mnx, cx); mxx = max(mxx, cx)
            mny = min(mny, cy); mxy = max(mxy, cy)
            for ny, nx in ((cy-1, cx), (cy+1, cx), (cy, cx-1), (cy, cx+1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] \
                        and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
        out.append((n, mnx, mny, mxx, mxy, sx / n, sy / n))
    out.sort(key=lambda c: -c[0])
    return out


def anchors(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    a = np.asarray(im).astype(float)
    alpha = a[..., 3] > 128
    inkd = np.sqrt(((a[..., :3] - INK) ** 2).sum(axis=2))
    ink = (inkd < 60) & alpha
    cand = []
    for c in components(ink):
        n, x0, y0, x1, y1, cx, cy = c
        bw_, bh_ = x1 - x0 + 1, y1 - y0 + 1
        if 15 <= n <= 900 and bw_ < 0.07 * w and 0.35 < bw_ / bh_ < 2.8 \
                and 0.12 * h < cy < 0.62 * h and 0.15 * w < cx < 0.85 * w:
            cand.append(c)
    # eye pair: two candidates at the same height, apart horizontally
    best = None
    for i in range(len(cand)):
        for j in range(i + 1, len(cand)):
            ci, cj = cand[i], cand[j]
            if abs(ci[6] - cj[6]) < 0.012 * h \
                    and 0.05 * w < abs(ci[5] - cj[5]) < 0.34 * w:
                score = ci[0] + cj[0]
                if best is None or score > best[0]:
                    best = (score, ci, cj)
    assert best, f"no eye pair in {path}: cand={[(round(c[5]), round(c[6]), c[0]) for c in cand]}"
    _, e1, e2 = best
    eye_y = (e1[6] + e2[6]) / 2
    eye_cx = (e1[5] + e2[5]) / 2
    spacing = abs(e1[5] - e2[5])
    boots = np.where(alpha)[0].max()
    return dict(size=(w, h), eye_y=eye_y, eye_cx=eye_cx,
                spacing=spacing, boots=boots, alpha=alpha,
                rgba=np.asarray(im))


def inner_rect(b, v):
    bw, bh = b["size"]
    vw, vh = v["size"]
    kx = v["spacing"] / b["spacing"]
    ky = (v["boots"] - v["eye_y"]) / (b["boots"] - b["eye_y"])
    dx = v["eye_cx"] - kx * b["eye_cx"]
    dy = v["eye_y"] - ky * b["eye_y"]
    return (dx / vw, dy / vh, dx / vw + kx * bw / vw, dy / vh + ky * bh / vh)


def canopy_stats(v, inner):
    a = v["rgba"].astype(int)
    alpha = v["alpha"]
    h, w = alpha.shape
    # hair fill colour (#344B86 family) — the ink outlines and the
    # navy shorts sit >55 away from it, the canopy fill is pale.
    hd = np.sqrt(((a[..., :3] - np.array([52, 75, 134])) ** 2).sum(axis=2))
    hairm = (hd < 40) & alpha
    ht = None
    for c in components(hairm):
        if c[0] > 800 and c[2] < 0.6 * h:
            ht = c[2]
            break
    if ht is None:
        return None
    top = alpha.copy()
    top[ht:, :] = False
    ys, xs = np.where(top)
    if len(xs) < 100:
        return None
    canopy_bottom = ys.max()
    inkd = np.sqrt(((a[..., :3] - INK) ** 2).sum(axis=2))
    ink = (inkd < 70) & alpha
    band = ink[canopy_bottom:int(v["eye_y"]), :]
    cols = band.sum(axis=0)
    shaft_x = np.argmax(cols) / w if cols.max() > 20 else None
    iw = inner[2] - inner[0]
    ccx_m = (xs.mean() / w - inner[0]) / iw
    bcx_m = ((xs.min() + xs.max()) / 2 / w - inner[0]) / iw
    return ccx_m, bcx_m, (xs.max() - xs.min()) / w / iw, \
        ((shaft_x - inner[0]) / iw if shaft_x is not None else None)


def overlay(base_path, var_path, inner, out_name):
    b = Image.open(base_path).convert("RGBA")
    v = Image.open(var_path).convert("RGBA")
    W, H = 760, 900
    canvas = Image.new("RGBA", (W, H), (16, 28, 52, 255))
    # the person rect in this canvas: height H, width from the base
    # crop's own aspect, centred — the base crop IS the person rect.
    pw = H * b.size[0] / b.size[1]
    px = (W - pw) / 2
    red = Image.new("RGBA", b.size, (255, 70, 70, 255))
    red.putalpha(b.getchannel("A").point(lambda t: t // 2))
    canvas.alpha_composite(red.resize((round(pw), H), Image.LANCZOS),
                           (round(px), 0))
    iw, ih = inner[2] - inner[0], inner[3] - inner[1]
    dw, dh = pw / iw, H / ih
    vv = v.resize((round(dw), round(dh)), Image.LANCZOS)
    cyan = Image.new("RGBA", vv.size, (60, 220, 255, 255))
    cyan.putalpha(vv.getchannel("A").point(lambda t: t // 2))
    canvas.alpha_composite(cyan, (round(px - inner[0] * dw),
                                  round(-inner[1] * dh)))
    canvas.convert("RGB").save(os.path.join(OUT, out_name))


def measure(base_name, var_name, kind):
    b = anchors(os.path.join(P29, base_name))
    v = anchors(os.path.join(P29, var_name))
    inner = inner_rect(b, v)
    iw, ih = inner[2] - inner[0], inner[3] - inner[1]
    face_cx_b = b["eye_cx"] / b["size"][0]
    print(f"== {var_name}: inner=({inner[0]:.4f},{inner[1]:.4f},"
          f"{inner[2]:.4f},{inner[3]:.4f}) poke={inner[1]/ih:.4f}")
    print(f"   base eye spacing {b['spacing']:.1f}px / face "
          f"{RIG_FACE_W[base_name]*b['size'][0]:.1f}px; variant spacing "
          f"{v['spacing']:.1f}px")
    if kind == "parasol":
        ccx_m, bcx_m, cwid, sx_m = canopy_stats(v, inner)
        print(f"   canopy centroid {ccx_m:.3f} / bbox centre "
              f"{bcx_m:.3f} vs face cx {face_cx_b:.3f}: "
              f"{(bcx_m-face_cx_b)*100:+.1f}% bw (bbox); "
              f"canopy width {cwid:.2f} bw")
        if sx_m is not None:
            print(f"   shaft x mapped {sx_m:.3f}: "
                  f"{(sx_m-face_cx_b)*100:+.1f}% bw from face cx")
    overlay(os.path.join(P29, base_name), os.path.join(P29, var_name),
            inner, f"overlay_{var_name}")
    return inner


if __name__ == "__main__":
    for bn, vn, kind in [
            ("base_rank0.png", "keyed2_rank0_parasol.png", "parasol"),
            ("base_rank0.png", "keyed2_rank0_uchiwa.png", "uchiwa"),
            ("base_rank1.png", "keyed2_rank1_parasol.png", "parasol"),
            ("base_rank1.png", "keyed2_rank1_uchiwa.png", "uchiwa")]:
        p = os.path.join(P29, vn)
        if os.path.exists(p):
            measure(bn, vn, kind)
