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
 * What the pages need to show the {@code servers} list of a command, an alias or a rate limit: this server's
 * cluster name, the members it hears, and which parts it shares.
 *
 * @param here    this server's cluster name; empty outside a cluster, where lists are not read
 * @param members this server and the members it heard, sorted; empty while it is in step with no cluster
 * @param shared  one bit per part this server shares: {@link #COMMANDS}, {@link #ALIASES}, {@link #RATE_LIMITS}
 */
public record ClusterView(String here, List<String> members, int shared) {

    public static final int COMMANDS = 1;
    public static final int ALIASES = 2;
    public static final int RATE_LIMITS = 4;

    public static final ClusterView NONE = new ClusterView("", List.of(), COMMANDS | ALIASES | RATE_LIMITS);

    public static final StreamCodec<ByteBuf, ClusterView> CODEC = StreamCodec.composite(
            GuiCodecs.TEXT, ClusterView::here,
            GuiCodecs.list(GuiCodecs.TEXT, GuiCodecs.SERVER_LIST_MAX), ClusterView::members,
            ByteBufCodecs.VAR_INT, ClusterView::shared,
            ClusterView::new);

    public boolean inCluster() {
        return !here.isEmpty();
    }

    public boolean shares(int part) {
        return (shared & part) != 0;
    }
}
