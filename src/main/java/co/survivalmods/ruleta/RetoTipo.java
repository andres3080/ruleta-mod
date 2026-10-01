package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Todos los retos que pueden salir en la ruleta.
 * Para agregar uno nuevo: copia un bloque de estos, cambia id/nombre/duración y la condición.
 */
public enum RetoTipo {

    PISAR_COLOR("pisar_color", "PISA UN COLOR", 60, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            Colores.Color c = colorAleatorio(rnd, cfg);
            return new RetoActivo(this,
                    grande("PISA " + c.nombre(), c.formato()),
                    texto("Párate sobre un bloque ").append(resaltado(c.nombre(), c.formato())),
                    false, cfg.duracion(this),
                    (p, r) -> {
                        BlockPos debajo = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
                        return Colores.bloqueEsDeColor(p.level().getBlockState(debajo), c.id());
                    });
        }
    },

    MIRAR_COLOR("mirar_color", "MIRA UN COLOR", 45, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            Colores.Color c = colorAleatorio(rnd, cfg);
            double distancia = Math.max(4, cfg.distanciaMirar);
            return new RetoActivo(this,
                    grande("MIRA " + c.nombre(), c.formato()),
                    texto("Apunta con la mira a un bloque ").append(resaltado(c.nombre(), c.formato())),
                    false, cfg.duracion(this),
                    (p, r) -> {
                        HitResult hit = p.pick(distancia, 1.0F, false);
                        if (hit.getType() != HitResult.Type.BLOCK) return false;
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        return Colores.bloqueEsDeColor(p.level().getBlockState(pos), c.id());
                    });
        }
    },

    TENER_ITEM("tener_item", "CONSIGUE UN ÍTEM", 90, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            Object[] elegido = ITEMS[rnd.nextInt(ITEMS.length)];
            Item item = (Item) elegido[0];
            String nombre = (String) elegido[1];
            return new RetoActivo(this,
                    grande("CONSIGUE: " + nombre.toUpperCase(), ChatFormatting.GOLD),
                    texto("Ten ").append(resaltado(nombre, ChatFormatting.GOLD)).append(texto(" en tu inventario")),
                    false, cfg.duracion(this),
                    (p, r) -> {
                        Inventory inv = p.getInventory();
                        for (int i = 0; i < inv.getContainerSize(); i++) {
                            if (inv.getItem(i).is(item)) return true;
                        }
                        return false;
                    });
        }
    },

    AGUA("agua", "¡AL AGUA!", 45, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("¡AL AGUA!", ChatFormatting.AQUA),
                    texto("Métete en el ").append(resaltado("agua", ChatFormatting.AQUA)),
                    false, cfg.duracion(this),
                    (p, r) -> p.isInWater());
        }
    },

    SUBIR("subir", "¡SUBE!", 60, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("¡SUBE 15 BLOQUES!", ChatFormatting.GREEN),
                    texto("Sube ").append(resaltado("15 bloques", ChatFormatting.GREEN)).append(texto(" más alto de donde estás")),
                    false, cfg.duracion(this),
                    (p, r) -> {
                        double[] ini = r.inicio(p);
                        return ini != null && p.getY() >= ini[1] + 15;
                    });
        }
    },

    MIRAR_CIELO("mirar_cielo", "MIRA AL CIELO", 15, false) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("MIRA AL CIELO", ChatFormatting.YELLOW),
                    texto("Mira ").append(resaltado("directo hacia arriba", ChatFormatting.YELLOW)),
                    false, cfg.duracion(this),
                    (p, r) -> p.getXRot() <= -75.0F);
        }
    },

    NO_AGACHARSE("no_agacharse", "NO TE AGACHES", 30, true) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("NO TE AGACHES", ChatFormatting.RED),
                    texto("Si te agachas (shift), ").append(resaltado("mueres", ChatFormatting.RED)),
                    true, cfg.duracion(this),
                    (p, r) -> p.isShiftKeyDown());
        }
    },

    QUIETO("quieto", "¡QUIETO!", 15, true) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("¡QUIETO!", ChatFormatting.RED),
                    texto("No te muevas de tu sitio o ").append(resaltado("mueres", ChatFormatting.RED)),
                    true, cfg.duracion(this),
                    (p, r) -> {
                        double[] ini = r.inicio(p);
                        if (ini == null) return false;
                        double dx = p.getX() - ini[0];
                        double dz = p.getZ() - ini[2];
                        return dx * dx + dz * dz > 1.5 * 1.5;
                    });
        }
    },

    NO_SALTAR("no_saltar", "NO SALTES", 30, true) {
        @Override
        public RetoActivo crear(Random rnd, RuletaConfig cfg) {
            return new RetoActivo(this,
                    grande("NO SALTES", ChatFormatting.RED),
                    texto("Si saltas, ").append(resaltado("mueres", ChatFormatting.RED)),
                    true, cfg.duracion(this),
                    (p, r) -> {
                        double[] ini = r.inicio(p);
                        return ini != null && RetoActivo.saltos(p) > ini[3];
                    });
        }
    };

    // ------------------------------------------------------------------

    /** Ítems posibles para el reto TENER_ITEM: {Item, nombre en español}. */
    private static final Object[][] ITEMS = {
            {Items.BREAD, "Pan"},
            {Items.TORCH, "Antorcha"},
            {Items.CRAFTING_TABLE, "Mesa de crafteo"},
            {Items.WATER_BUCKET, "Cubo de agua"},
            {Items.ARROW, "Flecha"},
            {Items.COAL, "Carbón"},
            {Items.APPLE, "Manzana"},
            {Items.COBBLESTONE, "Roca"},
            {Items.FURNACE, "Horno"},
            {Items.IRON_INGOT, "Lingote de hierro"},
            {Items.STONE_PICKAXE, "Pico de piedra"},
    };

    public final String id;
    public final String nombreCorto;
    public final int duracionPorDefecto;
    public final boolean evitar;

    RetoTipo(String id, String nombreCorto, int duracionPorDefecto, boolean evitar) {
        this.id = id;
        this.nombreCorto = nombreCorto;
        this.duracionPorDefecto = duracionPorDefecto;
        this.evitar = evitar;
    }

    public abstract RetoActivo crear(Random rnd, RuletaConfig cfg);

    public static RetoTipo porId(String id) {
        for (RetoTipo t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }

    public static List<String> idsPorDefecto() {
        List<String> ids = new ArrayList<>();
        for (RetoTipo t : values()) ids.add(t.id);
        return ids;
    }

    public static Map<String, Integer> duracionesPorDefecto() {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (RetoTipo t : values()) m.put(t.id, t.duracionPorDefecto);
        return m;
    }

    // ---- utilidades de texto ----

    static MutableComponent texto(String s) {
        return Component.literal(s).withStyle(ChatFormatting.WHITE);
    }

    static MutableComponent resaltado(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color, ChatFormatting.BOLD);
    }

    static MutableComponent grande(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(color, ChatFormatting.BOLD);
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
