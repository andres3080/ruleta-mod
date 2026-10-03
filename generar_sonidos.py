"""Genera los sonidos originales de la ruleta (tick, inicio, ganador) como .ogg."""
import os, subprocess
import numpy as np
from scipy.signal import butter, lfilter
from scipy.io import wavfile

SR = 44100
OUT = "ruleta-pack/assets/ruleta/sounds"
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)

def t(seg): return np.arange(int(SR * seg)) / SR
def bp(x, lo, hi, order=2):
    b, a = butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band"); return lfilter(b, a, x)
def lp(x, f, order=2):
    b, a = butter(order, f / (SR / 2)); return lfilter(b, a, x)
def norm(x, peak=0.9): return x / (np.max(np.abs(x)) + 1e-9) * peak
def guardar(nombre, x):
    wav = f"/tmp/{nombre}.wav"
    wavfile.write(wav, SR, (norm(x) * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
                    f"{OUT}/{nombre}.ogg"], check=True)

# --- tick: clac de la lengüeta de plástico golpeando un clavo ---
tt = t(0.07)
ruido = rng.standard_normal(len(tt))
clac = bp(ruido, 1800, 6000) * np.exp(-tt * 140)
tono = np.sin(2 * np.pi * 2300 * tt) * np.exp(-tt * 90) * 0.6
cuerpo = np.sin(2 * np.pi * 520 * tt) * np.exp(-tt * 60) * 0.5
guardar("tick", clac + tono + cuerpo)

# --- inicio: "fiuuu" que sube + golpe ---
tt = t(0.7)
barrido = np.zeros(len(tt))
ruido = rng.standard_normal(len(tt))
for i, f in enumerate(np.linspace(300, 3500, 24)):
    seg = slice(i * len(tt) // 24, (i + 1) * len(tt) // 24)
    barrido[seg] = bp(ruido, f * 0.7, min(f * 1.4, 20000))[seg]
env = np.sin(np.pi * np.clip(tt / 0.7, 0, 1)) ** 1.5
golpe = np.sin(2 * np.pi * 110 * tt) * np.exp(-tt * 12) * 0.8
guardar("inicio", barrido * env * 0.8 + golpe)

# --- ganador: campanitas en arpegio + brillo ---
tt = t(1.8)
x = np.zeros(len(tt))
def campana(f, ini, dur=1.4, vol=1.0):
    n0 = int(ini * SR); tl = t(dur)
    s = sum(a * np.sin(2 * np.pi * f * h * tl) for h, a in [(1, 1), (2.01, 0.45), (3.0, 0.25), (4.2, 0.12)])
    s *= np.exp(-tl * 3.2) * vol * np.minimum(1, tl / 0.005)
    end = min(len(x), n0 + len(s)); x[n0:end] += s[:end - n0]
for i, f in enumerate([1046.5, 1318.5, 1568.0, 2093.0]):   # Do-Mi-Sol-Do
    campana(f, i * 0.09, vol=0.8 if i < 3 else 1.0)
campana(1568.0, 0.36, 1.4, 0.5); campana(2637.0, 0.36, 1.2, 0.35)
brillo = bp(rng.standard_normal(len(tt)), 6000, 14000) * np.exp(-tt * 2.5) * 0.15
guardar("ganador", x + brillo)

# --- suspenso: redoble corto (mientras parpadea el color) ---
tt = t(1.0)
x = np.zeros(len(tt))
for k, ini in enumerate(np.arange(0, 0.85, 0.045)):
    n0 = int(ini * SR); tl = t(0.06)
    g = bp(rng.standard_normal(len(tl)), 150, 2500) * np.exp(-tl * 70) * (0.5 + 0.5 * ini / 0.85)
    x[n0:n0 + len(g)] += g
guardar("redoble", x)
# --- aparece: "pop" + brillo que sube ---
def brillo(dur, f0, f1, n, vol=0.5):
    x = np.zeros(int(SR * dur))
    for k in range(n):
        ini = k * dur / n
        f = f0 + (f1 - f0) * k / max(1, n - 1)
        tl = t(0.12)
        g = np.sin(2 * np.pi * f * tl) * np.exp(-tl * 30) * vol
        a = int(ini * SR); x[a:a + len(g)] += g[:len(x) - a]
    return x
tt = t(0.8)
pop = np.sin(2 * np.pi * (180 + 600 * np.exp(-tt * 25)) * tt) * np.exp(-tt * 14)
x = pop * 0.8
b = brillo(0.6, 1500, 4200, 14, 0.35); x[:len(b)] += b
x += bp(rng.standard_normal(len(tt)), 3000, 9000) * np.exp(-tt * 6) * 0.12
guardar("aparece", x)

# --- desaparece: brillo que baja + "fiuu" ---
tt = t(0.8)
x = brillo(0.8, 3800, 900, 12, 0.35)
ruido = rng.standard_normal(len(tt)); sw = np.zeros(len(tt))
for i, f in enumerate(np.linspace(4000, 400, 20)):
    seg = slice(i * len(tt) // 20, (i + 1) * len(tt) // 20)
    sw[seg] = bp(ruido, f * 0.7, min(f * 1.4, 20000))[seg]
x[:len(sw)] += sw * np.sin(np.pi * np.clip(tt / 0.8, 0, 1)) * 0.5
guardar("desaparece", x)
print("sonidos ok")
