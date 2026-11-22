#!/usr/bin/env python3
"""Summer full-body underlay for the v0.1.28 arm regenerations:
the app's own rank-0 composite (BODY_B + TSHIRT) on flat magenta,
plus a parasol-scene variant canvas size."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0127 as M  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 900, 1066
magenta = Image.new("RGBA", (W, H), (255, 0, 255, 255))
spec = {"base": "BODY_B", "top": "TSHIRT"}
canvas = M.draw(magenta, spec, W, H).convert("RGB")
canvas.save(os.path.join(OUT, "underlay_summer.png"))
print("underlay saved", canvas.size)
