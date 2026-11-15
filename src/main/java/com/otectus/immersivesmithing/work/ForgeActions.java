package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.item.SmithingToolItem;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.recipe.AuxInventory;
import com.otectus.immersivesmithing.recipe.ForgeRecipeSelection;
import com.otectus.immersivesmithing.recipe.ResultAssembler;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Forge work: pinning a plan, committing a job, the bellows, turning and drawing the billet, cancelling, the reheat rest
 * and deliberate recycling. Every method validates the station and the smith's hands itself and is the same code path for
 * the focused scene, World view and block use.
 */
public final class ForgeActions {
    private static final long CONFIRM_TICKS = 60;

    private record Pending(BlockPos pos, UUID piece, long gameTime) {}

    private static final Map<UUID, Pending> PENDING_RECYCLE = new HashMap<>();
    private static final Map<UUID, Pending> PENDING_CANCEL = new HashMap<>();

    /** Right-click with tongs (main hand) on the forge, routed by the part that was clicked. */
    public static void useTongs(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand, StationLayout.ForgePart part, boolean sneaking) {
        ItemStack tongs = player.getItemInHand(hand);
        if (WorkpieceCodec.isHolding(tongs)) {
            Optional<WorkpieceData> carried = WorkpieceCodec.getHeld(tongs);
            if (carried.isEmpty()) {
                Feedback.fail(player, "workpiece_unreadable");
            } else if (sneaking && part == StationLayout.ForgePart.BASIN) {
                recycle(player, forge, hand);
            } else {
                restPlace(player, forge, hand);
            }
            return;
        }
        switch (part) {
            case REHEAT -> {
                if (forge.restOccupied()) restTake(player, forge, hand);
                else if (forge.job() != null) workBillet(player, forge, hand, sneaking);
                else Feedback.info(player, "rest_empty");
            }
            case BELLOWS -> pump(player, forge, -1);
            case BOOK -> openBook(player, forge, hand);
            default -> {
                if (forge.job() != null || forge.hasUnreadableJob()) workBillet(player, forge, hand, sneaking);
                else if (forge.plan() != null && part == StationLayout.ForgePart.TRAY) commit(player, forge, hand);
                else openBook(player, forge, hand);
            }
        }
    }

    /** World view with tongs on a billet: draw it when it is ready, otherwise (or when sneaking) move it along the tray. */
    private static void workBillet(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand, boolean sneaking) {
        ForgeJob job = forge.job();
        if (job == null) {
            Feedback.fail(player, "job_unreadable");
            return;
        }
        if (!sneaking && job.drawable()) draw(player, forge, hand, -1);
        else turn(player, forge, -1, -1);
    }

    /**
     * A committed job is its owner's for {@code work.jobPrioritySeconds} after their last action on it (online or not);
     * after that anyone in the workshop may work, draw or cancel it. Bounded on purpose: no job is locked forever.
     */
    public static boolean mayWork(ServerPlayer player, ForgeJob job) {
        if (job.owner.equals(player.getUUID())) return true;
        return player.level().getGameTime() - job.lastTouch > 20L * ServerConfig.get(ServerConfig.WORK_JOB_PRIORITY_SECONDS);
    }

    private static boolean checkOwner(ServerPlayer player, ForgeJob job) {
        if (mayWork(player, job)) return true;
        ServerPlayer owner = player.getServer() != null ? player.getServer().getPlayerList().getPlayer(job.owner) : null;
        Feedback.fail(player, "job_owned", owner != null ? owner.getDisplayName() : Component.literal("?"));
        return false;
    }

    public static void openBook(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand) {
        if (forge.isLocked() && !player.getUUID().equals(forge.sessionOwner())) {
            Feedback.fail(player, "station_in_use");
        } else if (!forge.deposits().isEmpty()) {
            Feedback.fail(player, forge.meltProgress() > 0 ? "still_melting" : "needs_heat");
        } else if (forge.moltenUnits() <= 0) {
            Feedback.fail(player, "forge_empty");
        } else {
            ItemStack held = player.getItemInHand(hand);
            SmithingTier tier = held.getItem() instanceof SmithingToolItem tool ? tool.tier() : SmithingTier.STONE;
            SessionManager.openForge(player, forge, hand, tier);
        }
    }

    /** From the pattern book: pins a plan beside the tray. Nothing is reserved and the forge is not locked by it. */
    public static void pinPlan(ServerPlayer player, SmithsForgeBlockEntity forge, SmithingRecipe recipe) {
        forge.setPlan(recipe.getId());
        player.level().playSound(null, forge.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1.0F);
        Feedback.info(player, "plan_pinned", recipe.result().getHoverName());
    }

    /**
     * Commits the pinned plan: everything is checked again now (gates, cost modifiers, free molten metal, components), then
     * the metal is reserved, the exact component stacks go into escrow and the billet is cast on the tray. The target stack,
     * recovery cap, process profile and heat bands are frozen into the job.
     */
    public static void commit(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand) {
        if (forge.job() != null || forge.hasUnreadableJob()) {
            Feedback.fail(player, "job_in_progress");
            return;
        }
        net.minecraft.resources.ResourceLocation planId = forge.plan();
        SmithingRecipe recipe = planId == null ? null : SmithingData.current().recipe(planId);
        if (recipe == null) {
            if (planId != null) forge.setPlan(null);
            Feedback.fail(player, planId == null ? "no_plan" : "recipe_unavailable");
            return;
        }
        if (!forge.deposits().isEmpty()) {
            Feedback.fail(player, forge.meltProgress() > 0 ? "still_melting" : "needs_heat");
            return;
        }
        if (!forge.isReady() || !recipe.family().equals(forge.family())) {
            Feedback.fail(player, "plan_wrong_metal", recipe.familyName(), recipe.result().getHoverName());
            return;
        }
        Optional<Component> denied = ImmersiveSmithingAPI.recipeDenial(player, recipe);
        if (denied.isPresent()) {
            Feedback.fail(player, denied.get());
            return;
        }
        int units = ImmersiveSmithingAPI.metalUnits(player, recipe);
        if (forge.availableUnits() < units) {
            Feedback.fail(player, Component.translatable("screen.immersive_smithing.forge.needs_more",
                    MaterialUnits.describe(units - forge.availableUnits())));
            return;
        }
        int protectedSlot = ForgeRecipeSelection.protectedSlot(player, hand);
        var missing = AuxInventory.firstMissing(player, recipe.auxiliary(), protectedSlot);
        if (missing.isPresent()) {
            Feedback.fail(player, Component.translatable("screen.immersive_smithing.forge.needs_component",
                    missing.get().count(), missing.get().displayStack().getHoverName()));
            return;
        }
        List<ItemStack> escrow = AuxInventory.take(player, recipe.auxiliary(), protectedSlot);
        if (escrow == null) {
            Feedback.fail(player, "missing_auxiliary_now");
            return;
        }
        ItemStack target = ResultAssembler.assemble(recipe, escrow).orElse(ItemStack.EMPTY);
        if (target.isEmpty()) {
            for (ItemStack stack : escrow) ItemHandlerHelper.giveItemToPlayer(player, stack);
            Feedback.fail(player, "result_unavailable");
            return;
        }
        ProcessProfile profile = ProcessResolver.resolve(recipe);
        ForgeJob job = new ForgeJob();
        job.id = UUID.randomUUID();
        job.owner = player.getUUID();
        job.lastTouch = player.level().getGameTime();
        job.recipeId = recipe.getId();
        job.family = recipe.family();
        job.anvilPattern = recipe.anvilPattern();
        job.units = units;
        job.recoverable = ResultAssembler.recovery(recipe, escrow, units);
        job.target = target;
        job.escrow = List.copyOf(escrow);
        job.sockets = ProcessResolver.sockets(recipe, escrow, profile);
        job.profile = profile;
        job.thermal = configured(ProcessResolver.thermal(recipe.family(), profile));
        // The work keeps the recipe's own size: a discount changes the bill, not the work.
        job.workScale = ProcessResolver.workScale(recipe.metalUnits());
        forge.beginJob(job);
        ServerLevel level = player.serverLevel();
        level.playSound(null, forge.getBlockPos(), ModSounds.FORGE_CAST.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.COMMIT, player, 0, StationLayout.TRAY_CENTER, 1F, 0);
        Feedback.info(player, "job_committed", target.getHoverName(), MaterialUnits.describe(units));
    }

    /** The heat bands with the server's cooling and heating multipliers applied. Frozen into each job. */
    static ThermalProfile configured(ThermalProfile base) {
        int cooling = clampRate(base.cooling() * ServerConfig.get(ServerConfig.WORK_COOLING_MULTIPLIER));
        int heating = clampRate(base.heating() * ServerConfig.get(ServerConfig.WORK_HEATING_MULTIPLIER));
        return new ThermalProfile(base.workableMin(), base.workableMax(), base.optimalMin(), base.optimalMax(), cooling, heating);
    }

    private static int clampRate(double value) {
        return (int) Math.max(100, Math.min(10_000, Math.round(value)));
    }

    public static void pump(ServerPlayer player, SmithsForgeBlockEntity forge, int sequence) {
        if (!forge.pump()) return; // the previous stroke is still moving: not extra air
        forge.touchJob(player.level().getGameTime());
        ServerLevel level = player.serverLevel();
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.PUMP, player, sequence, StationLayout.BELLOWS_HANDLE, forge.air() / 1000F, 0);
        if (!forge.fireOn() && forge.job() != null) Feedback.info(player, "fire_out");
    }

    public static void turn(ServerPlayer player, SmithsForgeBlockEntity forge, int position, int sequence) {
        ForgeJob job = forge.job();
        if (job == null) {
            Feedback.fail(player, "no_billet");
            return;
        }
        if (!checkOwner(player, job)) return;
        if (findTongs(player, false) == null) {
            Feedback.fail(player, "tongs_needed");
            return;
        }
        int next = position < 0 ? Math.floorMod(job.position + 1, ThermalRules.TRAY_POSITIONS)
                : Math.max(0, Math.min(ThermalRules.TRAY_POSITIONS - 1, position));
        forge.turnBillet(next);
        forge.touchJob(player.level().getGameTime());
        WorkEvents.send(player.serverLevel(), forge.getBlockPos(), WorkEvents.TURN, player, sequence, StationLayout.trayZone(next, 1), 1F, next);
    }

    /** Draws the billet with empty tongs, once its coolest zone is workable. Its heat preparation is recorded now. */
    public static void draw(ServerPlayer player, SmithsForgeBlockEntity forge, @Nullable InteractionHand preferred, int sequence) {
        ForgeJob job = forge.job();
        if (job == null) {
            Feedback.fail(player, forge.hasUnreadableJob() ? "job_unreadable" : "no_billet");
            return;
        }
        if (!checkOwner(player, job)) return;
        InteractionHand hand = preferred != null && isEmptyTongs(player.getItemInHand(preferred)) ? preferred : findTongs(player, false);
        if (hand == null) {
            Feedback.fail(player, "empty_tongs_needed");
            return;
        }
        if (!job.drawable()) {
            ThermalProfile.HeatBand band = job.thermal.band(ThermalRules.coolest(job.heat));
            Feedback.fail(player, "billet_not_ready", Component.translatable("heat.immersive_smithing." + band.key()));
            return;
        }
        WorkpieceData piece = forge.drawJob();
        if (piece == null) {
            Feedback.fail(player, "not_enough_metal_now");
            return;
        }
        ItemStack tongs = player.getItemInHand(hand);
        WorkpieceCodec.setHeld(tongs, piece);
        if (!player.getAbilities().instabuild) SmithingToolItem.wearWithoutBreaking(tongs);
        ServerLevel level = player.serverLevel();
        level.playSound(null, forge.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.DRAW, player, sequence, StationLayout.TRAY_CENTER, piece.process().heatPrep / 1000F, 0);
        ModAdvancements.award(player, ModAdvancements.INTO_SHAPE);
        Feedback.info(player, "billet_drawn", piece.target().getHoverName(),
                Component.translatable("heat.immersive_smithing.prep." + prepBand(piece.process().heatPrep)));
    }

    static String prepBand(int prep) {
        if (prep >= 900) return "excellent";
        if (prep >= 700) return "good";
        if (prep >= 450) return "fair";
        return "poor";
    }

    /** Cancels an undrawn job after a confirming second request: metal released, components refunded once. */
    public static void cancel(ServerPlayer player, SmithsForgeBlockEntity forge) {
        ForgeJob job = forge.job();
        if (job == null) {
            Feedback.fail(player, "no_billet");
            return;
        }
        if (!checkOwner(player, job)) return;
        long now = player.level().getGameTime();
        Pending pending = PENDING_CANCEL.get(player.getUUID());
        if (pending == null || !pending.pos.equals(forge.getBlockPos()) || !pending.piece.equals(job.id) || now - pending.gameTime > CONFIRM_TICKS) {
            PENDING_CANCEL.put(player.getUUID(), new Pending(forge.getBlockPos().immutable(), job.id, now));
            Feedback.info(player, "cancel_confirm", job.target.getHoverName());
            return;
        }
        PENDING_CANCEL.remove(player.getUUID());
        boolean owner = job.owner.equals(player.getUUID());
        for (ItemStack stack : forge.cancelJob()) {
            if (owner) ItemHandlerHelper.giveItemToPlayer(player, stack);
            else net.minecraft.world.Containers.dropItemStack(player.level(), forge.getBlockPos().getX() + 0.5,
                    forge.getBlockPos().getY() + 1.0, forge.getBlockPos().getZ() + 0.5, stack);
        }
        WorkEvents.send(player.serverLevel(), forge.getBlockPos(), WorkEvents.CANCEL, player, 0, StationLayout.TRAY_CENTER, 1F, 0);
        Feedback.info(player, "job_cancelled");
    }

    /** Returns a workpiece to the forge: it rests in the mouth of the fire and reheats. Its progress is untouched. */
    public static void restPlace(ServerPlayer player, SmithsForgeBlockEntity forge, @Nullable InteractionHand preferred) {
        InteractionHand hand = preferred != null && WorkpieceCodec.isHolding(player.getItemInHand(preferred)) ? preferred : findTongs(player, true);
        if (hand == null) {
            Feedback.fail(player, "nothing_to_reheat");
            return;
        }
        ItemStack tongs = player.getItemInHand(hand);
        WorkpieceData piece = WorkpieceCodec.getHeld(tongs).orElse(null);
        if (piece == null) {
            Feedback.fail(player, "workpiece_unreadable");
            return;
        }
        WorkStage stage = piece.stage();
        if (stage == WorkStage.QUENCHING) {
            Feedback.fail(player, "finish_quench_first");
            return;
        }
        if (stage.quenched()) {
            Feedback.fail(player, "already_quenched");
            return;
        }
        if (forge.restOccupied()) {
            Feedback.fail(player, "rest_occupied");
            return;
        }
        forge.placeOnRest(piece);
        WorkpieceCodec.clearHeld(tongs);
        ServerLevel level = player.serverLevel();
        level.playSound(null, forge.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 0.9F, 1.1F);
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.PLACE, player, 0, StationLayout.REST_CENTER, 1F, 0);
        Feedback.info(player, forge.canHeat(piece.family()) ? "reheating" : "reheat_fire_out");
    }

    public static void restTake(ServerPlayer player, SmithsForgeBlockEntity forge, @Nullable InteractionHand preferred) {
        InteractionHand hand = preferred != null && isEmptyTongs(player.getItemInHand(preferred)) ? preferred : findTongs(player, false);
        if (hand == null) {
            Feedback.fail(player, "empty_tongs_needed");
            return;
        }
        if (forge.rest() == null) {
            Feedback.fail(player, forge.restOccupied() ? "workpiece_unreadable" : "rest_empty");
            return;
        }
        WorkpieceData piece = forge.takeFromRest();
        if (piece == null) return;
        WorkpieceCodec.setHeld(player.getItemInHand(hand), piece);
        ServerLevel level = player.serverLevel();
        level.playSound(null, forge.getBlockPos(), ModSounds.TONGS_GRAB.get(), SoundSource.BLOCKS, 0.9F, 0.9F);
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.TAKE, player, 0, StationLayout.REST_CENTER, 1F, 0);
        ThermalProfile.HeatBand band = piece.process().thermal.band(ThermalRules.coolest(piece.process().heat));
        Feedback.info(player, "taken_from_rest", Component.translatable("heat.immersive_smithing." + band.key()));
    }

    /**
     * Deliberate recycling, confirmed by a second sneak-use within three seconds: the piece's progress is destroyed and its
     * recoverable metal melts into the reservoir. Components not yet fitted come back; assimilated equipment does not
     * (its metal is in the recovery).
     */
    public static void recycle(ServerPlayer player, SmithsForgeBlockEntity forge, InteractionHand hand) {
        ItemStack holder = player.getItemInHand(hand);
        WorkpieceData piece = WorkpieceCodec.getHeld(holder).orElse(null);
        if (piece == null) {
            Feedback.fail(player, "workpiece_unreadable");
            return;
        }
        long now = player.level().getGameTime();
        Pending pending = PENDING_RECYCLE.get(player.getUUID());
        if (pending == null || !pending.pos.equals(forge.getBlockPos()) || !pending.piece.equals(piece.id()) || now - pending.gameTime > CONFIRM_TICKS) {
            PENDING_RECYCLE.put(player.getUUID(), new Pending(forge.getBlockPos().immutable(), piece.id(), now));
            Feedback.info(player, "recycle_confirm", piece.target().getHoverName(), MaterialUnits.describe(piece.recoverableNow()));
            return;
        }
        PENDING_RECYCLE.remove(player.getUUID());
        SmithsForgeBlockEntity.Result result = forge.acceptWorkpiece(piece);
        if (result != SmithsForgeBlockEntity.Result.OK) {
            if (result == SmithsForgeBlockEntity.Result.WRONG_FAMILY) {
                Feedback.fail(player, "result.wrong_family", forge.familyData().map(MaterialFamily::displayName).orElse(Component.literal("?")));
            } else {
                Feedback.fail(player, "result." + result.key());
            }
            return;
        }
        if (holder.is(ModItems.HOT_WORKPIECE.get())) holder.shrink(1);
        else WorkpieceCodec.clearHeld(holder);
        for (ItemStack component : piece.process().unattachedStacks()) ItemHandlerHelper.giveItemToPlayer(player, component);
        ServerLevel level = player.serverLevel();
        level.playSound(null, forge.getBlockPos(), SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 1.0F);
        WorkEvents.send(level, forge.getBlockPos(), WorkEvents.RECYCLE, player, 0, StationLayout.TRAY_CENTER, 1F, 0);
        Feedback.info(player, "workpiece_melted", MaterialUnits.describe(piece.recoverableNow()));
    }

    static boolean isEmptyTongs(ItemStack stack) {
        return stack.getItem() instanceof SmithingTongsItem && !WorkpieceCodec.isHolding(stack);
    }

    /** The hand holding tongs (loaded or empty as asked), main hand first. */
    @Nullable
    static InteractionHand findTongs(ServerPlayer player, boolean loaded) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SmithingTongsItem && WorkpieceCodec.isHolding(stack) == loaded) return hand;
        }
        return null;
    }

    public static void onLogout(UUID player) {
        PENDING_RECYCLE.remove(player);
        PENDING_CANCEL.remove(player);
    }

    private ForgeActions() {}
}
