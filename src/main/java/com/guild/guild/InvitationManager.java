package com.guild.guild;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 公会邀请管理（邀请-确认系统）。
 *
 * 规则：没有公会的玩家才能被邀请、才能接受加入。
 * 邀请有时效，过期自动失效。
 */
public final class InvitationManager {

    /** 被邀请人 -> 邀请信息 */
    private static final Map<UUID, Invitation> INVITATIONS = new ConcurrentHashMap<>();

    /** 邀请有效期（毫秒） */
    public static final long EXPIRY_MS = 5 * 60 * 1000L; // 5 分钟

    private InvitationManager() {
    }

    public static class Invitation {
        private final String guildId;
        private final String guildName;
        private final UUID inviter;
        private final UUID inviterId;
        private final String inviterName;
        private final long createdAt;

        public Invitation(String guildId, String guildName, UUID inviter, String inviterName) {
            this.guildId = guildId;
            this.guildName = guildName;
            this.inviter = inviter;
            this.inviterId = inviter;
            this.inviterName = inviterName;
            this.createdAt = System.currentTimeMillis();
        }

        public String getGuildId() { return guildId; }
        public String getGuildName() { return guildName; }
        public UUID getInviter() { return inviter; }
        public String getInviterName() { return inviterName; }

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EXPIRY_MS;
        }
    }

    /** 创建/覆盖邀请 */
    public static void invite(UUID player, String guildId, String guildName, UUID inviter, String inviterName) {
        INVITATIONS.put(player, new Invitation(guildId, guildName, inviter, inviterName));
    }

    /** 获取有效邀请（过期则移除并返回 null） */
    public static Invitation get(UUID player) {
        Invitation inv = INVITATIONS.get(player);
        if (inv == null) return null;
        if (inv.isExpired()) {
            INVITATIONS.remove(player);
            return null;
        }
        return inv;
    }

    /** 移除邀请（接受或拒绝后） */
    public static void remove(UUID player) {
        INVITATIONS.remove(player);
    }

    /** 清空所有邀请（重载） */
    public static void clear() {
        INVITATIONS.clear();
    }
}
