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
 * The import page, in whichever of its two steps it is in. Before a preview the report is empty and the
 * page explains what an import would do; after one it carries the report the server built, which is what
 * the admin confirms.
 *
 * <p>The plan itself stays on the server. What travels is what the admin reads, so a client cannot hand
 * back a plan of its own making.
 *
 * @param running        whether LuckPerms is the active backend, without which there is nothing to read
 * @param report         the lines of the last preview, empty when there is none
 * @param exposeCommands what the previewed plan was read with, so the page comes back on the same footing
 */
public record ImportData(boolean running, boolean previewed, boolean exposeCommands, List<String> report)
        implements GuiPageData {

    /** Most report lines carried; a report longer than this says more about the source than the import. */
    public static final int REPORT_MAX = 256;

    public static final StreamCodec<ByteBuf, ImportData> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ImportData::running,
            ByteBufCodecs.BOOL, ImportData::previewed,
            ByteBufCodecs.BOOL, ImportData::exposeCommands,
            GuiCodecs.list(GuiCodecs.TEXT, REPORT_MAX), ImportData::report,
            ImportData::new);

    @Override
    public GuiPage page() {
        return GuiPage.IMPORT;
    }
}
