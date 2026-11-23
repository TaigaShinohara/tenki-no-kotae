#!/usr/bin/env python3
"""v0.1.34: snapshot the CURRENT (v0.1.33) composites for all 8
combos x rain/parasol BEFORE the assets are rebuilt, so the
compare step can put 現行 | 新 side by side. Uses mirror_v0133."""
import os
import sys

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit33"))
import mirror_v0133 as M  # noqa: E402
from render33 import COMBOS, build  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit34")

if __name__ == "__main__":
    os.makedirs(os.path.join(OUT, "orig"), exist_ok=True)
    for label, base, top, extra in COMBOS:
        for held, tag in (("OPEN", "rain"), ("PARASOL", "parasol")):
            im = M.render(build(base, top, held, extra))
            im.save(os.path.join(OUT, f"cur_{label}_{tag}.png"))
        print("snapshotted", label)
    # snapshot the assets that will be rebuilt
    import shutil
    for n in ("arm_polo_umbrella_open", "arm_polo_parasol",
              "arm_cardigan_umbrella_open", "arm_cardigan_parasol",
              "arm_knitcoat_umbrella_open", "arm_knitcoat_parasol",
              "rank5_umbrella_open", "rank5_umbrella_parasol",
              "rank6_umbrella_open", "rank6_umbrella_parasol",
              "raincoat_umbrella_open", "raincoat_umbrella_parasol"):
        shutil.copy(os.path.join(M.RES, n + ".png"),
                    os.path.join(OUT, "orig", n + ".png"))
    print("assets snapshotted")
