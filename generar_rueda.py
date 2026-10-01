import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = "ruleta-pack/assets/ruleta/textures/font"
os.makedirs(OUT, exist_ok=True)
S = 4                 # supersampling
W = 256 * S
C = W / 2
R = 100 * S           # radio de la rueda
COLS = [  # (hex, nivel) en orden horario desde arriba
    ("#E5322D", 4), ("#F28A1E", 3), ("#F7D21C", 2), ("#3CC23C", 1),
    ("#19C6D9", 1), ("#2F5BE0", 2), ("#8E3FD6", 3), ("#E84FB0", 4),
]

def hx(h, f=1.0, a=255):
    r, g, b = int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16)
    if f >= 1:
        r, g, b = [int(v + (255 - v) * (f - 1)) for v in (r, g, b)]
    else:
        r, g, b = [int(v * f) for v in (r, g, b)]
    return (min(r, 255), min(g, 255), min(b, 255), a)

def star(d, cx, cy, r, fill):
    pts = []
    for j in range(10):
        ang = -math.pi / 2 + j * math.pi / 5
        rr = r if j % 2 == 0 else r * 0.45
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    d.polygon(pts, fill=fill)

def frame(rot_deg, resaltar=None):
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # sombra
    sh = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([C - R - 10*S, C - R - 4*S, C + R + 10*S, C + R + 16*S], fill=(0, 0, 0, 140))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(6 * S)))
    d = ImageDraw.Draw(img)
    # aro exterior
    d.ellipse([C - R - 9*S, C - R - 9*S, C + R + 9*S, C + R + 9*S], fill=(40, 26, 10, 255))
    d.ellipse([C - R - 6*S, C - R - 6*S, C + R + 6*S, C + R + 6*S], fill=(214, 168, 60, 255))
    d.ellipse([C - R - 2*S, C - R - 2*S, C + R + 2*S, C + R + 2*S], fill=(60, 40, 14, 255))
    box = [C - R, C - R, C + R, C + R]
    for i, (col, nivel) in enumerate(COLS):
        centro = -90 + i * 45 + rot_deg
        apagado = resaltar is not None and i != resaltar
        f_out = 0.45 if apagado else 1.0
        f_in = 0.6 if apagado else 1.35
        if resaltar == i:
            f_out, f_in = 1.15, 1.6
        d.pieslice(box, centro - 22.5, centro + 22.5, fill=hx(col, f_out))
        # brillo interior (degradado simple en 3 capas)
        for k, (rf, ff) in enumerate([(0.78, (f_out + f_in) / 2), (0.55, f_in)]):
            rr = R * rf
            d.pieslice([C - rr, C - rr, C + rr, C + rr], centro - 22.5, centro + 22.5, fill=hx(col, ff))
        # estrellas = dificultad
        for n in range(nivel):
            dist = R * (0.84 - n * 0.15)
            a = math.radians(centro)
            star(d, C + dist * math.cos(a), C + dist * math.sin(a), 7 * S,
                 (255, 255, 255, 120 if apagado else 235))
    # separadores
    for i in range(8):
        a = math.radians(-90 + i * 45 + 22.5 + rot_deg)
        d.line([C, C, C + R * math.cos(a), C + R * math.sin(a)], fill=(30, 20, 8, 255), width=3 * S)
    if resaltar is not None:
        centro = -90 + resaltar * 45 + rot_deg
        d.arc(box, centro - 22.5, centro + 22.5, fill=(255, 255, 255, 255), width=5 * S)
    # remaches dorados en el aro
    for i in range(16):
        a = math.radians(i * 22.5 + rot_deg)
        x, y = C + (R + 4*S) * math.cos(a), C + (R + 4*S) * math.sin(a)
        d.ellipse([x - 2.5*S, y - 2.5*S, x + 2.5*S, y + 2.5*S], fill=(255, 236, 150, 255))
    # centro
    hr = 17 * S
    d.ellipse([C - hr - 3*S, C - hr - 3*S, C + hr + 3*S, C + hr + 3*S], fill=(60, 40, 14, 255))
    d.ellipse([C - hr, C - hr, C + hr, C + hr], fill=(222, 170, 48, 255))
    d.ellipse([C - hr*0.7, C - hr*0.75, C + hr*0.5, C + hr*0.45], fill=(250, 214, 110, 255))
    d.ellipse([C - hr*0.45, C - hr*0.55, C - hr*0.05, C - hr*0.15], fill=(255, 248, 210, 255))
    # puntero (arriba, apuntando hacia abajo)
    py = C - R - 2*S
    pw = 15 * S
    top = 6 * S
    d.polygon([(C - pw - 3*S, top - 2*S), (C + pw + 3*S, top - 2*S), (C, py + 20*S)], fill=(40, 26, 10, 255))
    d.polygon([(C - pw, top), (C + pw, top), (C, py + 14*S)], fill=(214, 40, 40, 255))
    d.polygon([(C - pw*0.55, top + 3*S), (C - 2*S, top + 3*S), (C - 1*S, py + 2*S)], fill=(255, 120, 110, 255))
    d.ellipse([C - 5*S, top + 5*S, C + 5*S, top + 15*S], fill=(255, 225, 120, 255))
    return img.resize((256, 256), Image.LANCZOS)

def escalado(img, f):
    n = max(2, round(256 * f))
    peq = img.resize((n, n), Image.LANCZOS)
    lienzo = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    off = (256 - n) // 2
    if off >= 0:
        lienzo.alpha_composite(peq, (off, off))
    else:
        lienzo.alpha_composite(peq.crop((-off, -off, -off + 256, -off + 256)))
    return lienzo

# Giro: 16 fotogramas (U+E000..E00F)
for k in range(16):
    frame(k * 22.5).save(f"{OUT}/rueda_{k:02d}.png")
# Color ganador resaltado (U+E010..E017)
for s in range(8):
    k = 2 * ((8 - s) % 8)
    frame(k * 22.5, resaltar=s).save(f"{OUT}/rueda_sel_{s}.png")

# Entrada: crece girando (U+E020..E024), termina en el fotograma 0
ENTRADA = [0.15, 0.4, 0.65, 0.88, 1.04]
for j, f in enumerate(ENTRADA):
    rot = -(len(ENTRADA) - j) * 22.5
    escalado(frame(rot), f).save(f"{OUT}/rueda_in_{j}.png")

# Salida: el color ganador se encoge girando (U+E030 + s*8 + j)
SALIDA = [1.04, 0.85, 0.6, 0.35, 0.12]
for s in range(8):
    k = 2 * ((8 - s) % 8)
    for j, f in enumerate(SALIDA):
        escalado(frame(k * 22.5 + j * 18, resaltar=s), f).save(f"{OUT}/rueda_out_{s}_{j}.png")

# Fuente: assets/minecraft/font/default.json
import json
prov = []
def g(file, code):
    prov.append({"type": "bitmap", "file": f"ruleta:font/{file}", "height": 32, "ascent": 31, "chars": [chr(code)]})
for k in range(16): g(f"rueda_{k:02d}.png", 0xE000 + k)
for s in range(8): g(f"rueda_sel_{s}.png", 0xE010 + s)
for j in range(len(ENTRADA)): g(f"rueda_in_{j}.png", 0xE020 + j)
for s in range(8):
    for j in range(len(SALIDA)): g(f"rueda_out_{s}_{j}.png", 0xE030 + s * 8 + j)
fuente = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(OUT))), "minecraft", "font")
os.makedirs(fuente, exist_ok=True)
json.dump({"providers": prov}, open(os.path.join(fuente, "default.json"), "w"), indent=2)

# hoja de vista previa: entrada + salida
prev = Image.new("RGBA", (256 * 5, 256 * 2), (40, 44, 52, 255))
for j in range(5):
    prev.alpha_composite(Image.open(f"{OUT}/rueda_in_{j}.png"), (256 * j, 0))
    prev.alpha_composite(Image.open(f"{OUT}/rueda_out_2_{j}.png"), (256 * j, 256))
prev.save("preview_animacion.png")
print("ok")
