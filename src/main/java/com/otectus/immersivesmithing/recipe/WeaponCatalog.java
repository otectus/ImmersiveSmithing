package com.otectus.immersivesmithing.recipe;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.otectus.immersivesmithing.compat.CoverageDisposition;
import com.otectus.immersivesmithing.compat.CoveragePolicies;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.quality.QualityEligibility;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The audit behind {@code docs/compatibility/inventory}: every piece of equipment a mod registers and what Immersive Smithing does with
 * it, from the live registry and the last rebuild. One entry per item: its anvil shape, the forge recipes that make it
 * (material, units, components), what became of each of the mod's own recipes for it (disabled, kept with a reason, or
 * untouched), whether it can carry quality and what it recycles for. Written by {@code /immersivesmithing catalog} and
 * by the GameTests, which compare it with the checked-in manifest.
 */
public final class WeaponCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Builds the catalog of one namespace. Needs a rebuilt {@link SmithingData}. */
    public static JsonObject build(String namespace, RegistryAccess access) {
        SmithingData data = SmithingData.current();
        JsonObject root = new JsonObject();
        root.addProperty("mod", namespace);
        root.addProperty("version", ModList.get().getModContainerById(namespace)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("not loaded"));
        Integrations.forNamespace(namespace).ifPresent(i -> root.addProperty("integration", i.id()));
        // The other mods change routes (a tin supplier, Swappable Helms): the catalog is only comparable within one set.
        JsonArray loaded = new JsonArray();
        ModList.get().getMods().stream().map(m -> m.getModId()).filter(id -> !List.of("minecraft", "forge", "immersive_smithing").contains(id))
                .sorted().forEach(loaded::add);
        root.add("loaded_mods", loaded);
        String tested = CoveragePolicies.testedVersions().get(namespace);
        if (tested != null) root.addProperty("tested_version", tested);
        JsonArray items = new JsonArray();
        List<Item> registered = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(namespace)) registered.add(item);
        }
        registered.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        for (Item item : registered) {
            ItemStack stack = new ItemStack(item);
            if (!isEquipment(stack)) continue;
            items.add(entry(stack, data, access));
        }
        root.add("items", items);
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        items.forEach(e -> counts.merge(e.getAsJsonObject().get("disposition").getAsString(), 1, Integer::sum));
        JsonObject summary = new JsonObject();
        counts.forEach(summary::addProperty);
        root.add("dispositions", summary);
        return root;
    }

    /** Equipment worth an entry: anything the forge could consider, anything that deals attack damage, and any other
     *  unstackable item that wears down (firearms, flasks, horse armour has no durability and stays out). */
    static boolean isEquipment(ItemStack stack) {
        if (EquipmentClassifier.isCandidate(stack)) return true;
        if (stack.getMaxStackSize() != 1) return false;
        return stack.isDamageableItem() || !stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }

    private static JsonObject entry(ItemStack stack, SmithingData data, RegistryAccess access) {
        Item item = stack.getItem();
        JsonObject out = new JsonObject();
        out.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
        out.addProperty("kind", kind(stack));
        out.addProperty("shape", EquipmentClassifier.classify(stack).toString());
        JsonArray forge = new JsonArray();
        List<SmithingRecipe> recipes = new ArrayList<>(data.recipesProducing(item));
        recipes.sort(Comparator.comparing(r -> r.getId().toString()));
        for (SmithingRecipe r : recipes) {
            JsonObject f = new JsonObject();
            f.addProperty("recipe", r.getId().toString());
            f.addProperty("material", r.family().toString());
            f.addProperty("units", r.metalUnits());
            JsonArray aux = new JsonArray();
            r.auxiliary().forEach(a -> aux.add(describe(a)));
            f.add("components", aux);
            f.addProperty("pattern", r.anvilPattern().toString());
            if (r.sourceRecipe() != null) f.addProperty("from", r.sourceRecipe().toString());
            forge.add(f);
        }
        out.add("forge", forge);
        JsonArray natives = new JsonArray();
        List<Recipe<?>> sources = new ArrayList<>();
        for (Recipe<?> r : data.sourceRecipes()) {
            if (r instanceof SmithingRecipe) continue;
            try {
                ItemStack result = r.getResultItem(access);
                if (!result.isEmpty() && result.getItem() == item) sources.add(r);
            } catch (RuntimeException ignored) {
                // a broken third-party recipe makes nothing
            }
        }
        sources.sort(Comparator.comparing(r -> r.getId().toString()));
        for (Recipe<?> r : sources) {
            JsonObject n = new JsonObject();
            n.addProperty("recipe", r.getId().toString());
            ResourceLocation serializer = SourceRecipes.serializerId(r);
            n.addProperty("type", serializer != null ? serializer.toString() : "?");
            String state;
            if (data.suppressedRecipes().containsKey(r.getId())) state = "disabled: replaced by the forge recipe";
            else if (data.retainedRecipes().containsKey(r.getId())) state = "kept: " + data.retainedRecipes().get(r.getId());
            else state = "untouched";
            n.addProperty("state", state);
            natives.add(n);
        }
        out.add("native", natives);
        out.addProperty("quality", QualityEligibility.canCarryQuality(stack));
        Optional<RecyclingValue> value = MaterialResolver.recyclingValue(stack);
        out.addProperty("recycling", value.map(v -> v.units() + " " + v.family()).orElse("none"));
        String[] disposition = disposition(stack, recipes, sources, data);
        out.addProperty("disposition", disposition[0]);
        out.addProperty("reason", disposition[1]);
        return out;
    }

    /**
     * How the item is obtained, with the reason: forged; else why its forge recipe is inactive; else the reviewed decision
     * of the coverage data; else, for a curated integration, a gap ({@code missing_support}). Other mods (automatic
     * detection) are described from their native recipes.
     */
    private static String[] disposition(ItemStack stack, List<SmithingRecipe> recipes, List<Recipe<?>> sources, SmithingData data) {
        Item item = stack.getItem();
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        if (!Integrations.enabled(namespace)) {
            return pair(CoverageDisposition.INTEGRATION_DISABLED, "integration switched off in the server config; the mod is left as it ships");
        }
        if (!recipes.isEmpty()) {
            boolean auto = recipes.stream().allMatch(SmithingRecipe::isAuto);
            return pair(auto ? CoverageDisposition.FORGE_AUTO : CoverageDisposition.FORGE_DEFAULT,
                    recipes.stream().map(r -> r.getId().toString()).reduce((a, b) -> a + ", " + b).orElse(""));
        }
        List<String> inactive = data.report().inactiveFor(item);
        if (!inactive.isEmpty()) {
            String why = inactive.get(0);
            if (why.contains("unknown material family")) return pair(CoverageDisposition.MATERIAL_UNAVAILABLE, why);
            if (why.contains("integration is disabled")) return pair(CoverageDisposition.INTEGRATION_DISABLED, why);
            if (why.contains("native recipes it follows")) return pair(CoverageDisposition.DISABLED_BY_UPSTREAM, why);
            return pair(CoverageDisposition.MISSING_SUPPORT, why);
        }
        Optional<CoveragePolicies.Policy> policy = CoveragePolicies.policy(item);
        if (policy.isPresent()) return pair(policy.get().disposition(), policy.get().reason());
        if (Integrations.forNamespace(namespace).map(Integrations.Integration::curated).orElse(false)) {
            return pair(CoverageDisposition.MISSING_SUPPORT, "no forge recipe and no reviewed decision for this version of the mod");
        }
        // Automatic detection: explain from the native recipes.
        boolean kept = sources.stream().anyMatch(r -> data.retainedRecipes().containsKey(r.getId()));
        boolean untouched = sources.stream().anyMatch(r -> !data.retainedRecipes().containsKey(r.getId())
                && !data.suppressedRecipes().containsKey(r.getId()));
        if (sources.isEmpty()) {
            return QualityEligibility.canCarryQuality(stack) ? pair(CoverageDisposition.PROTECTED_LOOT, "loot or unique: graded when found, never forged")
                    : pair(CoverageDisposition.NON_EQUIPMENT, "no recipe: obtained by other means");
        }
        if (kept) return pair(CoverageDisposition.NATIVE_TRANSFORMATION, "made by the mod's own recipe, kept: "
                + sources.stream().filter(r -> data.retainedRecipes().containsKey(r.getId())).findFirst()
                        .map(r -> data.retainedRecipes().get(r.getId())).orElse(""));
        if (untouched) return pair(CoverageDisposition.NATIVE_NONMETAL_RECIPE, "crafted as the mod ships it");
        return pair(CoverageDisposition.MISSING_SUPPORT, "its native recipes are disabled but nothing forges it");
    }

    private static String[] pair(CoverageDisposition disposition, String reason) {
        return new String[]{disposition.id(), reason};
    }

    private static String kind(ItemStack stack) {
        EquipmentSlot slot = EquipmentClassifier.armorSlot(stack);
        if (slot != null) return "armor";
        if (EquipmentClassifier.isShield(stack)) return "shield";
        if (com.otectus.immersivesmithing.quality.QualityHooks.isMiningTool(stack)) return "tool";
        return "weapon";
    }

    /**
     * "2x minecraft:stick", "1x #minecraft:planks", "1x minecraft:iron_sword (reworked)". Tags are named, not expanded, so
     * the description does not change when another mod adds items to the tag.
     */
    static String describe(AuxiliaryIngredient aux) {
        return aux.count() + "x " + describe(aux.ingredient().toJson()) + (aux.consumesEquipment() ? " (reworked)" : "");
    }

    private static String describe(com.google.gson.JsonElement json) {
        if (json.isJsonArray()) {
            TreeSet<String> parts = new TreeSet<>();
            json.getAsJsonArray().forEach(e -> parts.add(describe(e)));
            return String.join(" | ", parts);
        }
        if (json.isJsonObject()) {
            JsonObject o = json.getAsJsonObject();
            if (o.has("tag")) return "#" + o.get("tag").getAsString();
            if (o.has("item")) return o.get("item").getAsString();
        }
        return json.toString();
    }

    public static Path write(Path directory, String namespace, RegistryAccess access) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("immersive_smithing_catalog_" + namespace + ".json");
        Files.writeString(file, GSON.toJson(build(namespace, access)) + "\n", StandardCharsets.UTF_8);
        return file;
    }

    public static String toJson(JsonObject catalog) {
        return GSON.toJson(catalog);
    }

    private WeaponCatalog() {}
}
