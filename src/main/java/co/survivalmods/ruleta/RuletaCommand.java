package co.survivalmods.ruleta;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /ruleta girar [reto]   → gira la ruleta (opcionalmente forzando un reto)
 * /ruleta cancelar       → detiene el reto en curso
 * /ruleta auto <seg>|off → giro automático cada X segundos
 * /ruleta lista          → muestra los retos
 * /ruleta recargar       → vuelve a leer config/ruleta.json
 * /ruleta admin agregar|quitar <nombre>, /ruleta admin lista
 *
 * Solo pueden usarlo los nombres que estén en "admins" del config, o la consola del servidor.
 */
public final class RuletaCommand {

    private RuletaCommand() {}

    /** La consola (sin jugador) siempre puede. Un jugador solo si está en la lista de admins. */
    private static boolean puedeUsar(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return true;
        return RuletaMod.config.esAdmin(p.getName().getString());
    }

    public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ruleta")
                .requires(RuletaCommand::puedeUsar)
                .then(Commands.literal("girar")
                        .executes(ctx -> girar(ctx, null))
                        .then(Commands.argument("reto", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(RetoTipo.idsPorDefecto(), b))
                                .executes(ctx -> girar(ctx, StringArgumentType.getString(ctx, "reto")))))
                .then(Commands.literal("cancelar").executes(RuletaCommand::cancelar))
                .then(Commands.literal("lista").executes(RuletaCommand::lista))
                .then(Commands.literal("recargar").executes(RuletaCommand::recargar))
                .then(Commands.literal("auto")
                        .then(Commands.literal("off").executes(ctx -> auto(ctx, 0)))
                        .then(Commands.argument("segundos", IntegerArgumentType.integer(30, 86400))
                                .executes(ctx -> auto(ctx, IntegerArgumentType.getInteger(ctx, "segundos")))))
                .then(Commands.literal("admin")
                        .then(Commands.literal("lista").executes(RuletaCommand::adminLista))
                        .then(Commands.literal("agregar")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .executes(ctx -> adminCambiar(ctx, true))))
                        .then(Commands.literal("quitar")
                                .then(Commands.argument("nombre", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(RuletaMod.config.admins, b))
                                        .executes(ctx -> adminCambiar(ctx, false)))))
        );
    }

    private static int girar(CommandContext<CommandSourceStack> ctx, String retoId) {
        CommandSourceStack src = ctx.getSource();
        RetoTipo forzado = null;
        if (retoId != null) {
            forzado = RetoTipo.porId(retoId);
            if (forzado == null) {
                src.sendFailure(Component.literal("No existe el reto '" + retoId + "'. Usa /ruleta lista"));
                return 0;
            }
        }
        if (RuletaMod.ruleta.activa()) {
            src.sendFailure(Component.literal("Ya hay una ruleta en curso. Usa /ruleta cancelar"));
            return 0;
        }
        if (!RuletaMod.ruleta.girar(src.getServer(), forzado)) {
            src.sendFailure(Component.literal("No se pudo girar (¿no hay retos activos en el config?)"));
            return 0;
        }
        return 1;
    }

    private static int cancelar(CommandContext<CommandSourceStack> ctx) {
        if (!RuletaMod.ruleta.activa()) {
            ctx.getSource().sendFailure(Component.literal("No hay ninguna ruleta en curso."));
            return 0;
        }
        RuletaMod.ruleta.cancelar(ctx.getSource().getServer());
        return 1;
    }

    private static int lista(CommandContext<CommandSourceStack> ctx) {
        RuletaConfig cfg = RuletaMod.config;
        ctx.getSource().sendSuccess(() -> Component.literal("Retos disponibles:").withStyle(ChatFormatting.GOLD), false);
        for (RetoTipo t : RetoTipo.values()) {
            boolean activo = cfg.retosActivos.contains(t.id);
            Component linea = Component.literal(activo ? " ✔ " : " ✘ ").withStyle(activo ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .append(Component.literal(t.id).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" — " + t.nombreCorto + " (" + cfg.duracion(t) + "s)").withStyle(ChatFormatting.GRAY));
            ctx.getSource().sendSuccess(() -> linea, false);
        }
        return 1;
    }

    private static int recargar(CommandContext<CommandSourceStack> ctx) {
        RuletaMod.config = RuletaConfig.cargar();
        RuletaMod.ruleta.reiniciarAuto();
        actualizarComandos(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal("Config de la ruleta recargada.").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int auto(CommandContext<CommandSourceStack> ctx, int segundos) {
        RuletaMod.config.autoIntervaloSegundos = segundos;
        RuletaMod.config.guardar();
        RuletaMod.ruleta.reiniciarAuto();
        String msg = segundos <= 0 ? "Giro automático apagado." : "La ruleta girará sola cada " + segundos + " segundos.";
        ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int adminLista(CommandContext<CommandSourceStack> ctx) {
        String lista = RuletaMod.config.admins.isEmpty() ? "(vacía)" : String.join(", ", RuletaMod.config.admins);
        ctx.getSource().sendSuccess(() -> Component.literal("Admins de la ruleta: " + lista).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int adminCambiar(CommandContext<CommandSourceStack> ctx, boolean agregar) {
        String nombre = StringArgumentType.getString(ctx, "nombre");
        RuletaConfig cfg = RuletaMod.config;
        if (agregar) {
            if (cfg.esAdmin(nombre)) {
                ctx.getSource().sendFailure(Component.literal(nombre + " ya es admin de la ruleta."));
                return 0;
            }
            cfg.admins.add(nombre);
        } else {
            boolean quitado = cfg.admins.removeIf(a -> a.equalsIgnoreCase(nombre));
            if (!quitado) {
                ctx.getSource().sendFailure(Component.literal(nombre + " no estaba en la lista."));
                return 0;
            }
        }
        cfg.guardar();
        actualizarComandos(ctx.getSource().getServer());
        String msg = agregar ? nombre + " ahora puede usar /ruleta" : nombre + " ya no puede usar /ruleta";
        ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /** Reenvía el árbol de comandos para que /ruleta aparezca o desaparezca al instante. */
    private static void actualizarComandos(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(p);
        }
    }
}
