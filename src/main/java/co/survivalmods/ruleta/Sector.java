package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;

/**
 * Los 8 colores de la rueda, en orden horario empezando por arriba.
 * El orden DEBE coincidir con las imágenes del paquete de texturas.
 */
public enum Sector {
    ROJO("ROJO", ChatFormatting.RED, Dificultad.EVENTO),
    NARANJA("NARANJA", ChatFormatting.GOLD, Dificultad.DIFICIL),
    AMARILLO("AMARILLO", ChatFormatting.YELLOW, Dificultad.MEDIO),
    VERDE("VERDE", ChatFormatting.GREEN, Dificultad.FACIL),
    CIAN("CIAN", ChatFormatting.AQUA, Dificultad.FACIL),
    AZUL("AZUL", ChatFormatting.BLUE, Dificultad.MEDIO),
    MORADO("MORADO", ChatFormatting.DARK_PURPLE, Dificultad.DIFICIL),
    ROSADO("ROSADO", ChatFormatting.LIGHT_PURPLE, Dificultad.EVENTO);

    public final String nombre;
    public final ChatFormatting color;
    public final Dificultad dificultad;

    Sector(String nombre, ChatFormatting color, Dificultad dificultad) {
        this.nombre = nombre;
        this.color = color;
        this.dificultad = dificultad;
    }

    /** Fotogramas de giro en el paquete de texturas (7,5° cada uno). */
    public static final int FOTOGRAMAS = 48;
    public static final double GRADOS_POR_FOTOGRAMA = 360.0 / FOTOGRAMAS;

    /** Fotograma en el que este sector queda justo bajo la lengüeta. */
    public int fotogramaFinal() {
        return Math.floorMod(FOTOGRAMAS - ordinal() * (FOTOGRAMAS / 8), FOTOGRAMAS);
    }

    /** Carácter de la fuente: rueda girada (fotograma 0-47). */
    public static String glifoRueda(int fotograma) {
        return String.valueOf((char) (0xE000 + Math.floorMod(fotograma, FOTOGRAMAS)));
    }

    public static final int FOTOGRAMAS_ENTRADA = 6;
    public static final int FOTOGRAMAS_SALIDA = 6;

    /** Animación de entrada: la rueda aparece creciendo y girando. */
    public static String glifoEntrada(int i) {
        return String.valueOf((char) (0xE050 + i));
    }

    /** Animación de salida: la rueda llena del color se encoge (0-2) y termina en un destello blanco (3-5). */
    public String glifoSalida(int i) {
        if (i < 3) return String.valueOf((char) (0xE060 + ordinal() * 8 + i));
        return String.valueOf((char) (0xE160 + (i - 3)));
    }

    /** Rueda girando rápido, con desenfoque de movimiento (fotograma 0-47). */
    public static String glifoBorroso(int fotograma) {
        return String.valueOf((char) (0xE100 + Math.floorMod(fotograma, FOTOGRAMAS)));
    }

    /** Rueda completamente pintada del color de este sector. variante 0/1 = bombillos alternos. */
    public String glifoLleno(int variante) {
        return String.valueOf((char) ((variante == 0 ? 0xE140 : 0xE148) + ordinal()));
    }

    /** Rueda detenida con este sector resaltado. variante 0/1 = bombillos alternos. */
    public String glifoResaltado(int variante) {
        return String.valueOf((char) ((variante == 0 ? 0xE040 : 0xE048) + ordinal()));
    }
}
