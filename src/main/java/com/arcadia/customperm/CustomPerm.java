package com.arcadia.customperm;

import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.command.ICommandTreeReloader;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.perm.InternalPermService;
import com.arcadia.customperm.perm.LuckPermsService;
import com.arcadia.customperm.perm.PermissionService;
import com.arcadia.customperm.util.VersionUtils;
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
    public static final int MIN_LUCKPERMS_MAJOR = 5;
    public static final int MIN_LUCKPERMS_MINOR = 4;
    public static final int MIN_LUCKPERMS_PATCH = 150;
    public static final String MIN_LUCKPERMS_VERSION =
            MIN_LUCKPERMS_MAJOR + "." + MIN_LUCKPERMS_MINOR + "." + MIN_LUCKPERMS_PATCH;

    public static ConfigManager configManager;
    public static PermissionService permissions;
    /** CommandTreeRewriter câblé comme ICommandTreeReloader — implémentation É2.6. */
    public static ICommandTreeReloader treeReloader = new CommandTreeRewriter();

    public CustomPerm(IEventBus modBus, ModContainer container) {
        // Config: load is internally try/catch'd, but defend against the constructor too.
        try {
            configManager = new ConfigManager();
            configManager.load();
        } catch (Throwable t) {
            LOGGER.error("[CustomPerm] Config init failed; falling back to in-memory empty config.", t);
            configManager = new ConfigManager();
            LOGGER.warn("[CustomPerm] Starting with EMPTY config — all permissions, grades and aliases are inactive until a successful reload.");
        }

        // P6 : instance partagée — évite de créer plusieurs InternalPermService sur le même configManager.
        // Utilisée soit comme backend principal (sans LP), soit comme fallback interne de LuckPermsService.
        InternalPermService internalBackend = new InternalPermService(configManager);

        // Backend selection: if LP is detected but its API blows up at instantiation
        // (incompatible LP version, classpath issue), fall back to internal rather than crash.
        if (ModList.get().isLoaded("luckperms")) {
            // Single traversal — version string used for both the gate and the warn log.
            String lpVer = ModList.get().getMods().stream()
                    .filter(m -> m.getModId().equals("luckperms"))
                    .findFirst().map(m -> m.getVersion().toString()).orElse("unknown");
            if (!VersionUtils.isVersionAtLeast(lpVer, MIN_LUCKPERMS_MAJOR, MIN_LUCKPERMS_MINOR, MIN_LUCKPERMS_PATCH)) {
                LOGGER.warn("[CustomPerm] LuckPerms version {} is below minimum {} — using internal backend.", lpVer, MIN_LUCKPERMS_VERSION);
                permissions = internalBackend;
            } else {
                try {
                    permissions = new LuckPermsService(internalBackend);
                    LOGGER.info("[CustomPerm] LuckPerms detected — using LuckPerms backend.");
                } catch (Throwable t) {
                    LOGGER.error("[CustomPerm] LuckPerms is loaded but its API failed to initialise — falling back to internal backend.", t);
                    permissions = internalBackend;
                }
            }
        } else {
            permissions = internalBackend;
            LOGGER.info("[CustomPerm] LuckPerms not present — using internal JSON grade backend.");
        }
        if (permissions == null) {
            LOGGER.error("[CustomPerm] Backend selection ended with null permissions — forcing internal backend.");
            permissions = internalBackend;
        }

        NeoForge.EVENT_BUS.register(CommandTreeRewriter.class);
        NeoForge.EVENT_BUS.addListener(CustomPerm::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (permissions instanceof LuckPermsService lps) {
            lps.initServerHooks(event.getServer());
        }

        // Boot-time health summary so admins can see in one line if everything is in order.
        String backend = backendLabel();
        int wrapped = event.getServer().getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = configManager.getCommands().grantedCommands.size();
        int aliases = configManager.getAliases().aliases.size();
        int grades = configManager.getGrades().grades.size();
        LOGGER.info("[CustomPerm] Ready — backend={} dispatcherCommands={} exposed={} aliases={} grades={}",
            backend, wrapped, exposed, aliases, grades);
    }

    public static String backendLabel() {
        if (permissions instanceof LuckPermsService lps) {
            return lps.isDegraded() ? "Internal — fallback from LuckPerms" : "LuckPerms";
        }
        return "Internal";
    }

    public static boolean isLuckPermsActive() {
        return permissions instanceof LuckPermsService lps && !lps.isDegraded();
    }
}
