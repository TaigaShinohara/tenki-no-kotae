#!/usr/bin/env python3
"""v0.1.30 fan placement, second pass. The first pass's torso
proxy (the top-alpha segment containing the centre) spans the
whole shirt sleeve-to-sleeve, which no placement can clear. The
torso boundary is now measured from the BODY: per row, the right
edge of the body alpha after removing the right arm's skin
cluster (the fan may cross the arm it is held by, never the
trunk). The towel and the head/hair zone stay forbidden."""
import sys
from collections import deque
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


def arm_cluster_bitmap(base):
    """Skin cluster of the right arm in the body bitmap (bitmap
    fractions == person fractions), seeded nearest the rig hand."""
    body = M.img(M.BODY_ART[base])
    b = np.asarray(body).astype(int)
    Hb, Wb = b.shape[:2]
    # pale-blue skin family (the hand of BODY_B sits a tone
    # deeper than the face: ~(165,205,235) vs (192,234,252))
    skin = ((b[:, :, 2] >= 200) & (b[:, :, 0] >= 130) &
            (b[:, :, 1] >= 175) & (b[:, :, 2] > b[:, :, 0] + 25) &
            (b[:, :, 3] > 0))
    rig = M.RIGS[base]
    ax, ay = int(rig[5] * Wb), int(rig[6] * Hb)
    cand = np.argwhere(skin[max(0, ay - 40):ay + 40,
                            max(0, ax - 40):ax + 40])
    cand = cand[np.argsort(((cand[:, 1] - 40) ** 2 +
                            (cand[:, 0] - 40) ** 2))]
    sy, sx = cand[0] + [max(0, ay - 40), max(0, ax - 40)]
    seen = np.zeros_like(skin)
    dq = deque([(sy, sx)])
    seen[sy, sx] = True
    while dq:
        y, x = dq.popleft()
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            ny, nx = y + dy, x + dx
            if 0 <= ny < Hb and 0 <= nx < Wb and skin[ny, nx] \
                    and not seen[ny, nx]:
                seen[ny, nx] = True
                dq.append((ny, nx))
    ys, xs = np.nonzero(seen)
    print(base, "arm cluster frac bbox:", xs.min() / Wb, ys.min() / Hb,
          xs.max() / Wb, ys.max() / Hb, "n=", len(xs))
    return seen


def dilate(m, r=2):
    out = m.copy()
    for _ in range(r):
        n = out.copy()
        n[1:, :] |= out[:-1, :]; n[:-1, :] |= out[1:, :]
        n[:, 1:] |= out[:, :-1]; n[:, :-1] |= out[:, 1:]
        out = n
    return out


def forbidden(spec):
    bl, pt, bw, ph = person_frame(spec)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    M.draw(canvas, dict(spec, held="NONE"), W, H)
    comp = np.asarray(canvas)[:, :, 3] > 0
    body_c = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    M.draw(body_c, dict(spec, held="NONE", top=None), W, H)
    body_a = np.asarray(body_c)[:, :, 3] > 0
    # arm cluster in canvas space (bitmap fractions == person
    # fractions), dilated — the arm zone the fan may cross
    cl = arm_cluster_bitmap(spec["base"])
    cl_img = Image.fromarray((cl * 255).astype(np.uint8)).resize(
        (max(1, round(bw)), max(1, round(ph))))
    arm_zone = np.zeros((H, W), dtype=bool)
    arm_zone[int(pt):int(pt) + cl_img.size[1],
             int(bl):int(bl) + cl_img.size[0]] = np.asarray(cl_img) > 0
    arm_zone = dilate(arm_zone, 6)
    trunk = body_a & ~arm_zone
    forb = np.zeros((H, W), dtype=bool)
    for y in range(H):
        xs = np.nonzero(trunk[y])[0]
        if len(xs):
            forb[y, :xs.max() + 1] = comp[y, :xs.max() + 1]
    forb |= comp & (np.arange(H)[:, None] < pt + 0.40 * ph)
    if spec.get("neck") == "TOWEL":
        tw = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        M.draw(tw, dict(spec, held="NONE", neck="TOWEL"), W, H)
        forb |= (np.asarray(tw)[:, :, 3] > 0) & ~comp
    u0, v0, u1, v1 = PATCH[spec["base"]]
    forb[int(pt + v0 * ph):int(pt + v1 * ph),
         int(bl + u0 * bw):int(bl + u1 * bw)] = False
    return forb, (bl, pt, bw, ph)


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

results = []
for fh in (0.19, 0.20, 0.21, 0.22, 0.23, 0.24, 0.25, 0.26):
    for rot in (0, -8, -16, -24, -32, -40, -48):
        for dx in (0.0, 0.01, 0.02, 0.03, 0.04, 0.05):
            worst = 0
            for name, spec, forb, forb_d, frame in CASES:
                rig = M.RIGS[spec["base"]]
                fx0, fx1 = FIST_X[spec["base"]]
                if not (fx0 - 0.005 <= rig[5] + dx <= fx1 + 0.005):
                    worst = 10 ** 9
                    break
                pa = prop_alpha(spec, frame, fh, rot, dx)
                worst = max(worst, int((pa & forb_d).sum()))
            cost = abs(rot) + 500 * dx + 600 * abs(fh - 0.21)
            results.append((worst, cost, fh, rot, dx))
results.sort()
for r in results[:6]:
    print("overlap/cost/fh/rot/dx:", r)
best = [r for r in results if r[0] == 0]
if best:
    best.sort(key=lambda r: r[1])
    _, _, fh, rot, dx = best[0]
    print("CHOSEN fh/rot/dx:", fh, rot, dx)
    for name, spec, forb, forb_d, frame in CASES:
        pa = prop_alpha(spec, frame, fh, rot, dx)
        ys, xs = np.nonzero(pa)
        bl, pt, bw, ph = frame
        print(name, "raw overlap:", int((pa & forb).sum()),
              "prop bbox frac x:", (xs.min() - bl) / bw, (xs.max() - bl) / bw,
              "y:", (ys.min() - pt) / ph, (ys.max() - pt) / ph)
