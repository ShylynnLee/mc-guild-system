package com.guild.guild;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会领地传送：/guild tp <领地名>
 *
 * 规则：
 *   - 需要 5 秒吟唱
 *   - 吟唱期间【移动】或【被攻击】立即取消
 *   - 吟唱完成后传送到该领地中心的地面上
 */
public class GuildTeleportHandler {

    /** 吟唱时长（毫秒） */
    private static final long CHANNEL_MS = 5000L;
    /** 移动容忍度（方块），超过即视为移动 */
    private static final double MOVE_EPSILON = 0.05;

    private static final Map<UUID, Task> PENDING = new ConcurrentHashMap<>();

    private static class Task {
        final BlockPos target;
        final String territoryName;
        final long startTime;
        Vec3 lastPos;
        int lastAnnounced = -1;

        Task(BlockPos target, String territoryName, Vec3 lastPos) {
            this.target = target;
            this.territoryName = territoryName;
            this.startTime = System.currentTimeMillis();
            this.lastPos = lastPos;
        }
    }

    /** 开始吟唱。返回 false 表示已在传送中 */
    public static boolean start(ServerPlayer player, Territory territory) {
        UUID uuid = player.getUUID();
        if (PENDING.containsKey(uuid)) {
            player.sendSystemMessage(Component.literal("你已经在传送中了").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        // 目标：领地中心的地面
        BlockPos center = territory.getCenter();
        ServerLevel level = player.serverLevel();
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
        BlockPos target = new BlockPos(center.getX(), groundY, center.getZ());

        PENDING.put(uuid, new Task(target, territory.getName(), player.position()));

        player.sendSystemMessage(Component.literal(
                "⏳ 传送到领地「" + territory.getName() + "」... 5秒")
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static boolean isPending(UUID uuid) {
        return PENDING.containsKey(uuid);
    }

    /** 取消某玩家的传送并提示原因 */
    public static void cancelWithMessage(ServerPlayer player, String reason) {
        if (PENDING.remove(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("传送已取消：" + reason).withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        Task task = PENDING.get(uuid);
        if (task == null) return;

        Vec3 pos = player.position();

        // 1) 移动检测
        if (task.lastPos != null && pos.distanceTo(task.lastPos) > MOVE_EPSILON) {
            cancelWithMessage(player, "移动了");
            return;
        }
        task.lastPos = pos;

        // 2) 倒计时
        long elapsed = System.currentTimeMillis() - task.startTime;
        long remainingMs = CHANNEL_MS - elapsed;

        if (remainingMs <= 0) {
            PENDING.remove(uuid);
            doTeleport(player, task.target, task.territoryName);
            return;
        }

        int sec = (int) Math.ceil(remainingMs / 1000.0);
        if (sec != task.lastAnnounced) {
            task.lastAnnounced = sec;
            player.sendSystemMessage(Component.literal(
                    "⏳ 传送到「" + task.territoryName + "」... " + sec + "秒（移动或被攻击会取消）")
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    /** 被攻击/受到伤害时取消传送 */
    @SubscribeEvent
    public void onDamaged(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        cancelWithMessage(player, "受到了攻击");
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    private void doTeleport(ServerPlayer player, BlockPos target, String territoryName) {
        ServerLevel level = player.serverLevel();
        level.getChunkAt(target);   // 确保目标区块加载

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, target.getX(), target.getZ());
        double tx = target.getX() + 0.5;
        double ty = Math.max(y, level.getMinBuildHeight());
        double tz = target.getZ() + 0.5;

        player.teleportTo(level, tx, ty, tz, player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal(
                "✅ 已传送到领地「" + territoryName + "」").withStyle(ChatFormatting.GREEN));
    }
}
