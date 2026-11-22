#!/usr/bin/env python3
"""v0.1.27 audit: before (v0.1.26) / after (v0.1.27) pairs.

Renders the same spec twice with the v0.1.27 mirror
(tools/mirror_v0127.py): the AFTER instance as shipped, and a
BEFORE instance mutated back to the v0.1.26 tables with its image
cache pre-seeded from tools/audit27/before_assets/ (the drawable
files as v0.1.26 shipped them). Each pair is one image, before on
the left, after on the right, same composition and crop — the
enlarged crops are the point: the user judges the fixed look.
"""
import importlib.util
import os

from PIL import Image

ROOT = os.path.expanduser("~/workspace/tenki-no-kotae")
MIRROR = os.path.join(ROOT, "tools/mirror_v0127.py")
BEFORE_DIR = os.path.join(ROOT, "tools/audit27/before_assets")
OUT = os.path.join(ROOT, "tools/audit27")
W, H = 760, 900


def load(name):
    spec = importlib.util.spec_from_file_location(name, MIRROR)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


after = load("mirror_after")
before = load("mirror_before")

# ---- mutate the BEFORE instance back to v0.1.26 ----
before.TOPCAL["top_tshirt"] = (0.70, 0.875, 0.0, 0.028)
rig = list(before.RIGS["RAINCOAT"])
rig[-1] = 0.464
before.RIGS["RAINCOAT"] = tuple(rig)
before.ARM["arm_polo_uchiwa"]["grip"] = (0.47, 0.93)
before.VARIANT[("RANK5_PORTRAIT", "OPEN")] = (
    "rank5_umbrella_open", (-0.0017, 0.1074, 0.8364, 0.9999))
before.VARIANT[("RANK6_PORTRAIT", "OPEN")] = (
    "rank6_umbrella_open", (0.0103, 0.0984, 0.8637, 0.9999))
before.head_art = lambda head, base: before.HEAD_ART[head]
for name in ("layer_head_cap", "layer_head_knitcap", "arm_polo_parasol",
             "arm_polo_uchiwa", "prop_towel", "rank5_umbrella_open",
             "rank6_umbrella_open"):
    before.IMG[name] = Image.open(
        os.path.join(BEFORE_DIR, name + ".png")).convert("RGBA")

PAIRS = [
    # (file, spec kwargs for spec_for, crop (x0, y0, x1, y1) in
    # canvas fractions, upscale)
    ("pair_chu1_head_cap.png",
     dict(rank=3, head="CAP"), (0.10, 0.00, 0.90, 0.58), 2),
    ("pair_chu2_parasol.png",
     dict(rank=0, held="PARASOL"), (0.30, 0.02, 1.00, 0.78), 2),
    ("pair_chu3_uchiwa.png",
     dict(rank=0, held="UCHIWA"), (0.28, 0.00, 1.00, 0.72), 2),
    ("pair_sho1_towel.png",
     dict(rank=0, neck="TOWEL"), (0.18, 0.12, 0.82, 0.62), 2),
    ("pair_sho2_raincoat_mask.png",
     dict(base="RAINCOAT", mask=True), (0.18, 0.00, 0.82, 0.52), 2),
    ("pair_sho3_rank5_open.png",
     dict(rank=5, held="OPEN"), (0.06, 0.00, 0.94, 1.00), 1),
    ("pair_sho3_rank6_open.png",
     dict(rank=6, held="OPEN"), (0.06, 0.00, 0.94, 1.00), 1),
    ("pair_sho4_rank0_hands.png",
     dict(rank=0), (0.08, 0.28, 0.92, 0.78), 2),
    ("pair_sho5_raincoat_cap.png",
     dict(base="RAINCOAT", head="CAP"), (0.12, 0.00, 0.88, 0.55), 2),
    ("pair_sho5_raincoat_knitcap.png",
     dict(base="RAINCOAT", head="KNITCAP"), (0.12, 0.00, 0.88, 0.55), 2),
]


def crop(im, box, scale):
    x0, y0, x1, y1 = box
    c = im.crop((int(x0 * W), int(y0 * H), int(x1 * W), int(y1 * H)))
    if scale != 1:
        c = c.resize((c.width * scale, c.height * scale),
                     Image.LANCZOS)
    return c


def main():
    for fname, kw, box, scale in PAIRS:
        b = crop(before.render(before.spec_for(**kw), W, H), box, scale)
        a = crop(after.render(after.spec_for(**kw), W, H), box, scale)
        pair = Image.new("RGB", (b.width + a.width + 8, b.height),
                         (24, 24, 32))
        pair.paste(b, (0, 0))
        pair.paste(a, (b.width + 8, 0))
        pair.save(os.path.join(OUT, fname))
        print(fname, pair.size)


if __name__ == "__main__":
    main()
