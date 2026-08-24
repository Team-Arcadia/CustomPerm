/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.command.RateLimiter;
import com.arcadia.customperm.perm.PermissionNodes;
import com.arcadia.customperm.perm.lp.LuckPermsAdminService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * Server-side entry point for the LuckPerms editor packets: the security boundary, the
 * validation, and the hop between Minecraft's server thread and LuckPerms' async storage.
 *
 * <p><strong>Class loading.</strong> This class is reachable on a server without LuckPerms (the
 * payloads are registered unconditionally, and a modified client could send one), so every
 * reference to {@code LuckPermsAdminService} sits in a method that is only ever called from
 * inside a {@link CustomPerm#isLuckPermsActive()} branch — and never as a method reference,
 * which would resolve eagerly. Same discipline as {@code NetworkHandler.dispatchGuiSync}.
 *
 * <p><strong>Trust model.</strong> Nothing arriving in these packets is trusted. The client is
 * told whether it may edit ({@code Snapshot.canEdit}) purely so it can grey out its buttons; the
 * check that matters is re-run here on every single edit, because a client that lies about its
 * own permissions is exactly the case this guards against.
 */
public final class LpRequestHandler {

    /**
     * Anti-spam budget for edits, keyed like a command in {@link RateLimiter}. Generous enough
     * that a human clicking through the editor never notices, tight enough that a scripted
     * client cannot turn one packet type into unbounded storage writes.
     */
    private static final String EDIT_RATE_KEY = "gui:lp_edit";
    private static final int EDIT_MAX_PER_WINDOW = 30;
    private static final int EDIT_WINDOW_SECONDS = 10;

    private LpRequestHandler() {
    }

    // ------------------------------------------------------------------ sync

    public static void handleSync(RequestLpSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.createCommandSourceStack().hasPermission(2)) return;

            if (!CustomPerm.isLuckPermsActive()) {
                // Not an error: the editor is simply not applicable on this server, and the
                // client renders an empty snapshot as "LuckPerms is not active" rather than
                // hanging on "Loading...".
                send(player, new LpSyncPayload(LpDto.Snapshot.EMPTY));
                return;
            }
            sendSnapshot(player, sanitize(payload));
        });
    }

    /** Truncates the free-text target so a long search term cannot reach the LuckPerms lookup. */
    private static RequestLpSyncPayload sanitize(RequestLpSyncPayload payload) {
        String target = payload.target();
        if (target.length() <= RequestLpSyncPayload.MAX_TARGET_LENGTH) return payload;
        return new RequestLpSyncPayload(payload.scope(),
                target.substring(0, RequestLpSyncPayload.MAX_TARGET_LENGTH));
    }

    /**
     * Builds the snapshot off-thread and delivers it on the server thread. Called only from
     * inside an {@code isLuckPermsActive()} branch — see the class javadoc.
     */
    private static void sendSnapshot(ServerPlayer player, RequestLpSyncPayload request) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean canEdit = canEdit(player);
        LuckPermsAdminService.snapshot(request, server, canEdit).whenComplete((snapshot, error) -> {
            LpDto.Snapshot resolved = snapshot != null
                    ? snapshot
                    : new LpDto.Snapshot(request.scopeKey(), canEdit, List.of(), List.of(), List.of());
            if (error != null) {
                CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms editor snapshot failed for scope {}.",
                        request.scopeKey(), error);
            }
            // LuckPerms completes its futures on its own executor; every packet send has to be
            // handed back to the server thread.
            server.execute(() -> send(player, new LpSyncPayload(resolved)));
        });
    }

    // ------------------------------------------------------------------ edit

    public static void handleEdit(LpEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!player.createCommandSourceStack().hasPermission(2)) return;

            if (!CustomPerm.isLuckPermsActive()) {
                send(player, LpEditResultPayload.fail("LuckPerms is not active on this server."));
                return;
            }
            if (!canEdit(player)) {
                send(player, LpEditResultPayload.fail(
                        "You do not have " + PermissionNodes.LP_EDIT + "."));
                return;
            }

            LpEditOp op = LpEditOp.fromName(payload.op());
            if (op == null) {
                send(player, LpEditResultPayload.fail("Unknown operation."));
                return;
            }
            String rejection = validate(op, payload.args());
            if (rejection != null) {
                send(player, LpEditResultPayload.fail(rejection));
                return;
            }

            RateLimiter.Result budget = RateLimiter.tryAcquire(
                    EDIT_RATE_KEY, player.getUUID(), EDIT_MAX_PER_WINDOW, EDIT_WINDOW_SECONDS);
            if (!budget.allowed()) {
                send(player, LpEditResultPayload.fail(
                        "Too many edits at once — retry in " + budget.retryAfterSeconds() + "s."));
                return;
            }

            applyEdit(player, op, payload);
        });
    }

    /**
     * Structural validation, before any LuckPerms call: argument count against the operation's
     * declared arity, and a length cap per argument. Content validation (names, node syntax,
     * numbers) belongs to {@code LuckPermsAdminService}, which owns the semantics.
     *
     * @return the rejection message, or {@code null} when the payload is well-formed
     */
    private static String validate(LpEditOp op, List<String> args) {
        if (args.size() != op.arity()) {
            return "Malformed request for " + op.name() + ".";
        }
        for (String arg : args) {
            if (arg.length() > LpEditPayload.MAX_ARG_LENGTH) {
                return "One of the values is too long (limit "
                        + LpEditPayload.MAX_ARG_LENGTH + " characters).";
            }
        }
        return null;
    }

    /** Applies the mutation off-thread, then reports and refreshes on the server thread. */
    private static void applyEdit(ServerPlayer player, LpEditOp op, LpEditPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String actor = player.getGameProfile().getName();

        LuckPermsAdminService.apply(op, payload.args()).whenComplete((summary, error) -> server.execute(() -> {
            if (error != null) {
                send(player, LpEditResultPayload.fail(unwrap(error)));
                return;
            }
            // Permission-store writes are worth an audit line: this is the one path where a
            // click in a GUI changes what every player on the server is allowed to do.
            CustomPerm.LOGGER.info("[CustomPerm] LuckPerms editor: {} performed {} — {}",
                    actor, op.name(), summary);
            send(player, LpEditResultPayload.ok(summary));
            refresh(player, payload);
        }));
    }

    /** Re-sends the screen the client says it is showing, so the edit becomes visible at once. */
    private static void refresh(ServerPlayer player, LpEditPayload payload) {
        if (payload.refreshScope().isEmpty()) return;
        sendSnapshot(player, sanitize(new RequestLpSyncPayload(payload.refreshScope(), payload.refreshTarget())));
    }

    /**
     * Turns a failed future into a message for the admin. {@code LpEditException} messages are
     * written for exactly this; anything else is a bug or a LuckPerms failure, and gets a
     * generic line plus a log entry rather than an internal message on screen.
     */
    private static String unwrap(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause()
                : error;
        if (cause instanceof LuckPermsAdminService.LpEditException) {
            return cause.getMessage();
        }
        CustomPerm.LOGGER.warn("[CustomPerm] LuckPerms editor edit failed.", cause);
        return "LuckPerms rejected the change (see the server log).";
    }

    // ------------------------------------------------------------------ shared

    /**
     * Write gate. Op level 2 is already established by the caller; this adds the dedicated node,
     * with an escape hatch at permission level 4 so a server owner is never locked out of the
     * editor by a node they would need the editor to grant themselves.
     */
    private static boolean canEdit(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack();
        if (source.hasPermission(4)) return true;
        try {
            return CustomPerm.permissions.hasPermission(source, PermissionNodes.LP_EDIT);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            CustomPerm.LOGGER.warn("[CustomPerm] Permission check for {} failed; denying editor writes.",
                    PermissionNodes.LP_EDIT, t);
            return false;
        }
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.hasDisconnected()) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
