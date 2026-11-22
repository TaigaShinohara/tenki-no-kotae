#!/usr/bin/env python3
"""Before / after previews for the v0.1.20 test-screen fix.

The two cases are the user's own screenshots (2026-11-22), resolved
twice: once with the v0.1.19 testSpec logic (a manual outfit kept
the auto spec's outfit-linked elements) and once with the v0.1.20
logic (the elements are re-derived from the picked outfit via
PersonFigure.outfitLinked, and the composer suppresses them on the
raincoat base outright).

The day behind both screenshots is a cold one (auto rank 5-6:
muffler + shiver in the auto spec, no held item).

  Case A — outfit pinned to rank 0, neck on 自動:
    before: body_b + tshirt + MUFFLER (leaked) + sweat
    after:  body_b + tshirt + sweat + fan (the fan follows the
            outfit on 自動), no muffler
  Case B — outfit pinned to the raincoat, everything else 自動:
    before: raincoat + MUFFLER (leaked) + shiver (leaked)
    after:  raincoat alone
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_parts import compose, OUT  # noqa: E402


def main():
    jobs = {
        "r3_rank0_manual_before": compose(
            "body_b", "top_tshirt", neck="MUFFLER", sweat=True),
        "r3_rank0_manual_after": compose(
            "body_b", "top_tshirt", sweat=True, held="UCHIWA"),
        "r3_raincoat_manual_before": compose(
            "outfit_raincoat", neck="MUFFLER", shiver=True),
        "r3_raincoat_manual_after": compose("outfit_raincoat"),
    }
    for name, img in jobs.items():
        p = os.path.join(OUT, name + ".png")
        img.save(p)
        print(p)


if __name__ == "__main__":
    main()
