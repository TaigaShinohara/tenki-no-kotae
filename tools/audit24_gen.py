#!/usr/bin/env python3
"""v0.1.24 audit: neck towel fit, mask fit, tops shifted up.

Usage: audit24_gen.py <before|after> [metrics]

"before" renders through mirror_v0123 (the v0.1.23 values),
"after" through mirror_v0124 (the v0.1.24 values). Crops land in
tools/audit24/. The metrics mode prints, per top x body: the
shoulder gap (drawn top shoulder-band top minus the body's own
shoulder top, from measure24), hand-skin survival and silhouette
width excess (the audit23 metrics); and the drawn towel / mask
geometry per base.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

TAG = sys.argv[1] if len(sys.argv) > 1 else "before"
if TAG == "before":
    import mirror_v0123 as M
else:
    import mirror_v0124 as M
import measure23 as MS  # noqa: E402
import audit23_gen as A23  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit24")
os.makedirs(OUT, exist_ok=True)

W, H = 1140, 1350

# The body's own shoulder top at the shoulder bands (measure24,
# torso cluster top beside the neck), body fractions.
BODY_SHOULDER = {"body_a": 0.327, "body_b": 0.330, "body_c": 0.312}
# bw/ph per base (hat test aspects; portraits from asset sizes).
ASPECT = {"BODY_A": 0.3962, "BODY_B": 0.3976, "BODY_C": 0.4494,
          "RANK5_PORTRAIT": 405 / 1024, "RANK6_PORTRAIT": 448 / 1024,
          "RAINCOAT": 0.3072}


def body_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    base = spec["base"]
    if base in M.PORTRAITS:
        person = M.img(M.BODY_ART[base])
        bw = ph * person.size[0] / person.size[1]
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def crop(img, spec, fx0, fy0, fx1, fy1, name, zoom=3):
    bl, pt, bw, ph = body_rect(spec)
    box = (round(bl + fx0 * bw), round(pt + fy0 * ph),
           round(bl + fx1 * bw), round(pt + fy1 * ph))
    c = img.crop(box)
    c = c.resize((c.size[0] * zoom, c.size[1] * zoom), Image.LANCZOS)
    c.save(os.path.join(OUT, name))
    return name


def render(spec):
    return M.draw(M.sky(W, H), spec, W, H).convert("RGB")


# ---------- metrics ----------

def shoulder_gap(top_name, body_name):
    """Drawn top's shoulder-band top minus the body's shoulder
    top, per side (measure24's method, on the active mirror's
    TOPCAL)."""
    import importlib
    a23 = importlib.import_module("audit23_gen")
    # audit23's drawn_top_alpha reads mirror_v0123.TOPCAL; swap in
    # the active mirror's values for this call.
    old = a23.M.TOPCAL[top_name]
    a23.M.TOPCAL[top_name] = M.TOPCAL[top_name]
    try:
        alpha, inv, (Hc, Wc) = a23.drawn_top_alpha(top_name, body_name)
    finally:
        a23.M.TOPCAL[top_name] = old

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

    ref = BODY_SHOULDER[body_name]
    lft, rgt = band_top(0.26, 0.36), band_top(0.64, 0.74)
    return (None if lft is None else lft - ref,
            None if rgt is None else rgt - ref)


def metrics():
    pairs = [("top_tshirt", "body_b"), ("top_shirt", "body_a"),
             ("top_cardigan", "body_a"), ("top_lightouter", "body_a"),
             ("top_knitcoat", "body_a"), ("top_down", "body_c")]
    # hand survival / width excess come from audit23 (v0.1.23
    # TOPCAL): only meaningful for the "before" tag; for "after"
    # they are recomputed with the swapped TOPCAL inside
    # shoulder_gap's helper — reuse the same swap here.
    for top, body in pairs:
        g = shoulder_gap(top, body)
        old = A23.M.TOPCAL[top]
        A23.M.TOPCAL[top] = M.TOPCAL[top]
        try:
            s = A23.hand_survival(top, body)
            e = A23.width_excess(top, body)
        finally:
            A23.M.TOPCAL[top] = old
        print(f"{top} x {body}: shoulder gap "
              f"L={g[0]:+.3f} R={g[1]:+.3f} ; hand survival "
              f"L={s['L'] if s['L'] is None else round(s['L'],1)} "
              f"R={s['R'] if s['R'] is None else round(s['R'],1)} ; "
              f"width excess " +
              " ".join(f"@{k}:{v:+.3f}" for k, v in e.items()))
    # towel geometry (drawn, body fractions)
    A = M.ANCH
    print("towel: top "
          f"{A.get('TOWEL_TOP')} bottom "
          f"{(A.get('TOWEL_TOP', 0) + A.get('TOWEL_H', 0)) if 'TOWEL_H' in A else 'width-driven'}")
    if "TOWEL_W" in A:  # before: width-driven, natural aspect
        tw = M.img("prop_towel")
        asp = tw.size[1] / tw.size[0]
        for base, bwr in ASPECT.items():
            h = A["TOWEL_W"] * bwr * asp
            print(f"   {base}: drawn h={h:.3f} "
                  f"bottom={A['TOWEL_TOP'] + h:.3f} w={A['TOWEL_W']:.3f}")
    else:
        tw = M.img("prop_towel")
        asp = tw.size[1] / tw.size[0]
        for base, bwr in ASPECT.items():
            w = A["TOWEL_H"] / asp / bwr
            print(f"   {base}: drawn w={w:.3f} "
                  f"bottom={A['TOWEL_TOP'] + A['TOWEL_H']:.3f}")
    # mask geometry (drawn cloth, body fractions)
    eye = 0.216
    if TAG == "before":
        for base, rig in M.RIGS.items():
            face_w = rig[3] and (rig[4] - rig[3])
            aw = A["MASK_W"] * face_w
            ah = aw * (662 / 1024) * ASPECT[base]
            print(f"   mask {base}: cloth x {0.125 * aw + (1 - 0) * 0:.3f}.."
                  f" w={0.749 * aw:.3f}bw top={eye + A['MASK_DROP'] + 0.027 * ah:.3f}"
                  f" bottom={eye + A['MASK_DROP'] + 0.973 * ah:.3f}")
    else:
        for base, rig in M.RIGS.items():
            cheek = rig[9]
            print(f"   mask {base}: cloth w={A['MASK_W'] * cheek:.3f}bw"
                  f" top={eye + A['MASK_DROP']:.3f}"
                  f" bottom={eye + A['MASK_DROP'] + A['MASK_H']:.3f}")


# ---------- renders ----------

TOWEL_SPECS = [
    ("bodyb", {"base": "BODY_B", "top": "TSHIRT", "neck": "TOWEL"}),
    ("bodya", {"base": "BODY_A", "top": "SHIRT", "neck": "TOWEL"}),
    ("bodyc", {"base": "BODY_C", "top": "DOWN", "neck": "TOWEL"}),
    ("rank5", {"base": "RANK5_PORTRAIT", "top": None, "neck": "TOWEL"}),
    ("rank6", {"base": "RANK6_PORTRAIT", "top": None, "neck": "TOWEL"}),
]
MASK_SPECS = [
    ("bodya", {"base": "BODY_A", "top": "SHIRT", "mask": True}),
    ("bodyb", {"base": "BODY_B", "top": "TSHIRT", "mask": True}),
    ("bodyc", {"base": "BODY_C", "top": "DOWN", "mask": True}),
    ("rank5", {"base": "RANK5_PORTRAIT", "top": None, "mask": True}),
    ("rank6", {"base": "RANK6_PORTRAIT", "top": None, "mask": True}),
    ("raincoat", {"base": "RAINCOAT", "top": None, "mask": True}),
]


def render_all():
    for tag, spec in TOWEL_SPECS:
        img = render(spec)
        img.save(os.path.join(OUT, f"{TAG}_towel_{tag}_full.png"))
        crop(img, spec, 0.05, 0.13, 0.95, 0.60,
             f"{TAG}_towel_{tag}_neck.png", 3)
    for tag, spec in MASK_SPECS:
        img = render(spec)
        img.save(os.path.join(OUT, f"{TAG}_mask_{tag}_full.png"))
        crop(img, spec, 0.02, 0.06, 0.98, 0.46,
             f"{TAG}_mask_{tag}_face.png", 3)
    # tops: shoulder + hands crops for ranks 0..4 and the down
    for r in range(5):
        spec = M.spec_for(rank=r)
        img = render(spec)
        img.save(os.path.join(OUT, f"{TAG}_rank{r}_full.png"))
        crop(img, spec, 0.05, 0.24, 0.95, 0.50,
             f"{TAG}_rank{r}_shoulder.png", 2)
        crop(img, spec, 0.0, 0.52, 1.0, 0.78,
             f"{TAG}_rank{r}_hands.png", 2)
    spec = {"base": "BODY_C", "top": "DOWN"}
    img = render(spec)
    img.save(os.path.join(OUT, f"{TAG}_down_full.png"))
    crop(img, spec, 0.0, 0.22, 1.0, 0.52,
         f"{TAG}_down_shoulder.png", 2)
    crop(img, spec, 0.0, 0.50, 1.0, 0.78,
         f"{TAG}_down_hands.png", 2)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[2] == "metrics":
        metrics()
    else:
        render_all()
        print("rendered", TAG)
