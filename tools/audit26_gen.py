#!/usr/bin/env python3
"""v0.1.26 audit renders: before = mirror_v0125 (grip-point held
drawing), after = mirror_v0126 (sleeve-group arm layers + umbrella
variant portraits). Full figures + enlarged crops land in
tools/audit26/{before,after}/; metrics.txt records the arm /
variant geometry (composer space) and the pad table.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mirror_v0125 as OLD  # noqa: E402
import mirror_v0126 as NEW  # noqa: E402
from PIL import Image  # noqa: E402

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "audit26")
W, H = 1140, 1350


def body_rect(M, spec):
    ph = H / (1.0 + M.pad_top_ratio(spec))
    pt = H - ph
    bw = ph * M.body_aspect(spec["base"])
    bl = (W - bw) / 2
    return bl, pt, bw, ph


def crop(img, M, spec, fx0, fy0, fx1, fy1, path, zoom=3):
    bl, pt, bw, ph = body_rect(M, spec)
    c = img.crop((round(bl + fx0 * bw), round(pt + fy0 * ph),
                  round(bl + fx1 * bw), round(pt + fy1 * ph)))
    c = c.resize((c.size[0] * zoom, c.size[1] * zoom), Image.LANCZOS)
    c.save(path)


def run(M, tag, spec, name, crops):
    d = os.path.join(OUT, tag)
    os.makedirs(d, exist_ok=True)
    img = M.draw(M.sky(W, H), spec, W, H).convert("RGB")
    img.save(os.path.join(d, f"{name}_full.png"))
    for fx0, fy0, fx1, fy1, suffix in crops:
        crop(img, M, spec, fx0, fy0, fx1, fy1,
             os.path.join(d, f"{name}_{suffix}.png"))


GRIP = [(0.50, 0.42, 1.02, 0.92, "grip")]
ROOT = [(0.42, 0.0, 1.02, 0.48, "root")]
FACE = [(0.10, -0.06, 0.90, 0.46, "face")]
FULL = [(0.30, 0.30, 1.02, 0.80, "mid")]

CASES = [
    # layered arm layers (auto shapes)
    ("rank0_open", dict(rank=0, held="OPEN", sweat=True), GRIP + ROOT),
    ("rank0_folding", dict(rank=0, held="FOLDED", sweat=True), GRIP),
    ("rank0_parasol", dict(rank=0, held="PARASOL", head="CAP", sweat=True), GRIP + ROOT),
    ("rank0_uchiwa", dict(rank=0, held="UCHIWA", sweat=True), GRIP),
    ("rank1_open", dict(rank=1, held="OPEN"), GRIP + ROOT),
    ("rank1_folding", dict(rank=1, held="FOLDED"), GRIP),
    ("rank2_folding", dict(rank=2, held="FOLDED"), GRIP),
    ("rank3_folding", dict(rank=3, held="FOLDED"), GRIP),
    ("rank4_folding", dict(rank=4, held="FOLDED"), GRIP),
    # layered open arms (test-screen combos; auto makes raincoats)
    ("rank2_open_manual", dict(rank=2, held="OPEN"), GRIP + ROOT),
    ("rank3_open_manual", dict(rank=3, held="OPEN"), GRIP + ROOT),
    ("rank4_open_manual", dict(rank=4, held="OPEN"), GRIP + ROOT),
    # umbrella variants (portraits / raincoat)
    ("rank5_open", dict(rank=5, held="OPEN"), FACE + GRIP),
    ("rank5_folding", dict(rank=5, held="FOLDED"), GRIP),
    ("rank5_open_mask", dict(rank=5, held="OPEN", mask=True), FACE),
    ("rank6_open", dict(rank=6, held="OPEN"), FACE + GRIP),
    ("rank6_open_knit", dict(rank=6, held="OPEN", head="KNITCAP"), FACE),
    ("rank6_folding", dict(rank=6, held="FOLDED"), GRIP),
    ("rain_open", dict(base="RAINCOAT", top=None, held="OPEN"), FACE + GRIP),
    ("rain_folding", dict(base="RAINCOAT", top=None, held="FOLDED"), GRIP),
    ("rain_open_cap", dict(base="RAINCOAT", top=None, held="OPEN", head="CAP"), FACE),
    # fallbacks (no arm picture / no variant: v0.1.22 drawing)
    ("rank2_parasol_fb", dict(rank=2, held="PARASOL"), FULL),
    ("rank5_parasol_fb", dict(rank=5, held="PARASOL", head="CAP"), FULL),
    ("rank3_uchiwa_fb", dict(rank=3, held="UCHIWA"), FULL),
]


def spec_of(M, kw):
    kw = dict(kw)
    rank = kw.pop("rank", None)
    base = kw.pop("base", None)
    top = kw.pop("top", "AUTO")
    return M.spec_for(rank=rank, base=base, top=top, **kw)


def main():
    for M, tag in ((OLD, "before"), (NEW, "after")):
        for name, kw, crops in CASES:
            run(M, tag, spec_of(M, kw), name, crops)
    # ---- metrics ------------------------------------------------
    lines = []
    lines.append("== arm layers (composer space, mirror_v0126) ==")
    for name, a in NEW.ARM.items():
        lines.append(
            f"{name:32s} grip=({a['grip'][0]:.3f},{a['grip'][1]:.3f}) "
            f"shaft={a['shaft']} dw={a['dw_frac']} dh={a['dh_frac']} "
            f"rot={a['rot']}")
    for spec_kw, label in (
            (dict(rank=0, held="OPEN"), "rank0 open (BODY_B)"),
            (dict(rank=1, held="OPEN"), "rank1 open (BODY_A)"),
            (dict(rank=2, held="OPEN"), "rank2 open manual (cardigan)"),
            (dict(rank=4, held="OPEN"), "rank4 open manual (knit coat)"),
            (dict(rank=0, held="PARASOL"), "rank0 parasol")):
        s = spec_of(NEW, spec_kw)
        lines.append(
            f"{label:32s} poke={NEW.arm_poke(s):.4f} "
            f"padTop={NEW.pad_top_ratio(s):.2f}")
    lines.append("")
    lines.append("== umbrella variants ==")
    for (base, held), (name, inner) in sorted(NEW.VARIANT.items()):
        s = {"base": base, "top": None, "held": held}
        lines.append(
            f"{name:26s} inner=({inner[0]:.4f},{inner[1]:.4f},"
            f"{inner[2]:.4f},{inner[3]:.4f}) "
            f"poke={NEW.variant_poke(s):.4f} "
            f"padTop={NEW.pad_top_ratio(s):.2f}")
    lines.append("")
    lines.append("== pad table (audit cases, after) ==")
    for name, kw, _ in CASES:
        s = spec_of(NEW, kw)
        lines.append(f"{name:22s} padTop={NEW.pad_top_ratio(s):.2f}")
    txt = "\n".join(lines) + "\n"
    with open(os.path.join(OUT, "metrics.txt"), "w") as f:
        f.write(txt)
    print(txt)


if __name__ == "__main__":
    main()
