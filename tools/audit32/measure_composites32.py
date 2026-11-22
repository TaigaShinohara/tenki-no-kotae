#!/usr/bin/env python3
"""v0.1.32 precise geometry check in the 760x900 audit frame,
using mirror_v0132's own placement math.

Layered ranks: canopy bbox measured on the arm layer, mapped
through arm_rect. Variants (rank 5/6, raincoat): canopy and head
measured on the variant picture (dark-blob components above /
around the face), mapped through the composer's inner-rect
transform.
"""
import importlib.util
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
RES = os.path.join(BASE, "app/src/main/res/drawable-nodpi")
spec = importlib.util.spec_from_file_location(
    "m32", os.path.join(BASE, "tools", "mirror_v0132.py"))
M = importlib.util.module_from_spec(spec)
spec.loader.exec_module(M)
W, H = 760, 900


def report(tag, can, apex_x, head_cx, head_w, face_cx, face_w):
    print(f"{tag}: canopy x {can[0]:.0f}-{can[1]:.0f} top {can[2]:.0f} "
          f"| margins L {can[0]:.0f} ({can[0] / W:.1%}) "
          f"R {W - can[1]:.0f} ({(W - can[1]) / W:.1%}) "
          f"T {can[2]:.0f} ({can[2] / W:.1%})")
    print(f"   apex {apex_x:.0f} vs head cx {head_cx:.0f}: "
          f"offset {apex_x - head_cx:+.0f} "
          f"(allow ±{0.35 * head_w:.0f} = 0.35 x head w {head_w:.0f}) "
          f"| covers head: {can[0] <= head_cx - head_w / 2 and can[1] >= head_cx + head_w / 2} "
          f"| face cx {face_cx:.0f} w {face_w:.0f}")


def layered(rank, held):
    s = M.spec_for(rank, held=held)
    base = s["base"]
    rig = M.RIGS[base]
    pad = M.pad_top_ratio(s)
    ph = H / (1 + pad)
    pt = H - ph
    bw = ph * M.body_aspect(base)
    bl = (W - bw) / 2
    name = M.arm_art(s["top"], held)
    rx, ry, dw, dh = M.arm_rect(name, rig, bl, pt, bw, ph)
    a = np.asarray(Image.open(os.path.join(RES, name + ".png"))
                   .convert("RGBA")).astype(int)
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    dark = (a[:, :, 3] > 0) & (lum < 130) & (a[:, :, 2] > a[:, :, 0])
    dark[430:] = False  # above the arm / sleeve
    ys, xs = np.where(dark)
    sx, sy = dw / a.shape[1], dh / a.shape[0]
    can = (rx + xs.min() * sx, rx + xs.max() * sx, ry + ys.min() * sy)
    apex_x = rx + xs[np.argmin(ys)] * sx
    face_cx = bl + rig[0] * bw
    face_w = (rig[4] - rig[3]) * bw
    report(f"rank{rank} {held} ({name})", can, apex_x,
           bl + bw / 2, bw, face_cx, face_w)


def components(mask):
    """Label 4-connected components; yield (size, xs, ys)."""
    seen = np.zeros(mask.shape, dtype=bool)
    out = []
    ys, xs = np.where(mask)
    for y0, x0 in zip(ys, xs):
        if seen[y0, x0]:
            continue
        q = deque([(y0, x0)])
        seen[y0, x0] = True
        pts = []
        while q:
            y, x = q.popleft()
            pts.append((x, y))
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < mask.shape[0] and 0 <= nx < mask.shape[1] \
                        and mask[ny, nx] and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
        arr = np.array(pts)
        out.append((len(pts), arr[:, 0], arr[:, 1]))
    return out


def variant(tag, s):
    vname, inner = M.variant_of(s["base"], s.get("top"), s.get("held"))
    pad = M.pad_top_ratio(s)
    ph = H / (1 + pad)
    pt = H - ph
    bw = ph * M.body_aspect(s["base"])
    bl = (W - bw) / 2
    dw = bw / (inner[2] - inner[0])
    dh = ph / (inner[3] - inner[1])
    dl = bl - inner[0] * dw
    dt = pt - inner[1] * dh
    a = np.asarray(Image.open(os.path.join(RES, vname + ".png"))
                   .convert("RGBA")).astype(int)
    ih, iw = a.shape[:2]
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    skin = (a[:, :, 3] > 0) & (lum > 165) & (a[:, :, 0] > 150)
    sy, sx = np.where(skin)
    ftop = sy.min()
    dark = (a[:, :, 3] > 0) & (lum < 130) & (a[:, :, 2] > a[:, :, 0])
    above = dark.copy()
    above[ftop + 40:] = False
    comps = [c for c in components(above) if c[0] > 200]
    comps.sort(key=lambda c: c[2].min())
    _, cxs, cys = comps[0]  # topmost dark blob = the canopy
    # head band: dark around the face, excluding the canopy comp
    can_mask = np.zeros(dark.shape, dtype=bool)
    can_mask[cys, cxs] = True
    band = dark & ~can_mask
    band[:max(0, ftop - int(0.20 * ih))] = False
    band[sy.max() + 30:] = False
    by, bx = np.where(band)
    mx, my = dw / iw, dh / ih
    can = (dl + cxs.min() * mx, dl + cxs.max() * mx, dt + cys.min() * my)
    apex_x = dl + cxs[np.argmin(cys)] * mx
    head_cx = dl + (bx.min() + bx.max()) / 2 * mx
    head_w = (bx.max() - bx.min()) * mx
    face_cx = dl + (sx.min() + sx.max()) / 2 * mx
    face_w = (sx.max() - sx.min()) * mx
    report(tag, can, apex_x, head_cx, head_w, face_cx, face_w)


if __name__ == "__main__":
    for rank, held in [(0, "OPEN"), (1, "OPEN"), (2, "OPEN"),
                       (3, "OPEN"), (4, "OPEN"), (0, "PARASOL"),
                       (1, "PARASOL")]:
        layered(rank, held)
    variant("rank5 OPEN", M.spec_for(5, held="OPEN"))
    variant("rank6 OPEN", M.spec_for(6, held="OPEN"))
    variant("raincoat OPEN", M.spec_for(base="RAINCOAT", held="OPEN"))
