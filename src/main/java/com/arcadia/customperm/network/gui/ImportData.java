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
 * The import page, both ways: from LuckPerms and, on its second tab, to LuckPerms. Each is in whichever of
 * its two steps it is in. Before a preview the report is empty and the page explains what would happen;
 * after one it carries the report the server built, which is what the admin confirms.
 *
 * <p>The plans themselves stay on the server. What travels is what the admin reads, so a client cannot
 * hand back a plan of its own making.
 *
 * @param running        whether LuckPerms is the active backend, without which there is nothing to read
 * @param report         the lines of the last import preview, empty when there is none
 * @param exposeCommands what the previewed plan was read with, so the page comes back on the same footing
 * @param export         the export tab
 */
public record ImportData(boolean running, boolean previewed, boolean exposeCommands, List<String> report,
                         Export export) implements GuiPageData {

    /** Most report lines carried; a report longer than this says more about the source than the import. */
    public static final int REPORT_MAX = 256;

    /**
     * The export tab. {@code exporting} is true while any export runs, whoever started it, with how far it
     * is: one runs at a time, and the page says so rather than offering a second.
     */
    public record Export(boolean previewed, List<String> report, boolean exporting, int done, int total) {

        public static final Export NONE = new Export(false, List.of(), false, 0, 0);

        public static final StreamCodec<ByteBuf, Export> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Export::previewed,
                GuiCodecs.list(GuiCodecs.TEXT, REPORT_MAX), Export::report,
                ByteBufCodecs.BOOL, Export::exporting,
                ByteBufCodecs.VAR_INT, Export::done,
                ByteBufCodecs.VAR_INT, Export::total,
                Export::new);
    }

    public static final StreamCodec<ByteBuf, ImportData> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ImportData::running,
            ByteBufCodecs.BOOL, ImportData::previewed,
            ByteBufCodecs.BOOL, ImportData::exposeCommands,
            GuiCodecs.list(GuiCodecs.TEXT, REPORT_MAX), ImportData::report,
            Export.CODEC, ImportData::export,
            ImportData::new);

    @Override
    public GuiPage page() {
        return GuiPage.IMPORT;
    }
}
