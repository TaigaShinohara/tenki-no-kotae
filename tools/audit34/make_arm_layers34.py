#!/usr/bin/env python3
"""v0.1.34 step 2 (final): write the three rebuilt OPEN arm layers
into drawable-nodpi and print the re-solved anchors at full
precision for PersonFigure.kt / mirror_v0134.py."""
import os
import sys

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools/audit34"))
import build_arm34  # noqa: E402

RES = build_arm34.RES

if __name__ == "__main__":
    for name in build_arm34.PLAN:
        img, anch = build_arm34.build_layer(name)
        img.save(os.path.join(RES, name + ".png"))
        print(f"ANCH {name}: gripX={anch['grip'][0]:.6f} "
              f"gripY={anch['grip'][1]} shaftX={anch['shaft']:.6f} "
              f"dwFrac={anch['dw']:.6f} dhFrac={anch['dh']} "
              f"px={img.size[0]}x{img.size[1]}")
        print(f"saved {name}.png")
