# Ruleta de Retos — mod de Fabric (Minecraft 26.2)

El mod corre **solo en el servidor**: los jugadores no tienen que instalar nada.
La ruleta gira en pantalla (títulos) con sonido, anuncia el reto en el chat y muestra una cuenta regresiva.
Quien no cumple el reto, muere.

## 1. Compilar el .jar

Necesitas **Java 25** (JDK). Elige una opción:

**A) Con IntelliJ IDEA (recomendado)**
1. Instala IntelliJ IDEA Community y un JDK 25 (por ejemplo, Temurin 25).
2. `File → Open` → abre la carpeta `ruleta`. IntelliJ detecta Gradle y descarga todo solo (la primera vez tarda varios minutos).
3. En `File → Project Structure`, pon el SDK en Java 25.
4. Panel **Gradle** (derecha) → `ruleta → Tasks → build → build`.
5. El mod queda en `build/libs/ruleta-1.0.0.jar`.

**B) Con GitHub (sin instalar nada)**
1. Sube esta carpeta a un repositorio nuevo de GitHub.
2. Ve a la pestaña **Actions**: se ejecuta "Compilar mod" automáticamente.
3. Cuando termine, en **Artifacts** descarga `ruleta-jar` (un .zip con el .jar adentro).

**C) Con Gradle instalado:** `gradle build` dentro de la carpeta.

## 2. Instalar en Exaroton

1. Detén el servidor.
2. **Archivos → mods** → sube `ruleta-1.0.0.jar`. (Fabric API ya debe estar instalado.)
3. Inicia el servidor.
4. En **Consola** de Exaroton escribe: `ruleta admin agregar TuNombreDeMinecraft`
5. Listo. Solo tú (y la consola) pueden usar `/ruleta`. A los demás ni siquiera les aparece el comando.

## 3. Comandos

| Comando | Qué hace |
|---|---|
| `/ruleta girar` | Gira la ruleta con un reto al azar |
| `/ruleta girar <reto>` | Gira, pero cae en el reto que digas (ej. `pisar_color`) |
| `/ruleta cancelar` | Detiene la ruleta o el reto en curso |
| `/ruleta lista` | Muestra los retos y su duración |
| `/ruleta auto <segundos>` | Gira sola cada X segundos (mínimo 30) |
| `/ruleta auto off` | Apaga el giro automático |
| `/ruleta recargar` | Vuelve a leer `config/ruleta.json` |
| `/ruleta admin agregar <nombre>` / `quitar <nombre>` / `lista` | Maneja quién puede usar la ruleta |

## 4. Rueda de colores y retos

La rueda tiene 8 colores. Cada color es una dificultad, y al caer en él sale un reto al azar de esa lista:

| Color | Dificultad |
|---|---|
| Verde, Cian | FÁCIL ★ |
| Amarillo, Azul | MEDIO ★★ |
| Naranja, Morado | DIFÍCIL ★★★ |
| Rojo, Rosado | EXTREMO ★★★★ |

- **FÁCIL:** mirar_cielo, mirar_suelo, agacharse, agua, item_facil, quieto (10 s)
- **MEDIO:** pisar_color, mirar_color, subir (15 bloques), no_agacharse, no_saltar, item_medio
- **DIFÍCIL:** matar_mob, bajar (20 bloques), item_dificil, no_saltar_largo (60 s), quieto_largo (30 s)
- **EXTREMO:** bajo_cero (Y<0), altura (Y≥150), matar_3, item_extremo (diamante, oro...), nether, no_saltar_extremo (2 min)

`/ruleta lista` muestra todo con sus tiempos. `/ruleta girar <id>` fuerza un reto.

### Paquete de texturas (para ver la rueda)

La rueda es una imagen que viene en `ruleta-pack.zip`. En el `server.properties` del servidor:

```
resource-pack=https://raw.githubusercontent.com/andres3080/ruleta-mod/main/ruleta-pack.zip
resource-pack-sha1=<sha1 del zip>
require-resource-pack=true
```

Al entrar, Minecraft pide aceptar el paquete y se descarga solo. Si cambias las imágenes
(`python generar_rueda.py`), vuelve a crear el zip y actualiza el sha1.
La rueda aparece creciendo y girando, frena poco a poco con un "tic" en cada clavo, y al final se encoge antes de mostrar el reto.
Los sonidos (tick, inicio, redoble, ganador) son originales y se generan con `python generar_sonidos.py`.
Sin el paquete, pon `"ruedaVisual": false` en el config y la ruleta se muestra en texto.

## 5. Configuración (`config/ruleta.json`)

```json
{
  "admins": ["TuNombre"],
  "castigo": "muerte",          // "muerte" o "aviso" (solo avisa, para probar)
  "afectarCreativo": false,      // los de creativo/espectador no participan
  "autoIntervaloSegundos": 0,    // 0 = solo gira cuando tú lo pides
  "distanciaMirar": 24,
  "retosActivos": ["pisar_color", "mirar_color", ...],  // borra los que no quieras
  "colores": ["white", "orange", ...],                   // colores que pueden salir
  "duraciones": { "pisar_color": 60, ... }               // segundos de cada reto
}
```

Después de editarlo desde Exaroton (**Archivos → config → ruleta.json**), usa `/ruleta recargar`.

## 6. Agregar un reto nuevo

En `RetoTipo.java`, copia uno de los bloques (por ejemplo `AGUA`), cambia el id, el nombre, la duración y la condición
`(p, r) -> ...`, que devuelve `true` cuando el jugador cumple el reto (o cuando lo rompe, si es de tipo evitar).
