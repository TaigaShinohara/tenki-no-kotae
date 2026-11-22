#!/usr/bin/env python3
"""v0.1.23 measurements: bodies / tops / hats off the shipped assets.

All coordinates are BODY fractions (fractions of the drawn body
rect), matching PersonRigs / PersonCanvas in PersonFigure.kt.

Canvas-family parts (bodies, tops, raincoat) are placed back into
the shared 1120x2240 canvas at their PersonCanvas.box, then mapped
to the paired body's fractions. Portraits (person_rank5/6) are
measured in their own image fractions (their drawn rect IS the
image, scaled).

Colors (measured family palette): hair #344B86, skin #C0EAFC,
ink #151E65..#21286C. Masks use tolerant RGB distance.
"""
import os

import numpy as np
from PIL import Image

RES = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
CW, CH = 1120.0, 2240.0

BOX = {
    "body_a": (0.1589, 0.0768, 0.8438, 0.9411),
    "body_b": (0.1580, 0.0763, 0.8446, 0.9397),
    "body_c": (0.1116, 0.0759, 0.8893, 0.9411),
    "top_tshirt": (0.1795, 0.3031, 0.8214, 0.6741),
    "top_shirt": (0.2420, 0.3125, 0.7580, 0.6263),
    "top_cardigan": (0.0839, 0.1911, 0.9170, 0.8004),
    "top_lightouter": (0.1411, 0.2741, 0.8625, 0.6920),
    "top_knitcoat": (0.1491, 0.2384, 0.8527, 0.7781),
    "top_down": (0.0598, 0.2487, 0.9411, 0.7098),
    "outfit_raincoat": (0.2348, 0.0759, 0.7670, 0.9420),
}
TOPCAL = {
    "top_tshirt": (1.0, 0.91, 0.0, 0.047),
    "top_shirt": (1.0, 0.90, 0.0, 0.044),
    "top_cardigan": (0.82, 0.55, 0.0, 0.05),
    "top_lightouter": (1.0, 0.87, 0.0, 0.075),
    "top_knitcoat": (1.0, 0.78, 0.0, 0.079),
    "top_down": (1.0, 0.85, 0.0, 0.090),
}
BODY_OF_TOP = {
    "top_tshirt": "body_b", "top_shirt": "body_a",
    "top_cardigan": "body_a", "top_lightouter": "body_a",
    "top_knitcoat": "body_a", "top_down": "body_c",
}

HAIR = np.array([0x34, 0x4B, 0x86], float)
SKIN = np.array([0xC0, 0xEA, 0xFC], float)


def load(name):
    return Image.open(os.path.join(RES, name + ".png")).convert("RGBA")


def arr(name):
    return np.asarray(load(name)).astype(float)


def dist(a, rgb):
    return np.sqrt(((a[..., :3] - rgb) ** 2).sum(-1))


def canvas_arr(name):
    """The part placed back into the shared canvas (RGBA float)."""
    im = load(name)
    b = BOX[name]
    out = np.zeros((int(CH), int(CW), 4))
    x0, y0 = round(b[0] * CW), round(b[1] * CH)
    x1, y1 = round(b[2] * CW), round(b[3] * CH)
    im = im.resize((x1 - x0, y1 - y0), Image.LANCZOS)
    a = np.asarray(im).astype(float)
    reg = out[y0:y1, x0:x1]
    al = a[..., 3:4] / 255.0
    reg[..., :3] = a[..., :3] * al + reg[..., :3] * (1 - al)
    reg[..., 3:4] = np.maximum(reg[..., 3:4], a[..., 3:4])
    out[y0:y1, x0:x1] = reg
    return out


def to_body(canvas_xy, body):
    """canvas fraction -> body fraction."""
    b = BOX[body]
    return ((canvas_xy[0] - b[0]) / (b[2] - b[0]),
            (canvas_xy[1] - b[1]) / (b[3] - b[1]))


def clusters(mask, min_px=40):
    """Connected components (4-conn) of a boolean mask -> list of
    (npix, x0, y0, x1, y1, cx, cy) in pixel coords."""
    from scipy import ndimage
    lab, n = ndimage.label(mask)
    out = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(xs) < min_px:
            continue
        out.append((len(xs), xs.min(), ys.min(), xs.max(), ys.max(),
                    xs.mean(), ys.mean()))
    return sorted(out, reverse=True)


def body_report(body):
    a = canvas_arr(body)
    H, W = a.shape[:2]
    alpha = a[..., 3] > 128
    skin = (dist(a, SKIN) < 60) & alpha
    hair = (dist(a, HAIR) < 70) & alpha

    def bf(x, y):
        return to_body((x / W, y / H), body)

    print(f"== {body} ==")
    # skin clusters: face + hands
    print(" skin clusters (npix, bbox body-frac, centroid):")
    for (n, x0, y0, x1, y1, cx, cy) in clusters(skin, 200):
        print(f"   n={n:6d} x {bf(x0,y0)[0]:.3f}..{bf(x1,y1)[0]:.3f}"
              f" y {bf(x0,y0)[1]:.3f}..{bf(x1,y1)[1]:.3f}"
              f" c=({bf(cx,cy)[0]:.3f},{bf(cx,cy)[1]:.3f})")
    # hair: extent + top profile
    ys, xs = np.where(hair)
    print(f" hair bbox: x {bf(xs.min(),0)[0]:.3f}..{bf(xs.max(),0)[0]:.3f}"
          f" y {bf(0,ys.min())[1]:.3f}..{bf(0,ys.max())[1]:.3f}")
    for fx in (0.40, 0.45, 0.50, 0.55, 0.60):
        # top of hair near body-fraction x fx
        colx = int((BOX[body][0] + fx * (BOX[body][2] - BOX[body][0])) * W)
        col = np.where(hair[:, max(0, colx - 6):colx + 7].any(1))[0]
        if len(col):
            print(f"   hair top at x={fx:.2f}: y={bf(0, col.min())[1]:.3f}")
    for fy in (0.10, 0.15, 0.20, 0.216):
        rowy = int((BOX[body][1] + fy * (BOX[body][3] - BOX[body][1])) * H)
        row = np.where(hair[max(0, rowy - 4):rowy + 5].any(0))[0]
        if len(row):
            print(f"   hair width at y={fy:.3f}: x {bf(row.min(),0)[0]:.3f}"
                  f"..{bf(row.max(),0)[0]:.3f} (w={bf(row.max(),0)[0]-bf(row.min(),0)[0]:.3f})")
    # total silhouette width profile (shoulder line detection)
    print(" silhouette width by y (body frac):")
    for fy in np.arange(0.28, 0.72, 0.04):
        rowy = int((BOX[body][1] + fy * (BOX[body][3] - BOX[body][1])) * H)
        row = np.where(alpha[max(0, rowy - 3):rowy + 4].any(0))[0]
        if len(row):
            print(f"   y={fy:.2f}: x {bf(row.min(),0)[0]:.3f}..{bf(row.max(),0)[0]:.3f}"
                  f" w={bf(row.max(),0)[0]-bf(row.min(),0)[0]:.3f}")
    print()


def top_report(top):
    body = BODY_OF_TOP[top]
    a = canvas_arr(top)
    H, W = a.shape[:2]
    alpha = a[..., 3] > 128
    cal = TOPCAL[top]

    def bf(x, y):
        return to_body((x / W, y / H), body)

    # raw (uncalibrated) silhouette in body fractions
    ys, xs = np.where(alpha)
    rx0, ry0 = bf(xs.min(), ys.min())
    rx1, ry1 = bf(xs.max(), ys.max())
    cx, cy = (rx0 + rx1) / 2, (ry0 + ry1) / 2

    def cal_pt(x, y):
        return (cx + (x - cx) * cal[0] + cal[2],
                cy + (y - cy) * cal[1] + cal[3])

    dx0, dy0 = cal_pt(rx0, ry0)
    dx1, dy1 = cal_pt(rx1, ry1)
    print(f"== {top} on {body} (cal {cal}) ==")
    print(f" raw bbox: x {rx0:.3f}..{rx1:.3f} y {ry0:.3f}..{ry1:.3f}")
    print(f" drawn bbox: x {dx0:.3f}..{dx1:.3f} y {dy0:.3f}..{dy1:.3f}")
    print(" drawn silhouette width by y (after cal):")
    for fy in np.arange(0.30, 0.86, 0.04):
        # find canvas rows mapping to drawn fy: invert cal on y
        raw_y = cy + (fy - cal[3] - cy) / cal[1]
        rowy = int((BOX[body][1] + raw_y * (BOX[body][3] - BOX[body][1])) * H)
        if rowy < 0 or rowy >= H:
            continue
        row = np.where(alpha[max(0, rowy - 3):rowy + 4].any(0))[0]
        if len(row):
            xa, _ = cal_pt(*bf(row.min(), 0))
            xb, _ = cal_pt(*bf(row.max(), 0))
            print(f"   y={fy:.2f}: x {xa:.3f}..{xb:.3f} w={xb-xa:.3f}")
        else:
            print(f"   y={fy:.2f}: -")
    # sleeve bottom: lowest alpha in the outer thirds of the raw bbox
    for side, (fx0, fx1) in (("L", (0.0, 0.30)), ("R", (0.70, 1.0))):
        sub = alpha[:, int(fx0 * W):int(fx1 * W)]
        colmax = np.where(sub.any(0))[0]
        if len(colmax):
            # per-column lowest painted row, take the median over the
            # sleeve columns (outer 15% of the part's own width)
            xs_part = np.where(alpha.any(0))[0]
            w0, w1 = xs_part.min(), xs_part.max()
            if side == "L":
                cols = range(w0, w0 + int(0.15 * (w1 - w0)))
            else:
                cols = range(w1 - int(0.15 * (w1 - w0)), w1)
            lows = []
            for c in cols:
                rows = np.where(alpha[:, c])[0]
                if len(rows):
                    lows.append(rows.max())
            if lows:
                med = float(np.median(lows))
                _, dyy = cal_pt(0, bf(0, med)[1])
                print(f" sleeve bottom {side}: drawn y={dyy:.3f}")
    print()


def portrait_report(name):
    a = arr(name)
    alpha = a[..., 3] > 128
    skin = (dist(a, SKIN) < 60) & alpha
    hair = (dist(a, HAIR) < 70) & alpha
    H, W = a.shape[:2]
    print(f"== {name} (own fractions) ==")
    print(" skin clusters:")
    for (n, x0, y0, x1, y1, cx, cy) in clusters(skin, 200):
        print(f"   n={n:6d} x {x0/W:.3f}..{x1/W:.3f} y {y0/H:.3f}..{y1/H:.3f}"
              f" c=({cx/W:.3f},{cy/H:.3f})")
    ys, xs = np.where(hair)
    print(f" hair bbox: x {xs.min()/W:.3f}..{xs.max()/W:.3f}"
          f" y {ys.min()/H:.3f}..{ys.max()/H:.3f}")
    for fx in (0.40, 0.50, 0.60):
        colx = int(fx * W)
        col = np.where(hair[:, max(0, colx - 6):colx + 7].any(1))[0]
        if len(col):
            print(f"   hair top at x={fx:.2f}: y={col.min()/H:.3f}")
    for fy in (0.10, 0.15, 0.20, 0.216):
        rowy = int(fy * H)
        row = np.where(hair[max(0, rowy - 4):rowy + 5].any(0))[0]
        if len(row):
            print(f"   hair width at y={fy:.3f}: x {row.min()/W:.3f}"
                  f"..{row.max()/W:.3f} (w={row.max()/W-row.min()/W:.3f})")
    print()


def hat_report(name):
    im = load(name)
    a = np.asarray(im)
    alpha = a[..., 3] > 128
    ys, xs = np.where(alpha)
    print(f"== {name} asset {im.size}: alpha bbox x {xs.min()/im.size[0]:.3f}"
          f"..{xs.max()/im.size[0]:.3f} y {ys.min()/im.size[1]:.3f}"
          f"..{ys.max()/im.size[1]:.3f} aspect h/w={im.size[1]/im.size[0]:.3f}")
    # width profile: where is the brim (widest lower band)?
    for fy in (0.5, 0.7, 0.85, 0.95):
        rowy = int(fy * im.size[1])
        row = np.where(alpha[max(0, rowy - 3):rowy + 4].any(0))[0]
        if len(row):
            print(f"   width at y={fy:.2f}: {row.min()/im.size[0]:.3f}"
                  f"..{row.max()/im.size[0]:.3f}")


if __name__ == "__main__":
    import sys
    what = sys.argv[1] if len(sys.argv) > 1 else "all"
    if what in ("all", "bodies"):
        for b in ("body_b", "body_a", "body_c"):
            body_report(b)
    if what in ("all", "tops"):
        for t in ("top_tshirt", "top_shirt", "top_cardigan",
                  "top_lightouter", "top_knitcoat", "top_down"):
            top_report(t)
    if what in ("all", "portraits"):
        portrait_report("person_rank5")
        portrait_report("person_rank6")
    if what in ("all", "hats"):
        hat_report("prop_cap")
        hat_report("prop_knitcap")
