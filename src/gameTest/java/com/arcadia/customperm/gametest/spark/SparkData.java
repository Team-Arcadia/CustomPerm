/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest.spark;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads the files spark saves with {@code --save-to-file}, so the scenario can judge its own results without
 * uploading anything. Both are plain protobuf: {@code .sparkheap} is spark's HeapData, {@code .sparkprofile} its
 * SamplerData. Only the fields the scenario needs are read; field numbers follow spark 1.10's proto files.
 */
public final class SparkData {

    private SparkData() {
    }

    /** Live instances per class name, as spark's heap summary lists them (taken after a full GC). */
    public record Heap(Map<String, Long> instances) {

        public long count(String type) {
            return instances.getOrDefault(type, 0L);
        }

        /** Instances of every class the filter accepts. */
        public long count(Predicate<String> filter) {
            return instances.entrySet().stream().filter(e -> filter.test(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        }
    }

    /** One frame of a sampled thread; {@code ms} is the time spent in it, callees included. */
    public record Frame(String className, String method, double ms, int[] children) {
    }

    /** One sampled thread: its frames, flattened, and the indexes of its top frames. */
    public record Sampled(String name, double ms, List<Frame> frames, int[] roots) {

        /**
         * Time spent in frames the filter accepts, callees included, counting each stack once: below a matching
         * frame nothing is added again, so a recursive or nested match does not inflate the total.
         */
        public double ms(Predicate<Frame> filter) {
            double total = 0;
            Deque<Integer> pending = new ArrayDeque<>();
            for (int root : roots) pending.push(root);
            while (!pending.isEmpty()) {
                Frame frame = frames.get(pending.pop());
                if (filter.test(frame)) {
                    total += frame.ms();
                    continue;
                }
                for (int child : frame.children()) pending.push(child);
            }
            return total;
        }

        /**
         * Time per {@code Class.method} among the frames the filter accepts, callees included. A method is counted
         * once per stack even when it calls itself, so the figures add up to what that method really cost.
         */
        public Map<String, Double> byMethod(Predicate<Frame> filter) {
            Map<String, Double> out = new HashMap<>();
            java.util.Set<String> onPath = new java.util.HashSet<>();
            for (int root : roots) walk(root, filter, onPath, out);
            return out;
        }

        private void walk(int index, Predicate<Frame> filter, java.util.Set<String> onPath, Map<String, Double> out) {
            Frame frame = frames.get(index);
            String key = frame.className().substring(frame.className().lastIndexOf('.') + 1) + "." + frame.method();
            boolean counted = filter.test(frame) && onPath.add(key);
            if (counted) out.merge(key, frame.ms(), Double::sum);
            for (int child : frame.children()) walk(child, filter, onPath, out);
            if (counted) onPath.remove(key);
        }

        public double share(Predicate<Frame> filter) {
            return ms == 0 ? 0 : ms(filter) / ms;
        }
    }

    /** A saved profile: its threads and the number of ticks it covered, 0 when spark did not record them. */
    public record Profile(List<Sampled> threads, long ticks) {

        public Sampled thread(String name) {
            return threads.stream().filter(t -> t.name().equals(name)).findFirst().orElse(null);
        }
    }

    public static Heap heap(Path file) throws IOException {
        Map<String, Long> instances = new HashMap<>();
        Reader data = new Reader(Files.readAllBytes(file));
        while (data.more()) {
            int field = data.tag();
            if (field != 2) {
                data.skip();
                continue;
            }
            Reader entry = data.message();
            long count = 0;
            String type = null;
            while (entry.more()) {
                switch (entry.tag()) {
                    case 2 -> count = entry.varint();
                    case 4 -> type = entry.string();
                    default -> entry.skip();
                }
            }
            if (type != null) instances.merge(type, count, Long::sum);
        }
        return new Heap(instances);
    }

    public static Profile profile(Path file) throws IOException {
        List<Sampled> threads = new ArrayList<>();
        long ticks = 0;
        Reader data = new Reader(Files.readAllBytes(file));
        while (data.more()) {
            switch (data.tag()) {
                case 1 -> {
                    Reader metadata = data.message();
                    while (metadata.more()) {
                        if (metadata.tag() == 12) ticks = metadata.varint();
                        else metadata.skip();
                    }
                }
                case 2 -> threads.add(thread(data.message()));
                default -> data.skip();
            }
        }
        return new Profile(threads, ticks);
    }

    private static Sampled thread(Reader node) {
        String name = "";
        double ms = 0;
        List<Frame> frames = new ArrayList<>();
        int[] roots = new int[0];
        while (node.more()) {
            switch (node.tag()) {
                case 1 -> name = node.string();
                case 3 -> frames.add(frame(node.message()));
                case 4 -> ms = sum(node.doubles());
                case 5 -> roots = concat(roots, node.ints());
                default -> node.skip();
            }
        }
        return new Sampled(name, ms, frames, roots);
    }

    private static Frame frame(Reader node) {
        String className = "";
        String method = "";
        double ms = 0;
        int[] children = new int[0];
        while (node.more()) {
            switch (node.tag()) {
                case 3 -> className = node.string();
                case 4 -> method = node.string();
                case 8 -> ms = sum(node.doubles());
                case 9 -> children = concat(children, node.ints());
                default -> node.skip();
            }
        }
        return new Frame(className, method, ms, children);
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double v : values) total += v;
        return total;
    }

    private static int[] concat(int[] a, int[] b) {
        if (a.length == 0) return b;
        int[] out = new int[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /** A protobuf wire-format cursor, just enough for spark's messages. */
    private static final class Reader {
        private final byte[] bytes;
        private int pos;
        private final int end;
        private int wire;

        Reader(byte[] bytes) {
            this(bytes, 0, bytes.length);
        }

        private Reader(byte[] bytes, int start, int end) {
            this.bytes = bytes;
            this.pos = start;
            this.end = end;
        }

        boolean more() {
            return pos < end;
        }

        int tag() {
            long key = varint();
            wire = (int) (key & 7);
            return (int) (key >>> 3);
        }

        long varint() {
            long result = 0;
            for (int shift = 0; ; shift += 7) {
                byte b = bytes[pos++];
                result |= (long) (b & 0x7f) << shift;
                if (b >= 0) return result;
            }
        }

        private int length() {
            int length = (int) varint();
            if (length < 0 || pos + length > end) throw new IllegalStateException("Truncated spark file");
            return length;
        }

        Reader message() {
            int length = length();
            Reader inner = new Reader(bytes, pos, pos + length);
            pos += length;
            return inner;
        }

        String string() {
            int length = length();
            String s = new String(bytes, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return s;
        }

        /** A repeated double, packed or not. */
        double[] doubles() {
            if (wire == 1) {
                double v = ByteBuffer.wrap(bytes, pos, 8).order(ByteOrder.LITTLE_ENDIAN).getDouble();
                pos += 8;
                return new double[]{v};
            }
            int length = length();
            ByteBuffer buffer = ByteBuffer.wrap(bytes, pos, length).order(ByteOrder.LITTLE_ENDIAN);
            double[] out = new double[length / 8];
            for (int i = 0; i < out.length; i++) out[i] = buffer.getDouble();
            pos += length;
            return out;
        }

        /** A repeated int32, packed or not. */
        int[] ints() {
            if (wire == 0) return new int[]{(int) varint()};
            Reader packed = message();
            List<Integer> values = new ArrayList<>();
            while (packed.more()) values.add((int) packed.varint());
            return values.stream().mapToInt(Integer::intValue).toArray();
        }

        void skip() {
            switch (wire) {
                case 0 -> varint();
                case 1 -> pos += 8;
                case 2 -> {
                    // Not "pos += length()": that reads pos before length() moves it past the varint.
                    int length = length();
                    pos += length;
                }
                case 5 -> pos += 4;
                default -> throw new IllegalStateException("Unsupported wire type " + wire);
            }
        }
    }
}
