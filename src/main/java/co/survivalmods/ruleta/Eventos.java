package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static net.minecraft.ChatFormatting.*;

/**
 * Eventos de supervivencia (colores ROJO y ROSADO de la rueda).
 * Gana quien siga vivo al terminar el tiempo; si mueres durante el evento, quedas eliminado.
 * Todo se hace con comandos de Minecraft para no depender de clases internas.
 * Los mobs y flechas del evento llevan la etiqueta "ruleta_ev" y se borran al terminar.
 */
public final class Eventos {

    private Eventos() {}

    private static final Random RND = new Random();
    private static final String TAG = "ruleta_ev";
    private static final double ALTURA_NOVA = 80;

    /** Imágenes del paquete de texturas (ver generar_cielo.py). */
    private static final String[] NOVA_ESTRELLA = glifos(0xE200, 8);
    private static final String[] NOVA_EXPLOSION = glifos(0xE210, 6);
    private static final String[] NOVA_NEBULOSA = glifos(0xE218, 4);
    private static final String[] GAS_CAPA = glifos(0xE220, 2);

    private static String[] glifos(int desde, int cuantos) {
        String[] g = new String[cuantos];
        for (int i = 0; i < cuantos; i++) g[i] = String.valueOf((char) (desde + i));
        return g;
    }

    static void registrar() {
        // ------------------------------------------------------------ LLUVIA DE CREEPERS
        Retos.add("lluvia_creepers", "LLUVIA DE CREEPERS", Dificultad.EVENTO, 45, (p, rnd, cfg, d) -> evento(p, d,
                "¡LLUVIA DE CREEPERS!", GREEN,
                Retos.texto("Sobrevive a los ").append(Retos.res("creepers", GREEN)).append(Retos.texto(" (no rompen bloques)")),
                (s, r, js, t) -> {
                    cmd(s, "gamerule minecraft:mob_griefing false");
                },
                (s, r, js, t) -> {
                    if (t % 50 != 0) return;
                    for (ServerPlayer j : js) {
                        for (int i = 0; i < 2; i++) invocarCerca(s, j, "minecraft:creeper", 5, 9, "");
                    }
                },
                (s, r, js, t) -> {
                    limpiarMobs(s);
                    cmd(s, "gamerule minecraft:mob_griefing true");
                }));

        // ------------------------------------------------------------ HORDA NOCTURNA
        Retos.add("horda_nocturna", "HORDA NOCTURNA", Dificultad.EVENTO, 60, (p, rnd, cfg, d) -> evento(p, d,
                "¡HORDA NOCTURNA!", DARK_PURPLE,
                Retos.texto("Se hizo de noche: sobrevive a las ").append(Retos.res("oleadas de monstruos", DARK_PURPLE)),
                (s, r, js, t) -> cmd(s, "time set midnight"),
                (s, r, js, t) -> {
                    if (t % 240 != 0) return;                 // una oleada cada 12 s
                    int oleada = t / 240 + 1;
                    String[] tipos = {"minecraft:zombie", "minecraft:skeleton", "minecraft:spider"};
                    for (ServerPlayer j : js) {
                        for (int i = 0; i < oleada + 1; i++) {
                            invocarCerca(s, j, tipos[RND.nextInt(tipos.length)], 6, 11, "");
                        }
                    }
                    avisar(js, Component.literal("☠ Oleada " + oleada).withStyle(DARK_PURPLE, BOLD));
                },
                (s, r, js, t) -> {
                    limpiarMobs(s);
                    cmd(s, "time set day");
                }));

        // ------------------------------------------------------------ TORMENTA DE RAYOS
        Retos.add("tormenta", "TORMENTA DE RAYOS", Dificultad.EVENTO, 40, (p, rnd, cfg, d) -> evento(p, d,
                "¡TORMENTA DE RAYOS!", YELLOW,
                Retos.texto("Donde veas ").append(Retos.res("chispas", YELLOW)).append(Retos.texto(" va a caer un rayo: ¡muévete o métete bajo techo!")),
                (s, r, js, t) -> {
                    cmd(s, "weather thunder 90s");
                    r.datos.put("rayos", new ArrayList<double[]>());
                },
                (s, r, js, t) -> {
                    @SuppressWarnings("unchecked")
                    List<double[]> rayos = (List<double[]>) r.datos.get("rayos");
                    // nuevo objetivo para cada jugador cada 1,25 s
                    if (t % 25 == 0) {
                        for (ServerPlayer j : js) {
                            double x = j.getX(), z = j.getZ();
                            if (RND.nextFloat() > 0.5F) {
                                double a = RND.nextDouble() * Math.PI * 2, dist = 2 + RND.nextDouble() * 3;
                                x += Math.cos(a) * dist;
                                z += Math.sin(a) * dist;
                            }
                            rayos.add(new double[]{x, z, t + 25});   // cae en 1,25 s
                        }
                    }
                    List<double[]> hechos = new ArrayList<>();
                    for (double[] ry : rayos) {
                        String sup = String.format(Locale.ROOT, "execute positioned %.2f 0 %.2f positioned over motion_blocking run ", ry[0], ry[1]);
                        if (t < ry[2] && t % 4 == 0) {
                            cmd(s, sup + "particle minecraft:electric_spark ~ ~0.3 ~ 0.6 0.1 0.6 0 25 force");
                        } else if (t == (int) ry[2]) {
                            cmd(s, sup + "summon minecraft:lightning_bolt ~ ~ ~");
                        } else if (t >= ry[2] + 2) {
                            // apagar el fuego que deja el rayo para no quemar construcciones
                            cmd(s, sup + "fill ~-3 ~-2 ~-3 ~3 ~3 ~3 minecraft:air replace minecraft:fire");
                            hechos.add(ry);
                        }
                    }
                    rayos.removeAll(hechos);
                },
                (s, r, js, t) -> cmd(s, "weather clear")));

        // ------------------------------------------------------------ GAS TÓXICO
        Retos.add("gas_toxico", "GAS TÓXICO", Dificultad.EVENTO, 60, (p, rnd, cfg, d) -> evento(p, d,
                "¡GAS TÓXICO!", DARK_GREEN,
                Retos.texto("El gas está subiendo: ").append(Retos.res("sube 10 bloques", GREEN)).append(Retos.texto(" por encima de donde estabas")),
                (s, r, js, t) -> {
                    Map<UUID, Double> base = new HashMap<>();
                    for (ServerPlayer j : js) {
                        double b = Math.floor(j.getY());
                        base.put(j.getUUID(), b);
                        // superficie del gas: una capa que mira hacia arriba y otra hacia abajo (para verla desde adentro)
                        invocarPantalla(s, "gasA_" + n(j), j.getX(), b, j.getZ(), GAS_CAPA[0], 45, "fixed",
                                "[-0.7071f,0f,0f,0.7071f]", "[0f,0f,0f]", 12, 2.0);
                        invocarPantalla(s, "gasB_" + n(j), j.getX(), b, j.getZ(), GAS_CAPA[1], 45, "fixed",
                                "[0.7071f,0f,0f,0.7071f]", "[0f,0f,0f]", 12, 2.0);
                    }
                    r.datos.put("base", base);
                },
                (s, r, js, t) -> {
                    @SuppressWarnings("unchecked")
                    Map<UUID, Double> base = (Map<UUID, Double>) r.datos.get("base");
                    double sube = Math.min(1.0, t / 300.0);          // el gas tarda 15 s en llegar arriba
                    for (ServerPlayer j : js) {
                        Double b = base.get(j.getUUID());
                        if (b == null) continue;
                        double tope = b + 10 * sube + 0.5;
                        if (t % 5 == 0) {
                            // la capa de gas sigue al jugador y va subiendo
                            String pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", j.getX(), tope, j.getZ());
                            cmd(s, "tp @e[tag=gasA_" + n(j) + "] " + pos);
                            cmd(s, "tp @e[tag=gasB_" + n(j) + "] " + pos);
                            // nubes de gas dentro del volumen
                            double centro = (b + tope) / 2, alto = Math.max(0.3, (tope - b) / 2);
                            cmd(s, String.format(Locale.ROOT, "particle minecraft:sculk_soul %.2f %.2f %.2f 8 %.2f 8 0.005 60 force @a",
                                    j.getX(), centro, j.getZ(), alto));
                        }
                        if (t % 40 == 0) {
                            cmd(s, "data merge entity @e[tag=gasA_" + n(j) + ",limit=1] {text:{text:\"" + GAS_CAPA[(t / 40) % 2] + "\"}}");
                        }
                        if (t >= 100 && t % 20 == 0 && j.getY() < tope) {
                            cmd(s, "damage " + n(j) + " 3 minecraft:magic");
                            cmd(s, "effect give " + n(j) + " minecraft:nausea 4 0 true");
                            actionbar(j, Component.literal("☣ ¡Estás en el gas! Sube más alto").withStyle(GREEN, BOLD));
                        }
                    }
                },
                (s, r, js, t) -> limpiarMobs(s)));

        // ------------------------------------------------------------ ENJAMBRE DE PHANTOMS
        Retos.add("enjambre_phantoms", "ENJAMBRE DE PHANTOMS", Dificultad.EVENTO, 45, (p, rnd, cfg, d) -> evento(p, d,
                "¡ENJAMBRE DE PHANTOMS!", BLUE,
                Retos.texto("Sobrevive a los ").append(Retos.res("phantoms", BLUE)).append(Retos.texto(" que bajan del cielo")),
                null,
                (s, r, js, t) -> {
                    if (t % 100 != 0) return;
                    for (ServerPlayer j : js) {
                        for (int i = 0; i < 2; i++) {
                            double a = RND.nextDouble() * Math.PI * 2;
                            cmd(s, String.format(Locale.ROOT, "summon minecraft:phantom %.2f %.2f %.2f {Tags:[\"%s\"]}",
                                    j.getX() + Math.cos(a) * 6, j.getY() + 12, j.getZ() + Math.sin(a) * 6, TAG));
                        }
                    }
                    // que no se quemen con el sol
                    cmd(s, "effect give @e[type=minecraft:phantom,tag=" + TAG + "] minecraft:fire_resistance 120 0 true");
                },
                (s, r, js, t) -> limpiarMobs(s)));

        // ------------------------------------------------------------ A CIEGAS
        Retos.add("a_ciegas", "A CIEGAS", Dificultad.EVENTO, 40, (p, rnd, cfg, d) -> evento(p, d,
                "¡A CIEGAS!", GRAY,
                Retos.texto("Sobrevive ").append(Retos.res("sin ver", GRAY)).append(Retos.texto(": hay monstruos cerca")),
                (s, r, js, t) -> {
                    for (ServerPlayer j : js) cmd(s, "effect give " + n(j) + " minecraft:blindness " + (r.duracionSeg + 2) + " 0 true");
                },
                (s, r, js, t) -> {
                    if (t % 200 != 0) return;
                    for (ServerPlayer j : js) {
                        invocarCerca(s, j, "minecraft:zombie", 4, 7, "");
                        invocarCerca(s, j, RND.nextBoolean() ? "minecraft:zombie" : "minecraft:spider", 4, 7, "");
                    }
                },
                (s, r, js, t) -> {
                    limpiarMobs(s);
                    for (ServerPlayer j : js) cmd(s, "effect clear " + n(j) + " minecraft:blindness");
                }));

        // ------------------------------------------------------------ LLUVIA DE FLECHAS
        Retos.add("lluvia_flechas", "LLUVIA DE FLECHAS", Dificultad.EVENTO, 30, (p, rnd, cfg, d) -> evento(p, d,
                "¡LLUVIA DE FLECHAS!", GOLD,
                Retos.texto("Caen flechas del cielo: ").append(Retos.res("busca techo", GOLD)),
                null,
                (s, r, js, t) -> {
                    if (t < 60) return;                               // 3 s para reaccionar
                    for (ServerPlayer j : js) {
                        if (t % 4 == 0) {
                            for (int i = 0; i < 2; i++) {
                                double a = RND.nextDouble() * Math.PI * 2, dist = RND.nextDouble() * 3;
                                flecha(s, j.getX() + Math.cos(a) * dist, j.getY() + 20, j.getZ() + Math.sin(a) * dist);
                            }
                        }
                        if (t % 20 == 0) flecha(s, j.getX(), j.getY() + 20, j.getZ());
                    }
                },
                (s, r, js, t) -> limpiarMobs(s)));

        // ------------------------------------------------------------ SUPERNOVA
        Retos.add("supernova", "SUPERNOVA", Dificultad.EVENTO, 75, (p, rnd, cfg, d) -> evento(p, d,
                "¡SUPERNOVA!", AQUA,
                Retos.texto("Una estrella va a explotar: ").append(Retos.res("pon bloques encima de ti", AQUA))
                        .append(Retos.texto(" para protegerte de la radiación")),
                (s, r, js, t) -> {
                    cmd(s, "time set midnight");
                    // una estrella enorme en el cielo sobre cada jugador
                    for (ServerPlayer j : js) {
                        invocarPantalla(s, "nova_" + n(j), j.getX(), j.getY() + ALTURA_NOVA, j.getZ(), NOVA_ESTRELLA[0], 25,
                                "center", "[0f,0f,0f,1f]", "[0f,0f,0f]", 15, 4.0);
                    }
                },
                (s, r, js, t) -> {
                    final int aviso = 300;                            // 15 s para cubrirse
                    // la imagen del cielo sigue a cada jugador
                    if (t % 10 == 0) {
                        for (ServerPlayer j : js) {
                            cmd(s, String.format(Locale.ROOT, "tp @e[tag=nova_%s] %.2f %.2f %.2f", n(j), j.getX(), j.getY() + ALTURA_NOVA, j.getZ()));
                        }
                    }
                    if (t < aviso) {
                        // la estrella crece, parpadea y se vuelve inestable
                        double f = t / (double) aviso;
                        int idx = Math.min(7, (int) (f * 8));
                        if (t % 4 == 0) {
                            int frame = (t % 8 < 4) ? idx : Math.max(0, idx - 1);
                            for (ServerPlayer j : js) glifoPantalla(s, "nova_" + n(j), NOVA_ESTRELLA[frame]);
                        }
                        if (t % 20 == 0) {
                            for (ServerPlayer j : js) escalaPantalla(s, "nova_" + n(j), 25 + 45 * f, 20);
                        }
                        int seg = (aviso - t) / 20;
                        if (t % 20 == 0 && seg <= 5 && seg >= 1) {
                            for (ServerPlayer j : js) {
                                titulo(j, Component.literal(String.valueOf(seg)).withStyle(RED, BOLD),
                                        Component.literal("¡Cúbrete!").withStyle(WHITE), 0, 22, 0);
                                cmd(s, "execute as " + n(j) + " at @s run playsound minecraft:block.note_block.bass master @s ~ ~ ~ 1 0.5");
                            }
                        }
                    } else if (t < aviso + 30) {
                        // ¡EXPLOSIÓN!: destello cegador y onda expansiva
                        int k = t - aviso;
                        for (ServerPlayer j : js) {
                            String tag = "nova_" + n(j);
                            if (k == 0) {
                                glifoPantalla(s, tag, NOVA_EXPLOSION[0]);
                                escalaPantalla(s, tag, 190, 3);
                                String c = String.format(Locale.ROOT, "%.2f %.2f %.2f", j.getX(), j.getY() + ALTURA_NOVA, j.getZ());
                                cmd(s, "particle minecraft:explosion_emitter " + c + " 10 10 10 0 30 force " + n(j));
                                cmd(s, "execute as " + n(j) + " at @s run playsound minecraft:entity.generic.explode master @s ~ ~ ~ 1 0.4");
                                cmd(s, "execute as " + n(j) + " at @s run playsound minecraft:entity.wither.spawn master @s ~ ~ ~ 0.8 0.6");
                                cmd(s, "execute as " + n(j) + " at @s run playsound minecraft:entity.lightning_bolt.thunder master @s ~ ~ ~ 1 0.5");
                                cmd(s, "effect give " + n(j) + " minecraft:nausea 6 0 true");
                                titulo(j, Component.literal("☢ ¡SUPERNOVA!").withStyle(AQUA, BOLD),
                                        Component.literal("¡Radiación! Ponte bajo bloques").withStyle(WHITE), 0, 50, 15);
                            } else if (k % 4 == 0 && k / 4 <= 5) {
                                glifoPantalla(s, tag, NOVA_EXPLOSION[k / 4]);
                                escalaPantalla(s, tag, 170 + 15 * (k / 4), 4);
                            }
                        }
                    } else {
                        // queda una nebulosa de colores; la radiación daña a quien vea el cielo
                        int k = t - aviso - 30;
                        if (k == 0) {
                            for (ServerPlayer j : js) {
                                glifoPantalla(s, "nova_" + n(j), NOVA_NEBULOSA[0]);
                                escalaPantalla(s, "nova_" + n(j), 150, 15);
                            }
                        } else if (k % 30 == 0) {
                            for (ServerPlayer j : js) glifoPantalla(s, "nova_" + n(j), NOVA_NEBULOSA[(k / 30) % 4]);
                        }
                    }
                    if (t >= aviso && t % 20 == 0) {
                        for (ServerPlayer j : js) {
                            BlockPos ojos = BlockPos.containing(j.getX(), j.getEyeY(), j.getZ());
                            if (j.level().canSeeSky(ojos)) {
                                cmd(s, "damage " + n(j) + " 4 minecraft:magic");
                                cmd(s, String.format(Locale.ROOT, "particle minecraft:glow %.2f %.2f %.2f 0.4 0.8 0.4 0 15 force @a",
                                        j.getX(), j.getY() + 1, j.getZ()));
                                actionbar(j, Component.literal("☢ ¡Estás expuesto a la radiación!").withStyle(AQUA, BOLD));
                            }
                        }
                    }
                },
                (s, r, js, t) -> {
                    limpiarMobs(s);
                    cmd(s, "time set day");
                }));
    }

    // ------------------------------------------------------------------ utilidades

    private static RetoActivo evento(Retos.Plantilla p, int d, String titulo, ChatFormatting color, Component desc,
                                     RetoActivo.Hook ini, RetoActivo.Hook tick, RetoActivo.Hook fin) {
        RetoActivo r = new RetoActivo(p, Retos.grande(titulo, color), desc, true, d, (j, x) -> false);
        r.sobrevivir = true;
        r.alIniciar = ini;
        r.alTick = tick;
        r.alTerminar = fin;
        return r;
    }

    static void cmd(MinecraftServer s, String c) {
        try {
            s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), c);
        } catch (Exception e) {
            RuletaMod.LOGGER.warn("[Ruleta] Falló el comando: {}", c, e);
        }
    }

    static String n(ServerPlayer j) {
        return j.getName().getString();
    }

    static void limpiarMobs(MinecraftServer s) {
        cmd(s, "kill @e[tag=" + TAG + "]");
    }

    /** Invoca un mob cerca del jugador: en la superficie si está al aire libre, o a su altura si está bajo techo. */
    static void invocarCerca(MinecraftServer s, ServerPlayer j, String tipo, double min, double max, String nbtExtra) {
        double a = RND.nextDouble() * Math.PI * 2;
        BlockPos ojos = BlockPos.containing(j.getX(), j.getEyeY(), j.getZ());
        boolean afuera = j.level().canSeeSky(ojos);
        double dist = afuera ? min + RND.nextDouble() * (max - min) : 1.5 + RND.nextDouble();
        double x = j.getX() + Math.cos(a) * dist, z = j.getZ() + Math.sin(a) * dist;
        String nbt = "{Tags:[\"" + TAG + "\"]" + nbtExtra + "}";
        if (afuera) {
            cmd(s, String.format(Locale.ROOT, "execute positioned %.2f 0 %.2f positioned over motion_blocking_no_leaves run summon %s ~ ~ ~ %s",
                    x, z, tipo, nbt));
        } else {
            cmd(s, String.format(Locale.ROOT, "summon %s %.2f %.2f %.2f %s", tipo, x, j.getY(), z, nbt));
        }
    }

    /**
     * Crea una "pantalla" (text_display) que muestra una imagen del paquete de texturas en el mundo.
     * billboard "center" = siempre mira al jugador; "fixed" = usa la rotación dada.
     */
    static void invocarPantalla(MinecraftServer s, String tag, double x, double y, double z, String glifo, double escala,
                                String billboard, String rotacion, String traslacion, int brillo, double alcance) {
        cmd(s, String.format(Locale.ROOT,
                "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],billboard:\"%s\",brightness:{sky:15,block:%d},"
                        + "background:0,view_range:%.1ff,teleport_duration:10,interpolation_duration:20,text:{text:\"%s\"},"
                        + "transformation:{left_rotation:%s,right_rotation:[0f,0f,0f,1f],translation:%s,scale:[%.1ff,%.1ff,%.1ff]}}",
                x, y, z, TAG, tag, billboard, brillo, alcance, glifo, rotacion, traslacion, escala, escala, escala));
    }

    static void glifoPantalla(MinecraftServer s, String tag, String glifo) {
        cmd(s, "data merge entity @e[tag=" + tag + ",limit=1] {text:{text:\"" + glifo + "\"}}");
    }

    static void escalaPantalla(MinecraftServer s, String tag, double escala, int ticks) {
        cmd(s, String.format(Locale.ROOT,
                "data merge entity @e[tag=%s,limit=1] {start_interpolation:0,interpolation_duration:%d,"
                        + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[%.1ff,%.1ff,%.1ff]}}",
                tag, ticks, escala, escala, escala));
    }

    static void flecha(MinecraftServer s, double x, double y, double z) {
        cmd(s, String.format(Locale.ROOT, "summon minecraft:arrow %.2f %.2f %.2f {Motion:[0.0d,-2.0d,0.0d],pickup:0b,Tags:[\"%s\"]}",
                x, y, z, TAG));
    }

    static void actionbar(ServerPlayer j, Component c) {
        j.connection.send(new ClientboundSetActionBarTextPacket(c));
    }

    static void avisar(List<ServerPlayer> js, Component c) {
        for (ServerPlayer j : js) actionbar(j, c);
    }

    static void titulo(ServerPlayer j, Component t, Component sub, int in, int stay, int out) {
        j.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        j.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        j.connection.send(new ClientboundSetTitleTextPacket(t));
    }
}
