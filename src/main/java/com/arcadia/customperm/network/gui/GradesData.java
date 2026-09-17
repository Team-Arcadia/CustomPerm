/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.gui;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Grades page: every internal grade with its ALLOW and DENY nodes and the players assigned to it,
 * plus the names of players known to the server for assignment.
 *
 * @param knownPlayers online players and players who joined before, sorted; the only names a grade
 *                     can be assigned to
 * @param fallbackMode {@code luckPermsFallbackMode}: whether grades take over when LuckPerms fails
 * @param defaultGrade grade applied to every player, empty for none
 * @param gateAll      whether every command reads its node ({@code gateAllCommands} in effect)
 */
public record GradesData(List<Grade> grades, List<String> knownPlayers, String fallbackMode, String defaultGrade,
                         boolean gateAll) implements GuiPageData {

    /** Most nodes carried per grade, per kind. */
    public static final int NODES_MAX = 1024;

    /** A player assigned to a grade; {@code name} is the UUID when no name is known. */
    public record Member(String uuid, String name, boolean online) {
        public static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Member::uuid,
                GuiCodecs.TEXT, Member::name,
                ByteBufCodecs.BOOL, Member::online,
                Member::new);
    }

    /** {@code weight} breaks ties between two grades covering a node just as specifically; 0 for most. */
    public record Grade(String name, int weight, List<String> allow, List<String> deny, List<Member> members) {
        public static final StreamCodec<ByteBuf, Grade> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Grade::name,
                ByteBufCodecs.VAR_INT, Grade::weight,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Grade::allow,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Grade::deny,
                GuiCodecs.list(Member.CODEC, GuiCodecs.SERVER_LIST_MAX), Grade::members,
                Grade::new);
    }

    public static final StreamCodec<ByteBuf, GradesData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Grade.CODEC, GuiCodecs.SERVER_LIST_MAX), GradesData::grades,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), GradesData::knownPlayers,
            GuiCodecs.TEXT, GradesData::fallbackMode,
            GuiCodecs.TEXT, GradesData::defaultGrade,
            ByteBufCodecs.BOOL, GradesData::gateAll,
            GradesData::new);

    @Override
    public GuiPage page() {
        return GuiPage.GRADES;
    }
}
