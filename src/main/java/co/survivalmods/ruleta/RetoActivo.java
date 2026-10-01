package co.survivalmods.ruleta;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Un reto ya sorteado (con su color/ítem concreto) que está en juego. */
public final class RetoActivo {

    /** Para retos de cumplir: true = ya lo cumplió. Para retos de evitar: true = lo rompió. */
    @FunctionalInterface
    public interface Condicion {
        boolean test(ServerPlayer jugador, RetoActivo reto);
    }

    // Índices del arreglo de datos iniciales
    public static final int X = 0, Y = 1, Z = 2, SALTOS = 3, KILLS = 4;

    public final Retos.Plantilla plantilla;
    /** Texto grande en pantalla, ej. "PISA ROJO". */
    public final Component titulo;
    /** Explicación, ej. "Párate sobre un bloque ROJO". */
    public final Component descripcion;
    /** true = reto de "NO hagas X". false = "haz X antes de que acabe el tiempo". */
    public final boolean evitar;
    public final int duracionSeg;
    private final Condicion condicion;

    private final Map<UUID, double[]> inicio = new HashMap<>();

    public RetoActivo(Retos.Plantilla plantilla, Component titulo, Component descripcion, boolean evitar,
                      int duracionSeg, Condicion condicion) {
        this.plantilla = plantilla;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.evitar = evitar;
        this.duracionSeg = duracionSeg;
        this.condicion = condicion;
    }

    public void registrarInicio(ServerPlayer p) {
        inicio.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ(), saltos(p), kills(p)});
    }

    public double[] inicio(ServerPlayer p) {
        return inicio.get(p.getUUID());
    }

    public boolean evaluar(ServerPlayer p) {
        return condicion.test(p, this);
    }

    public static int saltos(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
    }

    public static int kills(ServerPlayer p) {
        return p.getStats().getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
    }
}
