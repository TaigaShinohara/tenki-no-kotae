import numpy as np
from PIL import Image
import os
D = os.path.expanduser("~/workspace/tenki-no-kotae/tools/layers_keyed")
O = os.path.expanduser("~/workspace/tenki-no-kotae/tools/illust_fixes")
for name, frac in (("layer_head_cap", 0.80), ("layer_head_knitcap", 0.80)):
    im = Image.open(os.path.join(D, name+".png")).convert("RGBA")
    W,H = im.size
    bg = Image.new("RGBA", im.size, (24,40,80,255))
    bg.alpha_composite(im)
    crop = bg.crop((int(W*0.10), int(H*frac), int(W*0.90), H))
    crop = crop.resize((crop.size[0]*2, crop.size[1]*3), Image.NEAREST)
    crop.convert("RGB").save(os.path.join(O, f"zoom_{name}_bottom.png"))
    print(name, crop.size)
