package com.otectus.immersivesmithing.client;

import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.client.work.WorkHud;
import com.otectus.immersivesmithing.client.work.WorldViewInput;
import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.WorkLeases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Forge-bus client hooks of station work: World-view input, the focus lifecycle and effects each tick, the work view's
 * field of view (the saved option is never written), and hiding the first-person hands, crosshair and block outline while
 * a work view owns the camera. Registered from {@link ClientSetup}, so it only ever loads on a client.
 */
public final class WorkClientEvents {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        WorldViewInput.onInteraction(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        FocusController.tick();
        WorldViewInput.tick();
        WorkEffects.tick();
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov() || !FocusController.overridesView()) return;
        event.setFOV(FocusController.fov(event.getFOV()));
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (FocusController.overridesView()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (FocusController.overridesView()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (FocusController.overridesView() && event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        FocusController.reset();
        WorkEffects.stopAll();
    }

    /** The World-view status readout (the focused view draws its own, larger one). */
    static void renderWorldStatus(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null || FocusController.active()) return;
        BlockPos pos = WorldViewInput.lookedAtStation();
        if (pos == null) return;
        WorkLeases.Station kind = SmithingActionService.stationOf(mc.level.getBlockEntity(pos));
        if (kind == null) return;
        WorkHud.renderWorld(graphics, mc.font, mc.level, pos, kind, width, height);
    }

    private WorkClientEvents() {}
}
