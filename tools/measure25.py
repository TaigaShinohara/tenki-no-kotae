#!/usr/bin/env python3
"""v0.1.25 measurements.

Part 1 — the head layers (tools/layers_keyed/layer_head_*.png):
sizes, and the drawn geometry under the preview_layers.py rule
(face-anchored: scale = rig face width / layer face width, face cx
and face top onto the rig's) for every base — the layer top offset
(>0 means it pokes above the person top: the pad the composition
needs) and the layer bottom (where the layer's neck join lands).
Also locates the layer's own eye / mouth ink clusters to confirm
the by-eye HEAD_FACE boxes: after placement, the layer's eyes must
land on the rig's eye line.

Part 2 — the layered tops (ranks 2/3/4 on BODY_A, v0.1.24 TOPCAL):
silhouette edges of body vs drawn top per row, the shoulder gap,
and the collar peak (centre band) vs the outer's neckline
(shoulder bands) inside the drawn top.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0124 as M  # noqa: E402
import measure23 as MS  # noqa: E402
import audit23_gen as A23  # noqa: E402

LAY = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")

# HEAD_FACE from preview_layers.py (the placement source of truth):
# (left, top, right, bottom) of the face inside the layer image.
HEAD_FACE = {
    "layer_head_cap": (0.349, 0.557, 0.650, 0.975),
    "layer_head_knitcap": (0.306, 0.550, 0.689, 1.000),
}
ASPECT = {"BODY_A": 0.3962, "BODY_B": 0.3976, "BODY_C": 0.4494,
          "RANK5_PORTRAIT": 405 / 1024, "RANK6_PORTRAIT": 448 / 1024,
          "RAINCOAT": 0.3072}
INK = np.array([0x1B, 0x25, 0x66], float)


def head_layers():
    print("== layer files ==")
    for name in sorted(os.listdir(LAY)):
        im = Image.open(os.path.join(LAY, name))
        print(f"  {name}: {im.size[0]}x{im.size[1]} "
              f"{os.path.getsize(os.path.join(LAY, name))} bytes")
    print()
    for lname, (ll, lt, lr, lb) in HEAD_FACE.items():
        im = Image.open(os.path.join(LAY, lname + ".png")).convert("RGBA")
        iw, ih = im.size
        a = np.asarray(im).astype(float)
        alpha = a[..., 3] > 128
        ys, xs = np.where(alpha)
        print(f"== {lname} ({iw}x{ih}) alpha bbox "
              f"x {xs.min()/iw:.3f}..{xs.max()/iw:.3f} "
              f"y {ys.min()/ih:.3f}..{ys.max()/ih:.3f}")
        # eye / mouth ink clusters inside the face box (dark ink,
        # small clusters in the face's vertical span)
        ink = (MS.dist(a, INK) < 60) & alpha
        fx0, fy0 = int(ll * iw), int(lt * ih)
        fx1, fy1 = int(lr * iw), int(lb * ih)
        sub = ink[fy0:fy1, fx0:fx1]
        cls = [c for c in MS.clusters(sub, 12) if c[0] < 4000]
        feats = []
        for (n, x0, y0, x1, y1, cx, cy) in cls:
            feats.append(((fx0 + cx) / iw, (fy0 + cy) / ih, n))
        feats.sort(key=lambda f: f[1])
        print("   ink clusters in face box (x, y, npix): " +
              ", ".join(f"({x:.3f},{y:.3f},{n})" for x, y, n in feats))
        # drawn geometry per base
        for base, rig in M.RIGS.items():
            face_cx, face_top, face_bot, face_l, face_r = rig[:5]
            eye_y = M.FEATURES[base][0]
            bwr = ASPECT[base]
            dh_ph = (face_r - face_l) * bwr / (lr - ll) * (ih / iw)
            top = face_top - lt * dh_ph
            bottom = face_top + (lb - lt) * dh_ph
            # where the layer's eyes land: the two clusters nearest
            # the face's upper-middle (printed above); here just the
            # face-box mapping of the layer's eye band (lt + the
            # cluster offsets are printed for the record).
            print(f"   {base}: dh/ph={dh_ph:.3f} top={top:+.3f} "
                  f"bottom={bottom:.3f} (rig face bottom {face_bot:.3f}, "
                  f"eye line {eye_y:.3f})")
        print()


def top_edges(top_name, body_name):
    """Per-row silhouette edges (body fractions) of the body alone
    and of the drawn top, via audit23's inverse mapping."""
    ba = MS.canvas_arr(body_name)
    balpha = ba[..., 3] > 128
    Hc, Wc = balpha.shape[:2]
    bb = MS.BOX[body_name]
    alpha, inv, _ = A23.drawn_top_alpha(top_name, body_name)

    def row_edges(alpha_fn, fy):
        y = int((bb[1] + fy * (bb[3] - bb[1])) * Hc)
        row = alpha_fn(y)
        xs = np.where(row)[0]
        if len(xs) == 0:
            return None
        return ((xs.min() / Wc - bb[0]) / (bb[2] - bb[0]),
                (xs.max() / Wc - bb[0]) / (bb[2] - bb[0]))

    def top_row(y):
        row = np.zeros(Wc, bool)
        xs = np.arange(Wc)
        fy = (y / Hc - bb[1]) / (bb[3] - bb[1])
        px, py = inv((xs / Wc - bb[0]) / (bb[2] - bb[0]), fy)
        py = np.full(px.shape, py)
        ok = (px >= 0) & (px < Wc) & (py >= 0) & (py < Hc)
        row[ok] = alpha[np.clip(py[ok].astype(int), 0, Hc - 1),
                        np.clip(px[ok].astype(int), 0, Wc - 1)]
        return row

    print(f"== {top_name} x {body_name}: edges (body frac) ==")
    print("   y     body L..R        top L..R         excess L / R")
    for fy in (0.34, 0.36, 0.38, 0.40, 0.44, 0.48, 0.52, 0.56):
        b = row_edges(lambda y: balpha[max(0, y - 3):y + 4].any(0), fy)
        t = row_edges(top_row, fy)
        if b and t:
            print(f"   {fy:.2f}  {b[0]:.3f}..{b[1]:.3f}   "
                  f"{t[0]:.3f}..{t[1]:.3f}   "
                  f"{b[0]-t[0]:+.3f} / {t[1]-b[1]:+.3f}")
        else:
            print(f"   {fy:.2f}  body={b} top={t}")


def collar_profile(top_name, body_name):
    """Topmost drawn alpha row in x bands: centre (the inner
    shirt's collar peak) vs the shoulder bands (the outer's own
    neckline/shoulder start). Body fractions."""
    alpha, inv, (Hc, Wc) = A23.drawn_top_alpha(top_name, body_name)

    def band_top(fx0, fx1):
        vals = []
        for fx in np.arange(fx0, fx1, 0.005):
            for fy in np.arange(0.15, 0.60, 0.002):
                px, py = inv(fx, fy)
                if 0 <= int(px) < Wc and 0 <= int(py) < Hc and \
                        alpha[int(py), int(px)]:
                    vals.append(fy)
                    break
        return (min(vals), max(vals)) if vals else (None, None)

    print(f"== {top_name}: band tops (min..max of column tops) ==")
    for label, (x0, x1) in (("centre 0.44-0.56", (0.44, 0.56)),
                            ("L shoulder 0.30-0.42", (0.30, 0.42)),
                            ("R shoulder 0.58-0.70", (0.58, 0.70)),
                            ("L collar 0.38-0.46", (0.38, 0.46)),
                            ("R collar 0.54-0.62", (0.54, 0.62))):
        lo, hi = band_top(x0, x1)
        print(f"   {label}: {lo} .. {hi}")


if __name__ == "__main__":
    # audit23's helpers read mirror_v0123.TOPCAL; point them at the
    # shipped v0.1.24 values (same trick as audit24_gen).
    A23.M.TOPCAL.update(M.TOPCAL)
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "heads"):
        head_layers()
    if what in ("all", "tops"):
        for top in ("top_cardigan", "top_lightouter", "top_knitcoat"):
            top_edges(top, "body_a")
            collar_profile(top, "body_a")
            print()
