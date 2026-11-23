#!/usr/bin/env python3
"""v0.1.34: rebuild the three OPEN-umbrella arm layers with the
new parametric canopy (design34). The v0.1.32 settlement is kept:
the arm / sleeve / fist pixels are the shipped ones, byte for
byte — only the canopy + the shaft above the fist are erased and
redrawn, on a re-widened canvas whose anchors are re-solved so
every kept pixel lands on exactly the same composite position.

Per-layer plan (layer px, NEW canvas):
  polo     — apex stays where v0.1.32 put it (offset +7 measured,
             inside tolerance); the canvas widens for the new span.
  cardigan — the apex moves ~80 px left: v0.1.33's apex measured
             +47 px off the face centre in composite; the new
             design centres it (the shaft simply leans a little
             more, as the spec's straight apex->fist line allows).
  knitcoat — apex unchanged (offset ~0 measured).
Depths are solved so the rim feet land at composite y ~245
(mid-hair at the sides) on each layer's primary base.
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
RES = os.path.join(ROOT, "app/src/main/res/drawable-nodpi")
sys.path.insert(0, os.path.join(ROOT, "tools", "audit32"))
sys.path.insert(0, os.path.join(ROOT, "tools", "audit34"))
from draw_canopy32 import canopy_bottom, shaft_join  # noqa: E402
from design34 import draw_umbrella  # noqa: E402

INK = (1, 23, 82)
CLOTH_A = (32, 44, 97)
CLOTH_B = (36, 49, 103)

# current anchors (PersonFigure.kt / mirror ARM), per layer
CUR = {
    "arm_polo_umbrella_open":
        dict(grip=(0.72164, 0.84), shaft=0.70860, dw=1.38003, dh=0.95),
    "arm_cardigan_umbrella_open":
        dict(grip=(0.58555, 0.84), shaft=0.56458, dw=1.43003, dh=0.95),
    "arm_knitcoat_umbrella_open":
        dict(grip=(0.75382, 0.85), shaft=0.73950, dw=1.2576, dh=0.95),
}
HINT = {"arm_polo_umbrella_open": 490,
        "arm_cardigan_umbrella_open": 420,
        "arm_knitcoat_umbrella_open": 490}
# new-canvas plan: W2, off (kept art shifts right by off), apex in
# OLD canvas px (converted by +off), dome depth / sag (layer px)
PLAN = {
    "arm_polo_umbrella_open":
        dict(w2=880, off=60, ax_old=390, ay=55, hw=378, depth=190, sag=34),
    "arm_cardigan_umbrella_open":
        dict(w2=940, off=140, ax_old=290, ay=55, hw=376, depth=172, sag=32),
    "arm_knitcoat_umbrella_open":
        dict(w2=880, off=110, ax_old=353, ay=55, hw=376, depth=174, sag=32),
}


def erase_old(a, hint):
    """draw_canopy32's erase: canopy blob + shaft corridor down
    to the fist join. Returns (kept_rgba_int, join (jx, jy))."""
    H, W = a.shape[:2]
    al = a[:, :, 3] > 0
    bot = canopy_bottom(al)
    erase = np.zeros((H, W), dtype=bool)
    yy = np.arange(H)[:, None]
    erase |= al & (yy <= (bot + 2)[None, :])
    jy, jx, _ = shaft_join(a, hint)
    cx0, cx1 = int(jx) - 18, int(jx) + 18
    erase[jy - 400:jy - 60, cx0:cx1 + 1] |= al[jy - 400:jy - 60, cx0:cx1 + 1]
    lum = 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]
    tx0, tx1 = int(jx) - 11, int(jx) + 11
    tailzone = al[jy - 60:jy, tx0:tx1 + 1] & \
        (lum[jy - 60:jy, tx0:tx1 + 1] < 170)
    erase[jy - 60:jy, tx0:tx1 + 1] |= tailzone
    low = erase.copy()
    low[:430] = False
    low[jy - 400:jy, cx0:cx1 + 1] = False
    assert not low.any(), "erase mask reaches kept art below the canopy!"
    kept = a.copy()
    kept[erase] = 0
    return kept, (jx, jy)


def build_layer(name, plan=None, src_dir=RES):
    plan = plan or PLAN[name]
    src = Image.open(os.path.join(src_dir, name + ".png")).convert("RGBA")
    a = np.asarray(src).astype(int)
    H, W = a.shape[:2]
    W2, off = plan["w2"], plan["off"]
    kept, (jx, jy) = erase_old(a, HINT[name])
    _, _, shaft_col = shaft_join(a, HINT[name])

    base = Image.new("RGBA", (W2, H), (0, 0, 0, 0))
    base.alpha_composite(Image.fromarray(kept.astype(np.uint8), "RGBA"),
                         (off, 0))
    ax, ay = plan["ax_old"] + off, plan["ay"]
    J = (jx + off, jy)
    tilt = (J[0] - ax) / (J[1] - ay)
    art, _ = draw_umbrella(
        (W2, H), (ax, ay), plan["hw"], plan["depth"], plan["sag"],
        tilt, J, shaft_col, CLOTH_A, CLOTH_B, INK,
        shaft_w=16, outline_w=6, rib_w=5, knob=(13, 11))
    base.alpha_composite(art, (0, 0))

    # assert: outside the new art, the output is the kept original
    out = np.asarray(base).astype(int)
    orig_at = np.zeros((H, W2, 4), dtype=int)
    orig_at[:, off:off + W] = a
    kept_mask = np.zeros((H, W2), dtype=bool)
    kept_mask[:, off:off + W] = True
    # kept pixels = original minus erase; recompute erase support:
    kept_alpha = np.zeros((H, W2), dtype=int)
    kept_alpha[:, off:off + W] = kept[:, :, 3]
    art_alpha = np.asarray(art)[:, :, 3] > 0
    untouched = (kept_alpha > 0) & ~art_alpha
    assert (out[untouched] == orig_at[untouched]).all(), \
        f"{name}: kept pixels changed outside the new art!"
    # canopy inside the frame with margin
    ys, xs = np.where(np.asarray(art)[:, :, 3] > 0)
    print(f"{name}: canvas {W}x{H} -> {W2}x{H}, apex=({ax},{ay}) "
          f"J={J} tilt={tilt:.3f} art x {xs.min()}..{xs.max()} "
          f"(margins {xs.min()}/{W2 - 1 - xs.max()})")
    cur = CUR[name]
    anchors = dict(
        grip=((cur["grip"][0] * W + off) / W2, cur["grip"][1]),
        shaft=(cur["shaft"] * W + off) / W2,
        dw=cur["dw"] * W2 / W, dh=cur["dh"])
    print(f"   anchors: grip=({anchors['grip'][0]:.5f},{anchors['grip'][1]}) "
          f"shaft={anchors['shaft']:.5f} dw={anchors['dw']:.5f} "
          f"dh={anchors['dh']}")
    return base, anchors


if __name__ == "__main__":
    for name in PLAN:
        img, _ = build_layer(name)
        img.save(os.path.join(ROOT, "tools/audit34", f"cand_{name}.png"))
        print("candidate saved", name)
