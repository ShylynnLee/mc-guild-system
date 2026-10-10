package com.guild.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务器级公会成员名牌穿墙配置。
 */
public final class NametagConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path filePath;
    private static long loadedModified = -1L;
    private static boolean enabled = true;
    private static int maxDistance = 64;

    private NametagConfig() {
    }

    public static void init(Path configDir) {
        filePath = configDir.resolve("guild-nametag.json");
        if (!Files.exists(filePath)) {
            writeDefault();
        }
        reloadIfChanged();
    }

    public static boolean isEnabled() {
        reloadIfChanged();
        return enabled;
    }

    public static int getMaxDistance() {
        reloadIfChanged();
        return maxDistance;
    }

    private static void reloadIfChanged() {
        if (filePath == null) return;
        try {
            long modified = Files.getLastModifiedTime(filePath).toMillis();
            if (modified == loadedModified) return;
            String json = Files.readString(filePath);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
            maxDistance = root.has("maxDistance")
                    ? Math.clamp(root.get("maxDistance").getAsInt(), 8, 256)
                    : 64;
            loadedModified = modified;
        } catch (Exception ignored) {
            enabled = true;
            maxDistance = 64;
        }
    }

    private static void writeDefault() {
        try {
            Files.createDirectories(filePath.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("enabled", true);
            root.addProperty("maxDistance", 64);
            Files.writeString(filePath, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
