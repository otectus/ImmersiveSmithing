package com.otectus.immersivesmithing.client;

import com.otectus.immersivesmithing.client.screen.MakersMarkScreen;
import com.otectus.immersivesmithing.client.screen.PatternBookScreen;
import com.otectus.immersivesmithing.client.work.FocusController;
import com.otectus.immersivesmithing.client.work.WorkEffects;
import com.otectus.immersivesmithing.network.packet.CastResultPacket;
import com.otectus.immersivesmithing.network.packet.CloseSessionPacket;
import com.otectus.immersivesmithing.network.packet.FocusPacket;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.network.packet.OpenMakersMarkPacket;
import com.otectus.immersivesmithing.network.packet.StationEffectPacket;
import com.otectus.immersivesmithing.network.packet.StationNoticePacket;
import com.otectus.immersivesmithing.network.packet.WorkEventPacket;
import com.otectus.immersivesmithing.client.work.WorkFocusScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Client handlers for server packets. Runs on the client main thread. */
public final class ClientPacketHandler {

    /** The pattern book opens over a work view (and returns to it) or on its own. */
    public static void openForge(OpenForgeScreenPacket packet) {
        show(new PatternBookScreen(packet));
    }

    public static void castResult(CastResultPacket packet) {
        if (Minecraft.getInstance().screen instanceof PatternBookScreen screen && screen.sessionId() == packet.sessionId()) {
            screen.onCastResult(packet);
        }
    }

    public static void closeSession(CloseSessionPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PatternBookScreen book && book.sessionId() == packet.sessionId()) book.closeByServer();
        if (mc.player != null && !packet.reason().getString().isEmpty()) StationNotices.show(packet.reason(), true);
    }

    public static void stationNotice(StationNoticePacket packet) {
        StationNotices.show(packet.message(), packet.failure());
    }

    public static void openMakersMark(OpenMakersMarkPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null || mc.screen instanceof MakersMarkScreen || mc.screen instanceof WorkFocusScreen) {
            show(new MakersMarkScreen(packet.slot(), packet.piece()));
        }
    }

    public static void stationEffect(StationEffectPacket packet) {
        ClientEffects.spawn(packet.pos(), packet.type(), packet.strength());
    }

    public static void focus(FocusPacket packet) {
        FocusController.onPacket(packet);
    }

    public static void workEvent(WorkEventPacket packet) {
        WorkEffects.onEvent(packet);
    }

    private static void show(Screen screen) {
        if (FocusController.active()) FocusController.openOverlay(screen);
        else Minecraft.getInstance().setScreen(screen);
    }

    private ClientPacketHandler() {}
}
