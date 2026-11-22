#!/usr/bin/env python3
"""Key the regenerated parasol / uchiwa arms, measure grip + sleeve
landmarks, and composite them on BODY_B+TSHIRT with the sleeve-arm
placement rules. Saves *_fixed.png into sleeve_arms/keyed/."""
import json, os, sys
import numpy as np
from PIL import Image

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
sys.path.insert(0, TOOLS)
sys.path.insert(0, os.path.join(TOOLS, "sleeve_arms"))
from key_assets import key_image, skin_blobs  # noqa: E402
import mirror_v0125 as M  # noqa: E402
import preview_sleeve_arms as SA  # noqa: E402
from key_sleeve_arms import skin_clusters_frac  # noqa: E402

SRC = os.path.expanduser("~/workspace/imagine_media")
KEYED = os.path.join(TOOLS, "sleeve_arms", "keyed")
OUT = os.path.join(TOOLS, "illust_fixes", "fix23")
os.makedirs(OUT, exist_ok=True)

ITEMS = {
    "arm_polo_parasol":
        "media-generation-tenki-sleeve-arm-polo-parasol-0-0872fd92-f8f9-4413-b7a3-08c20bba16b8.webp",
    "arm_polo_uchiwa":
        "media-generation-tenki-sleeve-arm-polo-uchiwa-f-0-130ec615-f223-4c30-a767-5bd5b161eac4.webp",
}

report = {}
for name, fn in ITEMS.items():
    keyed, bbox, key = key_image(os.path.join(SRC, fn))
    layer = keyed.crop(bbox)
    scale = min(1.0, 1024 / max(layer.size))
    if scale < 1.0:
        layer = layer.resize((round(layer.size[0] * scale),
                              round(layer.size[1] * scale)), Image.LANCZOS)
    dst = os.path.join(KEYED, name + "_fixed.png")
    layer.save(dst, optimize=True)
    clusters = skin_clusters_frac(layer)
    report[name] = {"src": fn, "final": list(layer.size),
                    "skin_clusters": clusters}
    print(name, "->", dst, layer.size)
    print("   skin clusters:", json.dumps(clusters))
with open(os.path.join(OUT, "key_report.json"), "w") as f:
    json.dump(report, f, indent=1)
