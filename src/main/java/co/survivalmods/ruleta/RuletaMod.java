package co.survivalmods.ruleta;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RuletaMod implements ModInitializer {
    public static final String MOD_ID = "ruleta";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static RuletaConfig config;
    public static final Ruleta ruleta = new Ruleta();

    @Override
    public void onInitialize() {
        config = RuletaConfig.cargar();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                RuletaCommand.registrar(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(ruleta::tick);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (ruleta.activa()) ruleta.cancelar(server);
        });

        if (config.admins.isEmpty()) {
            LOGGER.warn("[Ruleta] No hay admins configurados. Desde la consola usa: ruleta admin agregar <TuNombre>");
        }
        LOGGER.info("[Ruleta] Mod de la ruleta cargado ({} retos).", Retos.TODAS.size());
    }
}
