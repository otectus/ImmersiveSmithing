package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * The server's answer in the Forge screen's selection stage: after a cast (what was cast, how much metal is left) or a
 * refused choice of a dimmed recipe. It carries the refreshed recipe rows and cast options; {@code closed} means the forge
 * is empty and the session is over.
 */
public record CastResultPacket(int sessionId, int sequence, ItemStack cast, int moltenUnits, List<OpenForgeScreenPacket.Entry> entries,
                               List<OpenForgeScreenPacket.CastOption> castOptions, Component message, boolean closed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(sessionId);
        buf.writeVarInt(sequence);
        buf.writeItem(cast);
        buf.writeVarInt(moltenUnits);
        OpenForgeScreenPacket.writeEntries(buf, entries);
        OpenForgeScreenPacket.writeCastOptions(buf, castOptions);
        buf.writeComponent(message);
        buf.writeBoolean(closed);
    }

    public static CastResultPacket decode(FriendlyByteBuf buf) {
        return new CastResultPacket(buf.readVarInt(), buf.readVarInt(), buf.readItem(), buf.readVarInt(),
                OpenForgeScreenPacket.readEntries(buf), OpenForgeScreenPacket.readCastOptions(buf), buf.readComponent(), buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.castResult(this));
    }
}
