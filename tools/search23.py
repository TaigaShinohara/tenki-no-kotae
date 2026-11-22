#!/usr/bin/env python3
"""v0.1.23 calibration search: grid-search TOPCAL per top.

For each top x body pair, dy is solved so the drawn collar lands
exactly on the target; (scaleX, scaleY) are grid-searched against:
  - silhouette width excess over the body at chest rows
    (target band -0.03..+0.05),
  - hand-skin survival under the drawn top (targets per top),
  - cuff (sleeve bottom) band for long sleeves,
  - hem band per top,
  - mild distortion penalty for the shirt / polo.
All geometry in body fractions, matching PersonCanvas / the
Kotlin collar test (box-based collar & hem).
"""
import itertools
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure23 as MS  # noqa: E402

SKIN = MS.SKIN

HANDS = {
    "body_b": {"L": (0.238, 0.615, 0.282, 0.651),
               "R": (0.715, 0.616, 0.756, 0.651)},
    "body_a": {"L": (0.209, 0.600, 0.300, 0.662),
               "R": (0.700, 0.600, 0.787, 0.662)},
    "body_c": {"L": (0.073, 0.609, 0.149, 0.655),
               "R": (0.850, 0.610, 0.925, 0.655)},
}
# per-top targets
TARGETS = {
    #          collar  hem band      cuff band     survival  distort
    "top_tshirt":     (0.330, (0.575, 0.625), (0.44, 0.52), 85, True),
    "top_shirt":      (0.335, (0.620, 0.680), (0.610, 0.650), 45, True),
    "top_cardigan":   (0.335, (0.640, 0.720), (0.610, 0.650), 45, False),
    "top_lightouter": (0.335, (0.640, 0.720), (0.610, 0.650), 45, False),
    "top_knitcoat":   (0.335, (0.690, 0.780), (0.610, 0.650), 45, False),
    "top_down":       (0.330, (0.690, 0.800), (0.600, 0.640), 45, False),
}
PAIRS = [("top_tshirt", "body_b"), ("top_shirt", "body_a"),
         ("top_cardigan", "body_a"), ("top_lightouter", "body_a"),
         ("top_knitcoat", "body_a"), ("top_down", "body_c")]

_cache = {}


def load_pair(top, body):
    if (top, body) in _cache:
        return _cache[(top, body)]
    ta = MS.canvas_arr(top)
    ba = MS.canvas_arr(body)
    Hc, Wc = ta.shape[:2]
    t_alpha = ta[..., 3] > 128
    b_alpha = ba[..., 3] > 128
    b_skin = (MS.dist(ba, SKIN) < 60) & b_alpha
    bb = MS.BOX[body]
    tb = MS.BOX[top]

    def bf(px, py):
        return ((px / Wc - bb[0]) / (bb[2] - bb[0]),
                (py / Hc - bb[1]) / (bb[3] - bb[1]))

    ys, xs = np.where(t_alpha)
    raw = (bf(xs.min(), ys.min()), bf(xs.max(), ys.max()))
    # cuff: median lowest painted row in the outer 15% columns
    lows = {}
    for side, cols in (("L", range(xs.min(), xs.min() + int(0.15 * (xs.max() - xs.min())))),
                       ("R", range(xs.max() - int(0.15 * (xs.max() - xs.min())), xs.max()))):
        vals = []
        for c in cols:
            rows = np.where(t_alpha[:, c])[0]
            if len(rows):
                vals.append(bf(0, rows.max())[1])
        lows[side] = float(np.median(vals)) if vals else raw[1][1]
    # hand skin pixel coords (body fractions), subsampled
    hand_pts = {}
    for side, (fx0, fy0, fx1, fy1) in HANDS[body].items():
        x0 = int((bb[0] + fx0 * (bb[2] - bb[0])) * Wc)
        x1 = int((bb[0] + fx1 * (bb[2] - bb[0])) * Wc)
        y0 = int((bb[1] + fy0 * (bb[3] - bb[1])) * Hc)
        y1 = int((bb[1] + fy1 * (bb[3] - bb[1])) * Hc)
        sub = b_skin[y0:y1, x0:x1]
        ys2, xs2 = np.where(sub)
        pts = np.stack([fx0 + (xs2 + x0 - x0) / Wc / (bb[2] - bb[0]) * 1.0,  # placeholder
                        ], 1) if False else None
        # exact: pixel centre -> body fraction
        px = x0 + xs2
        py = y0 + ys2
        fx = (px / Wc - bb[0]) / (bb[2] - bb[0])
        fy = (py / Hc - bb[1]) / (bb[3] - bb[1])
        hand_pts[side] = (fx[::2], fy[::2])
    # body silhouette widths at rows (drawn body fractions)
    body_w = {}
    for fy in (0.42, 0.50, 0.58):
        y = int((bb[1] + fy * (bb[3] - bb[1])) * Hc)
        row = np.where(b_alpha[max(0, y - 3):y + 4].any(0))[0]
        body_w[fy] = ((row.max() - row.min()) / Wc) / (bb[2] - bb[0])
    d = dict(t_alpha=t_alpha, raw=raw, cuff=lows, hand_pts=hand_pts,
             body_w=body_w, Hc=Hc, Wc=Wc, bb=bb, tb=tb)
    _cache[(top, body)] = d
    return d


def evaluate(top, body, sx, sy, collar_t):
    d = load_pair(top, body)
    (rx0, ry0), (rx1, ry1) = d["raw"]
    cx, cy = (rx0 + rx1) / 2, (ry0 + ry1) / 2
    dy = collar_t - (cy + (ry0 - cy) * sy)
    hem = cy + dy + (ry1 - cy) * sy
    cuff = {s: cy + dy + (v - cy) * sy for s, v in d["cuff"].items()}

    def raw_at(fx, fy):
        return (cx + (fx - cx) / sx, cy + (fy - dy - cy) / sy)

    # width excess: sample the drawn silhouette on a grid
    excess = {}
    Hc, Wc, bb = d["Hc"], d["Wc"], d["bb"]
    for fy in (0.42, 0.50, 0.58):
        xs_f = np.linspace(0.0, 1.0, 400)
        rxs, rys = raw_at(xs_f, np.full_like(xs_f, fy))
        px = ((bb[0] + rxs * (bb[2] - bb[0])) * Wc).astype(int)
        py = ((bb[1] + rys * (bb[3] - bb[1])) * Hc).astype(int)
        ok = (px >= 0) & (px < Wc) & (py >= 0) & (py < Hc)
        hit = np.zeros_like(ok)
        hit[ok] = d["t_alpha"][py[ok], px[ok]]
        if hit.sum() > 3:
            excess[fy] = (xs_f[hit].max() - xs_f[hit].min()) - d["body_w"][fy]
        else:
            excess[fy] = None
    # hand survival
    surv = {}
    for side, (fx, fy) in d["hand_pts"].items():
        if len(fx) == 0:
            surv[side] = None
            continue
        rxs, rys = raw_at(fx, fy)
        px = ((bb[0] + rxs * (bb[2] - bb[0])) * Wc).astype(int)
        py = ((bb[1] + rys * (bb[3] - bb[1])) * Hc).astype(int)
        ok = (px >= 0) & (px < Wc) & (py >= 0) & (py < Hc)
        cov = np.zeros(len(fx), bool)
        cov[ok] = d["t_alpha"][py[ok], px[ok]]
        surv[side] = 100.0 * (1 - cov.mean())
    return dict(dy=dy, hem=hem, cuff=cuff, excess=excess, surv=surv)


def score(top, ev, sx, sy):
    _, hem_band, cuff_band, surv_t, distort = TARGETS[top]
    p = 0.0
    for fy, e in ev["excess"].items():
        if e is None:
            p += 2.0
        elif e > 0.05:
            p += (e - 0.05) * 30
        elif e < -0.05:
            p += (-0.05 - e) * 12
    for s, v in ev["surv"].items():
        if v is not None and v < surv_t:
            p += (surv_t - v) * 0.06
    cm = np.mean(list(ev["cuff"].values()))
    if cm < cuff_band[0]:
        p += (cuff_band[0] - cm) * 18
    elif cm > cuff_band[1]:
        p += (cm - cuff_band[1]) * 18
    if ev["hem"] < hem_band[0]:
        p += (hem_band[0] - ev["hem"]) * 14
    elif ev["hem"] > hem_band[1]:
        p += (ev["hem"] - hem_band[1]) * 14
    if distort:
        p += max(0.0, abs(sx - sy) - 0.18) * 2.0
    return p


def main():
    for top, body in PAIRS:
        collar_t = TARGETS[top][0]
        best = None
        for sx in np.arange(0.55, 1.001, 0.025):
            for sy in np.arange(0.45, 1.001, 0.025):
                ev = evaluate(top, body, float(sx), float(sy), collar_t)
                sc = score(top, ev, sx, sy)
                if best is None or sc < best[0]:
                    best = (sc, float(sx), float(sy), ev)
        sc, sx, sy, ev = best
        print(f"{top} x {body}: cal=({sx:.3f}, {sy:.3f}, 0, {ev['dy']:.3f})"
              f" score={sc:.2f}")
        print(f"   hem={ev['hem']:.3f} cuff L={ev['cuff']['L']:.3f}"
              f" R={ev['cuff']['R']:.3f} surv "
              + " ".join(f"{k}={v:.0f}%" for k, v in ev["surv"].items())
              + " excess "
              + " ".join(f"@{k}:{v:+.3f}" for k, v in ev["excess"].items()
                         if v is not None))
        # also report the current cal for comparison
        cur = MS.TOPCAL[top]
        ev0 = evaluate(top, body, cur[0], cur[1],
                       TARGETS[top][0])
        # current dy differs; evaluate with its own dy instead:
        d = load_pair(top, body)
        (rx0, ry0), (rx1, ry1) = d["raw"]
        cx, cy = (rx0 + rx1) / 2, (ry0 + ry1) / 2
        evc = evaluate(top, body, cur[0], cur[1], collar_t)
        print(f"   [current cal {cur} hem={evc['hem']:.3f} surv "
              + " ".join(f"{k}={v:.0f}%" for k, v in evc["surv"].items())
              + "]")


if __name__ == "__main__":
    main()
