package com.guild.network;

import com.guild.GuildMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 向客户端同步公会成员名单和名牌穿墙配置。
 */
public record GuildNametagSyncPayload(boolean enabled, int maxDistance, List<UUID> members)
        implements CustomPacketPayload {

    public static final Type<GuildNametagSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GuildMod.MODID, "nametag_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GuildNametagSyncPayload> CODEC =
            StreamCodec.of(GuildNametagSyncPayload::encode, GuildNametagSyncPayload::decode);

    public GuildNametagSyncPayload {
        members = List.copyOf(members);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, GuildNametagSyncPayload payload) {
        buffer.writeBoolean(payload.enabled);
        buffer.writeVarInt(payload.maxDistance);
        buffer.writeVarInt(payload.members.size());
        for (UUID member : payload.members) {
            buffer.writeUUID(member);
        }
    }

    private static GuildNametagSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int maxDistance = buffer.readVarInt();
        int count = Math.min(buffer.readVarInt(), 256);
        List<UUID> members = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            members.add(buffer.readUUID());
        }
        return new GuildNametagSyncPayload(enabled, maxDistance, members);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
