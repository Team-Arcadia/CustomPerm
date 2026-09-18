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
 * @param names        whether names carry their prefix, for the Chat tab's preview
 */
public record GradesData(List<Grade> grades, List<String> knownPlayers, String fallbackMode, String defaultGrade,
                         boolean gateAll, NameSettings names) implements GuiPageData {

    /** Most nodes carried per grade, per kind. */
    public static final int NODES_MAX = 1024;

    /**
     * A player assigned to a grade, or refusing it; {@code name} is the UUID when no name is known,
     * {@code remaining} the seconds the assignment or the refusal has left, 0 when it is permanent, and
     * {@code context} the world it is held in, empty when it is held everywhere.
     */
    public record Member(String uuid, String name, boolean online, long remaining, String context) {
        public static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Member::uuid,
                GuiCodecs.TEXT, Member::name,
                ByteBufCodecs.BOOL, Member::online,
                ByteBufCodecs.VAR_LONG, Member::remaining,
                GuiCodecs.TEXT, Member::context,
                Member::new);
    }

    /**
     * {@code weight} breaks ties between two grades covering a node just as specifically; 0 for most.
     * {@code parents} are the grades this one inherits, nearest first, {@code deniedParents} the ones it
     * refuses wherever they would be inherited. {@code refusers} are the players who refuse this grade.
     *
     * <p>Two records rather than one wider one: the codec composes at most six components, and inheritance
     * and refusal read as two lists everywhere else too.
     */
    public record Grade(Header header, Inheritance inheritance, List<String> allow, List<String> deny,
                        Members players, Details details) {
        public static final StreamCodec<ByteBuf, Grade> CODEC = StreamCodec.composite(
                Header.CODEC, Grade::header,
                Inheritance.CODEC, Grade::inheritance,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Grade::allow,
                GuiCodecs.list(GuiCodecs.TEXT, NODES_MAX), Grade::deny,
                Members.CODEC, Grade::players,
                Details.CODEC, Grade::details,
                Grade::new);

        public String name() {
            return header.name();
        }

        public int weight() {
            return header.weight();
        }

        /** Raw text with its codes, empty for none. */
        public String prefix() {
            return header.prefix();
        }

        public String suffix() {
            return header.suffix();
        }

        public List<String> parents() {
            return inheritance.parents();
        }

        public List<String> deniedParents() {
            return inheritance.denied();
        }

        public List<Member> members() {
            return players.assigned();
        }

        public List<Member> refusers() {
            return players.refusing();
        }

        /**
         * Seconds left on an entry of this grade, 0 when it is permanent: a node ({@code "allow"} or
         * {@code "deny"}), a parent ({@code "parent"}) or a refused grade ({@code "refusedParent"}).
         */
        public long remaining(String kind, String node) {
            return Remaining.of(details.timers(), kind, node);
        }

        /** Nodes this grade gives in one world only. */
        public List<ScopedEntry> scoped() {
            return details.scoped();
        }
    }

    /** What a grade's nodes carry beyond their name: the time a temporary one has left, the world of a contextual one. */
    public record Details(List<Remaining> timers, List<ScopedEntry> scoped) {
        public static final Details NONE = new Details(List.of(), List.of());

        public static final StreamCodec<ByteBuf, Details> CODEC = StreamCodec.composite(
                Remaining.LIST, Details::timers,
                ScopedEntry.LIST, Details::scoped,
                Details::new);
    }

    /** A grade's own scalars: its name, its weight, and the prefix and suffix it gives, empty for none. */
    public record Header(String name, int weight, String prefix, String suffix) {
        public static final StreamCodec<ByteBuf, Header> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Header::name,
                ByteBufCodecs.VAR_INT, Header::weight,
                GuiCodecs.TEXT, Header::prefix,
                GuiCodecs.TEXT, Header::suffix,
                Header::new);
    }

    /** What a grade inherits, and what it refuses to inherit. */
    public record Inheritance(List<String> parents, List<String> denied) {
        public static final StreamCodec<ByteBuf, Inheritance> CODEC = StreamCodec.composite(
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Inheritance::parents,
                GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), Inheritance::denied,
                Inheritance::new);
    }

    /** The players assigned to a grade, and the players who refuse it. */
    public record Members(List<Member> assigned, List<Member> refusing) {
        public static final StreamCodec<ByteBuf, Members> CODEC = StreamCodec.composite(
                GuiCodecs.list(Member.CODEC, GuiCodecs.SERVER_LIST_MAX), Members::assigned,
                GuiCodecs.list(Member.CODEC, GuiCodecs.SERVER_LIST_MAX), Members::refusing,
                Members::new);
    }

    public static final StreamCodec<ByteBuf, GradesData> CODEC = StreamCodec.composite(
            GuiCodecs.list(Grade.CODEC, GuiCodecs.SERVER_LIST_MAX), GradesData::grades,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), GradesData::knownPlayers,
            GuiCodecs.TEXT, GradesData::fallbackMode,
            GuiCodecs.TEXT, GradesData::defaultGrade,
            ByteBufCodecs.BOOL, GradesData::gateAll,
            NameSettings.CODEC, GradesData::names,
            GradesData::new);

    @Override
    public GuiPage page() {
        return GuiPage.GRADES;
    }
}
