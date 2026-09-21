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
import com.arcadia.customperm.admin.CommandAdmin;
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
import com.arcadia.customperm.command.RateLimiter;
import com.arcadia.customperm.cluster.PartSync;
import com.arcadia.customperm.cluster.RateLimitsCodec;
import com.arcadia.customperm.config.AliasesConfig;
import com.arcadia.customperm.config.CommandsConfig;
import com.arcadia.customperm.config.GradesConfig;
import com.arcadia.customperm.config.RateLimitsConfig;
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.ServerCommands;
import com.arcadia.customperm.log.ActivityLog;
import com.arcadia.customperm.log.LogEntry;
import com.arcadia.customperm.log.LogKind;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.notify.AdminAlerts;
import com.arcadia.customperm.notify.AdminNotifier;
import com.arcadia.customperm.network.gui.GuiAccess;
import com.arcadia.customperm.network.gui.GuiArea;
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
    /**
     * An administration node limited to another member opens nothing here. This is what lets a server join a
     * cluster to read its real grades without being able to change them: grant the manage nodes with
     * {@code server=<production names>} and an administrator connected elsewhere holds nothing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_propagate")
    public static void anAdminNodeLimitedToAnotherServerOpensNothingHere(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try (TestPlayer admin = TestPlayer.reader(helper.getLevel(), "cp_cl_sandbox", 4)) {
            inCluster(server, new MemoryStore(), () -> {
                if (!GradeAdmin.create("cp_cl_sandbox").success()) fail("Could not create the grade.");
                if (!GradeAdmin.assign(server, admin.player().getGameProfile(), "cp_cl_sandbox").success()) {
                    fail("Could not assign the grade.");
                }
                AdminResult elsewhere = GradeAdmin.addNode(server, "cp_cl_sandbox",
                        "customperm.manage.aliases", false, 0, "server=other");
                if (!elsewhere.success()) fail("Limiting a manage node to a server must be accepted: " + elsewhere.message());
                if (GuiAccess.canEdit(admin.player(), GuiArea.ALIASES)) {
                    fail("A manage node limited to another member must open nothing here, level 4 included.");
                }
                AdminResult here = GradeAdmin.addNode(server, "cp_cl_sandbox",
                        "customperm.manage.aliases", false, 0, "server=gametest");
                if (!here.success()) fail("Could not grant the same node for this server: " + here.message());
                if (!GuiAccess.canEdit(admin.player(), GuiArea.ALIASES)) {
                    fail("The same node limited to this server must open its area.");
                }
            });
        }
        helper.succeed();
    }

    /**
     * A part a member does not share is neither published nor read back: it keeps its own file while the others
     * go on without it. This is what lets one member share grades and keep its own commands and aliases.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_propagate")
    public static void aPartNotSharedStaysLocal(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        var share = CustomPerm.configManager.getSettings().cluster.share;
        boolean sharedCommands = share.commands;
        share.commands = false;
        try {
            MemoryStore store = new MemoryStore();
            String part = new CommandsCodec().part();
            inCluster(server, store, () -> {
                // Another member publishes on that part. Its body is never parsed here, which is the point.
                store.write(part, List.of(new ClusterStore.Change("cp_cl_unshared", 0, "{}")), "other");
                Cluster.pollNow();
                if (CustomPerm.configManager.getCommands().grantedCommands.contains("cp_cl_unshared")) {
                    fail("A part this member does not share must not be read back from the cluster.");
                }
                AdminResult exposed = CommandAdmin.expose(server, "seed");
                if (!exposed.success()) fail("Exposing a command here failed: " + exposed.message());
                List<ClusterStore.Row> rows = store.changesSince(List.of(part), 0)
                        .getOrDefault(part, List.of());
                if (rows.stream().anyMatch(row -> "gametest".equals(row.updatedBy()))) {
                    fail("A part this member does not share must not be published to the cluster: " + rows);
                }
            });
        } finally {
            share.commands = sharedCommands;
        }
        helper.succeed();
    }

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

    /**
     * The {@code servers} list of an exposed command, an alias and a rate limit, set on another member, decides
     * what this member activates: nothing while it names only the other one, everything once it names this one.
     * The command keeps its original requirement meanwhile, so a node granted for it opens nothing here.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_lists")
    public static void serverListsDecideWhatThisMemberActivates(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_lists", 0);
             Grants ignored = Grants.allow(player, "customperm.command.time", "customperm.alias.cp_cl_scoped")) {
            inCluster(server, store, () -> {
                if (!"gametest".equals(Cluster.identity())) fail("The member's name must be what lists are read against.");
                CommandsConfig otherCommands = new CommandsCodec().empty();
                PartSync<CommandsConfig> commands = new PartSync<>(new CommandsCodec(), store, "other", host(otherCommands));
                commands.start();
                AliasesConfig otherAliases = new AliasesCodec().empty();
                PartSync<AliasesConfig> aliases = new PartSync<>(new AliasesCodec(), store, "other", host(otherAliases));
                aliases.start();
                RateLimitsConfig otherLimits = new RateLimitsCodec().empty();
                PartSync<RateLimitsConfig> limits = new PartSync<>(new RateLimitsCodec(), store, "other", host(otherLimits));
                limits.start();

                otherCommands.grantedCommands.add("time");
                otherCommands.commandServers.put("time", List.of("other"));
                otherAliases.aliases.put("cp_cl_scoped", new ArrayList<>(List.of("say scoped")));
                otherAliases.aliasServers.put("cp_cl_scoped", List.of("other"));
                RateLimitsConfig.Rule rule = new RateLimitsConfig.Rule();
                rule.maxExecutions = 2;
                rule.servers = List.of("other");
                otherLimits.rules.put("time", rule);
                if (commands.publish() != null || aliases.publish() != null || limits.publish() != null) {
                    fail("The other member's changes were refused.");
                }
                Cluster.pollNow();
                var config = CustomPerm.configManager;
                if (!config.getCommands().grantedCommands.contains("time") || !config.getCommands().servers("time").equals(List.of("other"))) {
                    fail("The command and its list must reach this member as they are: " + config.getCommands().commandServers);
                }
                if (player.canUse("time")) fail("A command exposed on another member only must keep its original requirement here.");
                if (server.getCommands().getDispatcher().getRoot().getChild("cp_cl_scoped") != null) {
                    fail("An alias limited to another member must not be registered here.");
                }
                if (config.getRateLimits().activeRule("time", Cluster.identity()) != null) {
                    fail("A rate limit limited to another member must not count uses here.");
                }

                otherCommands.commandServers.put("time", List.of("gametest", "other"));
                otherAliases.aliasServers.put("cp_cl_scoped", List.of("gametest"));
                otherLimits.rules.get("time").servers = List.of("gametest");
                if (commands.publish() != null || aliases.publish() != null || limits.publish() != null) {
                    fail("The other member's second changes were refused.");
                }
                Cluster.pollNow();
                if (!player.canUse("time")) fail("A command whose list names this member must be exposed here.");
                if (!player.canUse("cp_cl_scoped")) fail("An alias whose list names this member must be registered here.");
                if (config.getRateLimits().activeRule("time", Cluster.identity()) == null) {
                    fail("A rate limit whose list names this member must count uses here.");
                }

                // An emptied list means every member again.
                otherAliases.aliasServers.remove("cp_cl_scoped");
                if (aliases.publish() != null) fail("Emptying the list was refused.");
                Cluster.pollNow();
                if (!config.getAliases().servers("cp_cl_scoped").isEmpty() || !player.canUse("cp_cl_scoped")) {
                    fail("An alias without a list must exist on every member.");
                }
            });
        }
        helper.succeed();
    }

    /**
     * The text commands set a list and say what it does here: active or not, a member name no server answers to
     * (kept), a part this member does not share (the list stays in its file), and {@code here} and {@code all}.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_lists_admin")
    public static void serverListCommandsSayWhatTheListDoesHere(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        var share = CustomPerm.configManager.getSettings().cluster.share;
        boolean sharedAliases = share.aliases;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_listcmd", 0);
             Grants ignored = Grants.allow(player, "customperm.command.time")) {
            inCluster(server, new MemoryStore(), () -> {
                ServerCommands.run(server, "customperm command add time");
                List<String> out = ServerCommands.run(server, "customperm command servers time other");
                if (!ServerCommands.contains(out, "now active on other only") || !ServerCommands.contains(out, "Not active on this server (gametest)")
                        || !ServerCommands.contains(out, "other is not a member heard right now")) {
                    fail("Limiting a command to an unknown member must say so and keep it: " + out);
                }
                if (player.canUse("time")) fail("The command must stop being exposed here.");
                out = ServerCommands.run(server, "customperm command list");
                if (!ServerCommands.contains(out, "time  [other] (not on this server)")) fail("The list must show the servers: " + out);

                out = ServerCommands.run(server, "customperm command servers time here,other");
                if (!ServerCommands.contains(out, "gametest, other only") || !player.canUse("time")) {
                    fail("here must stand for this member and expose the command again: " + out);
                }
                out = ServerCommands.run(server, "customperm command servers time all");
                if (!ServerCommands.contains(out, "every member") || !CustomPerm.configManager.getCommands().commandServers.isEmpty()) {
                    fail("all must clear the list: " + out);
                }
                out = ServerCommands.run(server, "customperm command servers time");
                if (!ServerCommands.contains(out, "is active on every member") || !ServerCommands.contains(out, "This server is gametest")) {
                    fail("Without a list the command must show where it applies: " + out);
                }

                share.aliases = false;
                ServerCommands.run(server, "customperm alias add cp_cl_listalias say listed");
                out = ServerCommands.run(server, "customperm alias servers cp_cl_listalias gametest");
                if (!ServerCommands.contains(out, "does not share its aliases")) {
                    fail("A list on a part this member keeps local must warn that it reaches no other member: " + out);
                }
                out = ServerCommands.run(server, "customperm ratelimit servers cp_cl_nothing hub");
                if (!ServerCommands.contains(out, "No rate limit on /cp_cl_nothing")) fail("A missing rule must be named: " + out);
            });
        } finally {
            share.aliases = sharedAliases;
            AliasAdmin.remove(server, "cp_cl_listalias");
        }
        helper.succeed();
    }

    /**
     * server=here stands for this member wherever a context is typed, and the listings show each entry with where
     * it applies, without opening grades.json.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_lists_admin")
    public static void serverHereAndListingsShowWhereEntriesApply(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        String grade = "cp_cl_here";
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_herep", 0)) {
            List<String> out = ServerCommands.run(server, "customperm grade create " + grade);
            out = ServerCommands.run(server, "customperm grade addperm " + grade + " customperm.command.time server=here");
            if (!ServerCommands.contains(out, "server=here names this server in a cluster")) {
                fail("server=here must be refused outside a cluster: " + out);
            }
            inCluster(server, new MemoryStore(), () -> {
                List<String> added = ServerCommands.run(server, "customperm grade addperm " + grade + " customperm.command.time server=here");
                var scoped = CustomPerm.configManager.getGrades().grades.get(grade).contexts.get("server=gametest");
                if (scoped == null || !scoped.permissions.contains("customperm.command.time")) {
                    fail("server=here must be stored as this member's name: " + added);
                }
                ServerCommands.run(server, "customperm grade addperm " + grade + " customperm.command.seed 30d");
                List<String> shown = ServerCommands.run(server, "customperm grade list " + grade);
                if (!ServerCommands.contains(shown, "customperm.command.time (server=gametest)")
                        || !(ServerCommands.contains(shown, "customperm.command.seed (30d left)")
                            || ServerCommands.contains(shown, "customperm.command.seed (29d 23h left)"))) {
                    fail("grade list <grade> must show each node with its context and time left: " + shown);
                }
                ServerCommands.run(server, "customperm user addperm cp_cl_herep customperm.command.weather server=here");
                shown = ServerCommands.run(server, "customperm user list cp_cl_herep");
                if (!ServerCommands.contains(shown, "customperm.command.weather (server=gametest)")) {
                    fail("user list must show an own node with its context on the same line: " + shown);
                }
            });
        } finally {
            GradeAdmin.delete(server, grade);
        }
        helper.succeed();
    }

    /**
     * Command, then grade, then player: the command's list decides where it is exposed, a grade's entry naming a
     * server has the last word there over the list, both ways, and the player's own entry over the grade. A node
     * held everywhere opens nothing where the list says no.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_priority")
    public static void commandThenGradeThenPlayerDecideOnAServer(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        String grade = "cp_cl_prio";
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_prio", 0)) {
            inCluster(server, new MemoryStore(), () -> {
                GradeAdmin.create(grade);
                GradeAdmin.assign(server, player.player().getGameProfile(), grade);
                ServerCommands.run(server, "customperm command add time");

                // The command everywhere, the grade holding the node everywhere: open.
                ServerCommands.run(server, "customperm grade addperm " + grade + " customperm.command.time");
                if (!player.canUse("time")) fail("The grade's node must open an exposed command.");
                // The grade says not on this server: closed here, whatever the command's list says.
                ServerCommands.run(server, "customperm grade adddeny " + grade + " customperm.command.time server=here");
                if (player.canUse("time")) fail("A grade's refusal naming this server must win over the command being exposed everywhere.");
                // The player says yes here: they have the last word over their grade.
                ServerCommands.run(server, "customperm user addperm cp_cl_prio customperm.command.time server=here");
                if (!player.canUse("time")) fail("The player's own entry naming this server must win over their grade.");
                ServerCommands.run(server, "customperm user removeperm cp_cl_prio customperm.command.time server=here");
                ServerCommands.run(server, "customperm grade removedeny " + grade + " customperm.command.time server=here");

                // The command limited to another member: the node held everywhere does not open it here.
                ServerCommands.run(server, "customperm command servers time other");
                if (player.canUse("time")) fail("A node held everywhere must not undo the command's list.");
                // The grade names this server: it opens the command here for its members.
                var result = com.arcadia.customperm.admin.NodeServerAdmin.forGrade(server, grade, "customperm.command.time",
                        "gametest", com.arcadia.customperm.admin.NodeServerAdmin.ALLOW);
                if (!result.success() || !player.canUse("time")) {
                    fail("A grade's entry naming this server must open a command the list leaves out: " + result.summary());
                }
                // The player says no here: over their grade again.
                com.arcadia.customperm.admin.NodeServerAdmin.forPlayer(server, player.uuid(), "cp_cl_prio",
                        "customperm.command.time", "gametest", com.arcadia.customperm.admin.NodeServerAdmin.DENY);
                if (player.canUse("time")) fail("The player's refusal naming this server must win over their grade's.");
                // Back to following: the grade's word applies again, then nothing once it is taken back.
                com.arcadia.customperm.admin.NodeServerAdmin.forPlayer(server, player.uuid(), "cp_cl_prio",
                        "customperm.command.time", "gametest", com.arcadia.customperm.admin.NodeServerAdmin.INHERIT);
                if (!player.canUse("time")) fail("Following again must give the grade's word back.");
                com.arcadia.customperm.admin.NodeServerAdmin.forGrade(server, grade, "customperm.command.time",
                        "gametest", com.arcadia.customperm.admin.NodeServerAdmin.INHERIT);
                if (player.canUse("time")) fail("Without an entry naming this server the command's list decides again.");
                var scopes = CustomPerm.configManager.getGrades().grades.get(grade).contexts.get("server=gametest");
                if (scopes != null && (!scopes.permissions.isEmpty() || !scopes.deniedPermissions.isEmpty())) {
                    fail("Following again must leave no entry behind.");
                }
            });
        } finally {
            GradeAdmin.delete(server, grade);
        }
        helper.succeed();
    }

    /**
     * A member whose database cannot be reached still knows its name, and entries naming it keep applying: an
     * outage must not lift a refusal limited to this server.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_outage_name")
    public static void anEntryNamingThisServerHoldsWithoutTheStore(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        var config = CustomPerm.configManager;
        var cluster = config.getSettings().cluster;
        boolean enabled = cluster.enabled;
        String connection = cluster.connection;
        String name = cluster.serverName;
        boolean exposedBefore = config.getCommands().grantedCommands.contains("time");
        String grade = "cp_cl_outage";
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_outage", 0)) {
            // Cluster mode on, this member named, no store joined: what a member sees while its database is down.
            cluster.enabled = true;
            cluster.connection = "direct";
            cluster.serverName = "alpha";
            if (Cluster.running() || !"alpha".equals(Cluster.identity())) fail("The member must know its name without a store.");
            GradeAdmin.create(grade);
            GradeAdmin.assign(server, player.player().getGameProfile(), grade);
            ServerCommands.run(server, "customperm command add time");
            ServerCommands.run(server, "customperm grade addperm " + grade + " customperm.command.time");
            List<String> out = ServerCommands.run(server, "customperm grade adddeny " + grade + " customperm.command.time server=here");
            var scope = config.getGrades().grades.get(grade).contexts.get("server=alpha");
            if (scope == null || !scope.deniedPermissions.contains("customperm.command.time")) {
                fail("server=here must name this member without a store: " + out);
            }
            if (player.canUse("time")) fail("A refusal naming this member must hold while the store is out of reach.");
        } finally {
            cluster.enabled = enabled;
            cluster.connection = connection;
            cluster.serverName = name;
            GradeAdmin.delete(server, grade);
            if (!exposedBefore) com.arcadia.customperm.admin.CommandAdmin.hide(server, "time");
            config.save();
        }
        helper.succeed();
    }

    /** Outside a cluster a list is stored but read nowhere, and here has nothing to stand for. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "cluster_server_lists_admin")
    public static void outsideAClusterAListIsStoredAndIgnored(GameTestHelper helper) {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        try {
            ServerCommands.run(server, "customperm alias add cp_cl_alone say alone");
            List<String> out = ServerCommands.run(server, "customperm alias servers cp_cl_alone here");
            if (!ServerCommands.contains(out, "has no cluster name")) fail("here must be refused outside a cluster: " + out);
            out = ServerCommands.run(server, "customperm alias servers cp_cl_alone other");
            if (!ServerCommands.contains(out, "read once it runs in a cluster")) fail("The list must be said to be ignored here: " + out);
            if (server.getCommands().getDispatcher().getRoot().getChild("cp_cl_alone") == null) {
                fail("Outside a cluster an alias with a list must stay registered.");
            }
        } finally {
            AliasAdmin.remove(server, "cp_cl_alone");
        }
        helper.succeed();
    }

    private static <T> PartSync.Host<T> host(T config) {
        return new PartSync.Host<>() {
            @Override public T current() { return config; }
            @Override public void changed(T c, Set<String> holders) {}
            @Override public String label(String holder) { return holder; }
        };
    }

    /** Admin changes made here reach the shared log under this server's name; the other server's show here with its name. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_log")
    public static void theActivityLogIsSharedWithTheServerName(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        inCluster(server, store, () -> {
            store.appendLog("other", List.of(new ClusterStore.LogLine(LogKind.ADMIN.name(), new LogEntry(
                    System.currentTimeMillis(), "Alex", "", LogEntry.SOURCE_COMMAND, "grade create cp_cl_logged", true,
                    "Created grade cp_cl_logged"))));
            ActivityLog.admin("Tester", "", LogEntry.SOURCE_COMMAND, "cp_cl_here", true, "done");
            Cluster.pollNow();

            boolean shown = ActivityLog.recent(LogKind.ADMIN, 50).stream()
                    .anyMatch(e -> e.action().equals("grade create cp_cl_logged") && e.server().equals("other"));
            if (!shown) fail("The other server's entry must show here, with its server name.");
            boolean sent = store.logAfter(0, List.of(), 500).stream()
                    .anyMatch(r -> r.entry().action().equals("cp_cl_here") && r.server().equals("gametest"));
            if (!sent) fail("An entry recorded here must reach the shared log under this server's name.");
            boolean echoed = ActivityLog.recent(LogKind.ADMIN, 50).stream()
                    .anyMatch(e -> e.action().equals("cp_cl_here") && !e.server().isEmpty());
            if (echoed) fail("This server's own entries must not come back from the store as another server's.");
        });
        helper.succeed();
    }

    /**
     * Each rule says who shares its budget. network: three uses on the other server use up a limit of three here.
     * A group: only the servers named count together. server (the default): nothing is shared.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_uses")
    public static void eachRuleSaysWhoSharesItsBudget(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        MemoryStore store = new MemoryStore();
        inCluster(server, store, () -> {
            if (!RateLimitAdmin.set("cp_cl_net", 3, 60).success()) fail("Could not set the rule.");
            if (!RateLimitAdmin.setScope("cp_cl_net", "network").success()) fail("Could not set the network scope.");
            java.util.UUID player = java.util.UUID.randomUUID();
            store.appendUses("other", threeUses("cp_cl_net", player));
            Cluster.pollNow();
            if (RateLimiter.tryAcquire("cp_cl_net", player, 3, 60).allowed()) {
                fail("network: three uses on the other server must use up a limit of three here.");
            }
            java.util.UUID fresh = java.util.UUID.randomUUID();
            if (!RateLimiter.tryAcquire("cp_cl_net", fresh, 3, 60).allowed()) fail("A fresh player has uses left.");
            Cluster.pollNow();
            boolean sent = store.usesAfter(0, List.of(), 100).stream()
                    .anyMatch(r -> r.server().equals("gametest") && r.use().player().equals(fresh.toString()));
            if (!sent) fail("network: a use counted here must reach the store under this server's name.");

            if (!RateLimitAdmin.set("cp_cl_group", 3, 60).success()) fail("Could not set the group rule.");
            if (!RateLimitAdmin.setScope("cp_cl_group", "Other, gametest").success()) fail("Could not set the group scope.");
            java.util.UUID member = java.util.UUID.randomUUID();
            java.util.UUID outsider = java.util.UUID.randomUUID();
            store.appendUses("other", threeUses("cp_cl_group", member));
            store.appendUses("third", threeUses("cp_cl_group", outsider));
            Cluster.pollNow();
            if (RateLimiter.tryAcquire("cp_cl_group", member, 3, 60).allowed()) {
                fail("A server named in the group shares its budget with this one.");
            }
            if (!RateLimiter.tryAcquire("cp_cl_group", outsider, 3, 60).allowed()) {
                fail("A server outside the group keeps its own count.");
            }

            if (!RateLimitAdmin.set("cp_cl_local", 3, 60).success()) fail("Could not set the local rule.");
            java.util.UUID local = java.util.UUID.randomUUID();
            store.appendUses("other", threeUses("cp_cl_local", local));
            Cluster.pollNow();
            if (!RateLimiter.tryAcquire("cp_cl_local", local, 3, 60).allowed()) {
                fail("server (the default): another server's uses do not count here.");
            }
            int before = store.usesAfter(0, List.of(), 500).size();
            RateLimiter.tryAcquire("cp_cl_local", java.util.UUID.randomUUID(), 3, 60);
            Cluster.pollNow();
            if (store.usesAfter(0, List.of(), 500).size() != before) fail("A use of an unshared rule must not be sent.");

            RateLimitAdmin.remove("cp_cl_net");
            RateLimitAdmin.remove("cp_cl_group");
            RateLimitAdmin.remove("cp_cl_local");
        });
        helper.succeed();
    }

    private static List<ClusterStore.Use> threeUses(String command, java.util.UUID player) {
        long now = System.currentTimeMillis();
        List<ClusterStore.Use> uses = new ArrayList<>();
        for (int i = 0; i < 3; i++) uses.add(new ClusterStore.Use(command, player.toString(), now - 1000 + i));
        return uses;
    }

    /** server=<name> is a context while a cluster runs: it holds on the server of that name only; outside a cluster it is refused. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "cluster_server_context")
    public static void theServerContextHoldsOnThatServerOnly(GameTestHelper helper) throws Exception {
        if (!Modes.internalOnly(helper)) return;
        MinecraftServer server = helper.getLevel().getServer();
        if (GradeAdmin.addNode(server, "cp_cl_nowhere", "x", false, 0, "server=gametest").success()) {
            fail("Outside a cluster, server= must be refused: it would apply nowhere.");
        }
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_cl_ctx", 0)) {
            inCluster(server, new MemoryStore(), () -> {
                if (!GradeAdmin.create("cp_cl_ctx").success()) fail("Could not create the grade.");
                if (!GradeAdmin.assign(server, player.player().getGameProfile(), "cp_cl_ctx").success()) {
                    fail("Could not assign the grade.");
                }
                AdminResult here = GradeAdmin.addNode(server, "cp_cl_ctx", "cp.cluster.here", false, 0, "server=gametest");
                AdminResult there = GradeAdmin.addNode(server, "cp_cl_ctx", "cp.cluster.there", false, 0, "server=other");
                if (!here.success() || !there.success()) fail("In a cluster, server= must be accepted: " + here.message());
                if (!PermissionService.get().hasGrantedNode(player.source(), "cp.cluster.here")) {
                    fail("A node limited to this server's name must hold here.");
                }
                if (PermissionService.get().hasGrantedNode(player.source(), "cp.cluster.there")) {
                    fail("A node limited to another server must not hold here.");
                }
            });
        }
        helper.succeed();
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
            int asked = store.writeCalls();
            if (GradeAdmin.create("cp_cl_lost_again").success()) fail("While the outage lasts, changes stay refused.");
            if (store.writeCalls() != asked) {
                fail("Once the store is known down, a change is refused at once, without waiting on it again.");
            }

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
