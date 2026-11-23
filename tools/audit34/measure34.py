#!/usr/bin/env python3
"""v0.1.34 measurement pass: for every umbrella appearance combo,
measure in composite space (760x900, the mirror's frame):
  - hair bbox (from the head layer / base portrait, placed by the
    mirror's own math)
  - canopy bbox (arm layer top blob / variant top blob)
  - canopy width / hair width, apex offset from head centre,
    rim height at the hair's left & right edges, canvas margins.
These numbers set the new canopy's design targets."""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0133 as M  # noqa: E402

RES = M.RES
W, H = 760, 900
# apex x (asset px) of each arm layer's canopy — v0.1.33 values;
# the audit34 preview overrides this for candidate layers.
APEX_PX = {"arm_polo_umbrella_open": 390,
           "arm_cardigan_umbrella_open": 290 + 80,
           "arm_knitcoat_umbrella_open": 353}


def alpha(name):
    return np.asarray(M.img(name))[:, :, 3] > 0


def frame(spec):
    """Replicates mirror.draw's preamble: returns (bl, pt, bw, ph)."""
    pad = M.pad_top_ratio(spec)
    ph = H / (1.0 + pad)
    pt = H - ph
    base = spec["base"]
    if base in M.PORTRAITS:
        bw = ph * M.body_aspect(base)
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    bl = (W - bw) / 2
    return bl, pt, bw, ph


def hair_bbox_asset(name, face_frac_top=0.62):
    """Hair extent inside a head-layer asset: the widest alpha run
    over the rows above `face_frac_top` of the asset (the face /
    neck below is narrower), plus the hair's top row."""
    al = alpha(name)
    h, w = al.shape
    rows = []
    for y in range(int(h * face_frac_top)):
        xs = np.where(al[y])[0]
        rows.append((xs.min(), xs.max()) if len(xs) else None)
    tops = [y for y, r in enumerate(rows) if r]
    left = min(r[0] for r in rows if r)
    right = max(r[1] for r in rows if r)
    return left, tops[0], right, w, h


def canopy_bbox_arm(name):
    """Canopy extent inside an arm layer: per-column bottom of the
    top blob (draw_canopy32's method, corridor interpolated)."""
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "audit32"))
    from draw_canopy32 import canopy_bottom
    al = alpha(name)
    bot = canopy_bottom(al)
    cols = [x for x in range(al.shape[1]) if al[:bot[x] + 1, x].any()]
    top = min(np.where(al[:, x])[0].min() for x in cols)
    return cols[0], top, cols[-1], bot, al.shape[1], al.shape[0]


def report_arm(label, base, top):
    spec = {"base": base, "top": top, "held": "OPEN", "head": "CAP",
            "sunglasses": True, "mask": False, "neck": None,
            "mittens": False, "sweat": False, "shiver": False}
    bl, pt, bw, ph = frame(spec)
    rig = M.RIGS[base]
    # hair via the head layer
    hl, ht, w, h = M.head_layer_rect("CAP", base, bl, pt, bw, ph)
    hx0, hy0, hx1, aw, ah = hair_bbox_asset(M.head_art("CAP", base))
    hair_l = hl + hx0 / aw * w
    hair_r = hl + hx1 / aw * w
    hair_t = ht + hy0 / ah * h
    face_cx = bl + rig[0] * bw
    eye_y = pt + M.HEAD_EYE_Y[base] * ph
    # canopy via the arm layer
    name = M.arm_art(top, "OPEN")
    r = M.arm_rect(name, rig, bl, pt, bw, ph)
    cx0, cy0, cx1, bot, aw2, ah2 = canopy_bbox_arm(name)
    can_l = r[0] + cx0 / aw2 * r[2]
    can_r = r[0] + cx1 / aw2 * r[2]
    can_t = r[1] + cy0 / ah2 * r[3]
    # rim y at the hair's left/right edges (asset column -> layer y)
    def rim_at(comp_x):
        ax = (comp_x - r[0]) / r[2] * aw2
        xi = int(round(ax))
        xi = min(max(xi, cx0), cx1)
        return r[1] + bot[xi] / ah2 * r[3]
    apex_x = r[0] + APEX_PX[name] / aw2 * r[2]
    print(f"[{label}] base={base} top={top} layer={name}")
    print(f"  hair : x {hair_l:6.1f}..{hair_r:6.1f} (w {hair_r-hair_l:6.1f}) "
          f"top y {hair_t:6.1f}  eye y {eye_y:6.1f}  face_cx {face_cx:6.1f}")
    print(f"  canopy: x {can_l:6.1f}..{can_r:6.1f} (w {can_r-can_l:6.1f}) "
          f"top y {can_t:6.1f}  apex_x {apex_x:6.1f} (off {apex_x-face_cx:+.1f})")
    print(f"  ratio canopy/hair = {(can_r-can_l)/(hair_r-hair_l):.3f}   "
          f"margins L {can_l:.0f} R {W-can_r:.0f}")
    print(f"  rim y at hair L edge {rim_at(hair_l):6.1f} / R edge "
          f"{rim_at(hair_r):6.1f}   (hair top {hair_t:.1f}, eye {eye_y:.1f})")
    print(f"  ph {ph:.1f} pt {pt:.1f} bw {bw:.1f}")


def report_variant(label, base):
    spec = {"base": base, "top": None, "held": "OPEN", "head": "CAP",
            "sunglasses": True, "mask": False, "neck": None,
            "mittens": False, "sweat": False, "shiver": False}
    bl, pt, bw, ph = frame(spec)
    vname, inner = M.variant_of(base, None, "OPEN")
    vim = M.img(vname)
    dw = bw / (inner[2] - inner[0])
    dh = ph / (inner[3] - inner[1])
    ox, oy = bl - inner[0] * dw, pt - inner[1] * dh
    # hair from the BASE portrait, mapped through the inner rect
    bname = M.BODY_ART[base]
    bal = alpha(bname)
    bh, bwpx = bal.shape
    rows = []
    for y in range(int(bh * 0.45)):
        xs = np.where(bal[y])[0]
        rows.append((xs.min(), xs.max()) if len(xs) else None)
    hl = min(r[0] for r in rows if r)
    hr = max(r[1] for r in rows if r)
    ht = next(y for y, r in enumerate(rows) if r)
    hair_l = ox + (inner[0] + hl / bwpx * (inner[2] - inner[0])) * dw
    hair_r = ox + (inner[0] + hr / bwpx * (inner[2] - inner[0])) * dw
    hair_t = oy + (inner[1] + ht / bh * (inner[3] - inner[1])) * dh
    face_cx = bl + M.RIGS[base][0] * bw
    eye_y = pt + M.FEATURES[base][0] * ph
    # canopy: variant alpha above the hair-top line (variant coords)
    val = np.asarray(vim)[:, :, 3] > 0
    vh, vw = val.shape
    hair_top_v = (inner[1] + ht / bh * (inner[3] - inner[1])) * vh
    can = val.copy()
    can[int(hair_top_v) + 4:, :] = False
    ys, xs = np.where(can)
    can_l = ox + xs.min() / vw * dw
    can_r = ox + xs.max() / vw * dw
    can_t = oy + ys.min() / vh * dh
    print(f"[{label}] base={base} variant={vname} img {vw}x{vh}")
    print(f"  hair : x {hair_l:6.1f}..{hair_r:6.1f} (w {hair_r-hair_l:6.1f}) "
          f"top y {hair_t:6.1f}  eye y {eye_y:6.1f}  face_cx {face_cx:6.1f}")
    print(f"  canopy: x {can_l:6.1f}..{can_r:6.1f} (w {can_r-can_l:6.1f}) "
          f"top y {can_t:6.1f}")
    print(f"  ratio canopy/hair = {(can_r-can_l)/(hair_r-hair_l):.3f}   "
          f"margins L {can_l:.0f} R {W-can_r:.0f}")
    print(f"  ph {ph:.1f} pt {pt:.1f} bw {bw:.1f}  variant img drawn "
          f"x {ox:.1f}..{ox+dw:.1f}")


if __name__ == "__main__":
    report_arm("rank0", "BODY_B", "TSHIRT")
    report_arm("rank1", "BODY_A", "SHIRT")
    report_arm("rank2", "BODY_A", "CARDIGAN")
    report_arm("rank3", "BODY_A", "LIGHT_OUTER")
    report_arm("rank4", "BODY_A", "KNIT_COAT")
    report_variant("rank5", "RANK5_PORTRAIT")
    report_variant("rank6", "RANK6_PORTRAIT")
    report_variant("raincoat", "RAINCOAT")
