#!/usr/bin/env python3
"""v0.1.33 verification renders: for every parasol appearance
combo, a three-way panel — rain umbrella (the approved source) |
parasol as shipped in v0.1.32 | parasol as rebuilt in v0.1.33 —
plus a zoom crop of the parasol33 render on the grip / shoulder
(the places the v0.1.32 fallback broke: floating shoulder, sleeve
mismatch, shaft missing the fist). Same specs as render33.py."""
import os
import sys

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0132 as M32  # noqa: E402
import mirror_v0133 as M33  # noqa: E402
from PIL import Image  # noqa: E402
from render33 import COMBOS, build  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit33")


def side_by_side(imgs, path, gap=6):
    w = sum(i.size[0] for i in imgs) + gap * (len(imgs) - 1)
    h = max(i.size[1] for i in imgs)
    out = Image.new("RGB", (w, h), (255, 255, 255))
    x = 0
    for i in imgs:
        out.paste(i, (x, 0))
        x += i.size[0] + gap
    out.save(path)


def main():
    for label, base, top, extra in COMBOS:
        rain = M33.render(build(base, top, "OPEN", extra))
        par32 = M32.render(build(base, top, "PARASOL", extra))
        par33 = M33.render(build(base, top, "PARASOL", extra))
        side_by_side([rain, par32, par33], os.path.join(OUT, f"cmp_{label}.png"))
        # Zoom on the upper body (canopy root, shoulder, grip):
        # the person block occupies the centred lower ~90% of the
        # 760x900 canvas; the grip sits right-of-centre mid-height.
        crop = par33.crop((300, 60, 760, 560)).resize((460 * 2, 500 * 2),
                                                      Image.NEAREST)
        crop_r = rain.crop((300, 60, 760, 560)).resize((460 * 2, 500 * 2),
                                                       Image.NEAREST)
        side_by_side([crop_r, crop], os.path.join(OUT, f"crop_{label}.png"))
        print("compared", label)


if __name__ == "__main__":
    main()
