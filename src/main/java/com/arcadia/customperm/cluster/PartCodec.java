/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.cluster;

import java.util.Map;

/**
 * How one shared part of the configuration is cut into rows, one per holder, so two servers changing two
 * different holders never conflict. Bodies are canonical JSON: the same content gives the same text.
 */
public interface PartCodec<T> {

    /** Name of the part in the store, such as {@code grades}. */
    String part();

    /** Every holder of {@code config} and its body. */
    Map<String, String> split(T config);

    /** Puts one holder's body into {@code config}, or removes the holder when {@code body} is null. */
    void patch(T config, String holder, String body);

    /** Called once after a batch of patches, for what holds across holders (normalisation, caches). */
    void afterPatch(T config);

    /** A new, empty configuration. */
    T empty();
}
