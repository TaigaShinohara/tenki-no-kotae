#!/usr/bin/env python3
"""v0.1.26 landmark measurement for the umbrella-variant pictures.

Generated edits are not rigid transforms of the base portraits, so
an IoU fit over the whole lower body drifts (see measure26.py).
What the composer actually needs is narrower: when a variant is
drawn in place of its base, the FACE must land where the base's
face was (the head layer, mask and sunglasses anchor on it) and
the BOOTS must land on the person rect's bottom (the figure
stands). This script measures, per picture:

- the face skin box (pale-skin #C0EAFC component in the head band),
- the eye line and eye centre (dark-ink clusters inside the face),
- the alpha bottom (the boots).

and derives the variant's inner rect — where the base picture's
(0,0)-(1,1) maps inside the variant — from two anchors:
kx from the face widths / face centres, ky from the eye-to-boots
span. The base pictures are measured with the same detectors and
checked against the shipped PersonRigs values, so the detector
error is visible.

Raincoat base: the shipped outfit_raincoat.png IS the person rect
(the canvas box only places it), so it is measured whole.
"""
import os
from collections import deque

import numpy as np
from PIL import Image

RES = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
KEYED = os.path.expanduser(
    "~/workspace/tenki-no-kotae/tools/sleeve_arms/keyed")
PROTO = os.path.expanduser(
    "~/workspace/tenki-no-kotae/tools/prototypes")

SKIN = np.array([0xC0, 0xEA, 0xFC], float)
INK = np.array([0x1B, 0x25, 0x66], float)

# PersonRigs values to validate the detectors against (fractions):
# face (left, top, right, bottom), eyeY, mouthY
RIG = {
    "rank5": dict(face=(0.306, 0.180, 0.690, 0.314), eye=0.217, mouth=0.260),
    "rank6": dict(face=(0.323, 0.180, 0.671, 0.330), eye=0.216, mouth=0.261),
    "raincoat": dict(face=(0.201, 0.081, 0.743, 0.336), eye=0.216, mouth=0.260),
}
PICTURES = [
    ("rank5", "rank5_base", os.path.join(RES, "person_rank5.png")),
    ("rank5", "rank5_umbrella_open", os.path.join(KEYED, "rank5_umbrella_open.png")),
    ("rank5", "rank5_umbrella_folding", os.path.join(KEYED, "rank5_umbrella_folding.png")),
    ("rank6", "rank6_base", os.path.join(RES, "person_rank6.png")),
    ("rank6", "rank6_umbrella_open", os.path.join(PROTO, "rank6_umbrella_sleeve_proto2.png")),
    ("rank6", "rank6_umbrella_folding", os.path.join(KEYED, "rank6_umbrella_folding.png")),
    ("raincoat", "raincoat_base", os.path.join(RES, "outfit_raincoat.png")),
    ("raincoat", "raincoat_umbrella_open", os.path.join(KEYED, "raincoat_umbrella_open.png")),
    ("raincoat", "raincoat_umbrella_folding", os.path.join(KEYED, "raincoat_umbrella_folding.png")),
]


def components(mask):
    """Connected components of a bool mask: (n, x0, y0, x1, y1, cx, cy)
    in pixels, largest first."""
    h, w = mask.shape
    seen = np.zeros_like(mask)
    out = []
    ys, xs = np.where(mask)
    for y0, x0 in zip(ys.tolist(), xs.tolist()):
        if seen[y0, x0]:
            continue
        q = deque([(y0, x0)])
        seen[y0, x0] = True
        n = 0
        sx = sy = 0
        mnx = mxx = x0
        mny = mxy = y0
        while q:
            cy, cx = q.popleft()
            n += 1
            sx += cx
            sy += cy
            mnx = min(mnx, cx)
            mxx = max(mxx, cx)
            mny = min(mny, cy)
            mxy = max(mxy, cy)
            for ny, nx in ((cy - 1, cx), (cy + 1, cx),
                           (cy, cx - 1), (cy, cx + 1)):
                if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] \
                        and not seen[ny, nx]:
                    seen[ny, nx] = True
                    q.append((ny, nx))
        out.append((n, mnx, mny, mxx, mxy, sx / n, sy / n))
    out.sort(key=lambda c: -c[0])
    return out


def landmarks(path):
    im = Image.open(path).convert("RGBA")
    w, h = im.size
    a = np.asarray(im).astype(float)
    alpha = a[..., 3] > 128
    dist = np.sqrt(((a[..., :3] - SKIN) ** 2).sum(axis=2))
    skin = (dist < 45) & alpha
    # face: the skin component in the head band (top 42%) whose
    # centre x is near the middle — hands are skin too but sit
    # lower and narrower.
    head = skin.copy()
    head[int(0.42 * h):, :] = False
    comps = [c for c in components(head)
             if c[0] > 200 and 0.3 < c[5] / w < 0.7]
    face = comps[0]
    fn, fx0, fy0, fx1, fy1, fcx, fcy = face
    # eyes + mouth: ink clusters inside the face box (padded)
    inkd = np.sqrt(((a[..., :3] - INK) ** 2).sum(axis=2))
    ink = (inkd < 60) & alpha
    sub = np.zeros_like(ink)
    px0, py0 = max(0, fx0 - 5), max(0, fy0 - 5)
    px1, py1 = min(w, fx1 + 6), min(h, fy1 + 6)
    sub[py0:py1, px0:px1] = ink[py0:py1, px0:px1]
    feats = [c for c in components(sub) if 5 <= c[0] <= 3000]
    feats.sort(key=lambda c: c[6])
    eyes = [c for c in feats if c[6] < fcy]
    eye_y = np.mean([c[6] for c in eyes[:2]]) if eyes else None
    eye_cx = np.mean([c[5] for c in eyes[:2]]) if len(eyes) >= 2 else None
    boots = np.where(alpha)[0].max()
    return dict(
        size=(w, h),
        face=(fx0 / w, fy0 / h, fx1 / w, fy1 / h),
        face_cx=fcx / w,
        eye_y=(eye_y / h if eye_y is not None else None),
        eye_cx=(eye_cx / w if eye_cx is not None else None),
        boots=boots / h,
        ink=[(round(c[5] / w, 3), round(c[6] / h, 3), c[0])
             for c in feats[:5]],
    )


def main():
    measured = {}
    for base_name, name, path in PICTURES:
        lm = landmarks(path)
        measured[name] = lm
        w, h = lm["size"]
        f = lm["face"]
        ey = None if lm["eye_y"] is None else round(lm["eye_y"], 3)
        print(f"== {name} ({w}x{h})")
        print(f"   face=({f[0]:.3f},{f[1]:.3f},{f[2]:.3f},{f[3]:.3f})"
              f" cx={lm['face_cx']:.3f} eye_y={ey} boots={lm['boots']:.3f}")
        print(f"   ink in face: {lm['ink']}")
        if name.endswith("_base"):
            rig = RIG[base_name]
            rf = rig["face"]
            print(f"   rig: face=({rf[0]:.3f},{rf[1]:.3f},{rf[2]:.3f},"
                  f"{rf[3]:.3f}) eye={rig['eye']:.3f} "
                  f"mouth={rig['mouth']:.3f}")
    print()
    print("== inner rects (base (0,0)-(1,1) inside the variant) ==")
    for base_name, name, path in PICTURES:
        if name.endswith("_base"):
            continue
        b = measured[base_name + "_base"]
        v = measured[name]
        bw, bh = b["size"]
        vw, vh = v["size"]
        bf = b["face"]
        vf = v["face"]
        rig = RIG[base_name]
        kx = (vf[2] - vf[0]) * vw / ((bf[2] - bf[0]) * bw)
        ky = (v["boots"] - v["eye_y"]) / (b["boots"] - rig["eye"])
        dx = v["face_cx"] * vw - kx * b["face_cx"] * bw
        dy = v["eye_y"] * vh - ky * rig["eye"] * bh
        kw = kx * bw / vw
        kh = ky * bh / vh
        fx0, fy0 = dx / vw, dy / vh
        print(f"{name}: kx={kx:.3f} ky={ky:.3f} "
              f"inner=({fx0:.4f},{fy0:.4f},{fx0 + kw:.4f},{fy0 + kh:.4f}) "
              f"poke={fy0 / kh:.4f}")


if __name__ == "__main__":
    main()
