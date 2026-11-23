#!/usr/bin/env python3
"""v0.1.33 audit: render EVERY parasol appearance combo with the
v0.1.32 mirror (== shipped Kotlin math), plus its rain-umbrella
counterpart with identical other slots, so the held item is the
only difference. Parasol-day slots follow the auto spec: UV>=8
gives CAP + sunglasses; neck/mittens/signs are the outfit's own."""
import os
import sys

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
import mirror_v0132 as M  # noqa: E402
from PIL import Image  # noqa: E402

OUT = os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit33")

# (label, base, top, extra slots) — every (base, top) the parasol
# can meet: auto spec ranks 0-6 + the test screen's raincoat outfit.
COMBOS = [
    ("rank0", "BODY_B", "TSHIRT", dict(neck="TOWEL", sweat=True)),
    ("rank1", "BODY_A", "SHIRT", dict()),
    ("rank2", "BODY_A", "CARDIGAN", dict()),
    ("rank3", "BODY_A", "LIGHT_OUTER", dict()),
    ("rank4", "BODY_A", "KNIT_COAT", dict()),
    ("rank5", "RANK5_PORTRAIT", None, dict(neck="MUFFLER", shiver=True)),
    ("rank6", "RANK6_PORTRAIT", None,
     dict(neck="MUFFLER", mittens=True, shiver=True)),
    ("raincoat", "RAINCOAT", None, dict()),
]


def build(base, top, held, extra):
    spec = {"base": base, "top": top, "held": held,
            "head": "CAP", "sunglasses": True, "mask": False,
            "neck": None, "mittens": False, "sweat": False,
            "shiver": False}
    spec.update(extra)
    return spec


def side_by_side(imgs, path):
    w = sum(i.size[0] for i in imgs)
    h = max(i.size[1] for i in imgs)
    out = Image.new("RGB", (w, h))
    x = 0
    for i in imgs:
        out.paste(i, (x, 0))
        x += i.size[0]
    out.save(path)


def main():
    for label, base, top, extra in COMBOS:
        rain = M.render(build(base, top, "OPEN", extra))
        par = M.render(build(base, top, "PARASOL", extra))
        rain.save(os.path.join(OUT, f"diag_{label}_rain.png"))
        par.save(os.path.join(OUT, f"diag_{label}_parasol32.png"))
        side_by_side([rain, par], os.path.join(OUT, f"diag_{label}_pair.png"))
        print("rendered", label)


if __name__ == "__main__":
    main()
