package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

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
        invalidRecipe(recipe.getId(), reason);
        inactiveByResult.computeIfAbsent(recipe.resultItem(), k -> new ArrayList<>()).add(recipe.getId() + ": " + reason);
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
    }

    public void skippedEquipment(Item item, ResourceLocation recipe, String reason) {
        skippedEquipment.add(BuiltInRegistries.ITEM.getKey(item) + " (" + recipe + "): " + reason);
        log("Not auto-detecting {} from {}: {}", BuiltInRegistries.ITEM.getKey(item), recipe, reason);
    }

    public void suppressed(ResourceLocation recipe, Item result) {
        suppressed.add(recipe + " -> " + BuiltInRegistries.ITEM.getKey(result));
    }

    /** A conventional recipe for smithable or candidate equipment that is deliberately left working, and why. */
    public void retained(ResourceLocation recipe, Item result, String reason) {
        retained.add(BuiltInRegistries.ITEM.getKey(result) + " (" + recipe + "): " + reason);
        retainedById.putIfAbsent(recipe, reason);
        log("Keeping {} for {}: {}", recipe, BuiltInRegistries.ITEM.getKey(result), reason);
    }

    /** The integrations switched off in the server config. */
    public void integrations(java.util.Collection<String> disabled) {
        for (String modId : disabled) integrations.add(modId + ": disabled in the config, left exactly as the mod ships it");
    }

    public void note(String note) {
        notes.add(note);
        log(note);
    }

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
        section(out, "Notes", notes);
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
