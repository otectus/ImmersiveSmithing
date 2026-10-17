package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.otectus.immersivesmithing.test.TestSupport.STATION;

/**
 * Client coherence of the stations. Each test builds the real update packet the server sends after a change and
 * applies it to a detached copy of the block entity through {@code onDataPacket}, the path a client takes, so an
 * update that a client would drop (an empty tag becomes a null packet tag) leaves the copy stale and fails here.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationSyncGameTests {

    private static void deliver(BlockEntity server, BlockEntity client) {
        ClientboundBlockEntityDataPacket packet = (ClientboundBlockEntityDataPacket) server.getUpdatePacket();
        client.onDataPacket(null, packet);
    }

    @GameTest(template = "empty")
    public static void anvilClientClearsAfterTongsPickup(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(STATION, ModBlocks.SMITHS_ANVIL.get().defaultBlockState());
        SmithsAnvilBlockEntity anvil = (SmithsAnvilBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        SmithsAnvilBlockEntity client = new SmithsAnvilBlockEntity(anvil.getBlockPos(), anvil.getBlockState());
        ItemStack tongs = new ItemStack(ModItems.tongs(SmithingTier.IRON));
        WorkpieceCodec.setHeld(tongs, TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, -1, false, WorkpieceState.FORGED));
        player.setItemInHand(InteractionHand.MAIN_HAND, tongs);

        for (int round = 1; round <= 2; round++) {
            TestSupport.use(h, player, 0.9);
            deliver(anvil, client);
            h.assertTrue(anvil.hasWorkpiece() && client.hasWorkpiece() && !WorkpieceCodec.isHolding(tongs),
                    "Round " + round + ": the placed workpiece reaches the client and leaves the tongs");
            TestSupport.use(h, player, 0.9);
            deliver(anvil, client);
            h.assertTrue(!anvil.hasWorkpiece() && WorkpieceCodec.isHolding(tongs), "Round " + round + ": empty tongs take the workpiece");
            h.assertTrue(!client.hasWorkpiece(), "Round " + round + ": the client's anvil is empty after the pickup");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forgeClientClearsWhenDepositsLeave(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        SmithsForgeBlockEntity client = new SmithsForgeBlockEntity(forge.getBlockPos(), forge.getBlockState());
        forge.insertMetal(new ItemStack(Items.IRON_INGOT, 3), 3, false, new SmithsForgeBlockEntity.Result[1]);
        deliver(forge, client);
        h.assertTrue(client.depositUnits() == forge.depositUnits() && client.depositUnits() > 0, "Deposits reach the client");
        while (forge.canExtractDeposits() && !forge.extractLastDeposit().isEmpty()) {
            deliver(forge, client);
        }
        h.assertTrue(forge.depositUnits() == 0 && client.depositUnits() == 0 && client.deposits().isEmpty(),
                "The client's forge is empty once every deposit is taken back");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void troughClientTracksWaterBothWays(GameTestHelper h) {
        h.setBlock(STATION, ModBlocks.SMITHS_TROUGH.get().defaultBlockState());
        SmithsTroughBlockEntity trough = (SmithsTroughBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(STATION));
        SmithsTroughBlockEntity client = new SmithsTroughBlockEntity(trough.getBlockPos(), trough.getBlockState());
        trough.addBucket();
        deliver(trough, client);
        h.assertTrue(client.water() == trough.water() && client.water() > 0, "Water reaches the client");
        while (trough.takeBucket()) deliver(trough, client);
        deliver(trough, client);
        h.assertTrue(trough.water() == 0 && client.water() == 0, "The client's trough empties with the server's");
        h.succeed();
    }
}
