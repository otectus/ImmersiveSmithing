package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * An accepted (or refused) action at a station, sent to everyone tracking it: the material change, its sound and its sparks
 * all come from this one event, at the contact point in station-local coordinates. {@code actor} and {@code sequence} let
 * the acting client match it to the tool motion it already predicted, so it never plays a second impact.
 */
public record WorkEventPacket(BlockPos pos, int type, int actor, int sequence, float x, float y, float z, float strength, int data) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(type);
        buf.writeVarInt(actor);
        buf.writeVarInt(sequence);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeFloat(z);
        buf.writeFloat(strength);
        buf.writeVarInt(data);
    }

    public static WorkEventPacket decode(FriendlyByteBuf buf) {
        return new WorkEventPacket(buf.readBlockPos(), buf.readUnsignedByte(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.workEvent(this)));
        ctx.get().setPacketHandled(true);
    }
}
