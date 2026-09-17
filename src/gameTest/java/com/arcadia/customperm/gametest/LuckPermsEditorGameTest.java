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
import com.arcadia.customperm.gametest.support.Grants;
import com.arcadia.customperm.gametest.support.LuckPermsTestSupport;
import com.arcadia.customperm.gametest.support.Modes;
import com.arcadia.customperm.gametest.support.TestPlayer;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.LpEditPayload;
import com.arcadia.customperm.network.lp.LpEditResultPayload;
import com.arcadia.customperm.network.lp.LpRequestHandler;
import com.arcadia.customperm.network.lp.LpSyncPayload;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import com.arcadia.customperm.perm.PermissionNodes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.arcadia.customperm.gametest.support.LuckPermsTestSupport.apply;

/**
 * Server side of the in-game LuckPerms editor, against a real LuckPerms (LuckPerms editor procedure
 * E04-E20 and N01-N05, audit retest L01-L06). LuckPerms mode only.
 *
 * <p>Edit semantics go through LuckPermsAdminService, exactly what the packet handler calls, and are
 * checked against the resulting LuckPerms data, not only against the returned message. Gating and rate
 * limiting go through the packet handler with connected players. Screens are not covered: rendering and
 * button wiring need a client.</p>
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class LuckPermsEditorGameTest {

    private static final String TEMPLATE = "empty_3x3";

    /** E04 + E05: create refuses duplicates and bad names; delete refuses default and missing groups. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void groupCreateAndDelete(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try {
            expect(apply(LpEditOp.GROUP_CREATE, "cp_e_life"), "OK: Created group cp_e_life");
            expect(apply(LpEditOp.GROUP_CREATE, "cp_e_life"), "FAIL: Group already exists: cp_e_life");
            expect(apply(LpEditOp.GROUP_CREATE, "bad name!"), "FAIL: Invalid group name");
            if (!LuckPermsTestSupport.groupExists("cp_e_life")) fail("The created group is not in LuckPerms.");
            expect(apply(LpEditOp.GROUP_DELETE, "default"), "FAIL: The default group cannot be deleted.");
            expect(apply(LpEditOp.GROUP_DELETE, "cp_e_life"), "OK: Deleted group cp_e_life");
            expect(apply(LpEditOp.GROUP_DELETE, "cp_e_life"), "FAIL: No such group: cp_e_life");
            if (LuckPermsTestSupport.groupExists("cp_e_life")) fail("The deleted group is still in LuckPerms.");
        } finally {
            LuckPermsTestSupport.cleanup(List.of("cp_e_life"), List.of());
        }
        helper.succeed();
    }

    /** E06-E09: allow and deny, contexts, expiry, and removal limited to the requested context. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void permissionNodes(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try {
            apply(LpEditOp.GROUP_CREATE, "cp_e_nodes");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.allow", "true", "", "0"), "OK: Granted cp.allow");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.deny", "false", "", "0"), "OK: Denied cp.deny");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.allow", "true", "", "0"), "FAIL: cp_e_nodes already has cp.allow");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.allow", "true", "server=survival", "0"), "in context server=survival");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.temp", "true", "", "3600"), "OK: Granted cp.temp");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.bad", "true", "", "soon"), "FAIL: Invalid duration");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp bad", "true", "", "0"), "FAIL: A permission node cannot contain spaces");
            expect(apply(LpEditOp.GROUP_PERM_ADD, "cp_e_nodes", "cp.bad", "true", "nocontext", "0"), "FAIL: Malformed context");

            List<String> nodes = LuckPermsTestSupport.groupNodes("cp_e_nodes");
            for (String expected : List.of("cp.allow=true", "cp.deny=false", "cp.allow=true[server=survival]", "cp.temp=true@expiring")) {
                if (!nodes.contains(expected)) fail("Missing node " + expected + " in " + nodes);
            }

            expect(apply(LpEditOp.GROUP_PERM_REMOVE, "cp_e_nodes", "cp.allow", "server=survival"), "OK: Removed cp.allow");
            nodes = LuckPermsTestSupport.groupNodes("cp_e_nodes");
            if (nodes.contains("cp.allow=true[server=survival]") || !nodes.contains("cp.allow=true"))
                fail("Removing in a context must only remove that context's node: " + nodes);
            expect(apply(LpEditOp.GROUP_PERM_REMOVE, "cp_e_nodes", "cp.missing", ""), "FAIL: cp_e_nodes does not have cp.missing");
        } finally {
            LuckPermsTestSupport.cleanup(List.of("cp_e_nodes"), List.of());
        }
        helper.succeed();
    }

    /** E10 + L01-L03: inheritance, including the missing-parent and self-inheritance refusals. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void groupInheritance(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try {
            apply(LpEditOp.GROUP_CREATE, "cp_e_child");
            apply(LpEditOp.GROUP_CREATE, "cp_e_parent");
            expect(apply(LpEditOp.GROUP_PARENT_ADD, "cp_e_child", "cp_e_nope", ""), "FAIL: No such group: cp_e_nope");
            expect(apply(LpEditOp.GROUP_PARENT_ADD, "cp_e_child", "CP_E_Child", ""), "FAIL: A group cannot inherit itself.");
            if (LuckPermsTestSupport.groupNodes("cp_e_child").stream().anyMatch(n -> n.startsWith("group.")))
                fail("A refused parent was stored anyway: " + LuckPermsTestSupport.groupNodes("cp_e_child"));
            expect(apply(LpEditOp.GROUP_PARENT_ADD, "cp_e_child", "cp_e_parent", ""), "OK: Added parent cp_e_parent to cp_e_child");
            if (!LuckPermsTestSupport.groupNodes("cp_e_child").contains("group.cp_e_parent=true"))
                fail("The parent is not stored: " + LuckPermsTestSupport.groupNodes("cp_e_child"));
            expect(apply(LpEditOp.GROUP_PARENT_REMOVE, "cp_e_child", "cp_e_parent", ""), "OK: Removed parent cp_e_parent");
            expect(apply(LpEditOp.GROUP_PARENT_REMOVE, "cp_e_child", "cp_e_parent", ""), "FAIL: cp_e_child does not inherit cp_e_parent");
        } finally {
            LuckPermsTestSupport.cleanup(List.of("cp_e_child", "cp_e_parent"), List.of());
        }
        helper.succeed();
    }

    /** E11-E13: meta overwrite, prefix priority slots, weight and display name including clearing. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void metaPrefixWeightDisplayName(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try {
            apply(LpEditOp.GROUP_CREATE, "cp_e_meta");
            apply(LpEditOp.GROUP_META_SET, "cp_e_meta", "color", "red", "");
            apply(LpEditOp.GROUP_META_SET, "cp_e_meta", "color", "blue", "");
            assertOnly("cp_e_meta", "meta.color.", "meta.color.blue=true");
            expect(apply(LpEditOp.GROUP_META_UNSET, "cp_e_meta", "color", ""), "OK: Unset meta color");
            expect(apply(LpEditOp.GROUP_META_UNSET, "cp_e_meta", "color", ""), "FAIL: cp_e_meta has no meta color");

            apply(LpEditOp.GROUP_PREFIX_SET, "cp_e_meta", "100", "[A]", "");
            apply(LpEditOp.GROUP_PREFIX_SET, "cp_e_meta", "100", "[B]", "");
            assertOnly("cp_e_meta", "prefix.100.", "prefix.100.[B]=true");
            expect(apply(LpEditOp.GROUP_PREFIX_SET, "cp_e_meta", "100", "", ""), "FAIL: A prefix cannot be empty");
            expect(apply(LpEditOp.GROUP_SUFFIX_SET, "cp_e_meta", "10", "!", ""), "OK: Set suffix (priority 10)");
            expect(apply(LpEditOp.GROUP_PREFIX_UNSET, "cp_e_meta", "100", ""), "OK: Unset prefix (priority 100)");
            expect(apply(LpEditOp.GROUP_PREFIX_SET, "cp_e_meta", "high", "[C]", ""), "FAIL: Invalid priority");

            apply(LpEditOp.GROUP_WEIGHT_SET, "cp_e_meta", "10");
            assertOnly("cp_e_meta", "weight.", "weight.10=true");
            expect(apply(LpEditOp.GROUP_WEIGHT_SET, "cp_e_meta", "-1"), "OK: Cleared the weight");
            assertOnly("cp_e_meta", "weight.", null);
            apply(LpEditOp.GROUP_DISPLAYNAME_SET, "cp_e_meta", "Nice");
            assertOnly("cp_e_meta", "displayname.", "displayname.Nice=true");
            expect(apply(LpEditOp.GROUP_DISPLAYNAME_SET, "cp_e_meta", ""), "OK: Cleared the display name");
            assertOnly("cp_e_meta", "displayname.", null);
        } finally {
            LuckPermsTestSupport.cleanup(List.of("cp_e_meta"), List.of());
        }
        helper.succeed();
    }

    /** E15 + E16 + L04: player groups, temporary membership, primary group refusal then success. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void playerGroupsAndPrimaryGroup(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_e_user", 0)) {
            String uuid = player.uuid().toString();
            apply(LpEditOp.GROUP_CREATE, "cp_e_member");
            apply(LpEditOp.GROUP_CREATE, "cp_e_temp");
            expect(apply(LpEditOp.USER_PARENT_ADD, uuid, "cp_e_nope", "", "0"), "FAIL: No such group: cp_e_nope");
            expect(apply(LpEditOp.USER_PRIMARY_GROUP_SET, uuid, "cp_e_member"), "FAIL: Cannot set cp_e_member as primary group");
            expect(apply(LpEditOp.USER_PARENT_ADD, uuid, "cp_e_member", "", "0"), "OK: Added parent cp_e_member");
            expect(apply(LpEditOp.USER_PRIMARY_GROUP_SET, uuid, "cp_e_member"), "OK: Primary group of");
            if (!"cp_e_member".equals(LuckPermsTestSupport.primaryGroup(player.uuid())))
                fail("Primary group not stored, got " + LuckPermsTestSupport.primaryGroup(player.uuid()));
            expect(apply(LpEditOp.USER_PARENT_ADD, uuid, "cp_e_temp", "", "600"), "OK: Added parent cp_e_temp");
            if (!LuckPermsTestSupport.userNodes(player.uuid()).contains("group.cp_e_temp=true@expiring"))
                fail("Temporary membership not stored as expiring: " + LuckPermsTestSupport.userNodes(player.uuid()));
            expect(apply(LpEditOp.USER_PERM_ADD, "not-a-uuid", "cp.x", "true", "", "0"), "FAIL: Malformed player identifier");
        } finally {
            LuckPermsTestSupport.cleanup(List.of("cp_e_member", "cp_e_temp"), List.of());
        }
        helper.succeed();
    }

    /** E17 + E18: track create, append, insert, bounds, remove, delete, then promote and demote on it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void tracksPromoteAndDemote(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        List<String> groups = List.of("cp_e_t1", "cp_e_t2", "cp_e_t3");
        try (TestPlayer player = TestPlayer.join(helper.getLevel(), "cp_e_ranked", 0)) {
            groups.forEach(g -> apply(LpEditOp.GROUP_CREATE, g));
            expect(apply(LpEditOp.TRACK_CREATE, "cp_e_track"), "OK: Created track cp_e_track");
            expect(apply(LpEditOp.TRACK_CREATE, "cp_e_track"), "FAIL: Track already exists");
            apply(LpEditOp.TRACK_APPEND, "cp_e_track", "cp_e_t1");
            apply(LpEditOp.TRACK_APPEND, "cp_e_track", "cp_e_t2");
            expect(apply(LpEditOp.TRACK_APPEND, "cp_e_track", "cp_e_t2"), "FAIL: Track cp_e_track already contains cp_e_t2");
            apply(LpEditOp.TRACK_INSERT, "cp_e_track", "cp_e_t3", "1");
            if (!LuckPermsTestSupport.trackGroups("cp_e_track").equals(List.of("cp_e_t1", "cp_e_t3", "cp_e_t2")))
                fail("Insert at position 1 gave " + LuckPermsTestSupport.trackGroups("cp_e_track"));
            expect(apply(LpEditOp.TRACK_INSERT, "cp_e_track", "cp_e_t3", "99"), "FAIL:");
            expect(apply(LpEditOp.TRACK_REMOVE, "cp_e_track", "cp_e_t3"), "OK: Removed cp_e_t3 from track cp_e_track");

            String uuid = player.uuid().toString();
            apply(LpEditOp.USER_PARENT_ADD, uuid, "cp_e_t1", "", "0");
            expect(apply(LpEditOp.USER_PROMOTE, uuid, "cp_e_track"), "promoted to cp_e_t2");
            if (!LuckPermsTestSupport.userNodes(player.uuid()).contains("group.cp_e_t2=true"))
                fail("Promotion not stored: " + LuckPermsTestSupport.userNodes(player.uuid()));
            expect(apply(LpEditOp.USER_DEMOTE, uuid, "cp_e_track"), "demoted to cp_e_t1");

            expect(apply(LpEditOp.TRACK_DELETE, "cp_e_track"), "OK: Deleted track cp_e_track");
            expect(apply(LpEditOp.TRACK_DELETE, "cp_e_track"), "FAIL: No such track: cp_e_track");
        } finally {
            LuckPermsTestSupport.cleanup(groups, List.of("cp_e_track"));
        }
        helper.succeed();
    }

    /**
     * N01-N03 + E20: the edit node gates writes for level-2 operators, level 4 bypasses it, and a refusal
     * comes back as a failed result rather than silence. Also E19: a sync for a deleted group returns no
     * detail entry for it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void editGatingThroughThePacketHandler(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        TestPlayer readOnly = TestPlayer.reader(helper.getLevel(), "cp_e_readonly", 2);
        TestPlayer delegated = TestPlayer.reader(helper.getLevel(), "cp_e_delegate", 2);
        TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_e_owner", 4);
        Grants grant = Grants.allow(delegated, PermissionNodes.MANAGE_LUCKPERMS);
        for (TestPlayer p : List.of(readOnly, delegated, owner)) p.clearReceived();

        edit(readOnly, LpEditOp.GROUP_CREATE, "cp_e_by_readonly");
        edit(delegated, LpEditOp.GROUP_CREATE, "cp_e_by_delegate");
        edit(owner, LpEditOp.GROUP_CREATE, "cp_e_by_owner");
        edit(owner, LpEditOp.GROUP_DELETE, "cp_e_does_not_exist");
        LpRequestHandler.handleSync(new RequestLpSyncPayload(RequestLpSyncPayload.SCOPE_GROUP, "cp_e_does_not_exist"),
                owner.payloadContext());

        whenReceived(helper, 400,
                () -> readOnly.payloads(LpEditResultPayload.class).size() >= 1
                        && delegated.payloads(LpEditResultPayload.class).size() >= 1
                        && owner.payloads(LpEditResultPayload.class).size() >= 2
                        && !owner.payloads(LpSyncPayload.class).isEmpty(),
                () -> {
            try {
                String denied = results(readOnly);
                if (!denied.contains("FAIL: You do not have " + PermissionNodes.MANAGE_LUCKPERMS))
                    fail("Level 2 without the node must be refused, got: " + denied);
                if (LuckPermsTestSupport.groupExists("cp_e_by_readonly")) fail("A refused edit created the group.");
                if (!results(delegated).contains("OK: Created group cp_e_by_delegate"))
                    fail("Level 2 with the node must be able to write, got: " + results(delegated));
                String ownerResults = results(owner);
                if (!ownerResults.contains("OK: Created group cp_e_by_owner"))
                    fail("Level 4 must write without the node, got: " + ownerResults);
                if (!ownerResults.contains("FAIL: No such group: cp_e_does_not_exist"))
                    fail("A failed edit must come back as a failed result, got: " + ownerResults);
                var snapshots = owner.payloads(LpSyncPayload.class);
                if (snapshots.isEmpty()) fail("The operator received no snapshot for the group scope.");
                if (snapshots.get(0).snapshot().groups().stream().anyMatch(g -> g.name().equals("cp_e_does_not_exist")))
                    fail("A missing group appeared in its own detail snapshot.");
                helper.succeed();
            } finally {
                grant.close();
                readOnly.close();
                delegated.close();
                owner.close();
                LuckPermsTestSupport.cleanup(List.of("cp_e_by_readonly", "cp_e_by_delegate", "cp_e_by_owner"), List.of());
            }
        });
    }

    /**
     * N05 + L05: edits are capped at 30 and syncs at 40 per 10 seconds per player. Every request queues on
     * LuckPerms' storage executor, so the replies arrive over several seconds: the test waits until the
     * 40 expected snapshots are in, then gives a 41st the time to show up. Own batch: real time.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600, batch = "customperm_lp_editor_budget")
    public static void editAndSyncBudgets(GameTestHelper helper) {
        if (!Modes.luckPermsOnly(helper)) return;
        TestPlayer owner = TestPlayer.admin(helper.getLevel(), "cp_e_spammer", 4);
        owner.clearReceived();
        for (int i = 0; i < 31; i++) edit(owner, LpEditOp.GROUP_DELETE, "cp_e_missing_" + i);
        for (int i = 0; i < 45; i++) {
            LpRequestHandler.handleSync(RequestLpSyncPayload.of(RequestLpSyncPayload.SCOPE_TRACKS), owner.payloadContext());
        }
        waitForSnapshots(helper, owner, 40, 400);
    }

    private static void waitForSnapshots(GameTestHelper helper, TestPlayer owner, int expected, int ticksLeft) {
        int received = owner.payloads(LpSyncPayload.class).size();
        if (received < expected && ticksLeft > 0) {
            helper.runAfterDelay(10, () -> waitForSnapshots(helper, owner, expected, ticksLeft - 10));
            return;
        }
        // Either all expected replies are in or the wait ran out; give a 41st reply time to arrive.
        helper.runAfterDelay(40, () -> {
            try {
                long throttled = owner.payloads(LpEditResultPayload.class).stream()
                        .filter(r -> r.message().startsWith("Too many edits at once")).count();
                if (throttled != 1) fail("Expected exactly the 31st edit throttled, got " + throttled);
                int syncs = owner.payloads(LpSyncPayload.class).size();
                if (syncs != expected) fail("Expected exactly " + expected + " sync replies for 45 requests, got " + syncs);
                helper.succeed();
            } finally {
                owner.close();
            }
        });
    }

    /** Runs {@code check} once {@code ready} holds, polling every 10 ticks for at most {@code ticksLeft}. */
    private static void whenReceived(GameTestHelper helper, int ticksLeft, java.util.function.BooleanSupplier ready, Runnable check) {
        if (!ready.getAsBoolean() && ticksLeft > 0) {
            helper.runAfterDelay(10, () -> whenReceived(helper, ticksLeft - 10, ready, check));
            return;
        }
        check.run();
    }

    private static void edit(TestPlayer player, LpEditOp op, String... args) {
        LpRequestHandler.handleEdit(new LpEditPayload(op.name(), List.of(args), "", ""), player.payloadContext());
    }

    private static String results(TestPlayer player) {
        return player.payloads(LpEditResultPayload.class).stream()
                .map(r -> (r.success() ? "OK: " : "FAIL: ") + r.message())
                .toList().toString();
    }

    /** Asserts that the group's only node starting with {@code prefix} is {@code expected} (or none if null). */
    private static void assertOnly(String group, String prefix, String expected) {
        List<String> matching = LuckPermsTestSupport.groupNodes(group).stream().filter(n -> n.startsWith(prefix)).toList();
        List<String> wanted = expected == null ? List.of() : List.of(expected);
        if (!matching.equals(wanted)) fail("Nodes " + prefix + "* on " + group + ": expected " + wanted + ", got " + matching);
    }

    private static void expect(String outcome, String prefix) {
        if (!outcome.startsWith(prefix) && !outcome.contains(prefix)) fail("Expected '" + prefix + "', got '" + outcome + "'");
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
