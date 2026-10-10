package com.guild.guild;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * 公会数据模型。
 * 包含成员、角色权限、等级、领地等核心数据。
 */
public class Guild {

    /** 公会角色 */
    public enum Role {
        LEADER("leader", "会长"),
        ADMIN("admin", "管理员"),
        MEMBER("member", "成员"),
        GUEST("guest", "访客");

        public final String id;
        public final String displayName;

        Role(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static Role byId(String id) {
            for (Role r : values()) {
                if (r.id.equalsIgnoreCase(id)) return r;
            }
            return null;
        }
    }

    /** 权限动作 */
    public enum Permission {
        BUILD("build", "建筑"),
        BREAK("break", "破坏"),
        USE("use", "使用工具"),
        CHEST("chest", "容器");

        public final String id;
        public final String displayName;

        Permission(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public static Permission byId(String id) {
            for (Permission p : values()) {
                if (p.id.equalsIgnoreCase(id)) return p;
            }
            return null;
        }
    }

    private final String id;                 // 公会唯一 ID
    private String name;                     // 公会名称
    private UUID leader;                     // 会长 UUID
    private int level;                       // 公会等级
    private long experience;                 // 公会经验

    /** 成员映射：玩家 UUID -> 角色 */
    private final Map<UUID, Role> members = new LinkedHashMap<>();
    /** 权限表：角色 -> 权限动作 -> 是否允许 */
    private final Map<Role, Map<Permission, Boolean>> permissions = new EnumMap<>(Role.class);
    /** 公会领地列表 */
    private final List<Territory> territories = new ArrayList<>();
    /** 是否开启爆炸保护（默认开启） */
    private boolean explosionProtection = true;
    /** 是否开放公会仓库（默认开放） */
    private boolean warehouseEnabled = true;
    /** 被会长单独关闭仓库的成员 */
    private final Set<UUID> warehouseDeniedMembers = new HashSet<>();
    /** 公会已达成的成就 ID（用于升级判定） */
    private final Set<String> completedAdvancements = new HashSet<>();

    public Guild(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.level = 1;
        this.experience = 0;
        this.members.put(leader, Role.LEADER);
        initDefaultPermissions();
    }

    /** 初始化默认权限表 */
    private void initDefaultPermissions() {
        for (Role role : Role.values()) {
            Map<Permission, Boolean> map = new EnumMap<>(Permission.class);
            for (Permission p : Permission.values()) {
                map.put(p, false);
            }
            permissions.put(role, map);
        }
        // 会长：全部允许
        set(Role.LEADER, Permission.BUILD, true);
        set(Role.LEADER, Permission.BREAK, true);
        set(Role.LEADER, Permission.USE, true);
        set(Role.LEADER, Permission.CHEST, true);
        // 管理员：全部允许
        set(Role.ADMIN, Permission.BUILD, true);
        set(Role.ADMIN, Permission.BREAK, true);
        set(Role.ADMIN, Permission.USE, true);
        set(Role.ADMIN, Permission.CHEST, true);
        // 成员：建筑 / 破坏 / 工具 / 箱子 全部允许
        set(Role.MEMBER, Permission.BUILD, true);
        set(Role.MEMBER, Permission.BREAK, true);
        set(Role.MEMBER, Permission.USE, true);
        set(Role.MEMBER, Permission.CHEST, true);
    }

    private void set(Role role, Permission perm, boolean value) {
        permissions.get(role).put(perm, value);
    }

    // ===== 成员管理 =====

    public boolean addMember(UUID player, Role role) {
        if (members.size() >= com.guild.config.GuildConfig.COMMON.maxGuildMembers.get()) {
            return false;
        }
        members.put(player, role);
        return true;
    }

    public void removeMember(UUID player) {
        members.remove(player);
    }

    public boolean hasMember(UUID player) {
        return members.containsKey(player);
    }

    public Role getRole(UUID player) {
        return members.getOrDefault(player, Role.GUEST);
    }

    public Map<UUID, Role> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    /** 判断成员在领地内是否拥有某权限 */
    public boolean hasPermission(UUID player, Permission perm) {
        Role role = getRole(player);
        return permissions.getOrDefault(role, Collections.emptyMap()).getOrDefault(perm, false);
    }

    /** 设置某角色某权限 */
    public void setPermission(Role role, Permission perm, boolean value) {
        permissions.computeIfAbsent(role, r -> new EnumMap<>(Permission.class)).put(perm, value);
    }

    public Map<Role, Map<Permission, Boolean>> getPermissions() {
        return permissions;
    }

    // ===== 领地 =====

    public List<Territory> getTerritories() {
        return territories;
    }

    public boolean addTerritory(Territory t) {
        if (territories.size() >= getTerritoryLimit()) {
            return false;
        }
        territories.add(t);
        return true;
    }

    /** 当前公会等级对应的领地数上限（随等级提升） */
    public int getTerritoryLimit() {
        return TerritoryLimitConfig.getLimit(level);
    }

    /** 按名称查找本领地（不区分大小写） */
    public Optional<Territory> findTerritory(String name) {
        if (name == null) return Optional.empty();
        for (Territory t : territories) {
            if (t.getName().equalsIgnoreCase(name)) return Optional.of(t);
        }
        return Optional.empty();
    }

    /** 领地名称是否已存在 */
    public boolean territoryNameExists(String name) {
        return findTerritory(name).isPresent();
    }

    /** 删除指定领地，返回是否成功 */
    public boolean removeTerritory(Territory t) {
        return territories.remove(t);
    }

    /** 按名称删除领地，返回被删除的领地（不存在则 null） */
    public Territory removeTerritoryByName(String name) {
        Territory t = findTerritory(name).orElse(null);
        if (t != null) territories.remove(t);
        return t;
    }

    /** 判断坐标是否属于公会任一领地 */
    public Optional<Territory> territoryAt(BlockPos pos) {
        for (Territory t : territories) {
            if (t.contains(pos)) return Optional.of(t);
        }
        return Optional.empty();
    }

    // ===== 升级 =====

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        // 升级后刷新所有领地半径
        for (Territory t : territories) {
            t.refreshRadius(level);
        }
    }

    public long getExperience() {
        return experience;
    }

    public void addExperience(long amount) {
        this.experience += amount;
    }

    // ===== 成就与升级 =====

    /** 记录一个已达成成就 */
    public void addCompletedAdvancement(String id) {
        if (id != null && !id.isEmpty()) completedAdvancements.add(id);
    }

    /** 是否已达成该成就 */
    public boolean hasCompleted(String id) {
        return completedAdvancements.contains(id);
    }

    public Set<String> getCompletedAdvancements() {
        return Collections.unmodifiableSet(completedAdvancements);
    }

    /** 供存储层使用：直接拿到可变集合 */
    public Set<String> completedAdvancementsMutable() {
        return completedAdvancements;
    }

    /**
     * 是否满足下一级的升级条件（成就已全部达成，等待会长/管理员手动确认）。
     */
    public boolean canUpgrade() {
        int next = level + 1;
        java.util.List<String> req = UpgradeConfig.getRequirements(next);
        if (req.isEmpty()) return false;
        for (String id : req) {
            if (!completedAdvancements.contains(id)) return false;
        }
        return true;
    }

    /**
     * 手动升级：仅升一级，需当前等级条件已满足。
     * @return 是否成功升级
     */
    public boolean manualUpgrade() {
        if (!canUpgrade()) return false;
        setLevel(level + 1);
        return true;
    }

    // ===== 爆炸保护 =====

    public boolean isExplosionProtection() {
        return explosionProtection;
    }

    public void setExplosionProtection(boolean enabled) {
        this.explosionProtection = enabled;
    }

    public boolean isWarehouseEnabled() {
        return warehouseEnabled;
    }

    public void setWarehouseEnabled(boolean enabled) {
        this.warehouseEnabled = enabled;
    }

    public boolean isWarehouseMemberAllowed(UUID player) {
        return !warehouseDeniedMembers.contains(player);
    }

    public void setWarehouseMemberEnabled(UUID player, boolean enabled) {
        if (enabled) {
            warehouseDeniedMembers.remove(player);
        } else {
            warehouseDeniedMembers.add(player);
        }
    }

    public Set<UUID> getWarehouseDeniedMembers() {
        return Collections.unmodifiableSet(warehouseDeniedMembers);
    }

    // ===== 基本信息 =====

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        if (!members.containsKey(leader)) {
            members.put(leader, Role.LEADER);
        }
    }
}
