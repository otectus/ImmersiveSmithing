package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.work.WorkLeases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plans the framed work view of a station: from where the smith stands, an authored elevation and distance per station,
 * slightly to the preferred side. Candidates are tried in order (preferred side, other side, nearer, then a shoulder view)
 * and each is accepted only if the whole straight path from the player's eyes to it is free, the camera point has room
 * around it and the station is visible from it. Nothing in the world is hidden to make a shot work; with no safe
 * candidate the caller falls back to World view. Long pieces on narrow screens get a slightly farther camera rather than
 * a cropped piece.
 */
public final class WorkCamera {
    public record Shot(Vec3 position, float yaw, float pitch, double fov, Vec3 focus) {}

    private record Framing(double elevation, double distance, double lateral) {}

    public static Vec3 focusLocal(WorkLeases.Station kind, boolean fitting) {
        return switch (kind) {
            case FORGE -> StationLayout.FORGE_FOCUS;
            case ANVIL -> fitting ? StationLayout.ANVIL_FOCUS.add(0, 0, 0.1) : StationLayout.ANVIL_FOCUS;
            case TROUGH -> StationLayout.TROUGH_FOCUS;
            case GRINDSTONE -> StationLayout.GRINDSTONE_FOCUS;
        };
    }

    private static Framing framing(WorkLeases.Station kind, boolean fitting) {
        return switch (kind) {
            case FORGE -> new Framing(60, 1.65, 0.32);
            case ANVIL -> fitting ? new Framing(62, 1.05, 0.12) : new Framing(56, 1.32, 0.22);
            case TROUGH -> new Framing(30, 1.45, 0.38);
            case GRINDSTONE -> new Framing(30, 1.35, 0.62);
        };
    }

    @Nullable
    public static Shot plan(Level level, Entity player, BlockPos pos, WorkLeases.Station kind, boolean fitting,
                            ClientConfig.CameraMode mode, ClientConfig.Side side, double aspect, double fov) {
        BlockState state = level.getBlockState(pos);
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        Vec3 focus = StationLayout.toWorld(pos, facing, focusLocal(kind, fitting));
        Vec3 eye = player.getEyePosition();
        double s = side == ClientConfig.Side.RIGHT ? 1 : -1;
        List<Vec3> candidates = new ArrayList<>();
        if (mode == ClientConfig.CameraMode.CINEMATIC) {
            Framing f = framing(kind, fitting);
            double distance = f.distance * Math.max(1.0, (16.0 / 9.0) / Math.max(0.5, aspect));
            Vec3 toPlayer = new Vec3(eye.x - focus.x, 0, eye.z - focus.z);
            Vec3 h = toPlayer.lengthSqr() < 1e-4 ? StationLayout.dirToWorld(facing, new Vec3(0, 0, -1)) : toPlayer.normalize();
            Vec3 perp = new Vec3(-h.z, 0, h.x);
            double elevation = Math.toRadians(f.elevation);
            Vec3 base = focus.add(h.scale(distance * Math.cos(elevation))).add(0, distance * Math.sin(elevation), 0);
            candidates.add(base.add(perp.scale(f.lateral * s)));
            candidates.add(base.add(perp.scale(-f.lateral * s)));
            candidates.add(focus.add(base.add(perp.scale(f.lateral * s)).subtract(focus).scale(0.72)));
            candidates.add(focus.add(base.subtract(focus).scale(0.55)));
        }
        Vec3 look = player.getViewVector(1F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        candidates.add(eye.subtract(flat.scale(0.75)).add(right.scale(0.45 * s)).add(0, 0.18, 0));
        candidates.add(eye.subtract(flat.scale(0.75)).add(right.scale(-0.45 * s)).add(0, 0.18, 0));
        candidates.add(eye.subtract(flat.scale(0.35)).add(0, 0.12, 0));
        for (Vec3 c : candidates) {
            if (!clear(level, player, eye, c, focus, pos)) continue;
            double shotFov = mode == ClientConfig.CameraMode.SHOULDER ? fov * 1.12 : fov;
            return new Shot(c, yaw(c, focus), pitch(c, focus), shotFov, focus);
        }
        return null;
    }

    /** The straight path from the eyes, room around the camera, and a view of the station. */
    static boolean clear(Level level, Entity player, Vec3 eye, Vec3 camera, Vec3 focus, BlockPos station) {
        if (!level.noCollision(new AABB(camera, camera).inflate(0.16))) return false;
        BlockHitResult path = level.clip(new ClipContext(eye, camera, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (path.getType() != HitResult.Type.MISS) return false;
        BlockHitResult sight = level.clip(new ClipContext(camera, focus, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return sight.getType() == HitResult.Type.MISS || sight.getBlockPos().equals(station);
    }

    public static float yaw(Vec3 from, Vec3 to) {
        return Mth.wrapDegrees((float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90F);
    }

    public static float pitch(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dz = to.z - from.z;
        return Mth.wrapDegrees((float) -(Mth.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
    }

    /** A ray through a point of the screen (0..1 each way) for a camera pose. */
    public static Vec3 ray(float yaw, float pitch, double fov, double aspect, double sx, double sy) {
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double tanY = Math.tan(Math.toRadians(fov) / 2.0);
        double ndcX = sx * 2.0 - 1.0, ndcY = 1.0 - sy * 2.0;
        return forward.add(right.scale(ndcX * tanY * aspect)).add(up.scale(ndcY * tanY)).normalize();
    }

    private WorkCamera() {}
}
