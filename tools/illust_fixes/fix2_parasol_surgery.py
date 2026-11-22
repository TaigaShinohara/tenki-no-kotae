#!/usr/bin/env python3
"""Fix 2 (parasol arm), surgical approach: start from the CURRENT
arm_polo_parasol keyed layer (canopy / shaft / fist geometry is the
production-approved one), erase the detached sleeve blob and the
diagonal bare arm, then draw a new short sleeve (rounded shoulder
capsule with cuff line) sitting over a near-vertical bare arm that
lands on the existing fist. Canvas size and grip point unchanged,
so the existing ARM placement (grip .655/.955, dw 1.10, dh .66)
stays valid and the sleeve lands on the body's shoulder."""
import os, sys
import numpy as np
from PIL import Image, ImageDraw

TOOLS = os.path.expanduser("~/workspace/tenki-no-kotae/tools")
KEYED = os.path.join(TOOLS, "sleeve_arms", "keyed")
OUT = os.path.join(TOOLS, "illust_fixes", "fix23")
os.makedirs(OUT, exist_ok=True)

NAVY = (24, 27, 98, 255)
SKIN = (192, 234, 252, 255)      # canonical #C0EAFC
SLEEVE = (156, 200, 233, 255)    # sampled from the original sleeve
OL = 9                           # outline width (px at 1024 height)

src = Image.open(os.path.join(KEYED, "arm_polo_parasol.png")).convert("RGBA")
W, H = src.size
img = src.copy()
dr = ImageDraw.Draw(img)

# 1) erase sleeve blob + diagonal arm (keep shaft x<0.66, fist y>0.85)
poly = [(0.755 * W, 0.245 * H), (W, 0.245 * H), (W, 0.99 * H),
        (0.80 * W, 0.99 * H), (0.685 * W, 0.875 * H),
        (0.755 * W, 0.60 * H), (0.745 * W, 0.36 * H)]
dr.polygon(poly, fill=(0, 0, 0, 0))

# 2) bare arm: tapered, gently curved strip from under the sleeve to
#    the fist. Drawn as navy underlay + skin inlay polylines.
def strip(points, half_top, half_bot, color, shrink=0.0):
    pts = []
    n = len(points)
    for i, (x, y) in enumerate(points):
        t = i / (n - 1)
        h = (half_top * (1 - t) + half_bot * t) - shrink
        pts.append((x, y, h))
    # build polygon from perpendicular offsets (near-vertical strip:
    # offset mostly in x)
    left, right = [], []
    for i, (x, y, h) in enumerate(pts):
        if i == 0:
            dx, dy = points[1][0] - x, points[1][1] - y
        elif i == n - 1:
            dx, dy = x - points[i - 1][0], y - points[i - 1][1]
        else:
            dx, dy = points[i + 1][0] - points[i - 1][0], \
                     points[i + 1][1] - points[i - 1][1]
        L = max(1.0, (dx * dx + dy * dy) ** 0.5)
        nx, ny = -dy / L, dx / L
        left.append((x + nx * h, y + ny * h))
        right.append((x - nx * h, y - ny * h))
    dr.polygon(left + right[::-1], fill=color)
    # round caps
    for (x, y, h) in (pts[0], pts[-1]):
        dr.ellipse([x - h, y - h, x + h, y + h], fill=color)

arm_line = [(0.725 * W, 0.585 * H), (0.718 * W, 0.74 * H),
            (0.690 * W, 0.905 * H)]
strip(arm_line, 0.044 * W, 0.037 * W, NAVY)
strip(arm_line, 0.044 * W, 0.037 * W, SKIN, shrink=OL)

# 3) sleeve: dome-top capsule centred (0.715W, 0.525H)
cx, cy = 0.715 * W, 0.525 * H
sw, sh = 0.158 * W, 0.205 * H
x0, x1 = cx - sw / 2, cx + sw / 2
y0, y1 = cy - sh / 2, cy + sh / 2
r = sw / 2

def capsule(inflate, fill):
    a = inflate
    dr.pieslice([x0 - a, y0 - a, x1 + a, y0 + 2 * r + a],
                180, 360, fill=fill)
    dr.rectangle([x0 - a, y0 + r, x1 + a, y1 + a * 0.4], fill=fill)
    dr.ellipse([x0 - a, y1 - r * 0.5 - a, x0 + r * 0.5 + a, y1 + a],
               fill=fill)
    dr.ellipse([x1 - r * 0.5 - a, y1 - r * 0.5 - a, x1 + a, y1 + a],
               fill=fill)

capsule(OL, NAVY)
capsule(0, SLEEVE)
# cuff line near the bottom
cuff_y = y1 - 0.038 * H
dr.line([(x0 + 2, cuff_y), (x1 - 2, cuff_y)], fill=NAVY, width=OL)

# 4) restore the original fist on top (covers the arm's bottom seam)
fist_box = (round(0.55 * W), round(0.85 * H), round(0.78 * W), H)
fist = src.crop(fist_box)
fa = np.asarray(fist)[..., 3]
keep = Image.new("RGBA", fist.size, (0, 0, 0, 0))
keep.paste(fist, (0, 0), Image.fromarray(
    np.where(fa > 100, 255, 0).astype(np.uint8)))
img.alpha_composite(keep, fist_box[:2])
# restore the canopy strip the erase polygon may have shaved
strip_box = (0, 0, W, round(0.24 * H))
img.alpha_composite(src.crop(strip_box), strip_box[:2])

dst = os.path.join(KEYED, "arm_polo_parasol_fixed.png")
img.save(dst, optimize=True)
img.convert("RGBA").save(os.path.join(OUT, "parasol_surgery_layer.png"))
print("saved", dst, img.size)

# sanity: no opaque pixel of the old sleeve region remains right of
# x=0.86W above the fist, and sleeve fabric present at the target
a = np.asarray(img)
al = a[..., 3]
print("opaque px x>0.86W, 0.2H<y<0.8H:", int((al[:, int(0.86 * W):] > 24)
      [int(0.2 * H):int(0.8 * H)].sum()))
fab = (np.abs(a[..., :3].astype(int) -
              np.array(SLEEVE[:3])).sum(-1) < 60) & (al > 128)
ys, xs = np.nonzero(fab)
print(f"sleeve fill: cx={xs.mean()/W:.3f} cy={ys.mean()/H:.3f} "
      f"x[{xs.min()/W:.3f},{xs.max()/W:.3f}] y[{ys.min()/H:.3f},{ys.max()/H:.3f}]")
