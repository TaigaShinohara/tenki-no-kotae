#!/usr/bin/env python3
"""v2 prototype (rank6 + umbrella, fixed shaft/hook/canopy):
key the new generation, build preview + crops + v1/v2 comparison."""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
from key_assets import key_image  # noqa: E402

SRC = os.path.expanduser(
    "~/workspace/imagine_media/media-generation-tenki-rank6-umbrella-proto2-0-"
    "c2ec1c69-c95a-4e73-9244-b83eaa8aad0d.webp")
OLD_SRC = os.path.expanduser(
    "~/workspace/imagine_media/media-generation-tenki-rank6-umbrella-proto-0-"
    "1ca83617-ccef-4825-948d-170a631fb47a.webp")
DIR = os.path.expanduser("~/workspace/tenki-no-kotae/tools/prototypes")

# --- key + trim -------------------------------------------------------------
keyed, bbox, key = key_image(SRC)
keyed = keyed.crop(bbox)
# match previous proto: long edge 1024
w, h = keyed.size
scale = 1024 / max(w, h)
if scale < 1:
    keyed = keyed.resize((round(w * scale), round(h * scale)),
                         Image.LANCZOS)
keyed.save(os.path.join(DIR, "rank6_umbrella_sleeve_proto2.png"))
print("keyed:", keyed.size, "key color:", key)

# --- diff check vs previous generation (unrelated parts) --------------------
a = np.asarray(Image.open(SRC).convert("RGB")).astype(np.float32)
b = np.asarray(Image.open(OLD_SRC).convert("RGB")).astype(np.float32)
print("src sizes:", a.shape, b.shape)
if a.shape == b.shape:
    H, W, _ = a.shape
    regions = {
        "legs+boots (y .68-1, x .25-.75)":
            (slice(int(H * .68), H), slice(int(W * .25), int(W * .75))),
        "left arm+coat (y .40-.68, x .05-.30)":
            (slice(int(H * .40), int(H * .68)), slice(int(W * .05), int(W * .30))),
        "face (y .26-.37, x .35-.65)":
            (slice(int(H * .26), int(H * .37)), slice(int(W * .35), int(W * .65))),
    }
    for name, (ys, xs) in regions.items():
        d = np.abs(a[ys, xs] - b[ys, xs]).mean()
        print(f"mean|diff| {name}: {d:.1f}")

# --- preview on the app-like navy gradient ----------------------------------
CW, CH = 760, 900
top = np.array([33, 45, 100], dtype=np.float32)
bot = np.array([37, 82, 122], dtype=np.float32)
grad = np.linspace(0, 1, CH)[:, None, None]
bg = Image.fromarray(
    (top[None, None, :] * (1 - grad) + bot[None, None, :] * grad)
    .repeat(CW, axis=1).astype(np.uint8), "RGB")

fig_h = int(CH * 0.97)
fw = round(keyed.size[0] * fig_h / keyed.size[1])
fig = keyed.resize((fw, fig_h), Image.LANCZOS)
ox = (CW - fw) // 2
oy = CH - fig_h - 6
bg.paste(fig, (ox, oy), fig)
bg.save(os.path.join(DIR, "preview_rank6_umbrella_after2.png"))
print("preview figure box:", ox, oy, fw, fig_h)

# --- crops (fractions of the figure box) ------------------------------------
def crop(name, fx0, fy0, fx1, fy1, out_w=560):
    box = (ox + int(fw * fx0), oy + int(fig_h * fy0),
           ox + int(fw * fx1), oy + int(fig_h * fy1))
    c = bg.crop(box)
    r = out_w / c.size[0]
    c = c.resize((out_w, round(c.size[1] * r)), Image.LANCZOS)
    c.save(os.path.join(DIR, name))
    print(name, c.size)

crop("crop_rank6_grip2.png", 0.52, 0.20, 1.02, 0.52)       # mitten + hook
crop("crop_rank6_shoulder2.png", 0.45, 0.30, 1.02, 0.62)  # arm root/shoulder

# --- comparison: left = previous proto, right = v2 --------------------------
old = Image.open(os.path.join(DIR, "preview_rank6_umbrella_after.png"))
comp = Image.new("RGB", (CW * 2 + 16, CH), (10, 10, 14))
comp.paste(old, (0, 0))
comp.paste(bg, (CW + 16, 0))
comp.save(os.path.join(DIR, "compare_rank6_umbrella_v1_v2.png"))
print("comparison:", comp.size)
