package com.guild.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.guild.GuildMod.MODID;

/**
 * 方块与物品注册。
 */
public final class ModBlocks {

    // 方块注册表
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MODID);
    // 物品注册表
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MODID);
    // 创造模式物品栏
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** 公会终端 */
    public static final DeferredHolder<Block, GuildTerminalBlock> GUILD_TERMINAL =
            BLOCKS.register("guild_terminal", GuildTerminalBlock::new);

    /** 公会终端物品 */
    public static final DeferredHolder<Item, BlockItem> GUILD_TERMINAL_ITEM =
            ITEMS.register("guild_terminal",
                    () -> new BlockItem(GUILD_TERMINAL.get(), new Item.Properties()));

    /** 创造栏 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GUILD_TAB =
            TABS.register("guild", () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.guild"))
                    .icon(() -> GUILD_TERMINAL_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(GUILD_TERMINAL_ITEM.get()))
                    .build());

    private ModBlocks() {
    }
}
