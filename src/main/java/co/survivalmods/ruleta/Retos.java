package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static net.minecraft.ChatFormatting.*;

/**
 * Todos los retos, agrupados por dificultad.
 * Para agregar uno: copia una línea add(...) y cambia id, nombre, dificultad, segundos y condición.
 */
public final class Retos {

    @FunctionalInterface
    public interface Creador {
        RetoActivo crear(Plantilla p, Random rnd, RuletaConfig cfg, int duracion);
    }

    public record Plantilla(String id, String nombreCorto, Dificultad dificultad, int duracionPorDefecto, Creador creador) {
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return creador.crear(this, rnd, cfg, cfg.duracion(this));
        }
    }

    public static final List<Plantilla> TODAS = new ArrayList<>();

    static void add(String id, String nombre, Dificultad dif, int seg, Creador c) {
        TODAS.add(new Plantilla(id, nombre, dif, seg, c));
    }

    // ------------------------------------------------------------------ ítems por dificultad
    // {Item, nombre, cantidad}
    private static final Object[][] ITEMS_FACIL = {
            {Items.CRAFTING_TABLE, "Mesa de crafteo", 1}, {Items.TORCH, "Antorcha", 1},
            {Items.COBBLESTONE, "Roca", 8}, {Items.STICK, "Palo", 4},
    };
    private static final Object[][] ITEMS_MEDIO = {
            {Items.BREAD, "Pan", 1}, {Items.COAL, "Carbón", 3}, {Items.FURNACE, "Horno", 1},
            {Items.ARROW, "Flecha", 1}, {Items.STONE_PICKAXE, "Pico de piedra", 1}, {Items.APPLE, "Manzana", 1},
    };
    private static final Object[][] ITEMS_DIFICIL = {
            {Items.IRON_INGOT, "Lingote de hierro", 5}, {Items.WATER_BUCKET, "Cubo de agua", 1},
            {Items.IRON_PICKAXE, "Pico de hierro", 1}, {Items.SHIELD, "Escudo", 1},
            {Items.IRON_CHESTPLATE, "Peto de hierro", 1}, {Items.GOLD_INGOT, "Lingote de oro", 3},
    };
    private static final Object[][] ITEMS_EXTREMO = {
            {Items.DIAMOND, "Diamante", 3}, {Items.EMERALD, "Esmeralda", 1},
            {Items.OBSIDIAN, "Obsidiana", 4}, {Items.ENDER_PEARL, "Perla de ender", 2},
            {Items.GOLDEN_APPLE, "Manzana dorada", 1}, {Items.CAKE, "Pastel", 1},
            {Items.DIAMOND_PICKAXE, "Pico de diamante", 1}, {Items.IRON_INGOT, "Lingote de hierro", 24},
    };

    static {
        // ============================== FÁCIL ==============================
        add("mirar_cielo", "MIRA AL CIELO", Dificultad.FACIL, 15, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("MIRA AL CIELO", YELLOW), texto("Mira ").append(res("directo hacia arriba", YELLOW)),
                (j, r) -> j.getXRot() <= -75.0F));

        add("mirar_suelo", "MIRA AL SUELO", Dificultad.FACIL, 15, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("MIRA AL SUELO", GOLD), texto("Mira ").append(res("directo hacia tus pies", GOLD)),
                (j, r) -> j.getXRot() >= 75.0F));

        add("agacharse", "¡AGÁCHATE!", Dificultad.FACIL, 10, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡AGÁCHATE!", GREEN), texto("Presiona ").append(res("shift", GREEN)).append(texto(" para agacharte")),
                (j, r) -> j.isShiftKeyDown()));

        add("agua", "¡AL AGUA!", Dificultad.FACIL, 60, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡AL AGUA!", AQUA), texto("Métete en el ").append(res("agua", AQUA)),
                (j, r) -> j.isInWater()));

        add("item_facil", "CONSIGUE UN ÍTEM", Dificultad.FACIL, 60, (p, rnd, cfg, d) -> retoItem(p, rnd, d, ITEMS_FACIL));

        add("quieto", "¡QUIETO!", Dificultad.FACIL, 10, (p, rnd, cfg, d) -> evitar(p, d,
                grande("¡QUIETO!", RED), texto("No te muevas de tu sitio o ").append(res("mueres", RED)),
                Retos::seMovio));

        // ============================== MEDIO ==============================
        add("pisar_color", "PISA UN COLOR", Dificultad.MEDIO, 60, (p, rnd, cfg, d) -> {
            Colores.Color c = colorAleatorio(rnd, cfg);
            return cumplir(p, d, grande("PISA " + c.nombre(), c.formato()),
                    texto("Párate sobre un bloque ").append(res(c.nombre(), c.formato())),
                    (j, r) -> {
                        BlockPos debajo = BlockPos.containing(j.getX(), j.getY() - 0.05, j.getZ());
                        return Colores.bloqueEsDeColor(j.level().getBlockState(debajo), c.id());
                    });
        });

        add("mirar_color", "MIRA UN COLOR", Dificultad.MEDIO, 45, (p, rnd, cfg, d) -> {
            Colores.Color c = colorAleatorio(rnd, cfg);
            double distancia = Math.max(4, cfg.distanciaMirar);
            return cumplir(p, d, grande("MIRA " + c.nombre(), c.formato()),
                    texto("Apunta con la mira a un bloque ").append(res(c.nombre(), c.formato())),
                    (j, r) -> {
                        HitResult hit = j.pick(distancia, 1.0F, false);
                        if (hit.getType() != HitResult.Type.BLOCK) return false;
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        return Colores.bloqueEsDeColor(j.level().getBlockState(pos), c.id());
                    });
        });

        add("subir", "¡SUBE!", Dificultad.MEDIO, 60, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡SUBE 15 BLOQUES!", GREEN),
                texto("Sube ").append(res("15 bloques", GREEN)).append(texto(" más alto de donde estás")),
                (j, r) -> {
                    double[] ini = r.inicio(j);
                    return ini != null && j.getY() >= ini[RetoActivo.Y] + 15;
                }));

        add("no_agacharse", "NO TE AGACHES", Dificultad.MEDIO, 30, (p, rnd, cfg, d) -> evitar(p, d,
                grande("NO TE AGACHES", RED), texto("Si te agachas (shift), ").append(res("mueres", RED)),
                (j, r) -> j.isShiftKeyDown()));

        add("no_saltar", "NO SALTES", Dificultad.MEDIO, 30, (p, rnd, cfg, d) -> evitar(p, d,
                grande("NO SALTES", RED), texto("Si saltas, ").append(res("mueres", RED)),
                Retos::salto));

        add("item_medio", "CONSIGUE UN ÍTEM", Dificultad.MEDIO, 90, (p, rnd, cfg, d) -> retoItem(p, rnd, d, ITEMS_MEDIO));

        // ============================== DIFÍCIL ==============================
        add("matar_mob", "MATA UN MOB", Dificultad.DIFICIL, 60, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("MATA UN MOB", DARK_RED), texto("Mata ").append(res("cualquier mob", DARK_RED)),
                (j, r) -> killsNuevos(j, r) >= 1));

        add("bajar", "¡BAJA!", Dificultad.DIFICIL, 60, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡BAJA 20 BLOQUES!", GOLD),
                texto("Baja ").append(res("20 bloques", GOLD)).append(texto(" más hondo de donde estás")),
                (j, r) -> {
                    double[] ini = r.inicio(j);
                    return ini != null && j.getY() <= ini[RetoActivo.Y] - 20;
                }));

        add("item_dificil", "CONSIGUE UN ÍTEM", Dificultad.DIFICIL, 120, (p, rnd, cfg, d) -> retoItem(p, rnd, d, ITEMS_DIFICIL));

        add("no_saltar_largo", "NO SALTES", Dificultad.DIFICIL, 60, (p, rnd, cfg, d) -> evitar(p, d,
                grande("NO SALTES (60s)", RED), texto("Un minuto entero sin ").append(res("saltar", RED)),
                Retos::salto));

        add("quieto_largo", "¡QUIETO!", Dificultad.DIFICIL, 30, (p, rnd, cfg, d) -> evitar(p, d,
                grande("¡QUIETO! (30s)", RED), texto("Medio minuto sin ").append(res("moverte", RED)),
                Retos::seMovio));

        // ============================== EXTREMO ==============================
        add("bajo_cero", "BAJO CERO", Dificultad.EXTREMO, 120, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡BAJA A Y=0!", DARK_AQUA),
                texto("Llega ").append(res("por debajo de la capa Y=0", DARK_AQUA)),
                (j, r) -> j.getY() < 0));

        add("altura", "A LAS NUBES", Dificultad.EXTREMO, 120, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡SUBE A Y=150!", WHITE),
                texto("Llega ").append(res("por encima de la capa Y=150", WHITE)),
                (j, r) -> j.getY() >= 150));

        add("matar_3", "MATA 3 MOBS", Dificultad.EXTREMO, 90, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("MATA 3 MOBS", DARK_RED), texto("Mata ").append(res("3 mobs", DARK_RED)),
                (j, r) -> killsNuevos(j, r) >= 3));

        add("item_extremo", "CONSIGUE UN ÍTEM", Dificultad.EXTREMO, 240, (p, rnd, cfg, d) -> retoItem(p, rnd, d, ITEMS_EXTREMO));

        add("nether", "AL NETHER", Dificultad.EXTREMO, 180, (p, rnd, cfg, d) -> cumplir(p, d,
                grande("¡AL NETHER!", DARK_RED), texto("Entra al ").append(res("Nether", DARK_RED)),
                (j, r) -> j.level().dimension().equals(Level.NETHER)));

        add("no_saltar_extremo", "NO SALTES", Dificultad.EXTREMO, 120, (p, rnd, cfg, d) -> evitar(p, d,
                grande("NO SALTES (2 min)", RED), texto("Dos minutos sin ").append(res("saltar", RED)),
                Retos::salto));
    }

    static {
        Eventos.registrar();
    }

    private Retos() {}

    // ------------------------------------------------------------------ consultas

    public static Plantilla porId(String id) {
        for (Plantilla p : TODAS) if (p.id().equalsIgnoreCase(id)) return p;
        return null;
    }

    public static List<String> ids() {
        List<String> l = new ArrayList<>();
        for (Plantilla p : TODAS) l.add(p.id());
        return l;
    }

    public static Map<String, Integer> duracionesPorDefecto() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (Plantilla p : TODAS) m.put(p.id(), p.duracionPorDefecto());
        return m;
    }

    /** Retos activos (según el config) de una dificultad. */
    public static List<Plantilla> activos(RuletaConfig cfg, Dificultad dif) {
        List<Plantilla> l = new ArrayList<>();
        for (Plantilla p : TODAS) {
            if ((dif == null || p.dificultad() == dif) && cfg.retosActivos.contains(p.id())) l.add(p);
        }
        return l;
    }

    // ------------------------------------------------------------------ utilidades

    private static RetoActivo cumplir(Plantilla p, int d, Component titulo, Component desc, RetoActivo.Condicion c) {
        return new RetoActivo(p, titulo, desc, false, d, c);
    }

    private static RetoActivo evitar(Plantilla p, int d, Component titulo, Component desc, RetoActivo.Condicion c) {
        return new RetoActivo(p, titulo, desc, true, d, c);
    }

    private static RetoActivo retoItem(Plantilla p, Random rnd, int d, Object[][] lista) {
        Object[] elegido = lista[rnd.nextInt(lista.length)];
        Item item = (Item) elegido[0];
        String nombre = (String) elegido[1];
        int cantidad = (Integer) elegido[2];
        String texto = cantidad > 1 ? cantidad + " x " + nombre : nombre;
        return cumplir(p, d, grande("CONSIGUE: " + texto.toUpperCase(), GOLD),
                texto("Ten ").append(res(texto, GOLD)).append(texto(" en tu inventario")),
                (j, r) -> {
                    Inventory inv = j.getInventory();
                    int total = 0;
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        if (inv.getItem(i).is(item)) total += inv.getItem(i).getCount();
                    }
                    return total >= cantidad;
                });
    }

    private static boolean seMovio(net.minecraft.server.level.ServerPlayer j, RetoActivo r) {
        double[] ini = r.inicio(j);
        if (ini == null) return false;
        double dx = j.getX() - ini[RetoActivo.X];
        double dz = j.getZ() - ini[RetoActivo.Z];
        return dx * dx + dz * dz > 1.5 * 1.5;
    }

    private static boolean salto(net.minecraft.server.level.ServerPlayer j, RetoActivo r) {
        double[] ini = r.inicio(j);
        return ini != null && RetoActivo.saltos(j) > ini[RetoActivo.SALTOS];
    }

    private static double killsNuevos(net.minecraft.server.level.ServerPlayer j, RetoActivo r) {
        double[] ini = r.inicio(j);
        return ini == null ? 0 : RetoActivo.kills(j) - ini[RetoActivo.KILLS];
    }

    static MutableComponent texto(String s) {
        return Component.literal(s).withStyle(WHITE);
    }

    static MutableComponent res(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color, BOLD);
    }

    static MutableComponent grande(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color, BOLD);
    }

    static Colores.Color colorAleatorio(Random rnd, RuletaConfig cfg) {
        List<Colores.Color> validos = new ArrayList<>();
        for (String id : cfg.colores) {
            Colores.Color c = Colores.get(id);
            if (c != null) validos.add(c);
        }
        if (validos.isEmpty()) validos.add(Colores.get("red"));
        return validos.get(rnd.nextInt(validos.size()));
    }
}
