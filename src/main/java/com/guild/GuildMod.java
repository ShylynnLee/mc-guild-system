package com.guild;

import com.guild.block.ModBlocks;
import com.guild.command.GuildCommands;
import com.guild.config.GuildConfig;
import com.guild.config.NametagConfig;
import com.guild.config.WarehouseConfig;
import com.guild.network.GuildNametagState;
import com.guild.network.GuildNametagSync;
import com.guild.network.GuildNametagSyncPayload;
import com.guild.storage.GuildStorage;
import com.guild.storage.GuildWarehouseStorage;
import com.guild.guild.ProtectionHandler;
import com.guild.guild.TerritoryBorderTracker;
import com.guild.guild.PermissionInterceptor;
import com.guild.guild.UpgradeHandler;
import com.guild.guild.UpgradeConfig;
import com.guild.guild.TerritoryLimitConfig;
import com.guild.guild.TerritoryBuffHandler;
import com.guild.guild.GuildTeleportHandler;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(GuildMod.MODID)
public final class GuildMod {

    public static final String MODID = "guild";

    public GuildMod(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlocks.TABS.register(modBus);

        container.registerConfig(ModConfig.Type.COMMON, GuildConfig.SPEC);

        NeoForge.EVENT_BUS.register(new GuildCommands());
        NeoForge.EVENT_BUS.register(new ProtectionHandler());
        NeoForge.EVENT_BUS.register(new TerritoryBorderTracker());
        NeoForge.EVENT_BUS.register(new PermissionInterceptor());
        NeoForge.EVENT_BUS.register(new UpgradeHandler());
        NeoForge.EVENT_BUS.register(new TerritoryBuffHandler());
        NeoForge.EVENT_BUS.register(new GuildTeleportHandler());
        NeoForge.EVENT_BUS.register(new GuildNametagSync());
        NeoForge.EVENT_BUS.register(this);
        modBus.addListener(this::registerPayloadHandlers);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        java.nio.file.Path worldRoot = event.getServer().getWorldPath(LevelResource.ROOT);
        UpgradeConfig.init(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        TerritoryLimitConfig.init(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        WarehouseConfig.init(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        NametagConfig.init(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get());
        GuildStorage.init(worldRoot);
        GuildStorage.load();
        GuildWarehouseStorage.init(worldRoot, event.getServer().registryAccess());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        GuildStorage.save();
        GuildWarehouseStorage.save();
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                GuildNametagSyncPayload.TYPE,
                GuildNametagSyncPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> GuildNametagState.update(payload)));
    }
}
