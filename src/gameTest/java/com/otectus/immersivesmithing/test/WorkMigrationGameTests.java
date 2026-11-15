package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.network.packet.WorkActionPacket;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessProfiles;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ProcessResolver;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.TroughActions;
import com.otectus.immersivesmithing.work.WorkAction;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.work.WorkQuality;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/**
 * Saved-world compatibility of the hands-on overhaul, from fixtures written in the old formats, plus the deterministic
 * rules and the server's validation of reported actions.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkMigrationGameTests {

    /** A workpiece as the 1.0 to 1.2 codecs wrote it (version 1 has no RecoverableUnits). */
    static CompoundTag oldPayload(int version, String state, int forge, int anvil, String item) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", version);
        CompoundTag target = new CompoundTag();
        target.putString("id", item);
        target.putByte("Count", (byte) 1);
        tag.put("TargetStack", target);
        tag.putString("RecipeId", "immersive_smithing:smithing/iron_sword");
        tag.putString("MaterialFamily", "minecraft:iron");
        tag.putInt("MetalUnits", 18);
        if (version >= 2) tag.putInt("RecoverableUnits", 18);
        tag.putInt("ForgeScore", forge);
        tag.putInt("AnvilScore", anvil);
        tag.putBoolean("Faulty", false);
        tag.putString("State", state);
        tag.putString("AnvilPattern", "immersive_smithing:sword");
        return tag;
    }

    @GameTest(template = "empty")
    public static void oldForgedPieceMigratesOnceAndKeepsItsForgeScore(GameTestHelper h) {
        CompoundTag v1 = oldPayload(1, "FORGED", 73, 0, "minecraft:iron_sword");
        WorkpieceCodec.Read first = WorkpieceCodec.read(v1);
        WorkpieceCodec.Read again = WorkpieceCodec.read(v1);
        h.assertTrue(first.ok() && first.migrated(), "A version-1 FORGED piece decodes and is migrated");
        WorkpieceData wp = first.data();
        ProcessState p = wp.process();
        h.assertTrue(p.stage == WorkStage.BILLET && p.has(ProcessState.LEGACY_FORGE) && p.has(ProcessState.COMPONENTS_PAID)
                && !p.has(ProcessState.LEGACY_ANVIL), "Shaped again; its forge score is fixed; its components were paid");
        h.assertTrue(wp.forgeScore() == 73 && wp.anvilScore() == -1 && wp.recoverableUnits() == 18 && p.sockets.isEmpty() && !p.needsFitting(),
                "Stored score, paid metal as recovery cap, nothing to fit");
        h.assertTrue(p.thermal.workable(ThermalRules.coolest(p.heat)), "An old hot piece starts workable");
        h.assertTrue(again.data().id().equals(wp.id()), "Reading the same old payload twice gives the same piece");
        // Written back once in the current schema; reading that is not a migration and changes nothing.
        CompoundTag v3 = WorkpieceCodec.save(wp);
        WorkpieceCodec.Read reread = WorkpieceCodec.read(v3);
        h.assertTrue(v3.getInt("Version") == WorkpieceCodec.VERSION && reread.ok() && !reread.migrated() && reread.data().id().equals(wp.id())
                && reread.data().forgeScore() == 73, "Round trip in schema 3");
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        WorkpieceCodec.setRawHeld(tongs, v1);
        h.assertTrue(WorkpieceCodec.needsUpgrade(tongs), "Tongs with an old payload are upgraded the first time they tick");

        // Shaped anew, its anvil score is new work; quenching never rewrites the stored forge score.
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        anvil.setWorkpiece(wp);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.hammer(SmithingTier.NETHERITE)));
        WorkLeases.Lease lease = WorkLeases.acquire(player, anvil.getBlockPos(), WorkLeases.Station.ANVIL, false);
        for (int blow = 0; blow < 100 && !anvil.workpiece().process().allComplete(); blow++) {
            ProcessState now = anvil.workpiece().process();
            int op = ShapingRules.nextOperation(now);
            SmithingRun.tick(h, anvil, 14);
            if (!now.profile.operations().get(op).face().matches(now.flipped)) {
                AnvilActions.flip(player, anvil, lease, 0);
                continue;
            }
            ProcessState hot = anvil.workpiece().process().copy();
            java.util.Arrays.fill(hot.heat, 6500);
            hot.heatTick = anvil.clock();
            anvil.replaceWorkpiece(anvil.workpiece().withProcess(hot));
            ProcessProfile.Operation o = now.profile.operations().get(op);
            AnvilActions.strike(player, anvil, lease, op, o.centerU(), o.centerV(), o.kind().rough(), blow + 1);
        }
        ProcessState shaped = anvil.workpiece().process();
        h.assertTrue(shaped.stage == WorkStage.SHAPED && WorkQuality.anvilScore(shaped, -1) == 100, "New shaping decides the anvil score");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldShapedPieceOnAnAnvilCompletesWithItsScores(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "immersive_smithing:smiths_anvil");
        saved.put("Workpiece", oldPayload(2, "SHAPED", 73, 88, "minecraft:iron_sword"));
        var pos = h.absolutePos(STATION);
        h.getLevel().removeBlockEntity(pos);
        BlockEntity loaded = BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved);
        h.getLevel().setBlockEntity(loaded);
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(anvil.workpiece() != null && anvil.workpiece().stage() == WorkStage.SHAPED && anvil.workpiece().anvilScore() == 88,
                "The old shaped piece is still shaped with both scores");
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);
        AnvilActions.take(player, anvil, InteractionHand.MAIN_HAND);
        h.setBlock(SmithingRun.TROUGH, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SmithingRun.TROUGH));
        trough.addBucket();
        SmithingRun.quench(h, player, trough);
        ItemStack sword = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(Items.IRON_SWORD)) sword = player.getInventory().getItem(i);
        QualityData q = QualityData.get(sword).orElse(null);
        h.assertTrue(q != null && q.forgeScore() == 73 && q.anvilScore() == 88, "Completed with the stored scores, no fitting, nothing charged twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unreadablePayloadsAreKeptNotErased(GameTestHelper h) {
        CompoundTag future = oldPayload(2, "FORGED", 50, 0, "minecraft:iron_sword");
        future.putInt("Version", 99);
        CompoundTag missing = oldPayload(2, "FORGED", 50, 0, "missingmod:ancient_blade");
        h.assertTrue(!WorkpieceCodec.read(future).ok() && !WorkpieceCodec.read(missing).ok(), "Neither decodes");
        for (CompoundTag raw : List.of(future, missing)) {
            h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
            var pos = h.absolutePos(STATION);
            CompoundTag saved = new CompoundTag();
            saved.putString("id", "immersive_smithing:smiths_anvil");
            saved.put("Workpiece", raw.copy());
            h.getLevel().removeBlockEntity(pos);
            h.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved));
            SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertTrue(anvil.hasUnreadablePiece() && anvil.workpiece() == null && anvil.isOccupied(), "Held as raw data, not usable");
            CompoundTag resaved = anvil.saveWithoutMetadata();
            h.assertTrue(resaved.getCompound("Workpiece").equals(raw), "Saved back byte for byte");
            h.setBlock(STATION, Blocks.AIR);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
            CompoundTag dropped = drops.stream().map(e -> WorkpieceCodec.rawHeld(e.getItem())).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            h.assertTrue(raw.equals(dropped), "Breaking the anvil drops it with the raw data intact");
            drops.forEach(ItemEntity::discard);
        }
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        WorkpieceCodec.setRawHeld(tongs, future);
        h.assertTrue(WorkpieceCodec.isHolding(tongs) && WorkpieceCodec.getHeld(tongs).isEmpty(), "Tongs keep it and are not treated as empty");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldForgeEscrowIsRefundedOnce(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        var pos = h.absolutePos(STATION);
        CompoundTag saved = new CompoundTag();
        saved.putString("id", "immersive_smithing:smiths_forge");
        saved.putString("Family", "minecraft:iron");
        saved.putInt("Molten", 27);
        ListTag escrow = new ListTag();
        escrow.add(new ItemStack(Items.STICK, 2).save(new CompoundTag()));
        saved.put("Escrow", escrow);
        h.getLevel().removeBlockEntity(pos);
        h.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), saved));
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(pos);
        h.assertTrue(forge.job() == null && forge.moltenUnits() == 27, "Old escrow is never mistaken for a committed billet");
        SmithingRun.tick(h, forge, 3);
        CompoundTag after = forge.saveWithFullMetadata();
        h.getLevel().removeBlockEntity(pos);
        h.getLevel().setBlockEntity(BlockEntity.loadStatic(pos, h.getLevel().getBlockState(pos), after));
        SmithingRun.tick(h, (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(pos), 3);
        int sticks = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> e.getItem().is(Items.STICK)).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(sticks == 2, "Refunded at the forge exactly once, got " + sticks);
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).forEach(ItemEntity::discard);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rulesAreDeterministicAndProfilesComplete(GameTestHelper h) {
        ProcessProfile profile = ProcessProfiles.get(ImmersiveSmithing.id("straight_blade"));
        h.assertTrue(profile.id().equals(ImmersiveSmithing.id("straight_blade")), "The bundled straight blade profile is loaded");
        ProcessState a = ProcessState.fresh(profile, ThermalProfile.DEFAULT, 1000, List.of());
        ProcessState b = a.copy();
        int[] heats = {6500, 5000, 7600, 6000, 6900, 5900};
        for (int i = 0; i < 80; i++) {
            int op = ShapingRules.nextOperation(a);
            if (op < 0) break;
            if (!profile.operations().get(op).face().matches(a.flipped)) {
                a.flipped = !a.flipped;
                b.flipped = !b.flipped;
            }
            var ra = ShapingRules.strike(a, op, ShapingRules.Strength.NORMAL, heats[i % heats.length]);
            var rb = ShapingRules.strike(b, op, ShapingRules.Strength.NORMAL, heats[i % heats.length]);
            h.assertTrue(ra.equals(rb), "Same state and blows give the same result");
        }
        h.assertTrue(ShapingRules.shapingQuality(a) == ShapingRules.shapingQuality(b) && a.allComplete(), "Same final quality");
        ProcessState untouched = ProcessState.fresh(profile, ThermalProfile.DEFAULT, 1000, List.of());
        h.assertTrue(WorkQuality.anvilScore(untouched, -1) == 0, "No work is never Masterwork");
        h.assertTrue(ThermalRules.cool(7000, 0, ThermalProfile.DEFAULT, 1.0) == 7000 && ThermalRules.cool(7000, -50, ThermalProfile.DEFAULT, 1.0) == 7000,
                "No elapsed simulation, no cooling (offline and unloaded time never count)");
        // Every bundled anvil pattern resolves to a process through the data, not the generic fallback.
        for (ResourceLocation pattern : com.otectus.immersivesmithing.minigame.PatternRegistry.anvilPatterns().keySet()) {
            h.assertTrue(ProcessProfiles.forPattern(pattern) != null, "Pattern " + pattern + " has a process");
        }
        h.assertTrue(ProcessProfiles.problems().isEmpty(), "Bundled profiles load cleanly: " + ProcessProfiles.problems());
        // Every active recipe has a usable process.
        for (var row : com.otectus.immersivesmithing.work.ProcessReport.rows()) {
            h.assertTrue(!row.profile().getPath().isEmpty() && ProcessProfiles.get(row.profile()).operations().size() > 0, row.recipe() + " has a process");
        }
        // The same coverage as a file, like /immersivesmithing process: logs/immersive_smithing_process_report.txt.
        try {
            com.otectus.immersivesmithing.work.ProcessReport.write(h.getLevel().getServer().getServerDirectory().toPath().resolve("logs"));
        } catch (java.io.IOException e) {
            h.fail("The process report could not be written: " + e.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidProfilesAreRejectedWithTheField(GameTestHelper h) {
        String bad = "{\"operations\":[{\"id\":\"a\",\"kind\":\"draw\",\"work\":2,\"area\":[0.5,0.2,-0.5,0.5]}]}";
        try {
            ProcessProfile.fromJson(ImmersiveSmithing.id("bad"), com.google.gson.JsonParser.parseString(bad).getAsJsonObject());
            h.fail("An inverted area was accepted");
        } catch (com.google.gson.JsonParseException e) {
            h.assertTrue(e.getMessage().contains("operations[0].area"), "The message names the field: " + e.getMessage());
        }
        String unordered = "{\"heat\":{\"optimal_min\":0.9},\"operations\":[{\"id\":\"a\",\"kind\":\"draw\",\"work\":2,\"area\":[0,1,-0.5,0.5]}]}";
        try {
            ProcessProfile.fromJson(ImmersiveSmithing.id("bad"), com.google.gson.JsonParser.parseString(unordered).getAsJsonObject());
            h.fail("Unordered heat bands were accepted");
        } catch (com.google.gson.JsonParseException e) {
            h.assertTrue(e.getMessage().contains("ordered"), "Unordered bands explained: " + e.getMessage());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reportedActionsAreValidated(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        var pos = h.absolutePos(STATION);
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(pos);
        anvil.setWorkpiece(TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 70, -1, false, com.otectus.immersivesmithing.workpiece.WorkpieceState.FORGED));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.hammer(SmithingTier.IRON)));
        int tang = anvil.workpiece().process().profile.operationIndex("form_tang");
        int instance = anvil.instanceId();
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 1, tang, Float.NaN, 0F, 0));
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance + 1, WorkAction.STRIKE, 2, tang, 0.1F, 0F, 0));
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, 200, 3, tang, 0.1F, 0F, 0));
        h.assertTrue(anvil.workpiece().process().blows[tang] == 0, "Non-finite points, a stale station and unknown actions change nothing");
        SmithingRun.tick(h, anvil, 20);
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 10, tang, 0.1F, 0F, 0));
        int blows = anvil.workpiece().process().blows[tang];
        SmithingRun.tick(h, anvil, 20);
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 10, tang, 0.1F, 0F, 0));
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 9, tang, 0.1F, 0F, 0));
        h.assertTrue(blows == 1 && anvil.workpiece().process().blows[tang] == 1, "A replayed or older sequence number is dropped");
        FakePlayer other = TestSupport.player(h);
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.hammer(SmithingTier.IRON)));
        SmithingRun.tick(h, anvil, 20);
        SmithingActionService.handle(other, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 1, tang, 0.1F, 0F, 0));
        h.assertTrue(anvil.workpiece().process().blows[tang] == 1, "A second smith cannot work a station the first one holds");
        player.setPos(pos.getX() + 30, pos.getY(), pos.getZ());
        SmithingRun.tick(h, anvil, 20);
        SmithingActionService.handle(player, new WorkActionPacket(pos, instance, WorkAction.STRIKE, 20, tang, 0.1F, 0F, 0));
        h.assertTrue(anvil.workpiece().process().blows[tang] == 1, "Out of reach of the real player: refused");
        WorkLeases.onLogout(player);
        h.succeed();
    }
}
