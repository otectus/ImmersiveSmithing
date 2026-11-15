package com.otectus.immersivesmithing.client.work;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingHammerItem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The focused smith's own hammer, drawn at the anvil where the work is (the first-person hand is hidden while focused,
 * so there is exactly one hammer on screen). A blow is predicted at once: lift, a fast downswing, then it rests on the
 * metal until the server accepts or refuses the blow; only then does the material change, the sound play and the
 * hammer rebound. Observers do not see this tool; they see the smith's own arm swing and the accepted impact.
 */
public final class SceneTools {
    private static final long WIND_UP = 110, DOWN = 70, HOLD_MAX = 450, REBOUND = 160;

    @Nullable
    private static BlockPos strikePos;
    private static int strikeSeq = -1;
    private static long strikeStart;
    private static Vec3 strikePoint = Vec3.ZERO;
    private static long resolvedAt = -1;
    private static boolean heavy;

    public static void strike(BlockPos pos, int seq, Vec3 world, boolean heavyBlow) {
        strikePos = pos.immutable();
        strikeSeq = seq;
        strikeStart = Util.getMillis();
        strikePoint = world;
        resolvedAt = -1;
        heavy = heavyBlow;
        LocalPlayer player = Minecraft.getInstance().player;
        // The arm swing is broadcast by vanilla, so other players see the blow from the smith's body.
        if (player != null) player.swing(player.getMainHandItem().getItem() instanceof SmithingHammerItem ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    /** The server answered this blow (accepted or not): rebound now. */
    public static void resolve(int seq) {
        if (seq == strikeSeq && resolvedAt < 0) resolvedAt = Util.getMillis();
    }

    public static boolean pending(int seq) {
        return seq == strikeSeq;
    }

    /** Accepted impact effects wait for the predicted head to reach the metal, even on a fast local server. */
    public static long millisUntilImpact(int seq) {
        return seq == strikeSeq ? Math.max(0, strikeStart + WIND_UP + DOWN - Util.getMillis()) : 0;
    }

    /**
     * Draws the hammer for the focused smith at this anvil; {@code origin} is the block's world corner, the pose is the
     * block entity renderer's (block-local, not yet turned by facing).
     */
    public static void renderHammer(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int light) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !FocusController.focusedOn(pos)) return;
        ItemStack hammer = hammer(player);
        if (hammer.isEmpty()) return;
        WorkFocusScreen screen = WorkFocusScreen.showing();
        WorkTargets.Target target = screen != null ? screen.hoveredTarget() : null;
        long now = Util.getMillis();
        Vec3 point;
        float swing; // 0 = resting above the work, 1 = head on the metal, negative = lifted back
        boolean striking = strikePos != null && strikePos.equals(pos) && now - strikeStart < WIND_UP + DOWN + HOLD_MAX + REBOUND;
        boolean reduced = ClientConfig.get(ClientConfig.REDUCED_MOTION);
        if (striking) {
            point = strikePoint;
            long t = now - strikeStart;
            if (t < WIND_UP) swing = -smooth(t / (float) WIND_UP) * (heavy ? 0.6F : 0.4F);
            else if (t < WIND_UP + DOWN) swing = -(heavy ? 0.6F : 0.4F) + (1F + (heavy ? 0.6F : 0.4F)) * smooth((t - WIND_UP) / (float) DOWN);
            else if (resolvedAt < 0 && t < WIND_UP + DOWN + HOLD_MAX) swing = 1F;
            else {
                long since = resolvedAt < 0 ? t - WIND_UP - DOWN - HOLD_MAX : now - Math.max(resolvedAt, strikeStart + WIND_UP + DOWN);
                swing = 1F - smooth(Math.min(1F, since / (float) REBOUND));
            }
            if (reduced) swing = Math.max(0F, swing);
        } else {
            if (target == null || target.kind() != WorkTargets.Kind.OPERATION) return;
            point = target.world();
            swing = 0F;
        }
        Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition().subtract(pos.getX(), pos.getY(), pos.getZ());
        double hx = camera.x - local.x, hz = camera.z - local.z;
        double len = Math.sqrt(hx * hx + hz * hz);
        if (len < 1e-3) {
            hx = 0;
            hz = 1;
            len = 1;
        }
        double yaw = Math.atan2(hx, hz);
        // Between blows the hammer is held ready beside the work, so the piece stays in view; it comes over the point on
        // the downswing and goes back on the rebound.
        float aside = 1F - Math.max(0F, swing);
        double fx = -hx / len, fz = -hz / len;
        pose.pushPose();
        pose.translate(local.x - fz * 0.22 * aside, local.y + 0.025 + 0.18 * (1F - swing), local.z + fx * 0.22 * aside);
        pose.mulPose(Axis.YP.rotation((float) yaw));
        // Anchor the head, not the centre of the sprite: at contact the handle extends toward the smith, clear of
        // the work. NONE avoids the item-frame transform turning the head away from the intended contact point.
        pose.mulPose(Axis.XP.rotationDegrees(-25F - 65F * swing));
        pose.mulPose(Axis.ZP.rotationDegrees(45F));
        pose.scale(0.32F, 0.32F, 0.32F);
        pose.translate(-2 / 16F, -3 / 16F, 0);
        mc.getItemRenderer().renderStatic(hammer, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }

    private static ItemStack hammer(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SmithingHammerItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** The bare tongs' jaws meet the supplied point. Loading is represented by the station's real piece, never by
     * the inventory overlay as well. Use the smith's material tier when it is available. */
    public static void renderTongs(PoseStack pose, MultiBufferSource buffers, double x, double y, double z, int light) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack tool = new ItemStack(com.otectus.immersivesmithing.registry.ModItems.tongs(com.otectus.immersivesmithing.item.SmithingTier.IRON));
        if (mc.player != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = mc.player.getItemInHand(hand);
                if (held.getItem() instanceof com.otectus.immersivesmithing.item.SmithingTongsItem) {
                    tool = new ItemStack(held.getItem());
                    break;
                }
            }
        }
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(90F));
        // A steep grip clears the recessed forge rim and the water basin's wall before the handles lean outward.
        pose.mulPose(Axis.XP.rotationDegrees(155F));
        pose.mulPose(Axis.ZP.rotationDegrees(45F));
        pose.scale(0.4F, 0.4F, 0.4F);
        pose.translate(-3.5 / 16F, -3.5 / 16F, 0);
        mc.getItemRenderer().renderStatic(tool, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
        pose.popPose();
    }

    private static float smooth(float t) {
        t = Math.max(0F, Math.min(1F, t));
        return t * t * (3F - 2F * t);
    }

    private SceneTools() {}
}
