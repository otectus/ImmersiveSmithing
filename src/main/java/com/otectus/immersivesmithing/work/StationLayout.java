package com.otectus.immersivesmithing.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * The single source of station geometry. Every station model is authored facing north (front at z = 0) and rotated by its
 * blockstate; {@link #yaw} is that rotation for a {@code PoseStack} about the block centre, and {@link #toWorld}/{@link #toLocal}
 * are its exact inverses. Renderers, the client picker, server-side part routing and observer effects all go through here, so
 * a part is drawn, aimed at, validated and sparked at the same place in every facing.
 *
 * <p>Local coordinates are in blocks (0..1) in the north-facing model frame.</p>
 */
public final class StationLayout {
    private static final int[] COS = {1, 0, -1, 0};
    private static final int[] SIN = {0, -1, 0, 1};

    /** Quarter turns (clockwise seen from above) of the north-facing model. */
    public static int turns(Direction facing) {
        return (facing.get2DDataValue() + 2) % 4;
    }

    /** Degrees for {@code Axis.YP.rotationDegrees} about the block centre; matches the blockstate's {@code y} rotation. */
    public static float yaw(Direction facing) {
        return -turns(facing) * 90F;
    }

    public static Vec3 toWorld(BlockPos pos, Direction facing, double x, double y, double z) {
        int q = turns(facing);
        double cx = x - 0.5, cz = z - 0.5;
        // Rotation about +Y by yaw: x' = x cos + z sin, z' = -x sin + z cos, with exact quarter-turn values.
        return new Vec3(pos.getX() + 0.5 + cx * COS[q] + cz * SIN[q], pos.getY() + y, pos.getZ() + 0.5 - cx * SIN[q] + cz * COS[q]);
    }

    public static Vec3 toWorld(BlockPos pos, Direction facing, Vec3 local) {
        return toWorld(pos, facing, local.x, local.y, local.z);
    }

    public static Vec3 toLocal(BlockPos pos, Direction facing, Vec3 world) {
        int q = turns(facing);
        double wx = world.x - pos.getX() - 0.5, wz = world.z - pos.getZ() - 0.5;
        return new Vec3(0.5 + wx * COS[q] - wz * SIN[q], world.y - pos.getY(), 0.5 + wx * SIN[q] + wz * COS[q]);
    }

    /** A direction in local coordinates turned into world space (no translation). */
    public static Vec3 dirToWorld(Direction facing, Vec3 local) {
        int q = turns(facing);
        return new Vec3(local.x * COS[q] + local.z * SIN[q], local.y, -local.x * SIN[q] + local.z * COS[q]);
    }

    // ------------------------------------------------------------------ forge

    /** Named parts of the forge, decided from a local hit position. */
    public enum ForgePart {
        TRAY, BASIN, BELLOWS, REHEAT, FUEL, BOOK, SUPPLY, BODY;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final double FORGE_TOP = 10 / 16.0;
    /** The billet tray: front strip of the hearth top, x 4..12, z 3.5..6.5. */
    public static final Vec3 TRAY_CENTER = new Vec3(0.5, 10.2 / 16.0, 5 / 16.0);
    public static final double TRAY_HALF_LENGTH = 4 / 16.0;
    /** The reheat rest: an iron bar across the firebox mouth. */
    public static final Vec3 REST_CENTER = new Vec3(0.5, 6 / 16.0, 2 / 16.0);
    /** The bellows handle at the front right. */
    public static final Vec3 BELLOWS_HANDLE = new Vec3(14.5 / 16.0, 7 / 16.0, 1 / 16.0);
    public static final Vec3 BOOK_CENTER = new Vec3(1.5 / 16.0, 14 / 16.0, 5.5 / 16.0);
    public static final Vec3 SUPPLY_CENTER = new Vec3(14.5 / 16.0, 14 / 16.0, 8 / 16.0);
    public static final Vec3 FORGE_FOCUS = new Vec3(0.5, 0.62, 0.3);

    public static ForgePart forgePart(Vec3 local) {
        double x = local.x * 16, y = local.y * 16, z = local.z * 16;
        if (x >= 12.5 && y < 9.5 && z < 9.5) return ForgePart.BELLOWS;
        if (y >= 9.5) {
            if (x < 3) return z < 9 ? ForgePart.BOOK : ForgePart.BODY;
            if (x > 13) return ForgePart.SUPPLY;
            return z < 7.5 ? ForgePart.TRAY : ForgePart.BASIN;
        }
        if (x >= 3.5 && x <= 12.5 && z <= 4) return y >= 4.5 ? ForgePart.REHEAT : ForgePart.FUEL;
        return y >= 8 ? ForgePart.BASIN : ForgePart.FUEL;
    }

    /** Where billet zone {@code zone} sits on the tray at billet position {@code position} (local). */
    public static Vec3 trayZone(int position, int zone) {
        double offset = (Math.floorMod(position, ThermalRules.TRAY_POSITIONS) - 1) * 1.5 / 16.0;
        double x = TRAY_CENTER.x - TRAY_HALF_LENGTH * 0.75 + zone * TRAY_HALF_LENGTH * 0.75 + offset;
        return new Vec3(x, TRAY_CENTER.y, TRAY_CENTER.z);
    }

    // ------------------------------------------------------------------ anvil

    /** Top of the anvil's working face. */
    public static final double ANVIL_FACE = 1.0;
    /** Centre of the working area on the face; the piece's length runs along local -z (toward the horn) from tang to tip. */
    public static final Vec3 ANVIL_WORK_CENTER = new Vec3(0.5, ANVIL_FACE, 0.58);
    /** The fitting tray beside the work: reserved components lie here. */
    public static final Vec3 ANVIL_SUPPLY = new Vec3(0.5, ANVIL_FACE, 0.88);
    public static final Vec3 ANVIL_FOCUS = new Vec3(0.5, 1.02, 0.55);

    /**
     * Converts a point on the anvil's working plane (local) to canonical workpiece coordinates for a piece of the given
     * rendered length and width (blocks). Turning the piece over mirrors {@code v}.
     */
    public static float[] anvilToPiece(Vec3 local, double length, double width, boolean flipped) {
        double along = ANVIL_WORK_CENTER.z - local.z; // toward the horn is +u
        double across = local.x - ANVIL_WORK_CENTER.x;
        float u = (float) (0.5 + along / Math.max(0.05, length));
        float v = (float) (across / Math.max(0.02, width));
        if (flipped) v = -v;
        return new float[]{u, v};
    }

    public static Vec3 pieceToAnvil(float u, float v, double length, double width, boolean flipped, double height) {
        double vv = flipped ? -v : v;
        return new Vec3(ANVIL_WORK_CENTER.x + vv * width, ANVIL_FACE + height, ANVIL_WORK_CENTER.z - (u - 0.5) * length);
    }

    // ------------------------------------------------------------------ trough

    /** The point the piece is lowered toward: the middle of the basin. */
    public static final Vec3 TROUGH_CENTER = new Vec3(0.5, 0.35, 0.5);
    public static final Vec3 TROUGH_FOCUS = new Vec3(0.5, 0.5, 0.5);

    /** Water surface height (local) for a fill fraction; same as the renderer. */
    public static double troughSurface(float fill) {
        return (3.2 + 5.3 * Math.max(0F, Math.min(1F, fill))) / 16.0;
    }

    // ------------------------------------------------------------------ grindstone

    /** Top of the wheel, where an edge meets the stone. */
    public static final Vec3 WHEEL_CONTACT = new Vec3(0.5, 15 / 16.0, 0.5);
    public static final Vec3 GRINDSTONE_FOCUS = new Vec3(0.5, 0.75, 0.5);
    /** The rest beside the wheel where a piece waits. */
    public static final Vec3 GRINDSTONE_REST = new Vec3(0.5, 2.2 / 16.0, 0.32);

    private StationLayout() {}
}
