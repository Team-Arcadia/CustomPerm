/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm;

import com.arcadia.customperm.command.AliasManager;
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.command.ICommandTreeReloader;
import com.arcadia.customperm.command.RateLimitPersistence;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.network.NetworkHandler;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.BackendKind;
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
            // The empty fallback never came from disk: saving it would erase the real files.
            configManager.suspendSaves("config initialisation failed (" + t.getClass().getSimpleName() + ")");
            LOGGER.warn("[CustomPerm] Starting with EMPTY config — all permissions, grades and aliases are inactive until a successful reload.");
        }
        syncConfigAlert();

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
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.admin.ExpirySweeper::onServerTick);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.admin.WorldChangeResync::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.admin.WorldChangeResync::onChangedGameMode);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.perm.ModPermissions::onGatherHandler);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.perm.ModPermissions::onServerStarted);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.chat.NameDecoration::onNameFormat);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.chat.NameDecoration::onTabListNameFormat);
        NeoForge.EVENT_BUS.addListener(AdminNotifier::onServerStarted);
        NeoForge.EVENT_BUS.addListener(AdminNotifier::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AdminNotifier::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(RateLimitPersistence::onServerStarted);
        NeoForge.EVENT_BUS.addListener(RateLimitPersistence::onLevelSave);
        NeoForge.EVENT_BUS.addListener(RateLimitPersistence::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.cluster.Cluster::onServerStarted);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,
                com.arcadia.customperm.cluster.Cluster::onServerStopping);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.cluster.Cluster::onServerStopped);
        NeoForge.EVENT_BUS.addListener(com.arcadia.customperm.cluster.Cluster::onServerTick);
        NeoForge.EVENT_BUS.addListener(ActivityLog::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ActivityLog::onServerStopped);
        // Lowest: a command another mod cancels is not recorded as used.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, ActivityLog::onCommand);
        modBus.addListener(NetworkHandler::register);
    }

    private static PermissionService unavailableLuckPermsBackend(InternalPermService internalBackend, String reason) {
        raiseLuckPermsUnavailable(reason);
        if (configManager.getSettings().useInternalLuckPermsFallback()) {
            LOGGER.warn("[CustomPerm] {} — using internal backend (luckPermsFallbackMode=internal).", reason);
            return internalBackend;
        }
        LOGGER.error("[CustomPerm] {} — failing closed (luckPermsFallbackMode={}).",
                reason, configManager.getSettings().luckPermsFallbackMode);
        return new DenyPermissionService();
    }

    /**
     * Tells online and joining admins that LuckPerms is out of the loop until restart, and what
     * CustomPerm does instead. Safe to call repeatedly and from any thread: an unchanged alert is
     * not re-sent.
     */
    public static void raiseLuckPermsUnavailable(String reason) {
        String effect = configManager.getSettings().useInternalLuckPermsFallback()
                ? "CustomPerm now resolves its permissions from the internal grades (grades.json)"
                : "CustomPerm now denies every permission it manages (luckPermsFallbackMode=deny)";
        AdminNotifier.raise(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE,
                "LuckPerms is unavailable (" + reason + "). " + effect + " until the server restarts. See the server log.");
    }

    /** Raises or resolves the config alert to match the outcome of the last load. */
    public static void syncConfigAlert() {
        if (configManager.isDiskWritable()) {
            AdminNotifier.clear(AdminAlerts.Key.CONFIG_LOAD_FAILED, "configuration loaded, saving is enabled again.");
        } else {
            AdminNotifier.raise(AdminAlerts.Key.CONFIG_LOAD_FAILED,
                    "Configuration failed to load (" + configManager.getLastLoadFailure() + "). Changes are kept in "
                            + "memory but NOT saved until the file is fixed and /customperm reload succeeds.");
        }
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

        com.arcadia.customperm.config.UpgradeNotice.onServerStarted();
        logAdminAccessModel(event.getServer());

        // Boot-time health summary so admins can see in one line if everything is in order.
        String backend = backendLabel();
        int wrapped = event.getServer().getCommands().getDispatcher().getRoot().getChildren().size();
        int exposed = isDirectCommandExposureEnabled() ? configManager.getCommands().grantedCommands.size() : 0;
        int aliases = configManager.getAliases().aliases.size();
        int grades = configManager.getGrades().grades.size();
        LOGGER.info("[CustomPerm] Ready — backend={} dispatcherCommands={} exposed={} aliases={} grades={}",
            backend, wrapped, exposed, aliases, grades);
    }

    /**
     * Operators need explicitly granted nodes to administer CustomPerm. Said at every start, because after an
     * upgrade from 1.0.x nobody holds them and /customperm vanishes from the game until they are granted.
     */
    private static void logAdminAccessModel(net.minecraft.server.MinecraftServer server) {
        LOGGER.info("[CustomPerm] In-game administration needs op level 2 AND explicitly granted nodes: customperm.admin to "
                + "use /customperm and the admin interface, customperm.manage.<area> to change an area (customperm.* for all). "
                + "Being operator alone, level 4 included, gives no access. The console always has access.");
        if (!server.isDedicatedServer()) {
            LOGGER.info("[CustomPerm] This world has no console: its host administers CustomPerm without a node, and grants "
                    + "the nodes to other players.");
            return;
        }
        if (isLuckPermsActive()) {
            LOGGER.info("[CustomPerm] With LuckPerms, grant them from the console, e.g.: lp user <name> permission set customperm.* true");
        } else if (!com.arcadia.customperm.perm.AdminAccess.anyInternalAdmin()) {
            LOGGER.warn("[CustomPerm] No player holds customperm.admin: nobody can administer CustomPerm in game. From the "
                    + "console: customperm grade create admins, customperm grade addperm admins customperm.*, "
                    + "customperm grade assign <name> admins");
        }
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
        return backendKind().label();
    }

    public static BackendKind backendKind() {
        if (permissions instanceof LuckPermsService lps) {
            if (lps.isDegraded()) {
                return configManager.getSettings().useInternalLuckPermsFallback()
                        ? BackendKind.INTERNAL_FALLBACK
                        : BackendKind.DENY;
            }
            return BackendKind.LUCKPERMS;
        }
        if (permissions instanceof DenyPermissionService) {
            return BackendKind.DENY;
        }
        return BackendKind.INTERNAL;
    }

    public static boolean isLuckPermsActive() {
        return permissions instanceof LuckPermsService lps && !lps.isDegraded();
    }

    /** The mod list is fixed once loading is done; cached because command predicates ask on every node. */
    private static volatile Boolean luckPermsPresent;

    public static boolean isLuckPermsPresent() {
        Boolean present = luckPermsPresent;
        if (present == null) {
            present = ModList.get().isLoaded("luckperms");
            luckPermsPresent = present;
        }
        return present;
    }

    /**
     * Whether every root command reads its {@code customperm.command.<name>} node, not only exposed ones.
     * Never with LuckPerms installed: LuckPerms replaces every command requirement with its own check,
     * which already covers every command.
     */
    public static boolean gatesAllCommands() {
        return configManager.getSettings().gateAllCommands && !isLuckPermsPresent();
    }

    public static boolean isDirectCommandExposureEnabled() {
        // Direct command exposure is always active. The permission node
        // customperm.command.<name> is resolved by whatever PermissionService is in effect:
        // LuckPermsService when LuckPerms is installed (so `customperm.command.*` granted via
        // /lp works), InternalPermService otherwise (grades.json). Command wrapping and the
        // node check are therefore backend-agnostic — LuckPerms no longer bypasses them.
        return true;
    }
}
