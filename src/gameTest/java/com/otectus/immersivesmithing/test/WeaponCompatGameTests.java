package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.loot.ForgedLootModifier;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityCalculator;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.quality.QualityInheritance;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Epic Fight and Simply Swords against the real mods (-PepicFight, -PsimplySwords, or both; CoverageGameTests compares
 * their catalogs with the checked-in inventories): costs derived from each mod's own recipes, complete forge runs that produce the mod's own
 * items, quality in real combat exactly once (vanilla and Epic Fight battle mode, main hand and offhand, thrown spears,
 * weapon abilities), native runic upgrades and rerolls, loot weapons, the integration switches and recycling. Every
 * test passes trivially when its mod is absent, so the plain profile proves nothing here links against them.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WeaponCompatGameTests {
    private static final String EF = "epicfight";
    private static final String SS = "simplyswords";

    private static boolean loaded(String mod) {
        return ModList.get().isLoaded(mod);
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (item == Items.AIR) throw new IllegalStateException(id + " is not registered");
        return item;
    }

    private static ItemStack graded(String id, int score, boolean faulty) {
        ItemStack stack = new ItemStack(item(id));
        new QualityData(score, score, faulty).apply(stack);
        return stack;
    }

    // ------------------------------------------------------------------ costs from each mod's own recipes

    private static SmithingRecipe only(GameTestHelper h, String id) {
        List<SmithingRecipe> recipes = SmithingData.current().recipesProducing(item(id));
        h.assertTrue(recipes.size() == 1, id + " has exactly one forge recipe, got " + recipes.stream().map(SmithingRecipe::getId).toList());
        return recipes.get(0);
    }

    private static void costs(GameTestHelper h, String id, String family, int units, Object... components) {
        SmithingRecipe r = only(h, id);
        h.assertTrue(r.family().toString().equals(family) && r.metalUnits() == units,
                id + " costs " + units + " " + family + ", got " + r.metalUnits() + " " + r.family());
        h.assertTrue(r.auxiliary().size() == components.length / 2, id + " has " + components.length / 2 + " components, got " + r.auxiliary().size());
        for (int i = 0; i < components.length; i += 2) {
            Item component = (Item) components[i];
            int count = (Integer) components[i + 1];
            boolean found = false;
            for (AuxiliaryIngredient aux : r.auxiliary()) {
                if (aux.ingredient().test(new ItemStack(component)) && aux.count() == count) found = true;
            }
            h.assertTrue(found, id + " needs " + count + "x " + BuiltInRegistries.ITEM.getKey(component) + ", has " + r.auxiliary());
        }
    }

    private static void notForged(GameTestHelper h, String id) {
        h.assertTrue(SmithingData.current().recipesProducing(item(id)).isEmpty(), id + " is not forged");
    }

    private static void shape(GameTestHelper h, String id, String pattern) {
        ResourceLocation got = com.otectus.immersivesmithing.minigame.EquipmentClassifier.classify(new ItemStack(item(id)));
        h.assertTrue(got.equals(ImmersiveSmithing.id(pattern)), id + " uses the " + pattern + " pattern, got " + got);
        h.assertTrue(com.otectus.immersivesmithing.minigame.PatternRegistry.hasAnvilPattern(got), pattern + " pattern is defined");
    }

    private static boolean present(GameTestHelper h, String recipe) {
        return h.getLevel().getServer().getRecipeManager().byKey(new ResourceLocation(recipe)).isPresent();
    }

    @GameTest(template = "empty")
    public static void epicFightCostsFollowItsRecipes(GameTestHelper h) {
        if (!loaded(EF)) {
            h.succeed();
            return;
        }
        costs(h, "epicfight:iron_greatsword", "minecraft:iron", 54, Items.OAK_PLANKS, 1);
        costs(h, "epicfight:golden_greatsword", "minecraft:gold", 54, Items.OAK_PLANKS, 1);
        // An iron sword and two ingots: the sword's own 18 units and stick, plus 18.
        costs(h, "epicfight:iron_longsword", "minecraft:iron", 36, Items.STICK, 1);
        costs(h, "epicfight:iron_tachi", "minecraft:iron", 36, Items.STICK, 1);
        costs(h, "epicfight:golden_longsword", "minecraft:gold", 36, Items.STICK, 1);
        // An iron sword set on two sticks.
        costs(h, "epicfight:iron_spear", "minecraft:iron", 18, Items.STICK, 3);
        costs(h, "epicfight:iron_dagger", "minecraft:iron", 9, Items.STICK, 1);
        costs(h, "epicfight:glove", "minecraft:iron", 2, Items.LEATHER, 2);
        // Netherite: the diamond piece's whole shape in netherite, template-free (diamond longsword = sword + 2 diamonds).
        costs(h, "epicfight:netherite_longsword", "minecraft:netherite", 36, Items.STICK, 1);
        costs(h, "epicfight:netherite_spear", "minecraft:netherite", 18, Items.STICK, 3);
        costs(h, "epicfight:netherite_greatsword", "minecraft:netherite", 54, Items.OAK_PLANKS, 1);
        for (String tier : List.of("wooden", "stone", "diamond")) {
            for (String type : List.of("greatsword", "longsword", "tachi", "dagger", "spear")) notForged(h, "epicfight:" + tier + "_" + type);
        }
        // The uchigatana gets a recipe of its own (Epic Fight ships none): a diamond-stage katana on an iron blade.
        costs(h, "epicfight:uchigatana", "minecraft:iron", 36, Items.DIAMOND, 1, Items.LEATHER, 2, Items.STICK, 1);
        // The bokken is wooden: a crafting recipe, not smithing work.
        notForged(h, "epicfight:bokken");
        var bokken = h.getLevel().getServer().getRecipeManager().byKey(ImmersiveSmithing.id("compat/epicfight/bokken")).orElse(null);
        h.assertTrue(bokken != null && bokken.getResultItem(h.getLevel().registryAccess()).is(item("epicfight:bokken")), "The bokken can be crafted");
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        }, 3, 3);
        grid.setItem(2, new ItemStack(Items.OAK_PLANKS));
        grid.setItem(4, new ItemStack(Items.OAK_PLANKS));
        grid.setItem(6, new ItemStack(Items.STICK));
        var matches = h.getLevel().getServer().getRecipeManager().getRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, h.getLevel());
        h.assertTrue(matches.size() == 1 && matches.get(0).getId().equals(bokken.getId()),
                "Two planks on a stick make only the bokken: " + matches.stream().map(Recipe::getId).toList());
        h.assertTrue(!present(h, "epicfight:iron_longsword") && !present(h, "epicfight:netherite_longsword"), "Replaced recipes are disabled");
        h.assertTrue(present(h, "epicfight:iron_spear") && present(h, "epicfight:diamond_longsword"), "The sword-on-a-shaft and diamond recipes stay");
        shape(h, "epicfight:iron_greatsword", "greatsword");
        shape(h, "epicfight:iron_longsword", "longsword");
        shape(h, "epicfight:iron_tachi", "katana");
        shape(h, "epicfight:uchigatana", "katana");
        shape(h, "epicfight:iron_dagger", "dagger");
        shape(h, "epicfight:iron_spear", "spear");
        shape(h, "epicfight:glove", "cestus");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simplySwordsCostsFollowItsRecipes(GameTestHelper h) {
        if (!loaded(SS)) {
            h.succeed();
            return;
        }
        costs(h, "simplyswords:iron_longsword", "minecraft:iron", 18, Items.STICK, 1);
        costs(h, "simplyswords:iron_chakram", "minecraft:iron", 31, Items.STICK, 1);
        // Gold weapons with iron-nugget fittings: gold stock, nuggets as components.
        costs(h, "simplyswords:gold_claymore", "minecraft:gold", 18, Items.IRON_NUGGET, 4, Items.STICK, 1);
        costs(h, "simplyswords:gold_halberd", "minecraft:gold", 27, Items.IRON_NUGGET, 1, Items.STICK, 2);
        costs(h, "simplyswords:gold_katana", "minecraft:gold", 18, Items.STICK, 1);
        // Netherite: the diamond piece's shape in netherite, fittings kept.
        costs(h, "simplyswords:netherite_chakram", "minecraft:netherite", 27, Items.IRON_NUGGET, 4, Items.STICK, 1);
        costs(h, "simplyswords:netherite_longsword", "minecraft:netherite", 18, Items.STICK, 1);
        costs(h, "simplyswords:sword_on_a_stick", "minecraft:iron", 18, Items.STICK, 2, Items.STRING, 1);
        for (String type : List.of("chakram", "claymore", "cutlass", "greataxe", "greathammer", "halberd", "warglaive", "longsword")) {
            notForged(h, "simplyswords:diamond_" + type);
            h.assertTrue(present(h, "simplyswords:diamond_" + type), "Diamond " + type + " keeps its crafting recipe");
        }
        for (String type : List.of("longsword", "chakram", "twinblade")) {
            notForged(h, "simplyswords:runic_" + type);
            h.assertTrue(present(h, "simplyswords:runic_" + type) && present(h, "simplyswords:runic2_" + type), "Runic upgrade and reroll stay");
        }
        notForged(h, "simplyswords:brimstone_claymore");
        h.assertTrue(!present(h, "simplyswords:netherite_longsword"), "The netherite upgrade is replaced");
        h.assertTrue(present(h, "simplyswords:sword_on_a_stick"), "The sword on a stick can still be made from a longsword");
        shape(h, "simplyswords:iron_twinblade", "twinblade");
        shape(h, "simplyswords:iron_warglaive", "warglaive");
        shape(h, "simplyswords:iron_chakram", "chakram");
        shape(h, "simplyswords:iron_sai", "parrying_dagger");
        shape(h, "simplyswords:iron_cutlass", "saber");
        shape(h, "simplyswords:iron_claymore", "greatsword");
        shape(h, "simplyswords:iron_greataxe", "battleaxe");
        shape(h, "simplyswords:iron_greathammer", "battle_hammer");
        shape(h, "simplyswords:iron_glaive", "glaive");
        shape(h, "simplyswords:iron_rapier", "rapier");
        shape(h, "simplyswords:iron_scythe", "scythe");
        shape(h, "simplyswords:iron_halberd", "halberd");
        shape(h, "simplyswords:iron_katana", "katana");
        shape(h, "simplyswords:watching_warglaive", "warglaive");
        h.succeed();
    }

    // ------------------------------------------------------------------ complete runs

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void weaponsAreForgedIntoTheModsOwnItems(GameTestHelper h) {
        List<String> ids = new ArrayList<>(List.of("minecraft:iron_sword"));
        if (loaded(EF)) ids.addAll(List.of("epicfight:iron_longsword", "epicfight:iron_spear", "epicfight:netherite_greatsword", "epicfight:glove",
                "epicfight:uchigatana"));
        if (loaded(SS)) ids.addAll(List.of("simplyswords:iron_twinblade", "simplyswords:gold_claymore", "simplyswords:netherite_chakram",
                "simplyswords:iron_warglaive", "simplyswords:iron_sai"));
        for (String id : ids) {
            FakePlayer smith = TestSupport.player(h);
            SmithingRecipe recipe = only(h, id);
            ItemStack done = SmithingRun.smith(h, smith, recipe, ItemStack.EMPTY);
            h.assertTrue(done.is(item(id)) && done.getCount() == 1, "Forged " + id);
            QualityData quality = QualityData.get(done).orElse(null);
            h.assertTrue(quality != null && quality.forgeScore() == 100 && quality.anvilScore() == 100, id + " carries the run's scores");
            h.assertTrue(MakersMark.get(done).map(m -> m.smithId().equals(smith.getUUID())).orElse(false), id + " carries the smith's mark");
            for (AuxiliaryIngredient aux : recipe.auxiliary()) {
                h.assertTrue(smith.getInventory().countItem(aux.displayStack().getItem()) == 0, id + " consumed its components");
            }
            h.assertTrue(com.otectus.immersivesmithing.minigame.PatternRegistry.hasAnvilPattern(recipe.anvilPattern()),
                    id + " is shaped on a real pattern: " + recipe.anvilPattern());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ combat

    private static final int[] SCORES = {0, 50, 100};

    @GameTest(template = "empty")
    public static void meleeQualityAppliesExactlyOnce(GameTestHelper h) {
        List<String> ids = new ArrayList<>(List.of("minecraft:iron_sword"));
        // The glove deals little damage of its own: quality still scales it exactly once.
        if (loaded(EF)) ids.addAll(List.of("epicfight:iron_longsword", "epicfight:netherite_greatsword", "epicfight:glove", "epicfight:uchigatana"));
        if (loaded(SS)) ids.addAll(List.of("simplyswords:iron_katana", "simplyswords:brimstone_claymore"));
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        LivingEntity target = TestSupport.dummy(h);
        // Simply Swords' implicits (a katana's random double damage) are the mod's own effect: held off while measuring.
        Runnable attack = loaded(SS) ? () -> SimplySwordsTestSupport.withoutImplicits(() -> player.attack(target)) : () -> player.attack(target);
        for (String id : ids) {
            ItemStack plain = new ItemStack(item(id));
            TestSupport.hold(player, plain, ItemStack.EMPTY);
            float base = TestSupport.damageOf(player, target, attack);
            h.assertTrue(base > 1F, id + " hits for its own damage, got " + base);
            for (int score : SCORES) check(h, player, target, id, base, new QualityData(score, score, false), attack);
            check(h, player, target, id, base, new QualityData(50, 50, true), attack);
        }
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    private static void check(GameTestHelper h, TestSupport.TestPlayer player, LivingEntity target, String id, float base, QualityData quality,
                              Runnable attack) {
        ItemStack stack = new ItemStack(item(id));
        quality.apply(stack);
        TestSupport.hold(player, stack, ItemStack.EMPTY);
        float dealt = TestSupport.damageOf(player, target, attack);
        double expected = base * QualityCalculator.efficacyMultiplier(quality);
        h.assertTrue(Math.abs(dealt - expected) < 0.02, id + " at " + quality + " deals " + expected + ", got " + dealt);
    }

    @GameTest(template = "empty")
    public static void epicFightBattleHitsUseEachHandsOwnQuality(GameTestHelper h) {
        if (!loaded(EF)) {
            h.succeed();
            return;
        }
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        LivingEntity target = TestSupport.dummy(h);
        EpicFightTestSupport.battleMode(player);
        double m = QualityCalculator.efficacyMultiplier(new QualityData(100, 100, false));
        ItemStack plain = new ItemStack(item("epicfight:iron_dagger"));
        ItemStack fine = graded("epicfight:iron_dagger", 100, false);

        TestSupport.hold(player, plain.copy(), plain.copy());
        float mainBase = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, InteractionHand.MAIN_HAND));
        h.assertTrue(mainBase > 1F, "Battle-mode hit lands, got " + mainBase);
        TestSupport.hold(player, fine.copy(), plain.copy());
        float mainFine = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, InteractionHand.MAIN_HAND));
        h.assertTrue(Math.abs(mainFine - mainBase * m) < 0.05, "Main hand: x" + m + " once, got " + mainFine / mainBase);

        h.assertTrue(EpicFightTestSupport.offhandAttacksAllowed(player), "Two daggers allow offhand attacks");
        TestSupport.hold(player, plain.copy(), plain.copy());
        float offBase = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, InteractionHand.OFF_HAND));
        TestSupport.hold(player, plain.copy(), fine.copy());
        float offFine = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, InteractionHand.OFF_HAND));
        h.assertTrue(Math.abs(offFine - offBase * m) < 0.05, "Offhand: the offhand dagger's own quality, x" + m + ", got " + offFine / offBase);
        TestSupport.hold(player, fine.copy(), plain.copy());
        float leak = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, InteractionHand.OFF_HAND));
        h.assertTrue(Math.abs(leak - offBase) < 0.05, "The main hand's quality never reaches an offhand hit: " + leak + " vs " + offBase);
        double mainAttribute = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        h.assertTrue(Math.abs(mainAttribute - mainBase * m) < 0.05, "After the offhand hit the main hand's modifiers are back: " + mainAttribute);

        // Vanilla (mining) mode: the same weapon through the vanilla attack.
        EpicFightTestSupport.vanillaMode(player);
        TestSupport.hold(player, plain.copy(), ItemStack.EMPTY);
        float vanilla = TestSupport.damageOf(player, target, () -> player.attack(target));
        TestSupport.hold(player, fine.copy(), ItemStack.EMPTY);
        float vanillaFine = TestSupport.damageOf(player, target, () -> player.attack(target));
        h.assertTrue(Math.abs(vanillaFine - vanilla * m) < 0.05, "Vanilla mode: x" + m + " once, got " + vanillaFine / vanilla);
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simplySwordsThrownSpearCarriesQualityOnce(GameTestHelper h) {
        if (!loaded(SS)) {
            h.succeed();
            return;
        }
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        QualityData fine = new QualityData(100, 100, false);
        double m = QualityCalculator.efficacyMultiplier(fine);
        TestSupport.hold(player, new ItemStack(item("simplyswords:iron_spear")), ItemStack.EMPTY);
        Entity plainSpear = SimplySwordsTestSupport.throwSpear(h.getLevel(), player, InteractionHand.MAIN_HAND);
        float plain = SimplySwordsTestSupport.launchDamage(plainSpear);
        plainSpear.discard();
        ItemStack graded = graded("simplyswords:iron_spear", 100, false);
        TestSupport.hold(player, graded, ItemStack.EMPTY);
        Entity fineSpear = SimplySwordsTestSupport.throwSpear(h.getLevel(), player, InteractionHand.MAIN_HAND);
        float launched = SimplySwordsTestSupport.launchDamage(fineSpear);
        // Simply Swords sums the stack's attack-damage modifiers at launch, so the quality modifier is in there once.
        double expected = plain + (plain + 1.0) * (m - 1.0);
        h.assertTrue(Math.abs(launched - expected) < 0.02, "Spear launch damage " + expected + ", got " + launched);
        h.assertTrue(!fineSpear.getPersistentData().getBoolean(ImmersiveSmithing.MOD_ID + ":efficacy_scaled"),
                "The projectile hook leaves weapon-scaled spears alone");
        fineSpear.discard();
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void simplySwordsAbilityDamageScalesOnce(GameTestHelper h) {
        if (!loaded(SS)) {
            h.succeed();
            return;
        }
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        QualityData fine = new QualityData(100, 100, false);
        TestSupport.hold(player, new ItemStack(item("simplyswords:brimstone_claymore")), ItemStack.EMPTY);
        float plain = SimplySwordsTestSupport.abilityDamage(player, 1.0F);
        ItemStack graded = new ItemStack(item("simplyswords:brimstone_claymore"));
        fine.apply(graded);
        TestSupport.hold(player, graded, ItemStack.EMPTY);
        float scaled = SimplySwordsTestSupport.abilityDamage(player, 1.0F);
        double m = QualityCalculator.efficacyMultiplier(fine);
        h.assertTrue(plain > 0 && Math.abs(scaled / plain - m) < 0.01, "Attack-scaled ability damage follows quality once: x" + scaled / plain);
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    // ------------------------------------------------------------------ native transformations

    @GameTest(template = "empty")
    public static void runicUpgradeAndRerollKeepSmithingData(GameTestHelper h) {
        if (!loaded(SS)) {
            h.succeed();
            return;
        }
        var server = h.getLevel().getServer();
        var upgrade = (SmithingTransformRecipe) server.getRecipeManager().byKey(new ResourceLocation(SS, "runic_longsword")).orElseThrow();
        var reroll = (SmithingTransformRecipe) server.getRecipeManager().byKey(new ResourceLocation(SS, "runic2_longsword")).orElseThrow();
        FakePlayer smith = TestSupport.player(h);
        ItemStack base = new ItemStack(item("simplyswords:netherite_longsword"));
        new QualityData(80, 90, false).apply(base);
        MakersMark.stamp(base, smith);
        base.enchant(Enchantments.SHARPNESS, 2);
        base.setHoverName(Component.literal("Oathkeeper"));
        base.setDamageValue(100);
        SimpleContainer table = new SimpleContainer(new ItemStack(item("simplyswords:runic_tablet")), base, new ItemStack(Items.DIAMOND));
        h.assertTrue(upgrade.matches(table, h.getLevel()), "The runic upgrade takes the graded netherite longsword");
        ItemStack runic = upgrade.assemble(table, server.registryAccess());
        h.assertTrue(runic.is(item("simplyswords:runic_longsword")), "Runic longsword");
        QualityData kept = QualityData.get(runic).orElse(null);
        h.assertTrue(kept != null && kept.forgeScore() == 80 && kept.anvilScore() == 90, "Quality carried through the runic upgrade");
        h.assertTrue(MakersMark.get(runic).map(m -> m.smithId().equals(smith.getUUID())).orElse(false), "Maker's mark carried");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SHARPNESS, runic) == 2 && runic.getDamageValue() == 100
                && runic.getHoverName().getString().equals("Oathkeeper"), "Enchantment, damage and name carried");

        SimplySwordsTestSupport.giveRunicPower(runic, new ResourceLocation(SS, "freeze"));
        h.assertTrue(SimplySwordsTestSupport.hasGemPower(runic), "Runic power rolled");
        SimpleContainer again = new SimpleContainer(new ItemStack(item("simplyswords:runic_tablet")), runic, new ItemStack(Items.DIAMOND));
        h.assertTrue(reroll.matches(again, h.getLevel()), "The reroll takes the runic longsword");
        ItemStack rerolled = reroll.assemble(again, server.registryAccess());
        h.assertTrue(!SimplySwordsTestSupport.hasGemPower(rerolled), "The reroll clears the power so it rolls again");
        h.assertTrue(QualityData.get(rerolled).map(q -> q.forgeScore() == 80).orElse(false), "Quality survives the reroll");
        h.assertTrue(!QualityInheritance.inherit(rerolled, again) && !SimplySwordsTestSupport.hasGemPower(rerolled),
                "No inheritance hook brings the cleared power back");
        RecyclingValue base18 = MaterialResolver.recyclingValue(new ItemStack(item("simplyswords:netherite_longsword"))).orElseThrow();
        RecyclingValue runic18 = MaterialResolver.recyclingValue(rerolled).orElseThrow();
        h.assertTrue(runic18.family().equals(base18.family()) && runic18.units() == base18.units(),
                "A runic weapon melts for its netherite base's metal only: " + runic18.units());
        h.succeed();
    }

    // ------------------------------------------------------------------ loot weapons

    @GameTest(template = "empty")
    public static void lootWeaponsAreGradedButNeverForged(GameTestHelper h) {
        List<String> loot = new ArrayList<>();
        List<String> plain = new ArrayList<>(List.of("minecraft:diamond_sword"));
        if (loaded(EF)) {
            plain.addAll(List.of("epicfight:bokken", "epicfight:diamond_longsword"));
        }
        if (loaded(SS)) {
            loot.addAll(List.of("simplyswords:brimstone_claymore", "simplyswords:watching_warglaive", "simplyswords:runic_longsword"));
            plain.addAll(List.of("simplyswords:diamond_longsword", "simplyswords:diamond_chakram"));
        }
        ForgedLootModifier modifier = new ForgedLootModifier(new LootItemCondition[0]);
        LootContext context = new LootContext.Builder(new LootParams.Builder(h.getLevel()).create(LootContextParamSets.EMPTY)).create(null);
        context.setQueriedLootTableId(new ResourceLocation("minecraft", "chests/simple_dungeon"));
        for (String id : loot) {
            notForged(h, id);
            ItemStack found = new ItemStack(item(id));
            ItemStack owned = graded(id, 7, false);
            modifier.apply(new ObjectArrayList<>(List.of(found, owned)), context);
            QualityData q = QualityData.get(found).orElse(null);
            h.assertTrue(q != null && q.forgeScore() >= 35 && q.forgeScore() <= 69, id + " found in loot is graded Standard");
            h.assertTrue(QualityData.get(owned).map(o -> o.forgeScore() == 7).orElse(false), id + " already graded is untouched");
            h.assertTrue(MakersMark.get(found).isEmpty(), id + " gets no maker");
            h.assertTrue(MaterialResolver.recyclingValue(new ItemStack(item(id))).isEmpty() || id.contains("runic"), id + " is not recyclable");
        }
        for (String id : plain) {
            ItemStack stack = new ItemStack(item(id));
            h.assertTrue(!QualityEligibility.canCarryQuality(stack), id + " is not smithing work");
            modifier.apply(new ObjectArrayList<>(List.of(stack)), context);
            h.assertTrue(!QualityData.has(stack), id + " found in loot stays ungraded");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ integration switches

    @GameTest(template = "empty")
    public static void integrationSwitchesLeaveTheModAsShipped(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        record Case(String mod, ForgeConfigSpec.BooleanValue option, String nativeRecipe, String eligible) {}
        List<Case> cases = new ArrayList<>();
        if (loaded(EF)) cases.add(new Case(EF, ServerConfig.EPIC_FIGHT_INTEGRATION, "epicfight:iron_longsword", "epicfight:uchigatana"));
        if (loaded(SS)) cases.add(new Case(SS, ServerConfig.SIMPLY_SWORDS_INTEGRATION, "simplyswords:netherite_longsword", "simplyswords:brimstone_claymore"));
        var config = TestSupport.isolateServerConfig();
        try {
            for (Case c : cases) {
                Set<ResourceLocation> before = server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
                ItemStack owned = new ItemStack(item(c.eligible()));
                new QualityData(100, 100, false).apply(owned);
                try {
                    c.option().set(false);
                    SmithingData.rebuild(server);
                    long forged = SmithingData.current().recipes().stream()
                            .filter(r -> BuiltInRegistries.ITEM.getKey(r.resultItem()).getNamespace().equals(c.mod())).count();
                    h.assertTrue(forged == 0, c.mod() + " off: nothing of it is forged, got " + forged);
                    h.assertTrue(present(h, c.nativeRecipe()), c.mod() + " off: its own recipe " + c.nativeRecipe() + " works again");
                    h.assertTrue(!QualityEligibility.canCarryQuality(new ItemStack(item(c.eligible()))), c.mod() + " off: its loot weapons stay ungraded");
                    h.assertTrue(QualityData.has(owned) && owned.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND)
                            .get(Attributes.ATTACK_DAMAGE).stream().anyMatch(a -> "Smithing quality".equals(a.getName())),
                            c.mod() + " off: an already graded piece keeps working");
                } finally {
                    c.option().set(true);
                    SmithingData.rebuild(server);
                }
                Set<ResourceLocation> after = server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
                h.assertTrue(after.equals(before), c.mod() + " on again: the same recipes as before, nothing duplicated or lost");
            }
        } finally {
            TestSupport.restoreServerConfig(config);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ recycling

    @GameTest(template = "empty")
    public static void recyclingNeverMintsMetal(GameTestHelper h) {
        for (SmithingRecipe r : SmithingData.current().recipes()) {
            RecyclingValue value = MaterialResolver.recyclingValue(r.result()).orElse(null);
            if (value == null || !value.family().equals(r.family())) continue;
            h.assertTrue(value.units() <= r.metalUnits() || r.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment),
                    BuiltInRegistries.ITEM.getKey(r.resultItem()) + " recycles for " + value.units() + " but costs " + r.metalUnits());
        }
        if (loaded(EF)) {
            int sword = MaterialResolver.recyclingValue(new ItemStack(Items.IRON_SWORD)).orElseThrow().units();
            int spear = MaterialResolver.recyclingValue(new ItemStack(item("epicfight:iron_spear"))).orElseThrow().units();
            h.assertTrue(sword == spear, "A sword set on a shaft melts for the sword's metal");
            RecyclingValue uchigatana = MaterialResolver.recyclingValue(new ItemStack(item("epicfight:uchigatana"))).orElseThrow();
            h.assertTrue(uchigatana.units() == 36 && uchigatana.family().toString().equals("minecraft:iron"),
                    "The uchigatana melts for its iron blade only, not its diamond: " + uchigatana.units());
        }
        if (loaded(SS)) {
            h.assertTrue(MaterialResolver.recyclingValue(new ItemStack(item("simplyswords:brimstone_claymore"))).isEmpty(), "Uniques never melt down");
            h.assertTrue(MaterialResolver.recyclingValue(new ItemStack(item("simplyswords:diamond_chakram"))).isEmpty(), "Diamond pieces are not metal stock");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ both mods

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forgedWeaponsKeepTheirCombatIdentity(GameTestHelper h) {
        if (!loaded(EF)) {
            h.succeed();
            return;
        }
        List<String> ids = new ArrayList<>(List.of("epicfight:iron_longsword"));
        if (loaded(SS)) ids.addAll(List.of("simplyswords:iron_longsword", "simplyswords:iron_spear", "simplyswords:iron_katana"));
        for (String id : ids) {
            ItemStack forged = SmithingRun.smith(h, TestSupport.player(h), only(h, id), ItemStack.EMPTY);
            ItemStack bought = new ItemStack(item(id));
            h.assertTrue(EpicFightTestSupport.category(forged).equals(EpicFightTestSupport.category(bought)),
                    id + " keeps its Epic Fight category: " + EpicFightTestSupport.category(forged));
            var forgedModifiers = forged.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            var boughtModifiers = bought.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            long extra = forgedModifiers.values().stream().filter(a -> !boughtModifiers.containsValue(a)).count();
            long ours = forgedModifiers.values().stream().filter(a -> "Smithing quality".equals(a.getName())).count();
            h.assertTrue(extra == ours && ours <= 1, id + " differs only by the one smithing-quality modifier");
        }
        h.succeed();
    }

    private WeaponCompatGameTests() {}
}
