package co.survivalmods.ruleta;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.ChatFormatting.*;

/**
 * Eventos que cambian el cielo real (timeline del mod, ver data/ruleta/timeline/supernova.json):
 *  - TORMENTA SOLAR: día 2 a medianoche. La luna (fase "third_quarter") se reemplaza por un sol gigante
 *    y el cielo se pone amarillo. Te quemas si estás al aire libre.
 *  - LA GRIETA: día 5 de día. Cielo morado y una grieta enorme en lo alto.
 *    Mientras estés bajo el cielo se llena tu barra de CORRUPCIÓN; si se llena, recibes mucho daño.
 * Fuera de los eventos, el mod se salta los días 2 a 5 para que estos cielos nunca salgan solos.
 */
final class EventosCielo {

    private EventosCielo() {}

    static final long HORA_SOL = 24000L * 2 + 18000;     // medianoche del día 2: luna (sol gigante) en lo más alto
    static final long HORA_GRIETA = 24000L * 5 + 5000;   // mañana del día 5: cielo morado
    private static final String[] GRIETA = Eventos.glifosPublico(0xE260, 25);

    static void registrar() {
        // ------------------------------------------------------------ TORMENTA SOLAR
        Retos.add("tormenta_solar", "TORMENTA SOLAR", Dificultad.EVENTO, 60, (p, rnd, cfg, d) -> Eventos.eventoPublico(p, d,
                "¡TORMENTA SOLAR!", YELLOW,
                Retos.texto("El sol se acercó: ").append(Retos.res("quédate bajo techo o en el agua", YELLOW)).append(Retos.texto(" o te quemas")),
                (s, r, js, t) -> {
                    Eventos.supernovaActiva = true;
                    Eventos.cmd(s, "weather clear");
                    Eventos.cmd(s, "time set " + HORA_SOL);
                    for (ServerPlayer j : js) {
                        Eventos.titulo(j, Eventos.pantallaColor(Eventos.CIELO_BLANCO_GLIFO, 0xFFE070), Component.empty(), 0, 5, 30);
                        Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:block.beacon.activate master @s ~ ~ ~ 1 0.5");
                        Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:item.firecharge.use master @s ~ ~ ~ 1 0.5");
                    }
                },
                (s, r, js, t) -> {
                    if (t < 100) {   // 5 s para cubrirse
                        if (t % 20 == 0) for (ServerPlayer j : js)
                            Eventos.actionbar(j, Component.literal("☀ ¡Cúbrete! El sol quema en " + ((100 - t) / 20) + " s").withStyle(GOLD, BOLD));
                        return;
                    }
                    if (t % 20 != 0) return;
                    for (ServerPlayer j : js) {
                        String n = Eventos.n(j);
                        if (Eventos.expuestoAlCielo(j) && !j.isInWater()) {
                            Eventos.cmd(s, "damage " + n + " 2 minecraft:in_fire");
                            Eventos.cmd(s, "effect give " + n + " minecraft:hunger 2 1 true");
                            Eventos.cmd(s, String.format(Locale.ROOT, "particle minecraft:flame %.2f %.2f %.2f 0.3 0.9 0.3 0.02 20 force @a",
                                    j.getX(), j.getY() + 1, j.getZ()));
                            Eventos.cmd(s, "execute as " + n + " at @s run playsound minecraft:entity.generic.burn master @s ~ ~ ~ 0.6 1");
                            Eventos.actionbar(j, Component.literal("🔥 ¡Te estás quemando! Busca sombra o agua").withStyle(RED, BOLD));
                        } else {
                            Eventos.actionbar(j, Component.literal("✔ A la sombra").withStyle(GREEN));
                        }
                    }
                },
                (s, r, js, t) -> {
                    Eventos.cmd(s, "time set " + (24000L * 6 + 1000));
                    Eventos.supernovaActiva = false;
                }));

        // ------------------------------------------------------------ LA GRIETA
        Retos.add("grieta", "LA GRIETA", Dificultad.EVENTO, 60, (p, rnd, cfg, d) -> Eventos.eventoPublico(p, d,
                "¡LA GRIETA!", DARK_PURPLE,
                Retos.texto("Se abrió el cielo: ").append(Retos.res("no dejes que se llene tu corrupción", LIGHT_PURPLE))
                        .append(Retos.texto(" (escóndete bajo techo)")),
                (s, r, js, t) -> {
                    Eventos.supernovaActiva = true;
                    Eventos.cmd(s, "weather clear");
                    Eventos.cmd(s, "time set " + HORA_GRIETA);
                    // la grieta: una "pantalla" horizontal gigante sobre el grupo (fija en el mundo, no sigue la cámara)
                    // a 45 bloques sobre el grupo (más arriba la niebla de la distancia de renderizado la borra)
                    double[] c = Eventos.centroCielo(js);
                    for (String[] lado : new String[][]{{"grietaA", "[0.7071f,0f,0f,0.7071f]"}, {"grietaB", "[-0.7071f,0f,0f,0.7071f]"}}) {
                        Eventos.invocarPantalla(s, lado[0], c[0], c[1], c[2], GRIETA[0], 230, "fixed", lado[1], "[0f,0f,0f]", 15, 16.0);
                    }
                    Map<UUID, Integer> corr = new HashMap<>();
                    for (ServerPlayer j : js) {
                        corr.put(j.getUUID(), 0);
                        String b = barra(j);
                        Eventos.cmd(s, "bossbar add " + b + " \"Corrupción\"");
                        Eventos.cmd(s, "bossbar set " + b + " color purple");
                        Eventos.cmd(s, "bossbar set " + b + " style notched_10");
                        Eventos.cmd(s, "bossbar set " + b + " max 100");
                        Eventos.cmd(s, "bossbar set " + b + " value 0");
                        Eventos.cmd(s, "bossbar set " + b + " players " + Eventos.n(j));
                        Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:block.end_portal.spawn master @s ~ ~ ~ 1 0.6");
                    }
                    r.datos.put("corr", corr);
                },
                (s, r, js, t) -> {
                    // el cielo se rasga: 24 fotogramas (uno cada 2 ticks = 10 por segundo) y luego titilan las estrellas
                    final int inicio = 20, fotos = 24;
                    int f = -1;
                    if (t >= inicio && t < inicio + fotos * 2 && (t - inicio) % 2 == 0) f = (t - inicio) / 2;
                    else if (t >= inicio + fotos * 2 && t % 15 == 0) f = (t / 15) % 2 == 0 ? fotos - 1 : fotos;
                    if (f >= 0) for (String tag : new String[]{"grietaA", "grietaB"}) Eventos.glifoPantallaPublico(s, tag, GRIETA[f]);
                    if (t >= inicio && t < inicio + fotos * 2 && (t - inicio) % 8 == 0) for (ServerPlayer j : js) {
                        String n = Eventos.n(j);
                        Eventos.cmd(s, "execute as " + n + " at @s run playsound minecraft:block.glass.break master @s ~ ~ ~ 1 " + (0.5 + (t - inicio) / 100.0));
                        if (t == inicio) Eventos.cmd(s, "execute as " + n + " at @s run playsound minecraft:entity.warden.sonic_charge master @s ~ ~ ~ 1 0.5");
                    }
                    if (t == inicio + fotos * 2) for (ServerPlayer j : js)
                        Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:block.end_portal.spawn master @s ~ ~ ~ 1 0.5");
                    if (t % 10 == 0) Eventos.seguirGrupo(s, js, "grietaA", "grietaB");
                    if (t % 40 == 0) {
                        for (ServerPlayer j : js) {
                            Eventos.cmd(s, String.format(Locale.ROOT, "particle minecraft:reverse_portal %.2f %.2f %.2f 12 6 12 0.02 120 force %s",
                                    j.getX(), j.getY() + 14, j.getZ(), Eventos.n(j)));
                            Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:block.portal.ambient master @s ~ ~ ~ 0.7 0.5");
                        }
                    }
                    if (t % 5 != 0) return;
                    @SuppressWarnings("unchecked")
                    Map<UUID, Integer> corr = (Map<UUID, Integer>) r.datos.get("corr");
                    for (ServerPlayer j : js) {
                        int c = corr.getOrDefault(j.getUUID(), 0);
                        boolean expuesto = Eventos.expuestoAlCielo(j);
                        c = Math.max(0, Math.min(100, c + (expuesto ? 3 : -4)));
                        String n = Eventos.n(j);
                        if (c >= 100) {
                            Eventos.cmd(s, "damage " + n + " 10 minecraft:magic");
                            Eventos.cmd(s, "effect give " + n + " minecraft:darkness 4 0 true");
                            Eventos.cmd(s, "effect give " + n + " minecraft:nausea 5 0 true");
                            Eventos.cmd(s, "execute as " + n + " at @s run playsound minecraft:entity.warden.sonic_boom master @s ~ ~ ~ 1 0.8");
                            Eventos.cmd(s, String.format(Locale.ROOT, "particle minecraft:witch %.2f %.2f %.2f 0.5 1 0.5 0.1 60 force @a",
                                    j.getX(), j.getY() + 1, j.getZ()));
                            c = 40;
                        }
                        corr.put(j.getUUID(), c);
                        Eventos.cmd(s, "bossbar set " + barra(j) + " value " + c);
                        if (t % 20 == 0) {
                            Eventos.actionbar(j, expuesto
                                    ? Component.literal("☣ La grieta te está corrompiendo: " + c + "%").withStyle(LIGHT_PURPLE, BOLD)
                                    : Component.literal("✔ Escondido de la grieta (" + c + "%)").withStyle(GREEN));
                        }
                    }
                },
                (s, r, js, t) -> {
                    Eventos.limpiarMobs(s);
                    for (ServerPlayer j : s.getPlayerList().getPlayers()) Eventos.cmd(s, "bossbar remove " + barra(j));
                    Eventos.cmd(s, "time set " + (24000L * 6 + 1000));
                    Eventos.supernovaActiva = false;
                }));
    }

    private static String barra(ServerPlayer j) {
        return "ruleta:corr_" + Eventos.n(j).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }
}
