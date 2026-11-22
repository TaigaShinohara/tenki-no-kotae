#!/usr/bin/env python3
"""Composite the fixed sleeve arms on BODY_B+TSHIRT using
preview_sleeve_arms' placement math (760x900 canvas)."""
import os, sys
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
sys.path.insert(0, os.path.join(TOOLS, "sleeve_arms"))
import mirror_v0125 as M  # noqa: E402
import preview_sleeve_arms as SA  # noqa: E402

OUT = os.path.join(TOOLS, "illust_fixes", "fix23")
os.makedirs(OUT, exist_ok=True)
W, H = SA.W, SA.H


def render(layer_name, cfg, tag, towel=False):
    SA.ARM[layer_name] = cfg
    spec = M.spec_for(rank=1, top="TSHIRT", towel=towel)
    canvas = M.draw(M.sky(W, H), spec, W, H)
    bl, pt, bw, ph = SA.body_rect(spec)
    rig = M.RIGS["BODY_B"]
    hand_x, hand_y = bl + rig[5] * bw, pt + rig[6] * ph
    im = SA.layer_img(layer_name)
    dh = cfg["dh_frac"] * ph
    dw = (cfg["dw_frac"] * bw if "dw_frac" in cfg
          else dh * im.size[0] / im.size[1])
    dx = hand_x - cfg["grip"][0] * dw
    dy = hand_y - cfg["grip"][1] * dh
    if cfg.get("rot"):
        SA.paste_rot(canvas, im, dx, dy, dw, dh, cfg["grip"], cfg["rot"])
    else:
        SA.paste(canvas, im, dx, dy, dw, dh)
    canvas.convert("RGB").save(os.path.join(OUT, f"comp_{tag}.png"))
    box = (round(bl + 0.25 * bw), round(pt - 0.05 * ph),
           round(bl + 1.30 * bw), round(pt + 0.58 * ph))
    c = canvas.crop(box)
    r = 950 / c.size[0]
    c.resize((950, round(c.size[1] * r)), Image.LANCZOS).convert("RGB") \
        .save(os.path.join(OUT, f"comp_{tag}_root.png"))
    print("saved", tag, f"layer {dw:.0f}x{dh:.0f} at ({dx:.0f},{dy:.0f})")


render("arm_polo_uchiwa_fixed",
       {"grip": (0.42, 0.895), "dh_frac": 0.40, "rot": -16},
       "polo_uchiwa_fixed")
render("arm_polo_uchiwa_fixed",
       {"grip": (0.42, 0.895), "dh_frac": 0.40, "rot": -16},
       "polo_uchiwa_fixed_towel", towel=True)
render("arm_polo_parasol_fixed",
       {"grip": (0.475, 0.945), "dw_frac": 1.10, "dh_frac": 0.60},
       "polo_parasol_fixed_v1")

render("arm_polo_parasol_fixed",
       {"grip": (0.655, 0.955), "dw_frac": 1.10, "dh_frac": 0.66},
       "polo_parasol_fixed")
# towel interference for uchiwa, with the correct neck="TOWEL" spec
spec = M.spec_for(rank=1, top="TSHIRT", neck="TOWEL")
canvas = M.draw(M.sky(W, H), spec, W, H)
bl, pt, bw, ph = SA.body_rect(spec)
rig = M.RIGS["BODY_B"]
hand_x, hand_y = bl + rig[5] * bw, pt + rig[6] * ph
im = SA.layer_img("arm_polo_uchiwa_fixed")
cfg = {"grip": (0.42, 0.895), "dh_frac": 0.40, "rot": -16}
dh = cfg["dh_frac"] * ph
dw = dh * im.size[0] / im.size[1]
SA.paste_rot(canvas, im, hand_x - cfg["grip"][0] * dw,
             hand_y - cfg["grip"][1] * dh, dw, dh, cfg["grip"], -16)
canvas.convert("RGB").save(os.path.join(OUT, "comp_polo_uchiwa_fixed_towel.png"))
box = (round(bl - 0.05 * bw), round(pt - 0.05 * ph),
       round(bl + 1.30 * bw), round(pt + 0.62 * ph))
c = canvas.crop(box)
r = 950 / c.size[0]
c.resize((950, round(c.size[1] * r)), Image.LANCZOS).convert("RGB") \
    .save(os.path.join(OUT, "comp_polo_uchiwa_fixed_towel_root.png"))
print("saved towel uchiwa (neck=TOWEL)")
