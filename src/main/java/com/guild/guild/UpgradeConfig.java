package com.guild.guild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 公会升级路线配置。
 *
 * 配置文件 config/guild-upgrades.json：
 *   键 = 目标等级，值 = 该等级所需的成就 ID 列表（全部完成才升级）
 *
 * 例：升到 2 级需要完成 minecraft:story/crafting_table
 */
public final class UpgradeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path filePath;

    /** 目标等级 -> 所需成就 ID 列表 */
    private static final Map<Integer, List<String>> LEVEL_REQUIREMENTS = new TreeMap<>();

    /** 当前加载的成就 ID 全集（用于快速判断是否相关） */
    private static final Set<String> ALL_REQUIRED = new HashSet<>();

    private UpgradeConfig() {
    }

    /** 初始化配置路径（世界目录下的 config） */
    public static void init(Path configDir) {
        filePath = configDir.resolve("guild-upgrades.json");
        load();
    }

    /** 读取配置；不存在则生成默认路线 */
    @SuppressWarnings("unchecked")
    public static void load() {
        LEVEL_REQUIREMENTS.clear();
        ALL_REQUIRED.clear();

        try {
            if (filePath == null) return;
            if (!Files.exists(filePath)) {
                writeDefault();
            }
            String json = Files.readString(filePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                int level;
                try {
                    level = Integer.parseInt(e.getKey());
                } catch (NumberFormatException ex) {
                    continue;
                }
                List<String> list = new ArrayList<>();
                if (e.getValue().isJsonArray()) {
                    for (JsonElement el : (JsonArray) e.getValue()) {
                        list.add(el.getAsString());
                    }
                } else if (e.getValue().isJsonPrimitive()) {
                    list.add(e.getValue().getAsString());
                }
                if (!list.isEmpty()) {
                    LEVEL_REQUIREMENTS.put(level, list);
                    ALL_REQUIRED.addAll(list);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            // 目标等级 -> 所需成就（任意成员完成即记入公会，全部成就达成才升级）
            root.add("2", arr("minecraft:story/crafting_table"));
            root.add("3", arr("minecraft:story/iron"));
            root.add("4", arr("create:andesite_alloy"));
            root.add("5", arr("create:mechanical_press"));
            root.add("6", arr("create:precision_mechanism"));
            root.add("7", arr("create:steam_engine"));
            root.add("8", arr("minecraft:story/enter_the_end"));
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JsonArray arr(String... v) {
        JsonArray a = new JsonArray();
        for (String s : v) a.add(s);
        return a;
    }

    /** 获取升到 targetLevel 所需成就 */
    public static List<String> getRequirements(int targetLevel) {
        return LEVEL_REQUIREMENTS.getOrDefault(targetLevel, Collections.emptyList());
    }

    /** 升级路线中用到的全部成就 ID */
    public static java.util.Set<String> getAllRequired() {
        return Collections.unmodifiableSet(ALL_REQUIRED);
    }

    /** 是否是升级路线中用到的成就 */
    public static boolean isRelevant(String advancementId) {
        return ALL_REQUIRED.contains(advancementId);
    }

    /** 是否还有更高级别可升 */
    public static boolean hasLevel(int level) {
        return LEVEL_REQUIREMENTS.containsKey(level);
    }

    public static int getMaxConfiguredLevel() {
        return LEVEL_REQUIREMENTS.isEmpty() ? 1 : Collections.max(LEVEL_REQUIREMENTS.keySet());
    }
}
