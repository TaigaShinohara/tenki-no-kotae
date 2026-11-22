#!/usr/bin/env python3
"""v0.1.29 before/after pairs: left = v0.1.28 (mirror_v0128,
the polo arm-layer pictures), right = v0.1.29 (mirror_v0129,
the whole-picture portraits). Same spec, same crop both sides.
Plus trios with the plain base figure (no held item) on the
left for the base side-by-side comparison."""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0128 as M28  # noqa: E402
import mirror_v0129 as M29  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
W, H = 760, 900


def join(images, name):
    s = 2
    imgs = [im.resize((im.size[0] * s, im.size[1] * s), Image.LANCZOS)
            for im in images]
    out = Image.new("RGB",
                    (sum(i.size[0] for i in imgs) + 6 * (len(imgs) - 1),
                     imgs[0].size[1]), (10, 10, 16))
    x = 0
    d = ImageDraw.Draw(out)
    for im in imgs:
        out.paste(im, (x, 0))
        x += im.size[0] + 6
        if x < out.size[0]:
            d.line([(x - 4, 0), (x - 4, out.size[1])],
                   fill=(240, 240, 240), width=2)
    out.save(os.path.join(OUT, name))
    print("pair", name, out.size)


def pair(spec, crop, name, trio=False):
    box = (int(W * crop[0]), int(H * crop[1]),
           int(W * crop[2]), int(H * crop[3]))
    a = M28.draw(M28.sky(W, H), spec, W, H).convert("RGB").crop(box)
    b = M29.draw(M29.sky(W, H), spec, W, H).convert("RGB").crop(box)
    if trio:
        plain = dict(spec)
        plain["held"] = "NONE"
        p = M29.draw(M29.sky(W, H), plain, W, H).convert("RGB").crop(box)
        join([p, a, b], name)
    else:
        join([a, b], name)


# A parasol day in the app: UV >= 8, so cap + sunglasses too.
pair({"base": "BODY_B", "top": "TSHIRT", "held": "PARASOL",
      "head": "CAP", "sunglasses": True},
     (0.10, 0.0, 0.95, 0.85), "pair_parasol.png", trio=True)
pair({"base": "BODY_A", "top": "SHIRT", "held": "PARASOL",
      "head": "CAP", "sunglasses": True},
     (0.10, 0.0, 0.95, 0.85), "pair_parasol_rank1.png")
# A fan day: rank 0 only in the app; the neck towel rides along
# on the hottest days. Rank 1 is a hand-built combo (the spec
# derivation never produces it) drawn for the record.
pair({"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA",
      "neck": "TOWEL"},
     (0.10, 0.0, 0.95, 0.85), "pair_uchiwa.png", trio=True)
pair({"base": "BODY_A", "top": "SHIRT", "held": "UCHIWA"},
     (0.10, 0.0, 0.95, 0.85), "pair_uchiwa_rank1.png")
