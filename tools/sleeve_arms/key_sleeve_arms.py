#!/usr/bin/env python3
"""Keying for the sleeve-arm production batch (2026-11-22).

Keys the accepted edit generations (magenta bg) with the project
keyer, trims to the alpha bbox, scales the long edge to 1024 and
saves transparent PNGs to tools/sleeve_arms/keyed/. Also measures
skin clusters (grip candidates for the arm layers) and runs the
unrelated-parts diff check for the full-body (B) edits against
their original bases. App code / shipped assets are NOT touched.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
from key_assets import key_image, skin_blobs  # noqa: E402

SRC = os.path.expanduser("~/workspace/imagine_media")
DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(DIR, "keyed")
MAX_EDGE = 1024

# name -> accepted generation webp
ITEMS = {
    # A: arm layers
    "arm_polo_umbrella_open":
        "media-generation-tenki-sleeve-arm-polo-umbrella-0-64518bee-6266-4245-a8c7-875a854c952d.webp",
    "arm_polo_umbrella_folding":
        "media-generation-tenki-sleeve-arm-polo-umbrella-0-fd0117ba-40cc-4669-80c3-860951039a0c.webp",
    "arm_polo_parasol":
        "media-generation-tenki-sleeve-arm-polo-parasol-0-0a729213-b46e-4dcb-bcdd-6e477ecfd32f.webp",
    "arm_polo_uchiwa":
        "media-generation-tenki-sleeve-arm-polo-uchiwa-0-cda1d14d-98ea-4cb4-af28-061f32114235.webp",
    "arm_cardigan_umbrella_open":
        "media-generation-tenki-sleeve-arm-cardigan-umbr-0-eb31b1ed-0ab0-47fb-877a-bb4873b2946e.webp",
    "arm_cardigan_umbrella_folding":
        "media-generation-tenki-sleeve-arm-cardigan-umbr-0-b032b842-8ffe-4385-8ed1-128236a954f3.webp",
    "arm_knitcoat_umbrella_open":
        "media-generation-tenki-sleeve-arm-knitcoat-umbr-0-85b380c0-ae22-466e-8d4c-423217a3dc2f.webp",
    "arm_knitcoat_umbrella_folding":
        "media-generation-tenki-sleeve-arm-knitcoat-umbr-0-b3efff74-4978-4c93-8a11-5c6cc51de9d3.webp",
    # B: full-body portrait edits
    "rank5_umbrella_open":
        "media-generation-tenki-sleeve-rank5-umbrella-op-0-4aa4c0a3-2f91-48a4-8bf3-860a000817eb.webp",
    "rank5_umbrella_folding":
        "media-generation-tenki-sleeve-rank5-umbrella-fo-0-dd93dd42-841c-4ad6-ae41-1b79e835110a.webp",
    "rank6_umbrella_folding":
        "media-generation-tenki-sleeve-rank6-umbrella-fo-0-f95ae873-7dec-46b9-b813-d70da38bbc94.webp",
    "raincoat_umbrella_open":
        "media-generation-tenki-sleeve-raincoat-umbrella-0-6b94ecbd-cf28-475c-b8ba-faa316ffd252.webp",
    "raincoat_umbrella_folding":
        "media-generation-tenki-sleeve-raincoat-umbrella-0-a3080a6c-2940-4090-93be-c6ea5f40143e.webp",
}

# B items: original base webp for the unrelated-parts diff check
BASES = {
    "rank5_umbrella_open":
        "media-generation-tenki-v6-rank5-coatmuffler-0-4c801651-6b0f-4fa2-8a57-3489faa5b191.webp",
    "rank5_umbrella_folding":
        "media-generation-tenki-v6-rank5-coatmuffler-0-4c801651-6b0f-4fa2-8a57-3489faa5b191.webp",
    "rank6_umbrella_folding":
        "media-generation-tenki-v6-rank6-down-0-07edb834-43f5-4993-82d2-567d851017ee.webp",
    "raincoat_umbrella_open":
        "media-generation-tenki-part-raincoat-0-29e3b342-6172-4319-84d4-63a08c494c8d.webp",
    "raincoat_umbrella_folding":
        "media-generation-tenki-part-raincoat-0-29e3b342-6172-4319-84d4-63a08c494c8d.webp",
}


def skin_clusters_frac(rgba):
    """Skin clusters on a downscaled mask (crude BFS), as fractions
    of the keyed image: list of {cx, cy, left, top, right, bottom,
    mass} sorted by mass desc."""
    mask = skin_blobs(rgba)
    h, w = mask.shape
    ds = 4
    small = mask[::ds, ::ds]
    sh, sw = small.shape
    seen = np.zeros_like(small, dtype=bool)
    clusters = []
    for y0 in range(sh):
        for x0 in range(sw):
            if not small[y0, x0] or seen[y0, x0]:
                continue
            stack = [(y0, x0)]
            seen[y0, x0] = True
            pts = []
            while stack:
                y, x = stack.pop()
                pts.append((x, y))
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        ny, nx = y + dy, x + dx
                        if 0 <= ny < sh and 0 <= nx < sw \
                                and small[ny, nx] and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
            xs = [p[0] * ds / w for p in pts]
            ys = [p[1] * ds / h for p in pts]
            clusters.append({
                "cx": float(np.mean(xs)), "cy": float(np.mean(ys)),
                "left": float(min(xs)), "right": float(max(xs)),
                "top": float(min(ys)), "bottom": float(max(ys)),
                "mass": len(pts)})
    clusters.sort(key=lambda c: -c["mass"])
    return clusters[:4]


def diff_check(new_fn, base_fn):
    a = np.asarray(Image.open(os.path.join(SRC, new_fn))
                   .convert("RGB")).astype(np.float32)
    b = np.asarray(Image.open(os.path.join(SRC, base_fn))
                   .convert("RGB")).astype(np.float32)
    if a.shape != b.shape:
        return {"shape_mismatch": [list(a.shape), list(b.shape)]}
    H, W, _ = a.shape
    regions = {
        "legs+boots (y .68-1, x .25-.75)":
            (slice(int(H * .68), H), slice(int(W * .25), int(W * .75))),
        "left side (y .40-.68, x .05-.30)":
            (slice(int(H * .40), int(H * .68)), slice(int(W * .05), int(W * .30))),
        "face zone (y .24-.38, x .35-.65)":
            (slice(int(H * .24), int(H * .38)), slice(int(W * .35), int(W * .65))),
    }
    return {name: round(float(np.abs(a[ys, xs] - b[ys, xs]).mean()), 1)
            for name, (ys, xs) in regions.items()}


def main():
    os.makedirs(OUT, exist_ok=True)
    report = {}
    for name, fn in ITEMS.items():
        keyed, bbox, key = key_image(os.path.join(SRC, fn))
        layer = keyed.crop(bbox)
        scale = min(1.0, MAX_EDGE / max(layer.size))
        if scale < 1.0:
            layer = layer.resize((round(layer.size[0] * scale),
                                  round(layer.size[1] * scale)),
                                 Image.LANCZOS)
        dst = os.path.join(OUT, name + ".png")
        layer.save(dst, optimize=True)
        entry = {"src": fn, "final": list(layer.size),
                 "bytes": os.path.getsize(dst),
                 "skin_clusters": skin_clusters_frac(layer)}
        if name in BASES:
            entry["diff_vs_base"] = diff_check(fn, BASES[name])
        report[name] = entry
        print(name, json.dumps(entry))
    with open(os.path.join(DIR, "sleeve_arms_report.json"), "w") as f:
        json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
