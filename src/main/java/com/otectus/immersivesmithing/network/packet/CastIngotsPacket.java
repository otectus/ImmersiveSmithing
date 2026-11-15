package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.minigame.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A cast request from the Forge screen: which offered output and how many. The sequence number only grows, so a
 * duplicate or replayed packet is ignored; the server checks everything else and delivers what really fits.
 */
public record CastIngotsPacket(int sessionId, int sequence, ResourceLocation item, int count) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sequence);
        buf.writeResourceLocation(item);
        buf.writeVarInt(Math.max(0, count));
    }

    public static CastIngotsPacket decode(FriendlyByteBuf buf) {
        return new CastIngotsPacket(buf.readVarInt(), buf.readVarInt(), buf.readResourceLocation(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SessionManager.castIngots(player, sessionId, sequence, item, count);
        });
        context.setPacketHandled(true);
    }
}
