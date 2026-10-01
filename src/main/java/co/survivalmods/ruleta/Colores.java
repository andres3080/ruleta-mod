package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Los 16 colores de Minecraft (lana, concreto, terracota, vidrio, alfombra...). */
public final class Colores {
    public record Color(String id, String nombre, ChatFormatting formato) {}

    private static final Map<String, Color> COLORES = new LinkedHashMap<>();

    private static void add(String id, String nombre, ChatFormatting f) {
        COLORES.put(id, new Color(id, nombre, f));
    }

    static {
        add("white", "BLANCO", ChatFormatting.WHITE);
        add("orange", "NARANJA", ChatFormatting.GOLD);
        add("magenta", "MAGENTA", ChatFormatting.LIGHT_PURPLE);
        add("light_blue", "CELESTE", ChatFormatting.AQUA);
        add("yellow", "AMARILLO", ChatFormatting.YELLOW);
        add("lime", "VERDE LIMA", ChatFormatting.GREEN);
        add("pink", "ROSADO", ChatFormatting.LIGHT_PURPLE);
        add("gray", "GRIS", ChatFormatting.DARK_GRAY);
        add("light_gray", "GRIS CLARO", ChatFormatting.GRAY);
        add("cyan", "CIAN", ChatFormatting.DARK_AQUA);
        add("purple", "MORADO", ChatFormatting.DARK_PURPLE);
        add("blue", "AZUL", ChatFormatting.BLUE);
        add("brown", "CAFÉ", ChatFormatting.GOLD);
        add("green", "VERDE", ChatFormatting.DARK_GREEN);
        add("red", "ROJO", ChatFormatting.RED);
        add("black", "NEGRO", ChatFormatting.DARK_GRAY);
    }

    private Colores() {}

    public static List<String> idsPorDefecto() {
        return new ArrayList<>(COLORES.keySet());
    }

    public static Color get(String id) {
        return COLORES.get(id);
    }

    /**
     * true si el bloque es de ese color. Se basa en el id del bloque:
     * "red_wool", "red_concrete", "red_terracotta", "red_stained_glass"... empiezan por "red_".
     * "light_blue_wool" NO cuenta como "blue" porque no empieza por "blue_".
     */
    public static boolean bloqueEsDeColor(BlockState state, String colorId) {
        if (state == null || state.isAir()) return false;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.startsWith(colorId + "_");
    }
}
