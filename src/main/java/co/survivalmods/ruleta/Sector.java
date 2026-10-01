package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;

/**
 * Los 8 colores de la rueda, en orden horario empezando por arriba.
 * El orden DEBE coincidir con las imágenes del paquete de texturas.
 */
public enum Sector {
    ROJO("ROJO", ChatFormatting.RED, Dificultad.EXTREMO),
    NARANJA("NARANJA", ChatFormatting.GOLD, Dificultad.DIFICIL),
    AMARILLO("AMARILLO", ChatFormatting.YELLOW, Dificultad.MEDIO),
    VERDE("VERDE", ChatFormatting.GREEN, Dificultad.FACIL),
    CIAN("CIAN", ChatFormatting.AQUA, Dificultad.FACIL),
    AZUL("AZUL", ChatFormatting.BLUE, Dificultad.MEDIO),
    MORADO("MORADO", ChatFormatting.DARK_PURPLE, Dificultad.DIFICIL),
    ROSADO("ROSADO", ChatFormatting.LIGHT_PURPLE, Dificultad.EXTREMO);

    public final String nombre;
    public final ChatFormatting color;
    public final Dificultad dificultad;

    Sector(String nombre, ChatFormatting color, Dificultad dificultad) {
        this.nombre = nombre;
        this.color = color;
        this.dificultad = dificultad;
    }

    /** Fotograma (0-15) del paquete de texturas en el que este sector queda bajo el puntero. */
    public int fotogramaFinal() {
        return 2 * ((8 - ordinal()) % 8);
    }

    /** Carácter de la fuente: rueda girada (fotograma 0-15). */
    public static String glifoRueda(int fotograma) {
        return String.valueOf((char) (0xE000 + Math.floorMod(fotograma, 16)));
    }

    public static final int FOTOGRAMAS_ENTRADA = 5;
    public static final int FOTOGRAMAS_SALIDA = 5;

    /** Animación de entrada: la rueda aparece creciendo y girando. */
    public static String glifoEntrada(int i) {
        return String.valueOf((char) (0xE020 + i));
    }

    /** Animación de salida: la rueda con este sector resaltado se encoge. */
    public String glifoSalida(int i) {
        return String.valueOf((char) (0xE030 + ordinal() * 8 + i));
    }

    /** Carácter de la fuente: rueda con este sector resaltado. */
    public String glifoResaltado() {
        return String.valueOf((char) (0xE010 + ordinal()));
    }
}
