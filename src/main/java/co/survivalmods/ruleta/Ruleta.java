package co.survivalmods.ruleta;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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

    private enum Fase { INACTIVA, ENTRANDO, GIRANDO, MOSTRANDO_COLOR, SALIENDO, RETO }

    private static final Component PREFIJO = Component.literal("[Ruleta] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
    private static final int TICKS_MOSTRAR_COLOR = 50;

    private final Random rnd = new Random();

    private Fase fase = Fase.INACTIVA;
    private static final int TICKS_GIRO = 120;     // duración del giro (6 s)
    private int ticksEspera;
    private int tGiro;
    private double anguloInicio, anguloTotal, anguloActual;
    private int ultimoClavo;
    private boolean redobleSonado;
    private int fotograma; // fotograma actual de la rueda (0-47)
    private int ticksColor;
    private int pasoAnim;

    private Sector sector;
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

    /** Empieza a girar. Si forzado != null, la rueda caerá en un color de esa dificultad y saldrá ese reto. */
    public boolean girar(MinecraftServer server, Retos.Plantilla forzado) {
        if (fase != Fase.INACTIVA) return false;
        RuletaConfig cfg = RuletaMod.config;
        if (Retos.activos(cfg, null).isEmpty()) return false;

        // 1) Elegir el color (sector) de la rueda
        if (forzado != null) {
            List<Sector> opciones = new ArrayList<>();
            for (Sector s : Sector.values()) if (s.dificultad == forzado.dificultad()) opciones.add(s);
            if (opciones.isEmpty()) opciones.add(Sector.values()[rnd.nextInt(Sector.values().length)]);
            sector = opciones.get(rnd.nextInt(opciones.size()));
        } else {
            sector = Sector.values()[rnd.nextInt(Sector.values().length)];
        }

        // 2) Elegir el reto de la lista de esa dificultad
        Retos.Plantilla plantilla = forzado;
        if (plantilla == null) {
            List<Retos.Plantilla> lista = Retos.activos(cfg, sector.dificultad);
            if (lista.isEmpty()) lista = Retos.activos(cfg, null);
            plantilla = lista.get(rnd.nextInt(lista.size()));
        }
        reto = plantilla.crear(rnd, cfg);

        // 3) Preparar el giro: 4 vueltas + lo que falte para que el sector quede bajo la lengüeta
        fotograma = 0; // la animación de entrada termina en el fotograma 0
        anguloInicio = 0;
        double destino = sector.fotogramaFinal() * Sector.GRADOS_POR_FOTOGRAMA;
        anguloTotal = 360 * 4 + Math.floorMod((int) Math.round(destino - anguloInicio), 360);
        anguloActual = anguloInicio;
        ultimoClavo = clavo(anguloInicio);
        tGiro = 0;
        redobleSonado = false;
        ticksEspera = 0;
        pasoAnim = 0;
        fase = RuletaMod.config.ruedaVisual ? Fase.ENTRANDO : Fase.GIRANDO;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviarTiempos(p, 0, 30, 0);
            if (!RuletaMod.config.ruedaVisual) sonido(p, SoundEvents.NOTE_BLOCK_BELL, 0.8F, 0.6F);
        }
        sonidoRuleta(server, "aparece", 1.0F, 1.0F);
        broadcast(server, Component.literal("¡La ruleta está girando!").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public void cancelar(MinecraftServer server) {
        if (fase == Fase.INACTIVA) return;
        if (fase == Fase.RETO) ejecutarHook(server, reto.alTerminar, 0);
        limpiar();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
            enviarTiempos(p, 0, 1, 5);
            enviarTitulo(p, Component.empty(), Component.empty());
        }
        broadcast(server, Component.literal("La ruleta fue cancelada.").withStyle(ChatFormatting.GRAY));
    }

    public void reiniciarAuto() {
        ticksAuto = 0;
    }

    private void limpiar() {
        fase = Fase.INACTIVA;
        reto = null;
        sector = null;
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
            case ENTRANDO -> tickEntrada(server);
            case GIRANDO -> tickGiro(server);
            case MOSTRANDO_COLOR -> tickColor(server);
            case SALIENDO -> tickSalida(server);
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

    /** La rueda aparece: destello blanco que crece y luego se llenan los colores (un fotograma cada 2 ticks). */
    private void tickEntrada(MinecraftServer server) {
        if (ticksEspera > 0) {
            ticksEspera--;
            return;
        }
        if (pasoAnim < Sector.FOTOGRAMAS_ENTRADA) {
            Component titulo = Component.literal(Sector.glifoEntrada(pasoAnim)).withStyle(ChatFormatting.WHITE);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                enviarTitulo(p, titulo, Component.empty());
            }
            pasoAnim++;
            ticksEspera = 1;
        } else {
            fase = Fase.GIRANDO;
            ticksEspera = 2; // pequeña pausa antes de girar
            sonidoRuleta(server, "inicio", 1.0F, 1.0F);
        }
    }

    /** La rueda (llena del color ganador) se encoge, destello blanco, desaparece, y luego aparece el reto. */
    private void tickSalida(MinecraftServer server) {
        if (ticksEspera > 0) {
            ticksEspera--;
            return;
        }
        if (pasoAnim < Sector.FOTOGRAMAS_SALIDA) {
            Component titulo = Component.literal(sector.glifoSalida(pasoAnim)).withStyle(ChatFormatting.WHITE);
            boolean ultimo = pasoAnim == Sector.FOTOGRAMAS_SALIDA - 1;
            if (pasoAnim == 0) sonidoRuleta(server, "desaparece", 1.0F, 1.0F);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (ultimo) enviarTiempos(p, 0, 2, 4); // el último fotograma se desvanece
                enviarTitulo(p, titulo, Component.empty());
            }
            pasoAnim++;
            ticksEspera = 1;
        } else {
            revelar(server);
        }
    }

    /** Clavo (separación entre colores) que acaba de pasar por la lengüeta. */
    private static int clavo(double angulo) {
        return (int) Math.floor((angulo + 22.5) / 45.0);
    }

    private void tickGiro(MinecraftServer server) {
        if (ticksEspera > 0) {
            ticksEspera--;
            return;
        }
        tGiro++;
        double x = Math.min(1.0, tGiro / (double) TICKS_GIRO);
        double frenado = 1 - Math.pow(1 - x, 2.5);            // rápido al inicio, frena suave al final
        double anterior = anguloActual;
        anguloActual = anguloInicio + anguloTotal * frenado;
        boolean rapido = anguloActual - anterior > 14;   // gira tan rápido que se ve borrosa
        int nuevo = Math.floorMod((int) Math.round(anguloActual / Sector.GRADOS_POR_FOTOGRAMA), Sector.FOTOGRAMAS);
        boolean cambio = nuevo != fotograma || tGiro == 1;
        fotograma = nuevo;

        if (cambio) {
            Component titulo = (rapido && RuletaMod.config.ruedaVisual)
                    ? Component.literal(Sector.glifoBorroso(fotograma)).withStyle(ChatFormatting.WHITE)
                    : tituloRueda(fotograma);
            Component sub = Component.literal("★ Girando la ruleta ★").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                enviarTitulo(p, titulo, sub);
            }
        }

        // "tic" cada vez que un clavo golpea la lengüeta
        int c = clavo(anguloActual);
        if (c != ultimoClavo) {
            ultimoClavo = c;
            float tono = 0.92F + rnd.nextFloat() * 0.16F;
            if (RuletaMod.config.ruedaVisual) {
                sonidoRuleta(server, "tick", 0.9F, tono);
            } else {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) sonido(p, SoundEvents.NOTE_BLOCK_HAT, 0.7F, 1.4F);
            }
        }
        if (!redobleSonado && x >= 0.72) {
            redobleSonado = true;
            sonidoRuleta(server, "redoble", 0.6F, 1.0F);
        }

        if (x >= 1.0) {
            fotograma = sector.fotogramaFinal();
            mostrarColor(server);
        }
    }

    /** La rueda se detuvo: se muestra el color y la dificultad. */
    private void mostrarColor(MinecraftServer server) {
        fase = Fase.MOSTRANDO_COLOR;
        ticksColor = 0;
        Component sub = subtituloSector();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviarTiempos(p, 0, 40, 5);
            enviarTitulo(p, tituloResaltado(0), sub);
            if (!RuletaMod.config.ruedaVisual) {
                sonido(p, SoundEvents.NOTE_BLOCK_BELL, 1.0F, 1.0F);
                sonido(p, SoundEvents.NOTE_BLOCK_PLING, 1.0F, 1.5F);
            }
        }
        sonidoRuleta(server, "ganador", 1.0F, 1.0F);
        broadcast(server, Component.literal("La rueda cayó en ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(sector.nombre).withStyle(sector.color, ChatFormatting.BOLD))
                .append(Component.literal(" — dificultad ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(sector.dificultad.nombre + " " + sector.dificultad.estrellasTexto())
                        .withStyle(sector.dificultad.color, ChatFormatting.BOLD)));
    }

    private void tickColor(MinecraftServer server) {
        ticksColor++;
        if (ticksColor % 4 == 0 && ticksColor < TICKS_MOSTRAR_COLOR) {
            int variante = (ticksColor / 4) % 2;   // los bombillos parpadean
            Component titulo = (ticksColor >= 16 && RuletaMod.config.ruedaVisual)
                    ? Component.literal(sector.glifoLleno(variante)).withStyle(ChatFormatting.WHITE)   // se llena del color
                    : tituloResaltado(variante);
            Component sub = subtituloSector();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                enviarTitulo(p, titulo, sub);
            }
        }
        if (ticksColor >= TICKS_MOSTRAR_COLOR) {
            if (RuletaMod.config.ruedaVisual) {
                fase = Fase.SALIENDO;
                pasoAnim = 0;
                ticksEspera = 0;
            } else {
                revelar(server);
            }
        }
    }

    private Component tituloRueda(int frame) {
        if (RuletaMod.config.ruedaVisual) {
            return Component.literal(Sector.glifoRueda(frame)).withStyle(ChatFormatting.WHITE);
        }
        // Modo texto: el color que está bajo el puntero
        Sector s = Sector.values()[Math.floorMod((int) -Math.round(anguloActual / 45.0), 8)];
        return Component.literal("» " + s.nombre + " «").withStyle(s.color, ChatFormatting.BOLD);
    }

    private Component tituloResaltado(int variante) {
        if (RuletaMod.config.ruedaVisual) {
            return Component.literal(sector.glifoResaltado(variante)).withStyle(ChatFormatting.WHITE);
        }
        return Component.literal("» " + sector.nombre + " «")
                .withStyle(variante == 0 ? sector.color : ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    private Component subtituloSector() {
        return Component.literal(sector.nombre).withStyle(sector.color, ChatFormatting.BOLD)
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(sector.dificultad.nombre + " " + sector.dificultad.estrellasTexto())
                        .withStyle(sector.dificultad.color, ChatFormatting.BOLD));
    }

    private void revelar(MinecraftServer server) {
        RuletaConfig cfg = RuletaMod.config;
        fase = Fase.RETO;
        ticksRestantes = reto.duracionSeg * 20;
        participantes.clear();
        cumplieron.clear();
        eliminados.clear();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            enviarTiempos(p, 8, 70, 15); // el texto del reto aparece suavemente
            enviarTitulo(p, reto.titulo, reto.descripcion);
            sonido(p, SoundEvents.NOTE_BLOCK_PLING, 1.0F, 2.0F);
            sonido(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.0F);
            if (participa(p, cfg)) {
                participantes.add(p.getUUID());
                reto.registrarInicio(p);
            }
        }

        MutableComponent linea = Component.literal("━━━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY);
        broadcastSinPrefijo(server, linea);
        broadcast(server, Component.literal("¡La ruleta ha hablado!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        broadcastSinPrefijo(server, Component.literal("  Color: ").withStyle(ChatFormatting.GRAY).append(subtituloSector().copy()));
        broadcastSinPrefijo(server, Component.literal("  Reto: ").withStyle(ChatFormatting.GRAY).append(reto.titulo.copy()));
        broadcastSinPrefijo(server, Component.literal("  ").append(reto.descripcion.copy()));
        broadcastSinPrefijo(server, Component.literal("  Tiempo: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(reto.duracionSeg + " segundos").withStyle(ChatFormatting.WHITE)));
        if (reto.sobrevivir) {
            broadcastSinPrefijo(server, Component.literal("  ☠ EVENTO: sigue vivo hasta que acabe el tiempo. Si mueres, quedas eliminado.").withStyle(ChatFormatting.RED));
        } else if (reto.evitar) {
            broadcastSinPrefijo(server, Component.literal("  Aguanta sin hacerlo hasta que acabe el tiempo.").withStyle(ChatFormatting.GRAY));
        } else {
            broadcastSinPrefijo(server, Component.literal("  Cúmplelo antes de que acabe el tiempo.").withStyle(ChatFormatting.GRAY));
        }
        broadcastSinPrefijo(server, linea.copy());

        if (participantes.isEmpty()) {
            broadcast(server, Component.literal("No hay jugadores participando (¿todos en creativo?).").withStyle(ChatFormatting.GRAY));
            limpiar();
            return;
        }
        ejecutarHook(server, reto.alIniciar, 0);
    }

    /** Jugadores del reto que siguen en juego (conectados, vivos y no eliminados). */
    private List<ServerPlayer> jugadoresEnJuego(MinecraftServer server) {
        List<ServerPlayer> lista = new ArrayList<>();
        for (UUID id : participantes) {
            if (eliminados.contains(id)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive()) lista.add(p);
        }
        return lista;
    }

    private void ejecutarHook(MinecraftServer server, RetoActivo.Hook hook, int tick) {
        if (hook == null || reto == null) return;
        try {
            hook.run(server, reto, jugadoresEnJuego(server), tick);
        } catch (Exception e) {
            RuletaMod.LOGGER.error("[Ruleta] Error en el evento " + reto.plantilla.id(), e);
        }
    }

    private void tickReto(MinecraftServer server) {
        RuletaConfig cfg = RuletaMod.config;
        ticksRestantes--;

        if (reto.sobrevivir) {
            // quien muere durante el evento queda eliminado
            for (UUID id : participantes) {
                if (eliminados.contains(id)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null && !p.isAlive()) {
                    eliminados.add(id);
                    broadcast(server, Component.literal("☠ ").withStyle(ChatFormatting.DARK_RED)
                            .append(Component.literal(p.getName().getString()).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" no sobrevivió al evento").withStyle(ChatFormatting.RED)));
                }
            }
            ejecutarHook(server, reto.alTick, reto.duracionSeg * 20 - ticksRestantes - 1);
        }

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
        ejecutarHook(server, reto.alTerminar, reto.duracionSeg * 20);
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

    private static void enviarTiempos(ServerPlayer p, int entrada, int duracion, int salida) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(entrada, duracion, salida));
    }

    private static void enviarTitulo(ServerPlayer p, Component titulo, Component subtitulo) {
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitulo));
        p.connection.send(new ClientboundSetTitleTextPacket(titulo));
    }

    /**
     * Sonido propio del paquete de texturas (ruleta:tick, ruleta:inicio, ruleta:redoble, ruleta:ganador).
     * Se usa /playsound para no depender de clases internas; cada jugador lo escucha en su posición.
     */
    private static void sonidoRuleta(MinecraftServer server, String nombre, float volumen, float tono) {
        if (!RuletaMod.config.ruedaVisual) return;
        String cmd = String.format(java.util.Locale.ROOT,
                "execute as @a at @s run playsound ruleta:%s master @s ~ ~ ~ %.2f %.2f", nombre, volumen, tono);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
    }

    /** Sonido que solo escucha ese jugador. */
    private void sonido(ServerPlayer p, SoundEvent evento, float volumen, float tono) {
        sonido(p, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(evento), volumen, tono);
    }

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
