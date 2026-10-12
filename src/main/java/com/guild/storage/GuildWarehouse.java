package com.guild.storage;

import net.minecraft.core.NonNullList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;

import java.util.Locale;

/**
 * 按公会等级动态扩容、按分类限制物品的共享仓库。
 */
public final class GuildWarehouse implements Container {

    public static final int MAX_SLOTS = 54;

    public enum Category {
        EQUIPMENT("equipment", "装备"),
        MATERIALS("materials", "矿物"),
        BLOCKS("blocks", "方块");

        public final String id;
        public final String displayName;

        Category(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static Category parse(String value) {
            if (value == null) return EQUIPMENT;
            String normalized = value.toLowerCase(Locale.ROOT);
            for (Category category : values()) {
                if (category.id.equals(normalized) || category.displayName.equals(value)) {
                    return category;
                }
            }
            return null;
        }

        public boolean accepts(ItemStack stack) {
            if (stack.isEmpty()) return false;
            return switch (this) {
                case EQUIPMENT -> isEquipment(stack);
                case BLOCKS -> stack.getItem() instanceof BlockItem;
                case MATERIALS -> !isEquipment(stack)
                        && !(stack.getItem() instanceof BlockItem)
                        && isMaterial(stack);
            };
        }
    }

    private final String guildId;
    private final Category category;
    private int slotCount;
    private NonNullList<ItemStack> items;

    public GuildWarehouse(String guildId, Category category, int slotCount) {
        this.guildId = guildId;
        this.category = category;
        this.slotCount = normalizeSlotCount(slotCount);
        this.items = NonNullList.withSize(this.slotCount, ItemStack.EMPTY);
    }

    public static int slotsForLevel(int level) {
        return Math.min(MAX_SLOTS, Math.max(9, level * 9));
    }

    public String getGuildId() {
        return guildId;
    }

    public Category getCategory() {
        return category;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public void resize(int requestedSlots) {
        int newSize = normalizeSlotCount(requestedSlots);
        if (newSize == slotCount) return;

        NonNullList<ItemStack> resized = NonNullList.withSize(newSize, ItemStack.EMPTY);
        int retained = Math.min(slotCount, newSize);
        for (int slot = 0; slot < retained; slot++) {
            resized.set(slot, items.get(slot));
        }
        slotCount = newSize;
        items = resized;
    }

    private static int normalizeSlotCount(int requestedSlots) {
        int rounded = ((Math.max(requestedSlots, 1) + 8) / 9) * 9;
        return Math.min(MAX_SLOTS, rounded);
    }

    @Override
    public int getContainerSize() {
        return slotCount;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return isValidSlot(slot) ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || amount <= 0) return ItemStack.EMPTY;
        ItemStack existing = items.get(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;

        int removed = Math.min(amount, existing.getCount());
        ItemStack result = existing.copyWithCount(removed);
        existing.setCount(existing.getCount() - removed);
        if (existing.isEmpty()) items.set(slot, ItemStack.EMPTY);
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) return ItemStack.EMPTY;
        ItemStack existing = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        if (!existing.isEmpty()) setChanged();
        return existing;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) return;
        if (!stack.isEmpty() && !category.accepts(stack)) return;
        items.set(slot, stack);
        setChanged();
    }

    void restoreItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) return;
        items.set(slot, stack);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return isValidSlot(slot) && category.accepts(stack);
    }

    @Override
    public void setChanged() {
        GuildWarehouseStorage.saveWarehouse(guildId);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) {
            items.set(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    private boolean isValidSlot(int slot) {
        return slot >= 0 && slot < slotCount;
    }

    private static boolean isEquipment(ItemStack stack) {
        return stack.getItem() instanceof ArmorItem
                || stack.getItem() instanceof TieredItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof ShieldItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof ElytraItem
                || stack.getItem() instanceof FishingRodItem
                || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.ARMOR_ENCHANTABLE)
                || stack.is(ItemTags.WEAPON_ENCHANTABLE);
    }

    private static boolean isMaterial(ItemStack stack) {
        String registryName = stack.getItemHolder().unwrapKey()
                .map(key -> key.location().toString().toLowerCase(Locale.ROOT))
                .orElse("");
        String tagNames = stack.getTags()
                .map(tag -> tag.location().toString().toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        String searchable = registryName + " " + tagNames;
        String[] keywords = {
                "ingot", "nugget", "ore", "raw_", "gem", "alloy", "metal",
                "crushed", "dust", "plate", "gear", "coal", "redstone",
                "quartz", "diamond", "emerald", "lapis", "amethyst"
        };
        for (String keyword : keywords) {
            if (searchable.contains(keyword)) return true;
        }
        return false;
    }
}
