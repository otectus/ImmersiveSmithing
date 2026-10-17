package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.minigame.PatternRegistry;
import com.otectus.immersivesmithing.quality.MakersMark;
import com.otectus.immersivesmithing.quality.QualityCalculator;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Epic Knights and its add-ons against the real jars (-PepicKnights, optionally =all,iceandfire and -PepicFight): the
 * built-in costs, complete forge-anvil-trough runs that produce the mods' own items, damage quality on weapons that can
 * also block, gilding, darkening and visor states that keep everything the smith put into a piece, relics that stay
 * unique, plates that melt for their ingot and an economy that never mints metal. Each test passes trivially when the
 * mod it needs is absent.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EpicKnightsGameTests {
    private static final String EK = "magistuarmory";

    private static boolean loaded(String mod) {
        return ModList.get().isLoaded(mod);
    }

    private static Item item(String id) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (item == Items.AIR) throw new IllegalStateException(id + " is not registered");
        return item;
    }

    private static SmithingRecipe only(GameTestHelper h, String id) {
        List<SmithingRecipe> recipes = SmithingData.current().recipesProducing(item(id));
        h.assertTrue(recipes.size() == 1, id + " has exactly one forge recipe, got " + recipes.stream().map(SmithingRecipe::getId).toList());
        return recipes.get(0);
    }

    /** Family, units and every component with its count (components given as item id, count pairs). */
    private static void costs(GameTestHelper h, String id, String family, int units, Object... components) {
        SmithingRecipe r = only(h, id);
        h.assertTrue(r.family().toString().equals(family) && r.metalUnits() == units,
                id + " costs " + units + " " + family + ", got " + r.metalUnits() + " " + r.family());
        h.assertTrue(r.auxiliary().size() == components.length / 2, id + " has " + components.length / 2 + " components, got " + r.auxiliary());
        for (int i = 0; i < components.length; i += 2) {
            Item component = item((String) components[i]);
            int count = (Integer) components[i + 1];
            h.assertTrue(r.auxiliary().stream().anyMatch(a -> a.ingredient().test(new ItemStack(component)) && a.count() == count),
                    id + " needs " + count + "x " + components[i] + ", has " + r.auxiliary());
        }
        h.assertTrue(PatternRegistry.hasAnvilPattern(r.anvilPattern()), id + " uses a defined anvil pattern: " + r.anvilPattern());
    }

    private static void notForged(GameTestHelper h, String id) {
        h.assertTrue(SmithingData.current().recipesProducing(item(id)).isEmpty(), id + " is not forged");
    }

    private static boolean present(GameTestHelper h, String recipe) {
        return h.getLevel().getServer().getRecipeManager().byKey(new ResourceLocation(recipe)).isPresent();
    }

    // ------------------------------------------------------------------ costs

    @GameTest(template = "empty")
    public static void epicKnightsCostsFollowTheirRecipes(GameTestHelper h) {
        if (!loaded(EK)) {
            h.succeed();
            return;
        }
        // A barbute (3 ingots, 2 plates) and three plates; a plate is one ingot (two ingots make two).
        costs(h, "magistuarmory:armet", "forge:steel", 72);
        // A shortsword (two ingots and a hilt) lengthened by one ingot.
        costs(h, "magistuarmory:iron_bastardsword", "minecraft:iron", 27, "magistuarmory:hilt", 1);
        // Four ingots on a wooden kite shield, which is taken whole as the mod's recipe takes it.
        costs(h, "magistuarmory:iron_kiteshield", "minecraft:iron", 36, "magistuarmory:wood_kiteshield", 1);
        // The steel boss of an iron buckler is a fitting: a component, never refunded as iron.
        costs(h, "magistuarmory:iron_buckler", "minecraft:iron", 36, "magistuarmory:steel_ingot", 1);
        // Five chainmails of nine rings at half a nugget each: 22.5 units, rounded down.
        costs(h, "magistuarmory:chainmail_helmet", "forge:steel", 22);
        // Eight lamellar rows: six small plates (3/4 nugget each) and three leather strips per row.
        costs(h, "magistuarmory:lamellar_chestplate", "forge:steel", 36, "magistuarmory:leather_strip", 24);
        // Netherite: the diamond shortsword's shape (two gem slots and a hilt) in netherite, no template.
        costs(h, "magistuarmory:netherite_shortsword", "minecraft:netherite", 18, "magistuarmory:hilt", 1);
        costs(h, "magistuarmory:heavy_crossbow", "forge:steel", 27, "minecraft:string", 2, "minecraft:tripwire_hook", 1, "magistuarmory:pole", 1);
        // A steel flail on an iron head keeps its steel chain as a component.
        costs(h, "magistuarmory:iron_chainmorgenstern", "minecraft:iron", 27, "magistuarmory:steel_chain", 1, "minecraft:stick", 1,
                "magistuarmory:hilt", 1);
        h.assertTrue(only(h, "magistuarmory:iron_chainmorgenstern").anvilPattern().equals(ImmersiveSmithing.id("flail")), "Flails use the flail pattern");
        // Not metal work, transformations and loot keep their own route.
        for (String id : List.of("wood_shortsword", "stone_shortsword", "diamond_shortsword", "noble_sword", "ceremonialarmet", "maximilian_helmet",
                "rustedbarbute", "gambeson_chestplate")) {
            notForged(h, "magistuarmory:" + id);
        }
        h.assertTrue(present(h, "magistuarmory:noble_sword") && present(h, "magistuarmory:ceremonialarmet")
                && present(h, "magistuarmory:diamond_shortsword"), "Gilding and non-metal recipes stay");
        h.assertTrue(!present(h, "magistuarmory:armet") && !present(h, "magistuarmory:iron_bastardsword")
                && !present(h, "magistuarmory:netherite_shortsword_smithing"), "The replaced recipes are disabled");
        // Tin needs another mod's ingots; without them its weapons keep their (uncraftable) recipe and are explained.
        if (SmithingData.current().materials().family(new ResourceLocation("forge", "tin")).isEmpty()) {
            notForged(h, "magistuarmory:tin_shortsword");
            h.assertTrue(present(h, "magistuarmory:tin_shortsword"), "Tin's native recipe is left alone");
            h.assertTrue(SmithingData.current().report().inactiveFor(item("magistuarmory:tin_shortsword")).stream()
                    .anyMatch(r -> r.contains("unknown material family forge:tin")), "and the catalog says tin is missing");
        } else {
            costs(h, "magistuarmory:tin_shortsword", "forge:tin", 18, "magistuarmory:hilt", 1);
        }
        if (loaded("magistuarmoryaddon")) {
            costs(h, "magistuarmoryaddon:steel_longsword", "forge:steel", 28, "magistuarmory:hilt", 2);
            // Gold ingots on a steel helmet stay gold components; the helmet is steel.
            costs(h, "magistuarmoryaddon:lion_helmet", "forge:steel", 72, "minecraft:gold_ingot", 2);
            notForged(h, "magistuarmoryaddon:fully_gilded_armet");
        }
        if (loaded("slavicarmory")) costs(h, "slavicarmory:steel_bardiche", "forge:steel", 27, "magistuarmory:pole", 1);
        if (loaded("epic_knights_ores_and_alloys")) costs(h, "epic_knights_ores_and_alloys:steel_pickaxe", "forge:steel", 27, "minecraft:stick", 2);
        if (loaded("epic_knights_ice_and_fire")) {
            costs(h, "epic_knights_ice_and_fire:dragonsteel_fire_shortsword", "forge:fire_dragonsteel", 18, "iceandfire:witherbone", 1);
            notForged(h, "epic_knights_ice_and_fire:dragon_shortsword");
            notForged(h, "epic_knights_ice_and_fire:dragon_fire_shortsword");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ complete runs

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void epicKnightsPiecesComeOutOfTheWholeLoop(GameTestHelper h) {
        List<String> ids = new ArrayList<>();
        if (loaded(EK)) ids.addAll(List.of("magistuarmory:armet", "magistuarmory:iron_bastardsword", "magistuarmory:iron_kiteshield",
                "magistuarmory:heavy_crossbow", "magistuarmory:chainmail_helmet", "magistuarmory:netherite_shortsword"));
        if (loaded("magistuarmoryaddon")) ids.add("magistuarmoryaddon:steel_longsword");
        if (loaded("slavicarmory")) ids.add("slavicarmory:steel_bardiche");
        if (loaded("epic_knights_ice_and_fire")) ids.add("epic_knights_ice_and_fire:dragonsteel_fire_shortsword");
        if (loaded("epic_knights_ores_and_alloys")) ids.add("epic_knights_ores_and_alloys:steel_pickaxe");
        if (loaded("artillery_addon")) ids.add("artillery_addon:handgonne");
        if (loaded("medieval_boomsticks")) ids.add("medieval_boomsticks:javelin");
        if (loaded("ekotsk")) ids.add("ekotsk:night_watch_chestplate");
        for (String id : ids) {
            FakePlayer smith = TestSupport.player(h);
            SmithingRecipe recipe = only(h, id);
            ItemStack done = SmithingRun.smith(h, smith, recipe, ItemStack.EMPTY);
            h.assertTrue(done.is(item(id)) && done.getCount() == 1, "Forged the mod's own " + id);
            QualityData quality = QualityData.get(done).orElse(null);
            h.assertTrue(quality != null && quality.forgeScore() == 100 && quality.anvilScore() == 100, id + " carries the run's scores");
            h.assertTrue(MakersMark.get(done).map(m -> m.smithId().equals(smith.getUUID())).orElse(false), id + " carries the smith's mark");
            for (AuxiliaryIngredient aux : recipe.auxiliary()) {
                h.assertTrue(smith.getInventory().countItem(aux.displayStack().getItem()) == 0, id + " consumed its components");
            }
            h.assertTrue(QualityEligibility.canCarryQuality(done), id + " is smithing work");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ quality

    @GameTest(template = "empty")
    public static void weaponsThatBlockStillScaleTheirDamage(GameTestHelper h) {
        if (!loaded(EK)) {
            h.succeed();
            return;
        }
        ItemStack sword = new ItemStack(item("magistuarmory:iron_bastardsword"));
        h.assertTrue(sword.canPerformAction(net.minecraftforge.common.ToolActions.SHIELD_BLOCK), "Epic Knights' bastard sword can block");
        h.assertTrue(!EquipmentClassifier.isShield(sword), "but it is a weapon, not a shield");
        h.assertTrue(EquipmentClassifier.isShield(new ItemStack(item("magistuarmory:iron_kiteshield"))), "the kite shield is a shield");
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        LivingEntity target = TestSupport.dummy(h);
        for (String id : List.of("magistuarmory:iron_bastardsword", "magistuarmory:steel_heavymace", "magistuarmory:iron_pike")) {
            TestSupport.hold(player, new ItemStack(item(id)), ItemStack.EMPTY);
            float base = TestSupport.damageOf(player, target, () -> player.attack(target));
            for (int score : new int[]{0, 50, 100}) {
                QualityData q = new QualityData(score, score, false);
                ItemStack graded = new ItemStack(item(id));
                q.apply(graded);
                TestSupport.hold(player, graded, ItemStack.EMPTY);
                float dealt = TestSupport.damageOf(player, target, () -> player.attack(target));
                double expected = base * QualityCalculator.efficacyMultiplier(q);
                h.assertTrue(Math.abs(dealt - expected) < 0.02, id + " at " + score + " deals " + expected + ", got " + dealt);
            }
        }
        ItemStack shield = new ItemStack(item("magistuarmory:steel_kiteshield"));
        new QualityData(100, 100, false).apply(shield);
        h.assertTrue(shield.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.OFFHAND).values().stream()
                .noneMatch(a -> "Smithing quality".equals(a.getName())), "Shields stay efficacy-neutral");
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    // ------------------------------------------------------------------ transformations and states

    private static ItemStack gradedPiece(String id, FakePlayer smith) {
        ItemStack piece = new ItemStack(item(id));
        new QualityData(80, 90, false).apply(piece);
        MakersMark.stamp(piece, smith);
        piece.enchant(Enchantments.ALL_DAMAGE_PROTECTION, 3);
        piece.enchant(Enchantments.BINDING_CURSE, 1);
        piece.setHoverName(Component.literal("Helm of the Watch"));
        piece.setDamageValue(40);
        return piece;
    }

    private static void sameWork(GameTestHelper h, String what, ItemStack before, ItemStack after) {
        QualityData q = QualityData.get(after).orElse(null);
        h.assertTrue(q != null && q.forgeScore() == 80 && q.anvilScore() == 90, what + " keeps the quality");
        h.assertTrue(MakersMark.get(after).isPresent() && MakersMark.get(after).get().smithId().equals(MakersMark.get(before).get().smithId()),
                what + " keeps the maker's mark");
        h.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.ALL_DAMAGE_PROTECTION, after) == 3
                && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, after) == 1, what + " keeps enchantments and curses");
        h.assertTrue(after.getDamageValue() == 40 && after.getHoverName().getString().equals("Helm of the Watch"), what + " keeps wear and name");
    }

    @GameTest(template = "empty")
    public static void gildingAndDarkeningKeepTheSmithsWork(GameTestHelper h) {
        if (!loaded(EK)) {
            h.succeed();
            return;
        }
        var server = h.getLevel().getServer();
        FakePlayer smith = TestSupport.player(h);
        ItemStack armet = gradedPiece("magistuarmory:armet", smith);
        var gilding = (SmithingTransformRecipe) server.getRecipeManager().byKey(new ResourceLocation(EK, "ceremonialarmet")).orElseThrow();
        SimpleContainer table = new SimpleContainer(new ItemStack(item("magistuarmory:gilding_template")), armet, new ItemStack(Items.GOLD_INGOT));
        h.assertTrue(gilding.matches(table, h.getLevel()), "The gilding template takes the graded armet");
        ItemStack gilded = gilding.assemble(table, server.registryAccess());
        h.assertTrue(gilded.is(item("magistuarmory:ceremonialarmet")), "Ceremonial armet");
        sameWork(h, "Gilding", armet, gilded);
        RecyclingValue plain = MaterialResolver.recyclingValue(new ItemStack(item("magistuarmory:armet"))).orElseThrow();
        RecyclingValue golden = MaterialResolver.recyclingValue(new ItemStack(item("magistuarmory:ceremonialarmet"))).orElseThrow();
        h.assertTrue(golden.family().equals(plain.family()) && golden.units() == plain.units(),
                "A gilded armet melts for the armet's steel only, never its gold: " + golden.units() + " " + golden.family());
        h.assertTrue(QualityEligibility.canCarryQuality(gilded), "and can still be refined");
        if (loaded("magistuarmoryaddon")) {
            var darkening = (SmithingTransformRecipe) server.getRecipeManager()
                    .byKey(new ResourceLocation("magistuarmoryaddon", "dark_knight_helmet_from_armet_darkening")).orElseThrow();
            SimpleContainer dark = new SimpleContainer(new ItemStack(item("magistuarmory:darkening_template")), armet, new ItemStack(Items.BLACK_DYE));
            h.assertTrue(darkening.matches(dark, h.getLevel()), "The darkening template takes the graded armet");
            sameWork(h, "Darkening", armet, darkening.assemble(dark, server.registryAccess()));
        }
        h.succeed();
    }

    /** Swappable Helms replaces the helmet with its pair and copies the whole tag (SwapPacket.handle): emulated exactly. */
    private static ItemStack swap(ItemStack worn, Item to) {
        ItemStack target = new ItemStack(to);
        if (worn.hasTag()) target.setTag(worn.getTag().copy());
        return target;
    }

    @GameTest(template = "empty")
    public static void visorStatesAreOnePiece(GameTestHelper h) {
        if (!loaded(EK) || !loaded("epicknights_open_helms") || !loaded("quick_swap")) {
            h.succeed();
            return;
        }
        FakePlayer smith = TestSupport.player(h);
        ItemStack closed = gradedPiece("magistuarmory:armet", smith);
        ItemStack open = swap(closed, item("epicknights_open_helms:open_armet_helmet"));
        sameWork(h, "Opening the visor", closed, open);
        ItemStack again = swap(open, item("magistuarmory:armet"));
        sameWork(h, "Closing it again", closed, again);
        h.assertTrue(ItemStack.isSameItemSameTags(closed, again), "A round trip changes nothing");
        RecyclingValue c = MaterialResolver.recyclingValue(new ItemStack(item("magistuarmory:armet"))).orElseThrow();
        RecyclingValue o = MaterialResolver.recyclingValue(new ItemStack(item("epicknights_open_helms:open_armet_helmet"))).orElseThrow();
        h.assertTrue(o.family().equals(c.family()) && o.units() == c.units(), "The open armet melts for exactly the armet's metal: " + o.units());
        h.assertTrue(QualityEligibility.canCarryQuality(open), "The open state can carry and refine the piece's quality");
        h.assertTrue(SmithingData.current().recipesProducing(item("epicknights_open_helms:open_armet_helmet")).isEmpty(),
                "Visor states are never forged separately");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void relicsAndLootStayUnique(GameTestHelper h) {
        List<String> relics = new ArrayList<>();
        if (loaded("ekotsk")) relics.addAll(List.of("ekotsk:ice", "ekotsk:longclaw", "ekotsk:valyrian_steel_helmet"));
        for (String id : relics) {
            notForged(h, id);
            h.assertTrue(MaterialResolver.recyclingValue(new ItemStack(item(id))).isEmpty(), id + " is never melted down");
            h.assertTrue(QualityEligibility.canCarryQuality(new ItemStack(item(id))), id + " is graded when found");
        }
        if (loaded(EK)) {
            notForged(h, "magistuarmory:rustedbarbute");
            notForged(h, "magistuarmory:crown_decoration");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the economy

    @GameTest(template = "empty")
    public static void platesMeltForTheirIngot(GameTestHelper h) {
        if (!loaded(EK)) {
            h.succeed();
            return;
        }
        var plate = MaterialResolver.resolve(new ItemStack(item("magistuarmory:steel_plate"))).orElseThrow();
        h.assertTrue(plate.family().id().toString().equals("forge:steel") && plate.unitsEach() == 9, "A steel plate melts for one ingot of steel");
        h.assertTrue(MaterialResolver.resolve(new ItemStack(item("magistuarmory:steel_ring"))).isEmpty(), "A ring (half a nugget) is never melted");
        if (loaded("antiquelegacy")) {
            var iron = MaterialResolver.resolve(new ItemStack(item("antiquelegacy:iron_plate"))).orElseThrow();
            h.assertTrue(iron.family().id().toString().equals("minecraft:iron") && iron.unitsEach() == 9, "An antique iron plate melts for one iron ingot");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forgedKnightsGearNeverMintsMetal(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        for (SmithingRecipe r : CoverageGameTests.bundled()) {
            RecyclingValue value = MaterialResolver.recyclingValue(r.result()).orElse(null);
            if (value == null) continue;
            if (!value.family().equals(r.family())) problems.add(r.getId() + " melts for another metal " + value.family());
            else if (value.units() > r.metalUnits()) problems.add(r.getId() + " melts for " + value.units() + " but costs " + r.metalUnits());
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        if (loaded("magistuarmoryaddon")) {
            RecyclingValue lion = MaterialResolver.recyclingValue(new ItemStack(item("magistuarmoryaddon:lion_helmet"))).orElseThrow();
            h.assertTrue(lion.family().toString().equals("forge:steel") && lion.units() == 72, "A lion helmet melts for its steel, never its gold");
        }
        if (loaded("medieval_boomsticks")) {
            // Three throwing axes from three iron axes: each one carries one axe's iron and its nugget.
            costs(h, "medieval_boomsticks:iron_throwing_axe", "minecraft:iron", 28, "minecraft:stick", 3);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ Epic Fight with Epic Knights

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void forgedKnightsWeaponsKeepTheirEpicFightIdentity(GameTestHelper h) {
        if (!loaded(EK) || !loaded("epicfight")) {
            h.succeed();
            return;
        }
        for (String id : List.of("magistuarmory:iron_bastardsword", "magistuarmory:steel_zweihander", "magistuarmory:iron_pike")) {
            ItemStack forged = SmithingRun.smith(h, TestSupport.player(h), only(h, id), ItemStack.EMPTY);
            ItemStack bought = new ItemStack(item(id));
            h.assertTrue(EpicFightTestSupport.category(forged).equals(EpicFightTestSupport.category(bought)),
                    id + " keeps its Epic Fight category: " + EpicFightTestSupport.category(forged));
        }
        TestSupport.TestPlayer player = TestSupport.fighter(h);
        LivingEntity target = TestSupport.dummy(h);
        EpicFightTestSupport.battleMode(player);
        QualityData fine = new QualityData(100, 100, false);
        double m = QualityCalculator.efficacyMultiplier(fine);
        ItemStack plain = new ItemStack(item("magistuarmory:iron_bastardsword"));
        ItemStack graded = plain.copy();
        fine.apply(graded);
        TestSupport.hold(player, plain, ItemStack.EMPTY);
        float base = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, net.minecraft.world.InteractionHand.MAIN_HAND));
        TestSupport.hold(player, graded, ItemStack.EMPTY);
        float scaled = TestSupport.damageOf(player, target, () -> EpicFightTestSupport.battleAttack(player, target, net.minecraft.world.InteractionHand.MAIN_HAND));
        h.assertTrue(base > 1F && Math.abs(scaled - base * m) < 0.05, "Battle mode: an Epic Knights sword's quality applies once, x" + m
                + ", got " + scaled / base);
        double attribute = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        h.assertTrue(Math.abs(attribute - base * m) < 0.05, "and the modifiers do not pile up: " + attribute);
        EpicFightTestSupport.vanillaMode(player);
        TestSupport.hold(player, ItemStack.EMPTY, ItemStack.EMPTY);
        h.succeed();
    }

    private EpicKnightsGameTests() {}
}
