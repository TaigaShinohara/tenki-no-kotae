#!/usr/bin/env python3
"""Exact Python mirror of PersonComposer (Kotlin) for the v0.1.33 audit (v0.1.33 wiring).

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
    "top_tshirt": (0.64, 0.875, 0.0, 0.028),
    "top_shirt": (0.80, 0.80, 0.0, 0.014),
    "top_cardigan": (0.55, 0.45, 0.0, -0.003),
    "top_lightouter": (0.63, 0.625, 0.0, 0.000),
    "top_knitcoat": (0.67, 0.60, 0.0, 0.011),
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
    "RAINCOAT": (0.507, 0.081, 0.336, 0.201, 0.743, 0.848, 0.629, 0.148, 0.629, 0.40),
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
    # *_W is the painted width over body width, *_BOTTOM the
    # painted bottom line, *_HS flattens the tall generated domes.
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
    "UCHIWA_H": 0.20, "UCHIWA_GRIP_X": 0.502, "UCHIWA_GRIP_Y": 0.90,
    "UCHIWA_ROT": -60, "UCHIWA_DX": 0.04,
    "SWEAT_X1": 0.19, "SWEAT_X2": 0.83, "SWEAT_Y": 0.27, "SWEAT_R": 0.022,
    "SHIVER_X1": 0.045, "SHIVER_X2": 0.955, "SHIVER_TOP": 0.42,
    "SHIVER_STEP": 0.028, "SHIVER_AMP": 0.018, "SHIVER_W": 0.006,
}
ANCH_OVERRIDES = {}
# Painted box inside each hat asset (asset fractions, v0.1.23 —
# measured from the shipped PNGs' alpha). The cap asset carries
# wide transparent margins; the knit cap fills its frame.

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


# Head layers (v0.1.25): face boxes / eye lines inside the layer
# assets, as in PersonAnchors (fractions of the layer image).
HEAD_FACE = {"CAP": (0.349, 0.556, 0.651, 0.848),
             "KNITCAP": (0.306, 0.551, 0.689, 0.845)}
# v0.1.28: per-base chin-based target face boxes (measured off the
# shipped base assets with the same detector as the layers').
HEAD_TARGET = {
    "BODY_A": (0.308, 0.180, 0.687, 0.288),
    "BODY_B": (0.307, 0.181, 0.688, 0.289),
    "BODY_C": (0.330, 0.180, 0.667, 0.288),
    "RANK5_PORTRAIT": (0.306, 0.181, 0.689, 0.289),
    "RANK6_PORTRAIT": (0.324, 0.181, 0.670, 0.279),
    "RAINCOAT": (0.251, 0.180, 0.740, 0.288),
}
HEAD_EYE_Y = {"BODY_A": 0.216, "BODY_B": 0.216, "BODY_C": 0.216,
              "RANK5_PORTRAIT": 0.217, "RANK6_PORTRAIT": 0.216,
              "RAINCOAT": 0.216}
HEAD_EYE = {"CAP": 0.654, "KNITCAP": 0.650}
HEAD_ART = {"CAP": "layer_head_cap", "KNITCAP": "layer_head_knitcap"}
# v0.1.27: the raincoat base's own variants, clipped to the hood
# opening (same canvas / face anchors as the standard layers).
HEAD_ART_RAIN = {"CAP": "layer_head_cap_rain",
                 "KNITCAP": "layer_head_knitcap_rain"}


def head_art(head, base):
    return HEAD_ART_RAIN[head] if base == "RAINCOAT" else HEAD_ART[head]


def body_aspect(base):
    if base == "RANK5_PORTRAIT":
        return 405 / 1024
    if base == "RANK6_PORTRAIT":
        return 448 / 1024
    b = BOX[BODY_ART[base]]
    return (b[2] - b[0]) * CANVAS_W / ((b[3] - b[1]) * CANVAS_H)


def head_layer_rect(head, base, bl, pt, bw, ph):
    """(left, top, w, h): PersonAnchors.headLayerRect's math
    (v0.1.28): non-uniform scale — the layer's face box is drawn
    onto the base's measured target face box (width ratio x,
    height ratio y) — positioned by the face centre x and the
    eye line, for every base including the raincoat."""
    im = img(head_art(head, base))
    iw, ih = im.size
    ll, lt, lr, lb = HEAD_FACE[head]
    tl, tt, tr, tb = HEAD_TARGET[base]
    w = (tr - tl) * bw / (lr - ll)
    h = (tb - tt) * ph / (lb - lt)
    left = bl + (tl + tr) / 2 * bw - (ll + lr) / 2 * w
    top = pt + HEAD_EYE_Y[base] * ph - HEAD_EYE[head] * h
    return left, top, w, h


# ---- v0.1.26: arm layers + umbrella variants ----
# ARM mirrors PersonAnchors.armAnchor (grip, shaft, dw_frac,
# dh_frac, rot in PIL degrees); placement mirrors
# tools/sleeve_arms/preview_sleeve_arms.py, the source of truth.
ARM = {
    "arm_polo_umbrella_open":
        {"grip": (0.72164, 0.84), "shaft": 0.70860, "dw_frac": 1.38003, "dh_frac": 0.95, "rot": 0},
    "arm_polo_umbrella_folding":
        {"grip": (0.40, 0.40), "shaft": None, "dw_frac": None, "dh_frac": 0.70, "rot": 12},
    "arm_polo_parasol":
        {"grip": (0.72164, 0.84), "shaft": 0.70860, "dw_frac": 1.38003, "dh_frac": 0.95, "rot": 0},
    "arm_polo_uchiwa":
        {"grip": (0.235, 0.842), "shaft": None, "dw_frac": 0.690, "dh_frac": 0.351, "rot": 0},
    "arm_cardigan_umbrella_open":
        {"grip": (0.58555, 0.84), "shaft": 0.56458, "dw_frac": 1.43003, "dh_frac": 0.95, "rot": 0},
    "arm_cardigan_umbrella_folding":
        {"grip": (0.42, 0.40), "shaft": None, "dw_frac": None, "dh_frac": 0.70, "rot": 24},
    "arm_knitcoat_umbrella_open":
        {"grip": (0.75382, 0.85), "shaft": 0.73950, "dw_frac": 1.2576, "dh_frac": 0.95, "rot": 0},
    "arm_knitcoat_umbrella_folding":
        {"grip": (0.42, 0.40), "shaft": None, "dw_frac": None, "dh_frac": 0.70, "rot": 24},
    # v0.1.33: the parasols take their rain layers' anchors verbatim.
    "arm_cardigan_parasol":
        {"grip": (0.58555, 0.84), "shaft": 0.56458, "dw_frac": 1.43003, "dh_frac": 0.95, "rot": 0},
    "arm_knitcoat_parasol":
        {"grip": (0.75382, 0.85), "shaft": 0.73950, "dw_frac": 1.2576, "dh_frac": 0.95, "rot": 0},
}
ARM_ART = {  # (top, held) -> drawable name (PersonAnchors.armArt)
    ("TSHIRT", "OPEN"): "arm_polo_umbrella_open",
    ("SHIRT", "OPEN"): "arm_polo_umbrella_open",
    ("TSHIRT", "FOLDED"): "arm_polo_umbrella_folding",
    ("SHIRT", "FOLDED"): "arm_polo_umbrella_folding",
    ("TSHIRT", "PARASOL"): "arm_polo_parasol",
    ("SHIRT", "PARASOL"): "arm_polo_parasol",
    ("CARDIGAN", "OPEN"): "arm_cardigan_umbrella_open",
    ("LIGHT_OUTER", "OPEN"): "arm_cardigan_umbrella_open",
    ("CARDIGAN", "FOLDED"): "arm_cardigan_umbrella_folding",
    ("LIGHT_OUTER", "FOLDED"): "arm_cardigan_umbrella_folding",
    ("KNIT_COAT", "OPEN"): "arm_knitcoat_umbrella_open",
    ("KNIT_COAT", "FOLDED"): "arm_knitcoat_umbrella_folding",
    ("CARDIGAN", "PARASOL"): "arm_cardigan_parasol",
    ("LIGHT_OUTER", "PARASOL"): "arm_cardigan_parasol",
    ("KNIT_COAT", "PARASOL"): "arm_knitcoat_parasol",
}
# (base, top, held) -> (drawable, inner rect) (PersonAnchors.variant*)
VARIANT = {
    ("RANK5_PORTRAIT", None, "OPEN"): ("rank5_umbrella_open",
                                       (0.0039, 0.0818, 0.8056, 0.9999)),
    ("RANK5_PORTRAIT", None, "FOLDED"): ("rank5_umbrella_folding",
                                         (0.0002, -0.0011, 1.0002, 1.0000)),
    ("RANK6_PORTRAIT", None, "OPEN"): ("rank6_umbrella_open",
                                       (0.0037, 0.1361, 0.8830, 0.9999)),
    ("RANK6_PORTRAIT", None, "FOLDED"): ("rank6_umbrella_folding",
                                         (0.0016, -0.0023, 0.9756, 1.0000)),
    ("RAINCOAT", None, "OPEN"): ("raincoat_umbrella_open",
                                 (0.0624, 0.1690, 0.7201, 0.9998)),
    ("RAINCOAT", None, "FOLDED"): ("raincoat_umbrella_folding",
                                   (-0.0001, -0.0007, 0.8948, 1.0000)),
    # v0.1.33: parasol variants — repaints of the OPEN pictures,
    # so the OPEN inner rects verbatim.
    ("RANK5_PORTRAIT", None, "PARASOL"): ("rank5_umbrella_parasol",
                                          (0.0039, 0.0818, 0.8056, 0.9999)),
    ("RANK6_PORTRAIT", None, "PARASOL"): ("rank6_umbrella_parasol",
                                          (0.0037, 0.1361, 0.8830, 0.9999)),
    ("RAINCOAT", None, "PARASOL"): ("raincoat_umbrella_parasol",
                                    (0.0624, 0.1690, 0.7201, 0.9998)),
}


PATCH_FOR = {
    "BODY_B": ("patch_hand_b", (0.685, 0.575, 0.800, 0.670)),
    "BODY_A": ("patch_hand_a", (0.685, 0.575, 0.800, 0.680)),
}


def arm_art(top, held):
    return ARM_ART.get((top, held))


def variant_of(base, top, held):
    return VARIANT.get((base, top, held))


def arm_rect(name, rig, bl, pt, bw, ph):
    a = ARM[name]
    im = img(name)
    dh = a["dh_frac"] * ph
    dw = a["dw_frac"] * bw if a["dw_frac"] else dh * im.size[0] / im.size[1]
    hx = bl + rig[5] * bw
    hy = pt + rig[6] * ph
    anchor_x = a["shaft"] if a["shaft"] is not None else a["grip"][0]
    return (hx - anchor_x * dw, hy - a["grip"][1] * dh, dw, dh)


def arm_poke(spec):
    name = arm_art(spec.get("top"), spec.get("held"))
    if not name:
        return 0.0
    bw = body_aspect(spec["base"]) * 1000.0
    r = arm_rect(name, RIGS[spec["base"]], 0.0, 0.0, bw, 1000.0)
    return max(0.0, -r[1] / 1000.0)


def variant_poke(spec):
    v = variant_of(spec["base"], spec.get("top"), spec.get("held"))
    if not v:
        return 0.0
    inner = v[1]
    return max(0.0, inner[1] / (inner[3] - inner[1]))


def pad_top_ratio(spec):
    import math
    pad = 0.0
    # v0.1.29: a whole-picture variant owns the held item — its
    # poke is the only held-item contribution.
    if variant_of(spec["base"], spec.get("top"), spec.get("held")):
        vpoke = variant_poke(spec)
        if vpoke > 0:
            pad = max(pad, math.ceil((vpoke + 0.005) * 100) / 100)
    else:
        arm = arm_art(spec.get("top"), spec.get("held"))
        if spec.get("held") == "PARASOL" and not arm:
            pad = 0.34
        poke = arm_poke(spec)
        if poke > 0:
            pad = max(pad, math.ceil((poke + 0.005) * 100) / 100)
    head = spec.get("head")
    if head in ("CAP", "KNITCAP"):
        bw = body_aspect(spec["base"]) * 1000.0
        _, top, _, _ = head_layer_rect(head, spec["base"], 0.0, 0.0, bw, 1000.0)
        poke = max(0.0, -top / 1000.0)
        pad = max(pad, math.ceil((poke + 0.005) * 100) / 100)
    return pad


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


def paste_rot(canvas, im, dx, dy, dw, dh, grip, deg):
    """PIL rotation about the grip point (the preview batch's own
    helper): the unrotated rect is (dx, dy, dw, dh) and the grip —
    which the rect puts on the hand — stays fixed."""
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

    variant = variant_of(base, spec.get("top"), spec.get("held"))
    if variant:
        # v0.1.26: the base is swapped whole for its umbrella
        # variant, drawn so the variant's inner rect lands exactly
        # on the standard person rect.
        vname, inner = variant
        bw = ph * body_aspect(base)
        bl = (W - bw) / 2
        dw = bw / (inner[2] - inner[0])
        dh = ph / (inner[3] - inner[1])
        paste(canvas, img(vname),
              (bl - inner[0] * dw, pt - inner[1] * dh, dw, dh))
    elif base in PORTRAITS:
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

    # arm layer (v0.1.26: sleeve + arm + item as one picture, over
    # body + top; the composer rotates about the grip on the hand,
    # negating PIL's angle for Canvas — the mirror IS PIL, so it
    # applies the batch's angle directly, as the previews did)
    arm = None if variant else arm_art(spec.get("top"), spec.get("held"))
    if arm:
        a = ARM[arm]
        r = arm_rect(arm, rig, bl, pt, bw, ph)
        if a["rot"]:
            paste_rot(canvas, img(arm), r[0], r[1], r[2], r[3],
                      a["grip"], a["rot"])
        else:
            paste(canvas, img(arm), r)

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
    # head (v0.1.25: the head layer — hair + hat as one picture,
    # face-anchored onto the rig; the raincoat anchors on the eye
    # line, its rig face top being the hood opening)
    if spec.get("head") in ("CAP", "KNITCAP"):
        hn = spec["head"]
        l, t, w, h = head_layer_rect(hn, base, bl, pt, bw, ph)
        paste(canvas, img(head_art(hn, base)), (l, t, w, h))
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
    # held (v0.1.26: the grip-point fallback — drawn only where
    # neither an arm layer nor an umbrella variant served)
    held = spec.get("held")
    if variant or arm:
        held = None
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
        # v0.1.30: the solved placement — the grip point rides
        # UCHIWA_DX body widths outward of the rig hand and the
        # picture is rotated about the grip by UCHIWA_ROT (PIL
        # degrees), so the disc stands up-outside the fist, clear
        # of the torso front; then the base's own hand patch is
        # pasted over the join so the fist closes over the handle.
        im = img("prop_uchiwa")
        fh = A["UCHIWA_H"] * ph
        fw = fh * im.size[0] / im.size[1]
        gx = hand_x + A["UCHIWA_DX"] * bw
        rx = gx - A["UCHIWA_GRIP_X"] * fw
        ry = hand_y - A["UCHIWA_GRIP_Y"] * fh
        if A["UCHIWA_ROT"]:
            paste_rot(canvas, im, rx, ry, fw, fh,
                      (A["UCHIWA_GRIP_X"], A["UCHIWA_GRIP_Y"]),
                      A["UCHIWA_ROT"])
        else:
            paste(canvas, im, (rx, ry, fw, fh))
        patch = PATCH_FOR.get(base)
        if patch:
            pname, (u0, v0, u1, v1) = patch
            paste(canvas, img(pname),
                  (bl + u0 * bw, pt + v0 * ph, (u1 - u0) * bw,
                   (v1 - v0) * ph))
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
