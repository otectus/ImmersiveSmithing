package com.otectus.immersivesmithing.test;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.config.ServerConfigSource;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.recipe.AutoRecipeDetector;
import com.otectus.immersivesmithing.recipe.CompatibilityReport;
import com.otectus.immersivesmithing.recipe.RecipeDecisions;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selective exclusions: a native recipe id kept and protected ({@code recipeBlacklist}), a smithing recipe id switched off
 * ({@code smithingRecipeBlacklist}), an item or tag left out of automatic detection in every pass
 * ({@code autoDetectExclusions}) and a whole mod ({@code modBlacklist}). Every case rebuilds with an isolated config and
 * puts the server's configuration and recipes back afterwards.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExclusionGameTests {

    private static ResourceLocation rl(String id) {
        return new ResourceLocation(id);
    }

    private static boolean loaded(MinecraftServer server, String id) {
        return server.getRecipeManager().byKey(rl(id)).isPresent();
    }

    private static Set<ResourceLocation> managerIds(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
    }

    private static boolean decidedBy(Item item, RecipeDecisions.Outcome outcome, String recipe, String selector) {
        return SmithingData.current().decisionsFor(item).stream().anyMatch(d -> d.outcome() == outcome
                && recipe.equals(String.valueOf(d.recipe())) && selector.equals(d.selector()));
    }

    private interface Body {
        void run(MinecraftServer server);
    }

    /** Applies the options to an isolated copy of the config, rebuilds, runs the checks, then restores and rebuilds again. */
    private static void withOptions(GameTestHelper h, Runnable options, Body body) {
        MinecraftServer server = h.getLevel().getServer();
        var config = TestSupport.isolateServerConfig();
        try {
            options.run();
            SmithingData.rebuild(server);
            body.run(server);
        } finally {
            TestSupport.restoreServerConfig(config);
            SmithingData.rebuild(server);
        }
    }

    @GameTest(template = "empty")
    public static void recipeBlacklistKeepsAndProtectsTheNativeRecipe(GameTestHelper h) {
        withOptions(h, () -> ServerConfig.RECIPE_BLACKLIST.set(List.of("immersive_smithing_test:alt_metal_golden_sword", "minecraft:golden_sword")), server -> {
            String selector = "recipes.recipeBlacklist = minecraft:golden_sword";
            h.assertTrue(loaded(server, "minecraft:golden_sword"), "The blacklisted native recipe is never disabled, though a forge recipe makes the item");
            h.assertTrue(!SmithingData.current().recipesProducing(Items.GOLDEN_SWORD).isEmpty(), "The explicit forge recipe still applies");
            h.assertTrue(decidedBy(Items.GOLDEN_SWORD, RecipeDecisions.Outcome.KEPT, "minecraft:golden_sword", selector),
                    "The decision names the selector: " + SmithingData.current().decisionsFor(Items.GOLDEN_SWORD));
            h.assertTrue(loaded(server, "immersive_smithing_test:alt_metal_golden_sword"), "The other blacklisted source stays as well");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void smithingRecipeBlacklistSwitchesOffGeneratedAndExplicitIds(GameTestHelper h) {
        withOptions(h, () -> ServerConfig.SMITHING_RECIPE_BLACKLIST.set(List.of("immersive_smithing:auto/minecraft/chainmail_leggings",
                "immersive_smithing:smithing/netherite_helmet")), server -> {
            SmithingData data = SmithingData.current();
            h.assertTrue(data.recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty() && data.recipe(ImmersiveSmithing.id("auto/minecraft/chainmail_leggings")) == null,
                    "The generated recipe is gone");
            h.assertTrue(loaded(server, "immersive_smithing_test:chain_shape"), "And the native recipe it would have replaced stays");
            h.assertTrue(decidedBy(Items.CHAINMAIL_LEGGINGS, RecipeDecisions.Outcome.SKIPPED, "immersive_smithing_test:chain_shape",
                    "recipes.smithingRecipeBlacklist = immersive_smithing:auto/minecraft/chainmail_leggings"), "The chain pass names the selector");
            h.assertTrue(data.recipesProducing(Items.NETHERITE_HELMET).isEmpty(),
                    "The explicit netherite helmet recipe is off, and automatic detection does not stand in for it");
            h.assertTrue(loaded(server, "minecraft:netherite_helmet_smithing"), "So the smithing-table upgrade stays");
            Optional<RecyclingValue> helmet = MaterialResolver.recyclingValue(new ItemStack(Items.NETHERITE_HELMET));
            h.assertTrue(helmet.isPresent() && helmet.get().units() == 45, "Switching forging off never stops melting: " + helmet);
            Optional<RecyclingValue> leggings = MaterialResolver.recyclingValue(new ItemStack(Items.CHAINMAIL_LEGGINGS));
            h.assertTrue(leggings.isPresent() && leggings.get().units() == 63 && leggings.get().family().toString().equals("immersive_smithing_test:lapisine"),
                    "The leggings melt as their switched-off recipe would have made them: " + leggings);
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void autoDetectExclusionsCoverEveryPass(GameTestHelper h) {
        withOptions(h, () -> ServerConfig.AUTO_DETECT_EXCLUSIONS.set(List.of("minecraft:chainmail_chestplate", "minecraft:turtle_helmet")), server -> {
            SmithingData data = SmithingData.current();
            h.assertTrue(data.recipesProducing(Items.CHAINMAIL_CHESTPLATE).isEmpty() && loaded(server, "immersive_smithing_test:chain_addition"),
                    "Chain pass: nothing generated, the native recipe stays");
            h.assertTrue(data.recipesProducing(Items.TURTLE_HELMET).isEmpty() && loaded(server, "immersive_smithing_test:unpriced_base_upgrade"),
                    "Upgrade pass: nothing generated, the smithing-table upgrade stays");
            h.assertTrue(decidedBy(Items.TURTLE_HELMET, RecipeDecisions.Outcome.SKIPPED, "immersive_smithing_test:unpriced_base_upgrade",
                    "recipes.autoDetectExclusions = minecraft:turtle_helmet"), "With the selector in its decision");
            h.assertTrue(!data.recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty(), "An item not named is untouched");
        });
        // By tag: trimmable armour names every armour piece. The automatic ones go; the explicit ones stay and are reported as moot.
        withOptions(h, () -> ServerConfig.AUTO_DETECT_EXCLUSIONS.set(List.of("#minecraft:trimmable_armor")), server -> {
            SmithingData data = SmithingData.current();
            for (Item item : List.of(Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.TURTLE_HELMET)) {
                h.assertTrue(data.recipesProducing(item).isEmpty(), "The tag excludes " + item + " in its pass");
            }
            h.assertTrue(loaded(server, "immersive_smithing_test:chain_shape"), "Its native recipes stay");
            h.assertTrue(!data.recipesProducing(Items.IRON_CHESTPLATE).isEmpty() && !loaded(server, "minecraft:iron_chestplate"),
                    "An explicit recipe is unaffected and still replaces its native recipe");
            h.assertTrue(data.decisionsFor(Items.IRON_CHESTPLATE).stream().anyMatch(d -> "recipes.autoDetectExclusions = #minecraft:trimmable_armor".equals(d.selector())
                    && d.reason().contains("no effect") && d.reason().contains("minecraft:iron_chestplate")),
                    "The report says the exclusion is moot there, with the native recipe's fate");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oneOfTwoSourcesBlacklisted(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ShapelessRecipe first = new ShapelessRecipe(ImmersiveSmithing.id("test_a_boots"), "", CraftingBookCategory.EQUIPMENT, new ItemStack(Items.CHAINMAIL_BOOTS),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT)));
        ShapelessRecipe second = new ShapelessRecipe(ImmersiveSmithing.id("test_b_boots"), "", CraftingBookCategory.EQUIPMENT, new ItemStack(Items.CHAINMAIL_BOOTS),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.IRON_INGOT), Ingredient.of(Items.STRING)));
        CompatibilityReport report = new CompatibilityReport();
        var analyses = AutoRecipeDetector.analyzeCrafting(List.<Recipe<?>>of(first, second), server.registryAccess(), SmithingData.current().materials(), Set.of(), report);
        List<SmithingRecipe> open = AutoRecipeDetector.generate(analyses.values(), Set.of(), new AutoRecipeDetector.Scope(Set.of(), Set.of()), report);
        h.assertTrue(open.size() == 1 && first.getId().equals(open.get(0).sourceRecipe()), "Unrestricted, the first source by id wins");
        List<SmithingRecipe> one = AutoRecipeDetector.generate(analyses.values(), Set.of(),
                new AutoRecipeDetector.Scope(Set.of(first.getId().toString()), Set.of()), report);
        h.assertTrue(one.size() == 1 && second.getId().equals(one.get(0).sourceRecipe()) && one.get(0).metalUnits() == 27,
                "With the first blacklisted, the other allowed source still generates: " + one);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void modBlacklistLeavesTheModAloneWhoeverMakesIt(GameTestHelper h) {
        withOptions(h, () -> ServerConfig.MOD_BLACKLIST.set(List.of("minecraft")), server -> {
            SmithingData data = SmithingData.current();
            h.assertTrue(data.recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty() && loaded(server, "immersive_smithing_test:chain_shape"),
                    "Not detected even through a recipe of another namespace");
            h.assertTrue(!data.recipesProducing(Items.IRON_SWORD).isEmpty(), "Explicit recipes for its items still apply");
            h.assertTrue(loaded(server, "minecraft:iron_sword") && loaded(server, "minecraft:netherite_sword_smithing"),
                    "And none of its native recipes is disabled");
            h.assertTrue(decidedBy(Items.IRON_SWORD, RecipeDecisions.Outcome.KEPT, "minecraft:iron_sword", "recipes.modBlacklist = minecraft"),
                    "With the selector in the decision");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void excludeRestoreRebuildIsExact(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Set<ResourceLocation> before = managerIds(server);
        withOptions(h, () -> {
            ServerConfig.AUTO_DETECT_EXCLUSIONS.set(List.of("#minecraft:trimmable_armor"));
            ServerConfig.SMITHING_RECIPE_BLACKLIST.set(List.of("immersive_smithing:smithing/iron_sword"));
        }, s -> {
            SmithingData.rebuild(s);
            Set<ResourceLocation> excluded = managerIds(s);
            SmithingData.rebuild(s);
            h.assertTrue(managerIds(s).equals(excluded), "Rebuilding with the same exclusions changes nothing");
            h.assertTrue(s.getRecipeManager().getRecipes().size() == excluded.size(), "No recipe id appears twice");
            agree(h, SmithingData.current());
        });
        h.assertTrue(managerIds(server).equals(before), "Restored: exactly the original recipe set");
        agree(h, SmithingData.current());
        h.succeed();
    }

    /** {@code recipe <item>} (the decisions) and the report agree with what was actually disabled. */
    private static void agree(GameTestHelper h, SmithingData data) {
        Set<ResourceLocation> decided = new HashSet<>();
        for (SmithingRecipe r : data.recipes()) {
            data.decisionsFor(r.resultItem()).stream().filter(d -> d.outcome() == RecipeDecisions.Outcome.DISABLED).forEach(d -> decided.add(d.recipe()));
        }
        for (var entry : data.suppressedRecipes().entrySet()) {
            h.assertTrue(decided.contains(entry.getKey()), "Disabled " + entry.getKey() + " has its decision");
        }
        h.assertTrue(data.suppressedRecipes().keySet().containsAll(decided), "Every disabled decision was really disabled");
        h.assertTrue(data.report().suppressedCount() == data.suppressedRecipes().size(), "The report counts the same recipes");
    }

    @GameTest(template = "empty")
    public static void badEntriesAreReportedNeverFatal(GameTestHelper h) {
        withOptions(h, () -> {
            ServerConfig.AUTO_DETECT_EXCLUSIONS.set(List.of("Not An Id!", "examplemod:not_loaded", "#examplemod:empty_tag"));
            ServerConfig.SMITHING_RECIPE_BLACKLIST.set(List.of("immersive_smithing:auto/examplemod/nothing"));
        }, server -> {
            List<String> lines = SmithingData.current().report().lines();
            h.assertTrue(lines.stream().anyMatch(l -> l.contains("problem: recipes.autoDetectExclusions entry \"Not An Id!\"")), "An unparsable entry is a problem");
            h.assertTrue(lines.stream().anyMatch(l -> l.contains("examplemod:not_loaded: no such item")), "An unknown item is a note");
            h.assertTrue(lines.stream().anyMatch(l -> l.contains("#examplemod:empty_tag: the tag is empty")), "An empty tag is a note");
            h.assertTrue(lines.stream().anyMatch(l -> l.trim().equals("recipes.smithingRecipeBlacklist = immersive_smithing:auto/examplemod/nothing: 0 matches")),
                    "A selector that matches nothing shows 0 matches");
            h.assertTrue(!SmithingData.current().recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty(), "And everything else still works");
        });
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reloadAppliesTheEditedSharedFile(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerConfigSource.Snapshot before = ServerConfigSource.snapshot();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("immersive_smithing_reload");
            Path shared = Files.writeString(dir.resolve(ServerConfigSource.FILE_NAME), "[recipes]\n    autoDetectExclusions = []\n");
            Path world = Files.writeString(dir.resolve("world.toml"), "");
            ServerConfigSource.bindForTest(shared, world);
            SmithingData.rebuild(server);
            h.assertTrue(!SmithingData.current().recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty(), "Not excluded yet");
            // What /immersivesmithing reload does: the config first, then the rebuild of the datapack reload.
            Files.writeString(shared, "[recipes]\n    autoDetectExclusions = [\"minecraft:chainmail_leggings\"]\n");
            h.assertTrue(ServerConfigSource.reload().ok(), "The edited file is bound");
            SmithingData.rebuild(server);
            h.assertTrue(SmithingData.current().recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty()
                            && decidedBy(Items.CHAINMAIL_LEGGINGS, RecipeDecisions.Outcome.SKIPPED, "immersive_smithing_test:chain_shape",
                            "recipes.autoDetectExclusions = minecraft:chainmail_leggings"),
                    "One reload later the decisions show the new exclusion: " + SmithingData.current().decisionsFor(Items.CHAINMAIL_LEGGINGS));
            Set<ResourceLocation> ids = managerIds(server);
            ServerConfigSource.reload();
            SmithingData.rebuild(server);
            h.assertTrue(managerIds(server).equals(ids), "Reloading again without an edit changes nothing");
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            ServerConfigSource.restore(before);
            SmithingData.rebuild(server);
            if (dir != null) {
                for (String name : List.of(ServerConfigSource.FILE_NAME, "world.toml")) dir.resolve(name).toFile().delete();
                dir.toFile().delete();
            }
        }
        h.assertTrue(!SmithingData.current().recipesProducing(Items.CHAINMAIL_LEGGINGS).isEmpty(), "Restored");
        h.succeed();
    }

    private ExclusionGameTests() {}
}
