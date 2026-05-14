package com.arcadia.customperm.gametest;

import com.arcadia.customperm.CustomPerm;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Self-registering GameTest registry using {@link EventBusSubscriber}.
 *
 * Iterates each test class's {@code @GameTest}-annotated methods and registers them
 * individually via {@link RegisterGameTestsEvent#register(Method)} so that we can
 * log the count for diagnostics — the symptom of a silent registration miss is
 * "No test functions were given!" at GameTestServer startup, with no other clue.
 *
 * Uses {@link EventBusSubscriber.Bus#MOD} because {@link RegisterGameTestsEvent} fires
 * on the mod event bus (not the NeoForge/Forge event bus).
 */
@EventBusSubscriber(modid = CustomPerm.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CustomPermTestRegistry {

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        int registered = 0;
        Class<?>[] testClasses = {
            Customperm.class,
            CommandInterceptionTest.class,
            HotReloadTest.class,
            AliasExecutionTest.class,
            LuckPermsIntegrationTest.class
        };
        for (Class<?> cls : testClasses) {
            for (Method method : cls.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(GameTest.class)) continue;
                int modifiers = method.getModifiers();
                if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
                    CustomPerm.LOGGER.warn("[CustomPerm Tests] Skipping non-public/static @GameTest method {}.{}",
                            cls.getSimpleName(), method.getName());
                    continue;
                }
                try {
                    event.register(method);
                    registered++;
                } catch (Throwable t) {
                    CustomPerm.LOGGER.error("[CustomPerm Tests] Failed to register {}.{}: {}",
                        cls.getSimpleName(), method.getName(), t.toString());
                }
            }
        }
        CustomPerm.LOGGER.info("[CustomPerm Tests] Registered {} @GameTest method(s).", registered);
    }
}
