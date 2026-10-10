package com.guild.network;

import java.util.Set;
import java.util.UUID;

/**
 * 客户端使用的公会名牌状态。
 */
public final class GuildNametagState {

    private static volatile boolean enabled;
    private static volatile int maxDistance = 64;
    private static volatile Set<UUID> members = Set.of();

    private GuildNametagState() {
    }

    public static void update(GuildNametagSyncPayload payload) {
        enabled = payload.enabled();
        maxDistance = Math.clamp(payload.maxDistance(), 8, 256);
        members = Set.copyOf(payload.members());
    }

    public static void clear() {
        enabled = false;
        maxDistance = 64;
        members = Set.of();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean shouldSeeThrough(UUID player, float distance) {
        return enabled && distance <= maxDistance && members.contains(player);
    }

    public static boolean isMember(UUID player) {
        return members.contains(player);
    }

    public static int getMaxDistance() {
        return maxDistance;
    }
}
