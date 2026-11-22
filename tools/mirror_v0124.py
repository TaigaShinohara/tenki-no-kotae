#!/usr/bin/env python3
"""Exact Python mirror of PersonComposer (Kotlin) for the v0.1.24 audit.

Draws from the shipped drawable-nodpi assets with the same boxes,
calibrations, rigs and anchors as PersonFigure.kt. Anchor/cal values
are read from ANCH below — keep them in sync with PersonAnchors /
PersonCanvas.topCal in the Kotlin source (the audit script prints any
drift it is told about via --check).

Geometry recap (Kotlin): the canvas rect is W x H px; the person
height ph = H / (1 + padTopRatio); the person stands at the bottom
(pt = H - ph). For canvas-family bases the body bbox is drawn at
(bl, pt, bw, ph); tops are laid into the same canvas frame with
topCal about their bbox center. The rank-5 portrait is drawn at
width ph * imgW/imgH, height ph, centered. Rigs are fractions of
the drawn body rect (bl, pt, bw, ph).
"""
import os
import sys

from PIL import Image, ImageDraw

RES = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")

IMG = {}


def img(name):
    if name not in IMG:
        IMG[name] = Image.open(os.path.join(RES, name + ".png")).convert("RGBA")
    return IMG[name]

CANVAS_W, CANVAS_H = 1120.0, 2240.0

BOX = {
    "body_a": (0.1589, 0.0768, 0.8438, 0.9411),
    "body_b": (0.1580, 0.0763, 0.8446, 0.9397),
    "body_c": (0.1116, 0.0759, 0.8893, 0.9411),
    "top_tshirt": (0.1795, 0.3031, 0.8214, 0.6741),
    "top_shirt": (0.2420, 0.3125, 0.7580, 0.6263),
    "top_cardigan": (0.0839, 0.1911, 0.9170, 0.8004),
    "top_lightouter": (0.1411, 0.2741, 0.8625, 0.6920),
    "top_knitcoat": (0.1491, 0.2384, 0.8527, 0.7781),
    "top_down": (0.0598, 0.2487, 0.9411, 0.7098),
    "outfit_raincoat": (0.2348, 0.0759, 0.7670, 0.9420),
}
TOPCAL = {
    "top_tshirt": (0.70, 0.875, 0.0, 0.028),
    "top_shirt": (0.80, 0.80, 0.0, 0.014),
    "top_cardigan": (0.55, 0.45, 0.0, -0.003),
    "top_lightouter": (0.60, 0.625, 0.0, 0.004),
    "top_knitcoat": (0.65, 0.60, 0.0, 0.011),
    "top_down": (1.00, 0.575, 0.0, 0.005),
}
BODY_ART = {"BODY_A": "body_a", "BODY_B": "body_b", "BODY_C": "body_c",
            "RAINCOAT": "outfit_raincoat", "RANK5_PORTRAIT": "person_rank5",
            "RANK6_PORTRAIT": "person_rank6"}
PORTRAITS = ("RANK5_PORTRAIT", "RANK6_PORTRAIT")
TOP_ART = {"TSHIRT": "top_tshirt", "SHIRT": "top_shirt",
           "CARDIGAN": "top_cardigan", "LIGHT_OUTER": "top_lightouter",
           "KNIT_COAT": "top_knitcoat", "DOWN": "top_down"}
RIGS = {
    "BODY_A": (0.502, 0.180, 0.399, 0.248, 0.747, 0.754, 0.638, 0.241, 0.637, 0.360),
    "BODY_B": (0.480, 0.180, 0.337, 0.307, 0.689, 0.732, 0.630, 0.264, 0.630, 0.362),
    "BODY_C": (0.519, 0.180, 0.336, 0.330, 0.668, 0.893, 0.629, 0.106, 0.629, 0.316),
    "RANK5_PORTRAIT": (0.489, 0.180, 0.314, 0.306, 0.690, 0.756, 0.629, 0.242, 0.629, 0.365),
    "RANK6_PORTRAIT": (0.497, 0.180, 0.330, 0.323, 0.671, 0.80, 0.63, 0.20, 0.63, 0.328),
    "RAINCOAT": (0.507, 0.081, 0.336, 0.201, 0.743, 0.848, 0.629, 0.148, 0.629, 0.464),
}
# Measured feature lines (fractions of the body rect): the eye line
# and the mouth line of each base picture, from ink-cluster analysis
# of the shipped assets (v0.1.21). Sunglasses and the mask are placed
# from these, not from the skin-bbox fractions.
FEATURES = {
    "BODY_A": (0.216, 0.260), "BODY_B": (0.216, 0.260),
    "BODY_C": (0.216, 0.260), "RANK5_PORTRAIT": (0.217, 0.260),
    "RANK6_PORTRAIT": (0.216, 0.261), "RAINCOAT": (0.216, 0.260),
}
# anchors (mirror of PersonAnchors) — audit may override via ANCH_OVERRIDES
ANCH = {
    # v0.1.23: hats are placed by their PAINTED box (HAT_PAINT):
    # *_W is the painted width over body width, *_BOTTOM the
    # painted bottom line, *_HS flattens the tall generated domes.
    "CAP_W": 0.62, "CAP_BOTTOM": 0.195, "CAP_HS": 0.85,
    "KNITCAP_W": 0.66, "KNITCAP_BOTTOM": 0.205, "KNITCAP_HS": 0.72,
    "MASK_W": 1.02, "MASK_DROP": 0.020, "MASK_H": 0.068,
    "SUNG_W": 1.12,
    "MUFFLER_H": 0.27, "MUFFLER_TOP": 0.305,
    "TOWEL_H": 0.212, "TOWEL_TOP": 0.298,
    "MITTEN_H": 0.115, "MITTEN_HAND_AT": 0.42,
    "FOLD2_H": 0.30, "FOLD2_GRIP_X": 0.52, "FOLD2_GRIP_Y": 0.90,
    "UMB_WIDTH": 1.12, "UMB_TOP": 0.005, "UMB_SHAFT_X": 0.507,
    "UMB_SHAFT_W": 0.030,
    "PARASOL_W": 1.15, "PARASOL_TOP": -0.20, "PARASOL_SHAFT_X": 0.508,
    "PARASOL_SHAFT_W": 0.057,
    "UCHIWA_H": 0.21, "UCHIWA_GRIP_X": 0.502, "UCHIWA_GRIP_Y": 0.90,
    "SWEAT_X1": 0.19, "SWEAT_X2": 0.83, "SWEAT_Y": 0.27, "SWEAT_R": 0.022,
    "SHIVER_X1": 0.045, "SHIVER_X2": 0.955, "SHIVER_TOP": 0.42,
    "SHIVER_STEP": 0.028, "SHIVER_AMP": 0.018, "SHIVER_W": 0.006,
}
ANCH_OVERRIDES = {}
# Painted box inside each hat asset (asset fractions, v0.1.23 —
# measured from the shipped PNGs' alpha). The cap asset carries
# wide transparent margins; the knit cap fills its frame.
HAT_PAINT = {
    "prop_cap": (0.000, 0.175, 0.999, 0.927),
    "prop_knitcap": (0.001, 0.000, 0.999, 0.999),
}

# Cloth box inside the mask asset (asset fractions, v0.1.24 —
# measured from the shipped PNG: the white cloth component; the
# ear loops fill the rest of the width).
MASK_PAINT = (0.125, 0.027, 0.874, 0.973)

OUTFIT_FOR = {  # rank -> (base, top)
    0: ("BODY_B", "TSHIRT"), 1: ("BODY_A", "SHIRT"),
    2: ("BODY_A", "CARDIGAN"), 3: ("BODY_A", "LIGHT_OUTER"),
    4: ("BODY_A", "KNIT_COAT"), 5: ("RANK5_PORTRAIT", None),
    6: ("RANK6_PORTRAIT", None),
}


def pad_top_ratio(spec):
    if spec.get("held") == "PARASOL":
        return 0.34
    if spec.get("head") == "KNITCAP":
        return 0.04
    if spec.get("head") == "CAP":
        return 0.04
    return 0.0


def sky(w, h, top=(13, 32, 64), bottom=(38, 84, 124)):
    imgc = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(imgc)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(
            round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return imgc.convert("RGBA")


def paste(canvas, im, rect):
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    canvas.alpha_composite(
        im.resize((max(1, round(w)), max(1, round(h))), Image.LANCZOS),
        (round(x), round(y)))


def draw(canvas, spec, W=760, H=900):
    """spec keys: base, top, neck, mittens, head, mask, sunglasses,
    held, sweat, shiver. Mirrors PersonComposer.draw."""
    A = dict(ANCH, **ANCH_OVERRIDES)
    ph = H / (1.0 + pad_top_ratio(spec))
    pt = H - ph
    base = spec["base"]
    rig = RIGS[base]
    (face_cx, face_top, face_bot, face_l, face_r,
     hand_rx, hand_ry, hand_lx, hand_ly, cheek_w) = rig
    face_w, face_h = face_r - face_l, face_bot - face_top

    if base in PORTRAITS:
        person = img(BODY_ART[base])
        bw = ph * person.size[0] / person.size[1]
        bl = (W - bw) / 2
        paste(canvas, person, (bl, pt, bw, ph))
    else:
        body_name = BODY_ART[base]
        box = BOX[body_name]
        scale = ph / ((box[3] - box[1]) * CANVAS_H)
        cw, ch = CANVAS_W * scale, CANVAS_H * scale
        bw = (box[2] - box[0]) * cw
        bl = (W - bw) / 2
        ox, oy = bl - box[0] * cw, pt - box[1] * ch

        def part_rect(name):
            b = BOX[name]
            x0, y0 = ox + b[0] * cw, oy + b[1] * ch
            x1, y1 = ox + b[2] * cw, oy + b[3] * ch
            cal = TOPCAL.get(name)
            if cal:
                cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
                w, h = (x1 - x0) * cal[0], (y1 - y0) * cal[1]
                x0 = cx - w / 2 + cal[2] * bw
                y0 = cy - h / 2 + cal[3] * ph
                x1, y1 = x0 + w, y0 + h
            return (x0, y0, x1 - x0, y1 - y0)

        paste(canvas, img(body_name), part_rect(body_name))
        if spec.get("top"):
            tn = TOP_ART[spec["top"]]
            paste(canvas, img(tn), part_rect(tn))

    def bx(f):
        return bl + f * bw

    def by(f):
        return pt + f * ph

    hand_x, hand_y = bx(hand_rx), by(hand_ry)
    hybrid = base in PORTRAITS
    raincoat = base == "RAINCOAT"
    eye_y, mouth_y = FEATURES[base]

    def prop_top(name, cx, top_y, w):
        im = img(name)
        paste(canvas, im, (cx - w / 2, top_y, w, w * im.size[1] / im.size[0]))

    def prop_bottom(name, cx, bottom_y, w):
        im = img(name)
        prop_top(name, cx, bottom_y - w * im.size[1] / im.size[0], w)

    # neck
    if not raincoat:
        if spec.get("neck") == "MUFFLER" and not hybrid:
            im = img("prop_muffler")
            h = A["MUFFLER_H"] * ph
            w = h * im.size[0] / im.size[1]
            paste(canvas, im, (bx(0.5) - w / 2, by(A["MUFFLER_TOP"]), w, h))
        elif spec.get("neck") == "TOWEL":
            im = img("prop_towel")
            h = A["TOWEL_H"] * ph
            w = h * im.size[0] / im.size[1]
            paste(canvas, im, (bx(0.5) - w / 2, by(A["TOWEL_TOP"]), w, h))
    # mittens (the portrait bases wear theirs baked in)
    if spec.get("mittens") and not raincoat and not hybrid:
        mh = A["MITTEN_H"] * ph
        for name, hx, hy in (("prop_mitten_l", hand_lx, hand_ly),
                             ("prop_mitten_r", hand_rx, hand_ry)):
            im = img(name)
            mw = mh * im.size[0] / im.size[1]
            paste(canvas, im, (bx(hx) - mw / 2,
                               by(hy) - A["MITTEN_HAND_AT"] * mh, mw, mh))
    # head (v0.1.23: placed by the painted box, dome flattened)
    if spec.get("head") in ("CAP", "KNITCAP"):
        hn = spec["head"]
        art = "prop_cap" if hn == "CAP" else "prop_knitcap"
        im = img(art)
        px0, py0, px1, py1 = HAT_PAINT[art]
        rect_w = A[hn + "_W"] * bw / (px1 - px0)
        rect_h = rect_w * im.size[1] / im.size[0] * A[hn + "_HS"]
        paste(canvas, im, (bx(face_cx) - (px0 + px1) / 2 * rect_w,
                           by(A[hn + "_BOTTOM"]) - py1 * rect_h,
                           rect_w, rect_h))
    # face (placed from the measured feature lines, v0.1.21)
    if spec.get("mask"):
        im = img("prop_mask")
        px0, py0, px1, py1 = MASK_PAINT
        cloth_w = A["MASK_W"] * cheek_w * bw
        rect_w = cloth_w / (px1 - px0)
        rect_h = A["MASK_H"] * ph / (py1 - py0)
        paste(canvas, im, (bx(face_cx) - (px0 + px1) / 2 * rect_w,
                           by(eye_y + A["MASK_DROP"]) - py0 * rect_h,
                           rect_w, rect_h))
    if spec.get("sunglasses"):
        im = img("prop_sunglasses")
        w = A["SUNG_W"] * face_w * bw
        h = w * im.size[1] / im.size[0]
        cy = by(eye_y)
        paste(canvas, im, (bx(face_cx) - w / 2, cy - h / 2, w, h))
    # held
    held = spec.get("held")
    if held == "OPEN":
        umb = img("item_umbrella_open")
        uw = A["UMB_WIDTH"] * bw
        uh = uw * umb.size[1] / umb.size[0]
        u_left = hand_x - A["UMB_SHAFT_X"] * uw
        u_top = pt + A["UMB_TOP"] * ph
        d = ImageDraw.Draw(canvas)
        d.line([(u_left + A["UMB_SHAFT_X"] * uw, u_top + uh), (hand_x, hand_y)],
               fill=(0, 27, 68, 255), width=max(2, round(A["UMB_SHAFT_W"] * uw)))
        paste(canvas, umb, (u_left, u_top, uw, uh))
    elif held == "FOLDED":
        im = img("prop_folding")
        fh = A["FOLD2_H"] * ph
        fw = fh * im.size[0] / im.size[1]
        paste(canvas, im, (hand_x - A["FOLD2_GRIP_X"] * fw,
                           hand_y - A["FOLD2_GRIP_Y"] * fh, fw, fh))
    elif held == "PARASOL":
        im = img("prop_parasol")
        uw = A["PARASOL_W"] * bw
        uh = uw * im.size[1] / im.size[0]
        u_left = hand_x - A["PARASOL_SHAFT_X"] * uw
        u_top = pt + A["PARASOL_TOP"] * ph
        # The asset's shaft is cut short (v0.1.21): a drawn extension
        # — outline pass, then fill pass — runs from its end down to
        # the hand, as with the open umbrella's shaft.
        d = ImageDraw.Draw(canvas)
        sx = u_left + A["PARASOL_SHAFT_X"] * uw
        w_out = max(2, round(A["PARASOL_SHAFT_W"] * uw))
        d.line([(sx, u_top + uh), (hand_x, hand_y)],
               fill=(27, 37, 102, 255), width=w_out)
        d.line([(sx, u_top + uh), (hand_x, hand_y)],
               fill=(151, 201, 234, 255), width=max(1, round(w_out * 0.55)))
        paste(canvas, im, (u_left, u_top, uw, uh))
    elif held == "UCHIWA":
        im = img("prop_uchiwa")
        fh = A["UCHIWA_H"] * ph
        fw = fh * im.size[0] / im.size[1]
        paste(canvas, im, (hand_x - A["UCHIWA_GRIP_X"] * fw,
                           hand_y - A["UCHIWA_GRIP_Y"] * fh, fw, fh))
    # signs
    d = ImageDraw.Draw(canvas)
    if spec.get("sweat") and not raincoat:
        for sx in (A["SWEAT_X1"], A["SWEAT_X2"]):
            cx, cy = bx(sx), by(A["SWEAT_Y"])
            r = A["SWEAT_R"] * ph
            d.ellipse([cx - r * 0.62, cy - r * 0.38, cx + r * 0.62, cy + r * 0.88],
                      fill=(168, 216, 240, 255))
            d.polygon([(cx - r * 0.62, cy - r * 0.2), (cx + r * 0.62, cy - r * 0.2),
                       (cx, cy - r * 1.5)], fill=(168, 216, 240, 255))
    if spec.get("shiver") and not hybrid and not raincoat:
        for sx in (A["SHIVER_X1"], A["SHIVER_X2"]):
            x0 = bx(sx)
            pts = []
            for i in range(11):
                yy = by(A["SHIVER_TOP"] + i * A["SHIVER_STEP"])
                xx = x0 + (A["SHIVER_AMP"] if i % 2 == 1 else -A["SHIVER_AMP"]) * bw
                pts.append((xx, yy))
            d.line(pts, fill=(10, 26, 60, 255),
                   width=max(2, round(A["SHIVER_W"] * ph)))
    return canvas


def spec_for(rank=None, base=None, top="AUTO", **kw):
    if rank is not None:
        b, t = OUTFIT_FOR[rank]
        spec = {"base": b, "top": t}
    else:
        spec = {"base": base, "top": None}
    if top != "AUTO":
        spec["top"] = top
    spec.update(kw)
    return spec


def render(spec, W=760, H=900):
    return draw(sky(W, H), spec, W, H).convert("RGB")
