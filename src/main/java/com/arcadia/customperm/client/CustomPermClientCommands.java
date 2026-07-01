package com.arcadia.customperm.client;

import com.arcadia.customperm.CustomPerm;
import com.arcadia.customperm.client.gui.AliasesScreen;
import com.arcadia.customperm.client.gui.GradesScreen;
import com.arcadia.customperm.client.gui.StatusScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only trigger for the optional TesseraUI admin panel (H2.1): {@code /customperm gui
 * grades|aliases|status}. This is a genuinely separate, client-side-only command tree (see
 * {@link RegisterClientCommandsEvent}) — it never touches the server-authoritative
 * {@code /customperm} dispatcher registered in {@code CustomPermCommand}, and merging the same
 * root literal name into the client's local dispatcher is safe: Brigadier's node merge only
 * folds in children, it never overwrites an existing node's {@code requires} predicate.
 * <p>
 * The {@code .requires(hasPermission(2))} below is UX only (hides the entry from tab-complete
 * for non-admins); the real security boundary is the op-level-2 check in
 * {@code NetworkHandler.handleRequestSync}, since that's what actually gates the grades/aliases
 * data leaving the server.
 */
// RegisterClientCommandsEvent is a GAME-bus event (it doesn't implement IModBusEvent), which is
// also EventBusSubscriber's default — bus() itself is deprecated in this NeoForge version and
// must not be set explicitly.
@EventBusSubscriber(modid = CustomPerm.MODID, value = Dist.CLIENT)
public final class CustomPermClientCommands {

    private CustomPermClientCommands() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("customperm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("gui")
                    .then(Commands.literal("grades").executes(ctx -> openScreen(ctx, "grades")))
                    .then(Commands.literal("aliases").executes(ctx -> openScreen(ctx, "aliases")))
                    .then(Commands.literal("status").executes(ctx -> openScreen(ctx, "status"))))
        );
    }

    private static int openScreen(CommandContext<CommandSourceStack> ctx, String screen) {
        if (!CustomPerm.isTesseraUiPresent()) {
            ctx.getSource().sendFailure(Component.literal(
                "[CustomPerm] TesseraUI is not installed - this GUI is unavailable. Use the text commands instead."));
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        switch (screen) {
            case "grades" -> mc.setScreen(new GradesScreen());
            case "aliases" -> mc.setScreen(new AliasesScreen());
            default -> mc.setScreen(new StatusScreen());
        }
        return 1;
    }
}
