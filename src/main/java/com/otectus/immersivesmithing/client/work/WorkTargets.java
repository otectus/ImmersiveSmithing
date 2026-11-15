package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingHammerItem;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parts of a station a smith can act on right now, for the pointer and for keyboard selection alike. Each target has
 * a world point (for prompts, effects and the scene tool) and, on the anvil, the canonical point on the piece. Picking uses
 * the shared station layout and the piece's current shape, the same geometry the renderers draw; where inside an
 * operation's area a blow lands never changes its rules, so pointer and keyboard play identically.
 */
public final class WorkTargets {
    public enum Kind { BELLOWS, BILLET, DRAW, COMMIT, REST, BOOK, OPERATION, SOCKET, OUTPUT, WATER, WHEEL, TAKE, PIECE, PLACE }

    public record Target(Kind kind, int index, float u, float v, Vec3 world, Component label) {
        public boolean same(@Nullable Target other) {
            return other != null && other.kind == kind && other.index == index;
        }
    }

    private static final AABB BELLOWS = box(12.8, 2, 0, 16, 8.5, 9);
    private static final AABB TRAY = box(3.5, 9.8, 3, 12.5, 12, 7);
    private static final AABB DRAW_POINT = box(3, 10, 0, 13, 14.2, 3);
    private static final AABB REST = box(3.5, 3.5, 0, 12.5, 7.5, 3.5);
    private static final AABB BOOK = box(0, 13, 3, 3, 15.5, 9);
    private static final AABB WATER = box(1.5, 2.5, 3.5, 14.5, 10, 12.5);
    private static final AABB WHEEL = box(4.5, 3, 2.5, 11.5, 15.5, 13.5);
    private static final AABB STONE_REST = box(3.5, 1.5, 2.5, 12.5, 5, 7);

    private static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return new AABB(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
    }

    public static Direction facing(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    private static AABB world(BlockPos pos, Direction facing, AABB local) {
        Vec3 a = StationLayout.toWorld(pos, facing, local.minX, local.minY, local.minZ);
        Vec3 b = StationLayout.toWorld(pos, facing, local.maxX, local.maxY, local.maxZ);
        return new AABB(a, b);
    }

    private static Target target(Kind kind, int index, BlockPos pos, Direction facing, AABB local, String key) {
        return new Target(kind, index, 0F, 0F, StationLayout.toWorld(pos, facing, local.getCenter()),
                Component.translatable("target.immersive_smithing." + key));
    }

    /** Every target in a sensible keyboard order (the most likely next action first). */
    public static List<Target> list(Level level, BlockPos pos, WorkLeases.Station kind) {
        List<Target> out = new ArrayList<>();
        Direction facing = facing(level, pos);
        BlockEntity be = level.getBlockEntity(pos);
        switch (kind) {
            case FORGE -> {
                if (!(be instanceof SmithsForgeBlockEntity forge)) break;
                if (forge.job() != null) {
                    out.add(target(Kind.BILLET, 0, pos, facing, TRAY, "billet"));
                    out.add(target(Kind.DRAW, 0, pos, facing, DRAW_POINT, "draw"));
                } else if (forge.plan() != null) {
                    out.add(target(Kind.COMMIT, 0, pos, facing, TRAY, "commit"));
                }
                out.add(target(Kind.BELLOWS, 0, pos, facing, BELLOWS, "bellows"));
                out.add(target(Kind.REST, 0, pos, facing, REST, "rest"));
                out.add(target(Kind.BOOK, 0, pos, facing, BOOK, "book"));
            }
            case ANVIL -> {
                if (!(be instanceof SmithsAnvilBlockEntity anvil)) break;
                if (anvil.hasOutput()) {
                    out.add(new Target(Kind.OUTPUT, 0, 0F, 0F, StationLayout.toWorld(pos, facing, StationLayout.ANVIL_WORK_CENTER.add(0, 0.06, 0)),
                            Component.translatable("target.immersive_smithing.output")));
                    break;
                }
                WorkpieceData piece = anvil.workpiece();
                if (piece == null) {
                    // Set a carried piece down without leaving the view.
                    if (!anvil.hasUnreadablePiece() && smithHasTongs(true)) out.add(pieceTarget(Kind.PLACE, pos, facing, "place_piece"));
                    break;
                }
                ProcessState p = piece.process();
                WorkpieceShape shape = WorkpieceShape.of(piece);
                if (p.stage.shapeable()) {
                    List<ProcessProfile.Operation> ops = p.profile.operations();
                    int next = ShapingRules.nextOperation(p);
                    List<Integer> order = new ArrayList<>();
                    if (next >= 0 && ops.get(next).face().matches(p.flipped)) order.add(next);
                    for (int i = 0; i < ops.size(); i++) if (!order.contains(i) && ops.get(i).face().matches(p.flipped)) order.add(i);
                    for (int i : order) {
                        ProcessProfile.Operation o = ops.get(i);
                        float u = o.centerU(), v = o.centerV();
                        Vec3 local = pieceToAnvil(shape.point(u, WorkpieceShape.visibleV(v, p.flipped)));
                        out.add(new Target(Kind.OPERATION, i, u, v, StationLayout.toWorld(pos, facing, local), AnvilActions.operationName(o)));
                    }
                } else if (p.stage.quenched()) {
                    for (int i = 0; i < p.sockets.size(); i++) {
                        ProcessState.Socket s = p.sockets.get(i);
                        if (s.attached()) continue;
                        float[] uv = shape.visual.socket(s.name(), i);
                        Vec3 local = pieceToAnvil(shape.point(uv[0], WorkpieceShape.visibleV(uv[1], p.flipped)));
                        out.add(new Target(Kind.SOCKET, i, uv[0], uv[1], StationLayout.toWorld(pos, facing, local),
                                Component.translatableWithFallback("socket.immersive_smithing." + s.name(), s.name())));
                    }
                }
                // Lifting the piece off (to reheat it, or to quench it) with empty tongs, after the work itself.
                if (smithHasTongs(false)) out.add(pieceTarget(Kind.PIECE, pos, facing, "take_piece"));
            }
            case TROUGH -> {
                if (be instanceof SmithsTroughBlockEntity) out.add(target(Kind.WATER, 0, pos, facing, WATER, "water"));
            }
            case GRINDSTONE -> {
                if (be instanceof SmithsGrindstoneBlockEntity stone && !stone.piece().isEmpty()) {
                    out.add(target(Kind.WHEEL, 0, pos, facing, WHEEL, "wheel"));
                    out.add(target(Kind.TAKE, 0, pos, facing, STONE_REST, "take"));
                }
            }
        }
        return out;
    }

    /** The target under a ray from the camera, nearest first. */
    @Nullable
    public static Target pick(Level level, BlockPos pos, WorkLeases.Station kind, Vec3 from, Vec3 dir) {
        Vec3 to = from.add(dir.scale(8));
        Direction facing = facing(level, pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (kind == WorkLeases.Station.ANVIL && be instanceof SmithsAnvilBlockEntity anvil) return pickAnvil(level, pos, facing, anvil, from, dir);
        Target best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Target t : list(level, pos, kind)) {
            AABB local = switch (t.kind) {
                case BELLOWS -> BELLOWS;
                case BILLET, COMMIT -> TRAY;
                case DRAW -> DRAW_POINT;
                case REST -> REST;
                case BOOK -> BOOK;
                case WATER -> WATER;
                case WHEEL -> WHEEL;
                case TAKE -> STONE_REST;
                default -> null;
            };
            if (local == null) continue;
            Optional<Vec3> hit = world(pos, facing, local).clip(from, to);
            if (hit.isPresent() && hit.get().distanceToSqr(from) < bestDistance) {
                bestDistance = hit.get().distanceToSqr(from);
                best = t;
            }
        }
        return best;
    }

    @Nullable
    private static Target pickAnvil(Level level, BlockPos pos, Direction facing, SmithsAnvilBlockEntity anvil, Vec3 from, Vec3 dir) {
        List<Target> targets = list(level, pos, WorkLeases.Station.ANVIL);
        if (targets.isEmpty()) return null;
        if (targets.get(0).kind == Kind.OUTPUT || targets.get(0).kind == Kind.PLACE) {
            AABB box = new AABB(targets.get(0).world, targets.get(0).world).inflate(0.3, 0.12, 0.3);
            return box.clip(from, from.add(dir.scale(8))).isPresent() ? targets.get(0) : null;
        }
        WorkpieceData piece = anvil.workpiece();
        if (piece == null) return null;
        Target lift = targets.stream().filter(t -> t.kind == Kind.PIECE).findFirst().orElse(null);
        if (lift != null && !smithHasHammer()) {
            // Without a hammer nothing on the piece can be struck: pointing at it means picking it up.
            AABB box = new AABB(lift.world, lift.world).inflate(0.35, 0.12, 0.35);
            if (box.clip(from, from.add(dir.scale(8))).isPresent()) return lift;
        }
        ProcessState p = piece.process();
        WorkpieceShape shape = WorkpieceShape.of(piece);
        if (p.stage.quenched()) {
            Target best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Target t : targets) {
                Optional<Vec3> hit = new AABB(t.world, t.world).inflate(0.06).clip(from, from.add(dir.scale(8)));
                if (hit.isPresent() && hit.get().distanceToSqr(from) < bestDistance) {
                    bestDistance = hit.get().distanceToSqr(from);
                    best = t;
                }
            }
            return best;
        }
        // Intersect the ray with the piece's working plane, then ask the shape where on the piece that is.
        Vec3 localFrom = StationLayout.toLocal(pos, facing, from);
        Vec3 localDir = StationLayout.toLocal(pos, facing, from.add(dir)).subtract(localFrom);
        double planeY = StationLayout.ANVIL_FACE + shape.maxThickness();
        if (Math.abs(localDir.y) < 1e-5) return null;
        double t = (planeY - localFrom.y) / localDir.y;
        if (t <= 0) return null;
        Vec3 hit = localFrom.add(localDir.scale(t));
        double px = StationLayout.ANVIL_WORK_CENTER.z - hit.z;
        double pz = hit.x - StationLayout.ANVIL_WORK_CENTER.x;
        float[] uv = shape.toCanonical(px, pz);
        if (uv == null) return null;
        float v = WorkpieceShape.visibleV(uv[1], p.flipped);
        int op = ShapingRules.operationAt(p, uv[0], v);
        if (op < 0) return null;
        ProcessProfile.Operation o = p.profile.operations().get(op);
        return new Target(Kind.OPERATION, op, uv[0], v, StationLayout.toWorld(pos, facing, pieceToAnvil(shape.point(uv[0], uv[1]))),
                AnvilActions.operationName(o));
    }

    private static Target pieceTarget(Kind kind, BlockPos pos, Direction facing, String key) {
        return new Target(kind, 0, 0F, 0F, StationLayout.toWorld(pos, facing, StationLayout.ANVIL_WORK_CENTER.add(0, 0.06, 0)),
                Component.translatable("target.immersive_smithing." + key));
    }

    /** Whether the local smith holds tongs, loaded or empty as asked, in either hand. */
    private static boolean smithHasTongs(boolean loaded) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SmithingTongsItem && WorkpieceCodec.isHolding(stack) == loaded) return true;
        }
        return false;
    }

    private static boolean smithHasHammer() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (player.getMainHandItem().getItem() instanceof SmithingHammerItem
                || player.getOffhandItem().getItem() instanceof SmithingHammerItem);
    }

    /** A point in the piece frame to anvil-local coordinates (the piece's length runs toward the horn). */
    public static Vec3 pieceToAnvil(Vec3 piece) {
        return new Vec3(StationLayout.ANVIL_WORK_CENTER.x + piece.z, StationLayout.ANVIL_FACE + piece.y, StationLayout.ANVIL_WORK_CENTER.z - piece.x);
    }

    private WorkTargets() {}
}
