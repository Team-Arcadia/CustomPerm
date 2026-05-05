package com.example.customperm;

import com.example.customperm.command.CommandTreeRewriter;
import com.example.customperm.config.ConfigManager;
import com.example.customperm.perm.InternalPermService;
import com.example.customperm.perm.PermissionService;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(CustomPerm.MODID)
public class CustomPerm {
    public static final String MODID = "customperm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ConfigManager configManager;
    public static PermissionService permissions;

    public CustomPerm(IEventBus modBus, ModContainer container) {
        configManager = new ConfigManager();
        configManager.load();

        if (ModList.get().isLoaded("luckperms")) {
            // Class is loaded only here — its LP imports won't be resolved if LP is absent.
            permissions = new com.example.customperm.perm.LuckPermsService();
            LOGGER.info("[CustomPerm] LuckPerms detected — using LuckPerms backend.");
        } else {
            permissions = new InternalPermService(configManager);
            LOGGER.info("[CustomPerm] LuckPerms not present — using internal JSON grade backend.");
        }

        // Game-bus event: command registration happens per server start / /reload.
        NeoForge.EVENT_BUS.register(CommandTreeRewriter.class);
    }
}
