#!/usr/bin/env python3
"""Composite previews for the v0.1.19 layered 「今日の人」.

Mirrors the model PersonComposer (Kotlin) will use:

  - Bodies and tops share one generated canvas (1120x2240). A part's
    place in that canvas is its alpha bbox (tools/parts_report.json).
    The figure frame is the canvas scaled so the BODY's bbox height
    is the drawn person height; every canvas-family part is drawn at
    its bbox inside that frame, adjusted by a per-part calibration
    (scale about the bbox center, then dx/dy in body fractions).
  - Standalone props are placed by a "rig": the drawn body rect plus
    measured face box / neck / hand fractions. The same rig shape is
    produced for the old full portraits (hybrid bases), so one prop
    drawer serves both.

Tune CAL / ANCH here by eye; the numbers port to PersonAnchors.
"""
import json
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
KEYED = os.path.join(HERE, "parts_keyed")
RES = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
OUT = os.path.join(HERE, "previews2")
REPORT = json.load(open(os.path.join(HERE, "parts_report.json")))

CANVAS_W, CANVAS_H = 1120.0, 2240.0

P = {name: Image.open(os.path.join(KEYED, name + ".png")).convert("RGBA")
     for name in REPORT if os.path.exists(os.path.join(KEYED, name + ".png"))}
OLD = {i: Image.open(f"{RES}/person_rank{i}.png").convert("RGBA") for i in range(7)}
OLD_UMB = Image.open(f"{RES}/item_umbrella_open.png").convert("RGBA")
OLD_FOLD = Image.open(f"{RES}/item_umbrella_folding.png").convert("RGBA")
OLD_HAT = Image.open(f"{RES}/item_hat.png").convert("RGBA")

# ---- per-body measurements (fractions of the trimmed body image) ----
# face = (cx, top, bottom, left, right)
BODY = {
    "body_a": {"face": (0.502, 0.180, 0.399, 0.248, 0.747),
               "handR": (0.753, 0.582), "handL": (0.243, 0.581), "neck": 0.415},
    "body_b": {"face": (0.480, 0.180, 0.337, 0.307, 0.689),
               "handR": (0.732, 0.630), "handL": (0.264, 0.630), "neck": 0.36},
    "body_c": {"face": (0.519, 0.180, 0.336, 0.330, 0.668),
               "handR": (0.893, 0.629), "handL": (0.106, 0.629), "neck": 0.35},
    "outfit_raincoat": {"face": (0.507, 0.081, 0.336, 0.201, 0.743),
                        "handR": (0.848, 0.629), "handL": (0.148, 0.629),
                        "neck": 0.35},
}
OLD_MEAS = {
    0: {"face": (0.498, 0.150, 0.399, 0.111, 0.884),
        "handR": (0.637, 0.569), "handL": (0.359, 0.569), "neck": 0.42},
    1: {"face": (0.501, 0.181, 0.339, 0.307, 0.689),
        "handR": (0.731, 0.633), "handL": (0.264, 0.632), "neck": 0.36},
    2: {"face": (0.490, 0.181, 0.337, 0.306, 0.691),
        "handR": (0.733, 0.634), "handL": (0.262, 0.634), "neck": 0.36},
    3: {"face": (0.499, 0.181, 0.399, 0.246, 0.751),
        "handR": (0.726, 0.614), "handL": (0.272, 0.615), "neck": 0.42},
    4: {"face": (0.509, 0.179, 0.342, 0.305, 0.690),
        "handR": (0.767, 0.627), "handL": (0.228, 0.627), "neck": 0.36},
    5: {"face": (0.489, 0.180, 0.314, 0.306, 0.690),
        "handR": (0.756, 0.629), "handL": (0.242, 0.629), "neck": 0.34},
    6: {"face": (0.497, 0.180, 0.330, 0.323, 0.671),
        "handR": (0.80, 0.63), "handL": (0.20, 0.63), "neck": 0.35},
}

# ---- calibration: per top (scaleX, scaleY, dx, dy), dx/dy in body
# fractions. Non-uniform scale is allowed: the generated tops are
# product-shot proportions (longer than a worn garment), so the
# vertical axis is compressed to the worn length. ----
CAL = {
    "top_tshirt": (1.0, 1.0, 0.0, 0.0),
    "top_shirt": (1.0, 1.0, 0.0, 0.0),
    "top_cardigan": (0.82, 0.55, 0.0, 0.05),
    "top_lightouter": (1.0, 1.0, 0.0, 0.0),
    "top_knitcoat": (1.0, 0.83, 0.0, 0.04),
    "top_wintercoat": (1.0, 1.0, 0.0, 0.0),
    "top_down": (1.0, 1.0, 0.0, 0.0),
}

# ---- prop anchors (fractions of the drawn body rect) ----
ANCH = {
    # cap / knitcap: width / body width; bottom / body height
    "cap_w": 0.48, "cap_bottom": 0.26,
    "knitcap_w": 0.54, "knitcap_bottom": 0.245,
    # mask: width / face width; top = face top + frac * face height
    "mask_w": 1.06, "mask_top": 0.46,
    # sunglasses: width / face width; center = face top + frac * face height
    "sung_w": 1.12, "sung_cy": 0.57,
    # muffler: height / body height; top / body height (asset cropped)
    "muffler_h": 0.32, "muffler_top": 0.29,
    # towel: width / body width; top / body height (asset cropped)
    "towel_w": 0.50, "towel_top": 0.355,
    # mittens: height / body height
    "mitten_h": 0.115,
    # folding umbrella: height / body height; center dx from hand
    "fold_h": 0.30, "fold_dx": 0.10, "fold_bottom_dy": 0.05,
    # parasol: width / body width; top / body height
    "parasol_w": 1.15, "parasol_top": -0.30,
    # uchiwa: height / body height; center dx from hand; bottom dy from hand
    "uchiwa_h": 0.21, "uchiwa_dx": 0.12, "uchiwa_bottom_dy": 0.03,
    # old open umbrella (kept asset): the exact v0.1.16 Kotlin math —
    # canopy width / body width, shaft anchor at this fraction of the
    # body width, the asset's own shaft center, canopy top.
    "open_w": 1.12, "open_top": 0.005, "open_shaft_at": 0.68,
    "open_shaft_x": 0.507, "open_shaft_w": 0.030,
}


def sky(w, h, top=(13, 32, 64), bottom=(38, 84, 124)):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(
            round(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return img.convert("RGBA")


def paste(canvas, img, rect):
    x, y, w, h = rect
    if w <= 0 or h <= 0:
        return
    canvas.alpha_composite(
        img.resize((max(1, round(w)), max(1, round(h))), Image.LANCZOS),
        (round(x), round(y)))


class Rig:
    """Drawn body rect + measured points, all in pixels."""

    def __init__(self, left, top, w, h, meas):
        self.l, self.t, self.w, self.h = left, top, w, h
        self.face = meas["face"]
        self.handR = meas["handR"]
        self.handL = meas["handL"]
        self.neck = meas["neck"]

    def xy(self, fx, fy):
        return (self.l + fx * self.w, self.t + fy * self.h)

    @property
    def face_w(self):
        return (self.face[3] and (self.face[4] - self.face[3])) * self.w

    @property
    def face_h(self):
        return (self.face[2] - self.face[1]) * self.h


class Frame:
    """The shared-canvas frame for one body."""

    def __init__(self, body_name, person_h, origin_x, origin_y):
        bb = REPORT[body_name]["bbox_frac"]
        self.bx0, self.by0, self.bx1, self.by1 = bb
        self.scale = person_h / ((self.by1 - self.by0) * CANVAS_H)
        self.cw = CANVAS_W * self.scale
        self.ch = CANVAS_H * self.scale
        self.ox = origin_x - self.bx0 * self.cw
        self.oy = origin_y - self.by0 * self.ch
        self.body_w = (self.bx1 - self.bx0) * self.cw

    def part_rect(self, name, cal=(1.0, 1.0, 0.0, 0.0), body_w=0.0, body_h=0.0):
        bb = REPORT[name]["bbox_frac"]
        x0 = self.ox + bb[0] * self.cw
        y0 = self.oy + bb[1] * self.ch
        x1 = self.ox + bb[2] * self.cw
        y1 = self.oy + bb[3] * self.ch
        sx, sy, dx, dy = cal
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        w, h = (x1 - x0) * sx, (y1 - y0) * sy
        return (cx - w / 2 + dx * body_w, cy - h / 2 + dy * body_h, w, h)


def draw_marks(canvas, rig, shiver=False, sweat=False):
    d = ImageDraw.Draw(canvas)
    navy = (10, 26, 60, 255)
    if sweat:
        for sx in (0.16, 0.86):
            cx, cy = rig.xy(sx, 0.30)
            r = 0.022 * rig.h
            d.ellipse([cx - r * 0.62, cy - r, cx + r * 0.62, cy + r],
                      fill=(168, 216, 240, 255))
            d.polygon([(cx - r * 0.62, cy - r * 0.2), (cx + r * 0.62, cy - r * 0.2),
                       (cx, cy - r * 1.5)], fill=(168, 216, 240, 255))
    if shiver:
        for sx in (0.045, 0.955):
            x0, _ = rig.xy(sx, 0.0)
            pts = []
            for i in range(11):
                yy = rig.t + (0.42 + i * 0.028) * rig.h
                xx = x0 + (0.018 * rig.w if i % 2 else -0.018 * rig.w)
                pts.append((xx, yy))
            d.line(pts, fill=navy, width=max(2, round(0.006 * rig.h)))


def draw_props(canvas, rig, neck=None, mittens=False, head=None, head_img=None,
               mask=False, mask_img="prop_mask_a", sunglasses=None, held=None):
    face_cx, face_top, face_bot = rig.face[0], rig.face[1], rig.face[2]
    face_w, face_h = rig.face_w, rig.face_h
    hand = rig.xy(*rig.handR)
    hand_l = rig.xy(*rig.handL)
    if neck == "MUFFLER":
        img = P["prop_muffler"]
        h = ANCH["muffler_h"] * rig.h
        w = h * img.size[0] / img.size[1]
        cx, _ = rig.xy(0.5, 0)
        paste(canvas, img, (cx - w / 2, rig.t + ANCH["muffler_top"] * rig.h, w, h))
    if neck == "TOWEL":
        img = P["prop_towel"]
        w = ANCH["towel_w"] * rig.w
        h = w * img.size[1] / img.size[0]
        cx, _ = rig.xy(0.5, 0)
        paste(canvas, img, (cx - w / 2, rig.t + ANCH["towel_top"] * rig.h, w, h))
    if mittens:
        mh = ANCH["mitten_h"] * rig.h
        for img_name, hp in (("prop_mitten_l", hand_l), ("prop_mitten_r", hand)):
            img = P[img_name]
            mw = mh * img.size[0] / img.size[1]
            paste(canvas, img, (hp[0] - mw / 2, hp[1] - mh * 0.42, mw, mh))
    if head in ("CAP", "KNITCAP"):
        if head_img is not None:
            img = head_img
            wf, bot = ANCH["cap_w"], ANCH["cap_bottom"]
        elif head == "CAP":
            img, wf, bot = P["prop_cap2"], ANCH["cap_w"], ANCH["cap_bottom"]
        else:
            img, wf, bot = P["prop_knitcap_a"], ANCH["knitcap_w"], ANCH["knitcap_bottom"]
        w = wf * rig.w
        h = w * img.size[1] / img.size[0]
        cx, _ = rig.xy(face_cx, 0)
        paste(canvas, img, (cx - w / 2, rig.t + bot * rig.h - h, w, h))
    if mask:
        img = P[mask_img]
        w = ANCH["mask_w"] * face_w
        h = w * img.size[1] / img.size[0]
        cx, _ = rig.xy(face_cx, 0)
        top_y = rig.t + (face_top + ANCH["mask_top"] * (face_bot - face_top)) * rig.h
        paste(canvas, img, (cx - w / 2, top_y, w, h))
    if sunglasses:
        img = P[sunglasses]
        w = ANCH["sung_w"] * face_w
        h = w * img.size[1] / img.size[0]
        cx, _ = rig.xy(face_cx, 0)
        cy = rig.t + (face_top + ANCH["sung_cy"] * (face_bot - face_top)) * rig.h
        paste(canvas, img, (cx - w / 2, cy - h / 2, w, h))
    if held == "FOLDED":
        img = P["prop_umbrella_folding2"]
        fh = ANCH["fold_h"] * rig.h
        fw = fh * img.size[0] / img.size[1]
        paste(canvas, img, (hand[0] + ANCH["fold_dx"] * rig.w - fw / 2,
                            hand[1] + ANCH["fold_bottom_dy"] * rig.h - fh, fw, fh))
    if held == "OPEN_OLD":
        img = OLD_UMB
        uw = ANCH["open_w"] * rig.w
        uh = uw * img.size[1] / img.size[0]
        u_left = rig.l + ANCH["open_shaft_at"] * rig.w - ANCH["open_shaft_x"] * uw
        u_top = rig.t + ANCH["open_top"] * rig.h
        shaft_x = u_left + ANCH["open_shaft_x"] * uw
        d = ImageDraw.Draw(canvas)
        d.line([(shaft_x, u_top + uh), hand], fill=(0, 27, 68, 255),
               width=max(2, round(ANCH["open_shaft_w"] * uw)))
        paste(canvas, img, (u_left, u_top, uw, uh))
    if held == "PARASOL":
        img = P["prop_parasol"]
        uw = ANCH["parasol_w"] * rig.w
        uh = uw * img.size[1] / img.size[0]
        paste(canvas, img, (hand[0] - 0.462 * uw,
                            rig.t + ANCH["parasol_top"] * rig.h, uw, uh))
    if held == "UCHIWA":
        img = P["prop_uchiwa"]
        fh = ANCH["uchiwa_h"] * rig.h
        fw = fh * img.size[0] / img.size[1]
        paste(canvas, img, (hand[0] + ANCH["uchiwa_dx"] * rig.w - fw / 2,
                            hand[1] + ANCH["uchiwa_bottom_dy"] * rig.h - fh,
                            fw, fh))


def compose(body_name, top=None, person_h=760, margin=190, **props):
    bb = REPORT[body_name]["bbox_frac"]
    body_w = person_h * (bb[2] - bb[0]) * CANVAS_W / ((bb[3] - bb[1]) * CANVAS_H)
    W = round(body_w + margin * 2)
    H = person_h + 60
    canvas = sky(W, H)
    fr = Frame(body_name, person_h, margin, 30)
    rig = Rig(margin, 30, body_w, person_h, BODY[body_name])
    paste(canvas, P[body_name], fr.part_rect(body_name))
    if top:
        paste(canvas, P[top], fr.part_rect(top, CAL[top], body_w, person_h))
    shiver = props.pop("shiver", False)
    sweat = props.pop("sweat", False)
    draw_props(canvas, rig, **props)
    draw_marks(canvas, rig, shiver=shiver, sweat=sweat)
    return canvas.convert("RGB")


def compose_old(rank, person_h=760, margin=190, **props):
    person = OLD[rank]
    pw = person_h * person.size[0] / person.size[1]
    W = round(pw + margin * 2)
    H = person_h + 60
    canvas = sky(W, H)
    paste(canvas, person, (margin, 30, pw, person_h))
    rig = Rig(margin, 30, pw, person_h, OLD_MEAS[rank])
    shiver = props.pop("shiver", False)
    sweat = props.pop("sweat", False)
    draw_props(canvas, rig, **props)
    draw_marks(canvas, rig, shiver=shiver, sweat=sweat)
    return canvas.convert("RGB")


def main():
    os.makedirs(OUT, exist_ok=True)
    jobs = {
        "r2_rank0": compose("body_b", "top_tshirt", sweat=True),
        "r2_rank2_cardigan": compose("body_a", "top_cardigan"),
        "r2_rank4_knitcoat": compose("body_a", "top_knitcoat"),
        "r2_rank4_mask": compose("body_a", "top_knitcoat", mask=True),
        "r2_rank4_mask_b": compose("body_a", "top_knitcoat", mask=True,
                                   mask_img="prop_mask_b"),
        "r2_rank6_down": compose("body_c", "top_down", neck="MUFFLER",
                                 mittens=True, shiver=True),
        "r2_rank0_heat": compose("body_b", "top_tshirt", head="CAP",
                                 sunglasses="prop_sunglasses_b", held="UCHIWA",
                                 sweat=True),
        "r2_rank0_parasol": compose("body_b", "top_tshirt", head="CAP",
                                    sunglasses="prop_sunglasses_b",
                                    held="PARASOL", sweat=True),
        "r2_rank0_towel": compose("body_b", "top_tshirt", neck="TOWEL",
                                  sweat=True),
        "r2_rank3_open_old": compose("body_a", "top_lightouter",
                                     held="OPEN_OLD"),
        "r2_hybrid5": compose_old(5, neck="MUFFLER", shiver=True),
        "r2_hybrid5_knitcap": compose_old(5, neck="MUFFLER", head="KNITCAP",
                                          shiver=True),
        "r2_hybrid5_mask": compose_old(5, neck="MUFFLER", mask=True,
                                       shiver=True),
        "r2_raincoat_umb": compose("outfit_raincoat", held="OPEN_OLD"),
    }
    for name, img in jobs.items():
        p = os.path.join(OUT, name + ".png")
        img.save(p)
        print(p)


if __name__ == "__main__":
    main()
