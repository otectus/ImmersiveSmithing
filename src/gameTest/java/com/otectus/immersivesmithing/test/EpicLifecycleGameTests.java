package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.minigame.ForgeSession;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.network.packet.OpenForgeScreenPacket;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.*;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EpicLifecycleGameTests {
    private static SmithingRecipe upgrade() {
        return SmithingData.current().recipesProducing(BuiltInRegistries.ITEM.get(new ResourceLocation("magistuarmory:knight_chestplate")))
                .stream().filter(r -> new ResourceLocation("magistuarmory:knight_chestplate").equals(r.sourceRecipe())).findFirst().orElseThrow();
    }

    @GameTest(template = "empty")
    public static void completeUpgradeUsesEscrowMetadataAndTheNewSmith(GameTestHelper h) {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        SmithingRecipe recipe = upgrade();
        FakePlayer originalMaker = TestSupport.player(h), smith = TestSupport.player(h);
        ItemStack base = recipe.auxiliary().stream().filter(AuxiliaryIngredient::consumesEquipment).findFirst().orElseThrow().displayStack();
        EpicNativeGameTests.personalize(base, originalMaker);
        base.getOrCreateTag().putString("DoNotTransferUnknownData", "private upstream state");
        ItemStack expected = ResultAssembler.assemble(recipe, List.of(base)).orElseThrow();
        ItemStack result = SmithingRun.smith(h, smith, recipe, base);
        for (String key : List.of("display", "Enchantments", "Trim", "RepairCost", "Damage")) {
            h.assertTrue(java.util.Objects.equals(expected.getTag().get(key), result.getTag().get(key)), "Full supported base field retained: " + key);
        }
        h.assertTrue(!result.getTag().contains("DoNotTransferUnknownData"), "Target does not acquire arbitrary upstream/capability state");
        h.assertTrue(result.getTagElement(QualityData.ROOT).getDouble(QualityData.CARRY) == .375, "Fractional wear retained");
        h.assertTrue(MakersMark.get(result).orElseThrow().smithId().equals(smith.getUUID())
                && !MakersMark.get(result).orElseThrow().signed(), "Structural work gets the completing smith's unsigned mark");
        h.assertTrue(QualityData.get(result).orElseThrow().forgeScore() == 100, "New minigame scores apply");
        h.assertTrue(smith.getInventory().countItem(base.getItem()) == 0, "Base consumed once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyRefundBoundaryReturnsTheCompleteReservedBaseOnce(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        var level = h.getLevel(); var pos = h.absolutePos(TestSupport.STATION);
        for (String boundary : List.of("cancel", "logout", "death", "distance", "break", "chunk-restart")) {
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
            h.setBlock(TestSupport.STATION, net.minecraft.world.level.block.Blocks.AIR);
            h.setBlock(TestSupport.STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
            SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity)level.getBlockEntity(pos);
            SmithingRecipe recipe = upgrade(); forge.refundCast(recipe.metalUnits(), recipe.family());
            FakePlayer player = TestSupport.player(h);
            ItemStack base = recipe.auxiliary().get(0).displayStack(); EpicNativeGameTests.personalize(base, player);
            ItemStack expected = base.copy();
            player.getInventory().setItem(9, base);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
            forge.setPlan(recipe.getId());
            com.otectus.immersivesmithing.work.ForgeActions.commit(player, forge, InteractionHand.MAIN_HAND);
            h.assertTrue(player.getInventory().countItem(expected.getItem()) == 0 && forge.job() != null && forge.job().escrow.size() == 1,
                    "Actual base reserved: " + boundary);
            // Another smith cannot cancel the owner's job (or take its reservation) while the owner is around.
            FakePlayer intruder = TestSupport.player(h);
            intruder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.tongs(SmithingTier.NETHERITE)));
            com.otectus.immersivesmithing.work.ForgeActions.cancel(intruder, forge);
            com.otectus.immersivesmithing.work.ForgeActions.cancel(intruder, forge);
            h.assertTrue(forge.job() != null && forge.job().escrow.size() == 1, "Other player cannot steal or release escrow");
            switch (boundary) {
                case "cancel" -> { }
                case "logout" -> { SessionManager.onLogout(player); com.otectus.immersivesmithing.work.WorkLeases.onLogout(player); }
                case "death" -> { SessionManager.onDeath(player); com.otectus.immersivesmithing.work.WorkLeases.onDeath(player); }
                case "distance" -> player.setPos(player.getX() + 100, player.getY(), player.getZ());
                case "break" -> h.setBlock(TestSupport.STATION, net.minecraft.world.level.block.Blocks.AIR);
                default -> {
                    CompoundTag saved = forge.saveWithFullMetadata();
                    forge.onChunkUnloaded(); level.removeBlockEntity(pos);
                    forge = (SmithsForgeBlockEntity)BlockEntity.loadStatic(pos, level.getBlockState(pos), saved);
                    level.setBlockEntity(forge);
                    SmithsForgeBlockEntity.serverTick(level, pos, level.getBlockState(pos), forge);
                    SmithsForgeBlockEntity.serverTick(level, pos, level.getBlockState(pos), forge);
                    h.assertTrue(forge.job() != null && forge.job().escrow.size() == 1, "The committed job survives a restart");
                }
            }
            // A committed job is durable: whatever happened, its owner (back in reach) cancels it, confirmed, exactly once.
            if (!boundary.equals("break")) {
                player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
                com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge);
                com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge);
                com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge); // a stale repeat cannot refund again
                com.otectus.immersivesmithing.work.ForgeActions.cancel(player, forge);
            }
            List<ItemStack> returned = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i); if (s.is(expected.getItem())) returned.add(s);
            }
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).stream().map(ItemEntity::getItem)
                    .filter(s -> s.is(expected.getItem())).forEach(returned::add);
            h.assertTrue(returned.size() == 1 && ItemStack.matches(expected, returned.get(0)), "Exactly one full base returned: " + boundary);
            if (!boundary.equals("break")) {
                h.assertTrue(forge.job() == null && forge.moltenUnits() == recipe.metalUnits(), "Escrow cleared, unspent metal retained: " + boundary);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeAndPreviewWireFormatsAndOldWorkpiecesKeepTheirMeaning(GameTestHelper h) {
        var recipe = SmithingData.current().recipesProducing(Items.IRON_SWORD).get(0);
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new SmithingRecipeSerializer().toNetwork(buffer, recipe);
            var decoded = new SmithingRecipeSerializer().fromNetwork(recipe.getId(), buffer);
            h.assertTrue(decoded.recoverableUnits() == recipe.recoverableUnits() && decoded.keepsBaseData() == recipe.keepsBaseData(), "Recipe policy and recovery survive sync");
            var options = List.of(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.BIRCH_PLANKS, 2));
            var entry = new OpenForgeScreenPacket.Entry(recipe.getId(), recipe.result(), 18, List.of(options.get(0)), 7, 0,
                    Component.empty(), List.of(options), true);
            var packet = new OpenForgeScreenPacket(7, TestSupport.STATION, Component.literal("Iron"), 27, recipe.getId(), List.of(entry), List.of());
            packet.encode(buffer); var copy = OpenForgeScreenPacket.decode(buffer);
            h.assertTrue(copy.entries().get(0).alternatives().get(0).size() == 2 && copy.entries().get(0).upgrade(), "Complete alternatives and upgrade role survive sync");
            h.assertTrue(buffer.readableBytes() == 0, "Wire decoders consume exactly their payloads");
            var wp = TestSupport.workpiece(new ItemStack(Items.IRON_SWORD), 80, -1, false,
                    com.otectus.immersivesmithing.workpiece.WorkpieceState.FORGED);
            CompoundTag old = WorkpieceCodec.save(wp); old.remove("RecoverableUnits"); old.putInt("Version", 1);
            h.assertTrue(WorkpieceCodec.load(old).orElseThrow().recoverableUnits() == wp.metalUnits(), "Old workpiece defaults to its saved paid units");
        } finally { buffer.release(); }
        h.succeed();
    }
}
