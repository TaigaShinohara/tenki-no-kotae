#!/usr/bin/env python3
"""v0.1.30 fan placement, fourth pass — refined grid + diagnosis.

Pass 3's grid found NO zero-overlap placement (best: 246 px
against the dilated forbidden mask at fh 0.20 / rot -56 /
dx 0.04). This pass searches finer around that corner and, for
the best candidates, reports WHERE the remaining overlap sits
per case, so the compromise — if one is needed — is chosen by
zone, not by raw count."""
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, "tools")
# solve_uchiwa3's head execs solve_uchiwa's head (prop_alpha,
# dilate, person_frame, M) and defines forbidden3 / PATCH /
# FIST_X.
exec(open("tools/audit30/solve_uchiwa3.py").read().split("\nCASES = []")[0])

CASES = []
for rank, neck in ((0, None), (0, "TOWEL"), (1, None), (1, "TOWEL")):
    spec = M.spec_for(rank, neck=neck)
    forb, frame = forbidden3(spec)
    CASES.append((f"rank{rank}/towel={neck == 'TOWEL'}", spec, forb,
                  dilate(forb, 2), frame))

results = []
for fh in (0.19, 0.20, 0.21, 0.22):
    for rot in (-40, -44, -48, -52, -56, -60):
        for dx in (0.02, 0.03, 0.04, 0.05):
            worst = 0
            ok = True
            for name, spec, forb, forb_d, frame in CASES:
                rig = M.RIGS[spec["base"]]
                fx0, fx1 = FIST_X[spec["base"]]
                if not (fx0 - 0.005 <= rig[5] + dx <= fx1 + 0.005):
                    ok = False
                    break
                pa = prop_alpha(spec, frame, fh, rot, dx)
                worst = max(worst, int((pa & forb_d).sum()))
            if not ok:
                continue
            cost = abs(rot) + 500 * dx + 600 * abs(fh - 0.21)
            results.append((worst, cost, fh, rot, dx))
results.sort(key=lambda r: (r[0], r[1]))
for r in results[:10]:
    print("overlap/cost/fh/rot/dx:", r)

# zone diagnosis for the top 3
for _, _, fh, rot, dx in results[:3]:
    print(f"--- fh={fh} rot={rot} dx={dx}")
    for name, spec, forb, forb_d, frame in CASES:
        bl, pt, bw, ph = frame
        pa = prop_alpha(spec, frame, fh, rot, dx)
        ov = pa & forb
        ys, xs = np.nonzero(ov)
        if len(ys) == 0:
            print(f"  {name}: clean (raw)")
            continue
        print(f"  {name}: raw overlap {len(ys)} px, "
              f"x {(xs.min() - bl) / bw:.3f}..{(xs.max() - bl) / bw:.3f} bw, "
              f"y {(ys.min() - pt) / ph:.3f}..{(ys.max() - pt) / ph:.3f} ph")
