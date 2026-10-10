package com.guild.command;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.resources.ResourceLocation;
import com.guild.config.GuildConfig;
import com.guild.guild.Guild;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 聊天点击菜单（方案1）。
 *
 * 用 Minecraft 原版聊天组件的 ClickEvent 实现按钮，
 * 客户端无需安装本 mod 即可显示和点击。
 *
 * 点击 = 客户端执行命令 → 服务端处理 → 服务端重新发送菜单（刷新）。
 */
public final class GuildMenuBuilder {

    private GuildMenuBuilder() {
    }

    // ========== 主菜单 ==========

    public static void sendMainMenu(ServerPlayer player, Guild guild) {
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(header(guild));
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(info(guild));
        player.sendSystemMessage(upgradeProgress(guild, player));
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.literal("  可点击操作（带 ☞）：")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));

        // 第一行操作按钮（升级条件满足时显示升级按钮）
        MutableComponent row1 = Component.literal("  ");
        if (guild.canUpgrade()) {
            row1.append(button("⬆ 升级公会", "/guild upgrade",
                    "会长/管理员确认升级到 Lv." + (guild.getLevel() + 1), ChatFormatting.LIGHT_PURPLE));
            row1.append(Component.literal(" "));
        }
        row1.append(button("权限设置", "/guild perm member", "管理当前所在领地的权限（需走进领地）", ChatFormatting.GREEN));
        row1.append(Component.literal(" "));
        row1.append(suggestButton("圈占领地", "/guild claim ", "点击填入命令，需在公会终端旁并补全领地名称", ChatFormatting.GREEN));
        row1.append(Component.literal(" "));
        row1.append(button("邀请成员", "/guild invite ", "点击后输入玩家名邀请", ChatFormatting.AQUA));
        player.sendSystemMessage(row1);

        // 第二行操作按钮
        MutableComponent row2 = Component.literal("  ");
        String expText = guild.isExplosionProtection() ? "爆炸保护✅" : "爆炸保护❌";
        row2.append(button(expText, "/guild toggle explosion", "切换公会领地爆炸保护", ChatFormatting.GOLD));
        row2.append(Component.literal(" "));
        row2.append(button("公会信息", "/guild info", "查看公会详细信息", ChatFormatting.YELLOW));
        row2.append(Component.literal(" "));
        row2.append(button("离开公会", "/guild leave", "离开当前公会", ChatFormatting.RED));
        player.sendSystemMessage(row2);

        MutableComponent row3 = Component.literal("  ");
        row3.append(button("公会仓库", "/guild storage", "打开公会成员共享仓库", ChatFormatting.AQUA));
        if (guild.getRole(player.getUUID()) == Guild.Role.LEADER) {
            String state = guild.isWarehouseEnabled() ? "仓库:开" : "仓库:关";
            row3.append(Component.literal(" "));
            row3.append(button(state, "/guild warehouse toggle",
                    guild.isWarehouseEnabled() ? "关闭公会仓库（物品会保留）" : "重新打开公会仓库",
                    guild.isWarehouseEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        player.sendSystemMessage(row3);

        MutableComponent row4 = Component.literal("  ");
        row4.append(button("装备", "/guild storage equipment", "打开装备分类仓库", ChatFormatting.GOLD));
        row4.append(Component.literal(" "));
        row4.append(button("矿物", "/guild storage materials", "打开矿物分类仓库", ChatFormatting.GRAY));
        row4.append(Component.literal(" "));
        row4.append(button("方块", "/guild storage blocks", "打开方块分类仓库", ChatFormatting.GREEN));
        player.sendSystemMessage(row4);

        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("  ☞ 点击带手指标记的文字；先按 T 打开聊天框")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    /** 升级进度：显示下一等级所需成就及完成状态 */
    private static Component upgradeProgress(Guild guild, net.minecraft.server.level.ServerPlayer viewer) {
        int next = guild.getLevel() + 1;
        java.util.List<String> req = com.guild.guild.UpgradeConfig.getRequirements(next);
        if (req.isEmpty()) {
            return Component.literal("  升级：已达到配置的最高等级 Lv." + guild.getLevel())
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }

        MutableComponent c = Component.literal("  升级 Lv." + next + "：").withStyle(ChatFormatting.GRAY);
        for (int i = 0; i < req.size(); i++) {
            String id = req.get(i);
            boolean done = guild.hasCompleted(id);
            Component title = advancementTitle(id, viewer.getServer());
            c.append(title.copy().append(Component.literal(done ? "✅" : "❌"))
                    .withStyle(done ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (i < req.size() - 1) c.append(Component.literal(" "));
        }

        if (guild.canUpgrade()) {
            c.append(Component.literal("   ✅ 条件满足，会长/管理员执行 /guild upgrade 确认")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        } else {
            c.append(Component.literal("  (任一成员完成)").withStyle(ChatFormatting.DARK_GRAY));
        }
        return c;
    }

    /**
     * 获取成就的【本地化标题】。
     * 服务端返回的是 translatable 组件，客户端会按自己的语言渲染成中文。
     * 成就不存在（如未装对应 mod）时回退到英文短名。
     */
    /** 成就中文名兜底表（注册表取不到标题时使用） */
    private static final java.util.Map<String, String> CHINESE_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("minecraft:story/crafting_table", "制作工作台"),
            java.util.Map.entry("minecraft:story/iron", "铁！"),
            java.util.Map.entry("minecraft:story/enter_the_nether", "下界"),
            java.util.Map.entry("minecraft:story/enter_the_end", "结束便是起点"),
            java.util.Map.entry("minecraft:story/diamond", "钻石！"),
            java.util.Map.entry("minecraft:story/enchantments", "附魔台"),
            java.util.Map.entry("minecraft:story/anvil_use", "这有点贵"),
            java.util.Map.entry("minecraft:story/shaft", "螺旋上升"),
            java.util.Map.entry("create:andesite_alloy", "安山合金"),
            java.util.Map.entry("create:mechanical_press", "机械压力机"),
            java.util.Map.entry("create:precision_mechanism", "精密构件"),
            java.util.Map.entry("create:steam_engine", "蒸汽引擎")
    );

    private static final java.util.Set<String> DEBUGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static Component advancementTitle(String id, net.minecraft.server.MinecraftServer server) {
        try {
            if (server != null) {
                AdvancementHolder adv = server.getAdvancements().get(ResourceLocation.parse(id));
                if (adv != null) {
                    java.util.Optional<DisplayInfo> display = adv.value().display();
                    if (display.isPresent()) {
                        Component title = display.get().getTitle();
                        debug(id, "title=" + title);
                        return title;
                    }
                    debug(id, "adv存在但无display");
                } else {
                    debug(id, "adv=null(注册表查不到)");
                }
            } else {
                debug(id, "server=null");
            }
        } catch (Throwable t) {
            debug(id, "异常=" + t);
        }
        // 兜底：内置中文名 → 英文短名
        String cn = CHINESE_NAMES.get(id);
        return Component.literal(cn != null ? cn : shortName(id));
    }

    /** 每个 ID 只打印一次诊断信息，避免刷屏 */
    private static void debug(String id, String msg) {
        if (DEBUGGED.add(id)) {
            System.out.println("[GuildTitle] " + id + " -> " + msg);
        }
    }

    /** 把成就 ID 转成较短的显示名（兜底） */
    private static String shortName(String id) {
        String simple = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        String ns = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String prefix = ns.equals("minecraft") ? "" : (ns.equals("create") ? "[Create] " : "[" + ns + "] ");
        return prefix + simple.replace('_', ' ');
    }

    // ========== 权限菜单 ==========

    public static void sendPermMenu(ServerPlayer player, Guild guild, com.guild.guild.Territory territory, Guild.Role role) {
        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("  ☞ 点击带手指标记的文字；先按 T 打开聊天框")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("  权限管理 · 领地「" + territory.getName() + "」· " + role.displayName)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

        boolean canManage = guild.getRole(player.getUUID()) == Guild.Role.LEADER
                || guild.getRole(player.getUUID()) == Guild.Role.ADMIN;

        if (!canManage) {
            player.sendSystemMessage(Component.literal("  ⚠ 只有会长/管理员可以修改权限")
                    .withStyle(ChatFormatting.RED));
        }

        player.sendSystemMessage(Component.empty());

        for (Guild.Permission perm : Guild.Permission.values()) {
            boolean on = territory.getPermissions()
                    .getOrDefault(role, java.util.Collections.emptyMap())
                    .getOrDefault(perm, false);

            MutableComponent line = Component.literal("    ");
            line.append(Component.literal(pad(perm.displayName, 4)).withStyle(ChatFormatting.WHITE));
            line.append(Component.literal(on ? " ✅ " : " ❌ ")
                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));

            if (canManage) {
                String cmd = "/guild permission in " + territory.getName() + " "
                        + role.id + " " + perm.id + " " + (!on);
                line.append(button(on ? "[点击关闭]" : "[点击打开]", cmd,
                        "切换 " + role.displayName + " 的" + perm.displayName + "权限",
                        on ? ChatFormatting.RED : ChatFormatting.GREEN));
            } else {
                line.append(Component.literal("  (只读)").withStyle(ChatFormatting.DARK_GRAY));
            }
            player.sendSystemMessage(line);
        }

        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.literal("  切换角色：").withStyle(ChatFormatting.GRAY));

        MutableComponent roles = Component.literal("  ");
        for (Guild.Role r : Guild.Role.values()) {
            boolean selected = r == role;
            roles.append(button(selected ? "[" + r.displayName + "]" : r.displayName,
                    "/guild perm in " + territory.getName() + " " + r.id,
                    "查看该领地 " + r.displayName + " 的权限",
                    selected ? ChatFormatting.YELLOW : ChatFormatting.DARK_AQUA));
            roles.append(Component.literal(" "));
        }
        player.sendSystemMessage(roles);

        player.sendSystemMessage(Component.empty());

        // 多领地时：切换到其他领地的权限设置
        if (guild.getTerritories().size() > 1) {
            MutableComponent terrBtns = Component.literal("  切换领地：");
            terrBtns.withStyle(ChatFormatting.GRAY);
            for (com.guild.guild.Territory t : guild.getTerritories()) {
                boolean selected = t.getName().equals(territory.getName());
                terrBtns.append(button(selected ? "[" + t.getName() + "]" : t.getName(),
                        "/guild perm in " + t.getName() + " " + role.id,
                        "查看领地「" + t.getName() + "」的权限",
                        selected ? ChatFormatting.YELLOW : ChatFormatting.DARK_AQUA));
                terrBtns.append(Component.literal(" "));
            }
            player.sendSystemMessage(terrBtns);
            player.sendSystemMessage(Component.empty());
        }

        MutableComponent footer = Component.literal("  ");
        footer.append(button("[返回主菜单]", "/guild menu", "返回公会主会菜单", ChatFormatting.AQUA));
        player.sendSystemMessage(footer);

        player.sendSystemMessage(Component.literal("━━━━━━━━━━━━━━━━━━━━").withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("  ☞ 点击带手指标记的文字；先按 T 打开聊天框")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
    }

    // ========== 辅助 ==========

    private static MutableComponent header(Guild guild) {
        MutableComponent c = Component.literal("  公会「" + guild.getName() + "」");
        c.append(Component.literal("  等级 Lv." + guild.getLevel())
                .withStyle(ChatFormatting.GOLD));
        c.append(Component.literal("  经验 " + guild.getExperience())
                .withStyle(ChatFormatting.YELLOW));
        return c.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    private static Component info(Guild guild) {
        int territories = guild.getTerritories().size();
        int limit = guild.getTerritoryLimit();
        int radius = guild.getTerritories().isEmpty() ? 0 : guild.getTerritories().get(0).getRadius();

        MutableComponent c = Component.literal("  成员 " + guild.getMembers().size());
        c.append(Component.literal("  ｜  领地 " + territories + "/" + limit));
        if (territories > 0) {
            c.append(Component.literal("（" + territoryList(guild) + "）"));
        }
        c.append(Component.literal("  ｜  爆炸保护 " + (guild.isExplosionProtection() ? "✅" : "❌"))
                .withStyle(guild.isExplosionProtection() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return c.copy().withStyle(ChatFormatting.WHITE);
    }

    /** 领地名称列表，如：主基地(半径32), 分矿(半径32) */
    private static String territoryList(Guild guild) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < guild.getTerritories().size(); i++) {
            com.guild.guild.Territory t = guild.getTerritories().get(i);
            if (i > 0) sb.append(", ");
            sb.append(t.getName()).append("(半径").append(t.getRadius()).append(")");
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    /** 构造一个可点击按钮（点击直接执行命令） */
    private static MutableComponent button(String text, String command, String hover, ChatFormatting color) {
        return Component.literal("☞ " + text).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("点击执行：" + hover))));
    }

    /** 构造"点击填入命令"按钮（用于需要玩家补参数的命令，如圈地名） */
    private static MutableComponent suggestButton(String text, String commandPrefix, String hover, ChatFormatting color) {
        return Component.literal("☞ " + text).withStyle(style -> style
                .withColor(color)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, commandPrefix))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("点击填入命令：" + hover))));
    }

    private static String pad(String s, int len) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(" ");
        return sb.toString();
    }
}
