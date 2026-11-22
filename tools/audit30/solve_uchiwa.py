#!/usr/bin/env python3
"""v0.1.30: solve the fan's placement numerically. The fan prop is
grip-anchored on the right hand (grip (0.502, 0.90) of the asset,
as since v0.1.22); find (height, PIL rotation about the grip,
outward shift dx) so the prop's alpha never touches the forbidden
mask — the top garment's torso segment, the head/hair zone, and
the neck towel — on either summer base, with or without the
towel. The grip point itself must stay over the fist (the hand
patch covers the join)."""
import sys
import numpy as np
from PIL import Image

sys.path.insert(0, "tools")
import mirror_v0129 as M

W, H = 760, 900
GRIP = (0.502, 0.90)
PATCH = {"BODY_B": (0.685, 0.575, 0.800, 0.670),
         "BODY_A": (0.685, 0.575, 0.800, 0.680)}
FIST_X = {"BODY_B": (0.705, 0.792), "BODY_A": (0.700, 0.790)}


def person_frame(spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    bw = ph * M.body_aspect(spec["base"])
    bl = (W - bw) / 2
    return bl, pt, bw, ph


def top_alpha(spec):
    """The top garment's alpha alone, in canvas space."""
    bl, pt, bw, ph = person_frame(spec)
    name = M.TOP_ART[spec["top"]]
    box = M.BOX[name]
    b_box = M.BOX[M.BODY_ART[spec["base"]]]
    scale = ph / ((b_box[3] - b_box[1]) * M.CANVAS_H)
    cw, ch = M.CANVAS_W * scale, M.CANVAS_H * scale
    ox, oy = bl - b_box[0] * cw, pt - b_box[1] * ch
    x0, y0 = ox + box[0] * cw, oy + box[1] * ch
    x1, y1 = ox + box[2] * cw, oy + box[3] * ch
    cal = M.TOPCAL[name]
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    w, h = (x1 - x0) * cal[0], (y1 - y0) * cal[1]
    x0, y0 = cx - w / 2 + cal[2] * bw, cy - h / 2 + cal[3] * ph
    out = Image.new("L", (W, H), 0)
    im = M.img(name).split()[3].resize((max(1, round(w)), max(1, round(h))))
    out.paste(im, (round(x0), round(y0)))
    return np.asarray(out) > 0


def torso_segment(top_a, bl, bw):
    """Per row: the top-alpha segment containing the body's
    centre x — the torso panel (sleeves form their own segment
    where a gap exists)."""
    out = np.zeros_like(top_a)
    cx = int(bl + 0.5 * bw)
    for y in range(top_a.shape[0]):
        row = top_a[y]
        if not row[cx]:
            continue
        x0 = cx
        while x0 > 0 and row[x0 - 1]:
            x0 -= 1
        x1 = cx
        while x1 < row.shape[0] - 1 and row[x1 + 1]:
            x1 += 1
        out[y, x0:x1 + 1] = True
    return out


def forbidden(spec):
    bl, pt, bw, ph = person_frame(spec)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    M.draw(canvas, dict(spec, held="NONE"), W, H)
    comp = np.asarray(canvas)[:, :, 3] > 0
    forb = torso_segment(top_alpha(spec), bl, bw)
    head_zone = comp & (np.arange(H)[:, None] < pt + 0.40 * ph)
    forb |= head_zone
    if spec.get("neck") == "TOWEL":
        tw = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        M.draw(tw, dict(spec, held="NONE", neck="TOWEL"), W, H)
        towel = (np.asarray(tw)[:, :, 3] > 0) & ~comp
        forb |= towel
    # the hand patch rect is exempt (the handle dives behind the
    # fist there by design)
    u0, v0, u1, v1 = PATCH[spec["base"]]
    forb[int(pt + v0 * ph):int(pt + v1 * ph),
         int(bl + u0 * bw):int(bl + u1 * bw)] = False
    return forb, (bl, pt, bw, ph)


def dilate(m, r=2):
    out = m.copy()
    for _ in range(r):
        n = out.copy()
        n[1:, :] |= out[:-1, :]; n[:-1, :] |= out[1:, :]
        n[:, 1:] |= out[:, :-1]; n[:, :-1] |= out[:, 1:]
        out = n
    return out


def prop_alpha(spec, frame, fh, rot, dx):
    bl, pt, bw, ph = frame
    rig = M.RIGS[spec["base"]]
    hand_x = bl + rig[5] * bw + dx * bw
    hand_y = pt + rig[6] * ph
    im = M.img("prop_uchiwa")
    fhp = fh * ph
    fwp = fhp * im.size[0] / im.size[1]
    scaled = im.resize((max(1, round(fwp)), max(1, round(fhp))),
                       Image.LANCZOS)
    big = Image.new("RGBA", (scaled.size[0] * 3, scaled.size[1] * 3),
                    (0, 0, 0, 0))
    big.alpha_composite(scaled, (scaled.size[0], scaled.size[1]))
    gx = scaled.size[0] + GRIP[0] * scaled.size[0]
    gy = scaled.size[1] + GRIP[1] * scaled.size[1]
    if rot:
        big = big.rotate(rot, resample=Image.BICUBIC, center=(gx, gy))
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    canvas.alpha_composite(big, (round(hand_x - gx), round(hand_y - gy)))
    return np.asarray(canvas)[:, :, 3] > 0


CASES = []
for rank, neck in ((0, None), (0, "TOWEL"), (1, None), (1, "TOWEL")):
    spec = M.spec_for(rank, neck=neck)
    forb, frame = forbidden(spec)
    CASES.append((f"rank{rank}/towel={neck == 'TOWEL'}", spec, forb,
                  dilate(forb, 2), frame))

best = None
for fh in (0.21, 0.22, 0.23, 0.24, 0.25, 0.26):
    for rot in (0, -8, -16, -24, -32, -40):
        for dx in (0.0, 0.01, 0.02, 0.03, 0.04):
            ok = True
            for name, spec, forb, forb_d, frame in CASES:
                rig = M.RIGS[spec["base"]]
                gx = rig[5] + dx
                fx0, fx1 = FIST_X[spec["base"]]
                if not (fx0 - 0.005 <= gx <= fx1 + 0.005):
                    ok = False
                    break
                pa = prop_alpha(spec, frame, fh, rot, dx)
                if (pa & forb_d).any():
                    ok = False
                    break
            if not ok:
                continue
            cost = abs(rot) + 500 * dx + 600 * abs(fh - 0.21)
            if best is None or cost < best[0]:
                best = (cost, fh, rot, dx)
print("best:", best)
if best:
    _, fh, rot, dx = best
    for name, spec, forb, forb_d, frame in CASES:
        pa = prop_alpha(spec, frame, fh, rot, dx)
        ys, xs = np.nonzero(pa)
        # disc centre: asset (0.4995, 0.397) through the transform —
        # report the prop bbox and the raw-forbidden overlap
        print(name, "raw overlap:", int((pa & forb).sum()),
              "prop bbox x:", xs.min(), xs.max(), "y:", ys.min(), ys.max())
