package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;

public enum Dificultad {
    FACIL("FÁCIL", ChatFormatting.GREEN, 1),
    MEDIO("MEDIO", ChatFormatting.YELLOW, 2),
    DIFICIL("DIFÍCIL", ChatFormatting.GOLD, 3),
    EXTREMO("EXTREMO", ChatFormatting.RED, 4);

    public final String nombre;
    public final ChatFormatting color;
    public final int estrellas;

    Dificultad(String nombre, ChatFormatting color, int estrellas) {
        this.nombre = nombre;
        this.color = color;
        this.estrellas = estrellas;
    }

    public String estrellasTexto() {
        return "★".repeat(estrellas) + "☆".repeat(4 - estrellas);
    }
}
