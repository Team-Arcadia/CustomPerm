package com.arcadia.customperm.network;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.ClientNetworkHandler;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.config.GradesConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registers the two payloads that back the optional TesseraUI GUI (H2.1). The client-side
 * handler ({@link ClientNetworkHandler}) is only ever exercised behind an
 * {@link CustomPerm#isTesseraUiPresent()} guard — see that class for the lazy-classloading
 * rationale (same discipline as {@code LuckPermsService}).
 */
public final class NetworkHandler {

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(GuiSyncPayload.TYPE, GuiSyncPayload.STREAM_CODEC, ClientNetworkHandler::handleGuiSync);
        registrar.playToServer(RequestGuiSyncPayload.TYPE, RequestGuiSyncPayload.STREAM_CODEC,
                NetworkHandler::handleRequestSync);
    }

    private static void handleRequestSync(RequestGuiSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Real security boundary — mirrors the op-level-2 gate on /customperm itself
            // (CustomPermCommand.register). The client-side "gui" command's own .requires()
            // is UX only; this check is what actually protects grades/aliases data.
            if (!player.createCommandSourceStack().hasPermission(2)) return;
            PacketDistributor.sendToPlayer(player, buildSnapshot(player.getServer()));
        });
    }

    private static GuiSyncPayload buildSnapshot(MinecraftServer server) {
        ConfigManager configManager = CustomPerm.configManager;
        GradesConfig gradesConfig = configManager.getGrades();

        Map<String, GuiSyncPayload.GradeDto> grades = new HashMap<>();
        gradesConfig.grades.forEach((name, grade) -> grades.put(name, new GuiSyncPayload.GradeDto(
                Set.copyOf(grade.permissions), Set.copyOf(grade.deniedPermissions))));

        Map<String, Integer> aliases = new HashMap<>();
        configManager.getAliases().aliases.forEach((name, steps) -> aliases.put(name, steps.size()));

        boolean directCommandsEnabled = CustomPerm.isDirectCommandExposureEnabled();
        int configuredExposed = configManager.getCommands().grantedCommands.size();

        return new GuiSyncPayload(
                CustomPerm.backendLabel(),
                configManager.getSettings().luckPermsFallbackMode,
                CustomPerm.isLuckPermsActive(),
                directCommandsEnabled,
                server.getCommands().getDispatcher().getRoot().getChildren().size(),
                directCommandsEnabled ? configuredExposed : 0,
                gradesConfig.userGrades.size(),
                grades,
                aliases
        );
    }
}
