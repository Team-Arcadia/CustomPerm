/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.perm.lp;

import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.ChatMetaType;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.ChatMetaNode;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.MetaNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.track.DemotionResult;
import net.luckperms.api.track.PromotionResult;
import net.luckperms.api.track.Track;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Every LuckPerms write the in-game editor can perform, and the read side that feeds its
 * screens. This is the mod's only class that both imports {@code net.luckperms.api.*} and
 * mutates it, which is deliberate: LuckPerms is a soft dependency, so the set of classes that
 * cannot be loaded without it must stay small enough to audit.
 *
 * <p><strong>Loading discipline.</strong> Callers must reach this class only from inside a
 * {@code CustomPerm.isLuckPermsActive()} branch, and never through a method reference — a
 * method reference resolves its target eagerly, which would drag {@code LuckPermsProvider} onto
 * a LuckPerms-less server at verification time. {@code LpRequestHandler} is the only caller and
 * follows that rule; the reasoning is the same as {@code NetworkHandler.dispatchGuiSync}.
 *
 * <p><strong>Threading.</strong> Reads of already-loaded groups and tracks are in-memory and run
 * on the calling (server) thread. Everything that touches storage — loading all groups, loading
 * a user, saving — is asynchronous in LuckPerms and stays that way here: every public method
 * returns a {@link CompletableFuture}, and the caller is responsible for hopping back onto the
 * server thread before touching Minecraft state. Nothing in this class blocks on a future.
 *
 * <p><strong>Own nodes, not effective nodes.</strong> Prefix, suffix and weight are read from the
 * holder's own nodes rather than from its resolved cached meta. An editor edits what a holder
 * declares; showing the inherited value would make the "unset" button look broken whenever a
 * parent supplied the same key.
 */
public final class LuckPermsAdminService {

    /** LuckPerms refuses to delete this group, and so do we — with a clearer message. */
    private static final String DEFAULT_GROUP = "default";

    /** Cap on how many users a list request returns; the screen pages through a search instead. */
    private static final int MAX_USER_RESULTS = 200;

    private LuckPermsAdminService() {
    }

    // ------------------------------------------------------------------ reads

    /**
     * Builds the snapshot for one editor screen. Never completes exceptionally: a failure to
     * reach LuckPerms yields an empty snapshot for the requested scope, which the GUI renders as
     * an empty list rather than a broken screen.
     */
    public static CompletableFuture<LpDto.Snapshot> snapshot(RequestLpSyncPayload request,
                                                             MinecraftServer server,
                                                             boolean canEdit) {
        String scopeKey = request.scopeKey();
        try {
            LuckPerms api = LuckPermsProvider.get();
            return switch (request.scope()) {
                case RequestLpSyncPayload.SCOPE_GROUPS -> groupsSnapshot(api, scopeKey, canEdit);
                case RequestLpSyncPayload.SCOPE_GROUP -> groupSnapshot(api, scopeKey, canEdit, request.target());
                case RequestLpSyncPayload.SCOPE_USERS -> usersSnapshot(api, server, scopeKey, canEdit, request.target());
                case RequestLpSyncPayload.SCOPE_USER -> userSnapshot(api, server, scopeKey, canEdit, request.target());
                case RequestLpSyncPayload.SCOPE_TRACKS -> tracksSnapshot(api, scopeKey, canEdit);
                default -> CompletableFuture.completedFuture(empty(scopeKey, canEdit));
            };
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            return CompletableFuture.completedFuture(empty(scopeKey, canEdit));
        }
    }

    private static LpDto.Snapshot empty(String scopeKey, boolean canEdit) {
        return new LpDto.Snapshot(scopeKey, canEdit, List.of(), List.of(), List.of());
    }

    private static CompletableFuture<LpDto.Snapshot> groupsSnapshot(LuckPerms api, String scopeKey, boolean canEdit) {
        // Groups the server has never touched are not in memory; the list screen must show them
        // all, so pay the one-off storage read here rather than lie about what exists.
        return api.getGroupManager().loadAllGroups().thenApply(ignored -> {
            List<LpDto.GroupDto> groups = api.getGroupManager().getLoadedGroups().stream()
                    .map(g -> toGroupDto(g, false))
                    .sorted(Comparator.comparingInt((LpDto.GroupDto g) -> -g.weight())
                            .thenComparing(LpDto.GroupDto::name))
                    .toList();
            return new LpDto.Snapshot(scopeKey, canEdit, groups, List.of(), List.of());
        });
    }

    private static CompletableFuture<LpDto.Snapshot> groupSnapshot(LuckPerms api, String scopeKey,
                                                                   boolean canEdit, String name) {
        // The detail screen also needs the full group list: the "add parent" picker offers every
        // other group, and a second round-trip for it would make the screen flicker.
        return api.getGroupManager().loadGroup(name).thenCompose(loaded ->
                api.getGroupManager().loadAllGroups().thenApply(ignored -> {
                    List<LpDto.GroupDto> groups = new ArrayList<>();
                    loaded.ifPresent(group -> groups.add(toGroupDto(group, true)));
                    api.getGroupManager().getLoadedGroups().stream()
                            .filter(g -> !g.getName().equalsIgnoreCase(name))
                            .map(g -> toGroupDto(g, false))
                            .sorted(Comparator.comparing(LpDto.GroupDto::name))
                            .forEach(groups::add);
                    return new LpDto.Snapshot(scopeKey, canEdit, groups, List.of(), tracks(api));
                }));
    }

    /**
     * Online players first, then any LuckPerms user already in memory. A non-empty
     * {@code search} additionally resolves that exact username through LuckPerms' own lookup, so
     * an offline player can be edited by name.
     */
    private static CompletableFuture<LpDto.Snapshot> usersSnapshot(LuckPerms api, MinecraftServer server,
                                                                   String scopeKey, boolean canEdit,
                                                                   String search) {
        CompletableFuture<Optional<UUID>> searched = search.isEmpty()
                ? CompletableFuture.completedFuture(Optional.empty())
                : api.getUserManager().lookupUniqueId(search).thenApply(Optional::ofNullable);

        return searched.thenCompose(hit -> {
            CompletableFuture<?> loadSearched = hit
                    .map(uuid -> (CompletableFuture<?>) api.getUserManager().loadUser(uuid))
                    .orElseGet(() -> CompletableFuture.completedFuture(null));

            return loadSearched.thenApply(ignored -> {
                Set<UUID> online = onlineUuids(server);
                List<LpDto.UserDto> users = new ArrayList<>();
                Set<UUID> seen = new LinkedHashSet<>();

                for (User user : api.getUserManager().getLoadedUsers()) {
                    if (!matchesSearch(user, search, hit)) continue;
                    if (!seen.add(user.getUniqueId())) continue;
                    users.add(toUserDto(user, online.contains(user.getUniqueId()), false));
                    if (users.size() >= MAX_USER_RESULTS) break;
                }
                users.sort(Comparator.comparing((LpDto.UserDto u) -> !u.online())
                        .thenComparing(u -> u.username().toLowerCase(Locale.ROOT)));
                return new LpDto.Snapshot(scopeKey, canEdit, groupsForPicker(api), users, tracks(api));
            });
        });
    }

    private static boolean matchesSearch(User user, String search, Optional<UUID> exactHit) {
        if (search.isEmpty()) return true;
        if (exactHit.filter(uuid -> uuid.equals(user.getUniqueId())).isPresent()) return true;
        String username = user.getUsername();
        return username != null && username.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT));
    }

    private static CompletableFuture<LpDto.Snapshot> userSnapshot(LuckPerms api, MinecraftServer server,
                                                                  String scopeKey, boolean canEdit,
                                                                  String uuidText) {
        UUID uuid = parseUuid(uuidText);
        if (uuid == null) return CompletableFuture.completedFuture(empty(scopeKey, canEdit));
        return api.getUserManager().loadUser(uuid).thenApply(user -> {
            List<LpDto.UserDto> users = user == null
                    ? List.of()
                    : List.of(toUserDto(user, onlineUuids(server).contains(uuid), true));
            return new LpDto.Snapshot(scopeKey, canEdit, groupsForPicker(api), users, tracks(api));
        });
    }

    private static CompletableFuture<LpDto.Snapshot> tracksSnapshot(LuckPerms api, String scopeKey, boolean canEdit) {
        return api.getTrackManager().loadAllTracks()
                .thenCompose(ignored -> api.getGroupManager().loadAllGroups())
                .thenApply(ignored -> new LpDto.Snapshot(scopeKey, canEdit, groupsForPicker(api), List.of(), tracks(api)));
    }

    /** Name-only group entries, enough for the "add parent" and "append to track" pickers. */
    private static List<LpDto.GroupDto> groupsForPicker(LuckPerms api) {
        return api.getGroupManager().getLoadedGroups().stream()
                .map(g -> toGroupDto(g, false))
                .sorted(Comparator.comparing(LpDto.GroupDto::name))
                .toList();
    }

    private static List<LpDto.TrackDto> tracks(LuckPerms api) {
        return api.getTrackManager().getLoadedTracks().stream()
                .map(t -> new LpDto.TrackDto(t.getName(), List.copyOf(t.getGroups())))
                .sorted(Comparator.comparing(LpDto.TrackDto::name))
                .toList();
    }

    private static Set<UUID> onlineUuids(MinecraftServer server) {
        Set<UUID> uuids = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            uuids.add(player.getUUID());
        }
        return uuids;
    }

    // ------------------------------------------------------------------ DTO mapping

    private static LpDto.GroupDto toGroupDto(Group group, boolean full) {
        Collection<Node> own = group.getNodes();
        return new LpDto.GroupDto(
                group.getName(),
                Objects.requireNonNullElse(group.getDisplayName(), ""),
                group.getWeight().orElse(LpDto.NO_WEIGHT),
                chatMeta(own, ChatMetaType.PREFIX),
                chatMeta(own, ChatMetaType.SUFFIX),
                parentNames(own),
                full ? toNodeDtos(own) : List.of(),
                own.size());
    }

    private static LpDto.UserDto toUserDto(User user, boolean online, boolean full) {
        Collection<Node> own = user.getNodes();
        return new LpDto.UserDto(
                user.getUniqueId().toString(),
                Objects.requireNonNullElse(user.getUsername(), user.getUniqueId().toString()),
                user.getPrimaryGroup(),
                online,
                parentNames(own),
                full ? toNodeDtos(own) : List.of(),
                own.size());
    }

    private static List<String> parentNames(Collection<Node> nodes) {
        return nodes.stream()
                .filter(NodeType.INHERITANCE::matches)
                .map(n -> NodeType.INHERITANCE.cast(n).getGroupName())
                .distinct()
                .sorted()
                .toList();
    }

    /** Highest-priority own prefix/suffix, or an empty string when the holder declares none. */
    private static String chatMeta(Collection<Node> nodes, ChatMetaType type) {
        return nodes.stream()
                .filter(NodeType.CHAT_META::matches)
                .map(NodeType.CHAT_META::cast)
                .filter(meta -> meta.getMetaType() == type)
                .max(Comparator.comparingInt(ChatMetaNode::getPriority))
                .map(ChatMetaNode::getMetaValue)
                .orElse("");
    }

    private static List<LpDto.NodeDto> toNodeDtos(Collection<Node> nodes) {
        return nodes.stream()
                .map(LuckPermsAdminService::toNodeDto)
                .sorted(Comparator.comparing(LpDto.NodeDto::type).thenComparing(LpDto.NodeDto::key))
                .toList();
    }

    private static LpDto.NodeDto toNodeDto(Node node) {
        return new LpDto.NodeDto(
                node.getKey(),
                node.getValue(),
                formatContexts(node.getContexts()),
                node.hasExpiry() ? node.getExpiry().getEpochSecond() : LpDto.NO_EXPIRY,
                node.getType().name());
    }

    // ------------------------------------------------------------------ writes

    /**
     * Applies one editor mutation. The returned future completes with a human-readable summary
     * on success, or completes exceptionally with an {@link LpEditException} whose message is
     * safe to show the admin. Argument count is guaranteed by the caller
     * ({@code LpRequestHandler} checks {@link LpEditOp#arity()}), so this method validates
     * argument <em>content</em> only.
     */
    public static CompletableFuture<String> apply(LpEditOp op, List<String> args) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            return switch (op) {
                case GROUP_CREATE -> createGroup(api, args.get(0));
                case GROUP_DELETE -> deleteGroup(api, args.get(0));
                case TRACK_CREATE -> createTrack(api, args.get(0));
                case TRACK_DELETE -> deleteTrack(api, args.get(0));
                case TRACK_APPEND, TRACK_INSERT, TRACK_REMOVE -> editTrack(api, op, args);
                case USER_PROMOTE, USER_DEMOTE -> promoteOrDemote(api, op, args);
                case USER_PRIMARY_GROUP_SET -> setPrimaryGroup(api, args.get(0), args.get(1));
                default -> op.isUserOp() ? editUser(api, op, args) : editGroup(api, op, args);
            };
        } catch (LpEditException e) {
            return CompletableFuture.failedFuture(e);
        } catch (Throwable t) {
            if (t instanceof Error e) throw e;
            return CompletableFuture.failedFuture(new LpEditException("LuckPerms rejected the edit: " + describe(t)));
        }
    }

    private static CompletableFuture<String> createGroup(LuckPerms api, String name) {
        String clean = requireName(name, "group");
        return api.getGroupManager().loadGroup(clean).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.failedFuture(new LpEditException("Group already exists: " + clean));
            }
            return api.getGroupManager().createAndLoadGroup(clean)
                    .thenApply(group -> "Created group " + group.getName());
        });
    }

    private static CompletableFuture<String> deleteGroup(LuckPerms api, String name) {
        String clean = requireName(name, "group");
        if (clean.equalsIgnoreCase(DEFAULT_GROUP)) {
            return CompletableFuture.failedFuture(
                    new LpEditException("The default group cannot be deleted."));
        }
        return api.getGroupManager().loadGroup(clean).thenCompose(existing -> {
            Group group = existing.orElse(null);
            if (group == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("No such group: " + clean));
            }
            return api.getGroupManager().deleteGroup(group).thenApply(ignored -> "Deleted group " + clean);
        });
    }

    /**
     * The group mutations that are all "load, change the node map, save". Deliberately not
     * {@code GroupManager.modifyGroup}: that helper is built on {@code createAndLoadGroup}, so a
     * typo in a group name would silently create a group instead of reporting the mistake.
     */
    private static CompletableFuture<String> editGroup(LuckPerms api, LpEditOp op, List<String> args) {
        String name = requireName(args.get(0), "group");
        return api.getGroupManager().loadGroup(name).thenCompose(existing -> {
            Group group = existing.orElse(null);
            if (group == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("No such group: " + name));
            }
            String summary;
            try {
                summary = mutate(group, op, args);
            } catch (LpEditException e) {
                return CompletableFuture.<String>failedFuture(e);
            }
            return api.getGroupManager().saveGroup(group).thenApply(ignored -> summary);
        });
    }

    private static CompletableFuture<String> editUser(LuckPerms api, LpEditOp op, List<String> args) {
        UUID uuid = requireUuid(args.get(0));
        return api.getUserManager().loadUser(uuid).thenCompose(user -> {
            if (user == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("Unknown player: " + uuid));
            }
            String summary;
            try {
                summary = mutate(user, op, args);
            } catch (LpEditException e) {
                return CompletableFuture.<String>failedFuture(e);
            }
            return api.getUserManager().saveUser(user).thenApply(ignored -> summary);
        });
    }

    /**
     * The node-map mutations shared by groups and users. Group and user operations are distinct
     * enum constants (so the permission gate and the audit log can tell them apart) but the node
     * algebra underneath is identical, hence the single implementation over
     * {@link PermissionHolder}.
     */
    private static String mutate(PermissionHolder holder, LpEditOp op, List<String> args) {
        String who = holder.getFriendlyName();
        return switch (op) {
            case GROUP_PERM_ADD, USER_PERM_ADD -> {
                String key = requireNode(args.get(1));
                boolean value = Boolean.parseBoolean(args.get(2));
                ImmutableContextSet contexts = parseContexts(args.get(3));
                long duration = parseDuration(args.get(4));
                NodeBuilder<?, ?> builder = Node.builder(key).value(value).context(contexts);
                if (duration > 0) builder = builder.expiry(Duration.ofSeconds(duration));
                DataMutateResult result = holder.data().add(builder.build());
                if (!result.wasSuccessful()) {
                    throw new LpEditException(who + " already has " + key + describeContexts(contexts) + ".");
                }
                yield (value ? "Granted " : "Denied ") + key + " to " + who + describeContexts(contexts)
                        + describeDuration(duration);
            }
            case GROUP_PERM_REMOVE, USER_PERM_REMOVE -> {
                String key = requireNode(args.get(1));
                ImmutableContextSet contexts = parseContexts(args.get(2));
                int removed = clear(holder, contexts, n -> n.getKey().equalsIgnoreCase(key));
                if (removed == 0) {
                    throw new LpEditException(who + " does not have " + key + describeContexts(contexts) + ".");
                }
                yield "Removed " + key + " from " + who + describeContexts(contexts);
            }
            case GROUP_PARENT_ADD -> {
                String parent = requireName(args.get(1), "group");
                ImmutableContextSet contexts = parseContexts(args.get(2));
                yield addParent(holder, parent, contexts, 0L, who);
            }
            case USER_PARENT_ADD -> {
                String parent = requireName(args.get(1), "group");
                ImmutableContextSet contexts = parseContexts(args.get(2));
                yield addParent(holder, parent, contexts, parseDuration(args.get(3)), who);
            }
            case GROUP_PARENT_REMOVE, USER_PARENT_REMOVE -> {
                String parent = requireName(args.get(1), "group");
                ImmutableContextSet contexts = parseContexts(args.get(2));
                int removed = clear(holder, contexts, n -> NodeType.INHERITANCE.matches(n)
                        && NodeType.INHERITANCE.cast(n).getGroupName().equalsIgnoreCase(parent));
                if (removed == 0) {
                    throw new LpEditException(who + " does not inherit " + parent + describeContexts(contexts) + ".");
                }
                yield "Removed parent " + parent + " from " + who + describeContexts(contexts);
            }
            case GROUP_META_SET, USER_META_SET -> {
                String key = requireNode(args.get(1));
                String value = args.get(2);
                ImmutableContextSet contexts = parseContexts(args.get(3));
                // Meta is a single value per key per context: replace rather than accumulate,
                // otherwise the editor would stack invisible duplicates on every save.
                clear(holder, contexts, n -> NodeType.META.matches(n)
                        && NodeType.META.cast(n).getMetaKey().equalsIgnoreCase(key));
                holder.data().add(MetaNode.builder(key, value).context(contexts).build());
                yield "Set meta " + key + " = " + value + " on " + who + describeContexts(contexts);
            }
            case GROUP_META_UNSET, USER_META_UNSET -> {
                String key = requireNode(args.get(1));
                ImmutableContextSet contexts = parseContexts(args.get(2));
                int removed = clear(holder, contexts, n -> NodeType.META.matches(n)
                        && NodeType.META.cast(n).getMetaKey().equalsIgnoreCase(key));
                if (removed == 0) {
                    throw new LpEditException(who + " has no meta " + key + describeContexts(contexts) + ".");
                }
                yield "Unset meta " + key + " on " + who + describeContexts(contexts);
            }
            case GROUP_PREFIX_SET, USER_PREFIX_SET ->
                    setChatMeta(holder, ChatMetaType.PREFIX, args, who);
            case GROUP_SUFFIX_SET, USER_SUFFIX_SET ->
                    setChatMeta(holder, ChatMetaType.SUFFIX, args, who);
            case GROUP_PREFIX_UNSET, USER_PREFIX_UNSET ->
                    unsetChatMeta(holder, ChatMetaType.PREFIX, args, who);
            case GROUP_SUFFIX_UNSET, USER_SUFFIX_UNSET ->
                    unsetChatMeta(holder, ChatMetaType.SUFFIX, args, who);
            case GROUP_WEIGHT_SET -> {
                int weight = parseInt(args.get(1), "weight");
                // Weight is a property of the group, not of a context — clearing across all
                // contexts avoids leaving a stale weight behind in a context the editor never shows.
                holder.data().clear(NodeType.WEIGHT::matches);
                if (weight < 0) {
                    yield "Cleared the weight of " + who;
                }
                holder.data().add(WeightNode.builder(weight).build());
                yield "Set the weight of " + who + " to " + weight;
            }
            case GROUP_DISPLAYNAME_SET -> {
                String displayName = args.get(1).trim();
                holder.data().clear(NodeType.DISPLAY_NAME::matches);
                if (displayName.isEmpty()) {
                    yield "Cleared the display name of " + who;
                }
                holder.data().add(DisplayNameNode.builder(displayName).build());
                yield "Set the display name of " + who + " to " + displayName;
            }
            default -> throw new LpEditException("Unsupported operation: " + op.name());
        };
    }

    private static String addParent(PermissionHolder holder, String parent, ImmutableContextSet contexts,
                                    long duration, String who) {
        InheritanceNode.Builder builder = InheritanceNode.builder(parent).context(contexts);
        if (duration > 0) builder = builder.expiry(Duration.ofSeconds(duration));
        DataMutateResult result = holder.data().add(builder.build());
        if (!result.wasSuccessful()) {
            throw new LpEditException(who + " already inherits " + parent + describeContexts(contexts) + ".");
        }
        return "Added parent " + parent + " to " + who + describeContexts(contexts) + describeDuration(duration);
    }

    private static String setChatMeta(PermissionHolder holder, ChatMetaType type, List<String> args, String who) {
        int priority = parseInt(args.get(1), "priority");
        String value = args.get(2);
        ImmutableContextSet contexts = parseContexts(args.get(3));
        if (value.isEmpty()) {
            throw new LpEditException("A " + label(type) + " cannot be empty — use the unset button instead.");
        }
        // Same priority in the same context is one slot; replacing keeps the editor's view
        // ("one row per priority") true to the store.
        clear(holder, contexts, n -> matchesChatMeta(n, type, priority));
        holder.data().add(type.builder(value, priority).context(contexts).build());
        return "Set " + label(type) + " (priority " + priority + ") on " + who + describeContexts(contexts);
    }

    private static String unsetChatMeta(PermissionHolder holder, ChatMetaType type, List<String> args, String who) {
        int priority = parseInt(args.get(1), "priority");
        ImmutableContextSet contexts = parseContexts(args.get(2));
        int removed = clear(holder, contexts, n -> matchesChatMeta(n, type, priority));
        if (removed == 0) {
            throw new LpEditException(who + " has no " + label(type) + " at priority " + priority
                    + describeContexts(contexts) + ".");
        }
        return "Unset " + label(type) + " (priority " + priority + ") on " + who + describeContexts(contexts);
    }

    private static boolean matchesChatMeta(Node node, ChatMetaType type, int priority) {
        if (!NodeType.CHAT_META.matches(node)) return false;
        ChatMetaNode<?, ?> meta = NodeType.CHAT_META.cast(node);
        return meta.getMetaType() == type && meta.getPriority() == priority;
    }

    private static String label(ChatMetaType type) {
        return type == ChatMetaType.PREFIX ? "prefix" : "suffix";
    }

    private static CompletableFuture<String> setPrimaryGroup(LuckPerms api, String uuidText, String groupName) {
        UUID uuid = requireUuid(uuidText);
        String group = requireName(groupName, "group");
        return api.getUserManager().loadUser(uuid).thenCompose(user -> {
            if (user == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("Unknown player: " + uuid));
            }
            DataMutateResult result = user.setPrimaryGroup(group);
            if (!result.wasSuccessful()) {
                // LuckPerms requires the user to already inherit the group; saying so is more
                // useful than the raw FAIL constant.
                return CompletableFuture.<String>failedFuture(new LpEditException(
                        "Cannot set " + group + " as primary group — add it as a parent first."));
            }
            return api.getUserManager().saveUser(user)
                    .thenApply(ignored -> "Primary group of " + user.getFriendlyName() + " is now " + group);
        });
    }

    private static CompletableFuture<String> promoteOrDemote(LuckPerms api, LpEditOp op, List<String> args) {
        UUID uuid = requireUuid(args.get(0));
        String trackName = requireName(args.get(1), "track");
        return api.getTrackManager().loadTrack(trackName).thenCompose(loadedTrack -> {
            Track track = loadedTrack.orElse(null);
            if (track == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("No such track: " + trackName));
            }
            return api.getUserManager().loadUser(uuid).thenCompose(user -> {
                if (user == null) {
                    return CompletableFuture.<String>failedFuture(new LpEditException("Unknown player: " + uuid));
                }
                String summary;
                if (op == LpEditOp.USER_PROMOTE) {
                    PromotionResult result = track.promote(user, ImmutableContextSet.empty());
                    if (!result.wasSuccessful()) {
                        return CompletableFuture.<String>failedFuture(new LpEditException(
                                "Promotion failed: " + result.getStatus().name().toLowerCase(Locale.ROOT)
                                        .replace('_', ' ')));
                    }
                    summary = user.getFriendlyName() + " promoted to " + result.getGroupTo().orElse("?")
                            + " on track " + trackName;
                } else {
                    DemotionResult result = track.demote(user, ImmutableContextSet.empty());
                    if (!result.wasSuccessful()) {
                        return CompletableFuture.<String>failedFuture(new LpEditException(
                                "Demotion failed: " + result.getStatus().name().toLowerCase(Locale.ROOT)
                                        .replace('_', ' ')));
                    }
                    summary = user.getFriendlyName() + " demoted to " + result.getGroupTo().orElse("?")
                            + " on track " + trackName;
                }
                return api.getUserManager().saveUser(user).thenApply(ignored -> summary);
            });
        });
    }

    private static CompletableFuture<String> createTrack(LuckPerms api, String name) {
        String clean = requireName(name, "track");
        return api.getTrackManager().loadTrack(clean).thenCompose(existing -> {
            if (existing.isPresent()) {
                return CompletableFuture.<String>failedFuture(new LpEditException("Track already exists: " + clean));
            }
            return api.getTrackManager().createAndLoadTrack(clean)
                    .thenApply(track -> "Created track " + track.getName());
        });
    }

    private static CompletableFuture<String> deleteTrack(LuckPerms api, String name) {
        String clean = requireName(name, "track");
        return api.getTrackManager().loadTrack(clean).thenCompose(existing -> {
            Track track = existing.orElse(null);
            if (track == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("No such track: " + clean));
            }
            return api.getTrackManager().deleteTrack(track).thenApply(ignored -> "Deleted track " + clean);
        });
    }

    private static CompletableFuture<String> editTrack(LuckPerms api, LpEditOp op, List<String> args) {
        String trackName = requireName(args.get(0), "track");
        String groupName = requireName(args.get(1), "group");
        return api.getTrackManager().loadTrack(trackName).thenCompose(loadedTrack -> {
            Track track = loadedTrack.orElse(null);
            if (track == null) {
                return CompletableFuture.<String>failedFuture(new LpEditException("No such track: " + trackName));
            }
            return api.getGroupManager().loadGroup(groupName).thenCompose(loadedGroup -> {
                Group group = loadedGroup.orElse(null);
                if (group == null) {
                    return CompletableFuture.<String>failedFuture(new LpEditException("No such group: " + groupName));
                }
                DataMutateResult result;
                String summary;
                try {
                    switch (op) {
                        case TRACK_APPEND -> {
                            result = track.appendGroup(group);
                            summary = "Appended " + groupName + " to track " + trackName;
                        }
                        case TRACK_INSERT -> {
                            int index = parseInt(args.get(2), "position");
                            result = track.insertGroup(group, index);
                            summary = "Inserted " + groupName + " at position " + index + " on track " + trackName;
                        }
                        default -> {
                            result = track.removeGroup(group);
                            summary = "Removed " + groupName + " from track " + trackName;
                        }
                    }
                } catch (IndexOutOfBoundsException e) {
                    return CompletableFuture.<String>failedFuture(
                            new LpEditException("Position out of range for track " + trackName + "."));
                } catch (LpEditException e) {
                    return CompletableFuture.<String>failedFuture(e);
                }
                if (!result.wasSuccessful()) {
                    return CompletableFuture.<String>failedFuture(new LpEditException(
                            op == LpEditOp.TRACK_REMOVE
                                    ? "Track " + trackName + " does not contain " + groupName + "."
                                    : "Track " + trackName + " already contains " + groupName + "."));
                }
                return api.getTrackManager().saveTrack(track).thenApply(ignored -> summary);
            });
        });
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Removes every own node in {@code contexts} matching {@code predicate}, returning how many
     * went. LuckPerms' {@code NodeMap.clear} is void, and the editor needs the count to tell
     * "removed" from "there was nothing there" — a distinction that matters when the admin is
     * looking at a stale screen.
     */
    private static int clear(PermissionHolder holder, ImmutableContextSet contexts, Predicate<Node> predicate) {
        List<Node> doomed = holder.getNodes().stream()
                .filter(n -> n.getContexts().equals(contexts))
                .filter(predicate)
                .toList();
        int removed = 0;
        for (Node node : doomed) {
            if (holder.data().remove(node).wasSuccessful()) removed++;
        }
        return removed;
    }

    /** Parses the flattened {@code key=value;key=value} context form used on the wire. */
    public static ImmutableContextSet parseContexts(String flattened) {
        if (flattened == null || flattened.isBlank()) return ImmutableContextSet.empty();
        MutableContextSet set = MutableContextSet.create();
        for (String pair : flattened.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) {
                throw new LpEditException("Malformed context: " + pair + " (expected key=value)");
            }
            set.add(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return set.immutableCopy();
    }

    /** Inverse of {@link #parseContexts}; sorted so the same set always renders identically. */
    public static String formatContexts(ImmutableContextSet contexts) {
        if (contexts.isEmpty()) return "";
        return contexts.toSet().stream()
                .map(context -> context.getKey() + "=" + context.getValue())
                .sorted()
                .reduce((a, b) -> a + ";" + b)
                .orElse("");
    }

    private static String describeContexts(ImmutableContextSet contexts) {
        return contexts.isEmpty() ? "" : " in context " + formatContexts(contexts);
    }

    private static String describeDuration(long seconds) {
        if (seconds <= 0) return "";
        return " until " + Instant.now().plusSeconds(seconds).toString();
    }

    private static String requireName(String raw, String what) {
        String clean = raw == null ? "" : raw.trim();
        if (clean.isEmpty()) {
            throw new LpEditException("A " + what + " name is required.");
        }
        // LuckPerms stores these as identifiers; rejecting whitespace and separators here gives a
        // clear message instead of a storage-layer failure later.
        if (!clean.matches("[A-Za-z0-9_.\\-]{1,36}")) {
            throw new LpEditException("Invalid " + what + " name: " + clean
                    + " (letters, digits, _ . - only, 36 characters max)");
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    private static String requireNode(String raw) {
        String clean = raw == null ? "" : raw.trim();
        if (clean.isEmpty()) {
            throw new LpEditException("A permission node is required.");
        }
        if (clean.indexOf(' ') >= 0) {
            throw new LpEditException("A permission node cannot contain spaces: " + clean);
        }
        return clean;
    }

    private static UUID requireUuid(String raw) {
        UUID uuid = parseUuid(raw);
        if (uuid == null) {
            throw new LpEditException("Malformed player identifier: " + raw);
        }
        return uuid;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int parseInt(String raw, String what) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new LpEditException("Invalid " + what + ": " + raw);
        }
    }

    private static long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            long seconds = Long.parseLong(raw.trim());
            return Math.max(0L, seconds);
        } catch (NumberFormatException e) {
            throw new LpEditException("Invalid duration (seconds): " + raw);
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : message;
    }

    /**
     * A rejection whose message is meant for the admin's screen. Unchecked so it can be thrown
     * from inside the {@code Consumer}-shaped node mutations without widening their signatures.
     */
    public static final class LpEditException extends RuntimeException {

        public LpEditException(String message) {
            super(message);
        }

        // The message is the whole point of this exception and it never carries a cause worth
        // walking; skipping the stack trace keeps a rejected click from costing a fill-in.
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }
}
