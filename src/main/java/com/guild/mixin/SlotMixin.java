package com.guild.mixin;

import com.guild.storage.GuildWarehouse;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端在物品进入仓库前执行分类校验，避免错误物品被消耗。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void guild$filterWarehouseItem(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        Slot self = (Slot) (Object) this;
        if (!(self.container instanceof GuildWarehouse warehouse)) return;
        callback.setReturnValue(warehouse.getCategory().accepts(stack));
    }
}
