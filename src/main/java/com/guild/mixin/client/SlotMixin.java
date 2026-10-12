package com.guild.mixin.client;

import com.guild.storage.GuildWarehouse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端也提前阻止错误分类物品进入公会仓库。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void guild$filterWarehouseItem(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof ContainerScreen screen)) return;
        Slot self = (Slot) (Object) this;
        if (self.container instanceof Inventory) return;

        String title = screen.getTitle().getString();
        GuildWarehouse.Category category;
        if (title.contains("装备")) {
            category = GuildWarehouse.Category.EQUIPMENT;
        } else if (title.contains("矿物")) {
            category = GuildWarehouse.Category.MATERIALS;
        } else if (title.contains("方块")) {
            category = GuildWarehouse.Category.BLOCKS;
        } else {
            return;
        }
        callback.setReturnValue(category.accepts(stack));
    }
}
