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
    img.save(f"{FONT_DIR}/{nombre}.png", optimize=True)
    prov.append({"type": "bitmap", "file": f"ruleta:font/{nombre}.png", "height": height, "ascent": ascent, "chars": [chr(code)]})

for i in range(8): glifo(f"nova_estrella_{i}", estrella(i), 0xE200 + i)
for i in range(2): glifo(f"nova_colapso_{i}", colapso(i), 0xE208 + i)
for i in range(6): glifo(f"nova_explosion_{i}", explosion(i), 0xE210 + i)
for i in range(4): glifo(f"nova_nebulosa_{i}", nebulosa(i), 0xE218 + i)
for i in range(2): glifo(f"gas_capa_{i}", capa_gas(i), 0xE220 + i)
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
a_luna(lienzo.resize((256, 256)), "waning_crescent")          # fase 3: gigante roja
a_luna(explosion(3), "new_moon")                                # fase 4: explosión
a_luna(nebulosa(0), "waxing_crescent")                          # fase 5: nebulosa

prev = Image.new("RGBA", (256 * 6, 256 * 3), (6, 8, 20, 255))
for j, n in enumerate(["nova_estrella_0", "nova_estrella_4", "nova_estrella_7", "nova_colapso_1", "nova_explosion_0", "nova_explosion_2"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png"), (256 * j, 0))
for j, n in enumerate(["nova_explosion_3", "nova_explosion_5", "nova_nebulosa_0", "nova_nebulosa_1", "nova_nebulosa_2", "nova_nebulosa_3"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png"), (256 * j, 256))
for j, n in enumerate(["cielo_rojo_0", "cielo_rojo_3", "cielo_morado_0"]):
    prev.alpha_composite(Image.open(f"{FONT_DIR}/{n}.png").resize((512, 256)), (512 * j, 512))
for j, n in enumerate(["waning_crescent", "new_moon", "waxing_crescent"]):
    prev.alpha_composite(Image.open(f"{LUNA}/{n}.png").convert("RGBA"), (256 * (j + 3), 256 * 2))
prev.save("preview_eventos.png")
print("ok", len(prov))
