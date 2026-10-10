package com.guild.guild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.Iterator;

/**
 * 公会领地保护处理器。
 * 当前实现：爆炸保护 —— 开启保护的公会领地内，方块不会被爆炸破坏。
 */
public class ProtectionHandler {

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;

        // 遍历爆炸影响到的方块，移除处于保护领地内的方块
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            for (Guild guild : GuildManager.allGuilds()) {
                if (guild.isExplosionProtection() && guild.territoryAt(pos).isPresent()) {
                    it.remove();   // 受保护，不受爆炸影响
                    break;
                }
            }
        }
    }
}
