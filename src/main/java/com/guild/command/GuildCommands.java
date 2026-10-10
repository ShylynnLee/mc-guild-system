package com.guild.command;

import com.guild.block.GuildTerminalBlock;
import com.guild.config.GuildConfig;
import com.guild.config.WarehouseConfig;
import com.guild.guild.Guild;
import com.guild.guild.GuildManager;
import com.guild.guild.InvitationManager;
import com.guild.guild.Territory;
import com.guild.storage.GuildWarehouse;
import com.guild.storage.GuildWarehouseStorage;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.concurrent.CompletableFuture;

public class GuildCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("guild")
                .requires(src -> src.getEntity() instanceof ServerPlayer)

                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    if (GuildManager.createGuild(name, player)) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("公会已创建：" + name), true);
                                    } else {
                                        ctx.getSource().sendFailure(Component.literal("你已经在一个公会中"));
                                    }
                                    return 1;
                                })))

                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            Guild guild = GuildManager.getPlayerGuild(player);
                            if (guild == null) {
                                ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                return 0;
                            }
                            sendInfo(ctx.getSource(), guild);
                            return 1;
                        }))

                .then(Commands.literal("claim")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    String name = StringArgumentType.getString(ctx, "name");
                                    claimTerritory(ctx.getSource(), player, name);
                                    return 1;
                                })))

                // 传送至领地：/guild tp <领地名>（5秒吟唱，移动/被攻击取消）
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(GuildCommands::suggestTerritories)
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    Guild guild = GuildManager.getPlayerGuild(player);
                                    if (guild == null) {
                                        ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                        return 0;
                                    }
                                    String tName = StringArgumentType.getString(ctx, "name");
                                    java.util.Optional<Territory> terr = guild.findTerritory(tName);
                                    if (terr.isEmpty()) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "领地「" + tName + "」不存在，或不属于你的公会"));
                                        return 0;
                                    }
                                    boolean started = com.guild.guild.GuildTeleportHandler.start(player, terr.get());
                                    return started ? 1 : 0;
                                })))

                // 删除领地：/guild unclaim（当前领地） 或 /guild unclaim <领地名>
                .then(Commands.literal("unclaim")
                        .executes(ctx -> unclaimHere(ctx.getSource().getPlayer()))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> unclaimByName(ctx.getSource().getPlayer(),
                                        StringArgumentType.getString(ctx, "name")))))

                // 聊天点击菜单（客户端无需装 mod）
                .then(Commands.literal("menu")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            Guild guild = GuildManager.getPlayerGuild(player);
                            if (guild == null) {
                                ctx.getSource().sendFailure(Component.literal("你不在任何公会中，可用 /guild create 创建"));
                                return 0;
                            }
                            GuildMenuBuilder.sendMainMenu(player, guild);
                            return 1;
                        }))

                .then(Commands.literal("storage")
                        .executes(ctx -> openWarehouse(ctx.getSource().getPlayer(),
                                GuildWarehouse.Category.EQUIPMENT))
                        .then(Commands.literal("equipment")
                                .executes(ctx -> openWarehouse(ctx.getSource().getPlayer(),
                                        GuildWarehouse.Category.EQUIPMENT)))
                        .then(Commands.literal("materials")
                                .executes(ctx -> openWarehouse(ctx.getSource().getPlayer(),
                                        GuildWarehouse.Category.MATERIALS)))
                        .then(Commands.literal("blocks")
                                .executes(ctx -> openWarehouse(ctx.getSource().getPlayer(),
                                        GuildWarehouse.Category.BLOCKS))))

                .then(Commands.literal("warehouse")
                        .then(Commands.literal("on")
                                .executes(ctx -> setWarehouseEnabled(ctx.getSource().getPlayer(), true)))
                        .then(Commands.literal("off")
                                .executes(ctx -> setWarehouseEnabled(ctx.getSource().getPlayer(), false)))
                        .then(Commands.literal("toggle")
                                .executes(ctx -> toggleWarehouse(ctx.getSource().getPlayer())))
                        .then(Commands.argument("target", StringArgumentType.word())
                                .then(Commands.literal("on")
                                        .executes(ctx -> setMemberWarehouseByName(
                                                ctx.getSource(),
                                                ctx.getSource().getPlayer(),
                                                StringArgumentType.getString(ctx, "target"),
                                                true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setMemberWarehouseByName(
                                                ctx.getSource(),
                                                ctx.getSource().getPlayer(),
                                                StringArgumentType.getString(ctx, "target"),
                                                false)))))

                // 权限菜单：/guild perm [role] （当前领地） 或 /guild perm in <领地名> [role]
                .then(Commands.literal("perm")
                        // 当前领地
                        .executes(ctx -> showPermMenuHere(ctx.getSource().getPlayer(), Guild.Role.MEMBER))
                        .then(Commands.argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    String roleStr = StringArgumentType.getString(ctx, "role");
                                    Guild.Role role = Guild.Role.byId(roleStr);
                                    if (role == null) {
                                        ctx.getSource().sendFailure(Component.literal("无效角色"));
                                        return 0;
                                    }
                                    return showPermMenuHere(ctx.getSource().getPlayer(), role);
                                }))
                        // 指定领地：/guild perm in <领地名> [role]
                        .then(Commands.literal("in")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .executes(ctx -> showPermMenuIn(ctx.getSource().getPlayer(),
                                                StringArgumentType.getString(ctx, "territory"), Guild.Role.MEMBER))
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String roleStr = StringArgumentType.getString(ctx, "role");
                                                    Guild.Role role = Guild.Role.byId(roleStr);
                                                    if (role == null) {
                                                        ctx.getSource().sendFailure(Component.literal("无效角色"));
                                                        return 0;
                                                    }
                                                    return showPermMenuIn(ctx.getSource().getPlayer(),
                                                            StringArgumentType.getString(ctx, "territory"), role);
                                                })))))

                // 手动确认升级（会长/管理员，需成就条件已满足）
                .then(Commands.literal("upgrade")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            Guild guild = GuildManager.getPlayerGuild(player);
                            if (guild == null) {
                                ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                return 0;
                            }
                            Guild.Role role = guild.getRole(player.getUUID());
                            if (role != Guild.Role.LEADER && role != Guild.Role.ADMIN) {
                                ctx.getSource().sendFailure(Component.literal("只有会长或管理员可以升级公会"));
                                return 0;
                            }
                            if (!guild.canUpgrade()) {
                                ctx.getSource().sendFailure(Component.literal("升级条件尚未满足（需完成所需成就）"));
                                return 0;
                            }
                            int before = guild.getLevel();
                            guild.manualUpgrade();
                            int after = guild.getLevel();
                            int radius = guild.getTerritories().isEmpty() ? 0 : guild.getTerritories().get(0).getRadius();

                            Component msg = Component.literal(
                                    "🎉 公会「" + guild.getName() + "」升级：Lv." + before + " → Lv." + after
                                            + "（由 " + player.getName().getString() + " 确认）")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
                            for (java.util.UUID uid : guild.getMembers().keySet()) {
                                ServerPlayer member = ctx.getSource().getServer().getPlayerList().getPlayer(uid);
                                if (member != null) {
                                    member.sendSystemMessage(msg);
                                    if (radius > 0) {
                                        member.sendSystemMessage(Component.literal("   领地半径扩大到 " + radius)
                                                .withStyle(ChatFormatting.YELLOW));
                                    }
                                }
                            }
                            // 升级后刷新菜单，显示新的进度/可再升级状态
                            GuildMenuBuilder.sendMainMenu(player, guild);
                            return 1;
                        }))


                // ===== 正式邀请系统 =====
                // 发送邀请：对方必须没有公会
                .then(Commands.literal("invite")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer inviter = ctx.getSource().getPlayer();
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "target");

                                    Guild guild = GuildManager.getPlayerGuild(inviter);
                                    if (guild == null) {
                                        ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                        return 0;
                                    }
                                    // 没有公会才能被邀请
                                    if (GuildManager.getPlayerGuild(target.getUUID()) != null) {
                                        ctx.getSource().sendFailure(Component.literal("对方已有公会，需先退出才能被邀请"));
                                        return 0;
                                    }
                                    // 会长才能邀请
                                    if (guild.getRole(inviter.getUUID()) != Guild.Role.LEADER
                                            && guild.getRole(inviter.getUUID()) != Guild.Role.ADMIN) {
                                        ctx.getSource().sendFailure(Component.literal("只有会长或管理员可以邀请"));
                                        return 0;
                                    }
                                    InvitationManager.invite(target.getUUID(), guild.getId(), guild.getName(),
                                            inviter.getUUID(), inviter.getName().getString());

                                    target.sendSystemMessage(Component.literal(
                                            inviter.getName().getString() + " 邀请你加入公会「" + guild.getName()
                                                    + "」。使用 /guild accept 接受，/guild deny 拒绝（5分钟有效）"));
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "已向 " + target.getName().getString() + " 发送邀请"), true);
                                    return 1;
                                })))

                // 接受邀请：必须没有公会
                .then(Commands.literal("accept")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (GuildManager.getPlayerGuild(player.getUUID()) != null) {
                                ctx.getSource().sendFailure(Component.literal("你已有公会，请先 /guild leave"));
                                return 0;
                            }
                            InvitationManager.Invitation inv = InvitationManager.get(player.getUUID());
                            if (inv == null) {
                                ctx.getSource().sendFailure(Component.literal("没有待处理的公会邀请"));
                                return 0;
                            }
                            Guild guild = GuildManager.getGuild(inv.getGuildId());
                            if (guild == null) {
                                InvitationManager.remove(player.getUUID());
                                ctx.getSource().sendFailure(Component.literal("该公会已不存在"));
                                return 0;
                            }
                            // 再次确认：没有公会才能加入
                            if (GuildManager.getPlayerGuild(player.getUUID()) != null) {
                                ctx.getSource().sendFailure(Component.literal("你已有公会"));
                                return 0;
                            }
                            GuildManager.addMember(guild, player, Guild.Role.MEMBER);
                            InvitationManager.remove(player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "已加入公会「" + guild.getName() + "」"), true);
                            // 通知会长
                            ServerPlayer leader = ctx.getSource().getServer()
                                    .getPlayerList().getPlayer(guild.getLeader());
                            if (leader != null) {
                                leader.sendSystemMessage(Component.literal(
                                        player.getName().getString() + " 接受了邀请，加入了公会"));
                            }
                            return 1;
                        }))

                // 拒绝邀请
                .then(Commands.literal("deny")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            InvitationManager.Invitation inv = InvitationManager.get(player.getUUID());
                            if (inv == null) {
                                ctx.getSource().sendFailure(Component.literal("没有待处理的公会邀请"));
                                return 0;
                            }
                            InvitationManager.remove(player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal("已拒绝该邀请"), true);
                            return 1;
                        }))

                .then(Commands.literal("leave")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            Guild guild = GuildManager.getPlayerGuild(player);
                            if (guild == null) {
                                ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                return 0;
                            }
                            // 会长不能退出，只能解散
                            if (guild.getRole(player.getUUID()) == Guild.Role.LEADER) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "会长不能退出公会。若要结束公会，请使用 /guild disband 解散"));
                                return 0;
                            }
                            GuildManager.removeMember(guild, player.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.literal("已离开公会"), true);
                            return 1;
                        }))

                // 解散公会（只有会长）
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            Guild guild = GuildManager.getPlayerGuild(player);
                            if (guild == null) {
                                ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                return 0;
                            }
                            if (guild.getRole(player.getUUID()) != Guild.Role.LEADER) {
                                ctx.getSource().sendFailure(Component.literal("只有会长可以解散公会"));
                                return 0;
                            }
                            String guildName = guild.getName();
                            int territoryCount = guild.getTerritories().size();

                            // 先通知所有在线成员
                            Component disbandMsg = Component.literal(
                                    "💥 公会「" + guildName + "」已被 " + player.getName().getString() + " 解散")
                                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD);
                            for (java.util.UUID uid : guild.getMembers().keySet()) {
                                ServerPlayer member = ctx.getSource().getServer().getPlayerList().getPlayer(uid);
                                if (member != null && member != player) {
                                    member.sendSystemMessage(disbandMsg);
                                }
                            }

                            // 删除公会本身 → 所有领地随之失效
                            GuildWarehouseStorage.remove(guild.getId());
                            GuildManager.removeGuild(guild);

                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "公会「" + guildName + "」已解散，" + territoryCount + " 块领地已失效"), true);
                            return 1;
                        }))

                // ===== 爆炸保护开关 =====
                .then(Commands.literal("toggle")
                        .then(Commands.literal("explosion")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    Guild guild = GuildManager.getPlayerGuild(player);
                                    if (guild == null) {
                                        ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                        return 0;
                                    }
                                    if (guild.getRole(player.getUUID()) != Guild.Role.LEADER) {
                                        ctx.getSource().sendFailure(Component.literal("只有会长可以修改公会设置"));
                                        return 0;
                                    }
                                    boolean next = !guild.isExplosionProtection();
                                    guild.setExplosionProtection(next);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "爆炸保护已" + (next ? "开启" : "关闭")), true);
                                    return 1;
                                })))

                .then(Commands.literal("permission")
                        // 分支1：改【当前所在领地】 /guild permission <role> <action> <value>
                        .then(Commands.argument("role", StringArgumentType.word())
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayer();
                                                    Guild guild = GuildManager.getPlayerGuild(player);
                                                    if (guild == null) {
                                                        ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                                        return 0;
                                                    }
                                                    java.util.Optional<Territory> here =
                                                            guild.territoryAt(player.blockPosition());
                                                    if (here.isEmpty()) {
                                                        ctx.getSource().sendFailure(Component.literal(
                                                                "你不在任何领地中。请走进领地，或使用：/guild permission in <领地名> <角色> <动作> <true|false>"));
                                                        return 0;
                                                    }
                                                    return applyPermission(ctx.getSource(), player, guild, here.get(),
                                                            StringArgumentType.getString(ctx, "role"),
                                                            StringArgumentType.getString(ctx, "action"),
                                                            BoolArgumentType.getBool(ctx, "value"));
                                                }))))
                        // 分支2：指定领地 /guild permission in <领地名> <role> <action> <value>
                        .then(Commands.literal("in")
                                .then(Commands.argument("territory", StringArgumentType.word())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .then(Commands.argument("action", StringArgumentType.word())
                                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                                .executes(ctx -> {
                                                                    ServerPlayer player = ctx.getSource().getPlayer();
                                                                    Guild guild = GuildManager.getPlayerGuild(player);
                                                                    if (guild == null) {
                                                                        ctx.getSource().sendFailure(Component.literal("你不在任何公会中"));
                                                                        return 0;
                                                                    }
                                                                    String tName = StringArgumentType.getString(ctx, "territory");
                                                                    // 从【自己公会】的领地中查找 → 天然隔离其他公会
                                                                    java.util.Optional<Territory> terr = guild.findTerritory(tName);
                                                                    if (terr.isEmpty()) {
                                                                        ctx.getSource().sendFailure(Component.literal(
                                                                                "领地「" + tName + "」不存在，或不属于你的公会"));
                                                                        return 0;
                                                                    }
                                                                    return applyPermission(ctx.getSource(), player, guild, terr.get(),
                                                                            StringArgumentType.getString(ctx, "role"),
                                                                            StringArgumentType.getString(ctx, "action"),
                                                                            BoolArgumentType.getBool(ctx, "value"));
                                                                })))))))
        );
    }

    private static int openWarehouse(ServerPlayer player, GuildWarehouse.Category category) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (!WarehouseConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("服务器总开关已关闭公会仓库"));
            return 0;
        }
        if (!guild.isWarehouseEnabled()) {
            player.sendSystemMessage(Component.literal("公会仓库已被会长关闭"));
            return 0;
        }
        if (!guild.isWarehouseMemberAllowed(player.getUUID())) {
            player.sendSystemMessage(Component.literal("会长已单独关闭你的公会仓库权限"));
            return 0;
        }
        if (!guild.hasMember(player.getUUID()) || !guild.hasPermission(player.getUUID(), Guild.Permission.CHEST)) {
            player.sendSystemMessage(Component.literal("你没有公会仓库权限"));
            return 0;
        }

        GuildWarehouse warehouse = GuildWarehouseStorage.get(guild.getId(), category, guild.getLevel());
        int rows = warehouse.getSlotCount() / 9;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, p) -> new ChestMenu(
                        chestMenuType(rows), containerId, inventory, warehouse, rows),
                Component.literal("公会仓库 · " + category.displayName
                        + " (" + warehouse.getSlotCount() + "格)"));
        return player.openMenu(provider).isPresent() ? 1 : 0;
    }

    private static CompletableFuture<Suggestions> suggestTerritories(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        Guild guild = player == null ? null : GuildManager.getPlayerGuild(player);
        if (guild != null) {
            String input = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (Territory territory : guild.getTerritories()) {
                if (territory.getName().toLowerCase(java.util.Locale.ROOT).startsWith(input)) {
                    builder.suggest(territory.getName());
                }
            }
        }
        return builder.buildFuture();
    }

    private static MenuType<ChestMenu> chestMenuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Invalid warehouse rows: " + rows);
        };
    }

    private static int setWarehouseEnabled(ServerPlayer player, boolean enabled) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (guild.getRole(player.getUUID()) != Guild.Role.LEADER) {
            player.sendSystemMessage(Component.literal("只有会长可以开关公会仓库"));
            return 0;
        }
        guild.setWarehouseEnabled(enabled);
        com.guild.storage.GuildStorage.save();
        player.sendSystemMessage(Component.literal(enabled
                ? "✅ 公会仓库已打开，原物品已保留"
                : "🔒 公会仓库已关闭，原物品已保留"));
        return 1;
    }

    private static int toggleWarehouse(ServerPlayer player) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (guild.getRole(player.getUUID()) != Guild.Role.LEADER) {
            player.sendSystemMessage(Component.literal("只有会长可以开关公会仓库"));
            return 0;
        }
        return setWarehouseEnabled(player, !guild.isWarehouseEnabled());
    }

    private static int setMemberWarehouseByName(CommandSourceStack source, ServerPlayer actor,
                                                 String targetName, boolean enabled) {
        if (actor == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(actor);
        if (guild == null) {
            actor.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        java.util.UUID targetId = GuildManager.findMemberByName(source.getServer(), guild, targetName);
        if (targetId == null) {
            actor.sendSystemMessage(Component.literal("找不到该公会成员：" + targetName));
            return 0;
        }
        ServerPlayer onlineTarget = source.getServer().getPlayerList().getPlayer(targetId);
        return setMemberWarehouseEnabled(actor, targetId, targetName, onlineTarget, enabled);
    }

    private static int setMemberWarehouseEnabled(ServerPlayer actor, java.util.UUID targetId,
                                                 String targetName, ServerPlayer onlineTarget,
                                                 boolean enabled) {
        if (actor == null || targetId == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(actor);
        if (guild == null) {
            actor.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (guild.getRole(actor.getUUID()) != Guild.Role.LEADER) {
            actor.sendSystemMessage(Component.literal("只有会长可以开关成员仓库权限"));
            return 0;
        }
        if (!guild.hasMember(targetId)) {
            actor.sendSystemMessage(Component.literal("目标玩家不是本公会成员"));
            return 0;
        }

        guild.setWarehouseMemberEnabled(targetId, enabled);
        com.guild.storage.GuildStorage.save();
        actor.sendSystemMessage(Component.literal(enabled
                ? "✅ 已为 " + targetName + " 开启公会仓库"
                : "🔒 已为 " + targetName + " 关闭公会仓库（物品保留）"));
        if (onlineTarget != null && !onlineTarget.getUUID().equals(actor.getUUID())) {
            onlineTarget.sendSystemMessage(Component.literal(enabled
                    ? "✅ 会长已重新开启你的公会仓库权限"
                    : "🔒 会长已关闭你的公会仓库权限"));
        }
        return 1;
    }

    /**
     * 应用权限修改。
     * 隔离保证：territory 参数必须来自调用者自己的公会（已由调用方通过 guild 查找）。
     */
    private int applyPermission(CommandSourceStack src, ServerPlayer player, Guild guild,
                                Territory territory, String roleStr, String actionStr, boolean value) {
        // 只有会长或管理员可以修改权限
        Guild.Role actor = guild.getRole(player.getUUID());
        if (actor != Guild.Role.LEADER && actor != Guild.Role.ADMIN) {
            src.sendFailure(Component.literal("只有会长或管理员可以分配权限"));
            return 0;
        }
        Guild.Role role = Guild.Role.byId(roleStr);
        Guild.Permission perm = Guild.Permission.byId(actionStr);
        if (role == null || perm == null) {
            src.sendFailure(Component.literal("无效的角色或动作"));
            return 0;
        }
        territory.setPermission(role, perm, value);
        // 刷新权限菜单（显示该领地）
        GuildMenuBuilder.sendPermMenu(player, guild, territory, role);
        return 1;
    }

    /** 删除【当前所在领地】 */
    private int unclaimHere(ServerPlayer player) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (!canManageTerritory(player, guild)) return 0;

        java.util.Optional<Territory> here = guild.territoryAt(player.blockPosition());
        if (here.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "你不在任何领地中。请走进领地，或使用：/guild unclaim <领地名>"));
            return 0;
        }
        Territory t = here.get();
        guild.removeTerritory(t);
        player.sendSystemMessage(Component.literal(
                "已删除领地「" + t.getName() + "」，该区域保护已失效").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    /** 删除【指定名称】领地（只查自己公会，天然隔离） */
    private int unclaimByName(ServerPlayer player, String territoryName) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        if (!canManageTerritory(player, guild)) return 0;

        Territory t = guild.removeTerritoryByName(territoryName);
        if (t == null) {
            player.sendSystemMessage(Component.literal(
                    "领地「" + territoryName + "」不存在，或不属于你的公会"));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
                "已删除领地「" + t.getName() + "」，该区域保护已失效").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    /** 删除领地的权限：会长/管理员 */
    private boolean canManageTerritory(ServerPlayer player, Guild guild) {
        Guild.Role role = guild.getRole(player.getUUID());
        if (role != Guild.Role.LEADER && role != Guild.Role.ADMIN) {
            player.sendSystemMessage(Component.literal("只有会长或管理员可以删除领地"));
            return false;
        }
        return true;
    }

    /** 显示【当前所在领地】的权限菜单 */
    private int showPermMenuHere(ServerPlayer player, Guild.Role role) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        java.util.Optional<Territory> here = guild.territoryAt(player.blockPosition());
        if (here.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "你不在任何领地中。请走进领地，或使用：/guild perm in <领地名> [角色]"));
            return 0;
        }
        GuildMenuBuilder.sendPermMenu(player, guild, here.get(), role);
        return 1;
    }

    /** 显示【指定领地】的权限菜单（只查自己公会的领地，天然隔离） */
    private int showPermMenuIn(ServerPlayer player, String territoryName, Guild.Role role) {
        if (player == null) return 0;
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            player.sendSystemMessage(Component.literal("你不在任何公会中"));
            return 0;
        }
        java.util.Optional<Territory> terr = guild.findTerritory(territoryName);
        if (terr.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "领地「" + territoryName + "」不存在，或不属于你的公会"));
            return 0;
        }
        GuildMenuBuilder.sendPermMenu(player, guild, terr.get(), role);
        return 1;
    }

    private void sendInfo(CommandSourceStack src, Guild guild) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 公会：").append(guild.getName()).append(" ===\n");
        sb.append("等级: ").append(guild.getLevel()).append("  经验: ").append(guild.getExperience()).append("\n");
        sb.append("成员: ").append(guild.getMembers().size()).append(" 人\n");
        sb.append("领地: ").append(guild.getTerritories().size()).append("/").append(guild.getTerritoryLimit())
                .append(" 块（公会 Lv.").append(guild.getLevel()).append("）\n");
        sb.append("爆炸保护: ").append(guild.isExplosionProtection() ? "开启" : "关闭").append("\n");
        // 升级进度
        int next = guild.getLevel() + 1;
        java.util.List<String> req = com.guild.guild.UpgradeConfig.getRequirements(next);
        if (!req.isEmpty()) {
            sb.append("升级 Lv.").append(next).append(" 需要: ");
            for (String id : req) {
                Component title = GuildMenuBuilder.advancementTitle(id, src.getServer());
                sb.append(title.getString()).append(guild.hasCompleted(id) ? "(已完成) " : "(未完成) ");
            }
            sb.append("\n");
        }
        for (Territory t : guild.getTerritories()) {
            BlockPos c = t.getCenter();
            sb.append("  领地「").append(t.getName()).append("」 中心(")
                    .append(c.getX()).append(",").append(c.getY()).append(",").append(c.getZ())
                    .append(") 半径=").append(t.getRadius()).append(" 面积=").append(t.getArea()).append("\n");
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
    }

    private void claimTerritory(CommandSourceStack src, ServerPlayer player, String territoryName) {
        Guild guild = GuildManager.getPlayerGuild(player);
        if (guild == null) {
            src.sendFailure(Component.literal("你不在任何公会中"));
            return;
        }

        BlockPos playerPos = player.blockPosition();
        BlockPos terminalPos = playerPos;
        boolean found = false;

        for (int dx = -2; dx <= 2 && !found; dx++) {
            for (int dy = -2; dy <= 2 && !found; dy++) {
                for (int dz = -2; dz <= 2 && !found; dz++) {
                    BlockPos check = playerPos.offset(dx, dy, dz);
                    if (player.level().getBlockState(check).getBlock() instanceof GuildTerminalBlock) {
                        terminalPos = check;
                        found = true;
                    }
                }
            }
        }

        if (!found) {
            src.sendFailure(Component.literal("附近没有公会终端"));
            return;
        }

        // 领地名称唯一（同公会内）
        if (guild.territoryNameExists(territoryName)) {
            src.sendFailure(Component.literal("领地名称已存在：" + territoryName));
            return;
        }

        Territory territory = Territory.create(terminalPos, guild.getLevel(), territoryName);
        // 新领地以公会当前权限为初始模板，之后各领地独立修改
        territory.copyPermissionsFrom(guild.getPermissions());

        // 领地不能重叠（允许贴合：边界相邻但不共享方块）
        java.util.Optional<GuildManager.Overlap> overlap = GuildManager.findOverlap(territory);
        if (overlap.isPresent()) {
            Guild og = overlap.get().guild();
            Territory ot = overlap.get().territory();
            if (og == guild) {
                src.sendFailure(Component.literal(
                        "领地「" + territoryName + "」与你的领地「" + ot.getName() + "」重叠，"
                                + "请换位置（可与已有领地贴合，但不能重叠）"));
            } else {
                src.sendFailure(Component.literal(
                        "领地「" + territoryName + "」与公会「" + og.getName() + "」的领地「"
                                + ot.getName() + "」重叠，不能与其他公会领地重叠"));
            }
            return;
        }

        if (!guild.addTerritory(territory)) {
            src.sendFailure(Component.literal("公会领地已达上限 " + guild.getTerritoryLimit()
                    + " 块（当前公会 Lv." + guild.getLevel() + "）"));
            return;
        }

        src.sendSuccess(() -> Component.literal(
                "已创建领地「" + territory.getName() + "」，半径=" + territory.getRadius()), true);
    }
}
