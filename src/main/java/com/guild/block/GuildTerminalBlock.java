package com.guild.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 公会终端方块。
 * 领地范围就是以这个终端为中心的正方形柱体。
 */
public class GuildTerminalBlock extends Block {

    public GuildTerminalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 1200.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> 7));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal("公会终端：" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
            player.sendSystemMessage(Component.literal("使用 /guild claim 以该终端为中心圈占领地"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
