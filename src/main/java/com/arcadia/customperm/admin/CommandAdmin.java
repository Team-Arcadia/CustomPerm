/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.admin;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.CommandTreeRewriter;
import com.arcadia.customperm.config.CommandsConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Command exposure: which root commands CustomPerm gates behind {@code customperm.command.<name>}.
 * Shared by {@code /customperm command} and the admin interface, server thread only.
 */
public final class CommandAdmin {

    private CommandAdmin() {
    }

    public static boolean existsInDispatcher(MinecraftServer server, String name) {
        return server != null && server.getCommands().getDispatcher().getRoot().getChild(name) != null;
    }

    public static AdminResult expose(MinecraftServer server, String name) {
        if (name.equals("customperm")) {
            return AdminResult.fail("Cannot expose /customperm itself.");
        }
        if (!existsInDispatcher(server, name)) {
            return AdminResult.fail("Command /" + name
                    + " does not exist on this server. Check the spelling and that the providing mod is loaded.");
        }
        if (!CustomPerm.configManager.getCommands().grantedCommands.add(name)) {
            return AdminResult.fail("Command /" + name + " is already exposed.");
        }
        String warning = ConfigAdmin.persist();
        CommandTreeRewriter.repair(server);
        // Put the CustomPerm check (root and subtree) back over a LuckPerms injection: LuckPerms
        // injected at boot, and without this the node would be ignored.
        int reasserted = CommandTreeRewriter.reassertExposedCommands(server);
        CustomPerm.LOGGER.info("[CustomPerm] Re-asserted exposure over other permission handlers on {} node(s) for /{}.", reasserted, name);
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok("Exposed /" + name + " to the permission system. Grant `customperm.command." + name
                + "` to authorize.").warn(warning);
    }

    public static AdminResult hide(MinecraftServer server, String name) {
        if (!CustomPerm.configManager.getCommands().removeCommand(name)) {
            return AdminResult.fail("Command /" + name + " is not currently exposed.");
        }
        String warning = ConfigAdmin.persist();
        CommandTreeRewriter.repair(server);
        // Restores the original (LuckPerms or vanilla) requirement on the command.
        CommandTreeRewriter.reassertExposedCommands(server);
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok("/" + name + " is no longer exposed. Reverts to its original (vanilla) authorisation.")
                .warn(warning);
    }

    /**
     * Turns {@code gateAllCommands} on or off: whether every root command reads its node, not only
     * exposed ones. Read at check time: only the command trees need resending.
     */
    public static AdminResult setGateAll(MinecraftServer server, boolean enabled) {
        if (CustomPerm.isLuckPermsPresent()) {
            return AdminResult.fail("No effect with LuckPerms installed: LuckPerms already checks every command. "
                    + "Deny nodes with /lp instead.");
        }
        var settings = CustomPerm.configManager.getSettings();
        if (settings.gateAllCommands == enabled) {
            return AdminResult.ok((enabled ? "Every command already follows" : "Only exposed commands already follow")
                    + " their customperm.command.<name> node. No change.");
        }
        settings.gateAllCommands = enabled;
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        if (!enabled) {
            return AdminResult.ok("Only exposed commands follow their customperm.command.<name> node again.").warn(warning);
        }
        return AdminResult.ok("Every command now follows its customperm.command.<name> node.").warn(warning)
                .note("An explicit DENY, including a denied * or customperm.command.*, now blocks any command, operators included.")
                .note("An ALLOW now opens any command: a grade holding * or customperm.command.* can run every command of the server.")
                .note("/customperm is never affected: it needs op level 2, and customperm.admin not denied.");
    }

    /**
     * Whether an exposed command also keeps its original Brigadier requirement, so the CustomPerm node
     * adds to the command's own check instead of replacing it. Read at check time: only the command
     * trees need resending.
     */
    public static AdminResult setPreserveOriginal(MinecraftServer server, String name, boolean preserve) {
        CommandsConfig commands = CustomPerm.configManager.getCommands();
        if (!commands.grantedCommands.contains(name)) {
            return AdminResult.fail("Command /" + name + " is not currently exposed.");
        }
        if (commands.shouldPreserveOriginalRequires(name) == preserve) {
            return AdminResult.ok("/" + name + (preserve ? " already keeps" : " already does not keep")
                    + " its original requirement. No change.");
        }
        commands.preserveOriginalRequires.put(name, preserve);
        String warning = ConfigAdmin.persist();
        ConfigAdmin.resyncCommands(server);
        return AdminResult.ok(preserve
                ? "/" + name + " now requires both customperm.command." + name + " and its original requirement."
                : "/" + name + " is now authorised by customperm.command." + name + " alone.").warn(warning);
    }
}
