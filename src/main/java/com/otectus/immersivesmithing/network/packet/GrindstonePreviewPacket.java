package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.GrindstonePreviews;
import com.otectus.immersivesmithing.quality.GrindstonePreview;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-calculated refinement prices for the held piece at the station being looked at. */
public record GrindstonePreviewPacket(GrindstonePreview preview) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(preview.dimension());
        buf.writeBlockPos(preview.pos());
        // Match inventory synchronization, including other mods' share-tag/capability handling.
        buf.writeItem(preview.piece());
        buf.writeVarInt(preview.durabilityCost());
        buf.writeVarInt(preview.efficacyCost());
    }

    public static GrindstonePreviewPacket decode(FriendlyByteBuf buf) {
        return new GrindstonePreviewPacket(new GrindstonePreview(buf.readResourceLocation(), buf.readBlockPos(),
                buf.readItem(), buf.readVarInt(), buf.readVarInt()));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GrindstonePreviews.receive(preview));
    }
}
