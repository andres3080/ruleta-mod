package co.survivalmods.ruleta;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static net.minecraft.ChatFormatting.*;

/**
 * Evento "LLUVIA DE METEORITOS".
 * - El cielo se pone rojo (timeline del mod: día 4, de 5000 a 9500 ticks).
 * - Caen meteoritos de ~16-20 bloques hechos con "block displays" (no son bloques reales: no rompen nada).
 * - Dejan estela de fuego y humo; en el suelo se marca dónde van a caer.
 * - Al impactar: gran daño a los jugadores cercanos y una onda expansiva que recorre la superficie.
 */
final class Meteoritos {

    private Meteoritos() {}

    private static final Random RND = new Random();
    static final String TAG = "ruleta_ev";
    static final long HORA_CIELO_ROJO = 24000L * 4 + 5000;     // ver data/ruleta/timeline/supernova.json

    static final int VUELO = 100;            // ticks que tarda en caer (5 s)
    static final double RADIO_DANO = 14;     // bloques
    static final double DANO_MAX = 24;       // corazones x2 (24 = 12 corazones) en el centro
    static final int[] LANZAMIENTOS = {40, 260, 480, 700, 900};

    /** Un meteorito en vuelo. */
    static final class Met {
        final int id, inicio;
        final double sx, sy, sz, tx, ty, tz;
        boolean impacto = false;
        Met(int id, int inicio, double sx, double sy, double sz, double tx, double ty, double tz) {
            this.id = id; this.inicio = inicio;
            this.sx = sx; this.sy = sy; this.sz = sz; this.tx = tx; this.ty = ty; this.tz = tz;
        }
        double[] pos(int t) {
            double f = Math.min(1, (t - inicio) / (double) VUELO);
            f = f * f * (1.6 - 0.6 * f);     // acelera al caer
            return new double[]{sx + (tx - sx) * f, sy + (ty - sy) * f, sz + (tz - sz) * f};
        }
        String tag() { return "met_" + id; }
    }

    static void registrar() {
        Retos.add("meteoritos", "LLUVIA DE METEORITOS", Dificultad.EVENTO, 60, (p, rnd, cfg, d) -> Eventos.eventoPublico(p, d,
                "¡LLUVIA DE METEORITOS!", RED,
                Retos.texto("Caen meteoritos: ").append(Retos.res("aléjate de las marcas rojas", RED)).append(Retos.texto(" del suelo")),
                (s, r, js, t) -> {
                    Eventos.supernovaActiva = true;          // evita que el mod salte las fechas del cielo rojo
                    Eventos.cmd(s, "weather clear");
                    Eventos.cmd(s, "time set " + HORA_CIELO_ROJO);
                    r.datos.put("mets", new ArrayList<Met>());
                    for (ServerPlayer j : js)
                        Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:entity.ender_dragon.growl master @s ~ ~ ~ 1 0.5");
                },
                (s, r, js, t) -> {
                    @SuppressWarnings("unchecked")
                    List<Met> mets = (List<Met>) r.datos.get("mets");
                    for (int l : LANZAMIENTOS) if (t == l && !js.isEmpty()) lanzar(s, mets, js, t);
                    for (Met m : mets) tickMeteorito(s, m, js, t);
                },
                (s, r, js, t) -> {
                    Eventos.limpiarMobs(s);
                    Eventos.cmd(s, "time set " + (24000L * 6 + 1000));
                    Eventos.supernovaActiva = false;
                }));
    }

    private static void lanzar(MinecraftServer s, List<Met> mets, List<ServerPlayer> js, int t) {
        ServerPlayer obj = js.get(RND.nextInt(js.size()));
        double a = RND.nextDouble() * Math.PI * 2, dist = 3 + RND.nextDouble() * 12;
        double tx = obj.getX() + Math.cos(a) * dist, tz = obj.getZ() + Math.sin(a) * dist, ty = obj.getY();
        double b = RND.nextDouble() * Math.PI * 2;
        double sx = tx + Math.cos(b) * 130, sz = tz + Math.sin(b) * 130, sy = ty + 170;
        Met m = new Met(mets.size(), t, sx, sy, sz, tx, ty, tz);
        mets.add(m);

        // el meteorito: 3 cubos girados que juntos parecen una roca ardiente
        cubo(s, m, "minecraft:magma_block", 17, 0.31, 0.52, 0.17, 0.9);
        cubo(s, m, "minecraft:blackstone", 15, -0.6, 0.2, 0.45, 0.6);
        cubo(s, m, "minecraft:netherrack", 13, 0.1, -0.7, 0.3, 1.3);
        cubo(s, m, "minecraft:shroomlight", 9, 0.5, 0.5, -0.5, 0.7);

        for (ServerPlayer j : js) {
            Eventos.cmd(s, "execute as " + Eventos.n(j) + " at @s run playsound minecraft:entity.blaze.shoot master @s ~ ~ ~ 1 0.5");
        }
        Eventos.avisarTodos(js, Component.literal("☄ ¡Meteorito entrando!").withStyle(RED, BOLD));
    }

    /** Un cubo de bloque gigante, girado sobre su propio centro. */
    private static void cubo(MinecraftServer s, Met m, String bloque, double lado, double ax, double ay, double az, double ang) {
        double n = Math.sqrt(ax * ax + ay * ay + az * az);
        double sn = Math.sin(ang / 2) / n;
        double qx = ax * sn, qy = ay * sn, qz = az * sn, qw = Math.cos(ang / 2);
        double[] c = rotar(qx, qy, qz, qw, lado / 2, lado / 2, lado / 2);   // dónde queda el centro tras girar
        Eventos.cmd(s, String.format(Locale.ROOT,
                "summon minecraft:block_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\"],block_state:{Name:\"%s\"},"
                        + "brightness:{sky:15,block:15},view_range:16f,teleport_duration:2,"
                        + "transformation:{left_rotation:[%.4ff,%.4ff,%.4ff,%.4ff],right_rotation:[0f,0f,0f,1f],"
                        + "translation:[%.3ff,%.3ff,%.3ff],scale:[%.1ff,%.1ff,%.1ff]}}",
                m.sx, m.sy, m.sz, TAG, m.tag(), bloque, qx, qy, qz, qw, -c[0], -c[1], -c[2], lado, lado, lado));
    }

    private static double[] rotar(double qx, double qy, double qz, double qw, double vx, double vy, double vz) {
        // v' = q v q*
        double ix = qw * vx + qy * vz - qz * vy;
        double iy = qw * vy + qz * vx - qx * vz;
        double iz = qw * vz + qx * vy - qy * vx;
        double iw = -qx * vx - qy * vy - qz * vz;
        return new double[]{
                ix * qw + iw * -qx + iy * -qz - iz * -qy,
                iy * qw + iw * -qy + iz * -qx - ix * -qz,
                iz * qw + iw * -qz + ix * -qy - iy * -qx};
    }

    private static void tickMeteorito(MinecraftServer s, Met m, List<ServerPlayer> js, int t) {
        int k = t - m.inicio;
        if (k < 0) return;
        if (k <= VUELO) {
            double[] p = m.pos(t);
            String pos = String.format(Locale.ROOT, "%.2f %.2f %.2f", p[0], p[1], p[2]);
            Eventos.cmd(s, "tp @e[tag=" + m.tag() + "] " + pos);
            // estela: fuego, lava y una columna de humo que se queda en el aire
            Eventos.cmd(s, "particle minecraft:flame " + pos + " 5 5 5 0.08 90 force");
            Eventos.cmd(s, "particle minecraft:lava " + pos + " 5 5 5 0 12 force");
            Eventos.cmd(s, "particle minecraft:large_smoke " + pos + " 6 6 6 0.03 50 force");
            Eventos.cmd(s, "particle minecraft:campfire_signal_smoke " + pos + " 5 5 5 0.01 10 force");
            // marca roja en el suelo donde va a caer
            if (k % 4 == 0) {
                for (int i = 0; i < 24; i++) {
                    double a = i * Math.PI * 2 / 24;
                    Eventos.cmd(s, String.format(Locale.ROOT,
                            "particle minecraft:dust{color:[1.0,0.1,0.0],scale:4.0} %.2f %.2f %.2f 0.1 0.1 0.1 0 2 force",
                            m.tx + Math.cos(a) * RADIO_DANO * 0.7, m.ty + 0.3, m.tz + Math.sin(a) * RADIO_DANO * 0.7));
                }
            }
            if (k % 20 == 0) {
                for (ServerPlayer j : js)
                    Eventos.cmd(s, String.format(Locale.ROOT, "playsound minecraft:entity.blaze.burn master %s %s 6 0.5", Eventos.n(j), pos));
            }
        }
        if (k == VUELO && !m.impacto) impactar(s, m, js);
        // onda expansiva por la superficie: crece y se desvanece
        if (m.impacto && k > VUELO && k <= VUELO + 40) {
            int w = k - VUELO;
            double radio = 4 + w * 2.6;
            int puntos = 40;
            for (int i = 0; i < puntos; i++) {
                double a = i * Math.PI * 2 / puntos;
                double x = m.tx + Math.cos(a) * radio, z = m.tz + Math.sin(a) * radio;
                String sup = String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f positioned over motion_blocking_no_leaves run ", x, 0.0, z);
                if (w % 2 == 0) Eventos.cmd(s, sup + "particle minecraft:explosion ~ ~0.5 ~ 0.5 0.3 0.5 0 1 force");
                Eventos.cmd(s, sup + "particle minecraft:campfire_cosy_smoke ~ ~0.3 ~ 0.6 0.2 0.6 0.02 2 force");
                if (w % 3 == 0) Eventos.cmd(s, sup + "particle minecraft:flame ~ ~0.3 ~ 0.4 0.1 0.4 0.05 3 force");
            }
            if (w > 10 && w % 3 == 0) {
                int op = (int) Math.round(255 * (1 - (w - 10) / 30.0));
                byte b = (byte) Math.max(26, op);
                Eventos.cmd(s, "data merge entity @e[tag=" + m.tag() + "_ondaA,limit=1] {text_opacity:" + b + "b}");
                Eventos.cmd(s, "data merge entity @e[tag=" + m.tag() + "_ondaB,limit=1] {text_opacity:" + b + "b}");
            }
            if (w == 40) Eventos.cmd(s, "kill @e[tag=" + m.tag() + "_onda]");
        }
    }

    private static void impactar(MinecraftServer s, Met m, List<ServerPlayer> js) {
        m.impacto = true;
        Eventos.cmd(s, "kill @e[tag=" + m.tag() + "]");
        String c = String.format(Locale.ROOT, "%.2f %.2f %.2f", m.tx, m.ty + 1, m.tz);
        Eventos.cmd(s, "particle minecraft:explosion_emitter " + c + " 6 2 6 0 25 force");
        Eventos.cmd(s, "particle minecraft:lava " + c + " 8 3 8 0 120 force");
        Eventos.cmd(s, "particle minecraft:campfire_signal_smoke " + c + " 6 2 6 0.05 80 force");
        Eventos.cmd(s, "particle minecraft:flash " + c + " 0 0 0 0 1 force");

        // onda en el suelo: anillo naranja enorme (mira hacia arriba y hacia abajo)
        String[] rot = {"[-0.7071f,0f,0f,0.7071f]", "[0.7071f,0f,0f,0.7071f]"};
        String[] suf = {"_ondaA", "_ondaB"};
        for (int i = 0; i < 2; i++) {
            Eventos.cmd(s, String.format(Locale.ROOT,
                    "summon minecraft:text_display %.2f %.2f %.2f {Tags:[\"%s\",\"%s\",\"%s\"],billboard:\"fixed\",brightness:{sky:15,block:15},"
                            + "background:0,view_range:16f,text:{text:\"%s\",color:\"#FF7A1A\"},"
                            + "transformation:{left_rotation:%s,right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[8f,8f,8f]}}",
                    m.tx, m.ty + 1.2, m.tz, TAG, m.tag() + "_onda", m.tag() + suf[i], Eventos.ONDA_GLIFO, rot[i]));
            Eventos.cmd(s, String.format(Locale.ROOT,
                    "data merge entity @e[tag=%s,limit=1] {start_interpolation:1,interpolation_duration:40,"
                            + "transformation:{left_rotation:%s,right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[350f,350f,350f]}}",
                    m.tag() + suf[i], rot[i]));
        }

        for (ServerPlayer j : js) {
            double dx = j.getX() - m.tx, dy = j.getY() - m.ty, dz = j.getZ() - m.tz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            String n = Eventos.n(j);
            Eventos.cmd(s, String.format(Locale.ROOT, "playsound minecraft:entity.generic.explode master %s %s 12 0.4", n, c));
            Eventos.cmd(s, String.format(Locale.ROOT, "playsound minecraft:entity.lightning_bolt.thunder master %s %s 12 0.6", n, c));
            Eventos.cmd(s, String.format(Locale.ROOT, "playsound minecraft:entity.warden.sonic_boom master %s %s 8 0.6", n, c));
            if (d < 80) {
                Eventos.titulo(j, Eventos.pantallaColor(Eventos.CIELO_BLANCO_GLIFO, 0xFFB060), Component.empty(), 0, 4, 20);
                Eventos.cmd(s, "effect give " + n + " minecraft:nausea 4 0 true");
            }
            if (d < RADIO_DANO) {
                double dano = Math.max(4, DANO_MAX * (1 - d / RADIO_DANO));
                Eventos.cmd(s, String.format(Locale.ROOT, "damage %s %.1f minecraft:explosion", n, dano));
                Eventos.cmd(s, "effect give " + n + " minecraft:slowness 3 1 true");
                Eventos.actionbar(j, Component.literal("☄ ¡Te alcanzó el meteorito!").withStyle(RED, BOLD));
            }
        }
    }
}
