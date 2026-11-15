package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Everything the last rebuild decided, for {@code /immersivesmithing report} and compatibility bug reports. */
public final class CompatibilityReport {
    private final List<String> families = new ArrayList<>();
    private final List<String> skippedFamilies = new ArrayList<>();
    private final List<String> ambiguousItems = new ArrayList<>();
    private final List<String> explicitRecipes = new ArrayList<>();
    private final List<String> invalidRecipes = new ArrayList<>();
    private final List<String> autoRecipes = new ArrayList<>();
    private final List<String> skippedEquipment = new ArrayList<>();
    private final List<String> suppressed = new ArrayList<>();
    private final List<String> retained = new ArrayList<>();
    private final java.util.Map<ResourceLocation, String> retainedById = new java.util.LinkedHashMap<>();
    private final List<String> integrations = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final java.util.Map<String, Integer> inactiveBuiltIn = new java.util.TreeMap<>();
    private final java.util.Map<Item, List<String>> inactiveByResult = new java.util.HashMap<>();
    private final List<String> notConsidered = new ArrayList<>();
    private final List<String> untouched = new ArrayList<>();
    /** Configured selectors and the matches that are not item decisions (a skipped material family). */
    private final java.util.Map<String, Integer> selectors = new java.util.TreeMap<>();
    private final List<String> selectorProblems = new ArrayList<>();
    private final RecipeDecisions decisions = new RecipeDecisions();

    public void family(MaterialFamily f) {
        families.add(f.id() + (f.auto() ? " (auto)" : "") + ": " + f.sources().size() + " source items");
    }

    public void skippedFamily(ResourceLocation id, String reason) {
        skippedFamilies.add(id + ": " + reason);
        log("Skipped material family {}: {}", id, reason);
    }

    public void ambiguousItem(Item item, String detail) {
        ambiguousItems.add(BuiltInRegistries.ITEM.getKey(item) + " claimed by " + detail);
        log("Ambiguous material item {} ({})", BuiltInRegistries.ITEM.getKey(item), detail);
    }

    public void explicitRecipe(SmithingRecipe r) {
        explicitRecipes.add(r.getId() + " -> " + BuiltInRegistries.ITEM.getKey(r.resultItem()) + " [" + r.family() + ", " + r.metalUnits() + " units]");
        decision(r.resultItem(), r.getId(), RecipeDecisions.Origin.EXPLICIT, RecipeDecisions.Outcome.ACTIVE, r.getId(), "", null);
    }

    public void invalidRecipe(ResourceLocation id, String reason) {
        invalidRecipes.add(id + ": " + reason);
        if (com.otectus.immersivesmithing.compat.Integrations.compatModOf(id) != null) {
            // Built-in recipes for a mod whose metal or integration is missing: one summary line per reason, not hundreds.
            inactiveBuiltIn.merge(reason.startsWith("none of the native recipes") ? "the native recipe it follows is not loaded" : reason, 1, Integer::sum);
            log("Built-in smithing recipe {} is inactive: {}", id, reason);
        } else {
            ImmersiveSmithing.LOGGER.warn("Smithing recipe {} is inactive: {}", id, reason);
        }
    }

    /** An inactive smithing recipe, remembered for the coverage catalog of its result. */
    public void invalidRecipe(SmithingRecipe recipe, String reason) {
        invalidRecipe(recipe, reason, null);
    }

    /** An inactive smithing recipe and the configuration selector that made it so, if one did. */
    public void invalidRecipe(SmithingRecipe recipe, String reason, @Nullable String selector) {
        invalidRecipe(recipe.getId(), reason);
        inactiveByResult.computeIfAbsent(recipe.resultItem(), k -> new ArrayList<>()).add(recipe.getId() + ": " + reason);
        decision(recipe.resultItem(), recipe.getId(), RecipeDecisions.Origin.EXPLICIT, RecipeDecisions.Outcome.INACTIVE, null, reason, selector);
    }

    /** Why the built-in or datapack recipes for this item are inactive, if any are. */
    public List<String> inactiveFor(Item item) {
        return inactiveByResult.getOrDefault(item, List.of());
    }

    /** Logs the summary of inactive built-in recipes once per rebuild. */
    public void logInactiveBuiltIn() {
        inactiveBuiltIn.forEach((reason, count) ->
                ImmersiveSmithing.LOGGER.info("Immersive Smithing: {} built-in recipe(s) inactive: {}", count, reason));
    }

    public void autoRecipe(SmithingRecipe r) {
        boolean reworks = r.auxiliary().stream().anyMatch(AuxiliaryIngredient::consumesEquipment);
        autoRecipes.add(BuiltInRegistries.ITEM.getKey(r.resultItem()) + " from " + r.sourceRecipe()
                + " [" + r.family() + ", " + r.metalUnits() + " units, " + r.auxiliary().size() + " auxiliary"
                + (reworks ? ", reworks the base piece" : "") + "]");
        log("Auto-detected smithing recipe for {} from {}", BuiltInRegistries.ITEM.getKey(r.resultItem()), r.sourceRecipe());
        decision(r.resultItem(), r.sourceRecipe(), RecipeDecisions.Origin.AUTOMATIC, RecipeDecisions.Outcome.GENERATED, r.getId(), "", null);
    }

    public void skippedEquipment(Item item, ResourceLocation recipe, String reason) {
        skippedEquipment(item, recipe, reason, null);
    }

    /** Equipment automatic detection made nothing for, and the configuration selector that decided, if one did. */
    public void skippedEquipment(Item item, ResourceLocation recipe, String reason, @Nullable String selector) {
        skippedEquipment.add(BuiltInRegistries.ITEM.getKey(item) + " (" + recipe + "): " + reason);
        log("Not auto-detecting {} from {}: {}", BuiltInRegistries.ITEM.getKey(item), recipe, reason);
        decision(item, recipe, RecipeDecisions.Origin.AUTOMATIC, RecipeDecisions.Outcome.SKIPPED, null, reason, selector);
    }

    /**
     * A metal recipe whose result the forge does not consider at all (a crossbow, an item tagged non-smithable): not
     * equipment the forge makes, so nothing is generated or disabled for it.
     */
    public void notConsidered(Item item, ResourceLocation recipe, String reason, @Nullable String selector) {
        notConsidered.add(BuiltInRegistries.ITEM.getKey(item) + " (" + recipe + "): " + reason);
        log("Not considering {} from {}: {}", BuiltInRegistries.ITEM.getKey(item), recipe, reason);
        decision(item, recipe, RecipeDecisions.Origin.AUTOMATIC, RecipeDecisions.Outcome.SKIPPED, null, reason, selector);
    }

    public void suppressed(ResourceLocation recipe, Item result) {
        suppressed(recipe, result, null);
    }

    /** A native recipe replaced by {@code by} (the forge recipe generated from it or making the same item) and removed. */
    public void suppressed(ResourceLocation recipe, Item result, @Nullable ResourceLocation by) {
        suppressed.add(recipe + " -> " + BuiltInRegistries.ITEM.getKey(result));
        decision(result, recipe, RecipeDecisions.Origin.NATIVE, RecipeDecisions.Outcome.DISABLED, by,
                by != null ? "replaced by the forge recipe" : "replaced by a forge recipe", null);
    }

    /** A conventional recipe for smithable or candidate equipment that is deliberately left working, and why. */
    public void retained(ResourceLocation recipe, Item result, String reason) {
        retained(recipe, result, reason, null);
    }

    public void retained(ResourceLocation recipe, Item result, String reason, @Nullable String selector) {
        retained.add(BuiltInRegistries.ITEM.getKey(result) + " (" + recipe + "): " + reason);
        retainedById.putIfAbsent(recipe, reason);
        log("Keeping {} for {}: {}", recipe, BuiltInRegistries.ITEM.getKey(result), reason);
        decision(result, recipe, RecipeDecisions.Origin.NATIVE, RecipeDecisions.Outcome.KEPT, null, reason, selector);
    }

    /** A native recipe of equipment no forge recipe makes: it simply stays (diamond, wood, leather and other non-metal work). */
    public void untouched(ResourceLocation recipe, Item result) {
        untouched.add(BuiltInRegistries.ITEM.getKey(result) + " (" + recipe + ")");
        decision(result, recipe, RecipeDecisions.Origin.NATIVE, RecipeDecisions.Outcome.UNTOUCHED, null, "no forge recipe makes this item", null);
    }

    /** A decision that is not a report line of its own (a second source for an item already made, a curated namespace...). */
    public void decision(Item item, @Nullable ResourceLocation recipe, RecipeDecisions.Origin origin, RecipeDecisions.Outcome outcome,
                         @Nullable ResourceLocation smithing, String reason, @Nullable String selector) {
        decisions.add(new RecipeDecisions.Decision(item, recipe, origin, outcome, smithing, reason, selector));
    }

    public RecipeDecisions decisions() {
        return decisions;
    }

    /** A configured selector ({@code recipes.modBlacklist = examplemod}); the report counts what it matched. */
    public void selector(String selector) {
        selectors.putIfAbsent(selector, 0);
    }

    /** A match of a selector that is no item decision (a material family it skipped). */
    public void selectorMatched(String selector) {
        selectors.merge(selector, 1, Integer::sum);
    }

    /** A selector entry that could not be used (unparsable, an unknown item or an empty tag). */
    public void selectorProblem(String problem) {
        selectorProblems.add(problem);
        log(problem);
    }

    /** The integrations switched off in the server config. */
    public void integrations(java.util.Collection<String> disabled) {
        for (String modId : disabled) integrations.add(modId + ": "
                + (com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.owns(modId)
                ? com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.reason() : "disabled in the config; native routes retained"));
        var companions = java.util.Map.of("quick_swap", "0.1.0", "overgeared_epic_knights", "1.20.1-1.1.1",
                "knightsofterrafirma", "3.1.2|3.1.2-gregified", "bettercombat", "1.9.0+1.20.1",
                "epic_knights_x_better_combat", "1.0.1-1.20.1+1.21.1");
        net.minecraftforge.fml.ModList.get().getMods().stream().sorted(java.util.Comparator.comparing(m -> m.getModId())).forEach(mod -> {
            String expected = com.otectus.immersivesmithing.compat.CoveragePolicies.testedVersions().get(mod.getModId());
            if (expected == null) expected = companions.get(mod.getModId());
            if (expected == null && !com.otectus.immersivesmithing.compat.Integrations.EPIC_KNIGHTS_NAMESPACES.contains(mod.getModId())) return;
            String version = mod.getVersion().toString();
            boolean recognized = expected != null && java.util.Arrays.asList(expected.split("\\|")).contains(version);
            notes.add("Loaded " + mod.getModId() + " " + version + ": " + (recognized ? "recognized pinned version"
                    : "UNKNOWN VERSION; guarded mappings/adapters may be inactive, inspect the catalog before claiming support"));
        });
    }

    public void note(String note) {
        notes.add(note);
        log(note);
    }

    /** Whether the native recipe is already reported as kept. */
    boolean isRetained(ResourceLocation recipe) { return retainedById.containsKey(recipe); }

    public int autoCount() { return autoRecipes.size(); }
    public int explicitCount() { return explicitRecipes.size(); }
    public int suppressedCount() { return suppressed.size(); }
    public int familyCount() { return families.size(); }
    public int skippedCount() { return skippedEquipment.size(); }
    public int retainedCount() { return retained.size(); }
    public List<String> skippedEquipment() { return List.copyOf(skippedEquipment); }
    public List<String> retainedRecipes() { return List.copyOf(retained); }
    public java.util.Map<ResourceLocation, String> retainedById() { return java.util.Map.copyOf(retainedById); }

    public String summary() {
        return families.size() + " material families, " + explicitRecipes.size() + " explicit and " + autoRecipes.size()
                + " automatic smithing recipes, " + suppressed.size() + " crafting recipes disabled, "
                + retained.size() + " kept, " + skippedEquipment.size() + " equipment recipes skipped";
    }

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        out.add("Immersive Smithing compatibility report - " + LocalDateTime.now());
        out.add(summary());
        section(out, "Coverage by mod (explicit / automatic / skipped / native kept)", coverage());
        section(out, "Integrations switched off", integrations);
        section(out, "Material families", families);
        section(out, "Skipped material families", skippedFamilies);
        section(out, "Ambiguous material items (claimed by several families, ignored)", ambiguousItems);
        section(out, "Explicit smithing recipes", explicitRecipes);
        section(out, "Inactive smithing recipes", invalidRecipes);
        section(out, "Automatically detected equipment", autoRecipes);
        section(out, "Skipped or unsupported equipment", skippedEquipment);
        section(out, "Disabled crafting recipes", suppressed);
        section(out, "Native recipes kept (no forge recipe reproduces them, or the mod's own mechanic)", retained);
        section(out, "Native equipment recipes left untouched (no forge recipe makes the item)", untouched);
        section(out, "Metal recipes of items the forge does not consider", notConsidered);
        section(out, "Configuration selectors (decisions each one made)", selectorLines());
        section(out, "Exclusions applied", exclusionLines());
        section(out, "Notes", notes);
        return out;
    }

    private List<String> selectorLines() {
        java.util.Map<String, List<RecipeDecisions.Decision>> made = decisions.bySelector();
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>(selectors);
        made.forEach((selector, list) -> counts.merge(selector, list.size(), Integer::sum));
        List<String> out = new ArrayList<>();
        counts.forEach((selector, count) -> out.add(selector + ": " + count + (count == 1 ? " match" : " matches")));
        selectorProblems.forEach(problem -> out.add("problem: " + problem));
        return out;
    }

    private List<String> exclusionLines() {
        List<String> out = new ArrayList<>();
        decisions.bySelector().forEach((selector, list) -> list.forEach(d ->
                out.add(BuiltInRegistries.ITEM.getKey(d.output()) + " " + decisions.describe(d))));
        return out;
    }

    /** One line per item namespace: how many recipes the forge makes for it, and how many it declined. */
    private List<String> coverage() {
        java.util.Map<String, int[]> counts = new java.util.TreeMap<>();
        java.util.function.BiConsumer<List<String>, Integer> tally = (lines, column) -> {
            for (String line : lines) {
                String id = column == 0 ? line.substring(line.indexOf("-> ") + 3) : line;
                int colon = id.indexOf(':');
                if (colon <= 0) continue;
                counts.computeIfAbsent(id.substring(0, colon), k -> new int[4])[column]++;
            }
        };
        tally.accept(explicitRecipes, 0);
        tally.accept(autoRecipes, 1);
        tally.accept(skippedEquipment, 2);
        tally.accept(retained, 3);
        List<String> out = new ArrayList<>();
        counts.forEach((ns, c) -> out.add(String.format("%-24s %4d / %4d / %4d / %4d", ns, c[0], c[1], c[2], c[3])));
        return out;
    }

    public Path write(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("immersive_smithing_report.txt");
        Files.write(file, lines(), StandardCharsets.UTF_8);
        return file;
    }

    private static void section(List<String> out, String title, List<String> lines) {
        out.add("");
        out.add("== " + title + " (" + lines.size() + ")");
        lines.stream().sorted().forEach(l -> out.add("  " + l));
    }

    private static void log(String message, Object... args) {
        if (ServerConfig.get(ServerConfig.DEBUG_LOGGING)) {
            ImmersiveSmithing.LOGGER.info(message, args);
        } else {
            ImmersiveSmithing.LOGGER.debug(message, args);
        }
    }
}
