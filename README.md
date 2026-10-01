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

## 4. Retos incluidos

| id | Reto | Tipo | Tiempo |
|---|---|---|---|
| `pisar_color` | Párate sobre un bloque de un color al azar | Cumplir | 60 s |
| `mirar_color` | Apunta con la mira a un bloque de un color al azar | Cumplir | 45 s |
| `tener_item` | Ten un ítem al azar en el inventario | Cumplir | 90 s |
| `agua` | Métete al agua | Cumplir | 45 s |
| `subir` | Sube 15 bloques más alto de donde estabas | Cumplir | 60 s |
| `mirar_cielo` | Mira directo hacia arriba | Cumplir | 15 s |
| `no_agacharse` | Si te agachas, mueres | Evitar | 30 s |
| `quieto` | Si te mueves, mueres | Evitar | 15 s |
| `no_saltar` | Si saltas, mueres | Evitar | 30 s |

- **Cumplir:** tienes que hacerlo antes de que acabe el tiempo. Cuando lo logras, quedas a salvo.
- **Evitar:** si lo haces, mueres en ese instante.

Los colores cuentan para lana, concreto, terracota, vidrio, alfombras, etc. (cualquier bloque cuyo nombre empiece por el color).

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
