package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ISmithingCostModifier;
import com.otectus.immersivesmithing.api.ISmithingRecipeGate;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.block.SmithsGrindstoneBlock;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingData;
import com.otectus.immersivesmithing.network.packet.GrindstonePreviewPacket;
import com.otectus.immersivesmithing.quality.GrindstonePreview;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.ForgeRecipeSelection;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.registry.ModBlocks;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.WorkpieceState;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

/** Regression coverage for discounted metal, authoritative availability reasons and refinement quotes. */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CostRegressionGameTests {
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron");

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void discountedForgingCannotMintMetal(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        SmithingRecipe recipe = SmithingData.current().recipesProducing(Items.IRON_CHESTPLATE).get(0);
        ISmithingCostModifier half = new ISmithingCostModifier() {
            @Override public int metalUnits(ServerPlayer smith, SmithingRecipe r, int units) { return units / 2; }
        };
        var originalConfig = TestSupport.isolateServerConfig();
        ItemStack piece;
        try {
            ServerConfig.RECYCLING_EFFICIENCY.set(1.0);
            ImmersiveSmithingAPI.registerCostModifier(half);
            piece = SmithingRun.smith(h, player, recipe, ItemStack.EMPTY);
            ImmersiveSmithingAPI.unregisterCostModifier(half);
            SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(SmithingRun.FORGE));
            int paid = recipe.metalUnits() - forge.moltenUnits();
            h.assertTrue(paid == 36, "The full server smithing flow charged 36 of the initial 72 units");
            ItemStack restored = ItemStack.of(piece.save(new CompoundTag()));
            h.assertTrue(MaterialResolver.recyclingValue(restored).orElseThrow().units() == paid,
                    "The paid amount survives quenching, save/load and removal of the discount");
            h.assertTrue(forge.insertMetal(restored, 1, false, null) == 1 && forge.depositUnits() == paid,
                    "Depositing the finished piece returns exactly the charged metal");
            ServerConfig.RECYCLING_EFFICIENCY.set(0.5);
            h.assertTrue(MaterialResolver.resolve(restored).orElseThrow().unitsEach() == 18, "Efficiency applies after the paid cap");
            h.assertTrue(MaterialResolver.recyclingValue(new ItemStack(Items.IRON_CHESTPLATE)).orElseThrow().units() == 72,
                    "Legacy and found pieces without a paid record retain their normal value");
            RecyclingData.record(restored, IRON, 500);
            h.assertTrue(MaterialResolver.recyclingValue(restored).orElseThrow().units() == 72, "The record cannot raise the normal value");
            RecyclingData.record(restored, new ResourceLocation("minecraft", "gold"), 36);
            h.assertTrue(MaterialResolver.recyclingValue(restored).isEmpty(), "A mismatched record cannot create another metal");
        } finally {
            ImmersiveSmithingAPI.unregisterCostModifier(half);
            TestSupport.restoreServerConfig(originalConfig);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingRepairCannotWashOffDiscounts(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), h.absolutePos(TestSupport.STATION)));
        var originalConfig = TestSupport.isolateServerConfig();
        try {
            ServerConfig.INHERIT_QUALITY_ON_CRAFT.set(false);
            for (int slot = 1; slot <= 2; slot++) {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.setDamageValue(150);
                new QualityData(80, 80, false).apply(sword);
                RecyclingData.record(sword, IRON, 2);
                menu.getSlot(slot).set(sword);
            }
            ItemStack preview = menu.getSlot(0).getItem();
            h.assertTrue(preview.is(Items.IRON_SWORD) && !QualityData.has(preview), "Repair still respects disabled quality inheritance");
            h.assertTrue(MaterialResolver.recyclingValue(preview).orElseThrow().units() == 4,
                    "Repairing two discounted swords cannot turn their four units into eighteen");
            ItemStack taken = menu.quickMoveStack(player, 0);
            h.assertTrue(MaterialResolver.recyclingValue(taken).orElseThrow().units() == 4, "Shift-click preserves the preview's metal cap");
        } finally {
            TestSupport.restoreServerConfig(originalConfig);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeSmithingKeepsPaidMetal(GameTestHelper h) {
        ResourceLocation netherite = new ResourceLocation("minecraft", "netherite");
        ItemStack base = new ItemStack(Items.NETHERITE_SWORD);
        RecyclingData.record(base, netherite, 9);
        var remake = new SmithingTransformRecipe(ImmersiveSmithing.id("test/paid_remake"), Ingredient.of(Items.PAPER),
                Ingredient.of(Items.NETHERITE_SWORD), Ingredient.of(Items.DIAMOND), new ItemStack(Items.NETHERITE_AXE));
        ItemStack made = remake.assemble(new SimpleContainer(new ItemStack(Items.PAPER), base, new ItemStack(Items.DIAMOND)), h.getLevel().registryAccess());
        h.assertTrue(MaterialResolver.recyclingValue(made).orElseThrow().units() == 9, "Native tag-copying upgrades retain the cap");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyRecipeReasonUsesDiscountedCost(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        player.getInventory().clearContent();
        SmithingRecipe shovel = SmithingData.current().recipesProducing(Items.IRON_SHOVEL).get(0);
        h.setBlock(TestSupport.STATION, ModBlocks.SMITHS_FORGE.get().defaultBlockState());
        SmithsForgeBlockEntity forge = (SmithsForgeBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(TestSupport.STATION));
        forge.acceptWorkpiece(new WorkpieceData(shovel.result().copy(), shovel.getId(), IRON, 1, 0, -1, false,
                WorkpieceState.FORGED, shovel.anvilPattern()));
        ISmithingCostModifier discount = new ISmithingCostModifier() {
            @Override public int metalUnits(ServerPlayer smith, SmithingRecipe r, int units) { return r == shovel ? 1 : units; }
        };
        ISmithingRecipeGate gate = (smith, recipe) -> recipe == shovel ? Optional.of(Component.literal("Smithing level 10 required")) : Optional.empty();
        try {
            ImmersiveSmithingAPI.registerCostModifier(discount);
            Component missing = ForgeRecipeSelection.whyNone(player, forge, 0);
            h.assertTrue(missing.getContents() instanceof TranslatableContents t && t.getKey().equals("message.immersive_smithing.missing_auxiliary"),
                    "One unit is enough for the discounted shovel: explain the missing sticks, not metal");
            player.getInventory().setItem(9, new ItemStack(Items.STICK, 2));
            ImmersiveSmithingAPI.registerRecipeGate(gate);
            h.assertTrue(ForgeRecipeSelection.eligible(player, forge, 0).isEmpty(), "The gate blocks the only affordable recipe");
            h.assertTrue(ForgeRecipeSelection.whyNone(player, forge, 0).getString().equals("Smithing level 10 required"),
                    "With components present, report the gate's actual reason");
        } finally {
            ImmersiveSmithingAPI.unregisterRecipeGate(gate);
            ImmersiveSmithingAPI.unregisterCostModifier(discount);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void refinementQuoteMatchesChargeAndSurvivesNetwork(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        FakePlayer other = TestSupport.player(h);
        h.setBlock(TestSupport.STATION, ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState());
        BlockPos pos = h.absolutePos(TestSupport.STATION);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        new QualityData(40, 80, false).apply(sword);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        other.setItemInHand(InteractionHand.MAIN_HAND, sword.copy());
        player.giveExperienceLevels(20);
        ISmithingCostModifier discount = new ISmithingCostModifier() {
            @Override public int refinementLevelCost(ServerPlayer smith, ItemStack piece, int levels) {
                return smith == player && piece.is(Items.IRON_SWORD) ? levels / 2 : levels;
            }
        };
        try {
            ImmersiveSmithingAPI.registerCostModifier(discount);
            CompoundTag before = sword.save(new CompoundTag());
            GrindstonePreview preview = GrindstonePreview.forPlayer(player, pos).orElseThrow();
            h.assertTrue(preview.durabilityCost() == 1 && preview.efficacyCost() == 2, "Both prices include the requesting player's discount");
            h.assertTrue(GrindstonePreview.forPlayer(other, pos).orElseThrow().durabilityCost() == 3, "Another player's price stays undiscounted");
            h.assertTrue(before.equals(sword.save(new CompoundTag())) && player.experienceLevel == 20, "Quoting neither changes the piece nor charges XP");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            GrindstonePreview decoded;
            try {
                new GrindstonePreviewPacket(preview).encode(buffer);
                decoded = GrindstonePreviewPacket.decode(buffer).preview();
            } finally {
                buffer.release();
            }
            h.assertTrue(decoded.matches(player.level().dimension().location(), pos, sword)
                    && decoded.durabilityCost() == preview.durabilityCost() && decoded.efficacyCost() == preview.efficacyCost(),
                    "The overlay receives the exact quote and item identity over the wire");
            h.assertTrue(!decoded.matches(new ResourceLocation("minecraft", "the_nether"), pos, sword)
                    && !decoded.matches(player.level().dimension().location(), pos.above(), sword)
                    && !decoded.matches(player.level().dimension().location(), pos, new ItemStack(Items.IRON_AXE)), "Stale context never matches");
            TestSupport.use(h, player, 0.8); // rests the sword at the wheel
            var stone = (com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity) h.getLevel().getBlockEntity(pos);
            h.assertTrue(GrindstonePreview.forPlayer(player, pos).orElseThrow().durabilityCost() == decoded.durabilityCost(), "The resting piece is quoted the same");
            com.otectus.immersivesmithing.work.GrindstoneActions.hold(player, stone);
            for (int i = 0; i <= stone.passTicks(); i++) {
                if (stone.contact() != null) com.otectus.immersivesmithing.work.GrindstoneActions.hold(player, stone);
                com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity.serverTick(h.getLevel(), pos, stone.getBlockState(), stone);
            }
            h.assertTrue(player.experienceLevel == 20 - decoded.durabilityCost(), "The actual refinement charges the displayed price");
            h.assertTrue(!decoded.matches(player.level().dimension().location(), pos, stone.piece()), "Refining invalidates the old quote");
            com.otectus.immersivesmithing.work.GrindstoneActions.take(player, stone);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            h.assertTrue(GrindstonePreview.forPlayer(player, pos).orElseThrow().durabilityCost()
                    == SmithsGrindstoneBlock.levelCost(player, player.getMainHandItem(), 50), "Ungraded pieces use the discounted neutral price");
            h.assertTrue(!QualityData.has(player.getMainHandItem()), "Quoting an ungraded piece does not grade it");
            h.assertTrue(GrindstonePreview.forPlayer(player, pos.above()).isEmpty(), "Only an actual nearby grindstone can supply a quote");
        } finally {
            ImmersiveSmithingAPI.unregisterCostModifier(discount);
        }
        h.succeed();
    }

    /** These synchronous tests change settings in memory; autosaving the world's config races Forge's file watcher. */

    private CostRegressionGameTests() {}
}
