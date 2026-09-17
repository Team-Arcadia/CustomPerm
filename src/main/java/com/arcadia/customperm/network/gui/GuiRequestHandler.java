/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.admin.AliasAdmin;
import com.arcadia.customperm.admin.CommandAdmin;
import com.arcadia.customperm.admin.ConfigAdmin;
import com.arcadia.customperm.admin.RateLimitAdmin;
import com.arcadia.customperm.command.RateLimiter;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server side of the admin interface: page requests and actions.
 *
 * <p><strong>Trust model.</strong> Every packet is re-checked here: op level 2 to be answered at
 * all (a non-operator gets no reply, not even a refusal), the area's write node for an action,
 * the argument count, and a per-player rate limit. The client's read-only rendering is a
 * convenience, never the gate.
 *
 * <p><strong>Refresh.</strong> After a successful action the server pushes the page the admin is on
 * with {@code open = false}, so the screen updates in place without a second request and without
 * reopening an interface the admin closed in the meantime.
 */
public final class GuiRequestHandler {

    /** Anti-spam budgets, kept in memory like the LuckPerms editor's. */
    private static final String PAGE_RATE_KEY = "gui:page";
    private static final int PAGE_MAX_PER_WINDOW = 40;
    private static final int PAGE_WINDOW_SECONDS = 10;

    private static final String ACTION_RATE_KEY = "gui:action";
    private static final int ACTION_MAX_PER_WINDOW = 30;
    private static final int ACTION_WINDOW_SECONDS = 10;

    static {
        RateLimiter.registerInternalBudget(PAGE_RATE_KEY, PAGE_WINDOW_SECONDS);
        RateLimiter.registerInternalBudget(ACTION_RATE_KEY, ACTION_WINDOW_SECONDS);
    }

    private GuiRequestHandler() {
    }

    /** Whether this player's client can display the interface, i.e. has CustomPerm installed. */
    public static boolean clientSupportsInterface(ServerPlayer player) {
        return player.connection.hasChannel(GuiPagePayload.TYPE);
    }

    /** Opens a page for a player who typed {@code /customperm gui}; the command already checked op level 2. */
    public static void open(ServerPlayer player, GuiPage page) {
        sendPage(player, page, true);
    }

    // ------------------------------------------------------------------ packets

    public static void handleRequest(GuiRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!GuiAccess.canRead(player)) return;
            GuiPage page = GuiPage.fromId(payload.page());
            if (page == null) return;
            if (!RateLimiter.tryAcquire(PAGE_RATE_KEY, player.getUUID(), PAGE_MAX_PER_WINDOW, PAGE_WINDOW_SECONDS).allowed()) {
                return;
            }
            sendPage(player, page, true);
        });
    }

    public static void handleAction(GuiActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!GuiAccess.canRead(player)) return;

            GuiAction action = GuiAction.fromName(payload.action());
            if (action == null) {
                send(player, GuiActionResultPayload.fail("Unknown action."));
                return;
            }
            if (payload.args().size() != action.arity()) {
                send(player, GuiActionResultPayload.fail("Malformed request for " + action.name() + "."));
                return;
            }
            if (action.area() != null && !GuiAccess.canEdit(player, action.area())) {
                send(player, GuiActionResultPayload.fail("You do not have " + action.area().node() + "."));
                return;
            }
            RateLimiter.Result budget = RateLimiter.tryAcquire(
                    ACTION_RATE_KEY, player.getUUID(), ACTION_MAX_PER_WINDOW, ACTION_WINDOW_SECONDS);
            if (!budget.allowed()) {
                send(player, GuiActionResultPayload.fail(
                        "Too many actions at once, retry in " + budget.retryAfterSeconds() + "s."));
                return;
            }

            AdminResult result = apply(player, action, payload.args());
            if (result.success()) {
                // Audit line: a click in the interface changes what players are allowed to do.
                CustomPerm.LOGGER.info("[CustomPerm] Admin interface: {} performed {} {} ({})",
                        player.getGameProfile().getName(), action.name(), payload.args(), result.message());
            }
            send(player, new GuiActionResultPayload(result.success() && result.warnings().isEmpty(), result.summary()));
            GuiPage page = GuiPage.fromId(payload.page());
            if (result.success() && page != null) sendPage(player, page, false);
        });
    }

    private static AdminResult apply(ServerPlayer player, GuiAction action, List<String> args) {
        return switch (action) {
            case RELOAD -> ConfigAdmin.reload(player.getServer());
            case COMMAND_EXPOSE -> CommandAdmin.expose(player.getServer(), args.get(0));
            case COMMAND_HIDE -> CommandAdmin.hide(player.getServer(), args.get(0));
            case COMMAND_KEEP_ORIGINAL -> bool(args.get(1)) == null
                    ? malformed(action)
                    : CommandAdmin.setPreserveOriginal(player.getServer(), args.get(0), bool(args.get(1)));
            case ALIAS_CREATE -> AliasAdmin.create(player.getServer(), args.get(0), args.get(1));
            case ALIAS_DELETE -> AliasAdmin.remove(player.getServer(), args.get(0));
            case ALIAS_STEP_ADD -> AliasAdmin.addStep(player.getServer(), args.get(0), args.get(1));
            case ALIAS_STEP_SET -> index(args.get(1)) == null ? malformed(action)
                    : AliasAdmin.setStep(player.getServer(), args.get(0), index(args.get(1)), args.get(2));
            case ALIAS_STEP_MOVE -> index(args.get(1)) == null || index(args.get(2)) == null ? malformed(action)
                    : AliasAdmin.moveStep(player.getServer(), args.get(0), index(args.get(1)), index(args.get(2)));
            case ALIAS_STEP_REMOVE -> index(args.get(1)) == null ? malformed(action)
                    : AliasAdmin.removeStep(player.getServer(), args.get(0), index(args.get(1)));
            case RATELIMIT_SET -> index(args.get(1)) == null || index(args.get(2)) == null ? malformed(action)
                    : RateLimitAdmin.set(args.get(0), index(args.get(1)), index(args.get(2)));
            case RATELIMIT_ENABLE -> RateLimitAdmin.enable(args.get(0));
            case RATELIMIT_DISABLE -> RateLimitAdmin.disable(args.get(0));
            case RATELIMIT_REMOVE -> RateLimitAdmin.remove(args.get(0));
            case RATELIMIT_PERSISTENCE -> RateLimitAdmin.setPersistence(args.get(0), args.get(1));
        };
    }

    private static AdminResult malformed(GuiAction action) {
        return AdminResult.fail("Malformed request for " + action.name() + ".");
    }

    /** Strict non-negative index argument: plain decimal digits only, anything else is malformed. */
    private static Integer index(String value) {
        if (value.isEmpty() || value.length() > 9 || !value.chars().allMatch(Character::isDigit)) return null;
        return Integer.parseInt(value);
    }

    /** Strict boolean argument: only "true" and "false", anything else is malformed. */
    private static Boolean bool(String value) {
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> null;
        };
    }

    // ------------------------------------------------------------------ sending

    private static void sendPage(ServerPlayer player, GuiPage page, boolean open) {
        if (player.hasDisconnected() || !clientSupportsInterface(player)) return;
        PacketDistributor.sendToPlayer(player,
                new GuiPagePayload(open, GuiSnapshots.context(player), GuiSnapshots.page(page, player)));
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.hasDisconnected() || !player.connection.hasChannel(payload.type())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
