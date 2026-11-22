#!/usr/bin/env python3
"""Illustration audit renders for v0.1.25 + sleeve-arm batch.

Writes only under tools/illust_audit/. Current-app composites use
mirror_v0125 (the exact v0.1.25 composer mirror). Sleeve-arm
candidates replicate tools/sleeve_arms/preview_sleeve_arms.py.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
sys.path.insert(0, TOOLS)
sys.path.insert(0, os.path.join(TOOLS, "sleeve_arms"))
import mirror_v0125 as M  # noqa: E402
import preview_sleeve_arms as SA  # noqa: E402

OUT = os.path.join(TOOLS, "illust_audit")
FULL = os.path.join(OUT, "full")
CROPS = os.path.join(OUT, "crops")
PAIRS = os.path.join(OUT, "pairs")
for d in (FULL, CROPS, PAIRS):
    os.makedirs(d, exist_ok=True)

W, H = 1140, 1350
MANIFEST = []


def save(img, folder, name):
    p = os.path.join(folder, name)
    img.convert("RGB").save(p)
    MANIFEST.append(os.path.relpath(p, OUT))
    return p


def body_rect(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    base = spec["base"]
    if base in M.PORTRAITS:
        aspect = 405 / 1024 if base == "RANK5_PORTRAIT" else 448 / 1024
        bw = ph * aspect
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (W - bw) / 2, pt, bw, ph


def crop_body(img, spec, name, fx0, fy0, fx1, fy1, out_w=1050):
    bl, pt, bw, ph = body_rect(spec)
    box = (round(bl + fx0 * bw), round(pt + fy0 * ph),
           round(bl + fx1 * bw), round(pt + fy1 * ph))
    c = img.crop(box)
    r = out_w / c.size[0]
    c = c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)
    return save(c, CROPS, name)


def render_current(name, spec, crop_kinds=("head",)):
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    save(img, FULL, name + "_full.png")
    for kind in crop_kinds:
        if kind == "head":
            crop_body(img, spec, name + "_head.png", 0.02, -0.03, 0.98, 0.50)
        elif kind == "face_neck":
            crop_body(img, spec, name + "_face_neck.png", 0.08, 0.08, 0.92, 0.60)
        elif kind == "upper":
            crop_body(img, spec, name + "_upper.png", 0.0, 0.10, 1.0, 0.72)
        elif kind == "hands":
            crop_body(img, spec, name + "_hands.png", 0.0, 0.48, 1.0, 0.76)
        elif kind == "right_grip":
            crop_body(img, spec, name + "_right_grip.png", 0.42, 0.34, 1.08, 0.78)
        elif kind == "left_hand":
            crop_body(img, spec, name + "_left_hand.png", -0.08, 0.48, 0.58, 0.76)
    return img


def spec_rank(rank, **kw):
    return M.spec_for(rank=rank, **kw)


def spec_rain(**kw):
    return M.spec_for(rank=3, base="RAINCOAT", top=None, **kw)


# ---------- current app: bases ----------
for r in range(7):
    render_current(f"base_rank{r}", spec_rank(r), ("upper", "hands"))
render_current("base_raincoat", spec_rain(), ("upper", "hands"))
render_current("base_bodyc_down", M.spec_for(base="BODY_C", top="DOWN"),
               ("upper", "hands"))

# ---------- current app: head layers on every reachable base ----------
for r in range(7):
    for head, tag in (("CAP", "cap"), ("KNITCAP", "knit")):
        render_current(f"head_rank{r}_{tag}", spec_rank(r, head=head), ("head",))
for head, tag in (("CAP", "cap"), ("KNITCAP", "knit")):
    render_current(f"head_raincoat_{tag}", spec_rain(head=head), ("head",))

# ---------- current app: face / neck interactions ----------
for r in (1, 4, 5, 6):
    render_current(f"mask_rank{r}", spec_rank(r, mask=True), ("face_neck",))
render_current("mask_raincoat", spec_rain(mask=True), ("face_neck",))
render_current("mask_muffler_rank4", spec_rank(4, mask=True, neck="MUFFLER"),
               ("face_neck",))
for r in (4, 5, 6):
    render_current(f"mask_knit_rank{r}", spec_rank(r, mask=True, head="KNITCAP"),
                   ("face_neck",))
render_current("mask_knit_raincoat", spec_rain(mask=True, head="KNITCAP"),
               ("face_neck",))
for r in range(7):
    render_current(f"sung_cap_rank{r}", spec_rank(r, sunglasses=True, head="CAP"),
                   ("head", "face_neck"))
render_current("sung_cap_raincoat", spec_rain(sunglasses=True, head="CAP"),
               ("head", "face_neck"))
for r in (0, 4, 6):
    render_current(f"sung_bare_rank{r}", spec_rank(r, sunglasses=True),
                   ("face_neck",))
render_current("sung_knit_rank4", spec_rank(4, sunglasses=True, head="KNITCAP"),
               ("face_neck",))
render_current("towel_rank0_sweat", spec_rank(0, neck="TOWEL", sweat=True),
               ("face_neck", "right_grip"))
for r in (1, 4, 6):
    render_current(f"towel_rank{r}", spec_rank(r, neck="TOWEL"), ("face_neck",))
for r in (2, 4):
    render_current(f"muffler_rank{r}", spec_rank(r, neck="MUFFLER"), ("face_neck",))

# ---------- current app: held props ----------
for r in range(7):
    for held, tag in (("OPEN", "open"), ("FOLDED", "folded")):
        render_current(f"held_rank{r}_{tag}", spec_rank(r, held=held),
                       ("right_grip", "upper"))
render_current("held_raincoat_open", spec_rain(held="OPEN"), ("right_grip", "upper"))
render_current("held_raincoat_folded", spec_rain(held="FOLDED"),
               ("right_grip", "upper"))
for r in (0, 1, 4, 5, 6):
    render_current(f"held_rank{r}_parasol", spec_rank(r, held="PARASOL"),
                   ("head", "right_grip"))
render_current("held_raincoat_parasol", spec_rain(held="PARASOL"),
               ("head", "right_grip"))
for r in (0, 1, 4, 6):
    render_current(f"held_rank{r}_uchiwa", spec_rank(r, held="UCHIWA"),
                   ("face_neck", "right_grip"))
render_current("rank0_auto_hot", spec_rank(0, neck="TOWEL", sweat=True,
                                           held="UCHIWA", head="CAP",
                                           sunglasses=True),
               ("head", "face_neck", "right_grip"))
render_current("rank0_auto_parasol", spec_rank(0, sweat=True, held="PARASOL",
                                               head="CAP", sunglasses=True),
               ("head", "right_grip"))

# ---------- sleeve-arm candidates (A layers) ----------
AW, AH = 950, 1125


def paste(canvas, im, x, y, w, h):
    canvas.alpha_composite(
        im.resize((max(1, round(w)), max(1, round(h))), Image.LANCZOS),
        (round(x), round(y)))


def paste_rot(canvas, im, dx, dy, dw, dh, grip, deg):
    scaled = im.resize((max(1, round(dw)), max(1, round(dh))), Image.LANCZOS)
    big = Image.new("RGBA", (scaled.size[0] * 3, scaled.size[1] * 3), (0, 0, 0, 0))
    big.alpha_composite(scaled, (scaled.size[0], scaled.size[1]))
    gx = scaled.size[0] + grip[0] * scaled.size[0]
    gy = scaled.size[1] + grip[1] * scaled.size[1]
    big = big.rotate(deg, resample=Image.BICUBIC, center=(gx, gy))
    canvas.alpha_composite(big, (round(dx - scaled.size[0]),
                                 round(dy - scaled.size[1])))


def arm_body_rect(spec):
    ph = float(AH)
    base = spec["base"]
    if base in M.PORTRAITS:
        person = M.img(M.BODY_ART[base])
        bw = ph * person.size[0] / person.size[1]
    else:
        box = M.BOX[M.BODY_ART[base]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    return (AW - bw) / 2, 0.0, bw, ph


def crop_canvas(img, name, fx0, fy0, fx1, fy1, out_w=1050):
    box = (round(AW * fx0), round(AH * fy0), round(AW * fx1), round(AH * fy1))
    c = img.crop(box)
    r = out_w / c.size[0]
    c = c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)
    return save(c, CROPS, name)


def arm_preview(base, top, layer_name, out_name):
    spec = {"base": base, "top": top}
    canvas = M.draw(M.sky(AW, AH), spec, AW, AH)
    bl, pt, bw, ph = arm_body_rect(spec)
    rig = M.RIGS[base]
    hand_x, hand_y = bl + rig[5] * bw, pt + rig[6] * ph
    im = SA.layer_img(layer_name)
    cfg = SA.ARM[layer_name]
    dh = cfg["dh_frac"] * ph
    dw = cfg["dw_frac"] * bw if "dw_frac" in cfg else dh * im.size[0] / im.size[1]
    dx = hand_x - (cfg["shaft"] if "shaft" in cfg else cfg["grip"][0]) * dw
    dy = hand_y - cfg["grip"][1] * dh
    if cfg.get("rot"):
        paste_rot(canvas, im, dx, dy, dw, dh, cfg["grip"], cfg["rot"])
    else:
        paste(canvas, im, dx, dy, dw, dh)
    img = canvas.convert("RGB")
    save(img, FULL, out_name + "_full.png")
    return img


ARM_CASES = [
    ("BODY_B", "TSHIRT", "arm_polo_umbrella_open", "sleeve_polo_open"),
    ("BODY_B", "TSHIRT", "arm_polo_umbrella_folding", "sleeve_polo_folding"),
    ("BODY_B", "TSHIRT", "arm_polo_parasol", "sleeve_polo_parasol"),
    ("BODY_B", "TSHIRT", "arm_polo_uchiwa", "sleeve_polo_uchiwa"),
    ("BODY_A", "CARDIGAN", "arm_cardigan_umbrella_open", "sleeve_cardigan_open"),
    ("BODY_A", "CARDIGAN", "arm_cardigan_umbrella_folding", "sleeve_cardigan_folding"),
    ("BODY_A", "KNIT_COAT", "arm_knitcoat_umbrella_open", "sleeve_knitcoat_open"),
    ("BODY_A", "KNIT_COAT", "arm_knitcoat_umbrella_folding", "sleeve_knitcoat_folding"),
]
for base, top, layer, out in ARM_CASES:
    img = arm_preview(base, top, layer, out)
    crop_canvas(img, out + "_grip.png", 0.40, 0.38, 1.0, 0.86)
    crop_canvas(img, out + "_root.png", 0.34, 0.02, 1.0, 0.52)

# towel under the raised uchiwa arm (candidate interaction)
spec = {"base": "BODY_B", "top": "TSHIRT", "neck": "TOWEL", "sweat": True}
canvas = M.draw(M.sky(AW, AH), spec, AW, AH)
bl, pt, bw, ph = arm_body_rect({"base": "BODY_B", "top": "TSHIRT"})
rig = M.RIGS["BODY_B"]
hand_x, hand_y = bl + rig[5] * bw, rig[6] * ph
im = SA.layer_img("arm_polo_uchiwa")
cfg = SA.ARM["arm_polo_uchiwa"]
dh = cfg["dh_frac"] * ph
dw = dh * im.size[0] / im.size[1]
paste_rot(canvas, im, hand_x - cfg["grip"][0] * dw, hand_y - cfg["grip"][1] * dh,
          dw, dh, cfg["grip"], cfg["rot"])
img = canvas.convert("RGB")
save(img, FULL, "sleeve_polo_uchiwa_towel_full.png")
crop_canvas(img, "sleeve_polo_uchiwa_towel_neck_arm.png", 0.16, 0.10, 1.0, 0.72)

# ---------- sleeve-arm candidates (B full figures) + base pairs ----------
def gradient_bg(w=AW, h=AH):
    top = np.array([33, 45, 100], dtype=np.float32)
    bot = np.array([37, 82, 122], dtype=np.float32)
    grad = np.linspace(0, 1, h)[:, None, None]
    return Image.fromarray(
        (top[None, None, :] * (1 - grad) + bot[None, None, :] * grad)
        .repeat(w, axis=1).astype(np.uint8), "RGB").convert("RGBA")


def figure_canvas(keyed):
    bg = gradient_bg()
    fh = int(AH * 0.97)
    fw = round(keyed.size[0] * fh / keyed.size[1])
    fig = keyed.resize((fw, fh), Image.LANCZOS)
    ox, oy = (AW - fw) // 2, AH - fh - 6
    bg.alpha_composite(fig, (ox, oy))
    return bg.convert("RGB"), (ox, oy, fw, fh)


def figure_case(img_rgba, out_name):
    img, (ox, oy, fw, fh) = figure_canvas(img_rgba)
    save(img, FULL, out_name + "_full.png")
    # face/head crop and right-grip crop in canvas fractions of the figure box
    for suffix, (x0, y0, x1, y1) in {
        "head": (-0.05, 0.0, 1.05, 0.42),
        "grip": (0.48, 0.28, 1.10, 0.74),
    }.items():
        box = (round(ox + x0 * fw), round(oy + y0 * fh),
               round(ox + x1 * fw), round(oy + y1 * fh))
        c = img.crop(box)
        r = 1050 / c.size[0]
        c = c.resize((1050, round(c.size[1] * r)), Image.LANCZOS)
        save(c, CROPS, out_name + "_" + suffix + ".png")
    return img


def app_img(name):
    return Image.open(os.path.join(
        ROOT, "app/src/main/res/drawable-nodpi", name + ".png")).convert("RGBA")


FIG_CASES = [
    ("rank5_base", app_img("person_rank5")),
    ("rank5_open", SA.layer_img("rank5_umbrella_open")),
    ("rank5_folding", SA.layer_img("rank5_umbrella_folding")),
    ("rank6_base", app_img("person_rank6")),
    ("rank6_open_proto2", Image.open(os.path.join(
        TOOLS, "prototypes", "rank6_umbrella_sleeve_proto2.png")).convert("RGBA")),
    ("rank6_folding", SA.layer_img("rank6_umbrella_folding")),
    ("raincoat_open", SA.layer_img("raincoat_umbrella_open")),
    ("raincoat_folding", SA.layer_img("raincoat_umbrella_folding")),
]
fig_imgs = {}
for name, im in FIG_CASES:
    fig_imgs[name] = figure_case(im, "fig_" + name)


def pair(name, left, right):
    a, b = fig_imgs[left], fig_imgs[right]
    canvas = Image.new("RGB", (a.size[0] + b.size[0] + 18, a.size[1]), (8, 12, 24))
    canvas.paste(a, (0, 0))
    canvas.paste(b, (a.size[0] + 18, 0))
    d = ImageDraw.Draw(canvas)
    d.rectangle([a.size[0] + 7, 0, a.size[0] + 10, a.size[1]], fill=(240, 240, 240))
    save(canvas, PAIRS, name + ".png")


pair("pair_rank5_base_open", "rank5_base", "rank5_open")
pair("pair_rank5_base_folding", "rank5_base", "rank5_folding")
pair("pair_rank6_base_proto2", "rank6_base", "rank6_open_proto2")
pair("pair_rank6_base_folding", "rank6_base", "rank6_folding")
pair("pair_rank5_rank6_open", "rank5_open", "rank6_open_proto2")

with open(os.path.join(OUT, "manifest.txt"), "w") as f:
    f.write("\n".join(MANIFEST) + "\n")
print(f"generated {len(MANIFEST)} files")
