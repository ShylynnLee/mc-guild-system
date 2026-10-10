package com.guild.guild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * 领地数量上限配置（随公会等级提升）。
 *
 * 配置文件 config/guild-territories.json，稀疏映射：
 *   "键" = 解锁等级（达到该等级即生效）
 *   "值" = 该等级起的领地数上限
 *
 * 查询时取【小于等于当前等级的最大键】对应的值。
 * 例：{ "1": 1, "5": 2 }
 *   Lv1~4 → 1 块，Lv5 及以上 → 2 块
 */
public final class TerritoryLimitConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path filePath;

    /** 解锁等级 → 领地数上限（按 key 升序） */
    private static final TreeMap<Integer, Integer> LEVEL_TO_LIMIT = new TreeMap<>();

    private TerritoryLimitConfig() {
    }

    public static void init(Path configDir) {
        filePath = configDir.resolve("guild-territories.json");
        load();
    }

    public static void load() {
        LEVEL_TO_LIMIT.clear();
        try {
            if (filePath == null) return;
            if (!Files.exists(filePath)) writeDefault();

            JsonObject root = JsonParser.parseString(Files.readString(filePath)).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                try {
                    int lv = Integer.parseInt(e.getKey());
                    int limit = e.getValue().getAsInt();
                    LEVEL_TO_LIMIT.put(lv, limit);
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // 兜底：至少允许 1 块
        if (LEVEL_TO_LIMIT.isEmpty()) {
            LEVEL_TO_LIMIT.put(1, 1);
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("1", 1);   // Lv1~4：1 块
            root.addProperty("5", 2);   // Lv5 起：2 块
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 根据公会等级获取领地数上限 */
    public static int getLimit(int guildLevel) {
        Map.Entry<Integer, Integer> entry = LEVEL_TO_LIMIT.floorEntry(guildLevel);
        return entry != null ? entry.getValue() : LEVEL_TO_LIMIT.firstEntry().getValue();
    }
}
