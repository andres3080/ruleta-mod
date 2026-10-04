import glob
"""
Imágenes para los eventos (supernova en el cielo y gas tóxico).
Se agregan a la fuente del paquete y a las partículas. Ejecutar después de generar_rueda.py.
"""
import json, math, os
import numpy as np
from PIL import Image, ImageFilter

PACK = "ruleta-pack"
FONT_DIR = f"{PACK}/assets/ruleta/textures/font"
PART_DIR = f"{PACK}/assets/ruleta/textures/particle"
os.makedirs(FONT_DIR, exist_ok=True); os.makedirs(PART_DIR, exist_ok=True)
N = 256
yy, xx = np.mgrid[0:N, 0:N]
C = N / 2 - 0.5
RAD = np.hypot(xx - C, yy - C)
ANG = np.arctan2(yy - C, xx - C)
rng = np.random.default_rng(3)


def a_img(rgb, a):
    arr = np.dstack([np.clip(rgb, 0, 255), np.clip(a, 0, 1) * 255]).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def ruido(escala, semilla):
    r = np.random.default_rng(semilla)
    base = r.random((N // escala + 2, N // escala + 2))
    im = Image.fromarray((base * 255).astype(np.uint8)).resize((N, N), Image.BICUBIC)
    return np.asarray(im).astype(float) / 255


def fbm(semilla):
    return (ruido(64, semilla) * 0.5 + ruido(32, semilla + 1) * 0.25 + ruido(16, semilla + 2) * 0.15 + ruido(8, semilla + 3) * 0.1)


def suave(rgb, a, borde=10):
    """Recorta en círculo y oscurece los bordes (en Minecraft lo casi transparente se corta de golpe)."""
    a = np.clip(a, 0, 1) * np.clip((N / 2 - 2 - RAD) / borde, 0, 1)
    rgb = rgb * np.clip(a * 2.2, 0, 1)[..., None]     # cerca del borde se funde con el cielo negro
    return a_img(rgb, a)


def estrella(i):
    """Gigante roja (i = 0..7): un 'sol' rojo que crece, burbujea y se vuelve inestable."""
    f = i / 7
    r0 = 42 + 22 * f                                  # radio del disco
    rr = RAD / r0
    disco = rr < 1
    granos = fbm(100 + i) * 0.6 + ruido(6, 200 + i) * 0.4
    limbo = np.sqrt(np.clip(1 - rr ** 2, 0, 1))       # más oscuro en el borde, como el sol
    nucleo = np.array([255, 120, 70]) * (1 - f * 0.4) + np.array([240, 60, 35]) * f * 0.4
    borde_c = np.array([150, 15, 8])
    col = borde_c * (1 - limbo[..., None]) + nucleo * limbo[..., None]
    col = col * (0.75 + 0.45 * granos[..., None])
    manchas = (fbm(300 + i) > 0.74) & disco
    col[manchas] *= 0.55
    corona = np.exp(-np.clip(RAD - r0, 0, None) / (10 + 14 * f)) * (~disco)
    llamas = np.clip(fbm(400 + i) - 0.45, 0, 1) * 3 * np.exp(-np.clip(RAD - r0, 0, None) / 9) * (~disco)
    glow = np.clip(corona * 0.8 + llamas, 0, 1)
    col_glow = np.array([230, 35, 15]) * glow[..., None] + np.array([255, 150, 60]) * (llamas[..., None] * 0.5)
    rgb = np.where(disco[..., None], col, col_glow)
    a = np.where(disco, 1.0, glow)
    return suave(rgb, a, 14)


def colapso(i):
    """La estrella se encoge de golpe y brilla azul-blanco justo antes de explotar."""
    r0 = 14 - 6 * i
    inten = np.clip(1.3 - RAD / r0, 0, 1) + np.exp(-(RAD / (r0 * 3)) ** 2) * 0.8
    rgb = np.dstack([200 + 55 * np.clip(inten, 0, 1), 220 + 35 * np.clip(inten, 0, 1), np.full_like(inten, 255)])
    return suave(rgb, inten, 20)


def explosion(i):
    """0 = destello; 1..5 = onda de gas caliente que se expande (blanca -> naranja -> roja)."""
    if i == 0:
        inten = np.exp(-(RAD / 70) ** 2) * 1.4 + np.exp(-(RAD / 20) ** 2)
        return suave(np.full((N, N, 3), 255.0), inten, 30)
    f = i / 5
    radio = 30 + 85 * f
    fil = fbm(10 + i)
    anillo = np.exp(-((RAD - radio) / (8 + 14 * f)) ** 2) * (0.6 + 0.6 * fil)
    dentro = np.clip(1 - RAD / radio, 0, 1) * (0.35 + 0.5 * fil) * (1 - 0.5 * f)
    centro = np.exp(-(RAD / (18 - 8 * f)) ** 2)
    inten = np.clip(anillo + dentro + centro, 0, 1.2)
    calor = np.clip(1 - f + anillo * 0.3, 0, 1)[..., None]
    caliente, frio = np.array([255, 245, 220]), np.array([255, 110, 40])
    rgb = (caliente * calor + frio * (1 - calor)) * np.clip(inten, 0, 1)[..., None] ** 0.4
    rgb = np.where((centro > 0.3)[..., None], 255, rgb)
    return suave(rgb, inten, 25)


def nebulosa(i):
    """Remanente de supernova: nube de gas morado/azul con filamentos rojos y una estrella de neutrones."""
    n1, n2, n3 = fbm(40 + i), fbm(50 + i), fbm(60 + i)
    forma = np.clip(1 - (RAD / 120) ** 2, 0, 1)
    d = np.clip((0.35 + n1 * 0.9) * forma, 0, 1)
    fil = np.clip(1 - np.abs(n3 - 0.5) * 9, 0, 1) * forma
    morado, azul, rosa = np.array([150, 70, 230]), np.array([70, 150, 255]), np.array([255, 80, 120])
    m = np.clip(n2 * 1.4 - 0.2, 0, 1)[..., None]
    rgb = morado * (1 - m) + azul * m
    rgb = rgb * (0.45 + 0.75 * d[..., None])
    rgb = rgb * (1 - fil[..., None] * 0.7) + rosa * fil[..., None] * 0.7
    centro = np.exp(-(RAD / 7) ** 2)
    halo = np.exp(-(RAD / 30) ** 2) * 0.5
    rgb = rgb * (1 - centro[..., None]) + 255 * centro[..., None] + np.array([120, 160, 255]) * halo[..., None]
    a = np.clip(d * 0.9 + fil * 0.5 + centro + halo, 0, 1)
    return suave(rgb, a, 40)


def tinte(color, fuerza, nombre_ancho=512, alto=256):
    """Capa de color para toda la pantalla (cielo iluminado): más fuerte arriba y en el centro."""
    y, x = np.mgrid[0:alto, 0:nombre_ancho]
    v = 1 - y / alto
    hx = 1 - np.abs(x - nombre_ancho / 2) / (nombre_ancho / 2)
    a = fuerza * (0.45 + 0.55 * v) * (0.75 + 0.25 * hx)
    a = np.clip(a, 0.11 if fuerza < 0.9 else 0, 1)
    rgb = np.ones((alto, nombre_ancho, 3)) * np.array(color, dtype=float)
    return Image.fromarray(np.dstack([rgb, a * 255]).astype(np.uint8), "RGBA")


def capa_gas(i):
    """Capa de gas verde semitransparente (se ve desde arriba y desde abajo)."""
    n = fbm(70 + i)
    borde = np.clip((N / 2 - 4 - RAD) / 45, 0, 1)
    a = np.clip(0.25 + n * 0.55, 0, 0.8) * borde
    rgb = np.dstack([70 + 60 * n, 200 + 40 * n, 50 + 30 * n])
    return a_img(rgb, a)


def nube_particula(i):
    """Nube verde para la partícula del gas."""
    M = 32
    y, x = np.mgrid[0:M, 0:M]
    r = np.hypot(x - 15.5, y - 15.5) / 16
    r2 = np.random.default_rng(90 + i)
    n = np.asarray(Image.fromarray((r2.random((6, 6)) * 255).astype(np.uint8)).resize((M, M), Image.BICUBIC)) / 255
    a = np.clip((1 - r) * (0.6 + 0.6 * n), 0, 1) ** 1.3 * (1 - i / 10)
    rgb = np.dstack([110 + 60 * n, 220 + 30 * n, 80 + 40 * n])
    return a_img(rgb, a)


prov = []
def glifo(nombre, img, code, height=32, ascent=16):
    # Minecraft guarda cada letra en un atlas de 256x256: una imagen más grande sale como un cuadro blanco
    if max(img.size) > 256:
        k = 256 / max(img.size)
        img = img.resize((round(img.size[0] * k), round(img.size[1] * k)), Image.LANCZOS)
    img.save(f"{FONT_DIR}/{nombre}.png", optimize=True)
    prov.append({"type": "bitmap", "file": f"ruleta:font/{nombre}.png", "height": height, "ascent": ascent, "chars": [chr(code)]})

for i in range(8): glifo(f"nova_estrella_{i}", estrella(i), 0xE200 + i)
for i in range(2): glifo(f"nova_colapso_{i}", colapso(i), 0xE208 + i)
for i in range(6): glifo(f"nova_explosion_{i}", explosion(i), 0xE210 + i)
for i in range(4): glifo(f"nova_nebulosa_{i}", nebulosa(i), 0xE218 + i)
for i in range(2): glifo(f"gas_capa_{i}", capa_gas(i), 0xE220 + i)
# Onda expansiva de la supernova: anillo blanco suave (se ve desde abajo, cubre el cielo al crecer)
def onda():
    r = RAD / (N / 2)
    anillo = np.exp(-((r - 0.82) / 0.07) ** 2) + np.exp(-((r - 0.6) / 0.25) ** 2) * 0.35
    anillo *= 0.8 + 0.2 * fbm(500)
    a = np.clip(anillo, 0, 1) * np.clip((1 - r) / 0.06, 0, 1)
    a = np.where(a < 0.11, 0, a)
    return a_img(np.dstack([np.full((N, N), 255.0), np.full((N, N), 250.0), np.full((N, N), 240.0)]), a)
glifo("nova_onda", onda(), 0xE240)

# Capas de color para toda la pantalla (se muestran como título): rojo creciente, destello blanco, morado
for j, f in enumerate([0.14, 0.22, 0.30, 0.38]):
    glifo(f"cielo_rojo_{j}", tinte((255, 60, 20), f), 0xE230 + j, 320, 157)
glifo("cielo_blanco", tinte((255, 255, 255), 1.0), 0xE234, 320, 157)
glifo("cielo_morado_0", tinte((140, 70, 255), 0.18), 0xE235, 320, 157)
glifo("cielo_morado_1", tinte((90, 120, 255), 0.18), 0xE236, 320, 157)

fuente = f"{PACK}/assets/minecraft/font/default.json"
datos = json.load(open(fuente))
datos["providers"] = [p for p in datos["providers"] if not any(k in p["file"] for k in ("nova_", "gas_", "cielo_"))] + prov
json.dump(datos, open(fuente, "w"), indent=2)

# Partícula del gas: se redefine "sculk_soul" (casi no se usa en el juego) para que sean nubes verdes
for i in range(8):
    nube_particula(i).save(f"{PART_DIR}/gas_{i}.png")
os.makedirs(f"{PACK}/assets/minecraft/particles", exist_ok=True)
json.dump({"textures": [f"ruleta:gas_{i}" for i in range(8)]}, open(f"{PACK}/assets/minecraft/particles/sculk_soul.json", "w"), indent=2)

# ---- La luna: 3 fases se reemplazan por la supernova (el juego dibuja la luna sumando luz, el negro es invisible)
LUNA = f"{PACK}/assets/minecraft/textures/environment/celestial/moon"
os.makedirs(LUNA, exist_ok=True)
def a_luna(img, nombre):
    arr = np.asarray(img.convert("RGBA")).astype(float)
    rgb = arr[..., :3] * (arr[..., 3:4] / 255)          # premultiplicado sobre negro
    Image.fromarray(np.dstack([rgb, np.full(rgb.shape[:2], 255)]).astype(np.uint8), "RGBA").save(f"{LUNA}/{nombre}.png")
gigante = estrella(7).resize((330, 330), Image.LANCZOS)
lienzo = Image.new("RGBA", (256, 256), (0, 0, 0, 0)); lienzo.alpha_composite(gigante.crop((37, 37, 293, 293)), (0, 0))
# La supernova ahora es un disco gigante en el cielo (text_display), así que la luna de esas fases es invisible
for nombre in ["waning_crescent", "new_moon", "waxing_crescent"]:
    a_luna(Image.new("RGBA", (256, 256), (0, 0, 0, 0)), nombre)

# ---- Tormenta solar: la fase "third_quarter" de la luna es un sol gigante
def sol():
    r0 = 92
    rr = RAD / r0
    disco = rr < 1
    gran = fbm(600) * 0.5 + ruido(6, 601) * 0.5
    limbo = np.sqrt(np.clip(1 - rr ** 2, 0, 1))
    col = np.array([255, 150, 20]) * (1 - limbo[..., None]) + np.array([255, 245, 170]) * limbo[..., None]
    col = col * (0.85 + 0.3 * gran[..., None])
    corona = np.exp(-np.clip(RAD - r0, 0, None) / 14) * (~disco)
    llamas = np.clip(fbm(602) - 0.4, 0, 1) * 3 * np.exp(-np.clip(RAD - r0, 0, None) / 10) * (~disco)
    g = np.clip(corona * 0.9 + llamas, 0, 1)
    rgb = np.where(disco[..., None], col, np.array([255, 190, 40]) * g[..., None])
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), np.full((N, N), 255)]).astype(np.uint8), "RGBA")
sol().save(f"{LUNA}/third_quarter.png")

# ---- La grieta: el cielo se rasga y por dentro se ve el espacio exterior (6 etapas + 1 variante)
G = 512
gy, gx = np.mgrid[0:G, 0:G]

def espacio(semilla):
    """Interior: espacio profundo con estrellas, nebulosa y una galaxia."""
    r = np.random.default_rng(semilla)
    def nz(esc, k):
        b = np.random.default_rng(semilla * 10 + k).random((G // esc + 2, G // esc + 2))
        return np.asarray(Image.fromarray((b * 255).astype(np.uint8)).resize((G, G), Image.BICUBIC)).astype(float) / 255
    n = nz(64, 1) * 0.5 + nz(32, 2) * 0.3 + nz(16, 3) * 0.2
    m = nz(48, 4)
    base = np.dstack([np.full((G, G), 4.0), np.full((G, G), 3.0), np.full((G, G), 14.0)])
    neb = np.clip((n - 0.45) * 2.2, 0, 1)[..., None]
    col = np.array([120, 40, 200]) * (1 - m[..., None]) + np.array([30, 140, 230]) * m[..., None]
    rgb = base + col * neb * 0.85
    # galaxia espiral pequeña
    cx, cy = G * 0.62, G * 0.47
    dx, dy = gx - cx, (gy - cy) * 2.2
    rr = np.hypot(dx, dy); th = np.arctan2(dy, dx)
    brazo = np.clip(np.cos(2 * th - rr / 9) * 0.5 + 0.5, 0, 1) ** 3 * np.exp(-rr / 38)
    rgb += np.array([255, 230, 200]) * (brazo * 0.9 + np.exp(-(rr / 7) ** 2))[..., None]
    # estrellas
    est = r.random((G, G))
    for umbral, brillo in [(0.9993, 255), (0.997, 160)]:
        m2 = est > umbral
        rgb[m2] = np.maximum(rgb[m2], brillo)
    return np.clip(rgb, 0, 255)

ESPACIO = [espacio(5), espacio(6)]

def borde_ruido(semilla, n):
    r = np.random.default_rng(semilla)
    v = r.normal(0, 1, n)
    for _ in range(12): v = (np.roll(v, 1) + v + np.roll(v, -1)) / 3
    v = v / (np.abs(v).max() + 1e-9) * 1.6
    dientes = np.zeros(n)
    for c in r.integers(0, n, 40):                 # picos del rasgado
        w = r.integers(3, 9)
        dientes += np.clip(1 - np.abs(np.arange(n) - c) / w, 0, 1) * r.uniform(0.6, 1.6)
    return v + dientes

RUIDO_A, RUIDO_B = borde_ruido(11, G), borde_ruido(12, G)

def grieta(f, variante=0):
    """f: 0..1 qué tan abierta está."""
    largo = 0.30 + 0.62 * min(1, f * 1.6)          # fracción del ancho
    abre = 4 + 150 * f ** 1.2                       # apertura máxima en px
    x0, x1 = G / 2 - largo * G / 2, G / 2 + largo * G / 2
    t = np.clip((gx[0] - x0) / (x1 - x0), 0, 1)
    perfil = np.sin(np.pi * t) ** 0.75 * ((gx[0] >= x0) & (gx[0] <= x1))
    curva = np.sin(t * np.pi * 1.3 + 0.4) * 22 * (0.4 + f)       # la grieta no es recta
    arriba = G / 2 + curva - abre * perfil * 0.55 - np.abs(RUIDO_A) * (3 + 9 * f) * perfil
    abajo = G / 2 + curva + abre * perfil * 0.45 + np.abs(RUIDO_B) * (3 + 9 * f) * perfil
    dentro = (gy > arriba[None, :]) & (gy < abajo[None, :])
    # distancia aproximada al borde (vertical) para el brillo del filo
    d = np.minimum(np.abs(gy - arriba[None, :]), np.abs(gy - abajo[None, :]))
    suave_x = np.clip(perfil, 0, 1)[None, :] ** 0.5
    filo = np.exp(-d / (3 + 5 * f)) * suave_x
    halo = np.exp(-d / (14 + 30 * f)) * suave_x * (~dentro)
    rgb = np.zeros((G, G, 3))
    rgb[dentro] = ESPACIO[variante][dentro] * min(1, 0.4 + f)
    glow = np.array([240, 200, 255]) * filo[..., None] + np.array([170, 60, 255]) * halo[..., None]
    rgb = np.clip(rgb + glow, 0, 255)
    a = np.where(dentro, 1.0, np.clip(filo * 1.2 + halo * 0.8, 0, 1))
    a = np.where(a < 0.11, 0, a)
    return Image.fromarray(np.dstack([rgb, a * 255]).astype(np.uint8), "RGBA")

# 24 fotogramas de apertura (0xE260..0xE277) + 1 variante para que titilen las estrellas (0xE278)
for fn in glob.glob(f"{FONT_DIR}/grieta_*.png"): os.remove(fn)
GRIETA_FOTOS = 24
for i in range(GRIETA_FOTOS):
    x = (i + 1) / GRIETA_FOTOS
    f = 0.02 + 0.98 * (1 - (1 - x) ** 2)          # se abre rápido y frena al final
    glifo(f"grieta_{i:02d}", grieta(f, 0), 0xE260 + i)
glifo("grieta_var", grieta(1.0, 1), 0xE260 + GRIETA_FOTOS)

# Explosión fluida de la supernova: 16 fotogramas (0xE280..0xE28F) con la misma textura de gas
def explosion_suave(k, total=16):
    f = k / (total - 1)
    fil = fbm(13)
    radio = 18 + 100 * f
    anillo = np.exp(-((RAD - radio) / (6 + 16 * f)) ** 2) * (0.6 + 0.6 * fil)
    dentro = np.clip(1 - RAD / radio, 0, 1) * (0.35 + 0.5 * fil) * (1 - 0.5 * f)
    centro = np.exp(-(RAD / (24 - 14 * f)) ** 2) * (1.6 - f)
    flash = np.exp(-(RAD / 90) ** 2) * max(0, 1 - f * 4)
    inten = np.clip(anillo + dentro + centro + flash, 0, 1.2)
    calor = np.clip(1 - f * 1.1 + anillo * 0.3, 0, 1)[..., None]
    caliente, frio = np.array([255, 245, 220]), np.array([255, 110, 40])
    rgb = (caliente * calor + frio * (1 - calor)) * np.clip(inten, 0, 1)[..., None] ** 0.4
    rgb = np.where((centro > 0.3)[..., None], 255, rgb)
    return suave(rgb, inten, 25)
for fn in glob.glob(f"{FONT_DIR}/nova_expl16_*.png"): os.remove(fn)
for k in range(16):
    glifo(f"nova_expl16_{k:02d}", explosion_suave(k), 0xE280 + k)

# ---- Supernova en el cielo con TAMAÑO FIJO: el crecimiento va dentro de los fotogramas
# (si se cambia la escala de la pantalla, la imagen se desliza porque no escala desde su centro)
def nebulosa_brillante(i):
    """Nebulosa luminosa (se dibuja sobre el cielo morado, así que necesita colores claros)."""
    n1, n2, n3 = fbm(40 + i), fbm(50 + i), fbm(60 + i)
    forma = np.clip(1 - (RAD / 118) ** 2, 0, 1)
    d = np.clip((0.25 + n1 * 1.0) * forma, 0, 1)
    fil = np.clip(1 - np.abs(n3 - 0.5) * 8, 0, 1) * forma
    morado, azul, rosa = np.array([200, 120, 255]), np.array([110, 200, 255]), np.array([255, 110, 170])
    m = np.clip(n2 * 1.4 - 0.2, 0, 1)[..., None]
    rgb = morado * (1 - m) + azul * m
    rgb = rgb * (1 - fil[..., None] * 0.8) + rosa * fil[..., None] * 0.8
    centro = np.exp(-(RAD / 9) ** 2)
    halo = np.exp(-(RAD / 34) ** 2)
    rgb = rgb * (1 - centro[..., None]) + 255 * centro[..., None]
    rgb = rgb * (1 - halo[..., None] * 0.4) + np.array([200, 220, 255]) * halo[..., None] * 0.4
    a = np.clip(d * 0.85 + fil * 0.6 + centro + halo * 0.6, 0, 1) * np.clip(forma * 3, 0, 1)
    return a_img(np.clip(rgb, 0, 255), a)

ESC_FIJA = 330.0
def encoger(img, escala_antes):
    k = escala_antes / ESC_FIJA
    t = max(2, round(N * k))
    lienzo = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    peq = img.convert("RGBA").resize((t, t), Image.LANCZOS)
    lienzo.alpha_composite(peq, ((N - t) // 2, (N - t) // 2))
    return lienzo

for fn in glob.glob(f"{FONT_DIR}/nova_seq_*.png"): os.remove(fn)
seq = []
for i in range(16):                                   # 0..15 la gigante roja crece
    seq.append(encoger(estrella(min(7, i // 2)), 128 + 126 * i / 15))
seq.append(encoger(colapso(0), 140))                  # 16, 17 colapso
seq.append(encoger(colapso(1), 110))
NEB = [nebulosa_brillante(i) for i in range(4)]
for k in range(16):                                   # 18..33 explosión que se funde con la nebulosa
    img = encoger(explosion_suave(k), 180 + 9 * k)
    if k >= 10:
        w = (k - 9) / 6
        a1 = np.asarray(img).astype(float); a2 = np.asarray(NEB[0]).astype(float)
        img = Image.fromarray((a1 * (1 - w) + a2 * w).astype(np.uint8), "RGBA")
    seq.append(img)
seq += NEB                                            # 34..37 nebulosa
for n_, img in enumerate(seq):
    glifo(f"nova_seq_{n_:02d}", img, 0xE290 + n_)
datos = json.load(open(f"{PACK}/assets/minecraft/font/default.json"))
nuevos = ("grieta_", "nova_expl16_", "nova_seq_")
datos["providers"] = [p for p in datos["providers"] if not any(k in p["file"] for k in nuevos)] + [p for p in prov if any(k in p["file"] for k in nuevos)]
json.dump(datos, open(f"{PACK}/assets/minecraft/font/default.json", "w"), indent=2)

prev = Image.new("RGBA", (256 * 6, 256 * 3), (6, 8, 20, 255))
for j, n in enumerate(["nova_estrella_0", "nova_estrella_4", "nova_estrella_7", "nova_colapso_1", "nova_explosion_0", "nova_explosion_2"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png"), (256 * j, 0))
for j, n in enumerate(["nova_explosion_3", "nova_explosion_5", "nova_nebulosa_0", "nova_nebulosa_1", "nova_nebulosa_2", "nova_nebulosa_3"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png"), (256 * j, 256))
for j, n in enumerate(["cielo_rojo_0", "cielo_rojo_3", "cielo_morado_0"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png").resize((512, 256)), (512 * j, 512))
prev.alpha_composite(Image.open(f"{LUNA}/third_quarter.png").convert("RGBA"), (0, 512))
for k, n in enumerate(["05", "23"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/grieta_{n}.png").resize((256, 256)), (256 * (k + 1), 512))
for j, n in enumerate(["waning_crescent", "new_moon", "waxing_crescent"]):
    prev.alpha_composite(Image.open(f"{LUNA}/{n}.png").convert("RGBA"), (256 * (j + 3), 256 * 2))
prev.save("preview_eventos.png")
print("ok", len(prov))
