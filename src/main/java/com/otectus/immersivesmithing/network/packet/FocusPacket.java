package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The server granted (open) or ended (closed) a smith's control lease at a station, with the reason when ended. */
public record FocusPacket(boolean open, BlockPos pos, int station, int nonce, Component reason) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeBlockPos(pos);
        buf.writeByte(station);
        buf.writeInt(nonce);
        buf.writeComponent(reason);
    }

    public static FocusPacket decode(FriendlyByteBuf buf) {
        return new FocusPacket(buf.readBoolean(), buf.readBlockPos(), buf.readUnsignedByte(), buf.readInt(), buf.readComponent());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.focus(this)));
        ctx.get().setPacketHandled(true);
    }
}
