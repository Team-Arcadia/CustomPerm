package com.arcadia.customperm.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server -> client snapshot consumed by the TesseraUI Grades/Aliases/Status screens, in
 * response to {@link RequestGuiSyncPayload}. Mirrors the fields already assembled by
 * {@code CustomPermCommand.status()} so the GUI and the text command never disagree.
 */
public record GuiSyncPayload(
        String backendLabel,
        String lpFallbackMode,
        boolean luckPermsActive,
        boolean directCommandExposureEnabled,
        int dispatcherCommandCount,
        int exposedCommandCount,
        int userGradeCount,
        Map<String, GradeDto> grades,
        Map<String, Integer> aliases
) implements CustomPacketPayload {

    // createType(String) resolves its argument as a bare path under the "minecraft" namespace,
    // not "namespace:path" — build the Type explicitly to get our own "customperm" namespace.
    public static final Type<GuiSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("customperm", "gui_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Set<String>> STRING_SET =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Set<String>>collection(HashSet::new, ByteBufCodecs.STRING_UTF8);

    private static final StreamCodec<RegistryFriendlyByteBuf, GradeDto> GRADE_DTO_CODEC =
            StreamCodec.composite(
                    STRING_SET, GradeDto::permissions,
                    STRING_SET, GradeDto::deniedPermissions,
                    GradeDto::new
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, GradeDto>> GRADES_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, GradeDto, Map<String, GradeDto>>map(
                    HashMap::new, ByteBufCodecs.STRING_UTF8, GRADE_DTO_CODEC);

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> ALIASES_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, String, Integer, Map<String, Integer>>map(
                    HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, GuiSyncPayload> STREAM_CODEC =
            StreamCodec.of(GuiSyncPayload::write, GuiSyncPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, GuiSyncPayload payload) {
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.backendLabel);
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.lpFallbackMode);
        ByteBufCodecs.BOOL.encode(buf, payload.luckPermsActive);
        ByteBufCodecs.BOOL.encode(buf, payload.directCommandExposureEnabled);
        ByteBufCodecs.VAR_INT.encode(buf, payload.dispatcherCommandCount);
        ByteBufCodecs.VAR_INT.encode(buf, payload.exposedCommandCount);
        ByteBufCodecs.VAR_INT.encode(buf, payload.userGradeCount);
        GRADES_CODEC.encode(buf, payload.grades);
        ALIASES_CODEC.encode(buf, payload.aliases);
    }

    private static GuiSyncPayload read(RegistryFriendlyByteBuf buf) {
        String backendLabel = ByteBufCodecs.STRING_UTF8.decode(buf);
        String lpFallbackMode = ByteBufCodecs.STRING_UTF8.decode(buf);
        boolean luckPermsActive = ByteBufCodecs.BOOL.decode(buf);
        boolean directCommandExposureEnabled = ByteBufCodecs.BOOL.decode(buf);
        int dispatcherCommandCount = ByteBufCodecs.VAR_INT.decode(buf);
        int exposedCommandCount = ByteBufCodecs.VAR_INT.decode(buf);
        int userGradeCount = ByteBufCodecs.VAR_INT.decode(buf);
        Map<String, GradeDto> grades = GRADES_CODEC.decode(buf);
        Map<String, Integer> aliases = ALIASES_CODEC.decode(buf);
        return new GuiSyncPayload(backendLabel, lpFallbackMode, luckPermsActive, directCommandExposureEnabled,
                dispatcherCommandCount, exposedCommandCount, userGradeCount, grades, aliases);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record GradeDto(Set<String> permissions, Set<String> deniedPermissions) {
    }
}
