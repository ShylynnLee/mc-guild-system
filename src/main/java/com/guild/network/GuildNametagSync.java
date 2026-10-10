package com.guild.network;

import com.guild.config.NametagConfig;
import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 定期向在线玩家同步公会名牌数据。
 */
public final class GuildNametagSync {

    private static final int SYNC_INTERVAL_TICKS = 100;
    private int tickCounter;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < SYNC_INTERVAL_TICKS) return;
        tickCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        Guild guild = GuildManager.getPlayerGuild(player);
        List<java.util.UUID> members = guild == null
                ? List.of()
                : new ArrayList<>(guild.getMembers().keySet());
        player.connection.send(new GuildNametagSyncPayload(
                NametagConfig.isEnabled(),
                NametagConfig.getMaxDistance(),
                members));
    }
}
