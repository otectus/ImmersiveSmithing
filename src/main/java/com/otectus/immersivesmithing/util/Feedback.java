package com.otectus.immersivesmithing.util;

import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.StationNoticePacket;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Station messages. A real player receives them as notices that stay readable above the hotbar for the client's
 * configured time (several at once, a repeat refreshing rather than stacking), so a second message no longer wipes the
 * first before it is read. Anything else (fake players) gets a plain action-bar message.
 */
public final class Feedback {

    public static void info(Player player, String key, Object... args) {
        info(player, Component.translatable("message.immersive_smithing." + key, args));
    }

    public static void info(Player player, Component message) {
        send(player, message, false);
    }

    public static void fail(Player player, String key, Object... args) {
        fail(player, Component.translatable("message.immersive_smithing." + key, args));
    }

    public static void fail(Player player, Component message) {
        send(player, message, true);
        player.playNotifySound(ModSounds.ACTION_FAIL.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    private static void send(Player player, Component message, boolean failure) {
        if (player instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && sp.connection != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new StationNoticePacket(message, failure));
        } else {
            player.displayClientMessage(message, true);
        }
    }

    private Feedback() {}
}
