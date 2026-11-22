#!/usr/bin/env python3
"""Composite previews for the sleeve-arm batch.

A layers are placed over mirror_v0125 base scenes (body + top, no
old held props) with the same placement rules as preview_layers.py:
open umbrellas shaft-anchored (shaft x onto the rig hand x, grip y
onto the hand y, dw = 1.20 bw, dh = 0.95 ph, non-uniform), parasol
grip-anchored (dw = 1.10 bw, dh = 0.66 ph), folding grip-anchored
(dh = 0.70 ph, rot 24), uchiwa grip-anchored (dh = 0.45 ph,
rot -10). B figures are shown whole on the app-like navy gradient.
Crops of the grip / arm-root regions go to crops/.
"""
import os
import sys

import numpy as np
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
import mirror_v0125 as M  # noqa: E402

DIR = os.path.dirname(os.path.abspath(__file__))
KEYED = os.path.join(DIR, "keyed")
OUT = os.path.join(DIR, "previews")
CROPS = os.path.join(DIR, "crops")
W, H = 760, 900

# layer landmarks (fractions of the keyed layer image, by eye from
# the keyed PNGs): grip = center of the gripping hand;
# shaft = x of the umbrella shaft.
ARM = {
    "arm_cardigan_umbrella_open":
        {"grip": (0.57, 0.84), "shaft": 0.545, "dw_frac": 1.20, "dh_frac": 0.95},
    "arm_polo_umbrella_open":
        {"grip": (0.83, 0.84), "shaft": 0.815, "dw_frac": 1.20, "dh_frac": 0.95},
    "arm_knitcoat_umbrella_open":
        {"grip": (0.79, 0.85), "shaft": 0.775, "dw_frac": 1.20, "dh_frac": 0.95},
    "arm_polo_parasol":
        {"grip": (0.655, 0.955), "dw_frac": 1.10, "dh_frac": 0.66},
    "arm_cardigan_umbrella_folding":
        {"grip": (0.42, 0.40), "dh_frac": 0.70, "rot": 24},
    "arm_polo_umbrella_folding":
        {"grip": (0.40, 0.40), "dh_frac": 0.70, "rot": 12},
    "arm_knitcoat_umbrella_folding":
        {"grip": (0.42, 0.40), "dh_frac": 0.70, "rot": 24},
    "arm_polo_uchiwa":
        {"grip": (0.47, 0.93), "dh_frac": 0.40, "rot": -16},
}


def layer_img(name):
    return Image.open(os.path.join(KEYED, name + ".png")).convert("RGBA")


def body_rect(spec):
    ph = float(H)
    pt = 0.0
    base = spec["base"]
    if base in M.PORTRAITS:
        person = M.img(M.BODY_ART[base])
        bw = ph * person.size[0] / person.size[1]
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def paste(canvas, im, x, y, w, h):
    canvas.alpha_composite(
        im.resize((max(1, round(w)), max(1, round(h))), Image.LANCZOS),
        (round(x), round(y)))


def paste_rot(canvas, im, dx, dy, dw, dh, grip, deg):
    scaled = im.resize((max(1, round(dw)), max(1, round(dh))),
                       Image.LANCZOS)
    big = Image.new("RGBA", (scaled.size[0] * 3, scaled.size[1] * 3),
                    (0, 0, 0, 0))
    big.alpha_composite(scaled, (scaled.size[0], scaled.size[1]))
    gx = scaled.size[0] + grip[0] * scaled.size[0]
    gy = scaled.size[1] + grip[1] * scaled.size[1]
    big = big.rotate(deg, resample=Image.BICUBIC, center=(gx, gy))
    canvas.alpha_composite(big, (round(dx - scaled.size[0]),
                                 round(dy - scaled.size[1])))


def crop_save(canvas, name, fx0, fy0, fx1, fy1, out_w=620):
    box = (int(W * fx0), int(H * fy0), int(W * fx1), int(H * fy1))
    c = canvas.crop(box)
    r = out_w / c.size[0]
    c = c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)
    c.convert("RGB").save(os.path.join(CROPS, name))
    print("crop", name, c.size)


def arm_preview(base, top, layer_name, out_name):
    spec = {"base": base, "top": top}
    canvas = M.draw(M.sky(W, H), spec, W, H)
    bl, pt, bw, ph = body_rect(spec)
    rig = M.RIGS[base]
    hand_x, hand_y = bl + rig[5] * bw, pt + rig[6] * ph
    im = layer_img(layer_name)
    cfg = ARM[layer_name]
    dh = cfg["dh_frac"] * ph
    dw = (cfg["dw_frac"] * bw if "dw_frac" in cfg
          else dh * im.size[0] / im.size[1])
    if "shaft" in cfg:
        dx = hand_x - cfg["shaft"] * dw
    else:
        dx = hand_x - cfg["grip"][0] * dw
    dy = hand_y - cfg["grip"][1] * dh
    if cfg.get("rot"):
        paste_rot(canvas, im, dx, dy, dw, dh, cfg["grip"], cfg["rot"])
    else:
        paste(canvas, im, dx, dy, dw, dh)
    canvas.convert("RGB").save(os.path.join(OUT, out_name))
    print(f"{out_name}: layer {dw:.0f}x{dh:.0f} at ({dx:.0f},{dy:.0f}) "
          f"rot={cfg.get('rot', 0)}")
    return canvas


def gradient_bg():
    top = np.array([33, 45, 100], dtype=np.float32)
    bot = np.array([37, 82, 122], dtype=np.float32)
    grad = np.linspace(0, 1, H)[:, None, None]
    return Image.fromarray(
        (top[None, None, :] * (1 - grad) + bot[None, None, :] * grad)
        .repeat(W, axis=1).astype(np.uint8), "RGB").convert("RGBA")


def figure_preview(name, out_name):
    keyed = layer_img(name)
    bg = gradient_bg()
    fig_h = int(H * 0.97)
    fw = round(keyed.size[0] * fig_h / keyed.size[1])
    fig = keyed.resize((fw, fig_h), Image.LANCZOS)
    ox, oy = (W - fw) // 2, H - fig_h - 6
    bg.alpha_composite(fig, (ox, oy))
    bg.convert("RGB").save(os.path.join(OUT, out_name))
    print(f"{out_name}: figure {fw}x{fig_h} at ({ox},{oy})")
    return bg, (ox, oy, fw, fig_h)


def main():
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(CROPS, exist_ok=True)
    # A: composites
    c = arm_preview("BODY_A", "CARDIGAN", "arm_cardigan_umbrella_open",
                    "comp_cardigan_umbrella_open.png")
    crop_save(c, "crop_cardigan_open_grip.png", 0.52, 0.45, 1.0, 0.85)
    crop_save(c, "crop_cardigan_open_root.png", 0.45, 0.05, 1.0, 0.45)
    c = arm_preview("BODY_A", "CARDIGAN", "arm_cardigan_umbrella_folding",
                    "comp_cardigan_umbrella_folding.png")
    crop_save(c, "crop_cardigan_folding_grip.png", 0.5, 0.4, 1.0, 0.8)
    c = arm_preview("BODY_A", "KNIT_COAT", "arm_knitcoat_umbrella_open",
                    "comp_knitcoat_umbrella_open.png")
    crop_save(c, "crop_knitcoat_open_grip.png", 0.52, 0.45, 1.0, 0.85)
    c = arm_preview("BODY_A", "KNIT_COAT", "arm_knitcoat_umbrella_folding",
                    "comp_knitcoat_umbrella_folding.png")
    crop_save(c, "crop_knitcoat_folding_grip.png", 0.5, 0.4, 1.0, 0.8)
    c = arm_preview("BODY_B", "TSHIRT", "arm_polo_umbrella_open",
                    "comp_polo_umbrella_open.png")
    crop_save(c, "crop_polo_open_grip.png", 0.52, 0.45, 1.0, 0.85)
    crop_save(c, "crop_polo_open_sleeve.png", 0.5, 0.2, 1.0, 0.6)
    c = arm_preview("BODY_B", "TSHIRT", "arm_polo_umbrella_folding",
                    "comp_polo_umbrella_folding.png")
    crop_save(c, "crop_polo_folding_grip.png", 0.5, 0.4, 1.0, 0.8)
    c = arm_preview("BODY_B", "TSHIRT", "arm_polo_parasol",
                    "comp_polo_parasol.png")
    crop_save(c, "crop_polo_parasol_grip.png", 0.52, 0.45, 1.0, 0.85)
    c = arm_preview("BODY_B", "TSHIRT", "arm_polo_uchiwa",
                    "comp_polo_uchiwa.png")
    crop_save(c, "crop_polo_uchiwa_grip.png", 0.4, 0.35, 1.0, 0.8)
    # B: full figures
    for name, out in (
            ("rank5_umbrella_open", "fig_rank5_umbrella_open.png"),
            ("rank5_umbrella_folding", "fig_rank5_umbrella_folding.png"),
            ("rank6_umbrella_folding", "fig_rank6_umbrella_folding.png"),
            ("raincoat_umbrella_open", "fig_raincoat_umbrella_open.png"),
            ("raincoat_umbrella_folding", "fig_raincoat_umbrella_folding.png")):
        bg, (ox, oy, fw, fh) = figure_preview(name, out)
        # grip crop: right-side arm zone of the figure box
        box = (ox + int(fw * 0.55), oy + int(fh * 0.30),
               ox + int(fw * 1.05), oy + int(fh * 0.72))
        cr = bg.crop(box)
        r = 620 / cr.size[0]
        cr = cr.resize((620, round(cr.size[1] * r)), Image.LANCZOS)
        cr.convert("RGB").save(os.path.join(
            CROPS, "crop_" + out.replace("fig_", "").replace(".png", "")
            + "_grip.png"))
        print("crop for", out, cr.size)


if __name__ == "__main__":
    main()
