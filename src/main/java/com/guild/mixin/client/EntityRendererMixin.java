package com.guild.mixin.client;

import com.guild.network.GuildNametagState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 开启时仅让 64 格内未潜行的公会成员名牌穿墙。
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Redirect(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;isNameplateInRenderDistance(Lnet/minecraft/world/entity/Entity;D)Z"))
    private boolean guild$renderDistance(Entity entity, double distanceSquared) {
        boolean original = ClientHooks.isNameplateInRenderDistance(entity, distanceSquared);
        if (!GuildNametagState.isEnabled() || !(entity instanceof AbstractClientPlayer player)) {
            return original;
        }
        if (player.isShiftKeyDown() || !GuildNametagState.isMember(player.getUUID())) {
            return original;
        }
        double maxDistance = GuildNametagState.getMaxDistance();
        return distanceSquared <= maxDistance * maxDistance;
    }

    @Redirect(
            method = "renderNameTag",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;isDiscrete()Z"))
    private boolean guild$isDiscrete(Entity entity) {
        boolean original = entity.isDiscrete();
        if (!GuildNametagState.isEnabled() || !(entity instanceof AbstractClientPlayer player)) {
            return original;
        }
        if (player.isShiftKeyDown()) return original;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return true;
        if (!GuildNametagState.shouldSeeThrough(
                player.getUUID(), player.distanceTo(minecraft.player))) {
            return true;
        }
        return false;
    }

}
