package com.otectus.immersivesmithing.client;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * The server configuration on a remote client. The server's effective TOML replaces what Forge's handshake synced (the
 * world file), with the same call Forge's sync makes. On leaving a remote server the values are dropped, so none survive
 * into the next server or world; until a server sends its own, the defaults apply. The integrated server's host shares
 * the server's spec and is never sent anything.
 */
public final class ClientConfigSync {

    /** Main thread. */
    public static void apply(byte[] toml) {
        if (Minecraft.getInstance().isLocalServer()) return;
        try {
            ServerConfig.SPEC.setConfig(ServerConfigSource.decode(toml));
        } catch (RuntimeException e) {
            ImmersiveSmithing.LOGGER.error("Could not apply the server's Immersive Smithing configuration", e);
        }
    }

    /** Forge bus, registered by {@link ClientSetup}. */
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Connection connection = event.getConnection();
        boolean remote = connection != null ? !connection.isMemoryConnection() : !Minecraft.getInstance().isLocalServer();
        if (remote) ServerConfig.SPEC.setConfig(null);
    }

    private ClientConfigSync() {}
}
