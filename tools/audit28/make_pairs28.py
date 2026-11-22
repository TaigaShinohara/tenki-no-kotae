#!/usr/bin/env python3
"""v0.1.28 before/after pairs: left = v0.1.27 (mirror_v0127 +
the old drawable pictures recovered from git HEAD), right =
v0.1.28 (mirror_v0128 + the shipped pictures). Same spec, same
crop on both sides."""
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0127 as M27  # noqa: E402
import mirror_v0128 as M28  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.expanduser("~/workspace/tenki-no-kotae")
W, H = 760, 900


def old_art(name):
    data = subprocess.run(
        ["git", "-C", REPO, "show",
         f"HEAD:app/src/main/res/drawable-nodpi/{name}.png"],
        capture_output=True, check=True).stdout
    path = f"/tmp/old_{name}.png"
    with open(path, "wb") as f:
        f.write(data)
    return Image.open(path).convert("RGBA")


M27.IMG["arm_polo_parasol"] = old_art("arm_polo_parasol")
M27.IMG["arm_polo_uchiwa"] = old_art("arm_polo_uchiwa")


def pair(spec, crop, name):
    a = M27.draw(M27.sky(W, H), spec, W, H).convert("RGB")
    b = M28.draw(M28.sky(W, H), spec, W, H).convert("RGB")
    box = (int(W * crop[0]), int(H * crop[1]),
           int(W * crop[2]), int(H * crop[3]))
    a, b = a.crop(box), b.crop(box)
    s = 2
    a = a.resize((a.size[0] * s, a.size[1] * s), Image.LANCZOS)
    b = b.resize((b.size[0] * s, b.size[1] * s), Image.LANCZOS)
    out = Image.new("RGB", (a.size[0] + b.size[0] + 6, a.size[1]),
                    (10, 10, 16))
    out.paste(a, (0, 0))
    out.paste(b, (a.size[0] + 6, 0))
    ImageDraw.Draw(out).line(
        [(a.size[0] + 2, 0), (a.size[0] + 2, out.size[1])],
        fill=(240, 240, 240), width=2)
    out.save(os.path.join(OUT, name))
    print("pair", name, out.size)


pair({"base": "BODY_B", "top": "TSHIRT", "held": "PARASOL"},
     (0.24, 0.0, 1.0, 0.78), "pair_parasol.png")
pair({"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA"},
     (0.24, 0.0, 1.0, 0.78), "pair_uchiwa.png")
pair({"base": "BODY_B", "top": "TSHIRT", "held": "UCHIWA",
      "neck": "TOWEL"}, (0.24, 0.0, 1.0, 0.78), "pair_uchiwa_towel.png")
# face trio pair comes from verify_faces28 (v28) and its
# mirror_v0127 rerun (old): face3_body_a.png / face3_old_body_a.png
