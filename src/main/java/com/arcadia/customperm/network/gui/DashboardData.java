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
 * Dashboard page: the same facts as {@code /customperm status}, plus the text of every active alert.
 *
 * @param fallbackMode       {@code luckPermsFallbackMode} setting, relevant only when LuckPerms is installed
 * @param luckPermsInstalled whether the LuckPerms mod is loaded, active or not
 * @param savesSuspended     true when a config file failed to load and changes are kept in memory only
 */
public record DashboardData(String fallbackMode, boolean luckPermsInstalled, int dispatcherCommands,
                            int exposedCommands, int aliases, int rateLimits, int rateLimitsEnabled,
                            int grades, int playersWithGrades, boolean savesSuspended,
                            List<Alert> alerts, String cluster) implements GuiPageData {

    /** One active admin alert. {@code key} is the {@code AdminAlerts.Key} name. */
    public record Alert(String key, String message) {
        public static final StreamCodec<ByteBuf, Alert> CODEC = StreamCodec.composite(
                GuiCodecs.TEXT, Alert::key,
                GuiCodecs.TEXT, Alert::message,
                Alert::new);
    }

    public static final StreamCodec<ByteBuf, DashboardData> CODEC = StreamCodec.of(
            (buf, d) -> {
                GuiCodecs.TEXT.encode(buf, d.fallbackMode);
                ByteBufCodecs.BOOL.encode(buf, d.luckPermsInstalled);
                ByteBufCodecs.VAR_INT.encode(buf, d.dispatcherCommands);
                ByteBufCodecs.VAR_INT.encode(buf, d.exposedCommands);
                ByteBufCodecs.VAR_INT.encode(buf, d.aliases);
                ByteBufCodecs.VAR_INT.encode(buf, d.rateLimits);
                ByteBufCodecs.VAR_INT.encode(buf, d.rateLimitsEnabled);
                ByteBufCodecs.VAR_INT.encode(buf, d.grades);
                ByteBufCodecs.VAR_INT.encode(buf, d.playersWithGrades);
                ByteBufCodecs.BOOL.encode(buf, d.savesSuspended);
                GuiCodecs.list(Alert.CODEC, GuiCodecs.ALERTS_MAX).encode(buf, d.alerts);
                GuiCodecs.TEXT.encode(buf, d.cluster);
            },
            buf -> new DashboardData(
                    GuiCodecs.TEXT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    GuiCodecs.list(Alert.CODEC, GuiCodecs.ALERTS_MAX).decode(buf),
                    GuiCodecs.TEXT.decode(buf)));

    @Override
    public GuiPage page() {
        return GuiPage.DASHBOARD;
    }
}
