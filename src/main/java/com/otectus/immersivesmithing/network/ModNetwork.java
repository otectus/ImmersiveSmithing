package com.otectus.immersivesmithing.network;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.network.packet.CancelSessionPacket;
import com.otectus.immersivesmithing.network.packet.CastIngotsPacket;
import com.otectus.immersivesmithing.network.packet.CastResultPacket;
import com.otectus.immersivesmithing.network.packet.CloseSessionPacket;
import com.otectus.immersivesmithing.network.packet.ConfigSyncPacket;
import com.otectus.immersivesmithing.network.packet.FocusPacket;
import com.otectus.immersivesmithing.network.packet.GrindstonePreviewPacket;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.network.packet.OpenMakersMarkPacket;
import com.otectus.immersivesmithing.network.packet.SelectForgeRecipePacket;
import com.otectus.immersivesmithing.network.packet.SignWorkPacket;
import com.otectus.immersivesmithing.network.packet.StationEffectPacket;
import com.otectus.immersivesmithing.network.packet.StationNoticePacket;
import com.otectus.immersivesmithing.network.packet.WorkActionPacket;
import com.otectus.immersivesmithing.network.packet.WorkEventPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Small, purpose-specific packets. Clients report intents only; the server owns every outcome. Protocol 6 (1.3.0's
 * hands-on stations) replaced the minigame packets with {@link WorkActionPacket}, {@link FocusPacket} and
 * {@link WorkEventPacket}; client and server must match exactly, and a mismatched pair is refused at login.
 */
public final class ModNetwork {
    private static final String VERSION = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(ImmersiveSmithing.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        // Server -> client
        CHANNEL.messageBuilder(OpenForgeScreenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenForgeScreenPacket::encode).decoder(OpenForgeScreenPacket::decode)
                .consumerMainThread(OpenForgeScreenPacket::handle).add();
        CHANNEL.messageBuilder(CloseSessionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloseSessionPacket::encode).decoder(CloseSessionPacket::decode)
                .consumerMainThread(CloseSessionPacket::handle).add();
        CHANNEL.messageBuilder(StationEffectPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationEffectPacket::encode).decoder(StationEffectPacket::decode)
                .consumerMainThread(StationEffectPacket::handle).add();
        CHANNEL.messageBuilder(OpenMakersMarkPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenMakersMarkPacket::encode).decoder(OpenMakersMarkPacket::decode)
                .consumerMainThread(OpenMakersMarkPacket::handle).add();
        CHANNEL.messageBuilder(StationNoticePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationNoticePacket::encode).decoder(StationNoticePacket::decode)
                .consumerMainThread(StationNoticePacket::handle).add();
        CHANNEL.messageBuilder(GrindstonePreviewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GrindstonePreviewPacket::encode).decoder(GrindstonePreviewPacket::decode)
                .consumerMainThread(GrindstonePreviewPacket::handle).add();
        CHANNEL.messageBuilder(ConfigSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ConfigSyncPacket::encode).decoder(ConfigSyncPacket::decode)
                .consumerMainThread(ConfigSyncPacket::handle).add();
        CHANNEL.messageBuilder(CastResultPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CastResultPacket::encode).decoder(CastResultPacket::decode)
                .consumerMainThread(CastResultPacket::handle).add();
        CHANNEL.messageBuilder(FocusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FocusPacket::encode).decoder(FocusPacket::decode)
                .consumerMainThread(FocusPacket::handle).add();
        CHANNEL.messageBuilder(WorkEventPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WorkEventPacket::encode).decoder(WorkEventPacket::decode)
                .consumerMainThread(WorkEventPacket::handle).add();
        // Client -> server
        CHANNEL.messageBuilder(SelectForgeRecipePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectForgeRecipePacket::encode).decoder(SelectForgeRecipePacket::decode)
                .consumerNetworkThread(SelectForgeRecipePacket::handle).add();
        CHANNEL.messageBuilder(CancelSessionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CancelSessionPacket::encode).decoder(CancelSessionPacket::decode)
                .consumerNetworkThread(CancelSessionPacket::handle).add();
        CHANNEL.messageBuilder(SignWorkPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SignWorkPacket::encode).decoder(SignWorkPacket::decode)
                .consumerNetworkThread(SignWorkPacket::handle).add();
        CHANNEL.messageBuilder(CastIngotsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CastIngotsPacket::encode).decoder(CastIngotsPacket::decode)
                .consumerNetworkThread(CastIngotsPacket::handle).add();
        CHANNEL.messageBuilder(WorkActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkActionPacket::encode).decoder(WorkActionPacket::decode)
                .consumerNetworkThread(WorkActionPacket::handle).add();
    }

    private ModNetwork() {}
}
