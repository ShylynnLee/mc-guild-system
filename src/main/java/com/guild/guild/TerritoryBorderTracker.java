package com.guild.guild;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 领地边界提示。
 * 玩家进入公会领地时提示「已进入 xxx 公会领地」，
 * 离开时提示「已离开 xxx 领地」。
 */
public class TerritoryBorderTracker {

    /** 玩家 -> 当前所处领地的公会 ID（null = 不在任何领地） */
    private static final Map<UUID, String> CURRENT = new ConcurrentHashMap<>();

    /** 检查间隔（tick），5 tick = 0.25 秒 */
    private static final int CHECK_INTERVAL = 5;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }
        // 按固定间隔检查，避免每 tick 都扫描全部领地
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }

        UUID uuid = player.getUUID();
        Optional<Guild> now = GuildManager.guildAt(player.blockPosition());
        String newGuildId = now.map(Guild::getId).orElse(null);
        String oldGuildId = CURRENT.get(uuid);

        if (Objects.equals(newGuildId, oldGuildId)) {
            return;   // 未跨越边界
        }

        // 离开旧领地
        if (oldGuildId != null) {
            Guild old = GuildManager.getGuild(oldGuildId);
            if (old != null) {
                player.sendSystemMessage(Component.literal("已离开 " + old.getName() + " 领地"));
            }
        }

        // 进入新领地
        if (newGuildId != null) {
            Guild nowGuild = now.get();
            player.sendSystemMessage(Component.literal("已进入 " + nowGuild.getName() + " 公会领地"));
        }

        // 离开领地时 newGuildId 为 null；ConcurrentHashMap 不允许 null 值，
        // 直接 put(null) 会抛 NPE 导致玩家掉线，故用 remove 清除记录。
        if (newGuildId == null) {
            CURRENT.remove(uuid);
        } else {
            CURRENT.put(uuid, newGuildId);
        }
    }

    /** 玩家退出时清理 */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CURRENT.remove(event.getEntity().getUUID());
    }
}
