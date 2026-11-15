package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ForgeActions;
import com.otectus.immersivesmithing.work.GrindstoneActions;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.TroughActions;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/** Station behaviour and the hands-on work rules, through the real action services, driven with a fake player. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {

    @GameTest(template = "empty")
    public static void roundedStationsKeepPickingSurfaces(GameTestHelper h) {
        var forge = ModBlocks.SMITHS_FORGE.get().defaultBlockState().getShape(h.getLevel(), h.absolutePos(STATION));
        h.assertTrue(occupies(forge, 1, 1, 1), "The forge stands on a square plinth out to its corners");
        h.assertTrue(!occupies(forge, 8, 4, 2), "The firebox mouth is open at the front");
        h.assertTrue(occupies(forge, 8, 1, 8), "Forge foundation supports the hearth");
        h.assertTrue(!occupies(forge, 8, 12, 10.5), "Forge basin remains open for top loading");
        h.assertTrue(!occupies(forge, 8, 11, 5), "The billet tray is open in front of the divider");
        h.assertTrue(occupies(forge, 14.5, 4, 5), "The bellows stand at the front right");
        h.assertTrue(occupies(forge, 8, 13.5, 1), "Forge rim remains pickable");
        for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var anvil = ModBlocks.SMITHS_ANVIL.get().defaultBlockState()
                    .setValue(com.otectus.immersivesmithing.block.SmithsAnvilBlock.FACING, facing)
                    .getShape(h.getLevel(), h.absolutePos(STATION));
            double hornX = 8 + facing.getStepX() * 7.5;
            double hornZ = 8 + facing.getStepZ() * 7.5;
            h.assertTrue(occupies(anvil, hornX, 14.25, hornZ), "Anvil horn rotates toward " + facing);
            h.assertTrue(!occupies(anvil, 16 - hornX, 14.25, 16 - hornZ), "Anvil heel ends before the opposite block edge");
            h.assertTrue(occupies(anvil, 8, 8, 8), "Timber stump supports the anvil");
        }
        var wheel = ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState().getShape(h.getLevel(), h.absolutePos(STATION));
        h.assertTrue(!occupies(wheel, 8, 14.25, 4), "Wheel's upper corner is rounded off");
        h.assertTrue(occupies(wheel, 8, 14.25, 8), "Wheel's upper working surface is pickable");
        h.assertTrue(occupies(wheel, 8, 13.5, 4.5) && !occupies(wheel, 8, 13.5, 3.5),
                "The intermediate rounded shoulder matches the five-course wheel model");
        h.succeed();
    }

    private static boolean occupies(net.minecraft.world.phys.shapes.VoxelShape shape, double x, double y, double z) {
        return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(shape,
                net.minecraft.world.phys.shapes.Shapes.box((x - .01) / 16, (y - .01) / 16, (z - .01) / 16,
                        (x + .01) / 16, (y + .01) / 16, (z + .01) / 16), net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    private static SmithsForgeBlockEntity forge(GameTestHelper h) {
        return forgeAt(h, STATION);
    }

    private static SmithsForgeBlockEntity forgeAt(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        return (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void forgeMeltsOneFamily(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        SmithsForgeBlockEntity.Result[] err = new SmithsForgeBlockEntity.Result[1];
        h.assertTrue(forge.insertMetal(new ItemStack(Items.IRON_INGOT, 2), 2, false, err) == 2, "Two ingots accepted");
        h.assertTrue(forge.insertMetal(new ItemStack(Items.RAW_IRON), 1, false, err) == 1, "Raw iron joins the iron batch");
        h.assertTrue(forge.insertMetal(new ItemStack(Items.GOLD_INGOT), 1, false, err) == 0 && err[0] == SmithsForgeBlockEntity.Result.WRONG_FAMILY, "Gold is rejected");
        h.assertTrue(forge.insertFuel(new ItemStack(Items.IRON_INGOT), 1, false, err) == 0, "Metal is not fuel");
        h.assertTrue(forge.ignite() == SmithsForgeBlockEntity.Result.NO_FUEL, "Cannot ignite without fuel");
        h.assertTrue(forge.canExtractDeposits(), "Unlit deposits can be taken back");
        ItemStack back = forge.extractLastDeposit();
        h.assertTrue(back.is(Items.RAW_IRON) && forge.depositUnits() == 18, "Most recent deposit comes back first");
        h.assertTrue(forge.insertFuel(new ItemStack(Items.COAL, 2), 2, false, err) == 2, "Coal accepted");
        h.assertTrue(forge.ignite() == SmithsForgeBlockEntity.Result.OK, "Ignites");
        h.assertTrue(!forge.canExtractDeposits(), "Burning deposits cannot be taken back");
        h.succeedWhen(() -> {
            h.assertTrue(forge.isReady(), "Melted");
            h.assertTrue(forge.moltenUnits() == 18, "Exactly 18 units, got " + forge.moltenUnits());
            h.assertTrue(forge.getBlockState().getValue(SmithsForgeBlock.READY), "READY block state");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void netheriteNeedsLava(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        SmithsForgeBlockEntity.Result[] err = new SmithsForgeBlockEntity.Result[1];
        forge.insertMetal(new ItemStack(Items.NETHERITE_INGOT), 1, false, err);
        forge.insertFuel(new ItemStack(Items.COAL, 4), 4, false, err);
        h.assertTrue(forge.ignite() == SmithsForgeBlockEntity.Result.WRONG_FUEL, "Coal cannot melt netherite");
        h.assertTrue(forge.addLavaBucket(false) == SmithsForgeBlockEntity.Result.SOLID_FUEL_PRESENT, "Lava needs an empty firebox");
        h.assertTrue(forge.extractFuel().getCount() == 4, "Unlit coal comes back");
        h.assertTrue(forge.addLavaBucket(false) == SmithsForgeBlockEntity.Result.OK, "Lava accepted");
        h.assertTrue(forge.ignite() == SmithsForgeBlockEntity.Result.LAVA_NEEDS_NO_IGNITION, "Lava needs no ignition");
        h.succeedWhen(() -> h.assertTrue(forge.isReady() && forge.moltenUnits() == 9, "Netherite melted with lava"));
    }

    @GameTest(template = "empty")
    public static void forgeCapacityAndRecycling(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        SmithsForgeBlockEntity.Result[] err = new SmithsForgeBlockEntity.Result[1];
        int blocks = forge.insertMetal(new ItemStack(Items.IRON_BLOCK, 64), 64, false, err);
        h.assertTrue(blocks == forge.capacity() / 81, "Capacity limits blocks, got " + blocks);
        h.assertTrue(forge.insertMetal(new ItemStack(Items.IRON_BLOCK), 1, false, err) == 0 && err[0] == SmithsForgeBlockEntity.Result.FULL, "Full forge rejects more");

        SmithsForgeBlockEntity other = forgeAt(h, new BlockPos(0, 1, 0));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, 3);
        new QualityData(20, 20, true).apply(sword);
        h.assertTrue(other.insertMetal(sword, 1, false, err) == 1 && other.depositUnits() == 18, "Faulty enchanted sword is worth 18 units");
        WorkpieceData wp = TestSupport.workpiece(new ItemStack(Items.IRON_PICKAXE), 40, -1, false, WorkpieceState.FORGED);
        h.assertTrue(other.acceptWorkpiece(wp) == SmithsForgeBlockEntity.Result.OK && other.moltenUnits() == 27, "Workpiece melts back to 27 units");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilAndTroughTransfers(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, -1, false, WorkpieceState.FORGED));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        TestSupport.use(h, player, 0.9);
        h.assertTrue(anvil.hasWorkpiece() && !WorkpieceCodec.isHolding(tongs), "Tongs place the workpiece on the anvil");
        TestSupport.use(h, player, 0.9);
        h.assertTrue(!anvil.hasWorkpiece() && WorkpieceCodec.isHolding(tongs), "Empty tongs pick it back up");

        h.setBlock(STATION, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, 95, false, WorkpieceState.SHAPED));
        TestSupport.use(h, player, 0.5);
        h.assertTrue(WorkpieceCodec.isHolding(tongs) && !trough.occupied(), "An empty trough does not take the workpiece");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        TestSupport.use(h, player, 0.5);
        h.assertTrue(trough.water() == 1000 && player.getMainHandItem().is(Items.BUCKET), "Water bucket fills the trough");
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, -1, false, WorkpieceState.FORGED));
        TestSupport.use(h, player, 0.5);
        h.assertTrue(WorkpieceCodec.isHolding(tongs) && trough.water() == 1000 && !trough.occupied(), "Unshaped workpieces cannot be quenched");

        // An old shaped piece: lowered, held through the hiss, lifted; its stored scores are kept and no fitting is charged.
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, 95, false, WorkpieceState.SHAPED));
        TestSupport.use(h, player, 0.5);
        h.assertTrue(!WorkpieceCodec.isHolding(tongs) && trough.occupied() && trough.water() == 1000, "Lowered: the trough holds it, water reserved");
        SmithingRun.tick(h, trough, QuenchRules.BOUNDARY_TICKS);
        h.assertTrue(trough.immersed() && trough.water() == 750, "One quench's water is charged at the immersion boundary");
        SmithingRun.tick(h, trough, trough.quenchDuration());
        TestSupport.use(h, player, 0.5);
        h.assertTrue(!trough.occupied() && trough.water() == 750 && trough.quenchesLeft() == 3, "Lifted; nothing more charged");
        ItemStack result = find(player, Items.IRON_SWORD);
        QualityData q = QualityData.get(result).orElse(null);
        h.assertTrue(q != null && q.forgeScore() == 80 && q.anvilScore() == 95 && !q.faulty(), "Finished sword carries the stored scores");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forgeJobReservationRefundAndDraw(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        FakePlayer player = TestSupport.player(h);
        forge.refundCast(54, new net.minecraft.resources.ResourceLocation("minecraft", "iron"));
        SmithingRun.light(forge);
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.STONE));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        ItemStack namedSticks = new ItemStack(Items.STICK, 3);
        namedSticks.setHoverName(net.minecraft.network.chat.Component.literal("Ash"));
        player.getInventory().setItem(5, namedSticks);
        SmithingRecipe pickaxe = SmithingData.current().recipesProducing(Items.IRON_PICKAXE).get(0);

        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.job() == null && player.getInventory().countItem(Items.STICK) == 3, "Nothing to commit without a pinned plan");
        forge.setPlan(ImmersiveSmithing.id("invented"));
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.job() == null && forge.plan() == null, "An unknown plan is refused and unpinned");

        forge.setPlan(pickaxe.getId());
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.job() != null && forge.reservedUnits() == 27 && forge.availableUnits() == 27 && forge.moltenUnits() == 54,
                "Commit reserves 27 units in the reservoir");
        h.assertTrue(player.getInventory().countItem(Items.STICK) == 1, "Two sticks in escrow");
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(player.getInventory().countItem(Items.STICK) == 1, "A second commit while a billet is on the tray takes nothing");
        ForgeActions.cancel(player, forge);
        h.assertTrue(forge.job() != null, "Cancelling needs a confirmation");
        ForgeActions.cancel(player, forge);
        h.assertTrue(forge.job() == null && player.getInventory().countItem(Items.STICK) == 3 && forge.moltenUnits() == 54,
                "Confirmed cancel refunds once and frees the metal");
        ItemStack back = player.getInventory().getItem(5).is(Items.STICK) ? player.getInventory().getItem(5) : find(player, Items.STICK);
        h.assertTrue(back.getHoverName().getString().equals("Ash"), "The exact component stacks come back");

        forge.setPlan(pickaxe.getId());
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
        h.assertTrue(forge.job() != null && !WorkpieceCodec.isHolding(tongs), "A cold billet cannot be drawn");
        SmithingRun.heatEvenly(h, player, forge);
        ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
        WorkpieceData wp = WorkpieceCodec.getHeld(player.getMainHandItem()).orElse(null);
        h.assertTrue(wp != null && wp.target().is(Items.IRON_PICKAXE) && wp.stage() == WorkStage.BILLET, "The billet is drawn into the tongs");
        h.assertTrue(wp.process().heatPrep == 1000, "Even, ideal heating is a perfect heat preparation, got " + wp.process().heatPrep);
        h.assertTrue(forge.job() == null && forge.moltenUnits() == 27 && forge.plan() != null, "27 units left the reservoir; the plan stays pinned");
        h.assertTrue(wp.process().sockets.size() == 1 && wp.process().sockets.get(0).stacks().get(0).getCount() == 2
                && wp.process().sockets.get(0).stacks().get(0).getHoverName().getString().equals("Ash"), "The exact sticks travel with the piece");
        h.assertTrue(player.getInventory().countItem(Items.STICK) == 1, "The sticks are not refunded once drawn");
        h.assertTrue(player.getMainHandItem().getDamageValue() == 1, "Tongs wear by one");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void heatRulesBellowsScaleAndReheat(GameTestHelper h) {
        SmithsForgeBlockEntity forge = forge(h);
        FakePlayer player = TestSupport.player(h);
        forge.refundCast(27, new net.minecraft.resources.ResourceLocation("minecraft", "iron"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.IRON)));
        player.getInventory().setItem(5, new ItemStack(Items.STICK, 2));
        forge.setPlan(SmithingData.current().recipesProducing(Items.IRON_PICKAXE).get(0).getId());
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        SmithingRun.tick(h, forge, 200);
        h.assertTrue(forge.job().heat[1] <= ThermalRules.FRESH_BILLET, "Without a fire the billet does not heat");
        SmithingRun.light(forge);
        h.assertTrue(forge.pump() && !forge.pump() && forge.air() == ThermalRules.AIR_PER_PUMP, "A second stroke inside the cooldown adds no air");
        SmithingRun.tick(h, forge, ThermalRules.PUMP_COOLDOWN_TICKS);
        h.assertTrue(forge.pump(), "The next stroke after the cooldown works");
        // Pump without pause: the middle overheats and scale forms; the scale is recorded and bounded.
        for (int t = 0; t < 900; t++) {
            if (t % ThermalRules.PUMP_COOLDOWN_TICKS == 0) forge.pump();
            SmithingRun.tick(h, forge, 1);
        }
        h.assertTrue(forge.job().scale > 0 && forge.job().scale <= 1000, "Overheating builds bounded scale: " + forge.job().scale);
        h.assertTrue(!forge.job().burnt, "Nothing burns unless the server enables it");
        ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
        WorkpieceData drawn = WorkpieceCodec.getHeld(player.getMainHandItem()).orElseThrow();
        h.assertTrue(drawn.process().heatPrep < 900, "An overheated billet draws with a lower heat preparation: " + drawn.process().heatPrep);

        // Reheating keeps every bit of shaping progress.
        h.setBlock(SmithingRun.ANVIL, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SmithingRun.ANVIL));
        ProcessState worked = drawn.process().copy();
        worked.work[0] = 50;
        worked.stage = WorkStage.SHAPING;
        WorkpieceCodec.setHeld(player.getMainHandItem(), drawn.withProcess(worked));
        SmithingRun.tick(h, forge, 1);
        // With the billet drawn there is nothing to heat, so the fire is idle this tick, but it will heat the rest piece:
        // the smith must be told it is reheating, not that the fire is out.
        h.assertTrue(!forge.fireOn() && forge.canHeat(drawn.family()), "An idle lit forge can still heat a piece on its rest");
        ForgeActions.restPlace(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.rest() != null && !WorkpieceCodec.isHolding(player.getMainHandItem()), "The piece rests in the fire");
        SmithingRun.tick(h, forge, 100);
        ForgeActions.restTake(player, forge, InteractionHand.MAIN_HAND);
        WorkpieceData back = WorkpieceCodec.getHeld(player.getMainHandItem()).orElseThrow();
        h.assertTrue(back.process().work[0] == 50 && back.process().stage == WorkStage.SHAPING && back.id().equals(drawn.id()),
                "Same piece, same progress after reheating");
        h.assertTrue(back.process().heatPrep == drawn.process().heatPrep, "Reheating never rerecords the heat preparation");
        h.succeed();
    }

    /** With solid fuel the fire banks between heats, so a cooling piece can go back on the rest without a new spark. */
    @GameTest(template = "empty")
    public static void bankedFireReheatsWithoutANewSpark(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        SmithingRecipe recipe = SmithingRun.recipe(Items.IRON_SWORD);
        h.setBlock(SmithingRun.FORGE, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SmithingRun.FORGE));
        forge.refundCast(recipe.metalUnits(), recipe.family());
        forge.insertFuel(new ItemStack(Items.COAL, 4), 4, false, null);
        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.STICK));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.IRON)));
        forge.setPlan(recipe.getId());
        ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
        h.assertTrue(forge.job() != null && forge.ignite() == SmithsForgeBlockEntity.Result.OK, "A committed billet over lit coal");
        SmithingRun.tick(h, forge, 20);
        h.assertTrue(forge.fireOn(), "The coal fire heats the billet");
        java.util.Arrays.fill(forge.job().heat, (forge.job().thermal.optimalMin() + forge.job().thermal.optimalMax()) / 2);
        ForgeActions.draw(player, forge, InteractionHand.MAIN_HAND, -1);
        // Nothing to heat: the coal being burnt burns down (a quarter of a furnace's 1600 ticks), then the fire banks.
        SmithingRun.tick(h, forge, 500);
        h.assertTrue(!forge.fireOn() && forge.canHeat(recipe.family()), "A banked fire can still heat a piece on the rest");
        ForgeActions.restPlace(player, forge, InteractionHand.MAIN_HAND);
        SmithingRun.tick(h, forge, 2);
        h.assertTrue(forge.rest() != null && forge.fireOn(), "The piece on the rest lights the next coal without a new spark");
        ForgeActions.restTake(player, forge, InteractionHand.MAIN_HAND);
        // The coal lit for the rest burns down, then the bank runs out: left alone, the fire goes out.
        SmithingRun.tick(h, forge, 400 + 1200 + 5);
        h.assertTrue(!forge.canHeat(recipe.family()), "Left alone, a banked fire goes out");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilBlowsFollowTheRules(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        ItemStack hammer = new ItemStack(ModItems.hammer(SmithingTier.STONE));
        player.setItemInHand(InteractionHand.MAIN_HAND, hammer);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.tongs(SmithingTier.STONE)));
        WorkpieceData sword = TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 70, -1, false, WorkpieceState.FORGED);
        anvil.setWorkpiece(sword);
        WorkLeases.Lease lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
        ProcessState p = anvil.workpiece().process();
        int tang = p.profile.operationIndex("form_tang");
        int edge = p.profile.operationIndex("dress_edge");
        int tip = p.profile.operationIndex("set_point");
        h.assertTrue(tang >= 0 && edge >= 0 && tip >= 0, "The sword uses the straight blade process");

        AnvilActions.strike(player, anvil, lease, tang, Float.NaN, 0F, false, 1);
        AnvilActions.strike(player, anvil, lease, tang, 5F, 5F, false, 2);
        h.assertTrue(anvil.workpiece().process().work[tang] == 0, "Non-finite and off-piece points are ignored");
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, edge, 0.6F, 0F, false, 3);
        h.assertTrue(anvil.workpiece().process().blows[edge] == 0, "A back-face part needs the piece turned over");
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, tip, 0.95F, 0F, false, 4);
        h.assertTrue(anvil.workpiece().process().blows[tip] == 0, "The point waits for the blade");
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, tang, 0.1F, 0F, false, 5);
        int after = anvil.workpiece().process().work[tang];
        h.assertTrue(after > 0, "An accepted blow does work");
        AnvilActions.strike(player, anvil, lease, tang, 0.1F, 0F, false, 6);
        h.assertTrue(anvil.workpiece().process().work[tang] == after, "A blow inside the hammer's recovery is dropped");

        // Saturation: once a part is done, more blows add no work and mar it.
        for (int i = 0; i < 20 && !anvil.workpiece().process().complete(tang); i++) {
            SmithingRun.tick(h, anvil, 20);
            AnvilActions.strike(player, anvil, lease, tang, 0.1F, 0F, false, 10 + i);
        }
        h.assertTrue(anvil.workpiece().process().complete(tang), "The tang is formed");
        int quality = ShapingRules.operationQuality(anvil.workpiece().process(), tang);
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, tang, 0.1F, 0F, false, 40);
        ProcessState over = anvil.workpiece().process();
        h.assertTrue(over.work[tang] == over.required(tang) && over.marks[tang] > 0 && ShapingRules.operationQuality(over, tang) < quality,
                "Overworking adds no work and leaves a mark");
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, tang, 0.1F, 0F, false, 41);
        h.assertTrue(anvil.workpiece().process().marks[tang] < over.marks[tang], "A clean blow at the ideal heat smooths the mark (bounded)");

        // Cold metal does not move: nothing changes, nothing is lost.
        ProcessState cold = anvil.workpiece().process().copy();
        java.util.Arrays.fill(cold.heat, 3000);
        cold.heatTick = anvil.clock();
        anvil.replaceWorkpiece(anvil.workpiece().withProcess(cold));
        int blade = cold.profile.operationIndex("draw_blade");
        SmithingRun.tick(h, anvil, 20);
        AnvilActions.strike(player, anvil, lease, blade, 0.5F, 0F, false, 50);
        h.assertTrue(anvil.workpiece().process().work[blade] == 0 && anvil.workpiece().process().revision == cold.revision, "Too cold: no change");
        h.assertTrue(hammer.getDamageValue() == 0, "The hammer wears only when a piece is finished shaping");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void quenchBoundariesAndRecovery(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        trough.addBucket();
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, 90, false, WorkpieceState.SHAPED));
        // Lifted before the water: nothing used, nothing changed.
        TroughActions.lower(player, trough, InteractionHand.MAIN_HAND);
        SmithingRun.tick(h, trough, 3);
        TroughActions.lift(player, trough, InteractionHand.MAIN_HAND);
        WorkpieceData same = WorkpieceCodec.getHeld(tongs).orElseThrow();
        h.assertTrue(trough.water() == 1000 && same.stage() == WorkStage.SHAPED && same.process().dips == 0, "Before the boundary: refunded reservation");
        // Lifted early: the water is spent once, the progress stays, a second lowering costs nothing more.
        TroughActions.lower(player, trough, InteractionHand.MAIN_HAND);
        SmithingRun.tick(h, trough, QuenchRules.BOUNDARY_TICKS + 15);
        TroughActions.lift(player, trough, InteractionHand.MAIN_HAND);
        WorkpieceData partial = WorkpieceCodec.getHeld(tongs).orElseThrow();
        h.assertTrue(trough.water() == 750 && partial.stage() == WorkStage.QUENCHING && partial.process().quenchTicks == 15, "Early lift keeps progress");
        TroughActions.lower(player, trough, InteractionHand.MAIN_HAND);
        SmithingRun.tick(h, trough, QuenchRules.BOUNDARY_TICKS + trough.quenchDuration());
        h.assertTrue(trough.water() == 750, "Continuing a quench charges no more water");
        // Breaking the trough mid-quench drops the piece with its progress.
        h.setBlock(STATION, net.minecraft.world.level.block.Blocks.AIR);
        var drops = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(STATION)).inflate(2));
        WorkpieceData dropped = drops.stream().map(e -> WorkpieceCodec.getHeld(e.getItem())).filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get).findFirst().orElse(null);
        h.assertTrue(dropped != null && dropped.process().quenchTicks > 15 && dropped.process().dips == 2, "The dropped piece keeps its quench");
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void ironSwordFromBilletToCollection(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        SmithingRecipe recipe = SmithingData.current().recipesProducing(Items.IRON_SWORD).get(0);
        java.util.concurrent.atomic.AtomicInteger events = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<com.otectus.immersivesmithing.api.event.ItemSmithedEvent> listener = e -> {
            if (e.getEntity() != player) return;
            events.incrementAndGet();
            h.assertTrue(e.getCompletedAt() == com.otectus.immersivesmithing.api.event.ItemSmithedEvent.Station.ANVIL
                    && e.getTroughPos().equals(h.absolutePos(SmithingRun.TROUGH)) && e.getCompletionPos().equals(h.absolutePos(SmithingRun.ANVIL)),
                    "Completed at the anvil, quenched at the trough");
            e.getItem().getOrCreateTag().putBoolean("ListenerMark", true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            ItemStack sword = SmithingRun.smith(h, player, recipe, ItemStack.EMPTY);
            QualityData q = QualityData.get(sword).orElseThrow();
            h.assertTrue(q.forgeScore() == 100 && q.anvilScore() == 100 && !q.faulty(), "Careful work is Masterwork, got " + q.forgeScore() + "/" + q.anvilScore());
            h.assertTrue(events.get() == 1, "ItemSmithedEvent fired exactly once, got " + events.get());
            h.assertTrue(sword.getTag() != null && sword.getTag().getBoolean("ListenerMark"), "A listener's change survives collection");
            h.assertTrue(player.getInventory().countItem(Items.STICK) == 0 && player.getInventory().countItem(Items.IRON_SWORD) == 1,
                    "One sword, its stick fitted, nothing duplicated");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void grindstonePassChargesOnlyOnCompletion(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState());
        var stone = (com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        new QualityData(60, 60, false).apply(sword);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.giveExperienceLevels(30);
        GrindstoneActions.use(player, stone, InteractionHand.MAIN_HAND, player.getMainHandItem());
        h.assertTrue(!stone.piece().isEmpty() && player.getMainHandItem().isEmpty(), "The piece rests at the wheel");
        int levels = player.experienceLevel;
        GrindstoneActions.hold(player, stone);
        for (int i = 0; i < stone.passTicks() / 2; i++) tickStone(h, stone, player);
        GrindstoneActions.stop(player, stone);
        h.assertTrue(player.experienceLevel == levels && QualityData.get(stone.piece()).orElseThrow().forgeScore() == 60, "An interrupted pass costs and gives nothing");
        GrindstoneActions.hold(player, stone);
        for (int i = 0; i <= stone.passTicks(); i++) tickStone(h, stone, player);
        int cost = com.otectus.immersivesmithing.block.SmithsGrindstoneBlock.levelCost(60);
        h.assertTrue(player.experienceLevel == levels - cost && QualityData.get(stone.piece()).orElseThrow().forgeScore() == 65,
                "A full pass charges once and refines once");
        player.experienceLevel = 0;
        GrindstoneActions.hold(player, stone);
        h.assertTrue(stone.contact() == null, "Without the levels the pass does not start");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        GrindstoneActions.take(player, stone);
        h.assertTrue(stone.piece().isEmpty() && find(player, Items.IRON_SWORD).is(Items.IRON_SWORD), "Taken back");
        h.succeed();
    }

    private static void tickStone(GameTestHelper h, com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity stone, FakePlayer player) {
        if (stone.contact() != null) GrindstoneActions.hold(player, stone);
        com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity.serverTick(h.getLevel(), stone.getBlockPos(), stone.getBlockState(), stone);
    }

    private static ItemStack find(FakePlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return player.getInventory().getItem(i);
        }
        return ItemStack.EMPTY;
    }
}
