/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.DefaultPermissionHandler;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Answers the permission checks other mods make through NeoForge's {@link PermissionAPI}, so a node they
 * declare can be granted or denied in a grade like a CustomPerm node.
 *
 * <p><strong>Which handler answers is decided once, knowingly.</strong> NeoForge keeps exactly one active
 * handler, the one {@code permissionHandler} names in {@code neoforge-server.toml}; there is no chain to join.
 * LuckPerms, when that value is still NeoForge's default, rewrites it to its own. So CustomPerm always offers
 * {@link #ID}, and selects it itself only when LuckPerms is not installed, the value is still the default and
 * {@code answerOtherMods} allows: it never competes with LuckPerms and never overrides a value an admin chose.
 * The selection is made in memory, never saved, so turning the setting off takes effect at the next start.
 * The log says which handler is active and why.
 *
 * <p><strong>What it answers.</strong> A boolean node is resolved by the active backend like any node: an
 * explicit ALLOW is {@code true}, a DENY {@code false}, and nothing set is the node's own default, which is what
 * its mod decided (often an op level), never a refusal. A node of another type (a number, a text) has no
 * storage here and always answers its default.
 */
public final class ModPermissions {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(CustomPerm.MODID, "handler");

    /** Why the handler in force is the one it is, for the log line at start. */
    private static String reason = "not asked yet";

    private ModPermissions() {
    }

    public static void onGatherHandler(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ID, Handler::new);
        String configured = NeoForgeConfig.SERVER.permissionHandler.get();
        boolean isDefault = DefaultPermissionHandler.IDENTIFIER.toString().equals(configured);
        if (ModList.get().isLoaded("luckperms")) {
            reason = "LuckPerms is installed and answers them itself";
        } else if (!CustomPerm.configManager.getSettings().answerOtherMods) {
            reason = "answerOtherMods is off in settings.json";
        } else if (!isDefault) {
            reason = "permissionHandler in neoforge-server.toml names " + configured + ", which is left as chosen";
        } else {
            NeoForgeConfig.SERVER.permissionHandler.set(ID.toString());
            reason = "selected by CustomPerm, permissionHandler being NeoForge's default";
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ResourceLocation active = PermissionAPI.getActivePermissionHandler();
        int nodes = PermissionAPI.getRegisteredNodes().size();
        if (ID.equals(active)) {
            CustomPerm.LOGGER.info("[CustomPerm] Answering the permission checks of other mods ({} node(s) declared): {}.",
                    nodes, reason);
        } else {
            CustomPerm.LOGGER.info("[CustomPerm] Not answering the permission checks of other mods, {} does: {}.",
                    active, reason);
        }
    }

    /** Whether CustomPerm is the handler in force this session. */
    public static boolean answering() {
        return ID.equals(PermissionAPI.getActivePermissionHandler());
    }

    /** The boolean nodes other mods declared, by name, for completion and the import. */
    public static List<String> declaredNodes() {
        return PermissionAPI.getRegisteredNodes().stream()
                .filter(node -> node.getType() == PermissionTypes.BOOLEAN)
                .map(PermissionNode::getNodeName)
                .sorted()
                .toList();
    }

    private static final class Handler implements IPermissionHandler {
        private final Set<PermissionNode<?>> nodes;

        Handler(Collection<PermissionNode<?>> nodes) {
            this.nodes = Set.copyOf(nodes);
        }

        @Override
        public ResourceLocation getIdentifier() {
            return ID;
        }

        @Override
        public Set<PermissionNode<?>> getRegisteredNodes() {
            return nodes;
        }

        @Override
        public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            if (node.getType() == PermissionTypes.BOOLEAN) {
                Tristate verdict = AdminAccess.explicit(player.createCommandSourceStack(), node.getNodeName());
                if (verdict != Tristate.UNSET) return cast(verdict == Tristate.ALLOW);
            }
            return node.getDefaultResolver().resolve(player, player.getUUID(), context);
        }

        /**
         * An offline player is resolved from the grades when they decide; under any other backend, the
         * default, since there is no player to hand LuckPerms or the fail-closed backend.
         */
        @Override
        public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            if (node.getType() == PermissionTypes.BOOLEAN && CustomPerm.permissions instanceof InternalPermService) {
                Tristate verdict = PermissionResolver.check(CustomPerm.configManager.getGrades(), player,
                        node.getNodeName(), CustomPerm.configManager.getSettings().defaultGrade);
                if (verdict != Tristate.UNSET) return cast(verdict == Tristate.ALLOW);
            }
            return node.getDefaultResolver().resolve(null, player, context);
        }

        @SuppressWarnings("unchecked")
        private static <T> T cast(boolean value) {
            return (T) Boolean.valueOf(value);
        }
    }
}
