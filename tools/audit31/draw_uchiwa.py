#!/usr/bin/env python3
"""v0.1.31: redraw the fan prop so it READS as a fan.

v0.1.30 shipped a plain light-blue disc the same colour family
as the shirts, with the handle almost entirely under the hand
patch — in the composite it read as a balloon. This redraw keeps
the canvas (800x1024), the grip point (0.502, 0.90) and the
handle span exactly where the v0.1.30 solver expects them, and
changes only the picture:

- face: the near-white (241, 247, 252) sampled from the retired
  v0.1.29 portrait's fan, in a proper uchiwa silhouette — round
  top, sides easing down to a root barely wider than the handle;
- the fan's bones drawn as lines radiating from the root over
  the face, the way a real uchiwa shows them (the portrait's
  solid ink wedge read as an "eye" at prop scale, so lines carry
  the reading instead);
- handle: the family's ink (17, 31, 99), same x span as before
  (357..447), visible below the face.

The outline is the band between the silhouette and an inset
copy of the same silhouette function (one function, two
parameter sets, so the band stays even). Drawn programmatically
at 4x — no generative redraw.
"""
from PIL import Image, ImageDraw
import math

SS = 4
W, H = 800 * SS, 1024 * SS
INK = (17, 31, 99, 255)
FACE = (241, 247, 252, 255)
BONE = (122, 152, 208, 255)

CX = 0.502 * 800  # axis / grip x, final-resolution px
CY = 400.0
TAPER_Y = 580.0
EDGE = 26.0


def circle_half(y, r):
    d = r * r - (y - CY) * (y - CY)
    return math.sqrt(d) if d > 0 else 0.0


def half_width(y, r, root_half, root_y):
    ch = circle_half(y, r)
    if y <= TAPER_Y:
        return ch
    t = (y - TAPER_Y) / (root_y - TAPER_Y)
    t = t ** 1.35  # stay wide, then run in smoothly to the root
    start = circle_half(TAPER_Y, r)
    return min(ch, start + (root_half - start) * t)


def silhouette(r, root_half, root_y):
    top = CY - r
    pts = []
    y = top
    while y < root_y:
        pts.append((CX + half_width(y, r, root_half, root_y), y))
        y += 2.0
    pts.append((CX + root_half, root_y))
    pts.append((CX - root_half, root_y))
    y = root_y
    while y > top:
        y -= 2.0
        pts.append((CX - half_width(y, r, root_half, root_y), y))
    return [(x * SS, yy * SS) for x, yy in pts]


R_OUT, ROOT_OUT, Y_OUT = 368.0, 56.0, 845.0
R_IN, ROOT_IN, Y_IN = R_OUT - EDGE, 30.0, Y_OUT - 24.0

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# ---- handle first (the face overlaps its top) ----
HX0, HX1 = 357.0, 447.0
HY0, HY1 = 790.0, 1023.0
d.rounded_rectangle([HX0 * SS, HY0 * SS, HX1 * SS, HY1 * SS],
                    radius=(HX1 - HX0) / 2 * SS, fill=INK)

# ---- face: ink silhouette, then the inset face colour ----
d.polygon(silhouette(R_OUT, ROOT_OUT, Y_OUT), fill=INK)
inner = silhouette(R_IN, ROOT_IN, Y_IN)
d.polygon(inner, fill=FACE)

# ---- bones: lines from the root, clipped to the inner face ----
bones = Image.new("RGBA", (W, H), (0, 0, 0, 0))
bd = ImageDraw.Draw(bones)
root = (CX * SS, (Y_OUT + 4) * SS)
for a in (-39, -26, -13, 0, 13, 26, 39):
    th = math.radians(-90 + a)
    end = (root[0] + 660 * SS * math.cos(th),
           root[1] + 660 * SS * math.sin(th))
    bd.line([root, end], fill=BONE, width=9 * SS)
mask = Image.new("L", (W, H), 0)
ImageDraw.Draw(mask).polygon(inner, fill=255)
img.alpha_composite(Image.composite(
    bones, Image.new("RGBA", (W, H), (0, 0, 0, 0)), mask))

out = img.resize((800, 1024), Image.BICUBIC)
out.save("app/src/main/res/drawable-nodpi/prop_uchiwa.png")
print("saved prop_uchiwa.png", out.size)
