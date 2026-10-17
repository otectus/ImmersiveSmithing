package com.otectus.immersivesmithing.network.packet;

import com.otectus.immersivesmithing.client.ClientConfigSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The server's effective configuration as TOML text, for a remote client: the shared file in shared mode, which Forge's
 * own handshake sync (the world file's bytes) knows nothing about. Sent on login and after every rebuild (protocol 4).
 */
public record ConfigSyncPacket(byte[] toml) {
    private static final int MAX_BYTES = 1 << 20;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByteArray(toml);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buf) {
        return new ConfigSyncPacket(buf.readByteArray(MAX_BYTES));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientConfigSync.apply(toml));
    }
}
