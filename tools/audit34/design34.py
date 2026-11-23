#!/usr/bin/env python3
"""v0.1.34: the ONE parametric umbrella design every rebuilt
picture is drawn from (the review's conclusion: the v0.1.32 canopy
— a deep cone with 16 straight ribs converging on the apex — reads
as a pointed hat / kasa, above all in the white parasol).

The new design, per the settled spec:
- a WIDE, SHALLOW dome seen from the front: half-width hw against
  a depth of only ~0.40 x the full width;
- the rim is a gentle arc dipping at the front centre (sag), with
  one soft scallop per panel;
- 8 panels; the ribs are gentle curves that hug the dome's own
  arc and start spread over a small cap ellipse around the apex
  instead of converging on a single point;
- a small ferrule knob at the apex, nothing tall;
- the shaft is one straight line from the apex to the fist.

Geometry: apex A=(ax, ay); the dome shears by `tilt` (the feet
heights differ by tilt*hw a side — the arm layers use the shaft's
own slope, the portraits about half of theirs). rim_y and the top
arc share draw_canopy32's dome maths so old and new stay
comparable; what changed is the proportions and the rib work.
"""
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

SS = 3  # supersample
PANELS = 8


def top_arc_y(ax, ay, hw, depth, tilt, t):
    return ay + (depth + tilt * t * hw) * (1 - math.sqrt(max(0.0, 1 - t * t)))


def rim_y(ax, ay, hw, depth, tilt, sag, x):
    t = (x - ax) / hw
    return ay + depth + tilt * (x - ax) + sag * (1 - t * t)


def draw_umbrella(size, apex, hw, depth, sag, tilt, shaft_to,
                  shaft_col, cloth_a, cloth_b, ink,
                  shaft_w=16, outline_w=6, rib_w=5,
                  knob=(13, 11), texture=0.0, highlights=None,
                  seed=34):
    """Draws shaft + canopy onto a transparent image of `size`
    (w, h) and returns (image, art_mask) at full resolution.
    `highlights`: list of (cx, cy, rx, ry, angle_deg, alpha, col)
    soft streaks clipped to the canopy (the raincoat's gloss)."""
    W, H = size
    ax, ay = apex
    art = Image.new("RGBA", (W * SS, H * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(art)

    def S(p):
        return (p[0] * SS, p[1] * SS)

    # ---- shaft (the canopy covers its top) ----
    d.line([S(apex), S(shaft_to)], fill=shaft_col + (255,),
           width=int(shaft_w * SS))

    # ---- dome silhouette ----
    n = 160
    top = []
    for i in range(n + 1):
        t = -1.0 + 2.0 * i / n
        top.append((ax + t * hw, top_arc_y(ax, ay, hw, depth, tilt, t)))
    feet = []
    for i in range(PANELS + 1):
        x = ax - hw + i * (2 * hw / PANELS)
        feet.append((x, rim_y(ax, ay, hw, depth, tilt, sag, x)))

    def scallop_pts(i, dip):
        x0, x1 = feet[i][0], feet[i + 1][0]
        mid = ((x0 + x1) / 2, rim_y(ax, ay, hw, depth, tilt, sag,
                                   (x0 + x1) / 2) + dip)
        pts = []
        for k in range(13):
            t = k / 12
            x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * mid[0] + t * t * x1
            y = (1 - t) ** 2 * feet[i][1] + 2 * (1 - t) * t * mid[1] \
                + t * t * feet[i + 1][1]
            pts.append((x, y))
        return pts

    dip = depth * 0.055
    silhouette = list(top)
    for i in range(PANELS - 1, -1, -1):
        silhouette += scallop_pts(i, dip)[::-1]
    d.polygon([S(p) for p in silhouette], fill=cloth_a + (255,))

    # ---- alternate-panel shading, clipped to the silhouette ----
    shade = Image.new("RGBA", art.size, (0, 0, 0, 0))
    ds = ImageDraw.Draw(shade)
    for i in range(PANELS):
        if i % 2 == 0:
            continue
        x0, x1 = feet[i][0], feet[i + 1][0]
        mid = ((x0 + x1) / 2, rim_y(ax, ay, hw, depth, tilt, sag,
                                    (x0 + x1) / 2))
        ds.polygon([S(p) for p in [(ax, ay), feet[i], mid, feet[i + 1]]],
                   fill=cloth_b + (255,))
    mask_big = Image.new("L", art.size, 0)
    dm = ImageDraw.Draw(mask_big)
    dm.polygon([S(p) for p in silhouette], fill=255)
    shade_np = np.asarray(shade).copy()
    shade_np[:, :, 3] = np.minimum(np.asarray(shade)[:, :, 3],
                                   np.asarray(mask_big))
    art.alpha_composite(Image.fromarray(shade_np, "RGBA"), (0, 0))
    d = ImageDraw.Draw(art)

    # ---- highlights (raincoat gloss), clipped to the silhouette --
    if highlights:
        hl = Image.new("RGBA", art.size, (0, 0, 0, 0))
        dh_ = ImageDraw.Draw(hl)
        for (cx, cy, rx, ry, ang, alpha_v, col) in highlights:
            tmp = Image.new("RGBA", (int(rx * 2 * SS) + 4,
                                     int(ry * 2 * SS) + 4), (0, 0, 0, 0))
            dt = ImageDraw.Draw(tmp)
            dt.ellipse([2, 2, tmp.size[0] - 3, tmp.size[1] - 3],
                       fill=col + (alpha_v,))
            tmp = tmp.rotate(ang, resample=Image.BICUBIC, expand=True)
            hl.alpha_composite(tmp, (int(cx * SS - tmp.size[0] / 2),
                                     int(cy * SS - tmp.size[1] / 2)))
        hl_np = np.asarray(hl).copy()
        hl_np[:, :, 3] = np.minimum(np.asarray(hl)[:, :, 3],
                                    np.asarray(mask_big))
        art.alpha_composite(Image.fromarray(hl_np, "RGBA"), (0, 0))
        d = ImageDraw.Draw(art)

    # ---- ribs: gentle curves off a small cap ellipse ----
    cap_rx, cap_ry = hw * 0.055, depth * 0.10
    for i in range(1, PANELS):
        f = feet[i]
        t = (f[0] - ax) / hw
        # start: a point just off the apex toward the foot,
        # spread over the cap ellipse instead of one point
        sx = ax + math.sin(t * math.pi / 2) * cap_rx
        sy = ay + (1 - math.cos(t * math.pi / 2)) * cap_ry * 0.4 \
            + cap_ry * 0.30
        # control: the dome's own arc point at 0.55 of the way,
        # pushed a breath outward so the rib bows with the dome
        tc = t * 0.55
        cxp = ax + tc * hw
        cyp = top_arc_y(ax, ay, hw, depth, tilt, tc)
        cxp += (cxp - ax) * 0.05
        cyp += (cyp - ay) * 0.05
        pts = []
        for k in range(25):
            u = k / 24
            x = (1 - u) ** 2 * sx + 2 * (1 - u) * u * cxp + u * u * f[0]
            y = (1 - u) ** 2 * sy + 2 * (1 - u) * u * cyp + u * u * f[1]
            pts.append((x, y))
        d.line([S(p) for p in pts], fill=ink + (255,),
               width=int(rib_w * SS))

    # ---- outlines: top arc + scalloped rim ----
    d.line([S(p) for p in top], fill=ink + (255,),
           width=int(outline_w * SS), joint="curve")
    for i in range(PANELS):
        d.line([S(p) for p in scallop_pts(i, dip)], fill=ink + (255,),
               width=int(outline_w * SS))

    # ---- ferrule knob ----
    kw, kh = knob
    d.ellipse([S((ax - kw / 2, ay - kh * 0.72)),
               S((ax + kw / 2, ay + kh * 0.28))], fill=ink + (255,))

    art = art.resize((W, H), Image.LANCZOS)
    art_np = np.asarray(art)
    art_mask = art_np[:, :, 3] > 0

    # ---- painterly texture (portraits): soft mottling + grain,
    # clipped to the art, so drawn cloth sits in a painted picture
    if texture > 0:
        rng = np.random.default_rng(seed)
        field = rng.normal(0, 1, (H // 24 + 2, W // 24 + 2)).astype(np.float32)
        field_img = Image.fromarray(
            ((field - field.min()) / (field.ptp() + 1e-9) * 255).astype(np.uint8))
        field_img = field_img.resize((W, H), Image.BICUBIC) \
            .filter(ImageFilter.GaussianBlur(18))
        f = (np.asarray(field_img).astype(float) / 255.0 - 0.5) * 2
        grain = rng.normal(0, 1, (H, W))
        mod = 1.0 + (f * 0.055 + grain * 0.016) * texture
        out = art_np.astype(float)
        for c in range(3):
            out[:, :, c] = np.where(art_mask, out[:, :, c] * mod,
                                    out[:, :, c])
        art = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")
    return art, art_mask
