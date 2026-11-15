package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.client.StationNotices;
import com.otectus.immersivesmithing.client.screen.MakersMarkScreen;
import com.otectus.immersivesmithing.client.screen.PatternBookScreen;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.network.packet.FocusPacket;
import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.WorkAction;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The client side of a work lease: asks the server for focus, plans the shot, owns the camera override, the work screen
 * and every way out of them. The camera is presentation only: the player's body stays where it is, with ordinary collision
 * and vulnerability, and the server measures reach and sight from the real eyes.
 *
 * <p>Exit is immediate on Escape, a movement key, the focus key, damage, death, a jump in position (knockback, teleport),
 * a dimension change, the station disappearing or the server ending the lease; a cosmetic blend back may continue after
 * control has returned. Chat, the inventory, the pattern book and the engraving screen pause the view and return to it.</p>
 */
public final class FocusController {
    private static final long ENTRY_MS = 280;
    private static final long EXIT_MS = 200;

    public record Pose(Vec3 position, float yaw, float pitch) {}

    public static final class Focus {
        public final BlockPos pos;
        public final WorkLeases.Station kind;
        public final int nonce;
        public final ClientConfig.CameraMode mode;
        public final WorkCamera.Shot shot;
        final ResourceKey<Level> dimension;
        final long startMs;
        final Vec3 startEye;
        final float startYaw, startPitch;
        final Vec3 anchor;
        boolean overlay;
        long exitMs;
        int ticks;

        Focus(BlockPos pos, WorkLeases.Station kind, int nonce, ClientConfig.CameraMode mode, WorkCamera.Shot shot, LocalPlayer player) {
            this.pos = pos.immutable();
            this.kind = kind;
            this.nonce = nonce;
            this.mode = mode;
            this.shot = shot;
            this.dimension = player.level().dimension();
            this.startMs = Util.getMillis();
            this.startEye = player.getEyePosition();
            this.startYaw = player.getYRot();
            this.startPitch = player.getXRot();
            this.anchor = player.position();
        }
    }

    @Nullable
    private static Focus focus;
    @Nullable
    private static Focus leaving;
    @Nullable
    private static BlockPos requested;
    private static long requestedMs;
    private static int hookFrames;
    private static double lastFov = 70;

    @Nullable
    public static Focus current() {
        return focus;
    }

    public static boolean active() {
        return focus != null;
    }

    public static boolean focusedOn(BlockPos pos) {
        return focus != null && focus.pos.equals(pos);
    }

    /** Called from the camera mixin each frame it runs, so a missing hook can fall back to World view. */
    public static void hookAlive() {
        hookFrames++;
    }

    public static ClientConfig.CameraMode mode() {
        return ClientConfig.get(ClientConfig.CAMERA_MODE);
    }

    /** Asks to work at a station in a focused view. In World view nothing is requested; the station is worked by clicking. */
    public static void request(BlockPos pos) {
        if (mode() == ClientConfig.CameraMode.WORLD || focus != null) return;
        requested = pos.immutable();
        requestedMs = Util.getMillis();
        WorkClient.send(pos, WorkAction.FOCUS);
    }

    public static void onPacket(FocusPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!packet.open()) {
            if (focus != null && focus.pos.equals(packet.pos())) exit(packet.reason(), false);
            else if (!packet.reason().getString().isEmpty()) StationNotices.show(packet.reason(), true);
            return;
        }
        WorkLeases.Station kind = WorkLeases.Station.byId(packet.station());
        if (player == null || mc.level == null || kind == null || focus != null) return;
        boolean asked = requested != null && requested.equals(packet.pos());
        requested = null;
        ClientConfig.CameraMode mode = mode();
        if (!asked || mode == ClientConfig.CameraMode.WORLD) {
            WorkClient.send(packet.pos(), WorkAction.RELEASE);
            return;
        }
        boolean fitting = mc.level.getBlockEntity(packet.pos()) instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null
                && anvil.workpiece().stage().quenched();
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double fov = ClientConfig.get(ClientConfig.CAMERA_FOV);
        WorkCamera.Shot shot = WorkCamera.plan(mc.level, player, packet.pos(), kind, fitting, mode, ClientConfig.get(ClientConfig.CAMERA_SIDE), aspect, fov);
        if (shot == null && mode == ClientConfig.CameraMode.CINEMATIC) {
            shot = WorkCamera.plan(mc.level, player, packet.pos(), kind, fitting, ClientConfig.CameraMode.SHOULDER,
                    ClientConfig.get(ClientConfig.CAMERA_SIDE), aspect, fov);
        }
        if (shot == null) {
            WorkClient.send(packet.pos(), WorkAction.RELEASE);
            StationNotices.show(Component.translatable("message.immersive_smithing.no_room_for_view"), false);
            return;
        }
        focus = new Focus(packet.pos(), kind, packet.nonce(), mode, shot, player);
        leaving = null;
        hookFrames = 0;
        // The real head turns to the work, so observers see where the smith is looking.
        player.lookAt(EntityAnchorArgument.Anchor.EYES, shot.focus());
        mc.setScreen(new WorkFocusScreen(focus));
    }

    /** Ends the focus now. The server is told unless it ended it; the camera may blend back cosmetically. */
    public static void exit(@Nullable Component reason, boolean notifyServer) {
        Focus f = focus;
        if (f == null) return;
        focus = null;
        if (notifyServer) WorkClient.send(f.pos, WorkAction.RELEASE);
        boolean blend = !ClientConfig.get(ClientConfig.REDUCED_MOTION) && ClientConfig.get(ClientConfig.CAMERA_TRANSITION_SPEED) > 0;
        if (blend) {
            f.exitMs = Util.getMillis();
            leaving = f;
        } else {
            leaving = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WorkFocusScreen) mc.setScreen(null);
        if (reason != null && !reason.getString().isEmpty()) StationNotices.show(reason, true);
    }

    /** The focus screen is about to open another screen that should return to it. */
    public static void openOverlay(Screen screen) {
        if (focus == null) return;
        focus.overlay = true;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (focus == null) {
            if (requested != null && Util.getMillis() - requestedMs > 3000) requested = null;
            return;
        }
        Focus f = focus;
        f.ticks++;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            exit(null, false);
            return;
        }
        String why = null;
        if (!player.isAlive()) why = "session_interrupted";
        else if (!mc.level.dimension().equals(f.dimension)) why = "session_interrupted";
        else if (player.hurtTime > 0 && player.hurtTime >= player.hurtDuration - 1) why = "session_interrupted";
        else if (player.position().distanceToSqr(f.anchor) > 0.6 * 0.6) why = "moved_away";
        else if (SmithingActionService.stationOf(mc.level.getBlockEntity(f.pos)) != f.kind) why = "station_missing";
        else if (f.ticks == 6 && hookFrames == 0) why = "camera_unavailable";
        if (why != null) {
            exit(Component.translatable("message.immersive_smithing." + why), true);
            return;
        }
        Screen screen = mc.screen;
        if (screen == null) {
            if (f.overlay) {
                f.overlay = false;
                mc.setScreen(new WorkFocusScreen(f));
            } else {
                exit(null, true);
                return;
            }
        } else if (!(screen instanceof WorkFocusScreen) && !isOverlay(screen)) {
            exit(null, true);
            return;
        }
        if (f.ticks % 100 == 0) WorkClient.send(f.pos, WorkAction.FOCUS); // keeps the lease while watching
    }

    private static boolean isOverlay(Screen screen) {
        return screen instanceof ChatScreen || screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                || screen instanceof PatternBookScreen || screen instanceof MakersMarkScreen;
    }

    /** The camera override for this frame, or null for the ordinary camera. */
    @Nullable
    public static Pose cameraPose(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return null;
        long now = Util.getMillis();
        if (focus != null) {
            float t = blend(now - focus.startMs, ENTRY_MS);
            Vec3 pos = focus.startEye.lerp(focus.shot.position(), t);
            return new Pose(pos, Mth.rotLerp(t, focus.startYaw, focus.shot.yaw()), Mth.lerp(t, focus.startPitch, focus.shot.pitch()));
        }
        if (leaving != null) {
            float t = blend(now - leaving.exitMs, EXIT_MS);
            if (t >= 1F) {
                leaving = null;
                return null;
            }
            Vec3 eye = player.getEyePosition(partialTick);
            return new Pose(leaving.shot.position().lerp(eye, t), Mth.rotLerp(t, leaving.shot.yaw(), player.getViewYRot(partialTick)),
                    Mth.lerp(t, leaving.shot.pitch(), player.getViewXRot(partialTick)));
        }
        return null;
    }

    /** The field of view for this frame (the saved option is never written). */
    public static double fov(double vanilla) {
        long now = Util.getMillis();
        double result = vanilla;
        if (focus != null) result = Mth.lerp(blend(now - focus.startMs, ENTRY_MS), vanilla, focus.shot.fov());
        else if (leaving != null) result = Mth.lerp(blend(now - leaving.exitMs, EXIT_MS), leaving.shot.fov(), vanilla);
        lastFov = result;
        return result;
    }

    /** The FOV the current frame used, for picking. */
    public static double lastFov() {
        return lastFov;
    }

    /** Whether the first-person hands, crosshair and block outline are hidden this frame. */
    public static boolean overridesView() {
        return focus != null || leaving != null;
    }

    private static float blend(long elapsed, long duration) {
        double speed = ClientConfig.get(ClientConfig.CAMERA_TRANSITION_SPEED);
        if (ClientConfig.get(ClientConfig.REDUCED_MOTION) || speed <= 0) return 1F;
        float t = Mth.clamp((float) (elapsed / (duration / speed)), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /** Leaving the world or disconnecting drops everything without messages. */
    public static void reset() {
        focus = null;
        leaving = null;
        requested = null;
    }

    private FocusController() {}
}
