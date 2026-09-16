/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package com.arcadia.customperm.notify;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminAlertsTest {

    @Test
    void shouldNotify_onlyWhenAlertIsNewOrChanged() {
        AdminAlerts alerts = new AdminAlerts();

        assertTrue(alerts.raise(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "down"), "first raise must notify");
        assertFalse(alerts.raise(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "down"),
                "the same alert raised again (e.g. on every permission check) must not notify");
        assertTrue(alerts.raise(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "down, different reason"),
                "a changed message must notify");
    }

    @Test
    void shouldReportResolution_onlyForActiveAlert() {
        AdminAlerts alerts = new AdminAlerts();

        assertFalse(alerts.clear(AdminAlerts.Key.CONFIG_LOAD_FAILED), "clearing an inactive alert is not a resolution");
        alerts.raise(AdminAlerts.Key.CONFIG_LOAD_FAILED, "bad json");
        assertTrue(alerts.isActive(AdminAlerts.Key.CONFIG_LOAD_FAILED));
        assertTrue(alerts.clear(AdminAlerts.Key.CONFIG_LOAD_FAILED));
        assertFalse(alerts.isActive(AdminAlerts.Key.CONFIG_LOAD_FAILED));
        assertTrue(alerts.raise(AdminAlerts.Key.CONFIG_LOAD_FAILED, "bad json"),
                "an alert that comes back after resolution must notify again");
    }

    @Test
    void snapshot_isOrderedAndDetached() {
        AdminAlerts alerts = new AdminAlerts();
        assertTrue(alerts.snapshot().isEmpty(), "empty snapshot must not throw");

        alerts.raise(AdminAlerts.Key.CONFIG_LOAD_FAILED, "config");
        alerts.raise(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, "lp");
        var snapshot = alerts.snapshot();

        assertEquals(List.of(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE, AdminAlerts.Key.CONFIG_LOAD_FAILED),
                List.copyOf(snapshot.keySet()), "snapshot follows declaration order");
        alerts.clear(AdminAlerts.Key.LUCKPERMS_UNAVAILABLE);
        assertEquals(2, snapshot.size(), "snapshot must not change after the source does");
    }
}
