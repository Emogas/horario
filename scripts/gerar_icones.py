import os, sys
from PIL import Image, ImageDraw

res = sys.argv[1]
NAVY, AMBER, PAPER = (20, 33, 61, 255), (224, 149, 42, 255), (237, 241, 245, 255)

def relogio(size, fundo, escala=1.0):
    img = Image.new("RGBA", (size, size), fundo)
    d = ImageDraw.Draw(img)
    c = size / 2
    r = size * 0.34 * escala
    d.ellipse([c - r, c - r, c + r, c + r], fill=PAPER)
    w = max(2, int(size * 0.045 * escala))
    d.line([c, c, c, c - r * 0.62], fill=NAVY, width=w)
    d.line([c, c, c + r * 0.45, c + r * 0.2], fill=AMBER, width=w)
    p = w
    d.ellipse([c - p, c - p, c + p, c + p], fill=NAVY)
    return img

legado = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
frente = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

for dens, s in legado.items():
    pasta = os.path.join(res, "mipmap-" + dens)
    os.makedirs(pasta, exist_ok=True)
    base = relogio(s, NAVY)
    base.save(os.path.join(pasta, "ic_launcher.png"))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, s - 1, s - 1], fill=255)
    redondo = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    redondo.paste(base, (0, 0), mask)
    redondo.save(os.path.join(pasta, "ic_launcher_round.png"))

for dens, s in frente.items():
    pasta = os.path.join(res, "mipmap-" + dens)
    os.makedirs(pasta, exist_ok=True)
    relogio(s, (0, 0, 0, 0), 0.8).save(os.path.join(pasta, "ic_launcher_foreground.png"))

cor = os.path.join(res, "values", "ic_launcher_background.xml")
os.makedirs(os.path.dirname(cor), exist_ok=True)
with open(cor, "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            '    <color name="ic_launcher_background">#14213D</color>\n</resources>\n')
