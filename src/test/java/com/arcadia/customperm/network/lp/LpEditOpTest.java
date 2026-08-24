/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.network.lp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The edit vocabulary is the contract between the editor screens and
 * {@code LuckPermsAdminService}: the screens build an argument list positionally, the service
 * reads it positionally, and {@code LpRequestHandler} only checks the count. A wrong arity would
 * therefore not fail at compile time — it would send a well-formed packet that indexes the wrong
 * argument, so it is worth pinning here.
 */
class LpEditOpTest {

    @Test
    void everyOperationDeclaresAUsableArity() {
        for (LpEditOp op : LpEditOp.values()) {
            assertTrue(op.arity() >= 1, op + " must take at least the holder it targets");
            assertTrue(op.arity() <= LpEditPayload.MAX_ARGS,
                    op + " exceeds the argument cap enforced by the payload codec");
        }
    }

    @Test
    void userOperationsAreRecognisedByName() {
        for (LpEditOp op : LpEditOp.values()) {
            assertEquals(op.name().startsWith("USER_"), op.isUserOp(),
                    op + " is misclassified, which would send it down the wrong holder path");
        }
    }

    @Test
    void unknownOperationNamesAreRejectedRatherThanThrowing() {
        assertNull(LpEditOp.fromName("GROUP_DROP_DATABASE"));
        assertNull(LpEditOp.fromName(""));
        assertNull(LpEditOp.fromName("group_create"), "resolution must be case-sensitive");
    }

    @Test
    void knownOperationNamesRoundTrip() {
        for (LpEditOp op : LpEditOp.values()) {
            assertEquals(op, LpEditOp.fromName(op.name()));
        }
    }

    /**
     * Pins the argument layouts the screens actually build. These four are the ones with the
     * most positional arguments, and therefore the ones where an off-by-one would be silent.
     */
    @Test
    void arityMatchesTheDocumentedArgumentLayout() {
        assertEquals(5, LpEditOp.GROUP_PERM_ADD.arity());   // group, node, value, contexts, duration
        assertEquals(5, LpEditOp.USER_PERM_ADD.arity());    // uuid, node, value, contexts, duration
        assertEquals(4, LpEditOp.USER_PARENT_ADD.arity());  // uuid, group, contexts, duration
        assertEquals(3, LpEditOp.TRACK_INSERT.arity());     // track, group, index
    }
}
