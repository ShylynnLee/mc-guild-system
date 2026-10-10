package com.guild.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 公会系统配置。
 * 这里定义了领地数量、领地范围等核心参数。
 */
public final class GuildConfig {

    public static final ModConfigSpec SPEC;
    public static final Common COMMON;

    static {
        Pair<Common, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = pair.getLeft();
        SPEC = pair.getRight();
    }

    private GuildConfig() {
    }

    public static class Common {
        // 公会领地相关
        public final ModConfigSpec.IntValue initialTerritoryRadius;
        public final ModConfigSpec.IntValue territoryRadiusPerLevel;
        public final ModConfigSpec.IntValue territoryMinY;
        public final ModConfigSpec.IntValue territoryMaxY;

        // 公会基础
        public final ModConfigSpec.IntValue maxGuildMembers;

        Common(ModConfigSpec.Builder builder) {
            builder.push("territory");


            initialTerritoryRadius = builder
                    .comment("领地初始半径（方块数），领地是以终端为中心的正方形，默认 16")
                    .translation("guild.config.initialTerritoryRadius")
                    .defineInRange("initialTerritoryRadius", 24, 1, 4096);

            territoryRadiusPerLevel = builder
                    .comment("公会每升一级，领地半径自动增加的方块数（默认 8）")
                    .translation("guild.config.territoryRadiusPerLevel")
                    .defineInRange("territoryRadiusPerLevel", 8, 0, 256);


            territoryMinY = builder
                    .comment("领地下边界（含），默认 -64（下到虚空）")
                    .translation("guild.config.territoryMinY")
                    .defineInRange("territoryMinY", -64, -2032, 320);

            territoryMaxY = builder
                    .comment("领地上边界（含），默认 255（上到天空）")
                    .translation("guild.config.territoryMaxY")
                    .defineInRange("territoryMaxY", 255, -2032, 2047);

            builder.pop();

            builder.push("guild");

            maxGuildMembers = builder
                    .comment("每个公会最多成员数量")
                    .translation("guild.config.maxGuildMembers")
                    .defineInRange("maxGuildMembers", 50, 1, 1000);

            builder.pop();
        }
    }
}
