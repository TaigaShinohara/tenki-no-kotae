#!/usr/bin/env python3
"""v0.1.28 face measurement: skin components + eye/mouth ink clusters
for the 6 bases and the 4 head layers, all in each image's own
fractions. Calibrated against the shipped PersonRigs face boxes."""
import os
import numpy as np
from PIL import Image
from scipy import ndimage

RES = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
SKIN = np.array([0xC0, 0xEA, 0xFC], float)
INK = np.array([0x1B, 0x25, 0x66], float)

RIGS = {  # face_l, face_top, face_r, face_bot, eyeY (shipped)
    "body_a": (0.248, 0.180, 0.747, 0.399, 0.216),
    "body_b": (0.307, 0.180, 0.689, 0.337, 0.216),
    "body_c": (0.330, 0.180, 0.668, 0.336, 0.216),
    "person_rank5": (0.306, 0.180, 0.690, 0.314, 0.217),
    "person_rank6": (0.323, 0.180, 0.671, 0.330, 0.216),
    "outfit_raincoat": (0.201, 0.081, 0.743, 0.336, 0.216),
}


def load(name):
    return np.asarray(
        Image.open(os.path.join(RES, name + ".png")).convert("RGBA")
    ).astype(float)


def dist(a, rgb):
    return np.sqrt(((a[..., :3] - rgb) ** 2).sum(-1))


def components(mask):
    lab, n = ndimage.label(mask)
    out = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(xs) < 30:
            continue
        out.append((len(xs), xs.min(), ys.min(), xs.max(), ys.max(),
                    xs.mean(), ys.mean()))
    return sorted(out, reverse=True)


def ink_feats(a, alpha, box):
    """Ink clusters inside a fractional box: returns sorted
    (cx, cy, npix) in image fractions."""
    ih, iw = alpha.shape
    x0, y0 = int(box[0] * iw), int(box[1] * ih)
    x1, y1 = int(box[2] * iw), int(box[3] * ih)
    ink = (dist(a, INK) < 70) & alpha
    sub = ink[y0:y1, x0:x1]
    lab, n = ndimage.label(sub)
    feats = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(xs) < 8 or len(xs) > 6000:
            continue
        feats.append(((x0 + xs.mean()) / iw, (y0 + ys.mean()) / ih,
                      len(xs), (x0 + xs.min()) / iw, (x0 + xs.max()) / iw,
                      (y0 + ys.min()) / ih, (y0 + ys.max()) / ih))
    return sorted(feats, key=lambda f: f[1])


def report(name):
    a = load(name)
    ih, iw = a.shape[:2]
    alpha = a[..., 3] > 128
    skin = (dist(a, SKIN) < 46) & alpha
    print(f"== {name} ({iw}x{ih})")
    comps = components(skin)
    for (n, x0, y0, x1, y1, cx, cy) in comps[:6]:
        print(f"   skin comp n={n} box x {x0/iw:.3f}..{x1/iw:.3f} "
              f"y {y0/ih:.3f}..{y1/ih:.3f} c=({cx/iw:.3f},{cy/ih:.3f})")
    if name in RIGS:
        l, t, r, b, ey = RIGS[name]
        print(f"   RIG:    x {l:.3f}..{r:.3f} y {t:.3f}..{b:.3f} eyeY={ey:.3f}")
        feats = ink_feats(a, alpha, (l - 0.03, t - 0.02, r + 0.03, b))
        for f in feats:
            print(f"   ink ({f[0]:.3f},{f[1]:.3f}) n={f[2]} "
                  f"x {f[3]:.3f}..{f[4]:.3f} y {f[5]:.3f}..{f[6]:.3f}")
    print()


for n in ("body_a", "body_b", "body_c", "person_rank5", "person_rank6",
          "outfit_raincoat"):
    report(n)

print("########## HEAD LAYERS ##########")
for n in ("layer_head_cap", "layer_head_knitcap",
          "layer_head_cap_rain", "layer_head_knitcap_rain"):
    a = load(n)
    ih, iw = a.shape[:2]
    alpha = a[..., 3] > 128
    skin = (dist(a, SKIN) < 46) & alpha
    print(f"== {n} ({iw}x{ih})")
    comps = components(skin)
    for (nn, x0, y0, x1, y1, cx, cy) in comps[:5]:
        print(f"   skin comp n={nn} box x {x0/iw:.3f}..{x1/iw:.3f} "
              f"y {y0/ih:.3f}..{y1/ih:.3f} c=({cx/iw:.3f},{cy/ih:.3f})")
    # ink features anywhere in the lower 2/3 (the face zone)
    feats = ink_feats(a, alpha, (0.15, 0.35, 0.85, 1.0))
    for f in feats:
        print(f"   ink ({f[0]:.3f},{f[1]:.3f}) n={f[2]} "
              f"x {f[3]:.3f}..{f[4]:.3f} y {f[5]:.3f}..{f[6]:.3f}")
    print()
