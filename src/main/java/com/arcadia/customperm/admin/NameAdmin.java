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
import com.arcadia.customperm.chat.LegacyText;
import com.arcadia.customperm.chat.NameDecoration;
import com.arcadia.customperm.config.SettingsConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Whether player names carry their prefix and suffix, and how they are built. Shared by
 * {@code /customperm names} and the interface, server thread only. Unlike the prefixes themselves, these
 * settings apply with either backend: with LuckPerms they show the prefixes LuckPerms stores.
 */
public final class NameAdmin {

    private NameAdmin() {
    }

    private static SettingsConfig settings() {
        return CustomPerm.configManager.getSettings();
    }

    public static AdminResult setEnabled(MinecraftServer server, boolean enabled) {
        if (settings().decorateNames == enabled) {
            return AdminResult.ok("Names are already " + (enabled ? "decorated" : "left as they are") + ". No change.");
        }
        settings().decorateNames = enabled;
        String warning = ConfigAdmin.persist();
        NameDecoration.refreshAll(server);
        AdminResult result = AdminResult.ok(enabled
                ? "Names now carry their prefix and suffix, in chat and wherever the game shows them."
                : "Names are no longer decorated.").warn(warning);
        return enabled ? result.note("Chat messages stay signed: the name is decorated, never the message. "
                + "If another mod decorates names too, the two apply one inside the other.") : result;
    }

    /** Sets the format; it must keep {@code {name}}, or a prefix could pass for a player's name. */
    public static AdminResult setFormat(MinecraftServer server, String format) {
        String value = format == null ? "" : format.trim();
        if (!value.contains(SettingsConfig.NAME_PLACEHOLDER)) {
            return AdminResult.fail("The format must contain {name}: without it a prefix could pass for a name.");
        }
        String problem = LegacyText.problem(value, SettingsConfig.NAME_FORMAT_MAX);
        if (problem != null) return AdminResult.fail("Invalid format: " + problem);
        if (value.equals(settings().nameFormat)) return AdminResult.ok("The format is already " + value + ". No change.");
        settings().nameFormat = value;
        String warning = ConfigAdmin.persist();
        NameDecoration.refreshAll(server);
        return AdminResult.ok("Names are now built as " + value + ".").warn(warning);
    }

    /**
     * Shows the highest prefix only, or several in a row, for prefixes, suffixes or both ({@code which}
     * {@code "prefix"}, {@code "suffix"}, {@code "both"}). {@code limit} is how many a stack shows, null to
     * keep it. The spacers are set in {@code settings.json}.
     */
    public static AdminResult setStack(MinecraftServer server, String which, boolean stacked, Integer limit) {
        if (limit != null && (limit < 1 || limit > SettingsConfig.ChatStack.LIMIT_MAX)) {
            return AdminResult.fail("A stack shows 1 to " + SettingsConfig.ChatStack.LIMIT_MAX + " entries.");
        }
        java.util.List<SettingsConfig.ChatStack> stacks = switch (which) {
            case "prefix" -> java.util.List.of(settings().prefixStack);
            case "suffix" -> java.util.List.of(settings().suffixStack);
            default -> java.util.List.of(settings().prefixStack, settings().suffixStack);
        };
        String mode = stacked ? SettingsConfig.ChatStack.STACKED : SettingsConfig.ChatStack.HIGHEST;
        boolean changed = false;
        for (SettingsConfig.ChatStack stack : stacks) {
            changed |= !stack.mode.equals(mode) || (limit != null && stack.limit != limit);
            stack.mode = mode;
            if (limit != null) stack.limit = limit;
        }
        String what = which.equals("both") ? "Prefixes and suffixes" : which.equals("prefix") ? "Prefixes" : "Suffixes";
        if (!changed) return AdminResult.ok(what + " are already shown that way. No change.");
        String warning = ConfigAdmin.persist();
        NameDecoration.refreshAll(server);
        AdminResult result = AdminResult.ok(what + (stacked
                ? " now show up to " + stacks.get(0).limit + " in a row, highest priority first."
                : " now show the highest priority only.")).warn(warning);
        return CustomPerm.isLuckPermsActive()
                ? result.note("LuckPerms is active: its own meta formatting decides which prefix it gives, not this.")
                : result;
    }

    /** The settings, as a line for {@code /customperm names}. */
    public static String describe() {
        return "Name decoration: " + (settings().decorateNames ? "on" : "off") + ", format " + settings().nameFormat
                + ", prefixes " + stack(settings().prefixStack) + ", suffixes " + stack(settings().suffixStack)
                + ", from " + (CustomPerm.isLuckPermsActive() ? "LuckPerms" : "the grades") + ".";
    }

    private static String stack(SettingsConfig.ChatStack stack) {
        return stack.stacked() ? "stacked (up to " + stack.limit + ")" : "highest only";
    }
}
