package com.guild.mixin.client;

import com.guild.network.GuildNametagState;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.ClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开启时仅让 64 格内未潜行的公会成员名牌穿墙。
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "renderNameTag", at = @At("HEAD"))
    private void guild$beforeNameTag(
            Entity entity, Component displayName, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick,
            CallbackInfo callback) {
        if (guild$shouldEnhance(entity)) {
            RenderSystem.disableDepthTest();
        }
    }

    @Inject(method = "renderNameTag", at = @At("RETURN"))
    private void guild$afterNameTag(
            Entity entity, Component displayName, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, float partialTick,
            CallbackInfo callback) {
        if (guild$shouldEnhance(entity)) {
            RenderSystem.enableDepthTest();
        }
    }

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

        return true;
    }

    private boolean guild$shouldEnhance(Entity entity) {
        if (!GuildNametagState.isEnabled() || !(entity instanceof AbstractClientPlayer player)) {
            return false;
        }
        if (player.isShiftKeyDown()) return false;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return false;
        return GuildNametagState.shouldSeeThrough(
                player.getUUID(), player.distanceTo(minecraft.player));
    }
}
