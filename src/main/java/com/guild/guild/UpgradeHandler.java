package com.guild.guild;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 公会升级处理器。
 *
 * 规则：任意成员完成升级路线中的成就 → 记入公会 → 若满足当前等级的下一等级条件则自动升级。
 * 前提：玩家必须已在公会中（加入公会时会补录其已有的成就）。
 */
public class UpgradeHandler {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Guild guild = GuildManager.getPlayerGuild(player.getUUID());
        if (guild == null) return;

        GuildManager.backfillAdvancements(guild, player);
        com.guild.storage.GuildStorage.save();
    }

    @SubscribeEvent
    public void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String advancementId = event.getAdvancement().id().toString();
        // 只关心升级路线里用到的成就
        if (!UpgradeConfig.isRelevant(advancementId)) return;

        Guild guild = GuildManager.getPlayerGuild(player.getUUID());
        if (guild == null) return;

        if (guild.hasCompleted(advancementId)) return;
        guild.addCompletedAdvancement(advancementId);
        com.guild.storage.GuildStorage.save();

        // 注意：不自动升级 —— 升级需会长/管理员手动执行 /guild upgrade 确认
        // 这里仅提示该成就已记入公会
        player.sendSystemMessage(Component.literal(
                "✅ 成就已记入公会「" + guild.getName() + "」")
                .withStyle(ChatFormatting.GREEN));

        // 若已满足升级条件，提示会长/管理员去确认
        if (guild.canUpgrade()) {
            player.sendSystemMessage(Component.literal(
                    "⭐ 升级条件已满足！会长或管理员执行 /guild upgrade 确认升级")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }
}
