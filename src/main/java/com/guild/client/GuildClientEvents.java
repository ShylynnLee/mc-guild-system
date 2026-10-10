package com.guild.client;

import com.guild.GuildMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 客户端背包中的公会仓库入口。
 */
@EventBusSubscriber(modid = GuildMod.MODID, value = Dist.CLIENT)
public final class GuildClientEvents {

    private GuildClientEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            addInventoryButton(event, screen);
            return;
        }
        if (event.getScreen() instanceof ContainerScreen screen) {
            addWarehouseTabs(event, screen);
        }
    }

    private static void addInventoryButton(ScreenEvent.Init.Post event, InventoryScreen screen) {
        int x = screen.getGuiLeft() + screen.getXSize() - 60;
        int y = screen.getGuiTop() - 22;
        event.addListener(Button.builder(Component.literal("公会仓库"), pressed ->
                sendCommand("guild storage")).bounds(x, y, 60, 20).build());
    }

    private static void addWarehouseTabs(ScreenEvent.Init.Post event, ContainerScreen screen) {
        if (!screen.getTitle().getString().startsWith("公会仓库")) return;

        int x = screen.getGuiLeft();
        int y = screen.getGuiTop() - 22;
        event.addListener(Button.builder(Component.literal("装备"), pressed ->
                sendCommand("guild storage equipment")).bounds(x, y, 56, 20).build());
        event.addListener(Button.builder(Component.literal("矿物"), pressed ->
                sendCommand("guild storage materials")).bounds(x + 60, y, 56, 20).build());
        event.addListener(Button.builder(Component.literal("方块"), pressed ->
                sendCommand("guild storage blocks")).bounds(x + 120, y, 56, 20).build());
    }

    private static void sendCommand(String command) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().sendCommand(command);
        }
    }
}
