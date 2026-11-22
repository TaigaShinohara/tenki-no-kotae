#!/usr/bin/env python3
"""Composite previews for the v-next layer assets (tools/layers_keyed/).

Base scenes are rendered with mirror_v0123 (the exact mirror of the
Kotlin composer) WITHOUT the old hat/held props; the new layers are
then overlaid with the placement rules noted per layer:

 - heads: face-anchored (uniform scale; layer face cx/top onto the
   base rig's face cx/top). Also prints the base-hair coverage: the
   share of the base's hair pixels (band y 0.06..0.42 of the body
   rect, outside the face box) covered by the layer's alpha.
 - umbrella / parasol: shaft-anchored (shaft x onto the base hand x,
   canopy width = 1.12 / 1.15 of the body width, layer top at the
   person top / -0.20 with the parasol pad). Prints where the layer's
   hand lands vs the base hand.
 - folding / uchiwa: grip-anchored (grip point onto the base hand),
   drawn height = K * person height (K per layer, tuned by eye).

Outputs to tools/previews_layers/. App code is not touched.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0123 as M  # noqa: E402

LAY = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/previews_layers")
W, H = 760, 900

HAIR_RGB = np.array([0x34, 0x4B, 0x86], dtype=np.float32)

# layer landmarks (fractions of the keyed layer image, by eye from
# the keyed PNGs, cross-checked against layers_report.json)
HEAD_FACE = {  # (left, top, right, bottom) of the face in the layer;
    # y fractions use the canvas anisotropy (1120x2240): the layer's
    # canvas height span is crop_bottom - trim_top (0.325 / 0.322)
    "layer_head_cap": (0.349, 0.557, 0.650, 0.975),
    "layer_head_knitcap": (0.306, 0.550, 0.689, 1.0),
}
# arm landmarks: grip = where the hand holds the item (layer
# fractions). dh_frac = drawn height as a fraction of the person
# height, grip-anchored onto the base hand. rot = degrees
# counterclockwise about the grip (aligns a diagonal arm with the
# body's near-vertical arm line).
ARM = {
    "layer_arm_umbrella_open": {"grip": (0.755, 0.845), "dh_frac": 0.62},
    # open2 / parasol2: non-uniform placement (dw from the body
    # width, dh from the person height) — the canopy layers couple
    # canopy width to shaft length too tightly for a uniform scale.
    "layer_arm_umbrella_open2": {"grip": (0.815, 0.831), "dw_frac": 1.20,
                                 "dh_frac": 0.95},
    "layer_arm_parasol": {"grip": (0.635, 0.905), "dh_frac": 0.52},
    "layer_arm_parasol2": {"grip": (0.655, 0.963), "dw_frac": 1.10,
                           "dh_frac": 0.66},
    "layer_arm_folding": {"grip": (0.33, 0.44), "dh_frac": 0.70, "rot": 24},
    "layer_arm_uchiwa": {"grip": (0.55, 0.925), "dh_frac": 0.45, "rot": -10},
}

# shoulder points (body-rect fractions, by eye from the base art):
# where the arm meets the torso, for the anchor guidance printout.
SHOULDER = {
    "BODY_A": (0.735, 0.345), "BODY_B": (0.71, 0.35),
    "BODY_C": (0.86, 0.35), "RANK5_PORTRAIT": (0.735, 0.345),
    "RANK6_PORTRAIT": (0.78, 0.35),
}


def layer_img(name):
    return Image.open(os.path.join(LAY, name + ".png")).convert("RGBA")


def body_rect(spec, pad=0.0):
    """(bl, pt, bw, ph) of the drawn person, mirror_v0123's math."""
    ph = H / (1.0 + pad)
    pt = H - ph
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


def hair_coverage(layer_alpha_render, spec, rect):
    """Share of base hair pixels covered by the layer's alpha
    (layer_alpha_render: HxW float array in render space)."""
    bl, pt, bw, ph = rect
    a = np.asarray(M.img(M.BODY_ART[spec["base"]]).convert("RGBA")
                   ).astype(np.float32)
    d = np.sqrt(((a[..., :3] - HAIR_RGB) ** 2).sum(axis=2))
    mask = (a[..., 3] > 200) & (d < 70)
    rig = M.RIGS[spec["base"]]
    ftop, fbot, fl, fr = rig[1], rig[2], rig[3], rig[4]
    ys, xs = np.nonzero(mask)
    keep = []
    for x, y in zip(xs[::7], ys[::7]):
        fx, fy = x / a.shape[1], y / a.shape[0]
        if not (0.06 <= fy <= 0.40):
            continue
        if fl - 0.02 <= fx <= fr + 0.02 and ftop - 0.02 <= fy <= fbot + 0.05:
            continue  # face zone: the layer replaces it anyway
        rx, ry = round(bl + fx * bw), round(pt + fy * ph)
        if 0 <= rx < W and 0 <= ry < H:
            keep.append(layer_alpha_render[ry, rx] > 0.5)
    return (sum(keep) / len(keep)) if keep else None


def layer_alpha(im, dx, dy, dw, dh):
    """The layer's alpha rendered alone on an empty W x H canvas."""
    c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    paste(c, im, dx, dy, dw, dh)
    return np.asarray(c.getchannel("A")).astype(np.float32) / 255.0


def head_preview(base, top, layer_name, out_name):
    spec = {"base": base, "top": top}
    canvas = M.draw(M.sky(W, H), spec, W, H)
    bl, pt, bw, ph = body_rect(spec)
    rig = M.RIGS[base]
    face_l, face_t, face_r = rig[3], rig[1], rig[4]
    ll, lt, lr, lb = HEAD_FACE[layer_name]
    im = layer_img(layer_name)
    s = (face_r - face_l) * bw / ((lr - ll) * im.size[0])
    dw, dh = im.size[0] * s, im.size[1] * s
    dx = bl + (face_l + face_r) / 2 * bw - (ll + lr) / 2 * dw
    dy = pt + face_t * ph - lt * dh
    paste(canvas, im, dx, dy, dw, dh)
    cov = hair_coverage(layer_alpha(im, dx, dy, dw, dh), spec,
                        (bl, pt, bw, ph))
    canvas.convert("RGB").save(os.path.join(OUT, out_name))
    print(f"{out_name}: layer drawn {dw:.0f}x{dh:.0f} at ({dx:.0f},{dy:.0f}); "
          f"hair coverage {cov if cov is None else round(cov, 3)}")


def paste_rot(canvas, im, dx, dy, dw, dh, grip, deg):
    """Paste im scaled to (dw, dh) at (dx, dy), rotated by deg
    (counterclockwise as viewed) about the grip point."""
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


def arm_preview(base, top, layer_name, out_name, pad=0.0, **override):
    spec = {"base": base, "top": top}
    canvas = M.draw(M.sky(W, H), spec, W, H)
    bl, pt, bw, ph = body_rect(spec, pad)
    rig = M.RIGS[base]
    hand_x, hand_y = bl + rig[5] * bw, pt + rig[6] * ph
    im = layer_img(layer_name)
    cfg = dict(ARM[layer_name], **override)
    dh = cfg["dh_frac"] * ph
    dw = (cfg["dw_frac"] * bw if "dw_frac" in cfg
          else dh * im.size[0] / im.size[1])
    dx = hand_x - cfg["grip"][0] * dw
    dy = hand_y - cfg["grip"][1] * dh
    if cfg.get("rot"):
        paste_rot(canvas, im, dx, dy, dw, dh, cfg["grip"], cfg["rot"])
    else:
        paste(canvas, im, dx, dy, dw, dh)
    canvas.convert("RGB").save(os.path.join(OUT, out_name))
    print(f"{out_name}: layer drawn {dw:.0f}x{dh:.0f} at ({dx:.0f},{dy:.0f}) "
          f"rot={cfg.get('rot', 0)}; grip on hand by construction")


def main():
    os.makedirs(OUT, exist_ok=True)
    head_preview("BODY_A", "SHIRT", "layer_head_cap", "head_cap_body_a.png")
    head_preview("BODY_B", "TSHIRT", "layer_head_cap", "head_cap_body_b.png")
    head_preview("BODY_C", "DOWN", "layer_head_cap", "head_cap_body_c.png")
    head_preview("RANK5_PORTRAIT", None, "layer_head_cap", "head_cap_rank5.png")
    head_preview("BODY_A", "SHIRT", "layer_head_knitcap", "head_knit_body_a.png")
    head_preview("BODY_B", "TSHIRT", "layer_head_knitcap", "head_knit_body_b.png")
    head_preview("BODY_C", "DOWN", "layer_head_knitcap", "head_knit_body_c.png")
    head_preview("RANK6_PORTRAIT", None, "layer_head_knitcap", "head_knit_rank6.png")
    arm_preview("BODY_A", "LIGHT_OUTER", "layer_arm_umbrella_open2",
                "arm_umbrella2_body_a.png")
    arm_preview("RANK6_PORTRAIT", None, "layer_arm_umbrella_open2",
                "arm_umbrella2_rank6.png")
    arm_preview("BODY_A", "SHIRT", "layer_arm_folding", "arm_folding_body_a.png")
    arm_preview("BODY_B", "TSHIRT", "layer_arm_parasol2",
                "arm_parasol2_body_b.png")
    arm_preview("BODY_B", "TSHIRT", "layer_arm_uchiwa", "arm_uchiwa_body_b.png")
    arm_preview("BODY_A", "SHIRT", "layer_arm_uchiwa", "arm_uchiwa_body_a.png")


if __name__ == "__main__":
    main()
