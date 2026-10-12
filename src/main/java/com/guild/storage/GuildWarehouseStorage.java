package com.guild.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分类公会仓库持久化。每个公会、每个分类对应一个容器。
 */
public final class GuildWarehouseStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, GuildWarehouse> WAREHOUSES = new ConcurrentHashMap<>();

    private static Path filePath;
    private static HolderLookup.Provider registryAccess;
    private static boolean loading;

    private GuildWarehouseStorage() {
    }

    public static void init(Path worldRoot, HolderLookup.Provider provider) {
        filePath = worldRoot.resolve("guild_data").resolve("warehouses.json");
        registryAccess = provider;
        WAREHOUSES.clear();
        loading = true;
        try {
            load();
        } finally {
            loading = false;
        }
    }

    public static GuildWarehouse get(String guildId, GuildWarehouse.Category category, int guildLevel) {
        String key = key(guildId, category);
        GuildWarehouse warehouse = WAREHOUSES.computeIfAbsent(key,
                ignored -> new GuildWarehouse(guildId, category, GuildWarehouse.slotsForLevel(guildLevel)));
        warehouse.resize(GuildWarehouse.slotsForLevel(guildLevel));
        return warehouse;
    }

    public static void remove(String guildId) {
        String prefix = guildId + ":";
        boolean removed = WAREHOUSES.keySet().removeIf(key -> key.startsWith(prefix));
        if (removed) save();
    }

    public static void saveWarehouse(String guildId) {
        if (!loading) saveGuild(guildId);
    }

    public static void saveGuild(String guildId) {
        if (!loading) save();
    }

    public static void save() {
        if (filePath == null || registryAccess == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            JsonArray warehouses = new JsonArray();
            for (GuildWarehouse warehouse : WAREHOUSES.values()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("guildId", warehouse.getGuildId());
                entry.addProperty("category", warehouse.getCategory().id);
                entry.addProperty("slotCount", warehouse.getSlotCount());
                JsonArray items = new JsonArray();
                for (int slot = 0; slot < warehouse.getContainerSize(); slot++) {
                    ItemStack stack = warehouse.getItem(slot);
                    if (stack.isEmpty()) continue;
                    net.minecraft.nbt.Tag tag = stack.save(registryAccess);
                    if (!(tag instanceof CompoundTag compound)) continue;
                    JsonObject item = new JsonObject();
                    item.addProperty("slot", slot);
                    item.addProperty("nbt", compound.toString());
                    items.add(item);
                }
                entry.add("items", items);
                warehouses.add(entry);
            }
            JsonObject root = new JsonObject();
            root.add("warehouses", warehouses);
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void load() {
        if (filePath == null || registryAccess == null || !Files.exists(filePath)) return;
        try {
            String json = Files.readString(filePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("warehouses")) return;
            for (JsonElement element : root.getAsJsonArray("warehouses")) {
                JsonObject entry = element.getAsJsonObject();
                String guildId = entry.get("guildId").getAsString();
                GuildWarehouse.Category parsedCategory = GuildWarehouse.Category.parse(
                        entry.has("category") ? entry.get("category").getAsString() : "materials");
                GuildWarehouse.Category category = parsedCategory == null
                        ? GuildWarehouse.Category.MATERIALS
                        : parsedCategory;
                int slotCount = entry.has("slotCount")
                        ? entry.get("slotCount").getAsInt()
                        : GuildWarehouse.MAX_SLOTS;
                String key = key(guildId, category);
                GuildWarehouse warehouse = WAREHOUSES.computeIfAbsent(key,
                        ignored -> new GuildWarehouse(guildId, category, slotCount));
                if (!entry.has("items")) continue;
                for (JsonElement itemElement : entry.getAsJsonArray("items")) {
                    JsonObject item = itemElement.getAsJsonObject();
                    int slot = item.get("slot").getAsInt();
                    if (slot < 0 || slot >= warehouse.getContainerSize()) continue;
                    CompoundTag tag = TagParser.parseTag(item.get("nbt").getAsString());
                    ItemStack stack = ItemStack.parseOptional(registryAccess, tag);
                    if (!stack.isEmpty()) warehouse.restoreItem(slot, stack);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String key(String guildId, GuildWarehouse.Category category) {
        return guildId + ":" + category.id;
    }
}
