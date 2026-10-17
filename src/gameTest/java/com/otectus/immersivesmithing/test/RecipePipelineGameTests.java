package com.otectus.immersivesmithing.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.loot.ForgedLootModifier;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.recipe.AutoRecipeDetector;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.CompatibilityReport;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.recipe.SourceRecipes;
import com.otectus.immersivesmithing.registry.ModBlocks;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The recipe pipeline in any pack: per-recipe suppression, custom recipe classes left alone, a rebuild that neither
 * duplicates nor loses recipes, reworked pieces keeping exactly the data their native recipe keeps, inherited quality in
 * the crafting preview, the grindstone's neutral start and loot grading by eligibility.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipePipelineGameTests {

    private static Set<ResourceLocation> managerIds(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
    }

    @GameTest(template = "empty")
    public static void rebuildIsRepeatableAndKeepsOriginals(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Set<ResourceLocation> before = managerIds(server);
        Set<ResourceLocation> autoBefore = SmithingData.current().recipes().stream().filter(SmithingRecipe::isAuto)
                .map(SmithingRecipe::getId).collect(Collectors.toSet());
        SmithingData.rebuild(server);
        SmithingData.rebuild(server);
        h.assertTrue(managerIds(server).equals(before), "Rebuilding twice changes nothing in the recipe manager");
        h.assertTrue(server.getRecipeManager().getRecipes().size() == before.size(), "No generated recipe is duplicated");
        ResourceLocation ironSword = new ResourceLocation("minecraft", "iron_sword");
        h.assertTrue(server.getRecipeManager().byKey(ironSword).isEmpty(), "The vanilla iron sword recipe is disabled");
        CommentedConfig config = TestSupport.isolateServerConfig();
        try {
            ServerConfig.REPLACE_METAL_EQUIPMENT_RECIPES.set(false);
            SmithingData.rebuild(server);
            h.assertTrue(server.getRecipeManager().byKey(ironSword).isPresent(), "Turning replacement off brings the original back without /reload");
        } finally {
            ServerConfig.REPLACE_METAL_EQUIPMENT_RECIPES.set(true);
            SmithingData.rebuild(server);
            TestSupport.restoreServerConfig(config);
        }
        h.assertTrue(managerIds(server).equals(before), "And turning it on again restores the same set");
        Set<ResourceLocation> autoAfter = SmithingData.current().recipes().stream().filter(SmithingRecipe::isAuto)
                .map(SmithingRecipe::getId).collect(Collectors.toSet());
        h.assertTrue(autoAfter.equals(autoBefore), "Automatic recipes are regenerated, never added twice");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyTheReplacedRecipeIsDisabled(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        SmithingData data = SmithingData.current();
        h.assertTrue(server.getRecipeManager().byKey(new ResourceLocation("minecraft", "golden_sword")).isEmpty(),
                "The gold recipe of the golden sword is replaced by its gold forge recipe");
        ResourceLocation alt = new ResourceLocation("immersive_smithing_test", "alt_metal_golden_sword");
        h.assertTrue(server.getRecipeManager().byKey(alt).isPresent(), "A golden sword made from iron has no forge equivalent and stays");
        h.assertTrue(data.retainedRecipes().containsKey(alt) && data.retainedRecipes().get(alt).contains("minecraft:iron"),
                "The kept recipe is reported with its reason: " + data.retainedRecipes().get(alt));
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void customUpgradeClassesAreNeverConverted(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        var vanilla = new SmithingTransformRecipe(ImmersiveSmithing.id("test_vanilla_upgrade"), Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_SWORD));
        // A subclass with its own assembly (like a reroll that clears a rolled power).
        var custom = new SmithingTransformRecipe(ImmersiveSmithing.id("test_custom_upgrade"), Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_AXE), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_AXE)) {
            @Override
            public ItemStack assemble(net.minecraft.world.Container container, net.minecraft.core.RegistryAccess access) {
                return new ItemStack(Items.NETHERITE_AXE);
            }
        };
        h.assertTrue(SourceRecipes.kind(vanilla) == SourceRecipes.Kind.SMITHING_UPGRADE, "Vanilla upgrade is adaptable");
        h.assertTrue(SourceRecipes.kind(custom) == SourceRecipes.Kind.CUSTOM, "A subclass is custom");
        CompatibilityReport report = new CompatibilityReport();
        var context = new AutoRecipeDetector.UpgradeContext(List.<Recipe<?>>of(vanilla, custom), server.registryAccess(),
                SmithingData.current().materials(), new java.util.HashMap<>());
        var generated = AutoRecipeDetector.generateUpgrades(List.<Recipe<?>>of(vanilla, custom), context,
                new AutoRecipeDetector.Scope(Set.of(), Set.of()), report);
        h.assertTrue(generated.size() == 1 && generated.get(0).resultItem() == Items.NETHERITE_SWORD, "Only the vanilla upgrade is converted");
        h.assertTrue(report.retainedById().containsKey(custom.getId()), "The custom upgrade is reported as kept");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void clothBasesAreReworkedNotPricedAsMetal(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Recipe<?> leatherBoots = server.getRecipeManager().byKey(new ResourceLocation("minecraft", "leather_boots")).orElseThrow();
        Recipe<?> diamondSword = server.getRecipeManager().byKey(new ResourceLocation("minecraft", "diamond_sword")).orElseThrow();
        // A cloth piece made from a leather piece (Iron's Spells' wandering magician boots are leather boots and arcane
        // essence) has no metal shape: its upgrade reworks it for the addition alone, the native recipe's own price.
        var cloth = new ShapelessRecipe(ImmersiveSmithing.id("test_cloth_chestplate"), "", CraftingBookCategory.EQUIPMENT,
                new ItemStack(Items.LEATHER_CHESTPLATE), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.LEATHER_BOOTS), Ingredient.of(Items.RABBIT_HIDE)));
        var clothUpgrade = new SmithingTransformRecipe(ImmersiveSmithing.id("test_cloth_upgrade"), Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.LEATHER_CHESTPLATE), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.CHAINMAIL_CHESTPLATE));
        // A diamond piece nested the same way keeps its shape (Epic Fight's diamond longsword is a diamond sword and two diamonds).
        var gem = new ShapelessRecipe(ImmersiveSmithing.id("test_gem_axe"), "", CraftingBookCategory.EQUIPMENT, new ItemStack(Items.DIAMOND_AXE),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.DIAMOND), Ingredient.of(Items.DIAMOND)));
        var gemUpgrade = new SmithingTransformRecipe(ImmersiveSmithing.id("test_gem_upgrade"), Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(Items.DIAMOND_AXE), Ingredient.of(Items.NETHERITE_INGOT), new ItemStack(Items.NETHERITE_AXE));
        List<Recipe<?>> source = List.of(leatherBoots, diamondSword, cloth, clothUpgrade, gem, gemUpgrade);
        var context = new AutoRecipeDetector.UpgradeContext(source, server.registryAccess(), SmithingData.current().materials(), new java.util.HashMap<>());
        var generated = AutoRecipeDetector.generateUpgrades(source, context, new AutoRecipeDetector.Scope(Set.of(), Set.of()), new CompatibilityReport());
        SmithingRecipe chainmail = generated.stream().filter(r -> r.resultItem() == Items.CHAINMAIL_CHESTPLATE).findFirst().orElseThrow();
        h.assertTrue(chainmail.metalUnits() == 9 && chainmail.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment),
                "The cloth piece is reworked for one ingot, its leather never priced as metal: " + chainmail.metalUnits() + " units");
        SmithingRecipe axe = generated.stream().filter(r -> r.resultItem() == Items.NETHERITE_AXE).findFirst().orElseThrow();
        h.assertTrue(axe.metalUnits() == 36 && axe.auxiliary().stream().noneMatch(AuxiliaryIngredient::consumesEquipment),
                "A diamond sword and two diamonds keep their shape: " + axe.metalUnits() + " units");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void reworkedPieceKeepsWhatItsNativeRecipeKeeps(GameTestHelper h) {
        FakePlayer smith = TestSupport.player(h);
        // A smithing-table upgrade hands the base piece's data to the result: so does the forge.
        SmithingRecipe turtle = SmithingData.current().recipesProducing(Items.TURTLE_HELMET).get(0);
        h.assertTrue(turtle.keepsBaseData() && turtle.auxiliary().stream().anyMatch(a -> a.consumesEquipment()), "The upgrade reworks its base");
        ItemStack base = new ItemStack(Items.CHAINMAIL_HELMET);
        base.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        base.setHoverName(Component.literal("Old Faithful"));
        base.setDamageValue(base.getMaxDamage() - 2);
        new QualityData(5, 5, true).apply(base);
        MakersMark.stamp(base, TestSupport.player(h));
        ItemStack helmet = SmithingRun.smith(h, smith, turtle, base);
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, helmet) == 3, "Enchantment kept");
        h.assertTrue(helmet.getHoverName().getString().equals("Old Faithful"), "Name kept");
        h.assertTrue(helmet.getDamageValue() > 0 && helmet.getDamageValue() < helmet.getMaxDamage(), "Damage carried over, never past breaking");
        QualityData quality = QualityData.get(helmet).orElseThrow();
        h.assertTrue(!quality.faulty() && quality.forgeScore() == 100 && quality.anvilScore() == 100, "The new forging decides the quality, not the old one");
        h.assertTrue(MakersMark.get(helmet).orElseThrow().smithId().equals(smith.getUUID()), "The new smith's mark");
        h.assertTrue(smith.getInventory().countItem(Items.CHAINMAIL_HELMET) == 0, "The reworked piece was consumed exactly once");

        // A plain crafting recipe makes a fresh piece: the forge does not add the old piece's enchantments.
        SmithingRecipe chain = SmithingData.current().recipesProducing(Items.CHAINMAIL_CHESTPLATE).get(0);
        h.assertTrue(!chain.keepsBaseData(), "A plain crafting rework starts fresh");
        ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
        chest.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 2);
        ItemStack made = SmithingRun.smith(h, TestSupport.player(h), chain, chest);
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, made) == 0 && made.getDamageValue() == 0,
                "Fresh piece, as the crafting recipe would make it");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingPreviewShowsInheritedQuality(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        BlockPos pos = h.absolutePos(TestSupport.STATION);
        CraftingMenu menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        ItemStack graded = new ItemStack(Items.IRON_SWORD);
        graded.setDamageValue(100);
        new QualityData(90, 80, false).apply(graded);
        MakersMark.stamp(graded, player);
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        worn.setDamageValue(150);
        menu.getSlot(1).set(graded);
        menu.getSlot(2).set(worn);
        ItemStack preview = menu.getSlot(0).getItem();
        h.assertTrue(preview.is(Items.IRON_SWORD), "Two worn swords repair into one");
        QualityData shown = QualityData.get(preview).orElse(null);
        h.assertTrue(shown != null && shown.forgeScore() == 90 && shown.anvilScore() == 80, "The preview already shows the inherited quality");
        h.assertTrue(MakersMark.get(preview).isPresent(), "And the maker's mark");
        ItemStack taken = menu.quickMoveStack(player, 0);
        h.assertTrue(QualityData.get(taken).map(q -> q.forgeScore() == 90).orElse(false), "Shift-click takes exactly what was shown");
        // Two graded inputs: no honest answer.
        ItemStack other = new ItemStack(Items.IRON_SWORD);
        new QualityData(10, 10, false).apply(other);
        menu.getSlot(1).set(graded.copy());
        menu.getSlot(2).set(other);
        h.assertTrue(!QualityData.has(menu.getSlot(0).getItem()), "Two graded inputs: nothing is inherited");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void grindstoneStartsUngradedPiecesNeutral(GameTestHelper h) {
        FakePlayer player = TestSupport.player(h);
        h.setBlock(TestSupport.STATION, ModBlocks.SMITHS_GRINDSTONE.get().defaultBlockState());
        player.giveExperienceLevels(20);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        TestSupport.use(h, player, 0.8);
        h.assertTrue(!QualityData.has(player.getMainHandItem()) && player.experienceLevel == 20, "Asking costs nothing and writes nothing");
        TestSupport.use(h, player, 0.8);
        QualityData refined = QualityData.get(player.getMainHandItem()).orElse(null);
        int base = QualityEligibility.NEUTRAL_SCORE;
        h.assertTrue(refined != null && refined.forgeScore() == base + ServerConfig.get(ServerConfig.GRINDSTONE_SCORE_STEP)
                && refined.anvilScore() == base && !refined.faulty(), "Confirmed: the neutral grade plus one refinement");
        h.assertTrue(player.experienceLevel == 20 - com.otectus.immersivesmithing.block.SmithsGrindstoneBlock.levelCost(base), "Paid for it");
        h.assertTrue(MakersMark.get(player.getMainHandItem()).isEmpty(), "Refining never invents a maker");
        // Equipment that cannot carry quality is left alone.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        TestSupport.use(h, player, 0.8);
        TestSupport.use(h, player, 0.8);
        h.assertTrue(!QualityData.has(player.getMainHandItem()), "A diamond sword is not smithing work");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void lootIsGradedByEligibilityOnce(GameTestHelper h) {
        ForgedLootModifier modifier = new ForgedLootModifier(new LootItemCondition[0]);
        LootContext context = new LootContext.Builder(new LootParams.Builder(h.getLevel()).create(LootContextParamSets.EMPTY)).create(null);
        context.setQueriedLootTableId(new ResourceLocation("minecraft", "chests/simple_dungeon"));
        ItemStack forged = new ItemStack(Items.IRON_SWORD);
        ItemStack diamond = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack graded = new ItemStack(Items.IRON_AXE);
        new QualityData(3, 4, false).apply(graded);
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>(List.of(forged, diamond, graded));
        modifier.apply(loot, context);
        h.assertTrue(QualityData.has(forged), "Forge-made loot weapons are graded (tagged loot weapons: WeaponCompatGameTests)");
        h.assertTrue(!QualityData.has(diamond), "Equipment that cannot carry quality stays ungraded");
        QualityData kept = QualityData.get(graded).orElseThrow();
        h.assertTrue(kept.forgeScore() == 3 && kept.anvilScore() == 4, "An already graded stack is never overwritten");
        for (ItemStack stack : loot) h.assertTrue(MakersMark.get(stack).isEmpty(), "Loot never gets a maker's mark");
        h.succeed();
    }

    private RecipePipelineGameTests() {}
}
