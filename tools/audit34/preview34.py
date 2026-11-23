#!/usr/bin/env python3
"""v0.1.34 step 1 preview: patch mirror_v0133 with the candidate
arm layers + their re-solved anchors, render the rain composites
for ranks 0-4, and measure the new geometry (measure34 with the
candidate apexes)."""
import os
import sys

from PIL import Image

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "tools", "audit34"))
import mirror_v0133 as M  # noqa: E402
import build_arm34  # noqa: E402
import measure34  # noqa: E402

OUT = os.path.join(ROOT, "tools/audit34")
NAMES = list(build_arm34.PLAN)


def main():
    for name in NAMES:
        img, anch = build_arm34.build_layer(name)
        M.IMG[name] = img
        M.ARM[name] = {"grip": anch["grip"], "shaft": anch["shaft"],
                       "dw_frac": anch["dw"], "dh_frac": anch["dh"],
                       "rot": 0}
        plan = build_arm34.PLAN[name]
        measure34.APEX_PX[name] = plan["ax_old"] + plan["off"]
    sys.path.insert(0, os.path.join(ROOT, "tools", "audit33"))
    from render33 import COMBOS, build
    for label, base, top, extra in COMBOS[:5]:
        im = M.render(build(base, top, "OPEN", extra))
        im.save(os.path.join(OUT, f"prev_{label}_rain.png"))
        print("preview", label)
    for label, base, top, _extra in COMBOS[:5]:
        measure34.report_arm(label, base, top)


if __name__ == "__main__":
    main()
