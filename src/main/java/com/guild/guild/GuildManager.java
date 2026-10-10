package com.guild.guild;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会管理器（运行时注册表）。
 * 负责创建、查询、按玩家/坐标查找公会。
 */
public final class GuildManager {

    /** 所有公会：公会ID -> 公会 */
    private static final Map<String, Guild> GUILDS = new ConcurrentHashMap<>();
    /** 玩家 -> 公会ID 的反向索引，便于快速查询 */
    private static final Map<UUID, String> PLAYER_TO_GUILD = new ConcurrentHashMap<>();

    private GuildManager() {
    }

    /** 创建公会，返回是否成功 */
    public static boolean createGuild(String name, Player leader) {
        UUID leaderId = leader.getUUID();
        if (getPlayerGuild(leaderId) != null) {
            return false; // 已在公会中
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        Guild guild = new Guild(id, name, leaderId);
        GUILDS.put(id, guild);
        PLAYER_TO_GUILD.put(leaderId, id);
        // 创建时补录会长已有成就（避免老玩家卡级）
        backfillAdvancements(guild, leader);
        return true;
    }

    /** 根据玩家查询其公会 */
    public static Guild getPlayerGuild(UUID playerId) {
        String gid = PLAYER_TO_GUILD.get(playerId);
        return gid == null ? null : GUILDS.get(gid);
    }

    public static Guild getPlayerGuild(Player player) {
        return getPlayerGuild(player.getUUID());
    }

    /** 根据公会ID查询 */
    public static Guild getGuild(String id) {
        return GUILDS.get(id);
    }

    /** 根据公会名称查询（不区分大小写） */
    public static Guild getGuildByName(String name) {
        for (Guild g : GUILDS.values()) {
            if (g.getName().equalsIgnoreCase(name)) return g;
        }
        return null;
    }

    /** 邀请/加入：把玩家加入公会 */
    public static boolean addMember(Guild guild, Player player, Guild.Role role) {
        if (getPlayerGuild(player.getUUID()) != null) {
            return false;
        }
        if (!guild.addMember(player.getUUID(), role)) {
            return false;
        }
        PLAYER_TO_GUILD.put(player.getUUID(), guild.getId());
        // 加入时补录该成员已有成就
        backfillAdvancements(guild, player);
        return true;
    }

    /** 移除成员 */
    public static void removeMember(Guild guild, UUID playerId) {
        guild.removeMember(playerId);
        PLAYER_TO_GUILD.remove(playerId);
    }

    /**
     * 在【所有公会】中查找与候选领地重叠的领地（含跨公会）。
     * 返回重叠的领地及其所属公会，便于提示。
     *
     * 只贴合（边界相邻、无共享方块）不会被判定为重叠。
     */
    public static Optional<Overlap> findOverlap(Territory candidate) {
        for (Guild g : GUILDS.values()) {
            for (Territory t : g.getTerritories()) {
                if (candidate.overlaps(t)) {
                    return Optional.of(new Overlap(g, t));
                }
            }
        }
        return Optional.empty();
    }

    /** 通过在线玩家或 usercache.json 解析公会成员的离线用户名。 */
    public static UUID findMemberByName(MinecraftServer server, Guild guild, String name) {
        if (server == null || guild == null || name == null || name.isEmpty()) return null;
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null && guild.hasMember(online.getUUID())) return online.getUUID();

        Path cachePath = server.getWorldPath(LevelResource.ROOT).resolveSibling("usercache.json");
        if (!Files.exists(cachePath)) return null;
        try {
            String json = Files.readString(cachePath);
            com.google.gson.JsonArray profiles = com.google.gson.JsonParser.parseString(json).getAsJsonArray();
            for (com.google.gson.JsonElement element : profiles) {
                com.google.gson.JsonObject profile = element.getAsJsonObject();
                if (!name.equalsIgnoreCase(profile.get("name").getAsString())) continue;
                UUID uuid = UUID.fromString(profile.get("uuid").getAsString());
                if (guild.hasMember(uuid)) return uuid;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    /** 重叠结果：领地 + 所属公会 */
    public record Overlap(Guild guild, Territory territory) {}

    /** 判断某坐标是否在任何公会领地内 */
    public static Optional<Guild> guildAt(BlockPos pos) {
        for (Guild g : GUILDS.values()) {
            if (g.territoryAt(pos).isPresent()) return Optional.of(g);
        }
        return Optional.empty();
    }

    /**
     * 补录玩家在升级路线中已达成的成就到公会，并尝试升级。
     * 用于创建公会/成员加入时，保证老玩家进度被计入。
     */
    public static void backfillAdvancements(Guild guild, Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;

        for (String advId : UpgradeConfig.getAllRequired()) {
            try {
                AdvancementHolder adv = server.getAdvancements().get(ResourceLocation.parse(advId));
                if (adv != null && sp.getAdvancements().getOrStartProgress(adv).isDone()) {
                    guild.addCompletedAdvancement(advId);
                }
            } catch (Exception ignored) {
                // 成就不存在（如未装对应 mod）则跳过
            }
        }
        // 注意：只补录成就，不自动升级（升级需会长/管理员手动确认）
    }

    /** 获取所有公会 */
    public static Collection<Guild> allGuilds() {
        return Collections.unmodifiableCollection(GUILDS.values());
    }

    /**
     * 解散公会：移除公会本身及所有成员索引。
     * 领地存在于 Guild 对象中，公会移除后 guildAt() 遍历不到，领地自动失效。
     */
    public static void removeGuild(Guild guild) {
        if (guild == null) return;
        for (java.util.UUID uid : new java.util.ArrayList<>(guild.getMembers().keySet())) {
            PLAYER_TO_GUILD.remove(uid);
        }
        GUILDS.remove(guild.getId());
    }

    /** 清空（用于重载） */
    public static void clear() {
        GUILDS.clear();
        PLAYER_TO_GUILD.clear();
    }

    /** 从存储加载时注册公会 */
    public static void register(Guild guild) {
        GUILDS.put(guild.getId(), guild);
        for (UUID member : guild.getMembers().keySet()) {
            PLAYER_TO_GUILD.put(member, guild.getId());
        }
    }
}
