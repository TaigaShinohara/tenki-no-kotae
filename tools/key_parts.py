#!/usr/bin/env python3
"""v0.1.19 part pipeline: magenta-key the layered person parts.

Same color-distance keying as key_assets.py (imported, not copied),
applied to the new part family:
  - bodies (3) and tops (7) and the raincoat, generated in one shared
    1120x2240 canvas — the report records each part's alpha bbox as
    fractions of that canvas, so the compositor can realign a trimmed
    PNG onto the shared canvas with per-part calibration on top.
  - props (umbrella / hat / muffler / mittens / face items ...),
    some in the shared canvas, some standalone — those are placed by
    anchors instead, so only their trimmed size matters here.
The mittens arrive as one image holding a pair; they are split into
left / right single-mitten assets at the widest alpha gap near the
middle column.
Output: tools/parts_keyed/<name>.png + tools/parts_report.json.
Nothing is written into res/ here — the adoption decision (which
parts the app actually ships) is made from the preview composites.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from key_assets import key_image, measure  # noqa: E402

SRC = os.path.expanduser("~/workspace/imagine_media")
DST = os.path.join(os.path.dirname(os.path.abspath(__file__)), "parts_keyed")
MAX_EDGE = 1024

FILES = {
    # bodies (shared canvas)
    "body_a": "media-generation-tenki-part-body-a-0-1df86b0c-0f2d-4494-886b-c8c10f1c9427.webp",
    "body_b": "media-generation-tenki-part-body-b-0-00920967-7546-4640-b8c7-b468c571440c.webp",
    "body_c": "media-generation-tenki-part-body-c-0-1f6435f1-fcf7-4126-9fc7-3280205a8948.webp",
    # tops (shared canvas)
    "top_tshirt": "media-generation-tenki-part-top-tshirt-0-25633f0d-6d6d-4edd-8268-ebd622ee9eda.webp",
    "top_shirt": "media-generation-tenki-part-top-shirt-0-4670a7ad-64ae-41d4-a66d-af88ecee6b78.webp",
    "top_cardigan": "media-generation-tenki-part-top-cardigan-0-999c18f5-337f-4c81-869a-1bc4dc6501aa.webp",
    "top_lightouter": "media-generation-tenki-part-top-lightouter-0-25688e0c-56d2-427e-a84a-6a2acdb73665.webp",
    "top_knitcoat": "media-generation-tenki-part-top-knitcoat-0-9920e19f-d3f2-4cf4-ac3d-f8540f4c1319.webp",
    "top_wintercoat": "media-generation-tenki-part-top-wintercoat-0-32af4f5d-8487-4b36-a1c6-5fce9e95f627.webp",
    "top_down": "media-generation-tenki-part-top-down-0-4109bddb-e3da-4bf4-90f0-5d0f89d60ade.webp",
    # full outfit replacement (shared canvas)
    "outfit_raincoat": "media-generation-tenki-part-raincoat-0-29e3b342-6172-4319-84d4-63a08c494c8d.webp",
    # props in the shared canvas
    "prop_umbrella_open2": "media-generation-tenki-part-item-umbrella-open-0-b687c753-4388-4430-8a1f-07a36b9cc219.webp",
    "prop_umbrella_folding2": "media-generation-tenki-part-item-umbrella-foldi-0-ac849b06-82ab-4fc7-87f7-54fe4c86ab9a.webp",
    "prop_cap2": "media-generation-tenki-part-item-hat-0-591ed3cf-f63c-403f-8a71-9337bc091565.webp",
    "prop_muffler": "media-generation-tenki-part-item-muffler-0-2074c1f7-fa5f-463a-b693-acfbb248f602.webp",
    "prop_mittens_pair": "media-generation-tenki-part-item-mittens-0-a6f9f136-7476-4013-ab91-634b0422aa8a.webp",
    "prop_sunglasses_a": "media-generation-tenki-part-item-sunglasses-0-afdda485-84fe-421c-8323-6f8b0083e86f.webp",
    # standalone-canvas candidates
    "prop_mask_a": "media-generation-tenki-item-mask-0-6aea7e84-fa09-4b05-9b40-5fb3d3262968.webp",
    "prop_mask_b": "media-generation-tenki-item-mask-0-85759090-09a7-4087-8c25-30cdf3552a45.webp",
    "prop_sunglasses_b": "media-generation-tenki-item-sunglasses-0-d3f73bb0-5772-4a7c-987d-99d4ebe50ad0.webp",
    "prop_sunglasses_c": "media-generation-tenki-item-sunglasses-0-6f1b82d4-10ec-41a8-b4b2-099ffab46273.webp",
    "prop_knitcap_a": "media-generation-tenki-item-knitcap-0-ad4eef30-cf4f-437c-8c2a-abac3e3fcc69.webp",
    "prop_knitcap_b": "media-generation-tenki-item-knithat-0-323703ff-bb4f-439d-bfdd-8a1352c1c977.webp",
    "prop_towel": "media-generation-tenki-item-towel-0-a8178fad-9451-4bff-bd6b-bf99af8d2b36.webp",
    "prop_parasol": "media-generation-tenki-item-parasol-0-3c001673-7506-4bf0-9d18-e0a4affce2e0.webp",
    "prop_uchiwa": "media-generation-tenki-item-uchiwa-fan-0-292381d6-e642-4de0-a2ed-c0d56eb17f60.webp",
    "prop_umbrella_folding_alt": "media-generation-tenki-item-umbrella-folding-0-fe289dd1-a575-409e-a690-75cf2fc6fa06.webp",
    "prop_cap_alt": "media-generation-tenki-item-hat-cap-0-7335de40-7e42-468e-89d2-5402060e2cbf.webp",
}


# Props whose generated art is far longer than the worn look (scarf /
# towel drawn at full length). Keep only the top fraction of the
# trimmed image so the worn piece ends mid-torso. Anchor-placed props
# only — their bbox_frac is not used for placement.
CROP_KEEP_TOP = {"prop_muffler": 0.56, "prop_towel": 0.52}


def save_trimmed(name, out, bbox, canvas_size, extra=None):
    trimmed = out.crop(bbox)
    if name in CROP_KEEP_TOP:
        keep = round(trimmed.size[1] * CROP_KEEP_TOP[name])
        trimmed = trimmed.crop((0, 0, trimmed.size[0], keep))
        if extra is None:
            extra = {}
        extra["crop_keep_top"] = CROP_KEEP_TOP[name]
    scale = min(1.0, MAX_EDGE / max(trimmed.size))
    final = trimmed
    if scale < 1.0:
        final = trimmed.resize(
            (round(trimmed.size[0] * scale), round(trimmed.size[1] * scale)),
            Image.LANCZOS)
    path = os.path.join(DST, name + ".png")
    final.save(path, optimize=True)
    cw, ch = canvas_size
    entry = {
        "canvas": [cw, ch],
        "bbox_frac": [bbox[0] / cw, bbox[1] / ch, bbox[2] / cw, bbox[3] / ch],
        "trimmed": list(trimmed.size),
        "final": list(final.size),
        "bytes": os.path.getsize(path),
    }
    if extra:
        entry.update(extra)
    return entry


def split_mittens(name, out, bbox, canvas_size):
    """Split the pair image into left/right mittens at the widest
    near-zero alpha column run around the horizontal middle."""
    trimmed = out.crop(bbox)
    a = np.asarray(trimmed.getchannel("A"))
    colsum = (a > 8).sum(axis=0)
    w = trimmed.size[0]
    mid = w // 2
    lo, hi = int(w * 0.35), int(w * 0.65)
    # widest run of (near-)empty columns inside the middle band
    best, run, run_start = (0, -1), 0, lo
    for x in range(lo, hi):
        if colsum[x] <= 2:
            if run == 0:
                run_start = x
            run += 1
            if run > best[0]:
                best = (run, run_start)
        else:
            run = 0
    cut = best[1] + best[0] // 2 if best[0] > 0 else mid
    entries = {}
    for label, box in (("prop_mitten_l", (0, 0, cut, trimmed.size[1])),
                       ("prop_mitten_r", (cut, 0, trimmed.size[0], trimmed.size[1]))):
        piece = trimmed.crop(box)
        pb = piece.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
        piece = piece.crop(pb)
        scale = min(1.0, MAX_EDGE / max(piece.size))
        if scale < 1.0:
            piece = piece.resize(
                (round(piece.size[0] * scale), round(piece.size[1] * scale)),
                Image.LANCZOS)
        path = os.path.join(DST, label + ".png")
        piece.save(path, optimize=True)
        entries[label] = {"from": name, "final": list(piece.size),
                          "bytes": os.path.getsize(path)}
    return entries


def main():
    os.makedirs(DST, exist_ok=True)
    report = {}
    for name, fn in FILES.items():
        src = os.path.join(SRC, fn)
        out, bbox, key = key_image(src)
        canvas = Image.open(src).size
        if name == "prop_mittens_pair":
            report.update(split_mittens(name, out, bbox, canvas))
            continue
        extra = {}
        if name.startswith("body") or name.startswith("outfit"):
            trimmed = out.crop(bbox)
            extra["measure"] = measure(trimmed)
        entry = save_trimmed(name, out, bbox, canvas, extra)
        entry["src"] = fn
        entry["key"] = [float(x) for x in key]
        report[name] = entry
        print(name, entry["final"], "bbox",
              [round(v, 3) for v in entry["bbox_frac"]],
              extra.get("measure", ""))
    with open(os.path.join(os.path.dirname(__file__), "parts_report.json"), "w") as f:
        json.dump(report, f, indent=1)


if __name__ == "__main__":
    main()
