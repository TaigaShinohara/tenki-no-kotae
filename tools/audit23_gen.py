#!/usr/bin/env python3
"""v0.1.23 audit: hand visibility, shoulder/hem fit, hat fit.

Usage: audit23_gen.py <before|after> [metrics]

Renders composites through mirror_v0123 (the exact mirror of the
Kotlin composer) and saves enlarged crops under tools/audit23/.
The metrics mode prints, per top x body: hand-skin survival under
the drawn top, and silhouette width excess of the top over the
body at chest rows.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0123 as M  # noqa: E402
import measure23 as MS  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit23")
os.makedirs(OUT, exist_ok=True)
TAG = sys.argv[1] if len(sys.argv) > 1 else "before"

W, H = 1140, 1350  # render size (mirror default proportions, larger)


def body_rect(spec):
    """(bl, pt, bw, ph) of the drawn person in a W x H render."""
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


# ---------- metrics ----------

def hand_boxes(body_name):
    """Hand skin clusters of a canvas body, in body fractions:
    skin pixels in the y band 0.59..0.68 outside the torso centre,
    clustered per side."""
    a = MS.canvas_arr(body_name)
    alpha = a[..., 3] > 128
    skin = (MS.dist(a, MS.SKIN) < 60) & alpha
    Hc, Wc = skin.shape
    b = MS.BOX[body_name]

    def bf(x, y):
        return MS.to_body((x / Wc, y / Hc), body_name)

    boxes = {}
    for side, (fx0, fx1) in (("L", (0.0, 0.33)), ("R", (0.67, 1.0))):
        x0 = int((b[0] + fx0 * (b[2] - b[0])) * Wc)
        x1 = int((b[0] + fx1 * (b[2] - b[0])) * Wc)
        y0 = int((b[1] + 0.59 * (b[3] - b[1])) * Hc)
        y1 = int((b[1] + 0.68 * (b[3] - b[1])) * Hc)
        sub = skin[y0:y1, x0:x1]
        # the hand is the part NOT continuous with the torso block:
        # take skin pixels whose x is outside the body's own baked
        # top region is unreliable — instead use the measured
        # clusters: connected components, keep those whose centroid
        # y > 0.60 and that do not touch the top of the band.
        from scipy import ndimage
        lab, n = ndimage.label(sub)
        best = None
        for i in range(1, n + 1):
            ys, xs = np.where(lab == i)
            if len(xs) < 100:
                continue
            if ys.min() <= 1 and ys.max() < sub.shape[0] - 2:
                pass  # arm running through the band: still the hand zone
            cand = (len(xs), xs.min() + x0, ys.min() + y0,
                    xs.max() + x0, ys.max() + y0)
            if best is None or cand[0] > best[0]:
                best = cand
        if best:
            _, px0, py0, px1, py1 = best
            boxes[side] = (bf(px0, py0)[0], bf(px0, py0)[1],
                           bf(px1, py1)[0], bf(px1, py1)[1])
    return boxes


# Measured hand boxes (from measure23 clusters; the audit prints
# hand_boxes() so they can be cross-checked).
HANDS = {
    "body_b": {"L": (0.238, 0.615, 0.282, 0.651),
               "R": (0.715, 0.616, 0.756, 0.651)},
    "body_a": {"L": (0.209, 0.600, 0.300, 0.662),
               "R": (0.700, 0.600, 0.787, 0.662)},
    "body_c": {"L": (0.073, 0.609, 0.149, 0.655),
               "R": (0.850, 0.610, 0.925, 0.655)},
}


def drawn_top_alpha(top_name, body_name):
    """The top's alpha in canvas coordinates after TOPCAL, as a
    boolean array over the shared canvas."""
    a = MS.canvas_arr(top_name)
    alpha = a[..., 3] > 128
    cal = M.TOPCAL[top_name]
    Hc, Wc = alpha.shape
    b = MS.BOX[top_name]
    bb = MS.BOX[body_name]
    ys, xs = np.where(alpha)
    rx0 = (xs.min() / Wc - bb[0]) / (bb[2] - bb[0])
    rx1 = (xs.max() / Wc - bb[0]) / (bb[2] - bb[0])
    ry0 = (ys.min() / Hc - bb[1]) / (bb[3] - bb[1])
    ry1 = (ys.max() / Hc - bb[1]) / (bb[3] - bb[1])
    cx, cy = (rx0 + rx1) / 2, (ry0 + ry1) / 2

    def inv(fx, fy):
        """drawn body-fraction point -> canvas pixel of the raw top"""
        raw_x = cx + (fx - cal[2] - cx) / cal[0]
        raw_y = cy + (fy - cal[3] - cy) / cal[1]
        px = (bb[0] + raw_x * (bb[2] - bb[0])) * Wc
        py = (bb[1] + raw_y * (bb[3] - bb[1])) * Hc
        return px, py

    return alpha, inv, (Hc, Wc)


def hand_survival(top_name, body_name):
    """% of each hand box's body-skin pixels left uncovered by the
    drawn top. The body canvas is the ground truth for where the
    hand skin is (skin-coloured pixels inside the measured hand
    boxes, restricted to the actual hand clusters)."""
    ba = MS.canvas_arr(body_name)
    balpha = ba[..., 3] > 128
    skin = (MS.dist(ba, MS.SKIN) < 60) & balpha
    Hc, Wc = skin.shape
    bb = MS.BOX[body_name]
    alpha, inv, _ = drawn_top_alpha(top_name, body_name)
    out = {}
    for side, (fx0, fy0, fx1, fy1) in HANDS[body_name].items():
        x0 = int((bb[0] + fx0 * (bb[2] - bb[0])) * Wc)
        x1 = int((bb[0] + fx1 * (bb[2] - bb[0])) * Wc)
        y0 = int((bb[1] + fy0 * (bb[3] - bb[1])) * Hc)
        y1 = int((bb[1] + fy1 * (bb[3] - bb[1])) * Hc)
        sub = skin[y0:y1, x0:x1]
        ys, xs = np.where(sub)
        if len(xs) == 0:
            out[side] = None
            continue
        covered = 0
        for x, y in zip(xs, ys):
            fx = fx0 + (x / max(1, x1 - x0)) * (fx1 - fx0)
            fy = fy0 + (y / max(1, y1 - y0)) * (fy1 - fy0)
            px, py = inv(fx, fy)
            if 0 <= int(px) < Wc and 0 <= int(py) < Hc and alpha[int(py), int(px)]:
                covered += 1
        out[side] = 100.0 * (1 - covered / len(xs))
    return out


def width_excess(top_name, body_name):
    """top silhouette width - body silhouette width at chest rows
    (body fractions), both drawn (top after TOPCAL)."""
    ba = MS.canvas_arr(body_name)
    balpha = ba[..., 3] > 128
    Hc, Wc = ba.shape[:2]
    bb = MS.BOX[body_name]

    def width_at(alpha_fn, fy):
        y = int((bb[1] + fy * (bb[3] - bb[1])) * Hc)
        row = alpha_fn(y)
        xs = np.where(row)[0]
        if len(xs) == 0:
            return None
        return ((xs.max() - xs.min()) / Wc) / (bb[2] - bb[0])

    alpha, inv, _ = drawn_top_alpha(top_name, body_name)

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

    res = {}
    for fy in (0.42, 0.50, 0.58):
        bw_ = width_at(lambda y: balpha[max(0, y - 3):y + 4].any(0), fy)
        tw = width_at(top_row, fy)
        if bw_ is not None and tw is not None:
            res[fy] = tw - bw_
    return res


def metrics():
    print("hand boxes (auto):", {b: hand_boxes(b) for b in HANDS})
    pairs = [("top_tshirt", "body_b"), ("top_shirt", "body_a"),
             ("top_cardigan", "body_a"), ("top_lightouter", "body_a"),
             ("top_knitcoat", "body_a"), ("top_down", "body_c")]
    for top, body in pairs:
        s = hand_survival(top, body)
        e = width_excess(top, body)
        print(f"{top} x {body}: hand survival "
              f"L={s['L'] if s['L'] is None else round(s['L'],1)} "
              f"R={s['R'] if s['R'] is None else round(s['R'],1)} ; "
              f"width excess " +
              " ".join(f"@{k}:{v:+.3f}" for k, v in e.items()))


# ---------- renders ----------

def render_all():
    ranks = {0: dict(held="UCHIWA", sweat=True),
             1: {}, 2: {}, 3: {}, 4: {}}
    for r, kw in ranks.items():
        spec = M.spec_for(rank=r, **kw)
        img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
        img.save(os.path.join(OUT, f"{TAG}_rank{r}_full.png"))
        # shoulder / neck crop
        crop(img, spec, 0.05, 0.24, 0.95, 0.50,
             f"{TAG}_rank{r}_shoulder.png", 2)
        # hands crop (both sides)
        crop(img, spec, 0.0, 0.52, 1.0, 0.78,
             f"{TAG}_rank{r}_hands.png", 2)
    # hats on every base
    bases = ["BODY_A", "BODY_B", "BODY_C", "RANK5_PORTRAIT",
             "RANK6_PORTRAIT"]
    for base in bases:
        for head in ("CAP", "KNITCAP"):
            spec = {"base": base, "top": None, "head": head}
            if base == "BODY_A":
                spec["top"] = "SHIRT"
            elif base == "BODY_B":
                spec["top"] = "TSHIRT"
            elif base == "BODY_C":
                spec["top"] = "DOWN"
            img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
            tag = base.lower().replace("_portrait", "")
            crop(img, spec, 0.0, -0.06, 1.0, 0.34,
                 f"{TAG}_hat_{head.lower()}_{tag}.png", 2)
    # uchiwa grip zoom on rank 0
    spec = M.spec_for(rank=0, held="UCHIWA", sweat=True)
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    crop(img, spec, 0.55, 0.38, 1.0, 0.72,
         f"{TAG}_rank0_uchiwa_zoom.png", 3)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[2] == "metrics":
        metrics()
    else:
        render_all()
        print("rendered", TAG)
