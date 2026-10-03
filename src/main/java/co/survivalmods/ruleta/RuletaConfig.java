package co.survivalmods.ruleta;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuración guardada en config/ruleta.json.
 * Se puede editar desde Exaroton (Archivos → config → ruleta.json) y recargar con /ruleta recargar.
 */
public class RuletaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static Path ruta() {
        return FabricLoader.getInstance().getConfigDir().resolve("ruleta.json");
    }

    /** Nombres de Minecraft que pueden usar /ruleta. La consola del servidor siempre puede. */
    public List<String> admins = new ArrayList<>();

    /** "muerte" = mata a quien falle. "aviso" = solo avisa (útil para probar). */
    public String castigo = "muerte";

    /** Si es false, los jugadores en creativo o espectador no participan. */
    public boolean afectarCreativo = false;

    /** Segundos entre giros automáticos. 0 = apagado (solo gira cuando un admin lo pide). */
    public int autoIntervaloSegundos = 0;

    /** Distancia máxima (en bloques) para el reto de mirar un bloque. */
    public int distanciaMirar = 24;

    /** Versión del archivo (no tocar). */
    public int configVersion = 0;

    /** true = muestra la rueda de colores (requiere el paquete de texturas). false = ruleta de texto. */
    public boolean ruedaVisual = true;

    /** Retos que pueden salir en la ruleta. Borra los que no quieras. */
    public List<String> retosActivos = new ArrayList<>(Retos.ids());

    /** Colores que pueden salir en los retos de color. */
    public List<String> colores = new ArrayList<>(Colores.idsPorDefecto());

    /** Duración en segundos de cada reto. */
    public Map<String, Integer> duraciones = new LinkedHashMap<>(Retos.duracionesPorDefecto());

    public boolean esAdmin(String nombre) {
        for (String a : admins) {
            if (a.equalsIgnoreCase(nombre)) return true;
        }
        return false;
    }

    public int duracion(Retos.Plantilla p) {
        Integer d = duraciones.get(p.id());
        return d != null && d > 0 ? d : p.duracionPorDefecto();
    }

    public static RuletaConfig cargar() {
        Path ruta = ruta();
        RuletaConfig cfg = null;
        if (Files.exists(ruta)) {
            try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, RuletaConfig.class);
            } catch (Exception e) {
                RuletaMod.LOGGER.error("[Ruleta] No se pudo leer ruleta.json, se usan valores por defecto", e);
            }
        }
        if (cfg == null) cfg = new RuletaConfig();
        if (cfg.admins == null) cfg.admins = new ArrayList<>();
        if (cfg.configVersion < 5) {
            // Versión nueva con dificultades: se reinician las listas de retos y duraciones
            cfg.retosActivos = new ArrayList<>(Retos.ids());
            cfg.duraciones = new LinkedHashMap<>(Retos.duracionesPorDefecto());
            cfg.configVersion = 5;
        }
        if (cfg.retosActivos == null || cfg.retosActivos.isEmpty()) cfg.retosActivos = new ArrayList<>(Retos.ids());
        if (cfg.colores == null || cfg.colores.isEmpty()) cfg.colores = new ArrayList<>(Colores.idsPorDefecto());
        if (cfg.duraciones == null) cfg.duraciones = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : Retos.duracionesPorDefecto().entrySet()) {
            cfg.duraciones.putIfAbsent(e.getKey(), e.getValue());
        }
        if (cfg.castigo == null) cfg.castigo = "muerte";
        cfg.guardar();
        return cfg;
    }

    public void guardar() {
        try {
            Files.createDirectories(ruta().getParent());
            try (Writer w = Files.newBufferedWriter(ruta(), StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            RuletaMod.LOGGER.error("[Ruleta] No se pudo guardar ruleta.json", e);
        }
    }
}
