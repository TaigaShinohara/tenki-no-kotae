#!/usr/bin/env python3
"""v0.1.31: re-verify the fan placement with the redrawn prop.

Same forbidden zones and the same adopted numbers as the
v0.1.30 solve (height 0.20 ph, PIL rotation -60, grip shift
+0.04 bw) — only the prop's alpha changed (near-white uchiwa
silhouette instead of the light-blue disc). The v0.1.30 result
to match: rank 0 clean (plain and towel); rank 1 has one
accepted graze of ~126 raw px where the rim crosses the holding
arm's own outline.
"""
import sys

sys.path.insert(0, "tools")
# Reuse solve_uchiwa3's head: prop_alpha, dilate, person_frame,
# forbidden3, PATCH, FIST_X (all driven off the live drawable).
exec(open("tools/audit30/solve_uchiwa3.py").read().split("\nCASES = []")[0])

FH, ROT, DX = 0.20, -60, 0.04
for rank, neck in ((0, None), (0, "TOWEL"), (1, None), (1, "TOWEL")):
    spec = M.spec_for(rank, neck=neck)
    forb, frame = forbidden3(spec)
    bl, pt, bw, ph = frame
    pa = prop_alpha(spec, frame, FH, ROT, DX)
    ov = pa & forb
    ys, xs = (lambda t: (t[0], t[1]))(__import__("numpy").nonzero(ov))
    if len(ys) == 0:
        print(f"rank{rank}/towel={neck == 'TOWEL'}: clean (raw overlap 0 px)")
    else:
        print(f"rank{rank}/towel={neck == 'TOWEL'}: raw overlap {len(ys)} px, "
              f"x {(xs.min() - bl) / bw:.3f}..{(xs.max() - bl) / bw:.3f} bw, "
              f"y {(ys.min() - pt) / ph:.3f}..{(ys.max() - pt) / ph:.3f} ph")
