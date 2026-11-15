package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.network.packet.WorkActionPacket;
import com.otectus.immersivesmithing.util.Feedback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The single entry point for station intents from clients, in both the focused scene and World view. Before any station
 * code runs it checks, in order: a known action; finite numbers; a loaded station of a known kind at that position and the
 * exact station instance the client aimed at (a replaced block is a different station); a living, non-spectating smith
 * within reach of the real player's eyes and in their line of sight; the control lease (one writer per station, permission
 * checked when taken); a strictly growing sequence number (duplicates and replays are dropped); and a per-smith action
 * budget. The station classes then validate tools, piece state and timing themselves.
 */
public final class SmithingActionService {

    public static void handle(ServerPlayer player, WorkActionPacket packet) {
        WorkAction action = WorkAction.byId(packet.action());
        if (action == null || !Float.isFinite(packet.u()) || !Float.isFinite(packet.v())) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = packet.pos();
        if (!level.isLoaded(pos)) return;
        BlockEntity be = level.getBlockEntity(pos);
        WorkLeases.Station kind = stationOf(be);
        if (kind == null) return;
        if (action == WorkAction.RELEASE) {
            WorkLeases.Lease lease = WorkLeases.of(player);
            if (lease != null && lease.station.pos().equals(pos)) WorkLeases.release(player, null);
            return;
        }
        if (instanceOf(be) != packet.instance()) {
            if (action == WorkAction.FOCUS) Feedback.fail(player, "station_changed");
            return;
        }
        if (!player.isAlive() || player.isSpectator()) return;
        if (!WorkLeases.inReach(player, pos) || !WorkLeases.canSee(player, pos)) {
            Feedback.fail(player, "too_far");
            WorkLeases.Lease lease = WorkLeases.of(player);
            if (lease != null && lease.station.pos().equals(pos)) WorkLeases.release(player, Component.translatable("message.immersive_smithing.moved_away"));
            return;
        }
        WorkLeases.Lease lease = WorkLeases.acquire(player, pos, kind, action == WorkAction.FOCUS);
        if (lease == null || !lease.acceptSequence(packet.sequence()) || !WorkLeases.takeToken(lease)) return;
        if (action == WorkAction.FOCUS) return;
        boolean heavy = (packet.flags() & WorkAction.HEAVY) != 0;
        int seq = packet.sequence();
        switch (kind) {
            case FORGE -> forge(player, (SmithsForgeBlockEntity) be, action, packet.target(), seq);
            case ANVIL -> anvil(player, (SmithsAnvilBlockEntity) be, lease, action, packet, heavy, seq);
            case TROUGH -> trough(player, (SmithsTroughBlockEntity) be, action);
            case GRINDSTONE -> grindstone(player, (SmithsGrindstoneBlockEntity) be, action, packet.target());
        }
    }

    private static void forge(ServerPlayer player, SmithsForgeBlockEntity forge, WorkAction action, int target, int seq) {
        switch (action) {
            case PUMP -> ForgeActions.pump(player, forge, seq);
            case TURN -> ForgeActions.turn(player, forge, target, seq);
            case COMMIT -> ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
            case DRAW -> ForgeActions.draw(player, forge, null, seq);
            case CANCEL_JOB -> ForgeActions.cancel(player, forge);
            case REST_PLACE -> ForgeActions.restPlace(player, forge, null);
            case REST_TAKE -> ForgeActions.restTake(player, forge, null);
            case BOOK -> ForgeActions.openBook(player, forge, InteractionHand.MAIN_HAND);
            default -> { }
        }
    }

    private static void anvil(ServerPlayer player, SmithsAnvilBlockEntity anvil, WorkLeases.Lease lease, WorkAction action,
                              WorkActionPacket packet, boolean heavy, int seq) {
        switch (action) {
            case STRIKE -> AnvilActions.strike(player, anvil, lease, packet.target(), packet.u(), packet.v(), heavy, seq);
            case FLIP -> AnvilActions.flip(player, anvil, lease, seq);
            case FIT -> AnvilActions.fit(player, anvil, packet.target(), seq);
            case COLLECT -> AnvilActions.collect(player, anvil);
            case TAKE_PIECE -> AnvilActions.take(player, anvil, null);
            case PLACE_PIECE -> AnvilActions.place(player, anvil, null);
            default -> { }
        }
    }

    private static void trough(ServerPlayer player, SmithsTroughBlockEntity trough, WorkAction action) {
        switch (action) {
            case LOWER -> TroughActions.lower(player, trough, null);
            case LIFT -> TroughActions.lift(player, trough, null);
            default -> { }
        }
    }

    private static void grindstone(ServerPlayer player, SmithsGrindstoneBlockEntity stone, WorkAction action, int target) {
        switch (action) {
            case GRIND_HOLD -> GrindstoneActions.hold(player, stone);
            case GRIND_STOP -> GrindstoneActions.stop(player, stone);
            case GRIND_ASPECT -> GrindstoneActions.aspect(player, stone, target == 1);
            case GRIND_TAKE -> GrindstoneActions.take(player, stone);
            default -> { }
        }
    }

    @Nullable
    public static WorkLeases.Station stationOf(@Nullable BlockEntity be) {
        if (be instanceof SmithsForgeBlockEntity) return WorkLeases.Station.FORGE;
        if (be instanceof SmithsAnvilBlockEntity) return WorkLeases.Station.ANVIL;
        if (be instanceof SmithsTroughBlockEntity) return WorkLeases.Station.TROUGH;
        if (be instanceof SmithsGrindstoneBlockEntity) return WorkLeases.Station.GRINDSTONE;
        return null;
    }

    public static int instanceOf(@Nullable BlockEntity be) {
        if (be instanceof SmithsForgeBlockEntity f) return f.instanceId();
        if (be instanceof SmithsAnvilBlockEntity a) return a.instanceId();
        if (be instanceof SmithsTroughBlockEntity t) return t.instanceId();
        if (be instanceof SmithsGrindstoneBlockEntity g) return g.instanceId();
        return 0;
    }

    private SmithingActionService() {}
}
