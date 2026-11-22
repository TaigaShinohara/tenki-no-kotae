#!/usr/bin/env python3
"""v0.1.32 audit renders (mirror API: render(spec_for(...))).

- rain / parasol composites for v0.1.31 vs v0.1.32
- boards: v31 | v32, and base | v31 | v32 for rank 0
- rain x parasol silhouette overlay (rank 0)
- numeric check on the v32 composites: canopy bbox / apex vs
  the face centre, measured against the base render
- rank 5 / 6 / raincoat variant composites (check only)
"""
import importlib.util
import os

import numpy as np
from PIL import Image, ImageDraw

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
OUT = os.path.join(BASE, "tools", "audit32")


def load_mirror(tag):
    spec = importlib.util.spec_from_file_location(
        f"mirror_{tag}", os.path.join(BASE, "tools", f"mirror_{tag}.py"))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


M31 = load_mirror("v0131")
M32 = load_mirror("v0132")

SPECS = {
    "rank0_open": (0, "OPEN"),
    "rank1_open": (1, "OPEN"),
    "rank2_open": (2, "OPEN"),
    "rank3_open": (3, "OPEN"),
    "rank4_open": (4, "OPEN"),
    "rank0_parasol": (0, "PARASOL"),
    "rank1_parasol": (1, "PARASOL"),
}
RANKS = {"rank0_open": 0, "rank1_open": 1, "rank2_open": 2,
         "rank3_open": 3, "rank4_open": 4, "rank0_parasol": 0,
         "rank1_parasol": 1}


def board(name, imgs, labels):
    w = sum(i.size[0] for i in imgs)
    h = max(i.size[1] for i in imgs) + 30
    b = Image.new("RGB", (w, h), (12, 18, 34))
    d = ImageDraw.Draw(b)
    x = 0
    for im, lb in zip(imgs, labels):
        b.paste(im, (x, 0))
        d.text((x + 12, h - 22), lb, fill=(230, 240, 250))
        x += im.size[0]
    b.save(os.path.join(OUT, name))
    print("saved", name)


def measure(comp, base):
    """Canopy geometry on the composite, from the diff vs base."""
    c = np.asarray(comp).astype(int)
    b = np.asarray(base).astype(int)
    diff = (np.abs(c - b).sum(axis=2) > 24)
    can = diff.copy()
    can[430:] = False  # canopy lives above the sleeve tops
    ys, xs = np.where(can)
    apex_x = int(xs[np.argmin(ys)]) if len(ys) else -1
    # face from the base: pale skin blob inside the figure
    lum = 0.299 * b[:, :, 0] + 0.587 * b[:, :, 1] + 0.114 * b[:, :, 2]
    skin = (lum > 175) & (b[:, :, 0] > 150)
    skin[500:] = False
    # keep the skin blob near the image centre (the face)
    sy, sx = np.where(skin)
    keep = (sx > 200) & (sx < 560) & (sy > 60) & (sy < 420)
    sx, sy = sx[keep], sy[keep]
    fcx = (sx.min() + sx.max()) / 2
    fw = sx.max() - sx.min()
    print(f"  canopy x {xs.min()}-{xs.max()} y {ys.min()}-{ys.max()} "
          f"apex_x {apex_x} | face cx {fcx:.0f} w {fw} | "
          f"margins L {xs.min()} R {760 - 1 - xs.max()} T {ys.min()} "
          f"| apex offset {apex_x - fcx:+.0f} (allow ±{0.35 * fw:.0f})")


def main():
    os.makedirs(OUT, exist_ok=True)
    renders = {}
    for tag, m in [("v31", M31), ("v32", M32)]:
        for key, (rank, held) in SPECS.items():
            im = m.render(m.spec_for(rank, held=held))
            im.save(os.path.join(OUT, f"{key}_{tag}.png"))
            renders[(key, tag)] = im
            print("saved", f"{key}_{tag}.png")
    for key in SPECS:
        board(f"cmp_{key}.png",
              [renders[(key, "v31")], renders[(key, "v32")]],
              ["v0.1.31", "v0.1.32"])
    for key in ["rank0_open", "rank0_parasol"]:
        rank = RANKS[key]
        base = M32.render(M32.spec_for(rank))
        board(f"cmp3_{key}.png",
              [base, renders[(key, "v31")], renders[(key, "v32")]],
              ["base only", "v0.1.31", "v0.1.32"])
    print("== v0.1.32 numeric check (frame 760x900) ==")
    for key, (rank, held) in SPECS.items():
        print(key)
        measure(renders[(key, "v32")], M32.render(M32.spec_for(rank)))
    # silhouette overlay rain vs parasol (rank 0, v32): the arm
    # layers' alpha must be identical, so the composites may only
    # differ in colour, never in the silhouette vs the base.
    b0 = np.asarray(M32.render(M32.spec_for(0))).astype(int)
    sil = {}
    for key in ["rank0_open", "rank0_parasol"]:
        c = np.asarray(renders[(key, "v32")]).astype(int)
        sil[key] = np.abs(c - b0).sum(axis=2) > 24
    ra, pa = sil["rank0_open"], sil["rank0_parasol"]
    ov = np.zeros((*ra.shape, 3), dtype=np.uint8)
    ov[:] = (240, 244, 248)
    ov[ra & pa] = (40, 40, 60)
    ov[ra & ~pa] = (230, 60, 60)
    ov[~ra & pa] = (60, 90, 230)
    Image.fromarray(ov).save(os.path.join(OUT, "silhouette_overlay_v32.png"))
    print("silhouette diff: rain-only", int((ra & ~pa).sum()),
          "parasol-only", int((~ra & pa).sum()))
    # ---- variant portraits (check only, via the mirror) ----
    for key, spec in [("rank5_open", M32.spec_for(5, held="OPEN")),
                      ("rank6_open", M32.spec_for(6, held="OPEN")),
                      ("raincoat_open", M32.spec_for(base="RAINCOAT",
                                                      held="OPEN"))]:
        im = M32.render(spec)
        im.save(os.path.join(OUT, f"{key}_v32.png"))
        print("saved", f"{key}_v32.png")


if __name__ == "__main__":
    main()
