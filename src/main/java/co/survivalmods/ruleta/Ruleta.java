package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Lógica de la ruleta: animación de giro → reto con cuenta regresiva → castigo.
 * Todo corre en el servidor; los jugadores no necesitan instalar nada.
 */
public final class Ruleta {

    private enum Fase { INACTIVA, GIRANDO, RETO }

    /** Pausas (en ticks) entre cada "cambio" de la ruleta: empieza rápido y frena. */
    private static final int[] PASOS_GIRO = {
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            3, 3, 3, 3, 3, 3,
            4, 4, 4, 5, 5, 6, 7, 8, 10, 12, 15
    };

    private static final Component PREFIJO = Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

    private final Random rnd = new Random();

    private Fase fase = Fase.INACTIVA;
    private int paso;
    private int ticksEspera;
    private String ultimoNombreMostrado = "";

    private RetoActivo reto;
    private int ticksRestantes;
    private final Set<UUID> participantes = new LinkedHashSet<>();
    private final Set<UUID> cumplieron = new LinkedHashSet<>();
    private final Set<UUID> eliminados = new LinkedHashSet<>();

    private int ticksAuto;

    public boolean activa() {
        return fase != Fase.INACTIVA;
    }

    // ------------------------------------------------------------------
    // Control

    /** Empieza a girar. Si forzado != null, la ruleta caerá en ese reto. */
    public boolean girar(MinecraftServer server, RetoTipo forzado) {
        if (fase != Fase.INACTIVA) return false;
        RuletaConfig cfg = RuletaMod.config;

        RetoTipo tipo = forzado;
        if (tipo == null) {
            List<RetoTipo> posibles = retosActivos(cfg);
            if (posibles.isEmpty()) return false;
            tipo = posibles.get(rnd.nextInt(posibles.size()));
        }
        reto = tipo.crear(rnd, cfg);

        fase = Fase.GIRANDO;
        paso = 0;
        ticksEspera = 0;
        ultimoNombreMostrado = "";

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviarTiempos(p, 0, 40, 5);
        }
        broadcast(server, Component.literal("¡La ruleta está girando!").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public void cancelar(MinecraftServer server) {
        if (fase == Fase.INACTIVA) return;
        limpiar();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
        }
        broadcast(server, Component.literal("La ruleta fue cancelada.").withStyle(ChatFormatting.GRAY));
    }

    public void reiniciarAuto() {
        ticksAuto = 0;
    }

    private void limpiar() {
        fase = Fase.INACTIVA;
        reto = null;
        participantes.clear();
        cumplieron.clear();
        eliminados.clear();
        ticksAuto = 0;
    }

    // ------------------------------------------------------------------
    // Tick (se llama 20 veces por segundo)

    public void tick(MinecraftServer server) {
        switch (fase) {
            case INACTIVA -> tickAuto(server);
            case GIRANDO -> tickGiro(server);
            case RETO -> tickReto(server);
        }
    }

    private void tickAuto(MinecraftServer server) {
        int intervalo = RuletaMod.config.autoIntervaloSegundos;
        if (intervalo <= 0) {
            ticksAuto = 0;
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        ticksAuto++;
        int faltan = intervalo * 20 - ticksAuto;
        if (faltan == 30 * 20 && intervalo > 60) {
            broadcast(server, Component.literal("La ruleta girará en 30 segundos...").withStyle(ChatFormatting.YELLOW));
        }
        if (faltan <= 0) {
            ticksAuto = 0;
            girar(server, null);
        }
    }

    private void tickGiro(MinecraftServer server) {
        if (ticksEspera > 0) {
            ticksEspera--;
            return;
        }
        if (paso < PASOS_GIRO.length) {
            // Mostrar un reto al azar (distinto al anterior) mientras gira
            List<RetoTipo> posibles = retosActivos(RuletaMod.config);
            if (posibles.isEmpty()) posibles = List.of(RetoTipo.values());
            String nombre;
            int intentos = 0;
            do {
                nombre = posibles.get(rnd.nextInt(posibles.size())).nombreCorto;
            } while (nombre.equals(ultimoNombreMostrado) && posibles.size() > 1 && ++intentos < 10);
            ultimoNombreMostrado = nombre;

            float tono = 0.8F + (paso / (float) PASOS_GIRO.length) * 1.0F;
            Component titulo = Component.literal("» " + nombre + " «").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            Component sub = Component.literal("★ Girando la ruleta ★").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                enviarTitulo(p, titulo, sub);
                sonido(p, SoundEvents.NOTE_BLOCK_HAT, 0.8F, tono);
            }
            ticksEspera = PASOS_GIRO[paso];
            paso++;
        } else {
            revelar(server);
        }
    }

    private void revelar(MinecraftServer server) {
        RuletaConfig cfg = RuletaMod.config;
        fase = Fase.RETO;
        ticksRestantes = reto.duracionSeg * 20;
        participantes.clear();
        cumplieron.clear();
        eliminados.clear();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviarTiempos(p, 5, 70, 15);
            enviarTitulo(p, reto.titulo, reto.descripcion);
            sonido(p, SoundEvents.NOTE_BLOCK_BELL, 1.0F, 1.0F);
            sonido(p, SoundEvents.NOTE_BLOCK_PLING, 1.0F, 2.0F);
            if (participa(p, cfg)) {
                participantes.add(p.getUUID());
                reto.registrarInicio(p);
            }
        }

        MutableComponent linea = Component.literal("━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY);
        broadcastSinPrefijo(server, linea);
        broadcast(server, Component.literal("¡La ruleta ha hablado!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        broadcastSinPrefijo(server, Component.literal("  Reto: ").withStyle(ChatFormatting.GRAY).append(reto.titulo.copy()));
        broadcastSinPrefijo(server, Component.literal("  ").append(reto.descripcion.copy()));
        broadcastSinPrefijo(server, Component.literal("  Tiempo: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(reto.duracionSeg + " segundos").withStyle(ChatFormatting.WHITE)));
        if (reto.evitar) {
            broadcastSinPrefijo(server, Component.literal("  Aguanta sin hacerlo hasta que acabe el tiempo.").withStyle(ChatFormatting.GRAY));
        } else {
            broadcastSinPrefijo(server, Component.literal("  Cúmplelo antes de que acabe el tiempo.").withStyle(ChatFormatting.GRAY));
        }
        broadcastSinPrefijo(server, linea.copy());

        if (participantes.isEmpty()) {
            broadcast(server, Component.literal("No hay jugadores participando (¿todos en creativo?).").withStyle(ChatFormatting.GRAY));
            limpiar();
        }
    }

    private void tickReto(MinecraftServer server) {
        RuletaConfig cfg = RuletaMod.config;
        ticksRestantes--;

        for (UUID id : new ArrayList<>(participantes)) {
            if (cumplieron.contains(id) || eliminados.contains(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null || !p.isAlive()) continue;

            boolean resultado;
            try {
                resultado = reto.evaluar(p);
            } catch (Exception e) {
                RuletaMod.LOGGER.error("[Ruleta] Error evaluando reto", e);
                resultado = false;
            }

            if (reto.evitar && resultado) {
                eliminar(server, p, cfg);
            } else if (!reto.evitar && resultado) {
                cumplieron.add(id);
                sonido(p, SoundEvents.NOTE_BLOCK_CHIME, 1.0F, 1.5F);
                sonido(p, SoundEvents.NOTE_BLOCK_PLING, 1.0F, 1.8F);
                p.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.literal("✔ ¡Reto cumplido! Estás a salvo").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
                broadcast(server, Component.literal("✔ ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" cumplió el reto").withStyle(ChatFormatting.GREEN)));
            }
        }

        // Cuenta regresiva en la barra de acción, una vez por segundo
        if (ticksRestantes % 20 == 0 && ticksRestantes > 0) {
            int seg = ticksRestantes / 20;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                UUID id = p.getUUID();
                MutableComponent barra;
                if (eliminados.contains(id)) {
                    barra = Component.literal("☠ Eliminado").withStyle(ChatFormatting.DARK_RED);
                } else if (cumplieron.contains(id)) {
                    barra = Component.literal("✔ A salvo — quedan " + seg + "s").withStyle(ChatFormatting.GREEN);
                } else {
                    ChatFormatting colorTiempo = seg <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW;
                    barra = Component.literal("⌛ " + seg + "s  ").withStyle(colorTiempo, ChatFormatting.BOLD)
                            .append(reto.descripcion.copy());
                }
                p.connection.send(new ClientboundSetActionBarTextPacket(barra));
                if (seg <= 5 && participantes.contains(id) && !cumplieron.contains(id) && !eliminados.contains(id)) {
                    sonido(p, SoundEvents.NOTE_BLOCK_HAT, 1.0F, 2.0F);
                }
            }
        }

        if (ticksRestantes <= 0) {
            terminar(server, cfg);
        }
    }

    private void terminar(MinecraftServer server, RuletaConfig cfg) {
        List<String> sobrevivientes = new ArrayList<>();
        for (UUID id : participantes) {
            if (eliminados.contains(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue; // se desconectó
            if (reto.evitar || cumplieron.contains(id)) {
                sobrevivientes.add(p.getName().getString());
            } else {
                eliminar(server, p, cfg);
            }
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
            enviarTiempos(p, 5, 40, 10);
            enviarTitulo(p, Component.literal("¡TIEMPO!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(sobrevivientes.size() + " sobreviviente(s)").withStyle(ChatFormatting.WHITE));
            sonido(p, SoundEvents.NOTE_BLOCK_BASS, 1.0F, 0.5F);
        }

        if (sobrevivientes.isEmpty()) {
            broadcast(server, Component.literal("Nadie sobrevivió a la ruleta.").withStyle(ChatFormatting.RED));
        } else {
            broadcast(server, Component.literal("Sobrevivieron: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.join(", ", sobrevivientes)).withStyle(ChatFormatting.WHITE)));
        }
        limpiar();
    }

    private void eliminar(MinecraftServer server, ServerPlayer p, RuletaConfig cfg) {
        eliminados.add(p.getUUID());
        String nombre = p.getName().getString();
        broadcast(server, Component.literal("☠ ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(nombre).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" no cumplió el reto").withStyle(ChatFormatting.RED)));
        sonido(p, SoundEvents.NOTE_BLOCK_DIDGERIDOO, 1.0F, 0.5F);

        if ("muerte".equalsIgnoreCase(cfg.castigo)) {
            // Se usa el comando /kill para que funcione igual que una muerte normal (suelta ítems, mensaje, etc.)
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "kill " + nombre);
        }
    }

    // ------------------------------------------------------------------
    // Utilidades

    private static boolean participa(ServerPlayer p, RuletaConfig cfg) {
        if (p.isSpectator()) return false;
        if (p.isCreative() && !cfg.afectarCreativo) return false;
        return p.isAlive();
    }

    private static List<RetoTipo> retosActivos(RuletaConfig cfg) {
        List<RetoTipo> lista = new ArrayList<>();
        for (String id : cfg.retosActivos) {
            RetoTipo t = RetoTipo.porId(id);
            if (t != null && !lista.contains(t)) lista.add(t);
        }
        return lista;
    }

    private static void enviarTiempos(ServerPlayer p, int entrada, int duracion, int salida) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(entrada, duracion, salida));
    }

    private static void enviarTitulo(ServerPlayer p, Component titulo, Component subtitulo) {
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitulo));
        p.connection.send(new ClientboundSetTitleTextPacket(titulo));
    }

    /** Sonido que solo escucha ese jugador. */
    private void sonido(ServerPlayer p, Holder<SoundEvent> evento, float volumen, float tono) {
        p.connection.send(new ClientboundSoundPacket(evento, SoundSource.MASTER,
                p.getX(), p.getY(), p.getZ(), volumen, tono, rnd.nextLong()));
    }

    static void broadcast(MinecraftServer server, Component mensaje) {
        server.getPlayerList().broadcastSystemMessage(PREFIJO.copy().append(mensaje), false);
    }

    static void broadcastSinPrefijo(MinecraftServer server, Component mensaje) {
        server.getPlayerList().broadcastSystemMessage(mensaje, false);
    }
}
