package com.guild.guild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会领地权限拦截。
 *
 * 拦截规则（只在公会领地内生效，领地外完全不拦）：
 *   - 破坏方块   → BREAK 权限
 *   - 放置方块   → BUILD 权限
 *   - 打开容器   → CHEST 权限
 *   - 其它右键   → USE 权限
 *
 * 非公会成员按 GUEST（访客）权限判断，默认全 false → 会被拦截。
 */
public class PermissionInterceptor {

    /** 提示冷却：每个玩家最多每秒提示一次，避免刷屏 */
    private static final Map<UUID, Long> LAST_MSG = new ConcurrentHashMap<>();
    private static final long MSG_COOLDOWN_MS = 1000L;

    // ===== 破坏方块 =====
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        Optional<Guild> guildOpt = GuildManager.guildAt(event.getPos());
        if (guildOpt.isEmpty()) return;            // 领地外不拦
        Guild guild = guildOpt.get();
        Optional<Territory> terrOpt = guild.territoryAt(event.getPos());
        if (terrOpt.isEmpty()) return;
        Territory territory = terrOpt.get();

        // 按【该领地】自己的权限表判定（领地间独立）
        if (territory.hasPermission(player.getUUID(), guild, Guild.Permission.BREAK)) return;

        event.setCanceled(true);
        sendThrottled(player, guild, territory, "破坏");
    }

    // ===== 放置方块 =====
    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Optional<Guild> guildOpt = GuildManager.guildAt(event.getPos());
        if (guildOpt.isEmpty()) return;
        Guild guild = guildOpt.get();
        Optional<Territory> terrOpt = guild.territoryAt(event.getPos());
        if (terrOpt.isEmpty()) return;
        Territory territory = terrOpt.get();

        if (territory.hasPermission(player.getUUID(), guild, Guild.Permission.BUILD)) return;

        event.setCanceled(true);
        if (player instanceof ServerPlayer sp) {
            sendThrottled(sp, guild, territory, "建筑");
        }
    }

    // ===== 右键交互（容器 / 工具使用） =====
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = (Level) event.getLevel();
        if (level.isClientSide()) return;
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer sp)) return;

        BlockPos pos = event.getPos();
        Optional<Guild> guildOpt = GuildManager.guildAt(pos);
        if (guildOpt.isEmpty()) return;
        Guild guild = guildOpt.get();
        Optional<Territory> terrOpt = guild.territoryAt(pos);
        if (terrOpt.isEmpty()) return;
        Territory territory = terrOpt.get();

        // 打开容器 → CHEST 权限（按领地）
        if (isContainer(level, pos)) {
            if (territory.hasPermission(sp.getUUID(), guild, Guild.Permission.CHEST)) return;
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            sendThrottled(sp, guild, territory, "打开容器");
            return;
        }

        // 其它右键交互 → USE 权限（按领地）
        if (territory.hasPermission(sp.getUUID(), guild, Guild.Permission.USE)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        sendThrottled(sp, guild, territory, "交互");
    }

    /** 是否为容器（箱子、木桶、末影箱、熔炉等） */
    private boolean isContainer(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return be instanceof BaseContainerBlockEntity || be instanceof MenuProvider;
    }

    /** 节流提示：同一玩家 1 秒内只提示一次；提示具体领地名 */
    private void sendThrottled(ServerPlayer player, Guild guild, Territory territory, String action) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_MSG.get(uuid);
        if (last != null && now - last < MSG_COOLDOWN_MS) return;
        LAST_MSG.put(uuid, now);

        String role = guild.getRole(uuid).displayName;
        player.sendSystemMessage(Component.literal(
                "⚠ 你没有领地「" + territory.getName() + "」的" + action + "权限（当前角色：" + role + "）"));
    }
}
