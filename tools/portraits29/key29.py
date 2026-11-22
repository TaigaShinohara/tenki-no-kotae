#!/usr/bin/env python3
"""Key a generated portrait (magenta bg) the key_assets.py way:
distance-ramp alpha + despill, trim to alpha bbox, cap edge 1024."""
import sys
import numpy as np
from PIL import Image

T0, T1 = 45.0, 130.0

def key_image(path):
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(np.float32)
    corners = np.concatenate([
        a[:24, :24].reshape(-1, 3), a[:24, -24:].reshape(-1, 3),
        a[-24:, :24].reshape(-1, 3), a[-24:, -24:].reshape(-1, 3)])
    key = np.median(corners, axis=0)
    dist = np.sqrt(((a - key) ** 2).sum(axis=2))
    alpha = np.clip((dist - T0) / (T1 - T0), 0.0, 1.0)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    excess = np.clip(np.minimum(r, b) - g - 15.0, 0.0, None)
    a[..., 0] = r - excess
    a[..., 2] = b - excess
    rgba = np.dstack([a, alpha * 255.0]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA")
    bbox = out.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
    out = out.crop(bbox)
    w, h = out.size
    if max(w, h) > 1024:
        s = 1024 / max(w, h)
        out = out.resize((round(w * s), round(h * s)), Image.LANCZOS)
    return out

if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    out = key_image(src)
    out.save(dst)
    print(dst, out.size)
