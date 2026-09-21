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
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * What the interface's fields propose, every page alike: the names that exist on the server. Sent apart
 * from the pages, and only when it changed, since a node list can weigh more than the page it serves.
 *
 * @param nodes      permission nodes the server knows of
 * @param grades     grade names
 * @param players    names of online players and of players who joined before
 * @param contexts   contexts as the fields take them: {@code world=the_nether}, {@code server=hub}
 * @param commands   dispatcher roots outside {@code /customperm}
 * @param aliases    alias names
 * @param servers    this server and the cluster members it heard; empty outside a cluster
 * @param metaKeys   meta keys set anywhere
 * @param metaValues values in use, each as {@code key}, a newline, then the value
 * @param chatTexts  prefix and suffix texts in use, codes included
 */
public record GuiVocabulary(List<String> nodes, List<String> grades, List<String> players, List<String> contexts,
                            List<String> commands, List<String> aliases, List<String> servers,
                            List<String> metaKeys, List<String> metaValues, List<String> chatTexts) {

    public static final GuiVocabulary EMPTY = new GuiVocabulary(List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    private static final StreamCodec<ByteBuf, List<String>> NAMES =
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX);

    public static final StreamCodec<ByteBuf, GuiVocabulary> CODEC = StreamCodec.of(
            (buf, v) -> v.lists().forEach(list -> NAMES.encode(buf, list)),
            buf -> new GuiVocabulary(NAMES.decode(buf), NAMES.decode(buf), NAMES.decode(buf), NAMES.decode(buf),
                    NAMES.decode(buf), NAMES.decode(buf), NAMES.decode(buf), NAMES.decode(buf), NAMES.decode(buf),
                    NAMES.decode(buf)));

    /** Every list cut to what one list carries, so the packet always encodes. */
    public GuiVocabulary {
        nodes = cap(nodes);
        grades = cap(grades);
        players = cap(players);
        contexts = cap(contexts);
        commands = cap(commands);
        aliases = cap(aliases);
        servers = cap(servers);
        metaKeys = cap(metaKeys);
        metaValues = cap(metaValues);
        chatTexts = cap(chatTexts);
    }

    private static List<String> cap(List<String> list) {
        return list.size() <= GuiCodecs.SERVER_LIST_MAX ? List.copyOf(list)
                : List.copyOf(list.subList(0, GuiCodecs.SERVER_LIST_MAX));
    }

    private List<List<String>> lists() {
        return List.of(nodes, grades, players, contexts, commands, aliases, servers, metaKeys, metaValues, chatTexts);
    }

    /** The values in use for {@code key}. */
    public List<String> valuesOf(String key) {
        String head = key + "\n";
        List<String> values = new ArrayList<>();
        for (String entry : metaValues) {
            if (entry.startsWith(head)) values.add(entry.substring(head.length()));
        }
        return values;
    }
}
