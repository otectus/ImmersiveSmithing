package com.otectus.immersivesmithing.client;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.quality.GrindstonePreview;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/** Short-lived quote; never show a previous world's, station's or held item's price. */
@Mod.EventBusSubscriber(modid = ImmersiveSmithing.MOD_ID, value = Dist.CLIENT)
public final class GrindstonePreviews {
    private static GrindstonePreview latest;
    private static long receivedMs;

    public static void receive(GrindstonePreview preview) {
        latest = preview;
        receivedMs = Util.getMillis();
    }

    public static Optional<GrindstonePreview> current(Level level, BlockPos pos, ItemStack held) {
        return latest != null && Util.getMillis() - receivedMs <= 1500
                && latest.matches(level.dimension().location(), pos, held) ? Optional.of(latest) : Optional.empty();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        latest = null;
    }

    private GrindstonePreviews() {}
}
