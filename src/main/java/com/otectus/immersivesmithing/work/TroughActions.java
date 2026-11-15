package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.SigningService;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Trough work: lowering a shaped piece, and lifting it once the hiss has subsided. A piece with components to fit comes
 * out cooled and goes to the anvil; a piece without them (or one forged before 1.3.0) is completed as it is lifted.
 */
public final class TroughActions {

    /** Right-click (main hand) on the trough. */
    public static void use(ServerPlayer player, SmithsTroughBlockEntity trough, InteractionHand hand, ItemStack held) {
        if (held.is(Items.WATER_BUCKET)) {
            if (!trough.canAcceptBucket()) {
                Feedback.fail(player, "trough_full");
                return;
            }
            trough.addBucket();
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
            player.level().playSound(null, trough.getBlockPos(), ModSounds.TROUGH_FILL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return;
        }
        if (held.is(Items.BUCKET)) {
            if (trough.takeBucket()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.WATER_BUCKET)));
                player.level().playSound(null, trough.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                Feedback.fail(player, "trough_not_enough_water");
            }
            return;
        }
        if (held.getItem() instanceof SmithingTongsItem) {
            if (WorkpieceCodec.isHolding(held)) lower(player, trough, hand);
            else if (trough.occupied()) lift(player, trough, hand);
            else Feedback.fail(player, "nothing_to_quench");
            return;
        }
        if (held.is(ModItems.HOT_WORKPIECE.get())) {
            Feedback.fail(player, "use_tongs_to_quench");
            return;
        }
        if (held.isEmpty()) {
            WorkpieceData piece = trough.quenchPiece();
            if (piece != null && trough.immersed()) {
                int progress = QuenchRules.progress(piece.process(), trough.quenchDuration());
                Feedback.info(player, progress >= 1000 ? "quench_ready_to_lift" : "quench_in_progress", Math.min(100, progress / 10));
            } else {
                Feedback.info(player, "trough_status", trough.quenchesLeft());
            }
            return;
        }
        Feedback.fail(player, "trough_only_workpieces");
    }

    /** Lowers the piece in the tongs: the trough takes it and reserves the water; immersion begins after the lowering. */
    public static void lower(ServerPlayer player, SmithsTroughBlockEntity trough, @Nullable InteractionHand preferred) {
        InteractionHand hand = preferred != null && WorkpieceCodec.isHolding(player.getItemInHand(preferred)) ? preferred : ForgeActions.findTongs(player, true);
        if (hand == null) {
            Feedback.fail(player, "nothing_to_quench");
            return;
        }
        ItemStack tongs = player.getItemInHand(hand);
        WorkpieceData piece = WorkpieceCodec.getHeld(tongs).orElse(null);
        if (piece == null) {
            Feedback.fail(player, "workpiece_unreadable");
            return;
        }
        ProcessState p = piece.process();
        if (p.stage.quenched()) {
            Feedback.fail(player, p.needsFitting() ? "already_quenched" : "workpiece_to_trough");
            return;
        }
        if (p.stage != WorkStage.QUENCHING && !p.allComplete()) {
            Feedback.fail(player, "hammer_first");
            return;
        }
        if (p.stage != WorkStage.QUENCHING && !p.thermal.workable(ThermalRules.mean(p.heat))) {
            Feedback.fail(player, "reheat_before_quench");
            return;
        }
        if (trough.occupied()) {
            Feedback.fail(player, "trough_busy");
            return;
        }
        if (WorkLeases.acquire(player, trough.getBlockPos(), WorkLeases.Station.TROUGH, false) == null) return;
        if (!trough.lower(piece, player.getUUID())) {
            Feedback.fail(player, "trough_empty");
            return;
        }
        WorkpieceCodec.clearHeld(tongs);
        ServerLevel level = player.serverLevel();
        WorkEvents.send(level, trough.getBlockPos(), WorkEvents.LOWER, player, 0, StationLayout.TROUGH_CENTER, 1F, 0);
        Feedback.info(player, "lowering");
    }

    /**
     * Lifts the piece out with empty tongs. Before the immersion boundary it comes out as it went in (water released);
     * a quench lifted before the hiss subsides keeps its progress and can be lowered again; a finished quench finalizes the
     * forge score once and either completes the piece or leaves it cooled for fitting.
     */
    public static void lift(ServerPlayer player, SmithsTroughBlockEntity trough, @Nullable InteractionHand preferred) {
        InteractionHand hand = preferred != null && ForgeActions.isEmptyTongs(player.getItemInHand(preferred)) ? preferred : ForgeActions.findTongs(player, false);
        if (hand == null) {
            Feedback.fail(player, "empty_tongs_needed");
            return;
        }
        if (trough.quenchPiece() == null) {
            Feedback.fail(player, trough.occupied() ? "workpiece_unreadable" : "nothing_to_quench");
            return;
        }
        if (WorkLeases.acquire(player, trough.getBlockPos(), WorkLeases.Station.TROUGH, false) == null) return;
        int duration = trough.quenchDuration();
        boolean wasImmersed = trough.immersed();
        WorkpieceData piece = trough.lift();
        if (piece == null) return;
        ItemStack tongs = player.getItemInHand(hand);
        ServerLevel level = player.serverLevel();
        ProcessState p = piece.process();
        if (!wasImmersed || !QuenchRules.done(p, duration)) {
            WorkpieceCodec.setHeld(tongs, piece);
            WorkEvents.send(level, trough.getBlockPos(), WorkEvents.LIFT, player, 0, StationLayout.TROUGH_CENTER, 0F, 0);
            Feedback.info(player, wasImmersed ? "lifted_early" : "lowering_cancelled");
            return;
        }
        ProcessState next = p.copy();
        next.quenchControl = QuenchRules.control(next, duration);
        int forge = WorkQuality.forgeScore(next, piece.forgeScore(), ServerConfig.get(ServerConfig.WORK_HEAT_WEIGHT));
        int anvil = piece.anvilScore() >= 0 ? piece.anvilScore() : WorkQuality.anvilScore(next, piece.anvilScore());
        boolean faulty = piece.faulty() || next.burnt;
        next.stage = WorkStage.COOLED;
        next.revision++;
        WorkpieceData cooled = piece.withScores(forge, anvil, faulty, next);
        ModAdvancements.award(player, ModAdvancements.HISS);
        level.playSound(null, trough.getBlockPos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.4F);
        if (next.needsFitting()) {
            WorkpieceCodec.setHeld(tongs, cooled);
            WorkEvents.send(level, trough.getBlockPos(), WorkEvents.LIFT, player, 0, StationLayout.TROUGH_CENTER, 1F, 0);
            Feedback.info(player, "quenched_fit_next", cooled.target().getHoverName());
            return;
        }
        ItemStack result = SmithingCompletionService.complete(player, cooled, trough.getBlockPos(), ItemSmithedEvent.Station.TROUGH);
        ItemStack finished = result.copy();
        if (!player.getInventory().add(result) && !result.isEmpty()) {
            Containers.dropItemStack(player.level(), trough.getBlockPos().getX() + 0.5, trough.getBlockPos().getY() + 1.0,
                    trough.getBlockPos().getZ() + 0.5, result);
        }
        // One wear per completed cycle. The tongs are empty now, so wear can safely break them.
        InteractionHand used = hand;
        tongs.hurtAndBreak(1, player, pl -> pl.broadcastBreakEvent(used));
        QualityData quality = QualityData.get(finished).orElse(null);
        WorkEvents.send(level, trough.getBlockPos(), WorkEvents.COMPLETE, player, 0, StationLayout.TROUGH_CENTER, 1F,
                quality != null ? quality.quality().ordinal() : 0);
        Feedback.info(player, "quenched", quality != null ? quality.quality().displayName() : Component.empty());
        if (ServerConfig.get(ServerConfig.OPEN_SIGNING_AFTER_QUENCH)) {
            int slot = player.getInventory().findSlotMatchingItem(finished);
            if (slot >= 0) SigningService.open(player, slot, null);
        } else if (SigningService.canSign(finished, player)) {
            Feedback.info(player, "engrave_later");
        }
    }

    private TroughActions() {}
}
