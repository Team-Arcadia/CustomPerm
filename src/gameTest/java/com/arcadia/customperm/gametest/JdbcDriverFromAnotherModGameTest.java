/*
 * CustomPerm - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-CustomPerm-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */
package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.cluster.JdbcDrivers;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.sql.Driver;

/**
 * CustomPerm ships no database driver: cluster mode's direct connection instantiates by name whichever one another
 * mod already loaded. Unit tests cover the lookup against the test classpath; only a server carrying a real mod
 * proves the class of a nested jar is reachable from CustomPerm's own class loader.
 *
 * <p>These run in the Arcadia mode only, where {@code run/gametest-arcadia/mods} holds Arcadia Lib and nothing
 * else. In the other modes they succeed without asserting, the way {@link GameTestModeTest} reads its own mode.</p>
 */
@GameTestHolder(JdbcDriverFromAnotherModGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public class JdbcDriverFromAnotherModGameTest {

    /**
     * These tests live in their own GameTest namespace so the Arcadia run can enable them alone. Arcadia Lib
     * sends a mandatory payload on every player join, which kills every test that joins a test player; keeping
     * these apart is what lets the mode stay green without hiding that.
     */
    public static final String NAMESPACE = "customperm_driver";

    /** Set by the Arcadia run configuration in build.gradle. */
    public static final String MODE_PROPERTY = "customperm.gametest.arcadia";

    /** The driver Arcadia Lib carries, as a nested jar of its own. */
    private static final String MYSQL = "com.mysql.cj.jdbc.Driver";

    private static boolean arcadiaMode() {
        return "true".equals(System.getProperty(MODE_PROPERTY));
    }

    @GameTest(template = "empty_3x3", timeoutTicks = 100)
    public static void theModeCarriesArcadiaLibAndNothingElse(GameTestHelper helper) {
        if (!arcadiaMode()) {
            helper.succeed();
            return;
        }
        if (!ModList.get().isLoaded("arcadia_lib")) {
            throw new GameTestAssertException("Arcadia mode, but arcadia_lib is not loaded. Check that "
                    + "runGameTestServerArcadia copied its jar into run/gametest-arcadia/mods.");
        }
        if (CustomPerm.isLuckPermsPresent()) {
            throw new GameTestAssertException("Arcadia mode must not carry LuckPerms: its mods folder holds "
                    + "Arcadia Lib only, so the backend under test is the internal one.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_3x3", timeoutTicks = 100)
    public static void aDriverBroughtByAnotherModIsReachable(GameTestHelper helper) {
        if (!arcadiaMode()) {
            helper.succeed();
            return;
        }
        Driver driver = JdbcDrivers.firstOf(MYSQL);
        if (driver == null) {
            throw new GameTestAssertException("CustomPerm cannot load " + MYSQL + ", although Arcadia Lib carries "
                    + "it as a nested jar. Cluster mode's direct connection depends on exactly this lookup.");
        }
        helper.succeed();
    }

    @GameTest(template = "empty_3x3", timeoutTicks = 100)
    public static void thatDriverIsSpokenToInItsOwnVocabulary(GameTestHelper helper) {
        if (!arcadiaMode()) {
            helper.succeed();
            return;
        }
        Driver driver = JdbcDrivers.firstOf(MYSQL);
        if (driver == null) {
            throw new GameTestAssertException(MYSQL + " is not reachable; see aDriverBroughtByAnotherModIsReachable.");
        }
        try {
            if (JdbcDrivers.mariaDb(driver)) {
                throw new GameTestAssertException("MySQL's driver was taken for MariaDB's, so it would be given "
                        + "TLS modes it rejects.");
            }
            String url = JdbcDrivers.urlFor(driver, "db.example", 3306, "customperm");
            if (!url.startsWith("jdbc:mysql://")) {
                throw new GameTestAssertException("expected a jdbc:mysql URL for this driver, got " + url);
            }
            String mode = JdbcDrivers.sslMode(false, "verify");
            if (!"VERIFY_IDENTITY".equals(mode)) {
                throw new GameTestAssertException("expected VERIFY_IDENTITY for a MySQL driver, got " + mode);
            }
        } catch (java.sql.SQLException e) {
            throw new GameTestAssertException("the driver refused the cluster URL: " + e.getMessage());
        }
        helper.succeed();
    }
}
