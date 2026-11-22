#!/usr/bin/env python3
"""v0.1.30 fan placement, third pass — the feasible definition.

Full-silhouette clearance is geometrically impossible for a
grip-anchored disc (proof in docs/FEATURE_v0.1.30.md: the disc
radius 0.19-0.26 bw exceeds the hand-to-silhouette gap 0.10 bw +
max shift). The forbidden zone is therefore the torso FRONT:
composite alpha at x <= (the drawn top's right edge + 0.01 bw)
per row — beyond that edge lies only the shirt's side / sleeve
strip and the arm, which a hand-held fan naturally crosses. Rows
the top does not reach use the composite's own right edge. The
head/hair zone and the towel stay fully forbidden; the hand
patch rect is exempt."""
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, "tools")
exec(open("tools/audit30/solve_uchiwa.py").read().split("CASES = []")[0])

PATCH = {"BODY_B": (0.685, 0.575, 0.800, 0.670),
         "BODY_A": (0.685, 0.575, 0.800, 0.680)}
FIST_X = {"BODY_B": (0.705, 0.792), "BODY_A": (0.700, 0.790)}


def forbidden3(spec):
    bl, pt, bw, ph = person_frame(spec)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    M.draw(canvas, dict(spec, held="NONE"), W, H)
    comp = np.asarray(canvas)[:, :, 3] > 0
    ta = top_alpha(spec)
    forb = np.zeros((H, W), dtype=bool)
    for y in range(H):
        txs = np.nonzero(ta[y])[0]
        cxs = np.nonzero(comp[y])[0]
        if len(txs):
            edge = txs.max() + 0.01 * bw
        elif len(cxs):
            edge = cxs.max()
        else:
            continue
        forb[y, :int(edge) + 1] = comp[y, :int(edge) + 1]
    forb |= comp & (np.arange(H)[:, None] < pt + 0.40 * ph)
    if spec.get("neck") == "TOWEL":
        tw = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        M.draw(tw, dict(spec, held="NONE", neck="TOWEL"), W, H)
        forb |= (np.asarray(tw)[:, :, 3] > 0) & ~comp
    u0, v0, u1, v1 = PATCH[spec["base"]]
    forb[int(pt + v0 * ph):int(pt + v1 * ph),
         int(bl + u0 * bw):int(bl + u1 * bw)] = False
    return forb, (bl, pt, bw, ph)


CASES = []
for rank, neck in ((0, None), (0, "TOWEL"), (1, None), (1, "TOWEL")):
    spec = M.spec_for(rank, neck=neck)
    forb, frame = forbidden3(spec)
    CASES.append((f"rank{rank}/towel={neck == 'TOWEL'}", spec, forb,
                  dilate(forb, 2), frame))

results = []
for fh in (0.20, 0.21, 0.22, 0.23, 0.24, 0.25, 0.26):
    for rot in (0, -8, -16, -24, -32, -40, -48, -56):
        for dx in (0.0, 0.01, 0.02, 0.03, 0.04):
            worst = 0
            for name, spec, forb, forb_d, frame in CASES:
                rig = M.RIGS[spec["base"]]
                fx0, fx1 = FIST_X[spec["base"]]
                if not (fx0 - 0.005 <= rig[5] + dx <= fx1 + 0.005):
                    worst = 10 ** 9
                    break
                pa = prop_alpha(spec, frame, fh, rot, dx)
                worst = max(worst, int((pa & forb_d).sum()))
            cost = abs(rot) + 500 * dx + 600 * abs(fh - 0.21)
            results.append((worst, cost, fh, rot, dx))
results.sort(key=lambda r: (r[0], r[1]))
for r in results[:6]:
    print("overlap/cost/fh/rot/dx:", r)
feas = [r for r in results if r[0] == 0]
if feas:
    feas.sort(key=lambda r: r[1])
    _, _, fh, rot, dx = feas[0]
    print("CHOSEN fh/rot/dx:", fh, rot, dx)
    for name, spec, forb, forb_d, frame in CASES:
        pa = prop_alpha(spec, frame, fh, rot, dx)
        ys, xs = np.nonzero(pa)
        bl, pt, bw, ph = frame
        print(name, "raw overlap:", int((pa & forb).sum()),
              "prop bbox frac x:", round((xs.min() - bl) / bw, 3),
              round((xs.max() - bl) / bw, 3),
              "y:", round((ys.min() - pt) / ph, 3),
              round((ys.max() - pt) / ph, 3))
