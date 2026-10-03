"""
Genera las imágenes de la rueda (paquete de texturas) y la fuente que las usa.
Uso: python generar_rueda.py   (desde la carpeta del proyecto; requiere Pillow y numpy)
"""
import json, math, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PACK = "ruleta-pack"
OUT = f"{PACK}/assets/ruleta/textures/font"
os.makedirs(OUT, exist_ok=True)

S = 4                     # supersampling
W = 256 * S
C = W / 2
R = 94 * S                # radio del disco de colores
RIM_IN, RIM_OUT = R, 113 * S
FRAMES = 48               # fotogramas de giro (7.5° cada uno)
PASO = 360 / FRAMES

# (color, nivel de dificultad) en orden horario desde arriba
SECTORES = [
    ((235, 45, 45), 0), ((245, 135, 25), 3), ((250, 210, 30), 2), ((55, 190, 60), 1),
    ((25, 195, 215), 1), ((45, 90, 225), 2), ((145, 60, 215), 3), ((235, 75, 175), 0),
]
# nivel 0 = EVENTO (se dibuja una calavera en vez de estrellas)

yy, xx = np.mgrid[0:W, 0:W]
dx, dy = xx - C + 0.5, yy - C + 0.5
RAD = np.hypot(dx, dy)
ANG = (np.degrees(np.arctan2(dx, -dy)) + 360) % 360      # 0° arriba, sentido horario


def disco(resaltar=None, lleno=None):
    """Disco de colores sin rotar (sector 0 centrado arriba). lleno=s pinta todo del color del sector s."""
    idx = (((ANG + 22.5) % 360) // 45).astype(int)
    base = np.array([c for c, _ in SECTORES], dtype=float)[idx]
    if lleno is not None:
        base[:] = np.array(SECTORES[lleno][0], dtype=float)
    dentro = ((ANG + 22.5) % 45) / 45.0                        # 0..1 dentro del sector
    lateral = 1 - np.abs(dentro - 0.5) * 2                     # 1 en el centro del sector
    r = np.clip(RAD / R, 0, 1)
    luz = 0.78 + 0.42 * (1 - r) ** 1.3 + 0.08 * lateral        # más claro al centro
    col = base * luz[..., None]
    col = col + (255 - col) * np.clip((0.35 - r) / 0.35, 0, 1)[..., None] * 0.35
    borde = np.clip((r - 0.86) / 0.14, 0, 1)                   # oscurece el borde exterior
    col = col * (1 - 0.35 * borde[..., None])
    if resaltar is not None:
        sel = idx == resaltar
        col[~sel] *= 0.38
        col[sel] = col[sel] + (255 - col[sel]) * 0.22
    alpha = np.clip((R - RAD) / 1.5 + 0.5, 0, 1) * 255
    img = Image.fromarray(np.dstack([np.clip(col, 0, 255), alpha]).astype(np.uint8), "RGBA")
    d = ImageDraw.Draw(img)

    def polar(a, rr):
        a = math.radians(a - 90)
        return C + rr * math.cos(a), C + rr * math.sin(a)

    # separadores blancos
    for i in range(8):
        a = i * 45 + 22.5
        d.line([polar(a, 18 * S), polar(a, R)], fill=(255, 255, 255, 230), width=2 * S)
    # estrellas de dificultad y destellos
    for i, (_, nivel) in enumerate(SECTORES):
        a0 = i * 45
        apagado = resaltar is not None and resaltar != i
        if lleno is not None:
            nivel = -1                      # rueda llena: sin íconos, solo destellos
        if nivel == 0:
            x, y = polar(a0, R * 0.62)
            calavera(d, x, y, 15 * S, a0, apagado)
        for n in range(nivel):
            x, y = polar(a0, R * (0.78 - n * 0.15))
            estrella(d, x, y, 7.5 * S, (255, 255, 255, 110 if apagado else 245), a0)
        for (da, rf, tam) in [(-12, 0.55, 3.2), (13, 0.40, 2.4), (-6, 0.30, 1.8), (16, 0.70, 2.0)]:
            x, y = polar(a0 + da, R * rf)
            destello(d, x, y, tam * S, (255, 255, 255, 60 if apagado else 200))
    # clavos dorados en cada separación
    for i in range(8):
        x, y = polar(i * 45 + 22.5, R - 6 * S)
        d.ellipse([x - 3.6 * S, y - 3.6 * S, x + 3.6 * S, y + 3.6 * S], fill=(90, 60, 15, 255))
        d.ellipse([x - 2.8 * S, y - 2.8 * S, x + 2.8 * S, y + 2.8 * S], fill=(240, 200, 90, 255))
        d.ellipse([x - 1.6 * S, y - 2.2 * S, x + 0.4 * S, y - 0.6 * S], fill=(255, 250, 220, 255))
    if resaltar is not None:
        a0 = resaltar * 45
        box = [C - R + 2 * S, C - R + 2 * S, C + R - 2 * S, C + R - 2 * S]
        d.arc(box, a0 - 90 - 22.5, a0 - 90 + 22.5, fill=(255, 255, 255, 255), width=5 * S)
    return img


def estrella(d, cx, cy, r, fill, rot=0):
    pts = []
    for j in range(10):
        ang = math.radians(rot - 90) + j * math.pi / 5
        rr = r if j % 2 == 0 else r * 0.45
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    d.polygon(pts, fill=fill)


def calavera(d, cx, cy, r, ang, apagado):
    """Calavera blanca orientada hacia afuera de la rueda (icono de EVENTO)."""
    t = math.radians(ang)
    def p(lx, ly):
        return (cx + lx * math.cos(t) - ly * math.sin(t), cy + lx * math.sin(t) + ly * math.cos(t))
    def circulo(ox, oy, rr, n=28):
        return [p(ox + rr * math.cos(2 * math.pi * k / n), oy + rr * math.sin(2 * math.pi * k / n)) for k in range(n)]
    blanco = (255, 255, 255, 120 if apagado else 250)
    oscuro = (40, 10, 10, 160 if apagado else 255)
    d.polygon(circulo(0, -0.2 * r, r), fill=blanco)
    d.polygon([p(-0.55 * r, 0.4 * r), p(0.55 * r, 0.4 * r), p(0.55 * r, 1.0 * r), p(-0.55 * r, 1.0 * r)], fill=blanco)
    d.polygon(circulo(-0.4 * r, -0.1 * r, 0.27 * r, 16), fill=oscuro)
    d.polygon(circulo(0.4 * r, -0.1 * r, 0.27 * r, 16), fill=oscuro)
    d.polygon([p(0, 0.2 * r), p(-0.13 * r, 0.45 * r), p(0.13 * r, 0.45 * r)], fill=oscuro)
    for k in (-0.27, 0.0, 0.27):
        d.line([p(k * r, 0.62 * r), p(k * r, 1.0 * r)], fill=oscuro, width=max(1, int(0.09 * r)))


def destello(d, cx, cy, r, fill):
    d.polygon([(cx, cy - r * 2), (cx + r * 0.35, cy - r * 0.35), (cx + r * 2, cy), (cx + r * 0.35, cy + r * 0.35),
               (cx, cy + r * 2), (cx - r * 0.35, cy + r * 0.35), (cx - r * 2, cy), (cx - r * 0.35, cy - r * 0.35)],
              fill=fill)


def aro(fase):
    """Aro exterior metálico con bombillos (fase 0/1 alterna cuáles están encendidos)."""
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # sombra
    sh = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse([C - RIM_OUT, C - RIM_OUT + 6 * S, C + RIM_OUT, C + RIM_OUT + 10 * S], fill=(0, 0, 0, 150))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(5 * S)))
    # anillo con degradado metálico (vertical)
    t = np.clip((yy - (C - RIM_OUT)) / (2 * RIM_OUT), 0, 1)
    oro_claro, oro_osc = np.array([255, 222, 120]), np.array([150, 95, 25])
    col = oro_claro * (1 - t[..., None]) + oro_osc * t[..., None]
    anillo = (RAD <= RIM_OUT) & (RAD >= RIM_IN - 2 * S)
    a = np.where(anillo, np.clip((RIM_OUT - RAD) / 1.5 + 0.5, 0, 1) * 255, 0)
    img.alpha_composite(Image.fromarray(np.dstack([col, a]).astype(np.uint8), "RGBA"))
    d = ImageDraw.Draw(img)
    d.ellipse([C - RIM_OUT, C - RIM_OUT, C + RIM_OUT, C + RIM_OUT], outline=(70, 40, 10, 255), width=2 * S)
    canal = (RIM_IN + RIM_OUT) / 2
    d.ellipse([C - canal - 7 * S, C - canal - 7 * S, C + canal + 7 * S, C + canal + 7 * S], outline=(110, 65, 15, 255), width=12 * S)
    d.ellipse([C - RIM_IN - 1 * S, C - RIM_IN - 1 * S, C + RIM_IN + 1 * S, C + RIM_IN + 1 * S], outline=(60, 35, 8, 255), width=3 * S)
    # bombillos
    n = 24
    glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i in range(n):
        ang = math.radians(i * 360 / n - 90 + 7.5)
        x, y = C + canal * math.cos(ang), C + canal * math.sin(ang)
        on = (i + fase) % 2 == 0
        if on:
            gd.ellipse([x - 8 * S, y - 8 * S, x + 8 * S, y + 8 * S], fill=(255, 240, 160, 150))
            d.ellipse([x - 4.2 * S, y - 4.2 * S, x + 4.2 * S, y + 4.2 * S], fill=(255, 250, 215, 255))
            d.ellipse([x - 2.2 * S, y - 2.6 * S, x + 0.6 * S, y - 0.2 * S], fill=(255, 255, 255, 255))
        else:
            d.ellipse([x - 4 * S, y - 4 * S, x + 4 * S, y + 4 * S], fill=(185, 120, 45, 255))
            d.ellipse([x - 2 * S, y - 2.4 * S, x + 0.4 * S, y - 0.4 * S], fill=(225, 175, 95, 255))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(3 * S)))
    return img


def centro_y_brillo():
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    # brillo de vidrio sobre el disco (fijo)
    g = np.clip(1 - np.hypot((xx - (C - R * 0.35)) / (R * 0.95), (yy - (C - R * 0.55)) / (R * 0.6)), 0, 1) ** 1.6
    a = np.where(RAD < R - 2 * S, g * 75, 0)
    img.alpha_composite(Image.fromarray(np.dstack([np.full((W, W, 3), 255), a]).astype(np.uint8), "RGBA"))
    d = ImageDraw.Draw(img)
    hr = 18 * S
    d.ellipse([C - hr - 4 * S, C - hr - 4 * S, C + hr + 4 * S, C + hr + 4 * S], fill=(70, 40, 10, 255))
    t = np.clip((yy - (C - hr)) / (2 * hr), 0, 1)
    col = np.array([255, 230, 140]) * (1 - t[..., None]) + np.array([175, 110, 25]) * t[..., None]
    a = np.clip((hr - RAD) / 1.5 + 0.5, 0, 1) * 255
    img.alpha_composite(Image.fromarray(np.dstack([col, a]).astype(np.uint8), "RGBA"))
    d.ellipse([C - hr * 0.62, C - hr * 0.62, C + hr * 0.62, C + hr * 0.62], fill=(200, 140, 40, 255))
    estrella(d, C, C, hr * 0.5, (255, 245, 200, 255))
    d.ellipse([C - hr * 0.75, C - hr * 0.85, C - hr * 0.15, C - hr * 0.35], fill=(255, 255, 240, 170))
    return img


def puntero(desvio_grados):
    """Lengüeta roja arriba; se dobla cuando un clavo la empuja."""
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    px, py = C, 10 * S                 # pivote
    largo = 40 * S
    a = math.radians(desvio_grados)

    def rot(lx, ly):
        return px + lx * math.cos(a) + ly * math.sin(a), py - lx * math.sin(a) + ly * math.cos(a)

    contorno = [rot(-15 * S, 2 * S), rot(15 * S, 2 * S), rot(0, largo + 3 * S)]
    d.polygon(contorno, fill=(60, 15, 15, 255))
    cuerpo = [rot(-12 * S, 4 * S), rot(12 * S, 4 * S), rot(0, largo - 1 * S)]
    d.polygon(cuerpo, fill=(220, 35, 40, 255))
    luz = [rot(-9 * S, 6 * S), rot(-2 * S, 6 * S), rot(-1 * S, largo - 10 * S)]
    d.polygon(luz, fill=(255, 120, 115, 255))
    d.ellipse([px - 8 * S, py - 4 * S, px + 8 * S, py + 12 * S], fill=(70, 40, 10, 255))
    d.ellipse([px - 6 * S, py - 2 * S, px + 6 * S, py + 10 * S], fill=(245, 205, 95, 255))
    d.ellipse([px - 3.5 * S, py, px - 0.5 * S, py + 3 * S], fill=(255, 250, 225, 255))
    return img


def desvio(rot):
    """Cuánto se dobla la lengüeta según dónde está el clavo más cercano."""
    r = (rot + 22.5) % 45          # 0 = clavo justo bajo la lengüeta; sube a 45 mientras se acerca el siguiente
    if r > 33:
        return 24 * (r - 33) / 12    # el clavo que llega desde la izquierda la empuja
    return 0.0


AROS = [aro(0), aro(1)]
CENTRO = centro_y_brillo()
DISCO = disco()
DISCOS_SEL = [disco(s) for s in range(8)]


def componer(disco_img, rot, fase=0, desv=None):
    img = AROS[fase].copy()
    img.alpha_composite(disco_img.rotate(-rot, resample=Image.BICUBIC, center=(C, C)))
    img.alpha_composite(CENTRO)
    img.alpha_composite(puntero(desvio(rot) if desv is None else desv))
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


def fotograma_final(s):
    return (FRAMES - s * (FRAMES // 8)) % FRAMES


for f in os.listdir(OUT):
    if f.startswith("rueda"):
        os.remove(os.path.join(OUT, f))

prov = []
def glifo(nombre, img, code):
    img.save(f"{OUT}/{nombre}.png", optimize=True)
    prov.append({"type": "bitmap", "file": f"ruleta:font/{nombre}.png", "height": 32, "ascent": 31, "chars": [chr(code)]})

# Giro: U+E000..E02F
for k in range(FRAMES):
    glifo(f"rueda_{k:02d}", componer(DISCO, k * PASO, fase=(k // 3) % 2), 0xE000 + k)
# Color ganador resaltado: U+E040..E047 (y versión con bombillos alternos U+E048..E04F)
for s in range(8):
    rot = fotograma_final(s) * PASO
    glifo(f"rueda_sel_{s}", componer(DISCOS_SEL[s], rot, fase=0, desv=0), 0xE040 + s)
    glifo(f"rueda_sel_{s}_b", componer(DISCOS_SEL[s], rot, fase=1, desv=0), 0xE048 + s)
def blanquear(img, a):
    """Mezcla la imagen con blanco (a=0 nada, a=1 silueta blanca)."""
    arr = np.asarray(img).astype(float)
    arr[..., :3] = arr[..., :3] * (1 - a) + 255 * a
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def componer_borroso(rot, fase):
    """Fotograma con desenfoque de movimiento para cuando la rueda gira rápido."""
    muestras = [DISCO.rotate(-(rot + o), resample=Image.BICUBIC, center=(C, C)) for o in np.linspace(-12, 12, 7)]
    prom = np.mean([np.asarray(m).astype(float) for m in muestras], axis=0)
    img = AROS[fase].copy()
    img.alpha_composite(Image.fromarray(prom.astype(np.uint8), "RGBA"))
    img.alpha_composite(CENTRO)
    img.alpha_composite(puntero(0))
    return img.resize((256, 256), Image.LANCZOS)


# Giro rápido con desenfoque: U+E100..E12F
for k in range(FRAMES):
    glifo(f"rueda_blur_{k:02d}", componer_borroso(k * PASO, (k // 3) % 2), 0xE100 + k)

# Rueda llena del color ganador: U+E140+s (y bombillos alternos U+E148+s)
DISCOS_LLENOS = [disco(lleno=s) for s in range(8)]
for s in range(8):
    rot = fotograma_final(s) * PASO
    glifo(f"rueda_full_{s}", componer(DISCOS_LLENOS[s], rot, fase=0, desv=0), 0xE140 + s)
    glifo(f"rueda_full_{s}_b", componer(DISCOS_LLENOS[s], rot, fase=1, desv=0), 0xE148 + s)

# Entrada: destello blanco que crece y luego aparecen los colores (U+E050..E055)
BASE0 = componer(DISCO, 0, desv=0)
ENTRADA = [(0.2, 1.0), (0.55, 1.0), (1.06, 1.0), (1.0, 0.7), (1.0, 0.35), (1.0, 0.1)]
for j, (f, w) in enumerate(ENTRADA):
    glifo(f"rueda_in_{j}", escalado(blanquear(BASE0, w), f), 0xE050 + j)

# Salida: la rueda llena del color se encoge (U+E060+s*8+j) ...
SALIDA = [(1.0, 0.0), (0.7, 0.1), (0.38, 0.3)]
for s in range(8):
    llena = componer(DISCOS_LLENOS[s], fotograma_final(s) * PASO, desv=0)
    for j, (f, w) in enumerate(SALIDA):
        glifo(f"rueda_out_{s}_{j}", escalado(blanquear(llena, w), f), 0xE060 + s * 8 + j)
# ... y termina con un destello blanco que desaparece (U+E160..E162)
for j, f in enumerate([0.9, 0.5, 0.12]):
    glifo(f"rueda_flash_{j}", escalado(blanquear(BASE0, 1.0), f), 0xE160 + j)

fuente = f"{PACK}/assets/minecraft/font"
os.makedirs(fuente, exist_ok=True)
json.dump({"providers": prov}, open(f"{fuente}/default.json", "w"), indent=2)

prev = Image.new("RGBA", (256 * 6, 256 * 3), (0, 200, 0, 255))
for j in range(6):
    prev.alpha_composite(Image.open(f"{OUT}/rueda_in_{j}.png"), (256 * j, 0))
for j, n in enumerate(["rueda_blur_00", "rueda_blur_05", "rueda_sel_0", "rueda_full_0", "rueda_full_3", "rueda_full_6_b"]):
    prev.alpha_composite(Image.open(f"{OUT}/{n}.png"), (256 * j, 256))
for j, n in enumerate(["rueda_out_0_0", "rueda_out_0_1", "rueda_out_0_2", "rueda_flash_0", "rueda_flash_1", "rueda_flash_2"]):
    prev.alpha_composite(Image.open(f"{OUT}/{n}.png"), (256 * j, 512))
prev.save("preview.png")
print("ok", len(prov), "glifos")
