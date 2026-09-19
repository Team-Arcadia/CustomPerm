/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import com.arcadia.customperm.cluster.ClusterStore.Change;
import com.arcadia.customperm.cluster.ClusterStore.Row;
import com.arcadia.customperm.cluster.ClusterStore.StoreException;
import com.arcadia.customperm.cluster.ClusterStore.WriteResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keeps one part of this server's configuration in step with the store.
 *
 * <p>What the store holds, as this server last saw it, is kept per holder ({@link #synced}). After a change,
 * {@link #publish} writes the holders that differ from it, each against the version seen; the store refuses the
 * whole write when another server changed one of them meanwhile, and the change is then undone in memory, those
 * holders showing the other server's version. Memory therefore always matches what the store accepted. Rows other
 * servers write arrive through {@link #fetch} (any thread) and {@link #apply} (the thread that owns the
 * configuration).</p>
 *
 * <p>{@link #lastSeq} moves only with what {@link #fetch} returned, never with this server's own writes: a write
 * numbered 10 may commit before another server's 9 is seen, and moving to 10 would skip 9.</p>
 */
public final class PartSync<T> {

    /** The server side of a part: where its configuration lives, and what to do when it changed. */
    public interface Host<T> {
        T current();

        /** {@code holders} of the configuration were replaced from the store. */
        void changed(T config, Set<String> holders);

        /** How a holder is named to an admin: {@code grade vip}, {@code player Steve}. */
        String label(String holder);
    }

    public enum Start { SEEDED, ADOPTED_SAME, ADOPTED_REPLACED }

    private final PartCodec<T> codec;
    private final ClusterStore store;
    private final String server;
    private final Host<T> host;
    private final Map<String, Row> synced = new HashMap<>();
    private volatile long lastSeq;

    public PartSync(PartCodec<T> codec, ClusterStore store, String server, Host<T> host) {
        this.codec = codec;
        this.store = store;
        this.server = server;
        this.host = host;
    }

    public String part() {
        return codec.part();
    }

    /**
     * First contact. An empty store is filled from this server's configuration; a store already filled wins, and
     * this server's configuration is replaced by it.
     */
    public Start start() throws StoreException {
        absorb(store.changesSince(codec.part(), 0), true);
        Map<String, String> remote = liveBodies();
        Map<String, String> local = codec.split(host.current());
        if (remote.isEmpty()) {
            List<Change> changes = new ArrayList<>();
            local.forEach((holder, body) -> changes.add(new Change(holder, version(holder), body)));
            if (changes.isEmpty()) return Start.SEEDED;
            WriteResult result = store.write(codec.part(), changes, server);
            if (result.ok()) {
                absorb(result.written(), false);
                return Start.SEEDED;
            }
            // Another server seeded it at the same moment: take what it wrote.
            absorb(store.changesSince(codec.part(), lastSeq), true);
            remote = liveBodies();
        }
        if (remote.equals(local)) return Start.ADOPTED_SAME;
        Set<String> holders = new LinkedHashSet<>(local.keySet());
        holders.addAll(remote.keySet());
        restore(holders);
        return Start.ADOPTED_REPLACED;
    }

    /**
     * Writes what changed since the store was last seen. Null when written or when nothing changed; otherwise the
     * refusal to show, the change having been undone.
     */
    public String publish() {
        Map<String, String> local = codec.split(host.current());
        List<Change> changes = new ArrayList<>();
        local.forEach((holder, body) -> {
            if (!body.equals(liveBody(holder))) changes.add(new Change(holder, version(holder), body));
        });
        synced.forEach((holder, row) -> {
            if (row.body() != null && !local.containsKey(holder)) changes.add(new Change(holder, row.version(), null));
        });
        if (changes.isEmpty()) return null;
        Set<String> touched = new LinkedHashSet<>();
        changes.forEach(c -> touched.add(c.holder()));
        WriteResult result;
        try {
            result = store.write(codec.part(), changes, server);
        } catch (StoreException e) {
            restore(touched);
            throw new Unreachable(e);
        }
        if (result.ok()) {
            absorb(result.written(), false);
            return null;
        }
        absorb(result.conflicts(), false);
        restore(touched);
        Row first = result.conflicts().get(0);
        String by = first.updatedBy() == null || first.updatedBy().isEmpty() ? "another server" : first.updatedBy();
        return host.label(first.holder()) + " was changed on " + by + " meanwhile. This server now shows that "
                + "change; check it and try again.";
    }

    /** Rows written since the last ones applied. Any thread; one call at a time. */
    public List<Row> fetch() throws StoreException {
        return store.changesSince(codec.part(), lastSeq);
    }

    /** Applies fetched rows. The thread that owns the configuration. True when something changed here. */
    public boolean apply(List<Row> rows) {
        Set<String> changed = new LinkedHashSet<>();
        for (Row row : rows) {
            Row known = synced.get(row.holder());
            if (known == null || row.version() > known.version()) {
                if (known == null ? row.body() != null : !Objects.equals(known.body(), row.body())) {
                    changed.add(row.holder());
                }
                synced.put(row.holder(), row);
            }
            if (row.seq() > lastSeq) lastSeq = row.seq();
        }
        if (changed.isEmpty()) return false;
        restore(changed);
        return true;
    }

    public long lastSeq() {
        return lastSeq;
    }

    /** Thrown by {@link #publish} when the store cannot be reached; the change was undone. */
    public static final class Unreachable extends RuntimeException {
        public Unreachable(StoreException cause) {
            super(cause.getMessage(), cause);
        }
    }

    /** Sets {@code holders} in memory to what the store holds for them, as last seen. */
    private void restore(Set<String> holders) {
        T config = host.current();
        for (String holder : holders) codec.patch(config, holder, liveBody(holder));
        codec.afterPatch(config);
        host.changed(config, holders);
    }

    private void absorb(List<Row> rows, boolean fromFetch) {
        for (Row row : rows) {
            if (row == null) continue;
            Row known = synced.get(row.holder());
            if (known == null || row.version() >= known.version()) synced.put(row.holder(), row);
            if (fromFetch && row.seq() > lastSeq) lastSeq = row.seq();
        }
    }

    private long version(String holder) {
        Row row = synced.get(holder);
        return row == null ? 0 : row.version();
    }

    private String liveBody(String holder) {
        Row row = synced.get(holder);
        return row == null ? null : row.body();
    }

    private Map<String, String> liveBodies() {
        Map<String, String> bodies = new HashMap<>();
        synced.forEach((holder, row) -> {
            if (row.body() != null) bodies.put(holder, row.body());
        });
        return bodies;
    }
}
