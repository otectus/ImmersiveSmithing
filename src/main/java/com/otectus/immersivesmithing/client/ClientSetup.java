package com.otectus.immersivesmithing.client;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.client.particle.SparkParticle;
import com.otectus.immersivesmithing.client.renderer.SmithsAnvilRenderer;
import com.otectus.immersivesmithing.client.renderer.SmithsForgeRenderer;
import com.otectus.immersivesmithing.client.renderer.SmithsTroughRenderer;
import com.otectus.immersivesmithing.client.renderer.StationModels;
import com.otectus.immersivesmithing.client.renderer.TongsItemRenderer;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModParticles;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registration (mod bus, plus the server config's logout listener). Reached only through DistExecutor, so it never loads on a server. */
public final class ClientSetup {

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::onClientSetup);
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(StationNotices::register);
        modBus.addListener(ClientSetup::onRegisterParticles);
        modBus.addListener(ClientSetup::onRegisterModels);
        modBus.addListener(TongsItemRenderer::modifyModels);
        modBus.addListener(com.otectus.immersivesmithing.client.work.WorkKeys::register);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        modBus.addListener(ClientSetup::onRegisterOverlays);
        MinecraftForge.EVENT_BUS.addListener(ClientConfigSync::onLoggingOut);
        MinecraftForge.EVENT_BUS.register(WorkClientEvents.class);
    }

    private static void onRegisterReloadListeners(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new com.otectus.immersivesmithing.client.work.WorkpieceVisuals());
    }

    /** The World-view work status, drawn under the crosshair while looking at a station with work on it. */
    private static void onRegisterOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerAbove(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id(), "work_status",
                (gui, graphics, partialTick, width, height) -> WorkClientEvents.renderWorldStatus(graphics, width, height));
    }

    private static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        StationModels.ALL.forEach(event::register);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (SmithingTier tier : SmithingTier.values()) {
                ItemProperties.register(ModItems.tongs(tier), ImmersiveSmithing.id("loaded"),
                        (stack, level, entity, seed) -> WorkpieceCodec.isHolding(stack) ? 1.0F : 0.0F);
            }
        });
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHS_FORGE.get(), SmithsForgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHS_ANVIL.get(), SmithsAnvilRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHS_TROUGH.get(), SmithsTroughRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SMITHS_GRINDSTONE.get(), com.otectus.immersivesmithing.client.renderer.SmithsGrindstoneRenderer::new);
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK.get(), SparkParticle.Provider::new);
    }

    private ClientSetup() {}
}
