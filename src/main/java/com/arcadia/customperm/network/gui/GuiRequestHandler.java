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
import com.arcadia.customperm.admin.ExportAdmin;
import com.arcadia.customperm.admin.ExportPlan;
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.ImportAdmin;
import com.arcadia.customperm.admin.ImportPlan;
import com.arcadia.customperm.admin.LogAdmin;
import com.arcadia.customperm.admin.RateLimitAdmin;
import com.arcadia.customperm.admin.UserAdmin;
import com.arcadia.customperm.command.RateLimiter;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
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

    /** Opens the LuckPerms editor on a section; does nothing unless the LuckPerms mod is installed. */
    public static void openLuckPerms(ServerPlayer player, String section) {
        if (!CustomPerm.isLuckPermsPresent() || player.hasDisconnected() || !clientSupportsInterface(player)) return;
        PacketDistributor.sendToPlayer(player,
                new GuiPagePayload(true, GuiSnapshots.context(player), new LuckPermsData(section)));
    }

    /**
     * Whether a page can be shown at all: the LuckPerms editor exists only when the LuckPerms mod is
     * installed. Installed but not running, it is still shown, with a banner saying why it cannot edit.
     */
    public static boolean available(GuiPage page) {
        return (page != GuiPage.LUCKPERMS && page != GuiPage.IMPORT) || CustomPerm.isLuckPermsPresent();
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
                AdminResult refused = AdminResult.fail("You do not have " + action.area().node() + ".");
                logAction(player, action, payload.args(), refused);
                send(player, GuiActionResultPayload.fail(refused.message()));
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
            logAction(player, action, payload.args(), result);
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

    private static void logAction(ServerPlayer player, GuiAction action, List<String> args, AdminResult result) {
        ActivityLog.admin(player.createCommandSourceStack(), LogEntry.SOURCE_INTERFACE,
                action.name() + (args.isEmpty() ? "" : " " + String.join(" ", args)), result);
    }

    private static AdminResult apply(ServerPlayer player, GuiAction action, List<String> args) {
        return switch (action) {
            case RELOAD -> ConfigAdmin.reload(player.getServer());
            case COMMAND_EXPOSE -> CommandAdmin.expose(player.getServer(), args.get(0));
            case COMMAND_HIDE -> CommandAdmin.hide(player.getServer(), args.get(0));
            case COMMAND_KEEP_ORIGINAL -> bool(args.get(1)) == null
                    ? malformed(action)
                    : CommandAdmin.setPreserveOriginal(player.getServer(), args.get(0), bool(args.get(1)));
            case COMMAND_GATE_ALL -> bool(args.get(0)) == null ? malformed(action)
                    : CommandAdmin.setGateAll(player.getServer(), bool(args.get(0)));
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
            case GRADE_CREATE -> GradeAdmin.create(args.get(0));
            case GRADE_DELETE -> guarded(player, () -> GradeAdmin.delete(player.getServer(), args.get(0)));
            case GRADE_NODE_ADD -> kind(args.get(2)) == null ? malformed(action)
                    : duration(args.get(3)) < 0 ? badDuration(args.get(3))
                    : guarded(player, () -> GradeAdmin.addNode(player.getServer(), args.get(0), args.get(1), kind(args.get(2)),
                            duration(args.get(3)), args.get(4)));
            case GRADE_NODE_REMOVE -> kind(args.get(2)) == null ? malformed(action)
                    : guarded(player, () -> GradeAdmin.removeNode(player.getServer(), args.get(0), args.get(1), kind(args.get(2)),
                            args.get(3)));
            case GRADE_WEIGHT_SET -> signed(args.get(1)) == null ? malformed(action)
                    : guarded(player, () -> GradeAdmin.setWeight(player.getServer(), args.get(0), signed(args.get(1))));
            case GRADE_DISPLAYNAME_SET -> GradeAdmin.setDisplayName(args.get(0), args.get(1));
            case GRADE_PARENT_ADD -> duration(args.get(2)) < 0 ? badDuration(args.get(2))
                    : guarded(player, () -> GradeAdmin.addParent(player.getServer(), args.get(0), args.get(1), duration(args.get(2)),
                            args.get(3)));
            case GRADE_PARENT_REMOVE -> guarded(player, () -> GradeAdmin.removeParent(player.getServer(), args.get(0), args.get(1),
                    args.get(2)));
            case GRADE_PARENT_DENY -> duration(args.get(2)) < 0 ? badDuration(args.get(2))
                    : guarded(player, () -> GradeAdmin.denyParent(player.getServer(), args.get(0), args.get(1), duration(args.get(2)),
                            args.get(3)));
            case GRADE_PARENT_ALLOW -> guarded(player, () -> GradeAdmin.allowParent(player.getServer(), args.get(0), args.get(1),
                    args.get(2)));
            case GRADE_REFUSE -> duration(args.get(2)) < 0 ? badDuration(args.get(2))
                    : guarded(player, () -> refuseByName(player, args.get(0), args.get(1), duration(args.get(2)), args.get(3)));
            case GRADE_ACCEPT -> guarded(player, () -> acceptByUuid(player, args.get(0), args.get(1), args.get(2)));
            case GRADE_ASSIGN -> duration(args.get(2)) < 0 ? badDuration(args.get(2))
                    : guarded(player, () -> assignByName(player, args.get(0), args.get(1), duration(args.get(2)), args.get(3)));
            case GRADE_UNASSIGN -> guarded(player, () -> unassignByUuid(player, args.get(0), args.get(1), args.get(2)));
            case GRADE_DEFAULT -> guarded(player, () -> GradeAdmin.setDefault(player.getServer(), args.get(0)));
            case USER_NODE_ADD -> kind(args.get(2)) == null ? malformed(action)
                    : duration(args.get(3)) < 0 ? badDuration(args.get(3))
                    : guarded(player, () -> userNodeByName(player, args.get(0), args.get(1), kind(args.get(2)),
                            duration(args.get(3)), args.get(4)));
            case USER_NODE_REMOVE -> kind(args.get(2)) == null ? malformed(action)
                    : guarded(player, () -> userNodeByUuid(player, args.get(0), args.get(1), kind(args.get(2)), args.get(3)));
            case TRACK_PROMOTE -> moveByName(player, args.get(0), args.get(1), true, args.get(2));
            case TRACK_DEMOTE -> moveByName(player, args.get(0), args.get(1), false, args.get(2));
            case IMPORT_PREVIEW -> bool(args.get(0)) == null ? malformed(action)
                    : importPreview(player, bool(args.get(0)));
            case IMPORT_APPLY -> importApply(player, args.get(0));
            case GRADE_CHAT_ADD -> chatKind(args.get(1)) == null || signed(args.get(2)) == null ? malformed(action)
                    : duration(args.get(4)) < 0 ? badDuration(args.get(4))
                    : GradeAdmin.addChat(player.getServer(), args.get(0), chatKind(args.get(1)), signed(args.get(2)),
                            args.get(3), duration(args.get(4)), args.get(5));
            case GRADE_CHAT_REMOVE -> chatKind(args.get(1)) == null || signed(args.get(2)) == null ? malformed(action)
                    : GradeAdmin.removeChat(player.getServer(), args.get(0), chatKind(args.get(1)), signed(args.get(2)),
                            args.get(3));
            case USER_CHAT_ADD -> chatKind(args.get(1)) == null || signed(args.get(2)) == null ? malformed(action)
                    : duration(args.get(4)) < 0 ? badDuration(args.get(4))
                    : userChatByName(player, args.get(0), holder -> holder.add(chatKind(args.get(1)), signed(args.get(2)),
                            args.get(3), duration(args.get(4)), args.get(5)));
            case USER_CHAT_REMOVE -> chatKind(args.get(1)) == null || signed(args.get(2)) == null ? malformed(action)
                    : userChatByName(player, args.get(0), holder -> holder.remove(chatKind(args.get(1)), signed(args.get(2)),
                            args.get(3)));
            case GRADE_META_SET -> duration(args.get(3)) < 0 ? badDuration(args.get(3))
                    : com.arcadia.customperm.admin.MetaAdmin.setOnGrade(player.getServer(), args.get(0), args.get(1),
                            args.get(2), duration(args.get(3)), args.get(4));
            case GRADE_META_UNSET -> com.arcadia.customperm.admin.MetaAdmin.unsetOnGrade(player.getServer(), args.get(0),
                    args.get(1), args.get(2));
            case USER_META_SET -> duration(args.get(3)) < 0 ? badDuration(args.get(3))
                    : userMetaByName(player, args.get(0), (uuid, name) -> com.arcadia.customperm.admin.MetaAdmin.setOnPlayer(
                            player.getServer(), uuid, name, args.get(1), args.get(2), duration(args.get(3)), args.get(4)));
            case USER_NICK_SET -> uuid(args.get(0)) == null ? malformed(action)
                    : com.arcadia.customperm.admin.NickAdmin.set(player.getServer(), uuid(args.get(0)),
                            GradeAdmin.displayName(player.getServer(), uuid(args.get(0))), args.get(1), true);
            case USER_META_UNSET -> userMetaByName(player, args.get(0), (uuid, name) ->
                    com.arcadia.customperm.admin.MetaAdmin.unsetOnPlayer(player.getServer(), uuid, name, args.get(1),
                            args.get(2)));
            case NAMES_STACK -> !args.get(0).equals("highest") && !args.get(0).equals("stacked") ? malformed(action)
                    : com.arcadia.customperm.admin.NameAdmin.setStack(player.getServer(), "both",
                            args.get(0).equals("stacked"), null);
            case NAMES_DECORATE -> bool(args.get(0)) == null ? malformed(action)
                    : com.arcadia.customperm.admin.NameAdmin.setEnabled(player.getServer(), bool(args.get(0)));
            case EXPORT_PREVIEW -> exportPreview(player);
            case EXPORT_APPLY -> exportApply(player, args.get(0));
            case LOG_PLAYERS -> bool(args.get(0)) == null ? malformed(action) : LogAdmin.setPlayerLog(bool(args.get(0)));
            case LOG_MASK -> bool(args.get(0)) == null ? malformed(action) : LogAdmin.setMasking(bool(args.get(0)));
        };
    }

    /**
     * Reads LuckPerms and refreshes the page with the report. Answers at once and refreshes when the
     * answer comes: the read is asynchronous, and the admin should not be left looking at a frozen page.
     */
    private static AdminResult importPreview(ServerPlayer admin, boolean exposeCommands) {
        AdminResult refusal = importRefusal(admin);
        if (refusal != null) return refusal;
        String key = admin.getUUID().toString();
        // Inside the LuckPerms branch, and called rather than referenced: a method reference resolves its
        // target eagerly, which would drag LuckPerms onto a server that does not have it.
        com.arcadia.customperm.perm.lp.LuckPermsImport.read(exposeCommands)
                .whenComplete((plan, error) -> admin.getServer().execute(() -> {
                    if (plan != null) ImportAdmin.remember(key, plan, exposeCommands);
                    sendPage(admin, GuiPage.IMPORT, false);
                    if (plan == null) {
                        send(admin, GuiActionResultPayload.fail("LuckPerms could not be read"
                                + (error == null ? "." : ": " + error.getMessage())));
                    }
                }));
        return AdminResult.ok("Reading LuckPerms, this changes nothing.");
    }

    /** Applies what this admin previewed, and only that. */
    private static AdminResult importApply(ServerPlayer admin, String mode) {
        AdminResult refusal = importRefusal(admin);
        if (refusal != null) return refusal;
        if (!mode.equals("merge") && !mode.equals("replace")) return malformed(GuiAction.IMPORT_APPLY);
        String key = admin.getUUID().toString();
        ImportPlan plan = ImportAdmin.previewed(key);
        if (plan == null) {
            return AdminResult.fail("Read LuckPerms first. A preview older than 10 minutes is read again "
                    + "rather than trusted.");
        }
        AdminResult result = guarded(admin, () -> ImportAdmin.apply(admin.getServer(), plan, mode.equals("replace")));
        if (result.success()) ImportAdmin.forget(key);
        return result;
    }

    /** Importing asks for the three nodes together, and for something to read. */
    private static AdminResult importRefusal(ServerPlayer admin) {
        if (!GuiAccess.canImport(admin)) {
            return AdminResult.fail("Importing needs " + GuiArea.GRADES.node() + ", " + GuiArea.COMMANDS.node()
                    + " and " + GuiArea.LUCKPERMS.node() + ".");
        }
        return ImportAdmin.unavailable();
    }

    /**
     * Reads the grades and refreshes the page with what an export would write. The grades are read here;
     * LuckPerms is asked only which groups it already has, which is what makes it asynchronous.
     */
    private static AdminResult exportPreview(ServerPlayer admin) {
        AdminResult refusal = exportRefusal(admin);
        if (refusal != null) return refusal;
        String key = admin.getUUID().toString();
        ExportPlan plan = ExportAdmin.plan();
        com.arcadia.customperm.perm.lp.LuckPermsExport.existingGroups()
                .whenComplete((existing, error) -> admin.getServer().execute(() -> {
                    ExportAdmin.remember(key, plan.withExisting(existing == null ? java.util.Set.of() : existing));
                    sendPage(admin, GuiPage.IMPORT, false);
                }));
        return AdminResult.ok("Reading the grades, this changes nothing.");
    }

    /**
     * Starts writing what this admin previewed. Answers at once: the page follows the progress, and the
     * result arrives when it ends, recorded in the activity log like any other change.
     */
    private static AdminResult exportApply(ServerPlayer admin, String mode) {
        AdminResult refusal = exportRefusal(admin);
        if (refusal != null) return refusal;
        if (!mode.equals("merge") && !mode.equals("replace")) return malformed(GuiAction.EXPORT_APPLY);
        String key = admin.getUUID().toString();
        ExportPlan plan = ExportAdmin.previewed(key);
        if (plan == null) {
            return AdminResult.fail("Read the grades first. A preview older than 10 minutes is read again "
                    + "rather than trusted.");
        }
        boolean replace = mode.equals("replace");
        AdminResult lockout = ExportAdmin.lockout(admin, plan, replace);
        if (lockout != null) return lockout;
        AdminResult started = ExportAdmin.start(admin.getServer(), plan, replace,
                () -> sendPage(admin, GuiPage.IMPORT, false),
                result -> {
                    ActivityLog.admin(admin.createCommandSourceStack(), LogEntry.SOURCE_INTERFACE,
                            GuiAction.EXPORT_APPLY.name() + " " + mode + " (finished)", result);
                    send(admin, new GuiActionResultPayload(result.success() && result.warnings().isEmpty(),
                            result.summary()));
                    sendPage(admin, GuiPage.IMPORT, false);
                });
        if (started.success()) ExportAdmin.forget(key);
        return started;
    }

    /** Exporting asks for the grades and LuckPerms nodes together, and for LuckPerms to write to. */
    private static AdminResult exportRefusal(ServerPlayer admin) {
        if (!GuiAccess.canExport(admin)) {
            return AdminResult.fail("Exporting needs " + GuiArea.GRADES.node() + " and " + GuiArea.LUCKPERMS.node() + ".");
        }
        return ExportAdmin.unavailable();
    }

    /** Refuses a grade change that would lock this admin out of /customperm and the interface. */
    private static AdminResult guarded(ServerPlayer admin, java.util.function.Supplier<AdminResult> change) {
        return GradeAdmin.guarded(admin.createCommandSourceStack(), admin.getServer(), change);
    }

    /** Seconds in a duration box, 0 when it is empty (permanent), -1 when it cannot be read. */
    private static long duration(String raw) {
        return raw == null || raw.isBlank() ? 0 : com.arcadia.customperm.perm.Expiry.parse(raw);
    }

    private static AdminResult badDuration(String raw) {
        return AdminResult.fail("Invalid duration '" + raw.trim() + "': use w, d, h, m, s, such as 30d or 1d12h.");
    }

    /** By name, like assigning: the Tracks tab can move a player who holds nothing yet onto a first rung. */
    private static AdminResult moveByName(ServerPlayer admin, String name, String track, boolean up, String context) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> com.arcadia.customperm.admin.TrackAdmin.moveBy(admin.createCommandSourceStack(),
                        admin.getServer(), profile, track, up, context))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    private static AdminResult assignByName(ServerPlayer admin, String name, String grade, long seconds,
                                            String context) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> GradeAdmin.assign(admin.getServer(), profile, grade, seconds, context))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    private static AdminResult refuseByName(ServerPlayer admin, String name, String grade, long seconds, String context) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> UserAdmin.refuseGrade(admin.getServer(), profile.getId(), profile.getName(), grade, seconds,
                        context))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    private static AdminResult acceptByUuid(ServerPlayer admin, String rawUuid, String grade, String context) {
        java.util.UUID uuid = uuid(rawUuid);
        if (uuid == null) return malformed(GuiAction.GRADE_ACCEPT);
        return UserAdmin.acceptGrade(admin.getServer(), uuid, GradeAdmin.displayName(admin.getServer(), uuid), grade,
                context);
    }

    /** Adding addresses the player by name: the screen offers a field for someone who holds nothing yet. */
    private static AdminResult userNodeByName(ServerPlayer admin, String name, String node, boolean deny, long seconds,
                                              String context) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> UserAdmin.addNode(admin.getServer(), profile.getId(), profile.getName(), node, deny, seconds,
                        context))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    /** By name, like adding a node: a player picked in the field has no row, so no UUID, yet. */
    private static AdminResult userChatByName(ServerPlayer admin, String name,
                                              java.util.function.Function<com.arcadia.customperm.admin.ChatHolder, AdminResult> edit) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> edit.apply(com.arcadia.customperm.admin.ChatHolder.player(admin.getServer(),
                        profile.getId(), profile.getName())))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    /** By name, like a prefix, so a player who holds nothing yet can get meta. */
    private static AdminResult userMetaByName(ServerPlayer admin, String name,
                                              java.util.function.BiFunction<java.util.UUID, String, AdminResult> edit) {
        AdminResult refusal = GradeAdmin.unavailable();
        if (refusal != null) return refusal;
        GradeAdmin.Resolution resolution = GradeAdmin.resolvePlayer(admin.getServer(), name);
        return resolution.profile()
                .map(profile -> edit.apply(profile.getId(), profile.getName()))
                .orElseGet(() -> AdminResult.fail(resolution.problem()));
    }

    /** {@code "suffix"} is true, {@code "prefix"} false, anything else malformed. */
    private static Boolean chatKind(String raw) {
        return switch (raw) {
            case "prefix" -> false;
            case "suffix" -> true;
            default -> null;
        };
    }

    /** Removing addresses the player by UUID: the row always carries one, a resolvable name it may not. */
    private static AdminResult userNodeByUuid(ServerPlayer admin, String rawUuid, String node, boolean deny,
                                              String context) {
        java.util.UUID uuid = uuid(rawUuid);
        if (uuid == null) return malformed(GuiAction.USER_NODE_REMOVE);
        return UserAdmin.removeNode(admin.getServer(), uuid, GradeAdmin.displayName(admin.getServer(), uuid),
                node, deny, context);
    }

    private static AdminResult unassignByUuid(ServerPlayer admin, String rawUuid, String grade, String context) {
        java.util.UUID uuid = uuid(rawUuid);
        if (uuid == null) return malformed(GuiAction.GRADE_UNASSIGN);
        return GradeAdmin.unassign(admin.getServer(), uuid, GradeAdmin.displayName(admin.getServer(), uuid), grade,
                context);
    }

    /** The UUID a row carries, or {@code null} when the packet did not carry one. */
    private static java.util.UUID uuid(String raw) {
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code "deny"} is true, {@code "allow"} false, anything else malformed. */
    private static Boolean kind(String value) {
        return switch (value) {
            case "deny" -> Boolean.TRUE;
            case "allow" -> Boolean.FALSE;
            default -> null;
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

    /** Strict signed integer argument, for a grade weight: an optional minus then decimal digits. */
    private static Integer signed(String value) {
        String digits = value.startsWith("-") ? value.substring(1) : value;
        if (digits.isEmpty() || digits.length() > 9 || !digits.chars().allMatch(Character::isDigit)) return null;
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
        if (player.hasDisconnected() || !clientSupportsInterface(player) || !available(page)) return;
        PacketDistributor.sendToPlayer(player,
                new GuiPagePayload(open, GuiSnapshots.context(player), GuiSnapshots.page(page, player)));
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.hasDisconnected() || !player.connection.hasChannel(payload.type())) return;
        PacketDistributor.sendToPlayer(player, payload);
    }
}
