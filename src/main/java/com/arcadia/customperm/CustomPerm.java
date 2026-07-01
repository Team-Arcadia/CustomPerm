package com.arcadia.customperm;

import com.arcadia.customperm.command.AliasManager;
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.command.ICommandTreeReloader;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.network.NetworkHandler;
import com.arcadia.customperm.perm.DenyPermissionService;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
                permissions = unavailableLuckPermsBackend(internalBackend,
                        "LuckPerms version " + lpVer + " is below minimum " + MIN_LUCKPERMS_VERSION);
            } else {
                try {
                    permissions = new LuckPermsService(internalBackend);
                    LOGGER.info("[CustomPerm] LuckPerms detected — using LuckPerms backend.");
                } catch (Throwable t) {
                    LOGGER.error("[CustomPerm] LuckPerms is loaded but its API failed to initialise.", t);
                    permissions = unavailableLuckPermsBackend(internalBackend, "LuckPerms API failed to initialise");
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
        NeoForge.EVENT_BUS.addListener(CustomPerm::onServerStopped);
        modBus.addListener(NetworkHandler::register);
    }

    private static PermissionService unavailableLuckPermsBackend(InternalPermService internalBackend, String reason) {
        if (configManager.getSettings().useInternalLuckPermsFallback()) {
            LOGGER.warn("[CustomPerm] {} — using internal backend (luckPermsFallbackMode=internal).", reason);
            return internalBackend;
        }
        LOGGER.error("[CustomPerm] {} — failing closed (luckPermsFallbackMode={}).",
                reason, configManager.getSettings().luckPermsFallbackMode);
        return new DenyPermissionService();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (permissions instanceof LuckPermsService lps) {
            lps.initServerHooks(event.getServer());
        }

        // Filet de sécurité : wrappe les racines enregistrées par des handlers
        // RegisterCommandsEvent exécutés après le nôtre — l'ordre inter-mods n'est pas
        // garanti, même en EventPriority.LOWEST.
        int lateWrapped = CommandTreeRewriter.repair(event.getServer());
        if (lateWrapped > 0) {
            LOGGER.info("[CustomPerm] Wrapped {} late-registered command(s) at server start.", lateWrapped);
        }

        // Boot-time health summary so admins can see in one line if everything is in order.
        String backend = backendLabel();
        int wrapped = event.getServer().getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = isDirectCommandExposureEnabled() ? configManager.getCommands().grantedCommands.size() : 0;
        int aliases = configManager.getAliases().aliases.size();
        int grades = configManager.getGrades().grades.size();
        LOGGER.info("[CustomPerm] Ready — backend={} dispatcherCommands={} exposed={} aliases={} grades={}",
            backend, wrapped, exposed, aliases, grades);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        // Le dispatcher de ce serveur disparaît avec lui : purger l'état statique évite de
        // retenir l'ancien arbre de commandes (fuite) et d'utiliser des nœuds périmés au
        // prochain démarrage dans la même JVM.
        CommandTreeRewriter.clearServerState();
        AliasManager.clearServerState();
        if (permissions instanceof LuckPermsService lps) {
            lps.closeServerHooks();
        }
    }

    public static String backendLabel() {
        if (permissions instanceof LuckPermsService lps) {
            if (lps.isDegraded()) {
                return configManager.getSettings().useInternalLuckPermsFallback()
                        ? "Internal — fallback from LuckPerms"
                        : "Deny — LuckPerms unavailable";
            }
            return "LuckPerms";
        }
        if (permissions instanceof DenyPermissionService) {
            return "Deny — LuckPerms unavailable";
        }
        return "Internal";
    }

    public static boolean isLuckPermsActive() {
        return permissions instanceof LuckPermsService lps && !lps.isDegraded();
    }

    public static boolean isLuckPermsPresent() {
        return ModList.get().isLoaded("luckperms");
    }

    public static boolean isTesseraUiPresent() {
        return ModList.get().isLoaded("tesseraui");
    }

    public static boolean isDirectCommandExposureEnabled() {
        // LuckPerms owns normal command permissions whenever the mod is installed.
        // CustomPerm keeps aliases active, including when the LP backend is degraded.
        return !isLuckPermsPresent();
    }
}
