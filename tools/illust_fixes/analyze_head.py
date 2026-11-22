import numpy as np
from PIL import Image
import os

D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")

def classify(px):
    """px: (...,3) float. returns labels: 0 transp handled outside,
    1 skin(pale #C0EAFC), 2 hair/hat dark blue, 3 shirt light blue,
    4 outline very dark, 5 other"""
    r,g,b = px[...,0], px[...,1], px[...,2]
    lum = (r+g+b)/3
    mx = np.maximum(np.maximum(r,g),b); mn = np.minimum(np.minimum(r,g),b)
    lab = np.full(r.shape, 5)
    lab[(lum<70)] = 4                      # outline navy ~ (21,30,101) lum~50
    lab[(lum>=70)&(lum<150)&(b>r)] = 2      # hair #344B86 lum~88
    lab[(r>175)&(g>195)&(b>205)] = 1       # skin
    lab[(r>=120)&(r<=185)&(g>=170)&(g<=225)&(b>=215)&(lum>=150)] = 3  # shirt #A0CAED lum~190 but r<skin
    # skin vs shirt: skin r~192, shirt r~160-168 -> split by r
    lab[(lab==3)&(r>182)] = 1
    lab[(lab==1)&(r<=182)&(g>180)] = 3
    return lab

for name in ("layer_head_cap", "layer_head_knitcap"):
    im = Image.open(os.path.join(D, name+".png")).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    H,W,_ = a.shape
    alpha = a[...,3]
    rgb = a[...,:3]
    lab = classify(rgb)
    print(f"== {name} {W}x{H}")
    print(" y/H   opaqueW  skinW  hairW  shirtW  outlW  otherW   opaque x-range")
    for yi in range(int(H*0.60), H, 8):
        op = alpha[yi] > 128
        if op.sum()==0:
            print(f" {yi/H:.3f}  empty"); continue
        xs = np.nonzero(op)[0]
        row = lab[yi][op]
        print(f" {yi/H:.3f}  {op.mean():.3f}   "
              f"{(row==1).mean():.2f}  {(row==2).mean():.2f}   {(row==3).mean():.2f}   "
              f"{(row==4).mean():.2f}   {(row==5).mean():.2f}   [{xs.min()/W:.3f},{xs.max()/W:.3f}]")
    # bottom row detail
    print(" last rows:")
    for yi in range(H-6, H):
        op = alpha[yi] > 128
        xs = np.nonzero(op)[0]
        print(f"  y={yi} opaque={op.sum()} xrange=[{xs.min() if len(xs) else '-'},{xs.max() if len(xs) else '-'}]")
