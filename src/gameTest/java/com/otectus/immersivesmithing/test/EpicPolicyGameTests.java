package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ISmithingRecipeProvider;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.compat.epicknights.ComponentGraph;
import com.otectus.immersivesmithing.minigame.ForgePattern;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EpicPolicyGameTests {
    private static Item item(String id) { return BuiltInRegistries.ITEM.get(new ResourceLocation(id)); }

    @GameTest(template = "empty")
    public static void componentFractionsFollowTheActiveRecipes(GameTestHelper h) {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        var data = SmithingData.current();
        var graph = new ComponentGraph(data.sourceRecipes(), h.getLevel().registryAccess(), data.materials());
        for (String[] fixture : List.of(new String[]{"steel_plate", "9"}, new String[]{"small_steel_plate", "3/4"},
                new String[]{"steel_ring", "1/2"}, new String[]{"steel_chain", "3/2"}, new String[]{"steel_chainmail", "9/2"})) {
            var value = graph.item(item("magistuarmory:" + fixture[0])).orElseThrow();
            h.assertTrue(value.metal().toString().equals(fixture[1]), fixture[0] + " = " + value.metal());
        }
        // Change the loaded batch, not a static compatibility JSON. The cached graph is tied to this recipe generation.
        List<Recipe<?>> changed = new ArrayList<>(data.sourceRecipes());
        changed.removeIf(r -> r.getId().toString().equals("magistuarmory:steel_plate"));
        changed.add(new ShapelessRecipe(new ResourceLocation("magistuarmory", "steel_plate"), "", CraftingBookCategory.MISC,
                new ItemStack(item("magistuarmory:steel_plate"), 2), NonNullList.of(Ingredient.EMPTY,
                Ingredient.of(item("magistuarmory:steel_ingot")), Ingredient.of(item("magistuarmory:steel_ingot")),
                Ingredient.of(item("magistuarmory:steel_ingot")))));
        var overridden = new ComponentGraph(changed, h.getLevel().registryAccess(), data.materials());
        h.assertTrue(overridden.item(item("magistuarmory:steel_plate")).orElseThrow().metal().toString().equals("27/2"),
                "Changed batch costs 13.5 per plate without rounding individual components");
        h.assertTrue(graph.item(item("magistuarmory:steel_plate")).orElseThrow().metal().floor() == 9, "Previous generation stays immutable");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void structuralUpgradesConsumeTheExactBase(GameTestHelper h) {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        for (String[] fixture : List.of(new String[]{"knight_chestplate", "halfarmor_chestplate", "45"},
                new String[]{"steel_bastardsword", "steel_shortsword", "9"})) {
            SmithingRecipe recipe = SmithingData.current().recipesProducing(item("magistuarmory:" + fixture[0])).stream()
                    .filter(r -> r.sourceRecipe() != null && r.sourceRecipe().toString().equals("magistuarmory:" + fixture[0])).findFirst().orElseThrow();
            h.assertTrue(recipe.metalUnits() == Integer.parseInt(fixture[2]), fixture[0] + " pays added metal only");
            var bases = recipe.auxiliary().stream().filter(a -> a.consumesEquipment()).toList();
            h.assertTrue(bases.size() == 1 && bases.get(0).count() == 1
                    && bases.get(0).ingredient().test(new ItemStack(item("magistuarmory:" + fixture[1]))), "Exact base retained: " + fixture[1]);
            h.assertTrue(recipe.recoverableUnits() > recipe.metalUnits(), "Recovery includes the consumed base");
        }
        SmithingRecipe helmet = SmithingData.current().recipesProducing(item("magistuarmory:chainmail_helmet")).get(0);
        h.assertTrue(helmet.metalUnits() == 23 && helmet.recoverableUnits() == 22, "22.5 units: charge 23, recover at most 22");
        SmithingRecipe chest = SmithingData.current().recipesProducing(item("magistuarmory:chainmail_chestplate")).get(0);
        h.assertTrue(chest.metalUnits() == 36 && chest.recoverableUnits() == 36, "Eight chainmails total 36, not eight rounded costs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void providerRecipesReachTheManagerExactlyOnce(GameTestHelper h) {
        ResourceLocation id = ImmersiveSmithing.id("test/provider_sync");
        ISmithingRecipeProvider provider = (materials, access) -> List.of(new SmithingRecipe(id, new ResourceLocation("minecraft", "iron"),
                18, List.of(), new ItemStack(Items.IRON_SWORD), ForgePattern.STANDARD, ImmersiveSmithing.id("sword"), false, null));
        var server = h.getLevel().getServer();
        try {
            ImmersiveSmithingAPI.registerRecipeProvider(provider);
            SmithingData.rebuild(server);
            SmithingData.rebuild(server);
            h.assertTrue(server.getRecipeManager().byKey(id).isPresent() && SmithingData.current().recipe(id) != null,
                    "Provider recipe is in both the server snapshot and the manager synchronized to clients");
            h.assertTrue(server.getRecipeManager().getRecipes().stream().filter(r -> r.getId().equals(id)).count() == 1, "No duplicate after reload");
        } finally {
            ImmersiveSmithingAPI.recipeProviders().remove(provider);
            SmithingData.rebuild(server);
        }
        h.assertTrue(server.getRecipeManager().byKey(id).isEmpty(), "Removed provider does not survive the next rebuild");
        h.succeed();
    }

    private EpicPolicyGameTests() {}

    @GameTest(template = "empty")
    public static void longChainsAlternateRoutesAndCyclesHaveConservativeContent(GameTestHelper h) {
        var iron = new ResourceLocation("minecraft", "iron");
        List<Item> items = List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.GOLDEN_SWORD, Items.IRON_SWORD,
                Items.DIAMOND_SWORD, Items.NETHERITE_SWORD);
        List<SmithingRecipe> templates = new ArrayList<>();
        List<Recipe<?>> sources = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var id = ImmersiveSmithing.id("test/chain_" + (items.size() - i));
            NonNullList<Ingredient> inputs = NonNullList.create();
            inputs.add(Ingredient.of(Items.IRON_INGOT));
            if (i > 0) inputs.add(Ingredient.of(items.get(i - 1)));
            sources.add(new ShapelessRecipe(id, "", CraftingBookCategory.EQUIPMENT, new ItemStack(items.get(i)), inputs));
            templates.add(new SmithingRecipe(ImmersiveSmithing.id("test/template_" + i), iron, 1, List.of(),
                    new ItemStack(items.get(i)), ForgePattern.STANDARD, ImmersiveSmithing.id("sword"), false, null).resolveEpicSource(true));
        }
        java.util.Collections.reverse(sources);
        var first = resolve(h, templates, sources);
        h.assertTrue(first.stream().filter(r -> r.resultItem() == Items.NETHERITE_SWORD).findFirst().orElseThrow().recoverableUnits() == 54,
                "Six-node reverse-ordered chain includes all six added ingots");
        sources.add(new ShapelessRecipe(ImmersiveSmithing.id("test/cheap_route"), "", CraftingBookCategory.EQUIPMENT,
                new ItemStack(Items.NETHERITE_SWORD), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT))));
        var alternate = resolve(h, templates, sources).stream().filter(r -> r.resultItem() == Items.NETHERITE_SWORD).toList();
        h.assertTrue(alternate.size() == 2 && alternate.stream().allMatch(r -> r.recoverableUnits() == 9),
                "Both source IDs survive; every copy uses the lower proven material content");
        sources.add(new ShapelessRecipe(ImmersiveSmithing.id("test/unknown_route"), "", CraftingBookCategory.EQUIPMENT,
                new ItemStack(Items.NETHERITE_SWORD), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK))) {
            @Override public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer c, net.minecraft.core.RegistryAccess a) {
                return new ItemStack(Items.NETHERITE_SWORD);
            }
        });
        h.assertTrue(resolve(h, templates, sources).stream().filter(r -> r.resultItem() == Items.NETHERITE_SWORD)
                .allMatch(r -> r.recoverableUnits() == 0), "Unknown custom alternative cannot borrow expensive salvage");
        List<Recipe<?>> cycle = List.of(
                new ShapelessRecipe(ImmersiveSmithing.id("test/cycle_a"), "", CraftingBookCategory.EQUIPMENT,
                        new ItemStack(Items.IRON_SWORD), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLDEN_SWORD), Ingredient.of(Items.IRON_INGOT))),
                new ShapelessRecipe(ImmersiveSmithing.id("test/cycle_b"), "", CraftingBookCategory.EQUIPMENT,
                        new ItemStack(Items.GOLDEN_SWORD), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.IRON_INGOT))));
        h.assertTrue(resolve(h, templates, cycle).stream().allMatch(r -> r.recoverableUnits() == 0), "Unanchored cycles infer no profitable salvage");
        h.succeed();
    }

    private static List<SmithingRecipe> resolve(GameTestHelper h, List<SmithingRecipe> templates, List<Recipe<?>> sources) {
        return new com.otectus.immersivesmithing.compat.epicknights.EpicRecipeResolver(sources,
                h.getLevel().registryAccess(), SmithingData.current().materials(), new com.otectus.immersivesmithing.recipe.CompatibilityReport())
                .resolve(templates, sources);
    }

    @GameTest(template = "empty")
    public static void recyclingOverridesAndFalseWinOverInferredAndRawMetal(GameTestHelper h) throws Exception {
        var field = com.otectus.immersivesmithing.material.RecyclingOverrideLoader.class.getDeclaredField("overrides");
        field.setAccessible(true); Object original = field.get(null);
        var loader = new com.otectus.immersivesmithing.material.RecyclingOverrideLoader(net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY);
        var apply = loader.getClass().getDeclaredMethod("apply", java.util.Map.class,
                net.minecraft.server.packs.resources.ResourceManager.class, net.minecraft.util.profiling.ProfilerFiller.class);
        apply.setAccessible(true);
        var pack = new java.util.HashMap<ResourceLocation, com.google.gson.JsonElement>();
        pack.put(ImmersiveSmithing.id("test/override"), com.google.gson.JsonParser.parseString(
                "{\"item\":\"minecraft:iron_sword\",\"family\":\"minecraft:iron\",\"units\":7}"));
        try {
            apply.invoke(loader, pack, h.getLevel().getServer().getResourceManager(), net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            SmithingData.rebuild(h.getLevel().getServer());
            h.assertTrue(com.otectus.immersivesmithing.material.MaterialResolver.recyclingValue(new ItemStack(Items.IRON_SWORD)).orElseThrow().units() == 7,
                    "Explicit seven-unit value overrides built-in smithing cost");
            for (String id : List.of("minecraft:iron_sword", "minecraft:iron_ingot")) pack.put(new ResourceLocation(id),
                    com.google.gson.JsonParser.parseString("{\"item\":\"" + id + "\",\"recyclable\":false}"));
            apply.invoke(loader, pack, h.getLevel().getServer().getResourceManager(), net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            SmithingData.rebuild(h.getLevel().getServer());
            h.assertTrue(com.otectus.immersivesmithing.material.MaterialResolver.resolve(new ItemStack(Items.IRON_SWORD)).isEmpty()
                    && com.otectus.immersivesmithing.material.MaterialResolver.resolve(new ItemStack(Items.IRON_INGOT)).isEmpty(),
                    "False beats both a positive override and a raw source mapping");
        } finally { field.set(null, original); SmithingData.rebuild(h.getLevel().getServer()); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void sourcePolicyAndRemovedNativeRecipeNeverResurrect(GameTestHelper h) throws Exception {
        if (!ModList.get().isLoaded("magistuarmory")) { h.succeed(); return; }
        var server = h.getLevel().getServer();
        var original = List.copyOf(SmithingData.current().sourceRecipes());
        var source = new ResourceLocation("magistuarmory", "knight_chestplate");
        var policyClass = com.otectus.immersivesmithing.recipe.SourceRecipePolicies.class;
        var field = policyClass.getDeclaredField("policies"); field.setAccessible(true); Object previous = field.get(null);
        var loader = new com.otectus.immersivesmithing.recipe.SourceRecipePolicies(net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY);
        var apply = policyClass.getDeclaredMethod("apply", java.util.Map.class,
                net.minecraft.server.packs.resources.ResourceManager.class, net.minecraft.util.profiling.ProfilerFiller.class); apply.setAccessible(true);
        try {
            apply.invoke(loader, java.util.Map.of(source, com.google.gson.JsonParser.parseString("{\"operation\":\"native\",\"reason\":\"pack owns this route\"}")),
                    server.getResourceManager(), net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            SmithingData.rebuild(server);
            h.assertTrue(server.getRecipeManager().byKey(source).isPresent() && !SmithingData.current().suppressedRecipes().containsKey(source),
                    "Exact source policy retains native manufacturing");
            field.set(null, previous);
            server.getRecipeManager().replaceRecipes(original.stream().filter(r -> !r.getId().equals(source)).toList());
            SmithingData.rebuild(server); SmithingData.rebuild(server);
            h.assertTrue(server.getRecipeManager().byKey(source).isEmpty()
                    && SmithingData.current().recipesProducing(item("magistuarmory:knight_chestplate")).stream().noneMatch(r -> source.equals(r.sourceRecipe())),
                    "A new pack generation removing a source removes its forge route through repeated rebuilds");
        } finally { field.set(null, previous); server.getRecipeManager().replaceRecipes(original); SmithingData.rebuild(server); }
        h.succeed();
    }
}
