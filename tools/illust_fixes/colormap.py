import numpy as np
from PIL import Image
import os
D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
O = os.path.expanduser("~/workspace/tenki-no-kotae/tools/illust_fixes")

def classify(px):
    r,g,b = px[...,0], px[...,1], px[...,2]
    lum = (r+g+b)/3
    lab = np.full(r.shape, 5)
    lab[(lum<70)] = 4
    lab[(lum>=70)&(lum<150)&(b>r)] = 2
    lab[(r>182)&(g>195)&(b>205)] = 1
    lab[(r>=110)&(r<=182)&(g>=150)&(b>=200)&(lum>=140)] = 3
    return lab

COLORS = {0:(10,10,16),1:(192,234,252),2:(52,75,134),3:(200,60,60),4:(20,20,60),5:(255,180,0)}
for name in ("layer_head_cap","layer_head_knitcap"):
    im = Image.open(os.path.join(D,name+".png")).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    H,W,_ = a.shape
    lab = classify(a[...,:3])
    lab[a[...,3]<=128] = 0
    y0 = int(H*0.72)
    sub = lab[y0:]
    out = np.zeros((sub.shape[0], sub.shape[1], 3), np.uint8)
    for k,c in COLORS.items(): out[sub==k] = c
    img = Image.fromarray(out).resize((W*1, sub.shape[0]*3), Image.NEAREST)
    img.save(os.path.join(O, f"colormap_{name}.png"))
    print(name, "map rows y/H", y0/H, "-> 1.0 ; red=shirt/accent class3, pale=skin, blue=hair, dark=outline, orange=other")
