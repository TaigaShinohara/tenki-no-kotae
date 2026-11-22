#!/usr/bin/env python3
"""v0.1.16 asset pipeline: magenta-key the generated person/item art.

For each source webp (uniform #FF00FF background):
  1. alpha from RGB distance to the sampled key color (soft ramp) —
     pure color keying, NOT connected components, so detached marks
     that belong to the character (shiver lines, sweat drops) survive.
  2. despill: pull the magenta cast (min(r,b) - g) out of edge pixels.
  3. trim to the alpha bounding box.
  4. downscale so the long edge is <= 1024 px.
Also measures, per rank, the face / hand positions (skin-tone blobs)
in trimmed-image normalized coordinates, for the compositor anchors.
Writes PNGs into app/src/main/res/drawable-nodpi/ and a JSON report.
"""
import json
import os

import numpy as np
from PIL import Image

SRC = os.path.expanduser("~/workspace/imagine_media")
DST = os.path.expanduser(
    "~/workspace/tenki-no-kotae/app/src/main/res/drawable-nodpi")
MAX_EDGE = 1024
T0, T1 = 45.0, 130.0  # alpha ramp on RGB distance to key color

FILES = {
    "person_rank0": "media-generation-tenki-v6-rank0-tshirt-0-18accfc6-9bf5-485d-ba01-46b16f5b879c.webp",
    "person_rank1": "media-generation-tenki-person-design-v6-eyes-0-c207aa38-425d-4d98-a462-8bd6accc6baf.webp",
    "person_rank2": "media-generation-tenki-v6-rank2-cardigan-0-fc85a7cf-fba3-429a-bcb1-31a093ccff84.webp",
    "person_rank3": "media-generation-tenki-v6-rank3-trainer-0-340f1c76-1ee5-43c0-ac5b-d668b49ac752.webp",
    "person_rank4": "media-generation-tenki-v6-rank4-coat-0-78e17b20-4ce3-47a8-bf4c-8bfc7ffaaba4.webp",
    "person_rank5": "media-generation-tenki-v6-rank5-coatmuffler-0-4c801651-6b0f-4fa2-8a57-3489faa5b191.webp",
    "person_rank6": "media-generation-tenki-v6-rank6-down-0-07edb834-43f5-4993-82d2-567d851017ee.webp",
    "item_umbrella_open": "media-generation-tenki-item-umbrella-open-0-2861bd76-3bf4-48b9-a056-29374efdf1a5.webp",
    "item_umbrella_folding": "media-generation-tenki-item-umbrella-folding-0-44b68528-e2e2-44c3-bacb-54e82b37b0c7.webp",
    "item_hat": "media-generation-tenki-item-hat-0-423e38ee-b2ee-4fc8-96af-81438f070055.webp",
}


def key_image(path):
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(np.float32)
    h, w, _ = a.shape
    corners = np.concatenate([
        a[:24, :24].reshape(-1, 3), a[:24, -24:].reshape(-1, 3),
        a[-24:, :24].reshape(-1, 3), a[-24:, -24:].reshape(-1, 3)])
    key = np.median(corners, axis=0)
    dist = np.sqrt(((a - key) ** 2).sum(axis=2))
    alpha = np.clip((dist - T0) / (T1 - T0), 0.0, 1.0)

    # Despill: character palette (navy / pale skin / white / gray) has
    # min(r,b)-g <= ~0 everywhere; anything above +15 is key fringe.
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    excess = np.clip(np.minimum(r, b) - g - 15.0, 0.0, None)
    a[..., 0] = r - excess
    a[..., 2] = b - excess

    rgba = np.dstack([a, alpha * 255.0]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA")
    bbox = out.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    return out, bbox, key


def skin_blobs(rgba):
    """Skin = pale cyan-white: bright, b >= r >= g-30, low saturation."""
    a = np.asarray(rgba).astype(np.int32)
    r, g, b, al = a[..., 0], a[..., 1], a[..., 2], a[..., 3]
    mask = (al > 200) & (r > 175) & (g > 195) & (b > 205) & \
        (np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b) < 60)
    return mask


def measure(rgba):
    """Face (largest skin mass in top 40%) and hands (skin in the
    52-70% height band, split at the horizontal center)."""
    h, w = rgba.size[1], rgba.size[0]
    mask = skin_blobs(rgba)
    res = {}
    top = mask[: int(h * 0.40)]
    ys, xs = np.nonzero(top)
    if len(xs):
        res["face"] = {
            "cx": float(xs.mean() / w), "top": float(ys.min() / h),
            "bottom": float(ys.max() / h),
            "left": float(xs.min() / w), "right": float(xs.max() / w)}
    band = mask[int(h * 0.52): int(h * 0.72)]
    for name, sl in (("handL", slice(0, w // 2)), ("handR", slice(w // 2, w))):
        ys, xs = np.nonzero(band[:, sl])
        if len(xs) > 30:
            off = 0 if name == "handL" else w // 2
            res[name] = {
                "cx": float((xs.mean() + off) / w),
                "cy": float((ys.mean() + h * 0.52) / h)}
    return res


def main():
    os.makedirs(DST, exist_ok=True)
    report = {}
    for name, fn in FILES.items():
        out, bbox, key = key_image(os.path.join(SRC, fn))
        trimmed = out.crop(bbox)
        if name == "item_umbrella_open":
            # Keep the canopy + a shaft stub (top 60%): the generated
            # shaft is too short to reach the hand at any scale that
            # keeps the dome over the head, so the compositor extends
            # the shaft with a code-drawn line in the sampled color.
            trimmed = trimmed.crop(
                (0, 0, trimmed.size[0], round(trimmed.size[1] * 0.60)))
            px = trimmed.convert("RGB").getpixel(
                (round(trimmed.size[0] * 0.507), round(trimmed.size[1] * 0.95)))
            entry_extra = {"shaft_rgb": list(px), "cropped_to_top60": True}
        else:
            entry_extra = {}
        entry = {"src": fn, "key": [float(x) for x in key],
                 "trimmed": list(trimmed.size), **entry_extra}
        if name.startswith("person"):
            entry["measure"] = measure(trimmed)
        scale = min(1.0, MAX_EDGE / max(trimmed.size))
        if scale < 1.0:
            trimmed = trimmed.resize(
                (round(trimmed.size[0] * scale), round(trimmed.size[1] * scale)),
                Image.LANCZOS)
        dst = os.path.join(DST, name + ".png")
        trimmed.save(dst, optimize=True)
        entry["final"] = list(trimmed.size)
        entry["bytes"] = os.path.getsize(dst)
        report[name] = entry
        print(name, entry)
    with open(os.path.join(os.path.dirname(__file__), "key_report.json"), "w") as f:
        json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
