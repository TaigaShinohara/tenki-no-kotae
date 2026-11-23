#!/usr/bin/env python3
"""v0.1.34 step 4 (numbers): re-run measure34's geometry reports
against mirror_v0134 — the new assets with the re-solved anchors
and inner rects — and check the acceptance criteria: canopy wider
than the hair (ratio 1.1-1.25), apex over the head centre, frame
margins on both sides."""
import os
import sys

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit34"))
import mirror_v0134 as M34  # noqa: E402
import measure34  # noqa: E402

measure34.M = M34
measure34.APEX_PX = {"arm_polo_umbrella_open": 450,
                     "arm_cardigan_umbrella_open": 430,
                     "arm_knitcoat_umbrella_open": 463}

if __name__ == "__main__":
    measure34.report_arm("rank0", "BODY_B", "TSHIRT")
    measure34.report_arm("rank1", "BODY_A", "SHIRT")
    measure34.report_arm("rank2", "BODY_A", "CARDIGAN")
    measure34.report_arm("rank3", "BODY_A", "LIGHT_OUTER")
    measure34.report_arm("rank4", "BODY_A", "KNIT_COAT")
    measure34.report_variant("rank5", "RANK5_PORTRAIT")
    measure34.report_variant("rank6", "RANK6_PORTRAIT")
    measure34.report_variant("raincoat", "RAINCOAT")
