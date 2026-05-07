package com.example.customperm;

import com.example.customperm.command.CommandTreeRewriter;
import com.example.customperm.config.ConfigManager;
import com.example.customperm.perm.InternalPermService;
import com.example.customperm.perm.LuckPermsService;
import com.example.customperm.perm.PermissionService;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(CustomPerm.MODID)
public class CustomPerm {
    public static final String MODID = "customperm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ConfigManager configManager;
    public static PermissionService permissions;

    public CustomPerm(IEventBus modBus, ModContainer container) {
        // Config: load is internally try/catch'd, but defend against the constructor too.
        try {
            configManager = new ConfigManager();
            configManager.load();
        } catch (Throwable t) {
            LOGGER.error("[CustomPerm] Config init failed; falling back to in-memory empty config.", t);
            configManager = new ConfigManager();
        }

        // Backend selection: if LP is detected but its API blows up at instantiation
        // (incompatible LP version, classpath issue), fall back to internal rather than crash.
        if (ModList.get().isLoaded("luckperms")) {
            try {
                permissions = new LuckPermsService();
                LOGGER.info("[CustomPerm] LuckPerms detected — using LuckPerms backend.");
            } catch (Throwable t) {
                LOGGER.error("[CustomPerm] LuckPerms is loaded but its API failed to initialise — falling back to internal backend.", t);
                permissions = new InternalPermService(configManager);
            }
        } else {
            permissions = new InternalPermService(configManager);
            LOGGER.info("[CustomPerm] LuckPerms not present — using internal JSON grade backend.");
        }

        NeoForge.EVENT_BUS.register(CommandTreeRewriter.class);
        NeoForge.EVENT_BUS.addListener(CustomPerm::onServerStarted);

        // GameTest functions registry — only exercised under `gradlew runGameTestServer`
        // or when the /test command is used on a server with game tests enabled.
        com.example.customperm.test.CustomPermTestRegistry.init(modBus);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (permissions instanceof LuckPermsService lps) {
            lps.initServerHooks(event.getServer());
        }

        // Boot-time health summary so admins can see in one line if everything is in order.
        String backend = (permissions instanceof LuckPermsService) ? "LuckPerms" : "Internal";
        int wrapped = event.getServer().getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = configManager.getCommands().grantedCommands.size();
        int aliases = configManager.getAliases().aliases.size();
        int grades = configManager.getGrades().grades.size();
        LOGGER.info("[CustomPerm] Ready — backend={} dispatcherCommands={} exposed={} aliases={} grades={}",
            backend, wrapped, exposed, aliases, grades);
    }
}
