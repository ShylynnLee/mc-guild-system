package com.guild.guild;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 领地内 Buff。
 *
 * 仅当玩家身处【自己公会】的领地内时，按公会等级施加增益：
 *   挖掘速度(DIG_SPEED) / 奔跑速度(MOVEMENT_SPEED) / 生命治疗(REGENERATION)
 *
 * 强度封顶到 II（按设计要求），出领地后效果自然到期消失。
 */
public class TerritoryBuffHandler {

    /** 刷新间隔（tick） */
    private static final int CHECK_INTERVAL = 10;
    /** 效果持续时长（tick）= 10 秒，定期刷新保证不中断 */
    private static final int DURATION = 200;

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_INTERVAL != 0) return;

        Guild guild = GuildManager.getPlayerGuild(player.getUUID());
        if (guild == null) return;

        // 只在自己公会的领地内生效
        if (guild.territoryAt(player.blockPosition()).isEmpty()) return;

        int level = guild.getLevel();

        apply(player, MobEffects.DIG_SPEED, hasteAmp(level));
        apply(player, MobEffects.MOVEMENT_SPEED, speedAmp(level));
        apply(player, MobEffects.REGENERATION, regenAmp(level));
    }

    /** 挖掘速度：Lv2 → I，Lv4 → II（封顶 II） */
    private static int hasteAmp(int level) {
        if (level >= 4) return 1;
        if (level >= 2) return 0;
        return -1;
    }

    /** 奔跑速度：Lv3 → I，Lv5 → II（封顶 II） */
    private static int speedAmp(int level) {
        if (level >= 5) return 1;
        if (level >= 3) return 0;
        return -1;
    }

    /** 生命治疗：Lv6 → I，Lv8 → II（封顶 II） */
    private static int regenAmp(int level) {
        if (level >= 8) return 1;
        if (level >= 6) return 0;
        return -1;
    }

    private void apply(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
        if (amp < 0) return;
        MobEffectInstance inst = player.getEffect(effect);
        // 仅在效果即将到期或等级更低时刷新，避免重复添加
        if (inst == null || inst.getAmplifier() < amp || inst.getDuration() < 100) {
            player.addEffect(new MobEffectInstance(effect, DURATION, amp, false, true, true));
        }
    }
}
