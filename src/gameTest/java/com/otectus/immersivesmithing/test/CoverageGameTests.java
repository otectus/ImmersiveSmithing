package com.otectus.immersivesmithing.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import com.otectus.immersivesmithing.recipe.WeaponCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Coverage of every supported mod against the real jars (any -PepicFight / -PsimplySwords / -PepicKnights profile):
 * the live catalog of each loaded mod equals its checked-in inventory (docs/compatibility/inventory), no eligible item
 * lacks a route, the bundled recipes stand on their own without automatic detection, every metal and component they ask
 * for can be obtained, they disable exactly the native recipes they name, and switches and reloads are repeatable. Every
 * test passes trivially for the mods that are absent.
 */
@GameTestHolder(ImmersiveSmithing.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoverageGameTests {
    /** Mods with a checked-in inventory. */
    static final List<String> MODS = List.of("epicfight", "simplyswords", "magistuarmory", "magistuarmoryaddon", "slavicarmory",
            "antiquelegacy", "epic_knights_ice_and_fire", "epic_knights_ores_and_alloys", "darkagesarmory", "epicknights_open_helms",
            "artillery_addon", "medieval_boomsticks", "ekotsk", "thirtyyearswar", "fletchery",
            "bronzeaddition_epicknights", "epic_knights__japanese_armory", "overgeared_epic_knights", "knightsofterrafirma");
    private static final List<String> COMPARED = List.of("kind", "shape", "forge", "native", "quality", "recycling", "disposition");

    static List<String> loadedMods() {
        return MODS.stream().filter(m -> ModList.get().isLoaded(m)).toList();
    }

    // ------------------------------------------------------------------ the inventories

    /**
     * The checked-in inventories are grouped by test profile (docs/compatibility/inventory/<profile>/), each recording the
     * mods it was made with: the run compares against the profile with exactly its own mod set, if one is checked in.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void inventoryMatchesTheCheckedInFixtures(GameTestHelper h) throws IOException {
        Path root = Path.of(System.getProperty("immersive_smithing.manifestDir", "../docs/compatibility/inventory"));
        List<String> mods = loadedMods();
        // Every catalog is written before anything is compared, so one run regenerates all of them.
        for (String mod : mods) WeaponCatalog.write(Path.of("logs"), mod, h.getLevel().registryAccess());
        JsonObject metrics = new JsonObject();
        var snapshot = SmithingData.current();
        metrics.addProperty("smithing_recipes", snapshot.recipes().size());
        metrics.addProperty("material_families", snapshot.materials().families().size());
        metrics.addProperty("suppressed_sources", snapshot.suppressedRecipes().size());
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        long started = System.nanoTime();
        try {
            // Real vanilla recipe-sync encoding, restricted to the smithing portion of the snapshot.
            new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(new ArrayList<Recipe<?>>(snapshot.recipes())).write(buffer);
            metrics.addProperty("smithing_recipe_packet_bytes", buffer.writerIndex());
        } finally { buffer.release(); }
        metrics.addProperty("serialization_microseconds", (System.nanoTime() - started) / 1000);
        metrics.addProperty("measurement", "Smithing-only ClientboundUpdateRecipesPacket; not the entire native recipe manager or heap size");
        Files.createDirectories(Path.of("logs"));
        Files.writeString(Path.of("logs/immersive_smithing_snapshot_metrics.json"), metrics + "\n");
        if (mods.isEmpty()) {
            h.succeed();
            return;
        }
        JsonObject liveProfile = WeaponCatalog.build(mods.get(0), h.getLevel().registryAccess());
        JsonElement loaded = liveProfile.get("loaded_mods");
        Path dir = null;
        if (Files.isDirectory(root)) {
            try (var profiles = Files.list(root)) {
                for (Path profile : profiles.filter(Files::isDirectory).sorted().toList()) {
                    Path any = profile.resolve(mods.get(0) + ".json");
                    if (Files.exists(any) && loaded.equals(JsonParser.parseString(Files.readString(any)).getAsJsonObject().get("loaded_mods"))
                            && liveProfile.get("loaded_versions").equals(JsonParser.parseString(Files.readString(any)).getAsJsonObject().get("loaded_versions"))) {
                        dir = profile;
                        break;
                    }
                }
            }
        }
        if (dir == null) {
            // Not a checked-in profile: noEligibleEquipmentIsMissingSupport still holds every item to a route.
            com.otectus.immersivesmithing.ImmersiveSmithing.LOGGER.info("No checked-in inventory for this mod set {}: comparison skipped", loaded);
            h.succeed();
            return;
        }
        List<String> problems = new ArrayList<>();
        for (String mod : mods) {
            JsonObject live = WeaponCatalog.build(mod, h.getLevel().registryAccess());
            Path fixture = dir.resolve(mod + ".json");
            if (!Files.exists(fixture)) {
                problems.add("no inventory " + fixture + " (run tools/coverage_inventory.py --profile " + dir.getFileName() + ")");
                continue;
            }
            JsonObject expected = JsonParser.parseString(Files.readString(fixture)).getAsJsonObject();
            if (!expected.get("version").getAsString().equals(live.get("version").getAsString())) {
                problems.add(mod + " inventory is for " + expected.get("version") + ", loaded " + live.get("version"));
                continue;
            }
            List<String> diffs = compare(expected, live);
            if (!diffs.isEmpty()) problems.add(mod + " differs from its inventory (" + diffs.size() + "): "
                    + String.join(" | ", diffs.subList(0, Math.min(4, diffs.size()))));
        }
        h.assertTrue(problems.isEmpty(), String.join(" || ", problems));
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void noEligibleEquipmentIsMissingSupport(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        for (String mod : loadedMods()) {
            for (JsonElement e : WeaponCatalog.build(mod, h.getLevel().registryAccess()).getAsJsonArray("items")) {
                JsonObject item = e.getAsJsonObject();
                if (item.get("disposition").getAsString().equals("missing_support")) {
                    missing.add(item.get("id").getAsString() + " (" + item.get("reason").getAsString() + ")");
                }
            }
        }
        h.assertTrue(missing.isEmpty(), missing.size() + " items lack a route: " + String.join(", ", missing.subList(0, Math.min(8, missing.size()))));
        h.succeed();
    }

    static List<String> compare(JsonObject expected, JsonObject live) {
        Map<String, JsonObject> want = byId(expected);
        Map<String, JsonObject> got = byId(live);
        List<String> diffs = new ArrayList<>();
        for (String id : new TreeSet<>(want.keySet())) {
            if (!got.containsKey(id)) diffs.add(id + " is in the inventory but not registered");
        }
        for (Map.Entry<String, JsonObject> entry : got.entrySet()) {
            JsonObject expectedItem = want.get(entry.getKey());
            if (expectedItem == null) {
                diffs.add(entry.getKey() + " is registered but not in the inventory");
                continue;
            }
            for (String field : COMPARED) {
                JsonElement a = expectedItem.get(field);
                JsonElement b = entry.getValue().get(field);
                if (a == null ? b != null : !a.equals(b)) diffs.add(entry.getKey() + " " + field + ": inventory " + a + ", live " + b);
            }
        }
        return diffs;
    }

    private static Map<String, JsonObject> byId(JsonObject catalog) {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        catalog.getAsJsonArray("items").forEach(e -> out.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject()));
        return out;
    }

    // ------------------------------------------------------------------ the bundled recipes

    /** Active built-in recipes of the curated integrations (Epic Fight, Epic Knights). */
    static List<SmithingRecipe> bundled() {
        Set<String> curated = Integrations.curatedNamespaces();
        return SmithingData.current().recipes().stream()
                .filter(r -> curated.contains(String.valueOf(Integrations.compatModOf(r.getId()))))
                .sorted(java.util.Comparator.comparing(r -> r.getId().toString())).toList();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bundledRecipesStandWithoutAutomaticDetection(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Map<String, String> before = dispositions(h);
        Set<ResourceLocation> bundledBefore = bundled().stream().map(Recipe::getId).collect(Collectors.toSet());
        var config = TestSupport.isolateServerConfig();
        try {
            ServerConfig.AUTO_DETECT_EQUIPMENT.set(false);
            SmithingData.rebuild(server);
            Set<ResourceLocation> bundledAfter = bundled().stream().map(Recipe::getId).collect(Collectors.toSet());
            h.assertTrue(bundledAfter.equals(bundledBefore), "The same built-in recipes are active without automatic detection: "
                    + bundledBefore.size() + " vs " + bundledAfter.size());
            Map<String, String> after = dispositions(h);
            List<String> changed = new ArrayList<>();
            before.forEach((id, d) -> {
                if (!d.equals(after.get(id))) changed.add(id + " " + d + " -> " + after.get(id));
            });
            h.assertTrue(changed.isEmpty(), "Routes of curated mods do not depend on automatic detection: " + changed);
        } finally {
            ServerConfig.AUTO_DETECT_EQUIPMENT.set(true);
            SmithingData.rebuild(server);
            TestSupport.restoreServerConfig(config);
        }
        h.succeed();
    }

    private static Map<String, String> dispositions(GameTestHelper h) {
        Map<String, String> out = new LinkedHashMap<>();
        Set<String> curated = Integrations.curatedNamespaces();
        for (String mod : loadedMods()) {
            if (!curated.contains(mod)) continue;
            for (JsonElement e : WeaponCatalog.build(mod, h.getLevel().registryAccess()).getAsJsonArray("items")) {
                JsonObject item = e.getAsJsonObject();
                out.put(item.get("id").getAsString(), item.get("disposition").getAsString());
            }
        }
        return out;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void everyBundledRecipeCanBeMade(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Set<Item> obtainable = obtainableItems(server);
        List<String> problems = new ArrayList<>();
        for (SmithingRecipe r : bundled()) {
            MaterialFamily family = SmithingData.current().materials().family(r.family()).orElse(null);
            if (family == null || family.sources().isEmpty()) problems.add(r.getId() + ": no source of " + r.family());
            if (r.result().isEmpty() || r.resultItem() == net.minecraft.world.item.Items.AIR) problems.add(r.getId() + ": result is air");
            for (AuxiliaryIngredient aux : r.auxiliary()) {
                ItemStack[] items = aux.ingredient().getItems();
                if (items.length == 0) {
                    problems.add(r.getId() + ": a component matches nothing");
                } else if (java.util.Arrays.stream(items).noneMatch(s -> obtainable.contains(s.getItem()))) {
                    problems.add(r.getId() + ": no way to obtain " + BuiltInRegistries.ITEM.getKey(items[0].getItem()));
                }
            }
        }
        h.assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        h.succeed();
    }

    /** Items a player can get: Minecraft's own, anything a recipe of any kind makes, and anything a loot table names. */
    static Set<Item> obtainableItems(MinecraftServer server) {
        Set<Item> out = new HashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) out.add(item);
        }
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            try {
                ItemStack result = r.getResultItem(server.registryAccess());
                if (!result.isEmpty()) out.add(result.getItem());
            } catch (RuntimeException ignored) {
                // a broken recipe makes nothing
            }
        }
        var lootData = server.getLootData();
        var serializer = net.minecraft.world.level.storage.loot.Deserializers.createLootTableSerializer().create();
        StringBuilder text = new StringBuilder();
        for (ResourceLocation id : lootData.getKeys(LootDataType.TABLE)) {
            try {
                text.append(serializer.toJson(lootData.getLootTable(id))).append('\n');
            } catch (RuntimeException ignored) {
                // a table that cannot be written names nothing we need
            }
        }
        String all = text.toString();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!out.contains(item) && all.contains("\"" + BuiltInRegistries.ITEM.getKey(item) + "\"")) out.add(item);
        }
        return out;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bundledRecipesDisableExactlyWhatTheyName(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        SmithingData data = SmithingData.current();
        Set<ResourceLocation> loaded = data.sourceRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        for (SmithingRecipe r : bundled()) {
            Set<ResourceLocation> named = new HashSet<>(r.replaces());
            for (ResourceLocation id : r.replaces()) {
                if (loaded.contains(id) && server.getRecipeManager().byKey(id).isPresent()) problems.add(r.getId() + " leaves " + id + " active");
            }
            for (ResourceLocation id : r.follows()) {
                if (!named.contains(id) && server.getRecipeManager().byKey(id).isEmpty()) problems.add(r.getId() + " disabled " + id + ", which it only follows");
            }
            data.suppressedRecipes().forEach((id, item) -> {
                if (item == r.resultItem() && bundled().stream().noneMatch(variant -> variant.resultItem() == item && variant.replaces().contains(id)))
                    problems.add(id + " was disabled though no built-in recipe names it");
            });
        }
        h.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        h.succeed();
    }

    /**
     * The follow rule on test data: a recipe naming a native recipe that is not loaded stays inactive (a pack removed
     * it), one naming a loaded recipe is active and disables exactly that recipe.
     */
    @GameTest(template = "empty")
    public static void forgeRecipesFollowTheNativeRecipesTheyName(GameTestHelper h) {
        SmithingData data = SmithingData.current();
        ResourceLocation orphan = new ResourceLocation("immersive_smithing_test", "follows_missing");
        ResourceLocation follower = new ResourceLocation("immersive_smithing_test", "follows_present");
        ResourceLocation followed = new ResourceLocation("immersive_smithing_test", "followed_crafting");
        h.assertTrue(data.recipe(orphan) == null, "A recipe whose native recipe is gone is inactive");
        h.assertTrue(data.report().lines().stream().anyMatch(l -> l.contains(orphan.toString()) && l.contains("native recipes it follows")),
                "and the report says why");
        h.assertTrue(data.recipe(follower) != null, "A recipe whose native recipe is loaded is active");
        h.assertTrue(data.suppressedRecipes().containsKey(followed), "and disables the recipe it replaces");
        h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(followed).isEmpty(), "which is gone from the recipe manager");
        h.succeed();
    }

    // ------------------------------------------------------------------ switches and reloads

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void switchesAndReloadsAreRepeatable(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Set<ResourceLocation> before = ids(server);
        SmithingData.rebuild(server);
        h.assertTrue(ids(server).equals(before), "A second rebuild changes nothing");
        var config = TestSupport.isolateServerConfig();
        try {
            record Case(ForgeConfigSpec.BooleanValue option, List<String> namespaces) {}
            List<Case> cases = new ArrayList<>();
            if (ModList.get().isLoaded("magistuarmory")) cases.add(new Case(ServerConfig.EPIC_KNIGHTS_INTEGRATION, Integrations.EPIC_KNIGHTS_NAMESPACES));
            if (ModList.get().isLoaded("epicfight")) cases.add(new Case(ServerConfig.EPIC_FIGHT_INTEGRATION, List.of("epicfight")));
            for (Case c : cases) {
                try {
                    c.option().set(false);
                    SmithingData.rebuild(server);
                    long forged = SmithingData.current().recipes().stream()
                            .filter(r -> c.namespaces().contains(BuiltInRegistries.ITEM.getKey(r.resultItem()).getNamespace())).count();
                    h.assertTrue(forged == 0, c.namespaces().get(0) + " off: nothing of it is forged, got " + forged);
                    long nativeDisabled = SmithingData.current().suppressedRecipes().values().stream()
                            .filter(i -> c.namespaces().contains(BuiltInRegistries.ITEM.getKey(i).getNamespace())).count();
                    h.assertTrue(nativeDisabled == 0, c.namespaces().get(0) + " off: none of its recipes is disabled, got " + nativeDisabled);
                    Set<ResourceLocation> now = ids(server);
                    long builtIn = now.stream().filter(id -> {
                        String mod = Integrations.compatModOf(id);
                        return mod != null && c.namespaces().contains(mod);
                    }).count();
                    h.assertTrue(builtIn == 0, c.namespaces().get(0) + " off: no built-in recipe of it is left, got " + builtIn);
                } finally {
                    c.option().set(true);
                    SmithingData.rebuild(server);
                }
                h.assertTrue(ids(server).equals(before), c.namespaces().get(0) + " on again: the same recipes as before, nothing duplicated or lost");
            }
            // One add-on alone: the rest stays integrated.
            if (ModList.get().isLoaded("magistuarmoryaddon") && ModList.get().isLoaded("magistuarmory")) {
                var list = ServerConfig.EPIC_KNIGHTS_DISABLED_ADDONS;
                List<? extends String> previous = list.get();
                try {
                    list.set(List.of("magistuarmoryaddon"));
                    SmithingData.rebuild(server);
                    long addon = SmithingData.current().recipes().stream()
                            .filter(r -> BuiltInRegistries.ITEM.getKey(r.resultItem()).getNamespace().equals("magistuarmoryaddon")).count();
                    long base = SmithingData.current().recipes().stream()
                            .filter(r -> BuiltInRegistries.ITEM.getKey(r.resultItem()).getNamespace().equals("magistuarmory")).count();
                    h.assertTrue(addon == 0 && base > 0, "Only the switched-off add-on is left alone: add-on " + addon + ", base " + base);
                } finally {
                    list.set(previous);
                    SmithingData.rebuild(server);
                }
                h.assertTrue(ids(server).equals(before), "Add-on switch back on: the same recipes as before");
            }
        } finally {
            TestSupport.restoreServerConfig(config);
        }
        h.succeed();
    }

    private static Set<ResourceLocation> ids(MinecraftServer server) {
        return server.getRecipeManager().getRecipes().stream().map(Recipe::getId).collect(Collectors.toSet());
    }

    private CoverageGameTests() {}
}
