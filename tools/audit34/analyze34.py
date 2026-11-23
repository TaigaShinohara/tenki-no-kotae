#!/usr/bin/env python3
"""v0.1.34 analysis: per-picture landmarks for the rebuild —
arm layers: shaft join J + shaft colour (draw_canopy32's solver);
variants: hair/hood bbox (base picture through the inner rect),
face centre, fist blob, old canopy bbox, cloth/ink palette."""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
RES = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "audit32"))
import mirror_v0133 as M  # noqa: E402
from draw_canopy32 import shaft_join  # noqa: E402


def load(name):
    return np.asarray(Image.open(os.path.join(RES, name + ".png"))
                      .convert("RGBA")).astype(int)


print("== arm layers ==")
for name, hint in (("arm_polo_umbrella_open", 490),
                   ("arm_cardigan_umbrella_open", 420),
                   ("arm_knitcoat_umbrella_open", 490)):
    a = load(name)
    jy, jx, col = shaft_join(a, hint)
    print(f"{name}: size {a.shape[1]}x{a.shape[0]} join J=({jx:.1f},{jy}) "
          f"shaft_col={col}")

print("== variants ==")
for base, vname in (("RANK5_PORTRAIT", "rank5_umbrella_open"),
                    ("RANK6_PORTRAIT", "rank6_umbrella_open"),
                    ("RAINCOAT", "raincoat_umbrella_open")):
    _, inner = M.variant_of(base, None, "OPEN")
    va = load(vname)
    ba = load(M.BODY_ART[base])
    Hv, Wv = va.shape[:2]
    Hb, Wb = ba.shape[:2]
    # hair/hood in the BASE picture: alpha rows in the top 45%
    bal = ba[:, :, 3] > 0
    rows = []
    for y in range(int(Hb * 0.45)):
        xs = np.where(bal[y])[0]
        rows.append((xs.min(), xs.max()) if len(xs) else None)
    hl = min(r[0] for r in rows if r)
    hr = max(r[1] for r in rows if r)
    ht = next(y for y, r in enumerate(rows) if r)
    hb_ = max(y for y, r in enumerate(rows) if r)
    # face skin blob in the base: light pixels (lum > 170, b-r < 60)
    rgb = ba[:, :, :3]
    lum = 0.299 * rgb[:, :, 0] + 0.587 * rgb[:, :, 1] + 0.114 * rgb[:, :, 2]
    skin = (lum > 165) & (ba[:, :, 3] > 0)
    ys, xs = np.where(skin)
    face_cx_b = (xs.min() + xs.max()) / 2

    def to_var_x(fx):
        return (inner[0] + fx * (inner[2] - inner[0])) * Wv

    def to_var_y(fy):
        return (inner[1] + fy * (inner[3] - inner[1])) * Hv

    print(f"{vname}: img {Wv}x{Hv} inner={tuple(round(v,4) for v in inner)}")
    print(f"  hair(base) x {hl}..{hr} y {ht}..{hb_} -> variant x "
          f"{to_var_x(hl/Wb):.0f}..{to_var_x(hr/Wb):.0f} "
          f"y {to_var_y(ht/Hb):.0f}..{to_var_y(hb_/Hb):.0f} "
          f"(w {to_var_x(hr/Wb)-to_var_x(hl/Wb):.0f})")
    print(f"  face_cx base {face_cx_b:.0f}/{Wb} -> variant "
          f"{to_var_x(face_cx_b/Wb):.0f}")
    # old canopy: variant alpha above the hair-top line
    val = va[:, :, 3] > 0
    htop_v = int(to_var_y(ht / Hb))
    can = val.copy()
    can[htop_v + 4:, :] = False
    ys, xs = np.where(can)
    print(f"  old canopy x {xs.min()}..{xs.max()} y {ys.min()}..{ys.max()} "
          f"(w {xs.max()-xs.min()+1})")
    # canopy cloth palette: median of canopy pixels by luminance band
    cpix = va[:, :, :3][can]
    clum = 0.299 * cpix[:, 0] + 0.587 * cpix[:, 1] + 0.114 * cpix[:, 2]
    for lo, hi in ((0, 45), (45, 80), (80, 120), (120, 256)):
        band = cpix[(clum >= lo) & (clum < hi)]
        if len(band):
            med = tuple(int(np.median(band[:, c])) for c in range(3))
            print(f"    lum {lo:3d}-{hi:3d}: n={len(band):6d} median {med}")
