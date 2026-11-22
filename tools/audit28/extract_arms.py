#!/usr/bin/env python3
"""v0.1.28 arm extraction, step 1: key the two full-body edit
generations and map their non-ink regions (ink = barrier), so the
arm-layer parts (canopy / sleeve / forearm / fist / fan) can be
identified by region."""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.expanduser("~/workspace/tenki-no-kotae/tools"))
from key_assets import key_image  # noqa: E402

OUT = os.path.dirname(os.path.abspath(__file__))
GENS = {
    "parasol": "/home/hatch/workspace/imagine_media/media-generation-tenki-v28-parasol-side-0-aedd76e2-f0b5-41cd-bb5a-297e6646cf1f.webp",
    "uchiwa": "/home/hatch/workspace/imagine_media/media-generation-tenki-v28-uchiwa-side-0-00cfa713-afb5-433c-8f8b-ceb40b01da21.webp",
}
INK = np.array([21, 30, 101], float)

for name, path in GENS.items():
    rgba, bbox, key = key_image(path)
    rgba.save(os.path.join(OUT, f"gen_{name}_keyed.png"))
    a = np.asarray(rgba).astype(float)
    ih, iw = a.shape[:2]
    alpha = a[..., 3] > 128
    dink = np.sqrt(((a[..., :3] - INK) ** 2).sum(-1))
    ink = (dink < 85) & alpha
    fill = alpha & ~ink
    lab, n = ndimage.label(fill)
    print(f"== {name} ({iw}x{ih}) regions: {n}")
    regions = []
    for i in range(1, n + 1):
        ys, xs = np.where(lab == i)
        if len(xs) < 200:
            continue
        mc = a[lab == i][:, :3].mean(0)
        regions.append((len(xs), i, xs.min(), ys.min(), xs.max(),
                        ys.max(), xs.mean(), ys.mean(), mc))
    regions.sort(reverse=True)
    cmap = np.zeros((ih, iw, 3), np.uint8)
    palette = np.random.RandomState(7).randint(60, 255, (n + 1, 3))
    for (_, i, *_rest) in regions:
        cmap[lab == i] = palette[i]
    cmap[ink] = (10, 10, 40)
    Image.fromarray(cmap).save(os.path.join(OUT, f"gen_{name}_regions.png"))
    for (sz, i, x0, y0, x1, y1, cx, cy, mc) in regions:
        print(f"  R{i:<3d} n={sz:<8d} x {x0/iw:.3f}..{x1/iw:.3f} "
              f"y {y0/ih:.3f}..{y1/ih:.3f} c=({cx/iw:.3f},{cy/ih:.3f}) "
              f"rgb=({mc[0]:.0f},{mc[1]:.0f},{mc[2]:.0f})")
    print()
