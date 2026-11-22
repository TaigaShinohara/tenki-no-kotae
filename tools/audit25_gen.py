#!/usr/bin/env python3
"""v0.1.25 audit renders: before = mirror_v0124 (standalone hat
props, v0.1.24 topCal), after = mirror_v0125 (head layers, widened
open-front outers). Full figures + enlarged crops land in
tools/audit25/{before,after}/; metrics.txt records the head-layer
geometry (composer space) and the layered-top fit numbers.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0124 as OLD  # noqa: E402
import mirror_v0125 as NEW  # noqa: E402
import audit23_gen as A23  # noqa: E402
from PIL import Image  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit25")
W, H = 1140, 1350


def crop(img, M, spec, fy0, fy1, path, zoom=3):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    if spec["base"] in ("RANK5_PORTRAIT", "RANK6_PORTRAIT"):
        aspect = 405 / 1024 if spec["base"] == "RANK5_PORTRAIT" else 448 / 1024
        bw = ph * aspect
    else:
        box = M.BOX[M.BODY_ART[spec["base"]]]
        scale = ph / ((box[3] - box[1]) * M.CANVAS_H)
        bw = (box[2] - box[0]) * M.CANVAS_W * scale
    bl = (W - bw) / 2
    c = img.crop((round(bl), round(pt + fy0 * ph),
                  round(bl + bw), round(pt + fy1 * ph)))
    c = c.resize((c.size[0] * zoom, c.size[1] * zoom), Image.LANCZOS)
    c.save(path)


def run(M, tag, spec, name, crops):
    d = os.path.join(OUT, tag)
    os.makedirs(d, exist_ok=True)
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    img.save(os.path.join(d, f"{name}_full.png"))
    for fy0, fy1, suffix in crops:
        crop(img, M, spec, fy0, fy1, os.path.join(d, f"{name}_{suffix}.png"))


HEAD_CROP = [(0.0, 0.48, "head")]
UPPER_CROP = [(0.08, 0.66, "upper")]

CASES = [
    # head layers on every base
    ("cap_bodyA", dict(rank=3, head="CAP"), HEAD_CROP),
    ("knit_bodyA", dict(rank=3, head="KNITCAP"), HEAD_CROP),
    ("cap_bodyB", dict(rank=0, head="CAP"), HEAD_CROP),
    ("knit_bodyB", dict(rank=0, head="KNITCAP"), HEAD_CROP),
    ("cap_bodyC", dict(base="BODY_C", top="DOWN", head="CAP"), HEAD_CROP),
    ("knit_bodyC", dict(base="BODY_C", top="DOWN", head="KNITCAP"), HEAD_CROP),
    ("cap_rank5", dict(rank=5, head="CAP"), HEAD_CROP),
    ("knit_rank5", dict(rank=5, head="KNITCAP"), HEAD_CROP),
    ("cap_rank6", dict(rank=6, head="CAP"), HEAD_CROP),
    ("knit_rank6", dict(rank=6, head="KNITCAP"), HEAD_CROP),
    ("cap_rain", dict(rank=3, base="RAINCOAT", top=None, head="CAP"), HEAD_CROP),
    ("knit_rain", dict(rank=3, base="RAINCOAT", top=None, head="KNITCAP"), HEAD_CROP),
    # layered tops (inner shirt baked into the outer assets)
    ("rank2_cardigan", dict(rank=2), UPPER_CROP),
    ("rank3_lightouter", dict(rank=3), UPPER_CROP),
    ("rank4_knitcoat", dict(rank=4), UPPER_CROP),
]


def spec_of(M, kw):
    kw = dict(kw)
    rank = kw.pop("rank", None)
    base = kw.pop("base", None)
    top = kw.pop("top", "AUTO")
    return M.spec_for(rank=rank, base=base, top=top, **kw)


def seed_old_props():
    """The v0.1.24 mirror draws the standalone hat props, which
    v0.1.25 deleted from drawable-nodpi; pull them from the git
    history into the old mirror's image cache (files stay deleted
    in the tree)."""
    import io
    import subprocess
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    for name in ("prop_cap", "prop_knitcap"):
        data = subprocess.run(
            ["git", "show",
             f"HEAD:app/src/main/res/drawable-nodpi/{name}.png"],
            cwd=root, capture_output=True, check=True).stdout
        OLD.IMG[name] = Image.open(io.BytesIO(data)).convert("RGBA")


def main():
    seed_old_props()
    for M, tag in ((OLD, "before"), (NEW, "after")):
        for name, kw, crops in CASES:
            run(M, tag, spec_of(M, kw), name, crops)
    # ---- metrics ------------------------------------------------
    lines = []
    lines.append("== head layers (composer space, mirror_v0125) ==")
    for base in ("BODY_A", "BODY_B", "BODY_C", "RANK5_PORTRAIT",
                 "RANK6_PORTRAIT", "RAINCOAT"):
        rig = NEW.RIGS[base]
        bw = NEW.body_aspect(base) * 1000.0
        for head in ("CAP", "KNITCAP"):
            l, t, w, h = NEW.head_layer_rect(head, base, 0.0, 0.0, bw, 1000.0)
            face = NEW.HEAD_FACE[head]
            fcx = l + (face[0] + face[2]) / 2 * w
            lines.append(
                f"{base:15s} {head:8s} poke={max(0.0, -t / 1000):.3f} "
                f"bottom={(t + h) / 1000:.3f} (faceBottom {rig[2]:.3f}) "
                f"faceCx err={fcx / 1000 - rig[0] * NEW.body_aspect(base):+.4f} "
                f"padTop={NEW.pad_top_ratio({'base': base, 'head': head}):.2f}")
    lines.append("")
    lines.append("== layered tops (audit23 path, BODY_A) ==")
    for top, label in (("top_cardigan", "cardigan"),
                       ("top_lightouter", "light outer"),
                       ("top_knitcoat", "knit coat")):
        import mirror_v0124 as M124
        A23.M.TOPCAL[top] = M124.TOPCAL[top]
        b = A23.hand_survival(top, "body_a")
        import mirror_v0125 as M125
        # mirror_v0125 shares TOPCAL values with the Kotlin v0.1.25
        new_cal = {"top_cardigan": (0.55, 0.45, 0.0, -0.003),
                   "top_lightouter": (0.63, 0.625, 0.0, 0.0),
                   "top_knitcoat": (0.67, 0.60, 0.0, 0.011)}[top]
        A23.M.TOPCAL[top] = new_cal
        a = A23.hand_survival(top, "body_a")
        lines.append(
            f"{label:11s} hand survival {b['L']:.1f}/{b['R']:.1f} -> "
            f"{a['L']:.1f}/{a['R']:.1f}")
    txt = "\n".join(lines) + "\n"
    with open(os.path.join(OUT, "metrics.txt"), "w") as f:
        f.write(txt)
    print(txt)
    print("audit25 renders done")


if __name__ == "__main__":
    main()
