#!/usr/bin/env python3
"""Fix 1 (audit M1) v3: erase the shirt-collar/shoulder fragment at
the bottom of layer_head_cap / layer_head_knitcap.

Per column, a cut is derived from two detectors:
  A) trusted fabric = shirt-family colors (fabric #A0CAED / accent
     #75A1C9) in rows y>=0.85H forming horizontal runs >=12px
     (diagonal shoulder slopes are caught by B);
  B) the bottom-touching opaque run, when it is not skin-dominated
     (the neck tab is) and not the hair mass: its top is the collar /
     shoulder outline arc -- erase from there.
Hair protection: when the column's top run is a separate run above a
transparent gap (hair mass), the cut never rises above that run's
bottom. The neck-skin tab (skin-dominated bottom run, no fabric) is
kept whole. Canvas size / face position unchanged; originals kept.
"""
import os
import numpy as np
from PIL import Image

D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
MARGIN_A = 12
ZONE = 0.85
MINRUN = 12


def shirt_candidates(rgb, alpha):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lum = (r + g + b) / 3.0
    return (alpha > 128) & (r >= 100) & (r <= 186) & (g >= 150) & (g <= 235) \
        & (b >= 195) & ((b - r) >= 45) & (lum >= 140) & (lum <= 220)


def hair_mask(rgb, alpha):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    lum = (r + g + b) / 3.0
    return (alpha > 128) & (lum >= 70) & (lum < 150) & (b > r)


def skin_mask(rgb, alpha):
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return (alpha > 200) & (r > 175) & (g > 195) & (b > 205) & \
        ((np.maximum(np.maximum(r, g), b) - np.minimum(np.minimum(r, g), b)) < 60)


def wide_runs(mask_row, minrun=MINRUN):
    out = np.zeros_like(mask_row)
    x, W = 0, len(mask_row)
    while x < W:
        if mask_row[x]:
            x0 = x
            while x < W and mask_row[x]:
                x += 1
            if x - x0 >= minrun:
                out[x0:x] = True
        else:
            x += 1
    return out


def runs_of(col_opaque, gap=2):
    """list of (top, bottom) opaque runs, bridging <=gap transparent px"""
    runs = []
    y, H = 0, len(col_opaque)
    while y < H:
        if col_opaque[y]:
            top = y
            last = y
            y += 1
            while y < H:
                if col_opaque[y]:
                    last = y
                    y += 1
                elif y - last <= gap:
                    y += 1
                else:
                    break
            runs.append((top, last))
        else:
            y += 1
    return runs


def fix(name):
    src = Image.open(os.path.join(D, name + ".png")).convert("RGBA")
    a = np.asarray(src).copy()
    rgb = a[..., :3].astype(np.float32)
    alpha = a[..., 3].astype(np.float32)
    H, W = alpha.shape
    cand = shirt_candidates(rgb, alpha)
    fabric = np.zeros_like(cand)
    for y in range(int(H * ZONE), H):
        fabric[y] = wide_runs(cand[y])
    skin = skin_mask(rgb, alpha)
    hair = hair_mask(rgb, alpha)
    opaque = alpha > 24
    cut = np.full(W, -1, dtype=np.int32)
    for x in range(W):
        runs = runs_of(opaque[:, x])
        if not runs:
            continue
        top_run, bot_run = runs[0], runs[-1]
        c = -1
        ys = np.nonzero(fabric[:, x])[0]
        if len(ys):
            c = ys.min() - MARGIN_A
        # bottom run: fragment only when it is a SEPARATE run (the
        # face/tab and hair masses are single-run columns) and it is
        # neither skin-dominated (neck tab) nor hair-dominated (puff)
        seg = slice(bot_run[0], bot_run[1] + 1)
        skin_frac = skin[seg, x].mean() if bot_run[1] > bot_run[0] else 0.0
        hair_frac = hair[seg, x].mean() if bot_run[1] > bot_run[0] else 0.0
        # ... and only when the run lives in the bottom zone and
        # touches the bottom edge (a detached speck near the top --
        # e.g. the cap's top button -- must not turn the whole head
        # into a "bottom run")
        if len(runs) > 1 and skin_frac < 0.30 and hair_frac < 0.40 \
                and bot_run[0] >= int(H * 0.82) and bot_run[1] >= H - 3:
            c = bot_run[0] - 2 if c < 0 else min(c, bot_run[0] - 2)
        if c >= 0 and len(runs) > 1:
            # hair mass = top run separated by a gap: stay below it
            c = max(c, top_run[1] + 1)
        cut[x] = c
    erased = 0
    for x in range(W):
        if cut[x] >= 0:
            y0 = int(min(max(cut[x], 0), H))
            erased += int((alpha[y0:, x] > 24).sum())
            a[y0:, x, 3] = 0
    # verification numbers
    left = shirt_candidates(a[..., :3].astype(np.float32),
                            a[..., 3].astype(np.float32))
    fab_left = sum(int(wide_runs(left[y]).sum())
                   for y in range(int(H * ZONE), H))
    used = cut[cut >= 0]
    print(f"{name}: cut columns={len(used)}/{W}, cut y/H="
          f"[{used.min() / H:.3f},{used.max() / H:.3f}], erased px={erased}, "
          f"fabric left={fab_left}")
    out = Image.fromarray(a, "RGBA")
    dst = os.path.join(D, name + "_fixed.png")
    out.save(dst, optimize=True)
    print("  ->", dst, out.size, os.path.getsize(dst), "bytes")


if __name__ == "__main__":
    fix("layer_head_cap")
    fix("layer_head_knitcap")
