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
import com.arcadia.customperm.config.ConfigSnapshot;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.perm.AdminAccess;
import com.arcadia.customperm.perm.Tristate;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Replaces every top-level command node with a wrapped clone whose {@code requires}
 * predicate adds CustomPerm's permission check on top of the original.
 *
 * Why clone instead of mutating the existing node's {@code requirement} field?
 * Brigadier's {@code requirement} is {@code private final}, and writing to it via
 * reflection succeeds without exception but the JVM/JIT may keep the original value
 * (final field optimisation). By BUILDING a new node whose final field is set at
 * construction time, no JIT shenanigans can interfere.
 *
 * To install the wrapped node, we mutate the parent's {@code children} / {@code literals}
 * / {@code arguments} maps via reflection. The fields are final, but we read them
 * (no field-write involved) and modify the contained Map (which is mutable).
 *
 * The wrapper is dynamic — at evaluation time it consults
 * {@link com.arcadia.customperm.config.CommandsConfig#grantedCommands}. The admin can
 * add/remove commands at runtime without re-wrapping.
 *
 * <p><strong>LuckPerms coexistence.</strong> LuckPerms' NeoForge {@code BrigadierInjector}
 * overwrites every command node's {@code requirement} (reflectively) AFTER this event, which
 * would drop CustomPerm's clone-time predicate. To let {@code customperm.command.<name>} work
 * alongside LuckPerms, {@link #reassertExposedCommands} re-applies an {@link ExposureGate} on the
 * live requirement on the next server tick (and on config changes) — running last, and additively
 * (it only opens access for exposed+granted sources, deferring to LuckPerms/vanilla otherwise).</p>
 */
public class CommandTreeRewriter implements ICommandTreeReloader {

    private static final Field CHILDREN_FIELD;
    private static final Field LITERALS_FIELD;
    private static final Field ARGUMENTS_FIELD;
    private static final Field REQUIREMENT_FIELD;
    private static final Map<String, CommandNode<CommandSourceStack>> ORIGINAL_ROOTS = new HashMap<>();
    private static final Set<CommandNode<CommandSourceStack>> WRAPPED_NODES =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /** Set when the exposure gate needs re-applying on the next server tick (after LuckPerms injects). */
    private static volatile boolean reassertPending = false;

    static {
        try {
            CHILDREN_FIELD = CommandNode.class.getDeclaredField("children");
            CHILDREN_FIELD.setAccessible(true);
            LITERALS_FIELD = CommandNode.class.getDeclaredField("literals");
            LITERALS_FIELD.setAccessible(true);
            ARGUMENTS_FIELD = CommandNode.class.getDeclaredField("arguments");
            ARGUMENTS_FIELD.setAccessible(true);
            REQUIREMENT_FIELD = CommandNode.class.getDeclaredField("requirement");
            REQUIREMENT_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Brigadier API changed: CommandNode internal maps not found", e);
        }
    }

    /**
     * Called after every successful config hot-reload.
     *
     * <p>The predicates built by {@code wrapRecursive} read {@code grantedCommands} at evaluation
     * time, so a reload needs no structural re-wrapping. The {@code ClientboundCommandsPacket} is
     * already pushed by the reload handler ({@code CustomPermCommand.reload}, step 3) through
     * {@code server.execute()} (INVARIANT-501): do not repeat it here, it would send twice.</p>
     *
     * @param snapshot the new config snapshot, already applied in ConfigManager
     * @param server   the Minecraft server, null when none is running
     */
    @Override
    public void onConfigReload(ConfigSnapshot snapshot, MinecraftServer server) {
        if (server != null) {
            // Apply the aliases.json changes to the live dispatcher too: additions, removals
            // (restoring the shadowed node) and edited steps. Without this, /customperm reload
            // only touches the in-memory config and an alias keeps running the steps its
            // closure captured at registration.
            AliasManager.applyConfig(server.getCommands().getDispatcher());
        }
        // repair runs AFTER applyConfig: a node restored by an alias removal becomes eligible
        // for wrapping again once it is exposed.
        int repaired = repair(server);
        // Re-apply CustomPerm's check on top of any LuckPerms injection.
        reassertExposedCommands(server);
        CustomPerm.LOGGER.info("[CustomPerm] CommandTreeRewriter.onConfigReload — repaired {} command wrapper(s).", repaired);
    }

    // LOWEST: the best chance of running after the other mods' RegisterCommandsEvent handlers,
    // so their commands are already in the dispatcher when the wrapping happens.
    // (Second safety net: repair() on ServerStartedEvent.)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // Every RegisterCommandsEvent brings a new dispatcher (a vanilla /reload, a restart in
        // the same JVM): the static state points at the old tree and must be cleared, or it
        // leaks memory and AliasManager restores stale nodes.
        clearServerState();
        AliasManager.clearServerState();

        CustomPermCommand.register(dispatcher);
        NickCommand.register(dispatcher);
        AliasManager.registerAll(dispatcher);

        int wrapped = wrapUnwrappedRoots(dispatcher);
        CustomPerm.LOGGER.info("[CustomPerm] Wrapped {} top-level command(s) for permission gating.", wrapped);

        // LuckPerms' BrigadierInjector overwrites every command's requirement AFTER this event.
        // Defer the CustomPerm re-assertion to a server tick so it runs last and wins.
        reassertPending = true;
    }

    /**
     * Server-thread tick hook: re-applies CustomPerm's exposure gate once whenever it has been
     * flagged pending (boot, /reload, command tree rebuild). This runs AFTER LuckPerms'
     * BrigadierInjector has replaced command requirements, so CustomPerm's grant wins.
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!reassertPending) return;
        reassertPending = false;
        int n = reassertExposedCommands(event.getServer());
        if (n > 0) {
            CustomPerm.LOGGER.info("[CustomPerm] Re-asserted exposure over other permission handlers for {} command(s).", n);
            event.getServer().getPlayerList().getPlayers()
                .forEach(p -> event.getServer().getCommands().sendCommands(p));
        }
    }

    /**
     * Clears the static state tied to the current dispatcher; see onRegisterCommands.
     * Rate-limit history is deliberately not cleared here: a vanilla /reload rebuilds the dispatcher,
     * and wiping the counters with it handed every player a fresh quota. RateLimitPersistence clears
     * it on server stop, after saving it.
     */
    public static void clearServerState() {
        ORIGINAL_ROOTS.clear();
        WRAPPED_NODES.clear();
    }

    public static int repair(MinecraftServer server) {
        if (server == null) return 0;
        return wrapUnwrappedRoots(server.getCommands().getDispatcher());
    }

    /**
     * Re-applies CustomPerm's exposure gate directly on each exposed command's LIVE requirement,
     * by reflectively composing {@link ExposureGate} on top of whatever predicate currently owns
     * the node. This is what lets {@code customperm.command.<name>} work even when LuckPerms'
     * BrigadierInjector has replaced the requirement with its own — CustomPerm re-wraps last.
     *
     * <p>The composition is <strong>additive</strong>: if CustomPerm doesn't grant the source, the
     * gate defers to the delegate (LuckPerms' / vanilla's check), so no existing gating is broken.
     * Commands no longer exposed have their delegate restored.</p>
     *
     * @return number of nodes whose requirement was changed.
     */
    public static int reassertExposedCommands(MinecraftServer server) {
        if (server == null || !CustomPerm.isDirectCommandExposureEnabled()) return 0;
        var commands = CustomPerm.configManager.getCommands();
        String here = com.arcadia.customperm.cluster.Cluster.identity();
        CommandNode<CommandSourceStack> root = server.getCommands().getDispatcher().getRoot();

        int changed = 0;
        for (CommandNode<CommandSourceStack> node : root.getChildren()) {
            String name = node.getName();
            if ("customperm".equals(name)) continue;
            // LuckPerms injects a requirement on EVERY node (root + arguments/children), so the
            // gate must cover the whole subtree — otherwise the root command is reachable but its
            // sub-arguments (e.g. /gamemode <mode>) stay gated by LuckPerms.
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited = new IdentityHashMap<>();
            if (commands.exposedHere(name, here)) {
                changed += applyGateRecursive(node, name, visited);
            } else {
                changed += restoreGateRecursive(node, visited);
            }
        }
        return changed;
    }

    /** Reflectively composes an {@link ExposureGate} onto {@code node} and every descendant. */
    private static int applyGateRecursive(CommandNode<CommandSourceStack> node, String rootName,
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited) {
        if (visited.put(node, Boolean.TRUE) != null) return 0;  // cycle/redirect guard
        int changed = 0;
        try {
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> current = (Predicate<CommandSourceStack>) REQUIREMENT_FIELD.get(node);
            if (!(current instanceof ExposureGate)) {
                REQUIREMENT_FIELD.set(node, new ExposureGate(rootName, current));
                changed++;
            }
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not re-assert exposure for /{} (node '{}')", rootName, node.getName(), t);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            changed += applyGateRecursive(child, rootName, visited);
        }
        return changed;
    }

    /** Restores the delegate on {@code node} and every descendant still carrying our gate. */
    private static int restoreGateRecursive(CommandNode<CommandSourceStack> node,
            IdentityHashMap<CommandNode<CommandSourceStack>, Boolean> visited) {
        if (visited.put(node, Boolean.TRUE) != null) return 0;
        int changed = 0;
        try {
            @SuppressWarnings("unchecked")
            Predicate<CommandSourceStack> current = (Predicate<CommandSourceStack>) REQUIREMENT_FIELD.get(node);
            if (current instanceof ExposureGate gate) {
                REQUIREMENT_FIELD.set(node, gate.delegate());
                changed++;
            }
        } catch (Throwable t) {
            CustomPerm.LOGGER.warn("[CustomPerm] Could not restore requirement for node '{}'", node.getName(), t);
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            changed += restoreGateRecursive(child, visited);
        }
        return changed;
    }

    /**
     * Requirement predicate that grants an exposed command when CustomPerm authorises the source,
     * and otherwise defers to the {@code delegate} (the predicate previously on the node — e.g.
     * LuckPerms' injected requirement, or the vanilla op-level check). A concrete class (not a
     * lambda) so {@link #reassertExposedCommands} can recognise its own gate and stay idempotent.
     */
    record ExposureGate(String rootName, Predicate<CommandSourceStack> delegate)
            implements Predicate<CommandSourceStack> {
        @Override
        public boolean test(CommandSourceStack source) {
            // A gate left on a node after its command stopped being exposed must not keep granting it.
            // reassertExposedCommands restores the delegate on remove and reload, but access control
            // should not depend on every removal path remembering to do so.
            if (!CustomPerm.configManager.getCommands().exposedHere(rootName, com.arcadia.customperm.cluster.Cluster.identity())) {
                return delegate == null || delegate.test(source);
            }
            // Additive for a player without any value: the delegate (LuckPerms' own check) still decides.
            // An explicit false in LuckPerms now refuses the command, operators included, instead of
            // letting op level 2 through before LuckPerms is asked.
            return decide(source, rootName, true, () -> delegate == null || delegate.test(source), true);
        }
    }

    /**
     * Whether {@code rootName} is exposed for {@code source} on this server. It is when its list of servers names
     * this one. When the command is exposed only on other members, a grade or a player still has the last word
     * about this server: an entry of theirs limited to {@code server=<this one>}, allowing or denying, makes the
     * node decide here for them, as on an exposed command. A node held everywhere does not, so the command's list
     * keeps its meaning for the usual grade.
     */
    static boolean exposedFor(CommandSourceStack source, String rootName) {
        var commands = CustomPerm.configManager.getCommands();
        String here = com.arcadia.customperm.cluster.Cluster.identity();
        if (commands.exposedHere(rootName, here)) return true;
        if (here == null || !commands.grantedCommands.contains(rootName)) return false;
        return com.arcadia.customperm.perm.PermissionService.get().checkServerScoped(source, commandNode(rootName)) != Tristate.UNSET;
    }

    /** Node read for a root command, exposed or gated by {@code gateAllCommands}. */
    public static String commandNode(String rootName) {
        return "customperm.command." + rootName;
    }

    /**
     * CustomPerm's decision for a root command it gates, shared by the cloned requirement and
     * {@link ExposureGate}.
     *
     * <ul>
     *   <li>DENY: refused, operators included.</li>
     *   <li>ALLOW: granted, on top of the original requirement when the command keeps it.</li>
     *   <li>UNSET, exposed command: operators keep it; other players get the original requirement when
     *       {@code additive}, nothing otherwise.</li>
     *   <li>UNSET, command gated only by {@code gateAllCommands}: the original requirement, unchanged.</li>
     * </ul>
     * Non-player sources are always UNSET, so the console and command blocks keep their vanilla access.
     */
    static boolean decide(CommandSourceStack source, String rootName, boolean exposed,
                          java.util.function.BooleanSupplier original, boolean additive) {
        Tristate value = AdminAccess.explicit(source, commandNode(rootName));
        boolean keepOriginal = exposed && CustomPerm.configManager.getCommands().shouldPreserveOriginalRequires(rootName);
        return switch (value) {
            case DENY -> false;
            case ALLOW -> !keepOriginal || original.getAsBoolean();
            case UNSET -> {
                if (!exposed) yield original.getAsBoolean();
                if (source.hasPermission(2)) yield !keepOriginal || original.getAsBoolean();
                yield additive && original.getAsBoolean();
            }
        };
    }

    private static int wrapUnwrappedRoots(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!CustomPerm.isDirectCommandExposureEnabled()) {
            return 0;
        }

        Set<String> skipRoots = new HashSet<>();
        skipRoots.add("customperm");
        // Gated by customperm.nick itself: exposing it would hand it out without that node.
        if (NickCommand.registered()) skipRoots.add(NickCommand.ROOT);
        // Every alias holding a node here keeps its own gating, including one opened here only by a server= entry.
        CustomPerm.configManager.getAliases().aliases.keySet().stream().filter(AliasManager::registered).forEach(skipRoots::add);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot();
        List<CommandNode<CommandSourceStack>> originals = new ArrayList<>(root.getChildren());

        // Unwrapped command roots a redirect may be re-pointed at (see resolveRedirect). Aliases
        // and /customperm keep their own gating and are never cloned under another name.
        Set<CommandNode<CommandSourceStack>> redirectTargets = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CommandNode<CommandSourceStack> original : originals) {
            if (!skipRoots.contains(original.getName()) && !WRAPPED_NODES.contains(original)) {
                redirectTargets.add(original);
            }
        }

        int wrapped = 0;
        for (CommandNode<CommandSourceStack> original : originals) {
            String name = original.getName();
            if (skipRoots.contains(name)) continue;
            if (WRAPPED_NODES.contains(original)) continue;
            try {
                ORIGINAL_ROOTS.put(name, original);
                IdentityHashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> visited = new IdentityHashMap<>();
                Set<CommandNode<CommandSourceStack>> inProgress = Collections.newSetFromMap(new IdentityHashMap<>());
                CommandNode<CommandSourceStack> wrappedRoot =
                        wrapRecursive(original, name, visited, inProgress, redirectTargets);
                if (wrappedRoot == original) continue;  // unknown type — the tick-time re-assert still gates it
                replaceInParent(root, original, wrappedRoot);
                wrapped++;
            } catch (Throwable t) {
                CustomPerm.LOGGER.warn("[CustomPerm] Failed to wrap /{}", name, t);
            }
        }

        return wrapped;
    }

    static boolean executeOriginalCommand(CommandSourceStack source, String command) throws Exception {
        String rootName = commandRoot(command);
        if (rootName.isEmpty()) return false;

        CommandNode<CommandSourceStack> original = ORIGINAL_ROOTS.get(rootName);
        if (original == null) return false;

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(original);
        dispatcher.execute(command, source);
        return true;
    }

    private static String commandRoot(String command) {
        String trimmed = command == null ? "" : command.strip();
        if (trimmed.isEmpty()) return "";

        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        return trimmed.substring(0, end);
    }

    /**
     * Picks the redirect target for a wrapped clone.
     *
     * <p>A shortcut such as {@code /tp} is a literal that only redirects to another command root
     * ({@code teleport}). Keeping the original pointer sends everything typed after {@code /tp}
     * through the ORIGINAL, unwrapped {@code teleport} subtree: no exposure check, no rate limit,
     * under either name. When the target is an unwrapped command root, it is therefore cloned
     * under {@code rootName}, so {@code /tp} is governed by the rules of {@code tp} and
     * {@code /teleport} by its own.</p>
     *
     * <p>Other targets keep their pointer: the dispatcher root ({@code execute run}), nodes that
     * are not roots, and roots wrapped by an earlier pass. A target already cloned in this pass is
     * reused, which covers {@code /execute as ...} redirecting to {@code execute} itself; a target
     * still being cloned (a redirect cycle across roots) keeps the original pointer instead of
     * recursing forever.</p>
     */
    private static CommandNode<CommandSourceStack> resolveRedirect(
            CommandNode<CommandSourceStack> redirect,
            String rootName,
            IdentityHashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> visited,
            Set<CommandNode<CommandSourceStack>> inProgress,
            Set<CommandNode<CommandSourceStack>> redirectTargets) {
        if (redirect == null) return null;
        CommandNode<CommandSourceStack> done = visited.get(redirect);
        if (done != null) return done;
        if (!redirectTargets.contains(redirect) || inProgress.contains(redirect)) return redirect;
        return wrapRecursive(redirect, rootName, visited, inProgress, redirectTargets);
    }

    private static CommandNode<CommandSourceStack> wrapRecursive(
            CommandNode<CommandSourceStack> original,
            String rootName,
            IdentityHashMap<CommandNode<CommandSourceStack>, CommandNode<CommandSourceStack>> visited,
            Set<CommandNode<CommandSourceStack>> inProgress,
            Set<CommandNode<CommandSourceStack>> redirectTargets) {

        if (visited.containsKey(original)) return visited.get(original);
        if (!(original instanceof LiteralCommandNode<?>) && !(original instanceof ArgumentCommandNode<?, ?>)) {
            return original;  // unknown node type — leave alone
        }

        Predicate<CommandSourceStack> origReq = original.getRequirement();
        Predicate<CommandSourceStack> wrappedReq = source -> {
            java.util.function.BooleanSupplier originalAllows = () -> origReq == null || origReq.test(source);
            if (!CustomPerm.isDirectCommandExposureEnabled()) {
                return originalAllows.getAsBoolean();
            }
            // Exposed here, or opened here for this player by an entry of theirs naming this server: a command
            // limited to other members keeps its original requirement otherwise.
            boolean exposed = exposedFor(source, rootName);
            if (!exposed && !CustomPerm.gatesAllCommands()) {
                return originalAllows.getAsBoolean();
            }
            return decide(source, rootName, exposed, originalAllows, false);
        };

        // The redirect is a constructor argument, so it has to be resolved before this node exists.
        inProgress.add(original);
        CommandNode<CommandSourceStack> redirect;
        try {
            redirect = resolveRedirect(original.getRedirect(), rootName, visited, inProgress, redirectTargets);
        } finally {
            inProgress.remove(original);
        }

        CommandNode<CommandSourceStack> wrapped;
        if (original instanceof LiteralCommandNode<CommandSourceStack> literal) {
            wrapped = new LiteralCommandNode<>(
                literal.getLiteral(),
                wrapCommand(rootName, literal.getCommand()),
                wrappedReq,
                redirect,
                literal.getRedirectModifier(),
                literal.isFork()
            );
        } else {
            wrapped = cloneArgument(original, wrappedReq, rootName, redirect);
        }

        visited.put(original, wrapped);
        WRAPPED_NODES.add(wrapped);

        for (CommandNode<CommandSourceStack> child : original.getChildren()) {
            CommandNode<CommandSourceStack> wrappedChild =
                    wrapRecursive(child, rootName, visited, inProgress, redirectTargets);
            if (wrappedChild == child
                    && !(child instanceof LiteralCommandNode<?>)
                    && !(child instanceof ArgumentCommandNode<?, ?>)) {
                CustomPerm.LOGGER.warn("[CustomPerm] Skipping unknown child node type while wrapping /{} {}: {}",
                        rootName, child.getName(), child.getClass().getName());
                continue;
            }
            wrapped.addChild(wrappedChild);
        }

        return wrapped;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<CommandSourceStack> cloneArgument(
            CommandNode<CommandSourceStack> original,
            Predicate<CommandSourceStack> wrappedReq,
            String rootName,
            CommandNode<CommandSourceStack> redirect) {
        ArgumentCommandNode argNode = (ArgumentCommandNode) original;
        return new ArgumentCommandNode<>(
            argNode.getName(),
            argNode.getType(),
            wrapCommand(rootName, argNode.getCommand()),
            wrappedReq,
            redirect,
            argNode.getRedirectModifier(),
            argNode.isFork(),
            argNode.getCustomSuggestions()
        );
    }

    /**
     * Wraps a leaf node's execution callback with the rate-limit check for {@code rootName}
     * (see RateLimitsConfig). Applied uniformly to every executable node under the root, so
     * `/observable foo` and `/observable bar` share a single per-player counter keyed by
     * the root command name. Console/command-block sources have no stable UUID and are left
     * unlimited.
     */
    private static Command<CommandSourceStack> wrapCommand(String rootName, Command<CommandSourceStack> original) {
        if (original == null) return null;
        // Vanilla runs a CustomCommandExecutor (/function, /return) through its own entry point and makes
        // run(ctx) throw, so the wrapper must stay one or those commands fail on every call.
        if (original instanceof CustomCommandExecutor<?> custom) {
            @SuppressWarnings("unchecked")
            CustomCommandExecutor<CommandSourceStack> executor = (CustomCommandExecutor<CommandSourceStack>) custom;
            return (CustomCommandExecutor.CommandAdapter<CommandSourceStack>) (source, chain, modifiers, control) -> {
                if (!RateLimits.acquire(source, rootName)) {
                    source.callback().onFailure();
                    return;
                }
                executor.run(source, chain, modifiers, control);
            };
        }
        return ctx -> RateLimits.acquire(ctx.getSource(), rootName) ? original.run(ctx) : 0;
    }

    @SuppressWarnings("unchecked")
    private static void replaceInParent(
            CommandNode<CommandSourceStack> parent,
            CommandNode<CommandSourceStack> original,
            CommandNode<CommandSourceStack> wrapped) throws IllegalAccessException {

        Map<String, CommandNode<CommandSourceStack>> children =
            (Map<String, CommandNode<CommandSourceStack>>) CHILDREN_FIELD.get(parent);
        Map<String, LiteralCommandNode<CommandSourceStack>> literals =
            (Map<String, LiteralCommandNode<CommandSourceStack>>) LITERALS_FIELD.get(parent);
        Map<String, ArgumentCommandNode<CommandSourceStack, ?>> arguments =
            (Map<String, ArgumentCommandNode<CommandSourceStack, ?>>) ARGUMENTS_FIELD.get(parent);

        String name = original.getName();
        children.put(name, wrapped);
        if (wrapped instanceof LiteralCommandNode<CommandSourceStack> lit) {
            literals.put(name, lit);
            arguments.remove(name);
        } else if (wrapped instanceof ArgumentCommandNode<CommandSourceStack, ?> arg) {
            arguments.put(name, arg);
            literals.remove(name);
        }
    }
}
