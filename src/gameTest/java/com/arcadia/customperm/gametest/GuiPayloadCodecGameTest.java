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
import com.arcadia.customperm.network.gui.AliasesData;
import com.arcadia.customperm.network.gui.CommandsData;
import com.arcadia.customperm.network.gui.DashboardData;
import com.arcadia.customperm.network.gui.GradesData;
import com.arcadia.customperm.network.gui.GuiActionPayload;
import com.arcadia.customperm.network.gui.GuiActionResultPayload;
import com.arcadia.customperm.network.gui.GuiArea;
import com.arcadia.customperm.network.gui.GuiCodecs;
import com.arcadia.customperm.network.gui.GuiContext;
import com.arcadia.customperm.network.gui.GuiPagePayload;
import com.arcadia.customperm.network.gui.GuiRequestPayload;
import com.arcadia.customperm.network.gui.ImportData;
import com.arcadia.customperm.network.gui.NameSettings;
import com.arcadia.customperm.network.gui.Remaining;
import com.arcadia.customperm.network.gui.ScopedEntry;
import com.arcadia.customperm.network.gui.LogsData;
import com.arcadia.customperm.network.gui.LuckPermsData;
import com.arcadia.customperm.network.gui.PlayersData;
import com.arcadia.customperm.network.gui.RateLimitsData;
import com.arcadia.customperm.perm.BackendKind;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Round trips of every admin interface payload, and the decode caps that keep a modified client from
 * making the server allocate what its packet claims. Pure codec checks: they run as GameTests only
 * because netty and Minecraft's codecs are not on the unit test classpath.
 */
@GameTestHolder(CustomPerm.MODID)
@PrefixGameTestTemplate(false)
public class GuiPayloadCodecGameTest {

    private static final String TEMPLATE = "empty_3x3";

    @GameTest(template = TEMPLATE)
    public static void everyPayloadRoundTrips(GameTestHelper helper) {
        DashboardData data = new DashboardData("internal", true, 140, 3, 2, 4, 1, 5, 7, true,
                List.of(new DashboardData.Alert("CONFIG_LOAD_FAILED", "grades.json is invalid")));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(true,
                new GuiContext(BackendKind.INTERNAL_FALLBACK, GuiArea.ALIASES.bit() | GuiArea.GRADES.bit(), 1, false), data));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(false,
                new GuiContext(BackendKind.LUCKPERMS, 0, 0, true), new CommandsData(List.of(
                        new CommandsData.Row("gamemode", true, true, false, true, false),
                        new CommandsData.Row("oldmod", true, false, true, false, true)), true, true)));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(false,
                new GuiContext(BackendKind.INTERNAL, GuiArea.ALIASES.bit(), 0, true), new AliasesData(List.of(
                        new AliasesData.Alias("heal", List.of("effect give @s instant_health", "say healed"), true, 3, 60, true),
                        new AliasesData.Alias("kit", List.of("give @s bread 8"), false, 0, 0, false)))));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(true,
                new GuiContext(BackendKind.DENY, 0, 2, true), new RateLimitsData(List.of(
                        new RateLimitsData.Rule("gamemode", 3, 3600, true, false, RateLimitsData.Target.EXPOSED_COMMAND),
                        new RateLimitsData.Rule("ghost", 1, 5, false, true, RateLimitsData.Target.NONE)),
                        List.of("heal", "tp"))));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(true,
                new GuiContext(BackendKind.INTERNAL, GuiArea.GRADES.bit(), 0, true), new GradesData(List.of(
                        new GradesData.Grade(new GradesData.Header("vip", -5, "&6[VIP] ", " &7*"),
                                new GradesData.Inheritance(List.of("base", "extra"), List.of("locked")),
                                List.of("customperm.command.fly"), List.of("customperm.command.op"),
                                new GradesData.Members(
                                        List.of(new GradesData.Member("00000000-0000-0000-0000-000000000001", "Alex", true, 604800L, ""),
                                                new GradesData.Member("00000000-0000-0000-0000-000000000004", "Kai", false, 0L, "world=minecraft:the_nether")),
                                        List.of(new GradesData.Member("00000000-0000-0000-0000-000000000003", "Sam", false, 0L, ""))),
                                new GradesData.Details(List.of(new Remaining("allow:customperm.command.fly", 3600L)),
                                        List.of(new ScopedEntry("world=minecraft:the_nether", "deny", "customperm.command.fly"))))),
                        List.of("Alex", "Steve"), "internal", "vip", true,
                        new NameSettings(true, "{prefix}{name}{suffix}"))));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(false,
                new GuiContext(BackendKind.INTERNAL, GuiArea.GRADES.bit(), 0, false), new PlayersData(List.of(
                        new PlayersData.Player("00000000-0000-0000-0000-000000000002", "Steve", false,
                                new PlayersData.Held(List.of("staff"), List.of("vip"), "&d[Me] ", "", List.of(new Remaining("deny:customperm.command.time", 60L)),
                                        List.of(new ScopedEntry("world=minecraft:the_end", "grade", "builder"))),
                                List.of("customperm.command.weather"), List.of("customperm.command.time"))),
                        List.of("Alex", "Steve"), "deny", new NameSettings(false, "{prefix}&8| {name}"),
                        List.of(new PlayersData.Track("staff", List.of("member", "vip", "staff")),
                                new PlayersData.Track("empty", List.of())))));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(true,
                new GuiContext(BackendKind.LUCKPERMS, GuiArea.LUCKPERMS.bit(), 0, true), new LuckPermsData(LuckPermsData.TRACKS)));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(false,
                new GuiContext(BackendKind.INTERNAL, GuiArea.LOGS.bit(), 0, false), new LogsData(
                        List.of(new LogsData.Entry(1_789_000_000_000L, "Alex", "interface", "ALIAS_DELETE heal", false,
                                "You do not have customperm.manage.aliases.")),
                        List.of(new LogsData.Entry(1_789_000_000_001L, "Steve", "player", "/msg [masked]", true, "")),
                        true, false, 30)));
        expectRoundTrip(GuiPagePayload.STREAM_CODEC, new GuiPagePayload(true,
                new GuiContext(BackendKind.LUCKPERMS, GuiArea.GRADES.bit(), 0, true),
                new ImportData(true, true, false, List.of("2 group(s) become grades.", "Nothing is left behind."),
                        new ImportData.Export(true, List.of("1 grade(s) become LuckPerms groups."), true, 3, 7))));
        expectRoundTrip(GuiRequestPayload.STREAM_CODEC, new GuiRequestPayload("dashboard"));
        expectRoundTrip(GuiActionPayload.STREAM_CODEC, new GuiActionPayload("RELOAD", List.of("a", "b c"), "dashboard"));
        expectRoundTrip(GuiActionResultPayload.STREAM_CODEC, GuiActionResultPayload.fail("You do not have x."));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void oversizedClientInputFailsWhileDecoding(GameTestHelper helper) {
        expectDecodeFailure(buf -> {
            GuiCodecs.TEXT.encode(buf, "RELOAD");
            ByteBufCodecs.VAR_INT.encode(buf, 1);
            GuiCodecs.TEXT.encode(buf, "x".repeat(GuiCodecs.CLIENT_ARG_MAX + 1));
            GuiCodecs.TEXT.encode(buf, "dashboard");
        }, "an argument over the length cap");
        expectDecodeFailure(buf -> {
            GuiCodecs.TEXT.encode(buf, "RELOAD");
            ByteBufCodecs.VAR_INT.encode(buf, GuiCodecs.CLIENT_ARGS_MAX + 1);
            for (String arg : Collections.nCopies(GuiCodecs.CLIENT_ARGS_MAX + 1, "a")) GuiCodecs.TEXT.encode(buf, arg);
            GuiCodecs.TEXT.encode(buf, "dashboard");
        }, "more arguments than the list cap");
        expectDecodeFailure(buf -> GuiCodecs.TEXT.encode(buf, "p".repeat(GuiCodecs.CLIENT_NAME_MAX + 1)),
                "a page id over the name cap", GuiRequestPayload.STREAM_CODEC);
        helper.succeed();
    }

    private static <T> void expectRoundTrip(StreamCodec<ByteBuf, T> codec, T value) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, value);
            T decoded = codec.decode(buf);
            if (!value.equals(decoded)) fail("Round trip changed " + value + " into " + decoded);
            if (buf.readableBytes() != 0) fail("Codec left " + buf.readableBytes() + " unread bytes for " + value);
        } finally {
            buf.release();
        }
    }

    private static void expectDecodeFailure(Consumer<ByteBuf> writer, String what) {
        expectDecodeFailure(writer, what, GuiActionPayload.STREAM_CODEC);
    }

    private static void expectDecodeFailure(Consumer<ByteBuf> writer, String what, StreamCodec<ByteBuf, ?> codec) {
        ByteBuf buf = Unpooled.buffer();
        try {
            writer.accept(buf);
            try {
                codec.decode(buf);
            } catch (RuntimeException expected) {
                return;
            }
            fail("Decoding accepted " + what + ".");
        } finally {
            buf.release();
        }
    }

    private static void fail(String msg) {
        throw new GameTestAssertException(msg);
    }
}
