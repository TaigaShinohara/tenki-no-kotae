#!/usr/bin/env python3
"""Keying + measurement for the v-next layer assets (hair+hat heads,
arm+item arms), generated 2026-11-22.

Same keying as key_assets.py (color-distance alpha + despill), but:
 - head layers are cropped from the full-body edits in SOURCE-canvas
   fractions (x 0..1, y 0..HEAD_CROP_Y) before trimming, and the trim
   offset is recorded so the layer can be placed back in canvas space;
 - every layer gets measured: face box (heads), hair bbox (heads),
   skin/hand clusters and item landmarks (arms).
Outputs PNGs to tools/layers_keyed/ and a JSON report next to this
script. App code and shipped assets are NOT touched.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from key_assets import key_image, skin_blobs  # noqa: E402

SRC = os.path.expanduser("~/workspace/imagine_media")
OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
MAX_EDGE = 1024
HEAD_CROP_Y = 0.378  # canvas fraction: hair top .. just below the
# chin (skin ends ~0.367, the collar starts ~0.385), so the layer's
# bottom edge crosses bare neck and never double-draws a collar

HEADS = {
    "layer_head_cap":
        "media-generation-tenki-layer-head-cap-0-e4539c1b-8e1d-46ae-a539-37472ec1a722.webp",
    "layer_head_knitcap":
        "media-generation-tenki-layer-head-knitcap-0-4df83d66-4c46-4da9-9a12-e626d9032f54.webp",
}
ARMS = {
    "layer_arm_umbrella_open":
        "media-generation-tenki-layer-arm-umbrella-open5-0-201194d5-93f9-4baa-993e-05c4318a1b01.webp",
    "layer_arm_umbrella_open_alt":
        "media-generation-tenki-layer-arm-umbrella-open4-0-b5ea6822-e0e5-459a-920e-bfb63f04d2d7.webp",
    "layer_arm_folding":
        "media-generation-tenki-layer-arm-umbrella-foldi-0-746c318e-daaa-4cba-af33-f12e005e905b.webp",
    "layer_arm_parasol":
        "media-generation-tenki-layer-arm-parasol5-0-40272fc4-73cc-462d-b9b2-132426e5ebee.webp",
    "layer_arm_umbrella_open2":
        "media-generation-tenki-layer-arm-umbrella-open6-0-5aca3f4d-9a2f-4bf2-bb07-a76697ddf368.webp",
    "layer_arm_parasol2":
        "media-generation-tenki-layer-arm-parasol6-0-010ca4bd-2bc7-4321-abbd-0d0c5000b8be.webp",
    "layer_arm_uchiwa":
        "media-generation-tenki-layer-arm-uchiwa-0-acbdeb3f-6b67-4f47-8744-757ecab0e56e.webp",
}

HAIR_RGB = np.array([0x34, 0x4B, 0x86], dtype=np.float32)


def hair_mask(rgba):
    a = np.asarray(rgba).astype(np.float32)
    d = np.sqrt(((a[..., :3] - HAIR_RGB) ** 2).sum(axis=2))
    return (a[..., 3] > 200) & (d < 70)


def bbox_frac(mask):
    ys, xs = np.nonzero(mask)
    if len(xs) == 0:
        return None
    h, w = mask.shape
    return {"left": float(xs.min() / w), "right": float(xs.max() / w),
            "top": float(ys.min() / h), "bottom": float(ys.max() / h)}


def face_box(rgba):
    """Largest skin mass in the top 60% (the face), as fractions."""
    h, w = rgba.size[1], rgba.size[0]
    mask = skin_blobs(rgba)
    top = mask[: int(h * 0.60)]
    ys, xs = np.nonzero(top)
    if len(xs) == 0:
        return None
    return {"cx": float(xs.mean() / w), "top": float(ys.min() / h),
            "bottom": float(ys.max() / h),
            "left": float(xs.min() / w), "right": float(xs.max() / w)}


def skin_clusters(rgba):
    """Skin clusters (crude 8-connectivity via row runs is overkill;
    instead report the skin bbox plus the skin mass touching each
    image edge — the arm root touches the top/right edge)."""
    h, w = rgba.size[1], rgba.size[0]
    mask = skin_blobs(rgba)
    res = {"bbox": bbox_frac(mask)}
    if res["bbox"] is None:
        return res
    top_touch = mask[: max(2, h // 100), :]
    ys, xs = np.nonzero(top_touch)
    if len(xs):
        res["top_edge_x"] = [float(xs.min() / w), float(xs.max() / w)]
    right_touch = mask[:, -max(2, w // 100):]
    ys, xs = np.nonzero(right_touch)
    if len(ys):
        res["right_edge_y"] = [float(ys.min() / h), float(ys.max() / h)]
    # hand = skin cluster in the lower half away from the arm strip:
    # take skin pixels below 45% height; report their bbox + centroid.
    low = mask[int(h * 0.45):, :]
    ys, xs = np.nonzero(low)
    if len(xs):
        res["low_skin"] = {
            "cx": float(xs.mean() / w),
            "cy": float((ys.mean() + h * 0.45) / h),
            "left": float(xs.min() / w), "right": float(xs.max() / w),
            "top": float((ys.min() + h * 0.45) / h),
            "bottom": float((ys.max() + h * 0.45) / h)}
    return res


def finish(rgba, name, extra):
    """Trim already done by caller where needed; downscale + save."""
    scale = min(1.0, MAX_EDGE / max(rgba.size))
    if scale < 1.0:
        rgba = rgba.resize((round(rgba.size[0] * scale),
                            round(rgba.size[1] * scale)), Image.LANCZOS)
    dst = os.path.join(OUT, name + ".png")
    rgba.save(dst, optimize=True)
    return {"final": list(rgba.size), "bytes": os.path.getsize(dst), **extra}


def main():
    os.makedirs(OUT, exist_ok=True)
    report = {}
    for name, fn in HEADS.items():
        full, bbox, key = key_image(os.path.join(SRC, fn))
        W, H = full.size
        crop = full.crop((0, 0, W, round(HEAD_CROP_Y * H)))
        abox = crop.getchannel("A").point(
            lambda v: 255 if v > 8 else 0).getbbox()
        layer = crop.crop(abox)
        # face + hair measured on the FULL keyed edit (canvas space),
        # then re-expressed in layer-image fractions.
        face_full = face_box(full)
        hair_full = bbox_frac(hair_mask(full))
        entry = finish(layer, name, {
            "src": fn, "key": [float(x) for x in key],
            "src_size": [W, H],
            "char_bbox_src_frac": [bbox[0] / W, bbox[1] / H,
                                   bbox[2] / W, bbox[3] / H],
            "crop_canvas_frac": [0.0, 0.0, 1.0, HEAD_CROP_Y],
            "trim_offset_in_crop_frac": [abox[0] / W, abox[1] / (HEAD_CROP_Y * H)],
            "face_canvas_frac": face_full,
            "hair_canvas_frac": hair_full,
        })
        report[name] = entry
        print(name, json.dumps(entry, indent=1))
    for name, fn in ARMS.items():
        out, bbox, key = key_image(os.path.join(SRC, fn))
        W, H = out.size
        layer = out.crop(bbox)
        entry = finish(layer, name, {
            "src": fn, "key": [float(x) for x in key],
            "src_size": [W, H],
            "trim_offset_src_frac": [bbox[0] / W, bbox[1] / H],
            "skin": skin_clusters(layer),
            "hair": bbox_frac(hair_mask(layer)),
        })
        report[name] = entry
        print(name, json.dumps(entry, indent=1))
    with open(os.path.join(os.path.dirname(__file__),
                           "layers_report.json"), "w") as f:
        json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
