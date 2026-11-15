package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.api.SmithingStation;
import com.otectus.immersivesmithing.api.event.ItemSmithedEvent;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.item.SmithingHammerItem;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.SigningService;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.util.InventoryRoom;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Anvil work: placing and taking pieces, aimed blows, turning the piece over, fitting reserved components and collecting
 * the finished item. One writer at a time ({@link WorkLeases}); the piece itself lives in the anvil's block entity.
 */
public final class AnvilActions {
    private static final int FLIP_TICKS = 6;
    private static final float TOLERANCE = 0.05F;

    /** Right-click (main hand) in World view, or with an item that has no focused use. */
    public static void use(ServerPlayer player, SmithsAnvilBlockEntity anvil, InteractionHand hand, ItemStack held) {
        if (player.isSecondaryUseActive() && SigningService.canSign(held, player)) {
            SigningService.open(player, player.getInventory().selected, anvil.getBlockPos());
            return;
        }
        if (held.getItem() instanceof SmithingTongsItem) {
            if (WorkpieceCodec.isHolding(held)) place(player, anvil, hand);
            else if (anvil.hasOutput()) collect(player, anvil);
            else take(player, anvil, hand);
            return;
        }
        if (held.is(ModItems.HOT_WORKPIECE.get())) {
            place(player, anvil, hand);
            return;
        }
        WorkpieceData piece = anvil.workpiece();
        if (held.getItem() instanceof SmithingHammerItem && piece != null && piece.stage().shapeable()) {
            WorkLeases.Lease lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
            if (lease != null) flip(player, anvil, lease, 0);
            return;
        }
        if (held.isEmpty()) {
            if (anvil.hasOutput()) {
                collect(player, anvil);
                return;
            }
            if (piece != null && piece.process().needsFitting() && piece.stage().quenched()) {
                WorkLeases.Lease lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
                if (lease != null) fit(player, anvil, -1, 0);
                return;
            }
        }
        describe(player, anvil);
    }

    public static void describe(ServerPlayer player, SmithsAnvilBlockEntity anvil) {
        if (anvil.hasOutput()) {
            Feedback.info(player, "anvil_output", anvil.output().getHoverName());
            return;
        }
        if (anvil.hasUnreadablePiece()) {
            Feedback.fail(player, "workpiece_unreadable");
            return;
        }
        WorkpieceData piece = anvil.workpiece();
        if (piece == null) {
            Feedback.info(player, "anvil_empty");
            return;
        }
        ProcessState p = piece.process();
        switch (p.stage) {
            case BILLET, SHAPING -> {
                int next = ShapingRules.nextOperation(p);
                ThermalProfile.HeatBand band = p.thermal.band(ThermalRules.mean(anvil.currentHeat()));
                Feedback.info(player, "anvil_shaping", Math.round(p.progress() / 10F),
                        Component.translatable("heat.immersive_smithing." + band.key()),
                        next >= 0 ? operationName(p.profile.operations().get(next)) : Component.empty());
            }
            case SHAPED -> Feedback.info(player, "workpiece_to_trough");
            case QUENCHING -> Feedback.info(player, "finish_quench_first");
            case COOLED, FITTING -> Feedback.info(player, p.needsFitting() ? "fit_components" : "workpiece_to_trough");
        }
    }

    /** Puts the piece in the tongs (or a dropped Hot Workpiece) on the anvil. */
    public static void place(ServerPlayer player, SmithsAnvilBlockEntity anvil, @Nullable InteractionHand preferred) {
        if (anvil.isOccupied()) {
            Feedback.fail(player, "anvil_occupied");
            return;
        }
        InteractionHand hand = preferred;
        if (hand == null || !(WorkpieceCodec.isHolding(player.getItemInHand(hand)))) hand = ForgeActions.findTongs(player, true);
        if (hand == null) {
            Feedback.fail(player, "nothing_to_place");
            return;
        }
        if (WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false) == null) return;
        ItemStack holder = player.getItemInHand(hand);
        Optional<WorkpieceData> piece = WorkpieceCodec.getHeld(holder);
        if (piece.isEmpty()) {
            Feedback.fail(player, "workpiece_unreadable");
            return;
        }
        anvil.setWorkpiece(piece.get());
        if (holder.is(ModItems.HOT_WORKPIECE.get())) holder.shrink(1);
        else WorkpieceCodec.clearHeld(holder);
        ServerLevel level = player.serverLevel();
        level.playSound(null, anvil.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.PLACE, player, 0, StationLayout.ANVIL_WORK_CENTER, 1F, 0);
        WorkStage stage = piece.get().stage();
        if (stage.shapeable() && !piece.get().process().allComplete()) {
            boolean cold = !piece.get().process().thermal.workable(ThermalRules.coolest(anvil.currentHeat()));
            Feedback.info(player, cold ? "too_cold_reheat" : "use_hammer");
        } else if (stage == WorkStage.SHAPED) {
            Feedback.info(player, "workpiece_to_trough");
        } else if (stage.quenched()) {
            Feedback.info(player, piece.get().process().needsFitting() ? "fit_components" : "workpiece_to_trough");
        } else {
            Feedback.info(player, "finish_quench_first");
        }
    }

    /** Takes the piece off the anvil with empty tongs. */
    public static void take(ServerPlayer player, SmithsAnvilBlockEntity anvil, @Nullable InteractionHand preferred) {
        InteractionHand hand = preferred != null && ForgeActions.isEmptyTongs(player.getItemInHand(preferred)) ? preferred : ForgeActions.findTongs(player, false);
        if (hand == null) {
            Feedback.fail(player, "empty_tongs_needed");
            return;
        }
        if (anvil.workpiece() == null) {
            Feedback.fail(player, anvil.hasUnreadablePiece() ? "workpiece_unreadable" : "anvil_empty");
            return;
        }
        if (WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false) == null) return;
        WorkpieceData piece = anvil.takeWorkpiece();
        if (piece == null) return;
        WorkpieceCodec.setHeld(player.getItemInHand(hand), piece);
        ServerLevel level = player.serverLevel();
        level.playSound(null, anvil.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
        WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.TAKE, player, 0, StationLayout.ANVIL_WORK_CENTER, 1F, 0);
    }

    /** Ticks between accepted blows for this hammer and smith; progression assistance shortens it (bounded by config). */
    public static int recoveryTicks(ServerPlayer player, SmithingTier tier, boolean heavy) {
        int base = switch (tier) {
            case STONE -> ServerConfig.get(ServerConfig.STONE_BLOW_RECOVERY_TICKS);
            case IRON -> ServerConfig.get(ServerConfig.IRON_BLOW_RECOVERY_TICKS);
            case DIAMOND -> ServerConfig.get(ServerConfig.DIAMOND_BLOW_RECOVERY_TICKS);
            case NETHERITE -> ServerConfig.get(ServerConfig.NETHERITE_BLOW_RECOVERY_TICKS);
        };
        if (heavy) base = base * 3 / 2;
        double assist = ImmersiveSmithingAPI.assistedTimeMs(player, SmithingStation.ANVIL, 1000) / 1000.0;
        return Math.max(4, (int) Math.round(base / Math.max(1.0, assist)));
    }

    /**
     * One blow. The point must be finite and on the piece; the operation must take blows on the current face, have its
     * prerequisites done and be at a workable heat sampled at the accepted contact. Blows faster than the hammer's recovery
     * are dropped (not queued, not extra progress). The material change, sound and sparks go out as one event.
     */
    public static void strike(ServerPlayer player, SmithsAnvilBlockEntity anvil, WorkLeases.Lease lease, int target, float u, float v,
                              boolean heavy, int sequence) {
        if (!Float.isFinite(u) || !Float.isFinite(v) || u < -TOLERANCE || u > 1F + TOLERANCE || v < -0.5F - TOLERANCE || v > 0.5F + TOLERANCE) return;
        InteractionHand hand = findHammer(player);
        if (hand == null) {
            Feedback.fail(player, "hammer_needed");
            return;
        }
        WorkpieceData piece = anvil.workpiece();
        if (piece == null) {
            Feedback.fail(player, anvil.hasUnreadablePiece() ? "workpiece_unreadable" : "anvil_empty");
            return;
        }
        ProcessState current = piece.process();
        if (!current.stage.shapeable()) {
            Feedback.fail(player, current.stage.quenched() ? "fit_components" : "finish_quench_first");
            return;
        }
        SmithingTier tier = ((SmithingHammerItem) player.getItemInHand(hand).getItem()).tier();
        if (anvil.clock() - lease.lastBlow < recoveryTicks(player, tier, heavy)) return;
        List<ProcessProfile.Operation> ops = current.profile.operations();
        if (target >= ops.size()) return;
        int op = target;
        if (op >= 0) {
            ProcessProfile.Operation o = ops.get(op);
            boolean inside = u >= o.u0() - TOLERANCE && u <= o.u1() + TOLERANCE && v >= o.v0() - TOLERANCE && v <= o.v1() + TOLERANCE;
            if (!inside) op = ShapingRules.operationAt(current, u, v);
        } else {
            op = ShapingRules.operationAt(current, u, v);
        }
        lease.lastBlow = anvil.clock(); // a swing that lands nowhere still takes its recovery time
        ServerLevel level = player.serverLevel();
        if (op < 0) {
            WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.REFUSED, player, sequence, u, v, current.flipped ? 1 : 0, 0F,
                    ShapingRules.Outcome.NO_OPERATION.ordinal());
            hint(player, current);
            return;
        }
        ProcessProfile.Operation o = ops.get(op);
        int heat = anvil.currentHeat()[o.zone()];
        ProcessState next = current.copy();
        ShapingRules.StrikeResult result = ShapingRules.strike(next, op, heavy ? ShapingRules.Strength.HEAVY : ShapingRules.Strength.NORMAL, heat);
        int data = result.outcome().ordinal() | (op << 4) | (heavy ? 1 << 12 : 0);
        if (!result.outcome().changed()) {
            WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.REFUSED, player, sequence, u, v, current.flipped ? 1 : 0, 0F, data);
            switch (result.outcome()) {
                case TOO_COLD -> Feedback.fail(player, "too_cold_reheat");
                case WRONG_FACE -> Feedback.fail(player, "flip_to_reach", operationName(o));
                case NEEDS_PREREQUISITE -> {
                    int first = firstMissingPrerequisite(current, op);
                    Feedback.fail(player, "work_first", first >= 0 ? operationName(ops.get(first)) : Component.empty(), operationName(o));
                }
                default -> hint(player, current);
            }
            return;
        }
        anvil.replaceWorkpiece(piece.withProcess(next));
        WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.STRIKE, player, sequence, u, v, current.flipped ? 1 : 0,
                result.quality() / 1000F, data);
        if (result.finishedAll() && current.stage != WorkStage.SHAPED) {
            ItemStack hammer = player.getItemInHand(hand);
            InteractionHand used = hand;
            hammer.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(used));
            ModAdvancements.award(player, ModAdvancements.HAMMER_AND_STEEL);
            WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.SHAPED, player, sequence, StationLayout.ANVIL_WORK_CENTER, 1F, 0);
            Feedback.info(player, "shaped_quench_next", piece.target().getHoverName());
        } else if (result.outcome() == ShapingRules.Outcome.OVERWORKED) {
            Feedback.fail(player, "overworked", operationName(o));
        } else if (result.finishedOperation()) {
            int following = ShapingRules.nextOperation(next);
            if (following >= 0) {
                ProcessProfile.Operation n = ops.get(following);
                Feedback.info(player, n.face().matches(next.flipped) ? "operation_done" : "operation_done_flip", operationName(o), operationName(n));
            }
        }
    }

    private static void hint(ServerPlayer player, ProcessState p) {
        int next = ShapingRules.nextOperation(p);
        if (next < 0) Feedback.info(player, "workpiece_to_trough");
        else Feedback.info(player, ShapingRules.needsFlip(p) ? "work_here_flip" : "work_here", operationName(p.profile.operations().get(next)));
    }

    private static int firstMissingPrerequisite(ProcessState p, int op) {
        for (String dep : p.profile.operations().get(op).after()) {
            int index = p.profile.operationIndex(dep);
            if (index >= 0 && !p.complete(index)) return index;
        }
        return -1;
    }

    public static Component operationName(ProcessProfile.Operation o) {
        String pretty = o.region().replace('_', ' ');
        return Component.translatableWithFallback("operation.immersive_smithing." + o.region(), pretty);
    }

    /** Turns the piece over. Needs a hammer or tongs in a hand; does not change any progress. */
    public static void flip(ServerPlayer player, SmithsAnvilBlockEntity anvil, WorkLeases.Lease lease, int sequence) {
        WorkpieceData piece = anvil.workpiece();
        if (piece == null || !piece.stage().shapeable()) {
            Feedback.fail(player, piece == null ? "anvil_empty" : "nothing_to_flip");
            return;
        }
        if (findHammer(player) == null && ForgeActions.findTongs(player, false) == null) {
            Feedback.fail(player, "hammer_needed");
            return;
        }
        if (anvil.clock() - lease.lastBlow < FLIP_TICKS) return;
        lease.lastBlow = anvil.clock();
        ProcessState next = piece.process().copy();
        next.flipped = !next.flipped;
        next.revision++;
        anvil.replaceWorkpiece(piece.withProcess(next));
        WorkEvents.send(player.serverLevel(), anvil.getBlockPos(), WorkEvents.FLIP, player, sequence, StationLayout.ANVIL_WORK_CENTER, 1F, next.flipped ? 1 : 0);
    }

    /**
     * Fits one reserved component (a socket) to a quenched piece. The component is committed and any container it leaves
     * (a bucket) is handed back. Fitting the last one completes the piece: it appears on the anvil, ready to collect.
     */
    public static void fit(ServerPlayer player, SmithsAnvilBlockEntity anvil, int socket, int sequence) {
        WorkpieceData piece = anvil.workpiece();
        if (piece == null) {
            Feedback.fail(player, anvil.hasOutput() ? "anvil_output_waiting" : "anvil_empty");
            return;
        }
        ProcessState current = piece.process();
        if (!current.stage.quenched()) {
            Feedback.fail(player, current.stage.shapeable() ? "workpiece_to_trough" : "finish_quench_first");
            return;
        }
        int index = socket;
        if (index < 0 || index >= current.sockets.size() || current.sockets.get(index).attached()) {
            index = -1;
            for (int i = 0; i < current.sockets.size(); i++) if (!current.sockets.get(i).attached()) {
                index = i;
                break;
            }
        }
        ProcessState next = current.copy();
        ServerLevel level = player.serverLevel();
        if (index >= 0 && !next.has(ProcessState.COMPONENTS_PAID)) {
            ProcessState.Socket s = next.sockets.get(index);
            next.sockets.set(index, new ProcessState.Socket(s.name(), s.stacks(), true));
            next.stage = WorkStage.FITTING;
            next.revision++;
            for (ItemStack stack : s.stacks()) {
                ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
                if (!remainder.isEmpty()) {
                    remainder.setCount(Math.min(remainder.getMaxStackSize(), stack.getCount()));
                    ItemHandlerHelper.giveItemToPlayer(player, remainder);
                }
            }
            level.playSound(null, anvil.getBlockPos(), ModSounds.FIT.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
            WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.FIT, player, sequence, StationLayout.ANVIL_WORK_CENTER, 1F, index);
        }
        if (next.needsFitting()) {
            anvil.replaceWorkpiece(piece.withProcess(next));
            Feedback.info(player, "fitted", Component.translatableWithFallback("socket.immersive_smithing." + next.sockets.get(index).name(),
                    next.sockets.get(index).name()), next.socketsLeft());
            return;
        }
        anvil.takeWorkpiece();
        ItemStack result = SmithingCompletionService.complete(player, piece.withProcess(next), anvil.getBlockPos(), ItemSmithedEvent.Station.ANVIL);
        anvil.setOutput(result, next.profile.visual(), next.flipped);
        QualityData quality = QualityData.get(result).orElse(null);
        int grade = quality != null ? quality.quality().ordinal() : 0;
        level.playSound(null, anvil.getBlockPos(), ModSounds.ANVIL_FINISH.get(), SoundSource.BLOCKS, 0.8F, 1.1F);
        WorkEvents.send(level, anvil.getBlockPos(), WorkEvents.COMPLETE, player, sequence, StationLayout.ANVIL_WORK_CENTER, 1F, grade);
        Feedback.info(player, "finished_collect", result.getHoverName(),
                quality != null ? quality.quality().displayName() : Component.empty());
    }

    /** Takes the finished piece into the inventory. It stays on the anvil if there is no room. */
    public static void collect(ServerPlayer player, SmithsAnvilBlockEntity anvil) {
        if (!anvil.hasOutput()) {
            Feedback.fail(player, "anvil_empty");
            return;
        }
        ItemStack stack = anvil.output().copy();
        if (InventoryRoom.room(player.getInventory(), stack) < stack.getCount()) {
            Feedback.fail(player, "inventory_full_collect");
            return;
        }
        anvil.takeOutput();
        ItemStack finished = stack.copy();
        player.getInventory().add(stack);
        WorkEvents.send(player.serverLevel(), anvil.getBlockPos(), WorkEvents.COLLECT, player, 0, StationLayout.ANVIL_WORK_CENTER, 1F, 0);
        if (ServerConfig.get(ServerConfig.OPEN_SIGNING_AFTER_QUENCH)) {
            int slot = player.getInventory().findSlotMatchingItem(finished);
            if (slot >= 0) SigningService.open(player, slot, anvil.getBlockPos());
        } else if (SigningService.canSign(finished, player)) {
            Feedback.info(player, "engrave_later");
        }
    }

    @Nullable
    static InteractionHand findHammer(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).getItem() instanceof SmithingHammerItem) return hand;
        }
        return null;
    }

    /** Socket names for display. */
    public static List<String> socketNames(ProcessState p) {
        List<String> out = new ArrayList<>();
        for (ProcessState.Socket s : p.sockets) out.add(s.name());
        return out;
    }

    private AnvilActions() {}
}
