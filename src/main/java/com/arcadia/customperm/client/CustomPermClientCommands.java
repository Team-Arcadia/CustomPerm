/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.gui.TesseraGuiBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only trigger for the optional TesseraUI admin panel (H2.1): {@code /customperm gui}
 * opens a landing menu ({@code HubScreen}) with buttons to the Grades/Aliases/Status screens;
 * {@code /customperm gui grades|aliases|status} still opens a screen directly. This is a
 * genuinely separate, client-side-only command tree (see
 * {@link RegisterClientCommandsEvent}) — it never touches the server-authoritative
 * {@code /customperm} dispatcher registered in {@code CustomPermCommand}, and merging the same
 * root literal name into the client's local dispatcher is safe: Brigadier's node merge only
 * folds in children, it never overwrites an existing node's {@code requires} predicate.
 * <p>
 * The {@code .requires(hasPermission(2))} below is UX only (hides the entry from tab-complete
 * for non-admins); the real security boundary is the op-level-2 check in
 * {@code NetworkHandler.handleRequestSync}, since that's what actually gates the grades/aliases
 * data leaving the server.
 */
// RegisterClientCommandsEvent is a GAME-bus event (it doesn't implement IModBusEvent), which is
// also EventBusSubscriber's default — bus() itself is deprecated in this NeoForge version and
// must not be set explicitly.
@EventBusSubscriber(modid = CustomPerm.MODID, value = Dist.CLIENT)
public final class CustomPermClientCommands {

    private CustomPermClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("customperm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("gui")
                    .executes(ctx -> openScreen(ctx, "hub"))
                    .then(Commands.literal("grades").executes(ctx -> openScreen(ctx, "grades")))
                    .then(Commands.literal("aliases").executes(ctx -> openScreen(ctx, "aliases")))
                    .then(Commands.literal("status").executes(ctx -> openScreen(ctx, "status")))
                    // LuckPerms editor entry points. Registered unconditionally: whether
                    // LuckPerms is active is a server-side fact the client does not know at
                    // command-registration time, and the screens themselves report an inactive
                    // backend rather than pretending the subcommand does not exist.
                    .then(Commands.literal("luckperms").executes(ctx -> openScreen(ctx, "luckperms"))
                        .then(Commands.literal("groups").executes(ctx -> openScreen(ctx, "groups")))
                        .then(Commands.literal("players").executes(ctx -> openScreen(ctx, "players")))
                        .then(Commands.literal("tracks").executes(ctx -> openScreen(ctx, "tracks")))))
        );
    }

    private static int openScreen(CommandContext<CommandSourceStack> ctx, String screen) {
        if (!CustomPerm.isTesseraUiPresent()) {
            ctx.getSource().sendFailure(Component.literal(
                "[CustomPerm] TesseraUI is not installed - this GUI is unavailable. Use the text commands instead."));
            return 0;
        }
        // Plain static call — this class must not reference screen types directly, or verifying it
        // on a TesseraUI-less client would try (and fail) to load com.tesseraui.TesseraScreen.
        TesseraGuiBridge.open(screen);
        return 1;
    }
}
