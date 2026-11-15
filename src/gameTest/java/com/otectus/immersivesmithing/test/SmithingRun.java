package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ForgeActions;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.TroughActions;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;

/**
 * A complete, server-validated smithing run for one recipe, through the same action services a player uses: molten metal
 * and a fire in a Smith's Forge, the pattern pinned and committed at the tray, the billet heated evenly with the bellows and
 * turned until every zone is ideal, drawn, shaped on a Smith's Anvil with blows at the ideal heat (reheating on the forge's
 * rest whenever it cools), quenched in a Smith's Trough for the full hiss, its components fitted and the piece collected.
 * Components are put in the smith's inventory first (a reworked piece can be given explicitly). Returns the finished item as
 * it landed in the inventory, so tests see exactly what a player would.
 */
final class SmithingRun {
    static final BlockPos FORGE = new BlockPos(1, 1, 1);
    static final BlockPos ANVIL = new BlockPos(3, 1, 1);
    static final BlockPos TROUGH = new BlockPos(1, 1, 3);

    static ItemStack smith(GameTestHelper h, FakePlayer player, SmithingRecipe recipe, ItemStack reworked) {
        h.setBlock(FORGE, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(FORGE));
        // Seed paid stock directly: a fractional-content workpiece is deliberately worth less than its forge charge.
        forge.refundCast(recipe.metalUnits(), recipe.family());
        light(forge);
        player.getInventory().clearContent();
        int slot = 9;
        for (AuxiliaryIngredient aux : recipe.auxiliary()) {
            ItemStack stack = aux.consumesEquipment() && !reworked.isEmpty() ? reworked.copy() : aux.displayStack();
            player.getInventory().setItem(slot++, stack);
        }
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.NETHERITE));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        forge.setPlan(recipe.getId());
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        if (forge.job() == null) throw new IllegalStateException("commit refused for " + recipe.getId());
        heatEvenly(h, player, forge);
        ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
        if (!WorkpieceCodec.isHolding(player.getMainHandItem())) throw new IllegalStateException("billet not drawn for " + recipe.getId());

        h.setBlock(ANVIL, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ANVIL));
        AnvilActions.place(player, anvil, InteractionHand.MAIN_HAND);
        shape(h, player, forge, anvil);

        // Quench with the tongs in the main hand.
        ItemStack heldTongs = player.getOffhandItem();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, heldTongs);
        AnvilActions.take(player, anvil, InteractionHand.MAIN_HAND);
        h.setBlock(TROUGH, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(TROUGH));
        trough.addBucket();
        quench(h, player, trough);

        WorkpieceData cooled = WorkpieceCodec.getHeld(player.getMainHandItem()).orElse(null);
        if (cooled != null) {
            // Components to fit: at the anvil, one action per socket, then collect.
            AnvilActions.place(player, anvil, InteractionHand.MAIN_HAND);
            for (int i = 0; i < 16 && anvil.workpiece() != null; i++) AnvilActions.fit(player, anvil, -1, 0);
            AnvilActions.collect(player, anvil);
        }
        WorkLeases.release(player, null);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(recipe.resultItem())) return stack;
        }
        throw new IllegalStateException("finished " + recipe.resultItem() + " not in the inventory");
    }

    /** Lava where the metal accepts it; otherwise the first fuel its rule names, lit. */
    static void light(SmithsForgeBlockEntity forge) {
        forge.addLavaBucket(false);
        TagKey<Item> required = forge.familyData().map(MaterialFamily::requiredFuelTag).orElse(null);
        if (forge.heatSourceAllows(required)) return;
        forge.takeLavaBucket();
        Item fuel = BuiltInRegistries.ITEM.getTag(required).flatMap(set -> set.stream().findFirst()).map(holder -> holder.value()).orElse(null);
        if (fuel == null) throw new IllegalStateException("no fuel for " + forge.family());
        forge.insertFuel(new ItemStack(fuel, 32), 32, false, null);
        forge.ignite();
    }

    static void tick(GameTestHelper h, SmithsForgeBlockEntity forge, int ticks) {
        for (int i = 0; i < ticks; i++) SmithsForgeBlockEntity.serverTick(h.getLevel(), forge.getBlockPos(), forge.getBlockState(), forge);
    }

    static void tick(GameTestHelper h, SmithsAnvilBlockEntity anvil, int ticks) {
        for (int i = 0; i < ticks; i++) SmithsAnvilBlockEntity.serverTick(h.getLevel(), anvil.getBlockPos(), anvil.getBlockState(), anvil);
    }

    static void tick(GameTestHelper h, SmithsTroughBlockEntity trough, int ticks) {
        for (int i = 0; i < ticks; i++) SmithsTroughBlockEntity.serverTick(h.getLevel(), trough.getBlockPos(), trough.getBlockState(), trough);
    }

    /**
     * Brings the billet evenly into the ideal band like a careful smith: the coolest end goes over the hottest coals, the
     * bellows are pumped only while everything is still below the ideal band.
     */
    static void heatEvenly(GameTestHelper h, FakePlayer player, SmithsForgeBlockEntity forge) {
        long lastTurn = -100;
        for (int t = 0; t < 4000; t++) {
            ForgeJob job = forge.job();
            if (job == null) throw new IllegalStateException("billet vanished");
            int[] heat = job.heat;
            int lo = 0, hi = 0;
            for (int z = 1; z < heat.length; z++) {
                if (heat[z] < heat[lo]) lo = z;
                if (heat[z] > heat[hi]) hi = z;
            }
            if (inBand(job) && heat[hi] - heat[lo] <= 550) return;
            int want = lo == 0 ? 0 : lo == 2 ? 2 : 1;
            if (heat[hi] - heat[lo] > 300 && want != job.position && t - lastTurn >= 10) {
                ForgeActions.turn(player, forge, want, -1);
                lastTurn = t;
            }
            int ideal = (job.thermal.optimalMin() + job.thermal.optimalMax()) / 2;
            if (heat[lo] < ideal - 200 && heat[hi] < job.thermal.optimalMax() - 200) ForgeActions.pump(player, forge, -1);
            tick(h, forge, 1);
        }
        throw new IllegalStateException("billet never reached an even ideal heat");
    }

    private static boolean inBand(ForgeJob job) {
        for (int heat : job.heat) if (heat < job.thermal.optimalMin() + 150 || heat > job.thermal.optimalMax() - 150) return false;
        return true;
    }

    /** Ideal blows, in order, on the right face; the piece goes back to the forge's rest whenever the next zone cools. */
    static void shape(GameTestHelper h, FakePlayer player, SmithsForgeBlockEntity forge, SmithsAnvilBlockEntity anvil) {
        ItemStack tongs = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.hammer(SmithingTier.NETHERITE)));
        player.setItemInHand(InteractionHand.OFF_HAND, tongs);
        WorkLeases.Lease lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
        if (lease == null) throw new IllegalStateException("anvil lease refused");
        for (int blow = 0; blow < 400; blow++) {
            WorkpieceData piece = anvil.workpiece();
            if (piece == null) throw new IllegalStateException("piece vanished from the anvil");
            ProcessState p = piece.process();
            if (p.stage.ordinal() >= com.otectus.immersivesmithing.work.WorkStage.SHAPED.ordinal() && p.allComplete()) return;
            int op = ShapingRules.nextOperation(p);
            if (op < 0) return;
            ProcessProfile.Operation o = p.profile.operations().get(op);
            tick(h, anvil, 14);
            if (!o.face().matches(p.flipped)) {
                AnvilActions.flip(player, anvil, lease, 0);
                continue;
            }
            int heat = anvil.currentHeat()[o.zone()];
            if (heat < p.thermal.optimalMin() + 200) {
                reheat(h, player, forge, anvil);
                lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
                continue;
            }
            boolean heavy = o.kind().rough();
            AnvilActions.strike(player, anvil, lease, op, o.centerU(), o.centerV(), heavy, blow + 1);
        }
        throw new IllegalStateException("shaping did not finish");
    }

    /** To the forge's rest and back, at the top of the ideal band. */
    static void reheat(GameTestHelper h, FakePlayer player, SmithsForgeBlockEntity forge, SmithsAnvilBlockEntity anvil) {
        ItemStack hammer = player.getMainHandItem();
        ItemStack tongs = player.getOffhandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        player.setItemInHand(InteractionHand.OFF_HAND, hammer);
        AnvilActions.take(player, anvil, InteractionHand.MAIN_HAND);
        ForgeActions.restPlace(player, forge, InteractionHand.MAIN_HAND);
        for (int t = 0; t < 4000; t++) {
            WorkpieceData rest = forge.rest();
            if (rest == null) throw new IllegalStateException("nothing on the rest");
            int lo = ThermalRules.coolest(rest.process().heat);
            int hi = Math.max(rest.process().heat[0], Math.max(rest.process().heat[1], rest.process().heat[2]));
            if (lo >= rest.process().thermal.optimalMax() - 400 && hi <= rest.process().thermal.optimalMax() - 50) break;
            if (hi < rest.process().thermal.optimalMax() - 300) ForgeActions.pump(player, forge, -1);
            tick(h, forge, 1);
        }
        ForgeActions.restTake(player, forge, InteractionHand.MAIN_HAND);
        AnvilActions.place(player, anvil, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        player.setItemInHand(InteractionHand.OFF_HAND, tongs);
    }

    /** Lowers the piece, holds it through the whole hiss and lifts it at the end cue. */
    static void quench(GameTestHelper h, FakePlayer player, SmithsTroughBlockEntity trough) {
        WorkpieceData held = WorkpieceCodec.getHeld(player.getMainHandItem()).orElse(null);
        TroughActions.lower(player, trough, InteractionHand.MAIN_HAND);
        if (trough.quenchPiece() == null) {
            throw new IllegalStateException("not lowered: " + (held == null ? "no piece" : held.stage() + ", complete " + held.process().allComplete()
                    + ", mean heat " + ThermalRules.mean(held.process().heat) + ", water " + trough.availableWater()
                    + ", lease " + WorkLeases.holder(trough.getLevel(), trough.getBlockPos())));
        }
        tick(h, trough, QuenchRules.BOUNDARY_TICKS + trough.quenchDuration() + 2);
        TroughActions.lift(player, trough, InteractionHand.MAIN_HAND);
    }

    static ResourceLocation patternOf(SmithingRecipe recipe) {
        return recipe.anvilPattern();
    }

    /** The first recipe producing an item, for tests that only need a known recipe. */
    static SmithingRecipe recipe(Item item) {
        return SmithingData.current().recipesProducing(item).get(0);
    }

    private SmithingRun() {}
}
