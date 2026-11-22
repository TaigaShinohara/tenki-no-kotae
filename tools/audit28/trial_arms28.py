#!/usr/bin/env python3
"""v0.1.28 arm trial: composite the extracted v28 arm layers in
mirror_v0128 with measured anchors."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0128 as M  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
KEYED = os.path.expanduser("~/workspace/tenki-no-kotae/tools/sleeve_arms/keyed")
W, H = 760, 900

M.IMG["arm_polo_parasol"] = Image.open(
    os.path.join(KEYED, "arm_polo_parasol_v28.png")).convert("RGBA")
M.ARM["arm_polo_parasol"] = {"grip": (0.598, 0.909), "shaft": 0.598,
                             "dw_frac": 1.578, "dh_frac": 0.815, "rot": 0}
M.IMG["arm_polo_uchiwa"] = Image.open(
    os.path.join(KEYED, "arm_polo_uchiwa_v28.png")).convert("RGBA")
M.ARM["arm_polo_uchiwa"] = {"grip": (0.235, 0.842), "shaft": None,
                            "dw_frac": 0.690, "dh_frac": 0.351, "rot": 0}

def render(spec, name):
    canvas = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    canvas.save(os.path.join(OUT, name))
    crop = canvas.crop((int(W * 0.28), int(H * 0.02),
                        int(W * 1.0), int(H * 0.80)))
    crop = crop.resize((crop.size[0] * 2, crop.size[1] * 2), Image.LANCZOS)
    crop.save(os.path.join(OUT, name.replace(".png", "_crop.png")))
    print("rendered", name)

render({"base": "BODY_B", "top": "TSHIRT", "held": "PARASOL"},
       "trial28_parasol.png")
render({"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA"},
       "trial28_uchiwa.png")
render({"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA",
        "towel": True}, "trial28_uchiwa_towel.png")
