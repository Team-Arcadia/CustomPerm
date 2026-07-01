package com.arcadia.customperm.network;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.ClientNetworkHandler;
import com.arcadia.customperm.config.ConfigManager;
import com.arcadia.customperm.config.GradesConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registers the two payloads that back the optional TesseraUI GUI (H2.1). This class is loaded
 * on BOTH sides (RegisterPayloadHandlersEvent fires on client and dedicated server), and the
 * server needs {@code GuiSyncPayload}'s codec registered too — it's the one sending it.
 * <p>
 * What the server must NOT do is create a direct method reference into {@link ClientNetworkHandler}
 * (e.g. {@code ClientNetworkHandler::handleGuiSync}). A method reference resolves its target
 * method eagerly, at the point the referencing bytecode runs — unconditionally, on every side —
 * unlike a guarded method call, which only resolves its target on first actual invocation.
 * {@code ClientNetworkHandler}'s body touches {@code net.minecraft.client.Minecraft}, a class
 * that plain doesn't exist on a real (unstripped-in-dev-only) dedicated server jar. So the
 * handler passed to {@code playToClient} below is {@link #dispatchGuiSync}, declared in this
 * class — it only calls into {@code ClientNetworkHandler} from inside an
 * {@code FMLEnvironment.dist.isClient()} branch that a dedicated server never enters, so that
 * class is never resolved/loaded server-side. Same technique as gating {@code new
 * LuckPermsService(...)} behind {@code ModList.isLoaded("luckperms")} — just gated on the
 * running side instead of an optional mod's presence.
 * <p>
 * The registrar is also marked {@link PayloadRegistrar#optional()}: by default NeoForge treats
 * a registered payload channel as mandatory for the connection handshake — "connection will
 * fail" per {@code PayloadRegistrar}'s own javadoc if either side is missing a non-optional
 * channel. CustomPerm is meant to work with zero client-side mod install (README: "Server-side
 * only"); without {@code .optional()} here, a vanilla client would be flatly refused when
 * connecting to a server running this mod, just because the TesseraUI GUI feature exists.
 */
public final class NetworkHandler {

    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToClient(GuiSyncPayload.TYPE, GuiSyncPayload.STREAM_CODEC, NetworkHandler::dispatchGuiSync);
        registrar.playToServer(RequestGuiSyncPayload.TYPE, RequestGuiSyncPayload.STREAM_CODEC,
                NetworkHandler::handleRequestSync);
    }

    private static void dispatchGuiSync(GuiSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ClientNetworkHandler.handleGuiSync(payload, context);
        }
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
