#!/usr/bin/env python3
"""Grid-search new topCal values for the layered tops (ranks 2-4).

The raw top alpha is computed once per top; each candidate cal is
scored through the same inverse mapping audit23_gen.drawn_top_alpha
uses (its formula, with the candidate cal). Hand survival is only
evaluated for the base cal and each top's winner (it is the slow
metric); the before/after comparison uses this same code path as
audit24_gen's metrics, so deltas are meaningful.

Targets (drawn, BODY_A fractions):
- shoulder rows 0.36/0.38/0.40: width excess in [-0.012, +0.010],
  centre offset within ~0.006
- lower rows 0.50/0.54: width excess in [-0.020, +0.035]
- collar peak (centre band top) in [0.320, 0.334] (neck base 0.327)
- hem (box bottom through cal) inside the pinned test bands
- drawn box-width ratio <= 0.71 (test band is 0.55..0.72)
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0124 as M124  # noqa: E402
import measure23 as MS  # noqa: E402
import audit23_gen as A23  # noqa: E402

BODY = "body_a"
TOPS = {
    "top_cardigan": dict(hem=(0.63, 0.68)),
    "top_lightouter": dict(hem=(0.61, 0.66)),
    "top_knitcoat": dict(hem=(0.69, 0.73)),
}

_ba = MS.canvas_arr(BODY)
_balpha = _ba[..., 3] > 128
_Hc, _Wc = _balpha.shape[:2]
_bb = MS.BOX[BODY]


def body_edges(fy):
    y = int((_bb[1] + fy * (_bb[3] - _bb[1])) * _Hc)
    row = _balpha[max(0, y - 3):y + 4].any(0)
    xs = np.where(row)[0]
    return ((xs.min() / _Wc - _bb[0]) / (_bb[2] - _bb[0]),
            (xs.max() / _Wc - _bb[0]) / (_bb[2] - _bb[0]))


BODY_EDGES = {fy: body_edges(fy)
              for fy in (0.36, 0.38, 0.40, 0.50, 0.54)}


class Top:
    def __init__(self, name):
        self.name = name
        a = MS.canvas_arr(name)
        self.alpha = a[..., 3] > 128
        b = MS.BOX[name]
        ys, xs = np.where(self.alpha)
        self.rx0 = (xs.min() / _Wc - _bb[0]) / (_bb[2] - _bb[0])
        self.rx1 = (xs.max() / _Wc - _bb[0]) / (_bb[2] - _bb[0])
        self.ry0 = (ys.min() / _Hc - _bb[1]) / (_bb[3] - _bb[1])
        self.ry1 = (ys.max() / _Hc - _bb[1]) / (_bb[3] - _bb[1])
        self.cx = (self.rx0 + self.rx1) / 2
        self.cy = (self.ry0 + self.ry1) / 2

    def inv(self, cal, fx, fy):
        raw_x = self.cx + (fx - cal[2] - self.cx) / cal[0]
        raw_y = self.cy + (fy - cal[3] - self.cy) / cal[1]
        return ((_bb[0] + raw_x * (_bb[2] - _bb[0])) * _Wc,
                (_bb[1] + raw_y * (_bb[3] - _bb[1])) * _Hc)

    def edges(self, cal, fy):
        xs = np.arange(0.02, 0.981, 0.002)
        px, py = self.inv(cal, xs, np.full(xs.shape, fy))
        ok = (px >= 0) & (px < _Wc) & (py >= 0) & (py < _Hc)
        hit = np.zeros(xs.shape, bool)
        hit[ok] = self.alpha[np.clip(py[ok].astype(int), 0, _Hc - 1),
                             np.clip(px[ok].astype(int), 0, _Wc - 1)]
        if not hit.any():
            return None
        return xs[hit].min(), xs[hit].max()

    def collar_peak(self, cal):
        best = None
        for fx in np.arange(0.44, 0.561, 0.004):
            for fy in np.arange(0.28, 0.42, 0.002):
                px, py = self.inv(cal, fx, fy)
                if 0 <= int(px) < _Wc and 0 <= int(py) < _Hc and \
                        self.alpha[int(py), int(px)]:
                    best = fy if best is None else min(best, fy)
                    break
        return best

    def evaluate(self, cal):
        out = {}
        for fy in (0.36, 0.38, 0.40, 0.50, 0.54):
            e = self.edges(cal, fy)
            if e is None:
                return None
            b = BODY_EDGES[fy]
            out[f"exc@{fy}"] = (e[1] - e[0]) - (b[1] - b[0])
            out[f"off@{fy}"] = (e[0] + e[1]) / 2 - (b[0] + b[1]) / 2
        out["collar"] = self.collar_peak(cal)
        tb = MS.BOX[self.name]
        bh = _bb[3] - _bb[1]
        top_f = (tb[1] - _bb[1]) / bh
        bot_f = (tb[3] - _bb[1]) / bh
        cen = (top_f + bot_f) / 2
        out["hem"] = cen + cal[3] + (bot_f - cen) * cal[1]
        out["collarBox"] = cen + cal[3] + (top_f - cen) * cal[1]
        out["boxRatio"] = (tb[2] - tb[0]) / (_bb[2] - _bb[0]) * cal[0]
        return out


def score(top_name, m):
    if m is None:
        return 1e9
    p = 0.0
    for fy in (0.36, 0.38, 0.40):
        e = m[f"exc@{fy}"]
        if e < -0.012:
            p += (-0.012 - e) * 30
        elif e > 0.010:
            p += (e - 0.010) * 30
        p += abs(m[f"off@{fy}"]) * 10
    for fy in (0.50, 0.54):
        e = m[f"exc@{fy}"]
        if e < -0.020:
            p += (-0.020 - e) * 20
        elif e > 0.035:
            p += (e - 0.035) * 20
    c = m["collar"]
    if c is None:
        p += 3.0
    elif c < 0.320:
        p += (0.320 - c) * 30
    elif c > 0.334:
        p += (c - 0.334) * 30
    hem_lo, hem_hi = TOPS[top_name]["hem"]
    if not (hem_lo + 0.005 <= m["hem"] <= hem_hi - 0.005):
        p += 3.0
    if not (0.318 <= m["collarBox"] <= 0.352):
        p += 3.0
    if m["boxRatio"] > 0.71:
        p += 3.0
    return p


def fmt(m):
    return " ".join(
        f"{k}={v:+.3f}" if isinstance(v, float) else f"{k}={v}"
        for k, v in m.items())


def main():
    for top_name in TOPS:
        top = Top(top_name)
        cur = M124.TOPCAL[top_name]
        base = top.evaluate(cur)
        print(f"== {top_name} current cal {cur} score={score(top_name, base):.3f}")
        print("   " + fmt(base))
        best = (score(top_name, base), cur, base)
        for sx in np.arange(cur[0] - 0.01, cur[0] + 0.091, 0.01):
            for sy in np.arange(cur[1] - 0.04, cur[1] + 0.041, 0.01):
                for dy in np.arange(cur[3] - 0.008, cur[3] + 0.0081, 0.002):
                    cal = (round(float(sx), 3), round(float(sy), 3),
                           0.0, round(float(dy), 3))
                    m = top.evaluate(cal)
                    s = score(top_name, m)
                    if s < best[0] - 1e-9:
                        best = (s, cal, m)
        s, cal, m = best
        # hand survival for base and winner, via audit23's path
        A23.M.TOPCAL[top_name] = cur
        hb = A23.hand_survival(top_name, BODY)
        A23.M.TOPCAL[top_name] = cal
        hw = A23.hand_survival(top_name, BODY)
        print(f"   BEST score={s:.3f} cal={cal}")
        print("   " + fmt(m))
        print(f"   hand survival base L={hb['L']:.1f} R={hb['R']:.1f} "
              f"-> winner L={hw['L']:.1f} R={hw['R']:.1f}")
        print()


if __name__ == "__main__":
    main()
