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


def estrella(i):
    """Estrella inestable que crece y brilla más (i = 0..7)."""
    f = i / 7
    r0 = 10 + 10 * f
    glow = np.exp(-(RAD / (r0 * (2.2 + f * 1.5))) ** 2)
    core = np.clip(1.2 - RAD / r0, 0, 1) ** 0.6
    rayos = np.zeros_like(RAD)
    for k in range(4):
        th = k * math.pi / 2 + math.pi / 4 * (i % 2) * 0.1
        d = np.abs(np.sin(ANG - th))
        rayos += np.exp(-(d * RAD / 1.6) ** 2) * np.exp(-RAD / (40 + 60 * f))
    pulso = 0.85 + 0.15 * math.sin(i * 1.7)
    inten = np.clip(core + glow * 0.8 * pulso + rayos * (0.4 + 0.6 * f), 0, 1.5)
    tinte = np.array([255, 230 - 40 * f, 200 - 120 * f]) if i < 5 else np.array([230, 240, 255])
    rgb = 255 * np.clip(inten[..., None] * 0.6, 0, 1) + tinte * np.clip(inten[..., None], 0, 1) * 0.6
    rgb = np.where(core[..., None] > 0.5, 255, rgb)
    return a_img(rgb, np.clip(inten, 0, 1) * np.clip((N / 2 - RAD) / 10, 0, 1))


def explosion(i):
    """0 = destello cegador; 1..5 = onda expansiva."""
    if i == 0:
        inten = np.exp(-(RAD / 80) ** 2) * 1.3 + np.exp(-(RAD / 25) ** 2)
        rgb = np.full((N, N, 3), 255.0)
        return a_img(rgb, np.clip(inten, 0, 1) * np.clip((N / 2 - RAD) / 8, 0, 1))
    f = i / 5
    radio = 25 + 95 * f
    anillo = np.exp(-((RAD - radio) / (5 + 6 * f)) ** 2) * (1.2 - 0.7 * f)
    anillo *= 0.75 + 0.25 * fbm(10 + i)
    centro = np.exp(-(RAD / (30 - 15 * f)) ** 2) * (1 - 0.6 * f)
    restos = np.clip((fbm(20 + i) - 0.5) * 4, 0, 1) * np.exp(-((RAD - radio * 0.85) / 16) ** 2) * 0.5
    inten = np.clip(anillo + centro + restos, 0, 1.3)
    rgb = np.dstack([180 + 75 * inten, 200 + 55 * inten, np.full_like(inten, 255)])
    rgb = np.where((centro > 0.3)[..., None], 255, rgb)
    return a_img(rgb, np.clip(inten, 0, 1) * np.clip((N / 2 - RAD) / 8, 0, 1))


def nebulosa(i):
    """Nebulosa de colores que queda después (4 variantes que se alternan)."""
    n1, n2 = fbm(40 + i), fbm(50 + i)
    forma = np.clip(1 - RAD / 118, 0, 1) ** 0.8
    densidad = np.clip((n1 * 1.6 - 0.45) * forma * 1.8, 0, 1)
    anillo = np.exp(-((RAD - 70) / 22) ** 2) * (0.5 + 0.5 * n2)
    d = np.clip(densidad + anillo * 0.7, 0, 1)
    morado, cian, rosa = np.array([150, 60, 230]), np.array([60, 200, 255]), np.array([255, 90, 170])
    mezcla = n2[..., None]
    rgb = morado * (1 - mezcla) + cian * mezcla
    rgb = rgb * (1 - anillo[..., None] * 0.6) + rosa * anillo[..., None] * 0.6
    centro = np.exp(-(RAD / 9) ** 2)
    rgb = rgb * (1 - centro[..., None]) + 255 * centro[..., None]
    return a_img(rgb, np.clip(d * 0.85 + centro, 0, 1))


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
def glifo(nombre, img, code):
    img.save(f"{FONT_DIR}/{nombre}.png", optimize=True)
    prov.append({"type": "bitmap", "file": f"ruleta:font/{nombre}.png", "height": 32, "ascent": 16, "chars": [chr(code)]})

for i in range(8): glifo(f"nova_estrella_{i}", estrella(i), 0xE200 + i)
for i in range(6): glifo(f"nova_explosion_{i}", explosion(i), 0xE210 + i)
for i in range(4): glifo(f"nova_nebulosa_{i}", nebulosa(i), 0xE218 + i)
for i in range(2): glifo(f"gas_capa_{i}", capa_gas(i), 0xE220 + i)

fuente = f"{PACK}/assets/minecraft/font/default.json"
datos = json.load(open(fuente))
datos["providers"] = [p for p in datos["providers"] if not ("nova_" in p["file"] or "gas_" in p["file"])] + prov
json.dump(datos, open(fuente, "w"), indent=2)

# Partícula del gas: se redefine "sculk_soul" (casi no se usa en el juego) para que sean nubes verdes
for i in range(8):
    nube_particula(i).save(f"{PART_DIR}/gas_{i}.png")
os.makedirs(f"{PACK}/assets/minecraft/particles", exist_ok=True)
json.dump({"textures": [f"ruleta:gas_{i}" for i in range(8)]}, open(f"{PACK}/assets/minecraft/particles/sculk_soul.json", "w"), indent=2)

prev = Image.new("RGBA", (256 * 6, 256 * 3), (10, 12, 30, 255))
for j, i in enumerate([0, 3, 7]): prev.alpha_composite(Image.open(f"{FONT_DIR}/nova_estrella_{i}.png"), (256 * j, 0))
prev.alpha_composite(Image.open(f"{FONT_DIR}/nova_explosion_0.png"), (768, 0))
prev.alpha_composite(Image.open(f"{FONT_DIR}/nova_explosion_2.png"), (1024, 0))
prev.alpha_composite(Image.open(f"{FONT_DIR}/nova_explosion_5.png"), (1280, 0))
for j in range(4): prev.alpha_composite(Image.open(f"{FONT_DIR}/nova_nebulosa_{j}.png"), (256 * j, 256))
fondo = Image.new("RGBA", (512, 256), (90, 120, 80, 255))
prev.alpha_composite(fondo, (0, 512))
prev.alpha_composite(Image.open(f"{FONT_DIR}/gas_capa_0.png"), (0, 512))
prev.alpha_composite(Image.open(f"{FONT_DIR}/gas_capa_1.png"), (256, 512))
for j in range(4):
    p = Image.open(f"{PART_DIR}/gas_{j*2}.png").resize((96, 96))
    prev.alpha_composite(p, (560 + j * 110, 590))
prev.save("preview_eventos.png")
print("ok", len(prov))
