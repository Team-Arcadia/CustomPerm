/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.client.gui.lp;

import com.arcadia.customperm.network.lp.LpDto;
import com.arcadia.customperm.network.lp.LpEditOp;
import com.arcadia.customperm.network.lp.RequestLpSyncPayload;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Track editor: the promotion ladders that {@code /lp user promote} walks. A track is an ordered
 * list of groups, so the rows expose the order explicitly (append, insert at a position, remove)
 * rather than a drag handle the Minecraft GUI would not carry well.
 */
public final class LpTracksScreen extends AbstractLpScreen {

    private static final String INPUT_NEW_TRACK = "newTrack";
    private static final String INPUT_GROUP = "trackGroup";
    private static final String INPUT_POSITION = "trackPosition";

    public LpTracksScreen() {
        super(Component.literal("LuckPerms - Tracks"));
    }

    @Override
    protected String scope() {
        return RequestLpSyncPayload.SCOPE_TRACKS;
    }

    @Override
    protected String templateId() {
        return "customperm:ui/lp_tracks";
    }

    @Override
    protected void fill(LpDto.Snapshot snapshot, Map<String, String> data,
                        Map<String, Runnable> handlers, Map<String, Consumer<String>> submits) {
        List<LpDto.TrackDto> tracks = snapshot.tracks();
        data.put("noTracks", String.valueOf(tracks.isEmpty()));
        data.put("tracks", String.valueOf(tracks.size()));

        handlers.put("createTrack", this::createTrack);
        submits.put(INPUT_NEW_TRACK, text -> createTrack());

        for (int i = 0; i < tracks.size(); i++) {
            LpDto.TrackDto track = tracks.get(i);
            String name = track.name();
            data.put("track.name." + i, name);
            data.put("track.groups." + i, LpFormat.join(track.groups()));
            data.put("track.size." + i, String.valueOf(track.groups().size()));

            String appendKey = "track.append." + i;
            String insertKey = "track.insert." + i;
            String removeGroupKey = "track.removegroup." + i;
            String deleteKey = "track.delete." + i;
            data.put("track.appendAction." + i, appendKey);
            data.put("track.insertAction." + i, insertKey);
            data.put("track.removeGroupAction." + i, removeGroupKey);
            data.put("track.deleteAction." + i, deleteKey);

            // The group and position fields are shared across rows: a track edit is one action
            // at a time, and per-row fields would leave a dozen empty boxes on screen.
            handlers.put(appendKey, () -> withGroup(group -> edit(LpEditOp.TRACK_APPEND, name, group)));
            handlers.put(insertKey, () -> withGroup(group ->
                    edit(LpEditOp.TRACK_INSERT, name, group, position())));
            handlers.put(removeGroupKey, () -> withGroup(group -> edit(LpEditOp.TRACK_REMOVE, name, group)));
            handlers.put(deleteKey, () -> edit(LpEditOp.TRACK_DELETE, name));
        }
    }

    private void createTrack() {
        String name = value(INPUT_NEW_TRACK);
        if (name.isEmpty()) return;
        edit(LpEditOp.TRACK_CREATE, name);
        clearInput(INPUT_NEW_TRACK);
    }

    private void withGroup(java.util.function.Consumer<String> action) {
        String group = value(INPUT_GROUP);
        if (group.isEmpty()) return;
        action.accept(group);
    }

    /** Insert position, defaulting to the head of the track when the field is left empty. */
    private String position() {
        String raw = value(INPUT_POSITION);
        return raw.isEmpty() ? "0" : raw;
    }
}
