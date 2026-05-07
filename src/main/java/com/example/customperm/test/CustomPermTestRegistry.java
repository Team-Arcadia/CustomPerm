package com.example.customperm.test;

import com.example.customperm.CustomPerm;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.Method;

/**
 * Registers each {@code @GameTest}-annotated method of {@link Customperm}
 * via {@link RegisterGameTestsEvent#register(Method)}.
 *
 * We iterate methods explicitly (rather than passing the whole class) so that we
 * can log the count for diagnostics — the symptom of a silent registration miss
 * is "No test functions were given!" at GameTestServer startup, with no other
 * clue. The log line below disambiguates that.
 */
public class CustomPermTestRegistry {

    public static void init(IEventBus modBus) {
        modBus.addListener(CustomPermTestRegistry::onRegisterGameTests);
        CustomPerm.LOGGER.info("[CustomPerm Tests] Registry init: listener attached to mod bus.");
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        int registered = 0;
        for (Method method : Customperm.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(GameTest.class)) continue;
            try {
                event.register(method);
                registered++;
            } catch (Throwable t) {
                CustomPerm.LOGGER.error("[CustomPerm Tests] Failed to register {}: {}", method.getName(), t.toString());
            }
        }
        CustomPerm.LOGGER.info("[CustomPerm Tests] Registered {} @GameTest method(s).", registered);
    }
}
