package com.guild.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务器级公会仓库总开关。
 */
public final class WarehouseConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path filePath;
    private static long loadedModified = -1L;
    private static boolean enabled = true;

    private WarehouseConfig() {
    }

    public static void init(Path configDir) {
        filePath = configDir.resolve("guild-warehouse.json");
        if (!Files.exists(filePath)) {
            writeDefault();
        }
        isEnabled();
    }

    public static boolean isEnabled() {
        if (filePath == null) return enabled;
        try {
            long modified = Files.getLastModifiedTime(filePath).toMillis();
            if (modified != loadedModified) {
                String json = Files.readString(filePath);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
                loadedModified = modified;
            }
        } catch (Exception ignored) {
            enabled = true;
        }
        return enabled;
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", true);
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
