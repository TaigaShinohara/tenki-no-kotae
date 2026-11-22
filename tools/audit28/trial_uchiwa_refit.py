#!/usr/bin/env python3
"""v0.1.28 uchiwa refit trial: rotate the CURRENT arm_polo_uchiwa
about its grip in the mirror (v0.1.27 placement) and see whether
the sleeve can land on the shoulder without wrecking the fan."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0127 as M  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 760, 900
spec = {"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA"}

for rot in (-16, 20, 35, 47, 60, -47):
    M.ARM["arm_polo_uchiwa"]["rot"] = rot
    canvas = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    canvas.save(os.path.join(OUT, f"trial_uchiwa_rot{rot:+d}.png"))
    crop = canvas.crop((int(W * 0.30), int(H * 0.28),
                        int(W * 0.98), int(H * 0.78)))
    crop = crop.resize((crop.size[0] * 2, crop.size[1] * 2), Image.LANCZOS)
    crop.save(os.path.join(OUT, f"trial_uchiwa_rot{rot:+d}_crop.png"))
    print("rot", rot, "done")
