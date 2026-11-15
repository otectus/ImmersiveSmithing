package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.work.AnvilActions;
import com.otectus.immersivesmithing.work.ProcessProfile;
import com.otectus.immersivesmithing.work.ProcessProfiles;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/** Persistence of the frozen workpiece presentation after fitting turns it into an anvil output. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnvilVisualGameTests {
    private static final ResourceLocation CUSTOM_VISUAL = ImmersiveSmithing.id("test_custom_blade");
    private static final ResourceLocation NEXT_VISUAL = ImmersiveSmithing.id("test_next_output");

    @GameTest(template = "empty")
    public static void fitCompletionKeepsCustomVisualAndFlippedFace(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        SmithsAnvilBlockEntity anvil = placeAnvil(h);
        anvil.setWorkpiece(cooledPiece());

        AnvilActions.fit(player, anvil, 0, 1);

        h.assertTrue(anvil.workpiece() == null && anvil.output().is(Items.IRON_SWORD),
                "Fitting the final socket completes the real output");
        h.assertTrue(CUSTOM_VISUAL.equals(anvil.outputVisual()),
                "The completed output keeps the process snapshot's explicit visual id");
        h.assertTrue(anvil.outputFlipped(), "The completed output keeps the face that was upward during fitting");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void outputPresentationRoundTripsDiskAndUpdateTag(GameTestHelper h) {
        SmithsAnvilBlockEntity anvil = placeAnvil(h);
        anvil.setOutput(new ItemStack(Items.IRON_SWORD), CUSTOM_VISUAL, true);

        CompoundTag saved = anvil.saveWithFullMetadata();
        SmithsAnvilBlockEntity loaded = (SmithsAnvilBlockEntity) BlockEntity.loadStatic(
                anvil.getBlockPos(), anvil.getBlockState(), saved);
        h.assertTrue(loaded != null && loaded.output().is(Items.IRON_SWORD)
                        && CUSTOM_VISUAL.equals(loaded.outputVisual()) && loaded.outputFlipped(),
                "Disk NBT restores the output stack, visual id and flipped face");

        CompoundTag update = anvil.getUpdateTag();
        h.assertTrue(CUSTOM_VISUAL.toString().equals(update.getString("OutputVisual")) && update.getBoolean("OutputFlipped"),
                "The update tag carries both presentation fields");
        SmithsAnvilBlockEntity client = new SmithsAnvilBlockEntity(anvil.getBlockPos(), anvil.getBlockState());
        ClientboundBlockEntityDataPacket packet = (ClientboundBlockEntityDataPacket) anvil.getUpdatePacket();
        client.onDataPacket(null, packet);
        h.assertTrue(client.output().is(Items.IRON_SWORD) && CUSTOM_VISUAL.equals(client.outputVisual()) && client.outputFlipped(),
                "The actual block-entity update packet restores the same presentation on a detached client copy");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyOutputWithoutPresentationStillLoads(GameTestHelper h) {
        SmithsAnvilBlockEntity anvil = placeAnvil(h);
        CompoundTag legacy = new CompoundTag();
        legacy.putString("id", "immersive_smithing:smiths_anvil");
        legacy.put("Output", new ItemStack(Items.IRON_SWORD).save(new CompoundTag()));

        SmithsAnvilBlockEntity loaded = (SmithsAnvilBlockEntity) BlockEntity.loadStatic(
                anvil.getBlockPos(), anvil.getBlockState(), legacy);
        h.assertTrue(loaded != null && loaded.output().is(Items.IRON_SWORD),
                "An output saved before presentation metadata remains collectible");
        h.assertTrue(loaded.outputVisual() == null && !loaded.outputFlipped(),
                "Missing legacy metadata uses the renderer fallback and unflipped default");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void collectionClearsPresentationBeforeNextOutput(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        SmithsAnvilBlockEntity anvil = placeAnvil(h);
        anvil.setOutput(new ItemStack(Items.IRON_SWORD), CUSTOM_VISUAL, true);

        AnvilActions.collect(player, anvil);

        h.assertTrue(!anvil.hasOutput() && anvil.outputVisual() == null && !anvil.outputFlipped(),
                "Collecting clears the output and both presentation fields");
        boolean collected = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.IRON_SWORD)) collected = true;
        }
        h.assertTrue(collected,
                "The cleared metadata does not come from losing the collected item");
        anvil.setOutput(new ItemStack(Items.IRON_AXE), NEXT_VISUAL, false);
        h.assertTrue(anvil.output().is(Items.IRON_AXE) && NEXT_VISUAL.equals(anvil.outputVisual()) && !anvil.outputFlipped(),
                "A later output starts with only its own presentation metadata");
        h.succeed();
    }

    private static SmithsAnvilBlockEntity placeAnvil(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        return (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
    }

    private static WorkpieceData cooledPiece() {
        ProcessProfile base = ProcessProfiles.GENERIC;
        ProcessProfile custom = new ProcessProfile(ImmersiveSmithing.id("test_custom_process"), CUSTOM_VISUAL, base.heat(),
                base.operations(), base.quenchTicks(), base.immersionMin(), List.of("guard"), List.of());
        ProcessState process = ProcessState.fresh(custom, ThermalProfile.DEFAULT, 1000,
                List.of(new ProcessState.Socket("guard", List.of(new ItemStack(Items.STICK)), false)));
        process.stage = WorkStage.COOLED;
        process.flipped = true;
        return new WorkpieceData(new ItemStack(Items.IRON_SWORD), ImmersiveSmithing.id("test_visual_recipe"),
                new ResourceLocation("minecraft", "iron"), 18, 80, 80, false, ImmersiveSmithing.id("sword"), 18, process);
    }

    private AnvilVisualGameTests() {}
}
