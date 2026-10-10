package com.guild.guild;

import com.guild.config.GuildConfig;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * 公会领地。
 *
 * 领地以【公会终端】为中心的正方形柱体：
 *   - X/Z：center ± radius
 *   - Y：配置的 [minY, maxY]（默认 -64 ~ 255）
 *
 * 每个领地：
 *   1. 有唯一【名称】
 *   2. 拥有【独立权限表】（角色 → 权限），与其他领地互不影响
 *   3. 只属于所属公会，其他公会不可修改
 */
public class Territory {

    private final BlockPos center;
    private int radius;
    private String name;

    /** 领地独立权限表：角色 → 权限动作 → 是否允许 */
    private final Map<Guild.Role, Map<Guild.Permission, Boolean>> permissions = new EnumMap<>(Guild.Role.class);

    public Territory(BlockPos center, int radius, String name) {
        this.center = center;
        this.radius = radius;
        this.name = (name == null || name.isEmpty()) ? "未命名领地" : name;
        initDefaultPermissions();
    }

    /** 初始化默认权限：会长/管理员/成员全开，访客全关 */
    private void initDefaultPermissions() {
        for (Guild.Role role : Guild.Role.values()) {
            Map<Guild.Permission, Boolean> row = new EnumMap<>(Guild.Permission.class);
            for (Guild.Permission p : Guild.Permission.values()) {
                row.put(p, false);
            }
            permissions.put(role, row);
        }
        for (Guild.Role role : new Guild.Role[]{Guild.Role.LEADER, Guild.Role.ADMIN, Guild.Role.MEMBER}) {
            for (Guild.Permission p : Guild.Permission.values()) {
                permissions.get(role).put(p, true);
            }
        }
    }

    // ===== 权限 =====

    /** 某公会成员在本领地是否有某权限（按其在公会的角色判定） */
    public boolean hasPermission(UUID playerId, Guild guild, Guild.Permission perm) {
        Guild.Role role = guild.getRole(playerId);
        return permissions.getOrDefault(role, Collections.emptyMap()).getOrDefault(perm, false);
    }

    public void setPermission(Guild.Role role, Guild.Permission perm, boolean value) {
        permissions.computeIfAbsent(role, r -> new EnumMap<>(Guild.Permission.class))
                .put(perm, value);
    }

    public Map<Guild.Role, Map<Guild.Permission, Boolean>> getPermissions() {
        return permissions;
    }

    /** 从模板（通常是公会默认权限）复制初始权限 */
    public void copyPermissionsFrom(Map<Guild.Role, Map<Guild.Permission, Boolean>> template) {
        if (template == null) return;
        for (Map.Entry<Guild.Role, Map<Guild.Permission, Boolean>> e : template.entrySet()) {
            permissions.put(e.getKey(), new EnumMap<>(e.getValue()));
        }
    }

    // ===== 几何 =====

    public static Territory create(BlockPos center, int guildLevel, String name) {
        int base = GuildConfig.COMMON.initialTerritoryRadius.get();
        int perLevel = GuildConfig.COMMON.territoryRadiusPerLevel.get();
        int radius = base + perLevel * Math.max(0, guildLevel - 1);
        return new Territory(center, radius, name);
    }

    public void refreshRadius(int guildLevel) {
        int base = GuildConfig.COMMON.initialTerritoryRadius.get();
        int perLevel = GuildConfig.COMMON.territoryRadiusPerLevel.get();
        this.radius = base + perLevel * Math.max(0, guildLevel - 1);
    }

    /**
     * 判断两个领地是否【重叠】（共享方块列）。
     *
     * X/Z 闭区间相交即重叠；只贴合（一个的边界紧挨另一个）不算重叠。
     * 领地在 Y 上都是全高，故只需比较 X/Z。
     *
     * 例：A[0,10] 与 B[11,21] → 贴合，允许
     *     A[0,10] 与 B[10,20] → 共享 x=10 → 重叠，拒绝
     */
    public boolean overlaps(Territory other) {
        if (other == null) return false;
        boolean overlapX = this.getMinX() <= other.getMaxX() && other.getMinX() <= this.getMaxX();
        boolean overlapZ = this.getMinZ() <= other.getMaxZ() && other.getMinZ() <= this.getMaxZ();
        return overlapX && overlapZ;
    }

    public boolean contains(BlockPos pos) {
        int minY = GuildConfig.COMMON.territoryMinY.get();
        int maxY = GuildConfig.COMMON.territoryMaxY.get();
        if (pos.getY() < minY || pos.getY() > maxY) return false;
        return Math.abs(pos.getX() - center.getX()) <= radius
                && Math.abs(pos.getZ() - center.getZ()) <= radius;
    }

    // ===== 基本信息 =====

    public BlockPos getCenter() { return center; }
    public int getRadius() { return radius; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getArea() { return (radius * 2 + 1) * (radius * 2 + 1); }
    public int getMinX() { return center.getX() - radius; }
    public int getMaxX() { return center.getX() + radius; }
    public int getMinZ() { return center.getZ() - radius; }
    public int getMaxZ() { return center.getZ() + radius; }
    public int getMinY() { return GuildConfig.COMMON.territoryMinY.get(); }
    public int getMaxY() { return GuildConfig.COMMON.territoryMaxY.get(); }
}
