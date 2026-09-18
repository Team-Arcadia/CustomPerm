/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.command;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.admin.NickAdmin;
import com.arcadia.customperm.perm.PermissionNodes;
import com.arcadia.customperm.perm.PermissionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /nick [<nickname> | clear]}: a player choosing their own nickname, with {@link PermissionNodes#NICK}.
 * Colour codes need {@link PermissionNodes#NICK_COLOR} on top. Anyone else's nickname is set by an admin with
 * {@code /customperm user nick}.
 *
 * <p>Its own root rather than a {@code /customperm} subcommand: that root needs {@code customperm.admin},
 * which a player choosing a nickname has no reason to hold. It is left out when another mod already
 * registered {@code /nick}: merging into that command would put this one behind the other's requirement.
 */
public final class NickCommand {

    public static final String ROOT = "nick";

    /** Whether the current command tree carries this /nick rather than another mod's. */
    private static volatile boolean registered;

    private NickCommand() {
    }

    public static boolean registered() {
        return registered;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registered = false;
        if (dispatcher.getRoot().getChild(ROOT) != null) {
            CustomPerm.LOGGER.warn("[CustomPerm] Another mod already registers /{}, so CustomPerm's is left out. "
                    + "Nicknames are still set with /customperm user nick.", ROOT);
            return;
        }
        dispatcher.register(Commands.literal(ROOT)
                .requires(source -> source.getPlayer() != null
                        && PermissionService.get().hasPermission(source, PermissionNodes.NICK))
                .executes(NickCommand::show)
                .then(Commands.literal("clear").executes(ctx -> set(ctx, "")))
                .then(Commands.argument("nickname", StringArgumentType.greedyString())
                        .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "nickname")))));
        registered = true;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        String nickname = NickAdmin.nickname(player.getUUID());
        ctx.getSource().sendSuccess(() -> Component.literal(nickname == null
                ? "You have no nickname. /nick <nickname> sets one, /nick clear takes it off."
                : "You are shown as " + nickname + ". /nick clear takes it off."), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String text) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        boolean codes = PermissionService.get().hasPermission(ctx.getSource(), PermissionNodes.NICK_COLOR);
        AdminResult result = NickAdmin.set(ctx.getSource().getServer(), player.getUUID(),
                player.getGameProfile().getName(), text, codes);
        result.warnings().forEach(warning -> ctx.getSource().sendFailure(Component.literal(warning)));
        if (!result.success()) {
            ctx.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
