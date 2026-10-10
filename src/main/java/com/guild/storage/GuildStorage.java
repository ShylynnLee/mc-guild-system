package com.guild.storage;

import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import com.guild.guild.Territory;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * 公会数据持久化（JSON 文件存储）。
 * 存放在世界目录下的 guild_data/guilds.json。
 */
public final class GuildStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path filePath;

    private GuildStorage() {
    }

    /** 初始化存储路径（世界目录） */
    public static void init(Path worldRoot) {
        filePath = worldRoot.resolve("guild_data").resolve("guilds.json");
    }

    /** 保存所有公会 */
    public static void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            JsonArray arr = new JsonArray();
            for (Guild guild : GuildManager.allGuilds()) {
                arr.add(toJson(guild));
            }
            Files.writeString(filePath, GSON.toJson(arr));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 加载所有公会 */
    public static void load() {
        if (filePath == null || !Files.exists(filePath)) return;
        try {
            String json = Files.readString(filePath);
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            for (JsonElement el : arr) {
                GuildManager.register(fromJson(el.getAsJsonObject()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JsonObject toJson(Guild guild) {
        JsonObject o = new JsonObject();
        o.addProperty("id", guild.getId());
        o.addProperty("name", guild.getName());
        o.addProperty("leader", guild.getLeader().toString());
        o.addProperty("level", guild.getLevel());
        o.addProperty("experience", guild.getExperience());
        o.addProperty("explosionProtection", guild.isExplosionProtection());
        o.addProperty("warehouseEnabled", guild.isWarehouseEnabled());
        com.google.gson.JsonArray deniedWarehouseMembers = new com.google.gson.JsonArray();
        for (UUID denied : guild.getWarehouseDeniedMembers()) deniedWarehouseMembers.add(denied.toString());
        o.add("warehouseDeniedMembers", deniedWarehouseMembers);
        com.google.gson.JsonArray advs = new com.google.gson.JsonArray();
        for (String a : guild.getCompletedAdvancements()) advs.add(a);
        o.add("completedAdvancements", advs);

        // 成员
        JsonObject members = new JsonObject();
        for (Map.Entry<UUID, Guild.Role> e : guild.getMembers().entrySet()) {
            members.addProperty(e.getKey().toString(), e.getValue().id);
        }
        o.add("members", members);

        // 领地
        JsonArray territories = new JsonArray();
        for (Territory t : guild.getTerritories()) {
            JsonObject tj = new JsonObject();
            BlockPos c = t.getCenter();
            tj.addProperty("x", c.getX());
            tj.addProperty("y", c.getY());
            tj.addProperty("z", c.getZ());
            tj.addProperty("radius", t.getRadius());
            tj.addProperty("name", t.getName());
            // 领地独立权限表
            JsonObject tp = new JsonObject();
            for (Guild.Role role : Guild.Role.values()) {
                JsonObject row = new JsonObject();
                for (Guild.Permission p : Guild.Permission.values()) {
                    row.addProperty(p.id, t.getPermissions()
                            .getOrDefault(role, java.util.Collections.emptyMap())
                            .getOrDefault(p, false));
                }
                tp.add(role.id, row);
            }
            tj.add("permissions", tp);
            territories.add(tj);
        }
        o.add("territories", territories);

        return o;
    }

    private static Guild fromJson(JsonObject o) {
        String id = o.get("id").getAsString();
        String name = o.get("name").getAsString();
        UUID leader = UUID.fromString(o.get("leader").getAsString());
        Guild guild = new Guild(id, name, leader);
        guild.setLevel(o.get("level").getAsInt());
        guild.addExperience(o.get("experience").getAsLong());
        guild.setExplosionProtection(o.has("explosionProtection") && o.get("explosionProtection").getAsBoolean());
        guild.setWarehouseEnabled(!o.has("warehouseEnabled") || o.get("warehouseEnabled").getAsBoolean());
        if (o.has("completedAdvancements")) {
            for (com.google.gson.JsonElement el : o.getAsJsonArray("completedAdvancements")) {
                guild.addCompletedAdvancement(el.getAsString());
            }
        }

        // 成员
        JsonObject members = o.getAsJsonObject("members");
        for (Map.Entry<String, JsonElement> e : members.entrySet()) {
            UUID pid = UUID.fromString(e.getKey());
            Guild.Role role = Guild.Role.byId(e.getValue().getAsString());
            guild.addMember(pid, role == null ? Guild.Role.MEMBER : role);
        }
        if (o.has("warehouseDeniedMembers")) {
            for (JsonElement denied : o.getAsJsonArray("warehouseDeniedMembers")) {
                guild.setWarehouseMemberEnabled(UUID.fromString(denied.getAsString()), false);
            }
        }

        // 领地
        if (o.has("territories")) {
            for (JsonElement el : o.getAsJsonArray("territories")) {
                JsonObject tj = el.getAsJsonObject();
                BlockPos center = new BlockPos(tj.get("x").getAsInt(), tj.get("y").getAsInt(), tj.get("z").getAsInt());
                String tName = tj.has("name") ? tj.get("name").getAsString() : "未命名领地";
                Territory t = new Territory(center, tj.get("radius").getAsInt(), tName);
                // 恢复领地独立权限
                if (tj.has("permissions")) {
                    JsonObject tp = tj.getAsJsonObject("permissions");
                    for (Guild.Role role : Guild.Role.values()) {
                        if (!tp.has(role.id)) continue;
                        JsonObject row = tp.getAsJsonObject(role.id);
                        for (Guild.Permission p : Guild.Permission.values()) {
                            if (row.has(p.id)) {
                                t.setPermission(role, p, row.get(p.id).getAsBoolean());
                            }
                        }
                    }
                }
                guild.getTerritories().add(t);
            }
        }

        return guild;
    }
}
