package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.WorkAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One reported intent at a station: where (position and the station instance it was aimed at), what, a monotonic sequence
 * number, the target part, the point on the piece in canonical coordinates and flags. Decoding is bounded; everything else
 * is validated by {@link SmithingActionService} on the server thread.
 */
public record WorkActionPacket(BlockPos pos, int instance, int action, int sequence, int target, float u, float v, int flags) {

    public WorkActionPacket(BlockPos pos, int instance, WorkAction action, int sequence, int target, float u, float v, int flags) {
        this(pos, instance, action.ordinal(), sequence, target, u, v, flags);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(instance);
        buf.writeByte(action);
        buf.writeVarInt(sequence);
        buf.writeVarInt(target + 1);
        buf.writeFloat(u);
        buf.writeFloat(v);
        buf.writeByte(flags);
    }

    public static WorkActionPacket decode(FriendlyByteBuf buf) {
        return new WorkActionPacket(buf.readBlockPos(), buf.readInt(), buf.readUnsignedByte(), buf.readVarInt(),
                Math.min(buf.readVarInt(), 64) - 1, buf.readFloat(), buf.readFloat(), buf.readUnsignedByte());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer player = context.getSender();
        context.enqueueWork(() -> {
            if (player != null) SmithingActionService.handle(player, this);
        });
        context.setPacketHandled(true);
    }
}
