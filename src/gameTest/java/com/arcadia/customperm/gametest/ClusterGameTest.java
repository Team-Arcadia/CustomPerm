/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.admin.AdminResult;
import com.arcadia.customperm.admin.AliasAdmin;
import com.arcadia.customperm.admin.GradeAdmin;
import com.arcadia.customperm.admin.RateLimitAdmin;
import com.arcadia.customperm.cluster.AliasesCodec;
import com.arcadia.customperm.cluster.Cluster;
import com.arcadia.customperm.cluster.ClusterGate;
import com.arcadia.customperm.cluster.ClusterStore;
import com.arcadia.customperm.cluster.CommandsCodec;
import com.arcadia.customperm.cluster.GradesCodec;
import com.arcadia.customperm.cluster.MemoryStore;
import com.arcadia.customperm.cluster.PartCodec;
import com.arcadia.customperm.cluster.PartSync;
import com.arcadia.customperm.cluster.RateLimitsCodec;
import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.perm.PermissionService;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cluster mode as far as a single GameTest server can see it: switched on without what it needs, the server
 * keeps running alone and says why; switched off again, the alert goes. Arcadia Lib is never on the GameTest
 * classpath, so this also proves CustomPerm starts and decides without it.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class ClusterGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "cluster")
    public static void clusterWithoutWhatItNeedsRunsAloneAndSaysWhy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var settings = CustomPerm.configManager.getSettings().cluster;
        if (Cluster.state() != ClusterGate.State.OFF) fail("Cluster mode must be off by default, got " + Cluster.state());
        ClusterGate.State expected = CustomPerm.isLuckPermsActive() ? ClusterGate.State.LUCKPERMS
                : server.isDedicatedServer() ? ClusterGate.State.NO_ARCADIA_LIB : ClusterGate.State.SINGLEPLAYER;
        try {
            settings.enabled = true;
            Cluster.onServerStarted(new ServerStartedEvent(server));
            if (Cluster.state() != expected) fail("Expected " + expected + ", got " + Cluster.state());
            if (Cluster.serverName() != null) fail("A server running alone has no cluster name.");
            boolean alert = AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE);
            if (alert == (expected == ClusterGate.State.LUCKPERMS)) {
                fail("The cluster alert must be raised unless LuckPerms decides, alert=" + alert + " state=" + expected);
            }
        } finally {
            settings.enabled = false;
            Cluster.onServerStarted(new ServerStartedEvent(server));
        }
        if (Cluster.state() != ClusterGate.State.OFF) fail("Switched off again, cluster mode must be OFF.");
        if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) fail("Switched off, the cluster alert must go.");
        helper.succeed();
    }

    // ------------------------------------------------------------------ two servers, one store

    private static final GradesCodec CODEC = new GradesCodec();

    /**
     * The second server of these tests: a configuration of its own, kept in step with the same store as the
     * GameTest server, which plays the first.
     */
    private static final class OtherServer implements PartSync.Host<GradesConfig> {
        final GradesConfig config = CODEC.empty();
        final PartSync<GradesConfig> sync;

        OtherServer(ClusterStore store) throws ClusterStore.StoreException {
            sync = new PartSync<>(CODEC, store, "other", this);
            sync.start();
        }

        @Override public GradesConfig current() { return config; }
        @Override public void changed(GradesConfig c, Set<String> holders) {}
        @Override public String label(String holder) { return holder; }

        void poll() throws ClusterStore.StoreException {
            sync.apply(sync.fetch());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Runs {@code body} with this server in a cluster over {@code store}, then puts its grades, commands, aliases
     * and rate limits back as they were.
     */
    private static void inCluster(MinecraftServer server, MemoryStore store, ThrowingRunnable body) throws Exception {
        var config = CustomPerm.configManager;
        Map<String, String> grades = CODEC.split(config.getGrades());
        Map<String, String> commands = new CommandsCodec().split(config.getCommands());
        Map<String, String> aliases = new AliasesCodec().split(config.getAliases());
        Map<String, String> limits = new RateLimitsCodec().split(config.getRateLimits());
        try {
            Cluster.attach(store, "gametest", server);
            body.run();
        } finally {
            Cluster.detach();
            restore(CODEC, config.getGrades(), grades);
            restore(new CommandsCodec(), config.getCommands(), commands);
            restore(new AliasesCodec(), config.getAliases(), aliases);
            restore(new RateLimitsCodec(), config.getRateLimits(), limits);
            PermissionService.get().onConfigReload(config.getSnapshot());
            CustomPerm.treeReloader.onConfigReload(config.getSnapshot(), server);
            config.save();
            if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) {
                AdminNotifier.clear(AdminAlerts.Key.CLUSTER_UNAVAILABLE, "cluster test finished.");
            }
        }
    }

    private static <T> void restore(PartCodec<T> codec, T live, Map<String, String> saved) {
        for (String holder : new ArrayList<>(codec.split(live).keySet())) codec.patch(live, holder, null);
        saved.forEach((holder, text) -> codec.patch(live, holder, text));
        codec.afterPatch(live);
    }

    /** Exposed commands, aliases and rate limits travel like grades, each on its own row. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_parts")
    public static void commandsAliasesAndRateLimitsAreSharedToo(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        inCluster(server, store, () -> {
            CommandsConfig otherCommands = new CommandsCodec().empty();
            PartSync<CommandsConfig> commands = new PartSync<>(new CommandsCodec(), store, "other", host(otherCommands));
            commands.start();
            AliasesConfig otherAliases = new AliasesCodec().empty();
            PartSync<AliasesConfig> aliases = new PartSync<>(new AliasesCodec(), store, "other", host(otherAliases));
            aliases.start();
            RateLimitsConfig otherLimits = new RateLimitsCodec().empty();
            PartSync<RateLimitsConfig> limits = new PartSync<>(new RateLimitsCodec(), store, "other", host(otherLimits));
            limits.start();

            otherCommands.grantedCommands.add("seed");
            if (commands.publish() != null) fail("The other server's exposure was refused.");
            Cluster.pollNow();
            if (!CustomPerm.configManager.getCommands().grantedCommands.contains("seed")) {
                fail("A command exposed on the other server must be exposed here.");
            }

            if (!AliasAdmin.create(server, "cp_cl_alias", "say cluster").success()) fail("Could not create the alias here.");
            if (!RateLimitAdmin.set("seed", 3, 60).success()) fail("Could not set the rate limit here.");
            aliases.apply(aliases.fetch());
            limits.apply(limits.fetch());
            if (!List.of("say cluster").equals(otherAliases.aliases.get("cp_cl_alias"))) {
                fail("An alias created here must reach the other server.");
            }
            var rule = otherLimits.rules.get("seed");
            if (rule == null || rule.maxExecutions != 3 || rule.windowSeconds != 60) {
                fail("A rate limit set here must reach the other server.");
            }
        });
        helper.succeed();
    }

    private static <T> PartSync.Host<T> host(T config) {
        return new PartSync.Host<>() {
            @Override public T current() { return config; }
            @Override public void changed(T c, Set<String> holders) {}
            @Override public String label(String holder) { return holder; }
        };
    }

    /** A grade and an assignment made on the other server give the player the node here, and the reverse. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_propagate")
    public static void aChangeOnOneServerAppliesOnTheOther(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_prop", 0)) {
            String uuid = player.player().getUUID().toString();
            inCluster(server, store, () -> {
                OtherServer other = new OtherServer(store);
                GradesConfig.Grade grade = new GradesConfig.Grade();
                grade.name = "cp_cl_vip";
                grade.permissions.add("cp.cluster.fly");
                other.config.grades.put(grade.name, grade);
                other.config.userGrades.put(uuid, new ArrayList<>(List.of(grade.name)));
                if (other.sync.publish() != null) fail("The other server's write was refused.");

                if (PermissionService.get().hasGrantedNode(player.source(), "cp.cluster.fly")) {
                    fail("Before polling, this server cannot know yet.");
                }
                Cluster.pollNow();
                if (!PermissionService.get().hasGrantedNode(player.source(), "cp.cluster.fly")) {
                    fail("After polling, the grade made on the other server must grant its node here.");
                }

                AdminResult added = GradeAdmin.addNode(server, "cp_cl_vip", "cp.cluster.walk", false);
                if (!added.success()) fail("Adding a node here failed: " + added.message());
                other.poll();
                if (!other.config.grades.get("cp_cl_vip").permissions.contains("cp.cluster.walk")) {
                    fail("A node added here must reach the other server.");
                }

                AdminResult deleted = GradeAdmin.delete(server, "cp_cl_vip");
                if (!deleted.success()) fail("Deleting here failed: " + deleted.message());
                other.poll();
                if (other.config.grades.containsKey("cp_cl_vip")) fail("A grade deleted here must go on the other server.");
                if (other.config.userGrades.containsKey(uuid)) {
                    fail("Deleting a grade unassigns it everywhere, the other server included.");
                }
            });
        }
        helper.succeed();
    }

    /** Both servers change one grade from the same version: the one that writes second is refused and shown the first. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_conflict")
    public static void theSecondOfTwoChangesToOneGradeIsRefused(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        inCluster(server, store, () -> {
            if (!GradeAdmin.create("cp_cl_same").success()) fail("Could not create the grade.");
            OtherServer other = new OtherServer(store);
            other.config.grades.get("cp_cl_same").permissions.add("cp.cluster.other");
            if (other.sync.publish() != null) fail("The other server wrote first and must be accepted.");

            AdminResult here = GradeAdmin.addNode(server, "cp_cl_same", "cp.cluster.here", false);
            if (here.success()) fail("Written second from an old version, the change must be refused: " + here.message());
            if (!here.message().contains("changed on other")) fail("The refusal must name the other server: " + here.message());
            var grade = CustomPerm.configManager.getGrades().grades.get("cp_cl_same");
            if (grade.permissions.contains("cp.cluster.here")) fail("The refused change must be undone here.");
            if (!grade.permissions.contains("cp.cluster.other")) fail("This server must now show the other server's change.");

            AdminResult retry = GradeAdmin.addNode(server, "cp_cl_same", "cp.cluster.here", false);
            if (!retry.success()) fail("Retried on the current version, the change must go through: " + retry.message());
        });
        helper.succeed();
    }

    /** Store unreachable: changes are refused and undone, rights stay as last read, the alert says so and then goes. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_outage")
    public static void anUnreachableStoreRefusesChangesAndKeepsTheLastRights(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        inCluster(server, store, () -> {
            if (!GradeAdmin.create("cp_cl_kept").success()) fail("Could not create the grade.");
            store.setDown(true);
            AdminResult created = GradeAdmin.create("cp_cl_lost");
            if (created.success()) fail("With the store down, a change must be refused.");
            if (CustomPerm.configManager.getGrades().grades.containsKey("cp_cl_lost")) fail("The refused grade must not exist.");
            if (!CustomPerm.configManager.getGrades().grades.containsKey("cp_cl_kept")) fail("What was read before stays.");
            if (!AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) fail("Admins must be told the store is down.");

            store.setDown(false);
            Cluster.pollNow();
            if (AdminNotifier.isActive(AdminAlerts.Key.CLUSTER_UNAVAILABLE)) fail("The alert must go once the store answers.");
            if (!GradeAdmin.create("cp_cl_lost").success()) fail("Back up, the change must go through.");
        });
        helper.succeed();
    }

    /** Joining a store another server already filled: the store wins over this server's grades. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_adopt")
    public static void joiningAFilledStoreAdoptsIt(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        OtherServer first = new OtherServer(store);
        GradesConfig.Grade grade = new GradesConfig.Grade();
        grade.name = "cp_cl_network";
        first.config.grades.put(grade.name, grade);
        if (first.sync.publish() != null) fail("The first server's write was refused.");
        if (!GradeAdmin.create("cp_cl_local_only").success()) fail("Could not create the local grade.");

        inCluster(server, store, () -> {
            var grades = CustomPerm.configManager.getGrades().grades;
            if (!grades.containsKey("cp_cl_network")) fail("The store's grade must be adopted.");
            if (grades.containsKey("cp_cl_local_only")) fail("A grade the store does not hold must go: the store wins.");
        });
        GradeAdmin.delete(server, "cp_cl_local_only");
        helper.succeed();
    }

    private static void fail(String message) {
        throw new GameTestAssertException(message);
    }
}
