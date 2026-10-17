package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ISmithingMaterialProvider;
import com.otectus.immersivesmithing.api.ISmithingRecipeProvider;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.compat.CoveragePolicies;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialDefinition;
import com.otectus.immersivesmithing.material.MaterialDefinitionLoader;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.material.RecyclingOverride;
import com.otectus.immersivesmithing.material.RecyclingOverrideLoader;
import com.otectus.immersivesmithing.material.RecyclingValue;
import com.otectus.immersivesmithing.material.UpgradePolicy;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.minigame.PatternRegistry;
import com.otectus.immersivesmithing.registry.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable server-side snapshot of materials, active smithing recipes, recycling values and disabled
 * crafting recipes. Rebuilt after every datapack reload once tags are bound (never during gameplay ticks);
 * gameplay code only reads {@link #current()}.
 */
public final class SmithingData {
    private static final SmithingData EMPTY = new SmithingData(MaterialRegistry.EMPTY, List.of(), Map.of(), Set.of(), Map.of(), Map.of(),
            List.of(), new CompatibilityReport());
    private static volatile SmithingData current = EMPTY;
    private static volatile boolean dirty = true;

    private final MaterialRegistry materials;
    private final Map<ResourceLocation, SmithingRecipe> byId;
    private final Map<ResourceLocation, List<SmithingRecipe>> byFamily;
    private final Map<Item, List<SmithingRecipe>> byResult;
    private final Map<Item, RecyclingValue> recycling;
    private final Set<Item> recyclingExcluded;
    private final Map<ResourceLocation, Item> suppressed;
    private final Map<ResourceLocation, String> retained;
    private final List<Recipe<?>> sources;
    private final CompatibilityReport report;

    private SmithingData(MaterialRegistry materials, List<SmithingRecipe> recipes, Map<Item, RecyclingValue> recycling,
                         Set<Item> recyclingExcluded, Map<ResourceLocation, Item> suppressed, Map<ResourceLocation, String> retained,
                         List<Recipe<?>> sources, CompatibilityReport report) {
        this.materials = materials;
        Map<ResourceLocation, SmithingRecipe> ids = new LinkedHashMap<>();
        Map<ResourceLocation, List<SmithingRecipe>> families = new HashMap<>();
        Map<Item, List<SmithingRecipe>> results = new HashMap<>();
        for (SmithingRecipe r : recipes) {
            ids.put(r.getId(), r);
            families.computeIfAbsent(r.family(), k -> new ArrayList<>()).add(r);
            results.computeIfAbsent(r.resultItem(), k -> new ArrayList<>()).add(r);
        }
        families.replaceAll((k, v) -> List.copyOf(v));
        results.replaceAll((k, v) -> List.copyOf(v));
        this.byId = Map.copyOf(ids);
        this.byFamily = Map.copyOf(families);
        this.byResult = Map.copyOf(results);
        this.recycling = Map.copyOf(recycling);
        this.recyclingExcluded = Set.copyOf(recyclingExcluded);
        this.suppressed = Map.copyOf(suppressed);
        this.retained = Map.copyOf(retained);
        this.sources = List.copyOf(sources);
        this.report = report;
    }

    public static SmithingData current() {
        return current;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean isDirty() {
        return dirty;
    }

    public MaterialRegistry materials() { return materials; }
    @Nullable public SmithingRecipe recipe(ResourceLocation id) { return byId.get(id); }
    public Collection<SmithingRecipe> recipes() { return byId.values(); }
    public List<SmithingRecipe> recipesFor(@Nullable ResourceLocation family) { return family == null ? List.of() : byFamily.getOrDefault(family, List.of()); }
    public List<SmithingRecipe> recipesProducing(Item item) { return byResult.getOrDefault(item, List.of()); }
    @Nullable public RecyclingValue recycling(Item item) { return recycling.get(item); }
    public boolean isRecyclingExcluded(Item item) { return recyclingExcluded.contains(item); }
    /** Disabled conventional recipe ids and the item each one produced. */
    public Map<ResourceLocation, Item> suppressedRecipes() { return suppressed; }
    /** Conventional recipes for smithable or candidate equipment that were deliberately left working, with the reason. */
    public Map<ResourceLocation, String> retainedRecipes() { return retained; }
    /** The recipes as the last datapack load produced them, before this rebuild disabled or added any. */
    public List<Recipe<?>> sourceRecipes() { return sources; }
    public CompatibilityReport report() { return report; }

    /**
     * The recipes as the last datapack load produced them. The rebuild rewrites the recipe manager (disabled recipes
     * removed, automatic ones added), so a later rebuild without a datapack reload starts from this snapshot instead of
     * its own output: nothing is generated twice and no original recipe is lost when an option or integration changes.
     */
    private static List<Recipe<?>> sourceSnapshot = List.of();
    private static Set<Recipe<?>> lastWritten = Set.of();

    /** Rebuilds everything from the server's current recipes and tags, then rewrites its recipe manager. */
    public static synchronized void rebuild(MinecraftServer server) {
        long start = System.nanoTime();
        RecipeManager manager = server.getRecipeManager();
        RegistryAccess access = server.registryAccess();
        CompatibilityReport report = new CompatibilityReport();
        AutoRecipeDetector.Scope scope = AutoRecipeDetector.Scope.fromConfig();
        report.integrations(Integrations.disabledNamespaces());

        // 1. Material families.
        List<MaterialDefinition> definitions = new ArrayList<>(MaterialDefinitionLoader.definitions());
        for (ISmithingMaterialProvider provider : ImmersiveSmithingAPI.materialProviders()) {
            try {
                definitions.addAll(provider.materials());
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Smithing material provider failed", e);
            }
        }
        Map<ResourceLocation, MaterialDefinition> mergedDefs = new LinkedHashMap<>();
        definitions.forEach(d -> mergedDefs.merge(d.family(), d, MaterialDefinition::merge));
        // Extra sources for a family nobody defines (its mod is absent): nothing to add them to.
        mergedDefs.values().removeIf(d -> {
            if (d.sourcesOnly()) report.note("Sources for " + d.family() + " ignored: no material definition creates that family");
            return d.sourcesOnly();
        });
        MaterialRegistry materials = MaterialRegistry.build(mergedDefs.values(), report);

        // 2. Explicit recipes (datapack), validated against the families that actually exist.
        List<Recipe<?>> all = sourceRecipes(manager);
        List<SmithingRecipe> active = new ArrayList<>();
        Set<ResourceLocation> removed = new HashSet<>();
        Set<Item> explicitResults = new HashSet<>();
        List<SmithingRecipe> explicit = new ArrayList<>();
        Set<ResourceLocation> loadedIds = new HashSet<>();
        for (Recipe<?> r : all) {
            loadedIds.add(r.getId());
            if (r instanceof SmithingRecipe s) explicit.add(s);
            // A built-in crafting recipe (the bokken) follows its integration's switch like the forge recipes do.
            else if (Integrations.compatRecipeDisabled(r.getId()) != null) {
                removed.add(r.getId());
                report.invalidRecipe(r.getId(), Integrations.compatRecipeDisabled(r.getId()));
            }
        }
        for (ISmithingRecipeProvider provider : ImmersiveSmithingAPI.recipeProviders()) {
            try {
                explicit.addAll(provider.recipes(materials, access));
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Smithing recipe provider failed", e);
            }
        }
        explicit.sort(Comparator.comparing(r -> r.getId().toString()));
        Set<ResourceLocation> seenIds = new HashSet<>();
        for (SmithingRecipe r : explicit) {
            String reason = null;
            if (!seenIds.add(r.getId())) reason = "duplicate recipe id";
            else if (Integrations.compatRecipeDisabled(r.getId()) != null) reason = Integrations.compatRecipeDisabled(r.getId());
            else if (materials.family(r.family()).isEmpty()) reason = "unknown material family " + r.family();
            else if (r.result().is(ModTags.NON_SMITHABLE_EQUIPMENT) || ImmersiveSmithingAPI.isExcluded(r.result())) reason = "result is excluded from smithing";
            else if (!r.follows().isEmpty() && r.follows().stream().noneMatch(loadedIds::contains)) {
                // The pack removed the native recipe (or this version of the mod has none): the forge alternative goes too.
                reason = "none of the native recipes it follows is loaded " + r.follows();
            }
            if (reason != null) {
                removed.add(r.getId());
                report.invalidRecipe(r, reason);
                continue;
            }
            active.add(r);
            explicitResults.add(r.resultItem());
            report.explicitRecipe(r);
        }

        // 3. Crafting analysis, used both for automatic recipes and for recycling values.
        Map<ResourceLocation, AutoRecipeDetector.Analysis> analyses = AutoRecipeDetector.analyzeCrafting(all, access, materials, explicitResults, report);
        List<SmithingRecipe> auto = new ArrayList<>();
        if (ServerConfig.get(ServerConfig.AUTO_DETECT_EQUIPMENT)) {
            auto.addAll(AutoRecipeDetector.generate(analyses.values(), explicitResults, scope, report));
            // Smithing-table upgrades (modded netherite and similar) are redirected into the forge as well.
            Map<Item, SmithingRecipe> smithable = new HashMap<>();
            active.forEach(r -> smithable.putIfAbsent(r.resultItem(), r));
            auto.forEach(r -> smithable.putIfAbsent(r.resultItem(), r));
            AutoRecipeDetector.UpgradeContext upgrades = new AutoRecipeDetector.UpgradeContext(all, access, materials, smithable);
            List<SmithingRecipe> redirected = AutoRecipeDetector.generateUpgrades(all, upgrades, scope, report);
            redirected.forEach(r -> smithable.putIfAbsent(r.resultItem(), r));
            auto.addAll(redirected);
            // Crafting recipes that rework a finished piece with more metal, priced from the base's own recipe.
            auto.addAll(AutoRecipeDetector.generateChains(analyses.values(), upgrades, scope, report));
        }
        active.addAll(auto);

        // 4. Presentation data travels with each recipe to the client.
        for (SmithingRecipe r : active) {
            MaterialFamily family = materials.family(r.family()).orElseThrow();
            ResourceLocation pattern = r.explicitAnvilPattern() != null ? r.explicitAnvilPattern() : EquipmentClassifier.classify(r.result());
            if (!PatternRegistry.hasAnvilPattern(pattern)) {
                report.note("Anvil pattern " + pattern + " for " + r.getId() + " is not defined; the generic pattern is used");
            }
            r.setDisplay(family.displayName(), SmithingRecipe.metalDisplay(family, r.metalUnits()), pattern);
        }

        // 5. Disable a conventional recipe only where a forge recipe replaces that very recipe. A recipe that names what it
        // replaces (the bundled integration data) decides exactly; everything else follows the analysis below.
        Map<Item, List<SmithingRecipe>> forgeByResult = new HashMap<>();
        Map<Item, Set<ResourceLocation>> declared = new HashMap<>();
        active.forEach(r -> {
            forgeByResult.computeIfAbsent(r.resultItem(), k -> new ArrayList<>()).add(r);
            if (!r.replaces().isEmpty()) declared.computeIfAbsent(r.resultItem(), k -> new HashSet<>()).addAll(r.replaces());
        });
        Map<ResourceLocation, Item> suppressed = new HashMap<>();
        boolean replace = ServerConfig.get(ServerConfig.REPLACE_METAL_EQUIPMENT_RECIPES)
                && !ServerConfig.get(ServerConfig.ALLOW_VANILLA_CRAFTING_ALONGSIDE_SMITHING);
        if (replace) {
            boolean smithingTable = ServerConfig.get(ServerConfig.SUPPRESS_SMITHING_TABLE_UPGRADES);
            for (Recipe<?> r : all) {
                if (r instanceof SmithingRecipe || scope.skipsRecipe(r.getId())) continue;
                ItemStack result;
                try {
                    result = r.getResultItem(access);
                } catch (RuntimeException e) {
                    continue;
                }
                if (result.isEmpty()) continue;
                List<SmithingRecipe> forge = forgeByResult.get(result.getItem());
                if (forge == null) continue;
                if (scope.skipsItem(result.getItem())) continue;
                String kept = null;
                Set<ResourceLocation> named = declared.get(result.getItem());
                if (named != null) {
                    if (r.getType() != RecipeType.CRAFTING && r.getType() != RecipeType.SMITHING) continue;
                    SourceRecipes.Kind kind = SourceRecipes.kind(r);
                    if (!named.contains(r.getId())) {
                        kept = "not one of the native recipes the built-in forge recipe replaces (kept by design)";
                    } else if (r.getType() == RecipeType.CRAFTING && !kind.isCrafting()) {
                        kept = "custom recipe type " + SourceRecipes.describe(r) + " is never disabled";
                    } else if (r.getType() == RecipeType.SMITHING && kind != SourceRecipes.Kind.SMITHING_UPGRADE) {
                        kept = "custom smithing recipe " + SourceRecipes.describe(r) + " is never disabled";
                    } else if (r.getType() == RecipeType.SMITHING && !smithingTable) {
                        kept = "smithing-table upgrades are kept by the config";
                    }
                    if (kept != null) {
                        report.retained(r.getId(), result.getItem(), kept);
                    } else {
                        suppressed.put(r.getId(), result.getItem());
                        report.suppressed(r.getId(), result.getItem());
                    }
                    continue;
                }
                if (r.getType() == RecipeType.CRAFTING) {
                    AutoRecipeDetector.Analysis a = analyses.get(r.getId());
                    if (a == null) continue; // custom recipe types are reported by the analysis
                    if (!a.hasMetal()) {
                        // No raw metal: it remakes a finished piece (a sword set on a shaft), next to the forge recipe.
                        if (a.base() != null) report.retained(r.getId(), result.getItem(), "remakes a finished piece without adding metal; kept next to the forge recipe");
                        continue;
                    }
                    if (replacing(forge, r.getId(), a.family()) == null) {
                        kept = a.family() == null ? a.detail() + "; no forge recipe reproduces it" : "no forge recipe uses its metal " + a.family();
                    }
                } else if (r.getType() == RecipeType.SMITHING) {
                    // Only upgrade recipes. Trim recipes report a sample armor piece as their result.
                    if (!smithingTable || !(r instanceof SmithingTransformRecipe upgrade)) continue;
                    if (SourceRecipes.kind(r) != SourceRecipes.Kind.SMITHING_UPGRADE) continue; // custom: kept and reported
                    if (replacingUpgrade(forge, upgrade, materials) == null) {
                        kept = "no forge recipe reproduces this upgrade (its addition or base differs)";
                    }
                } else {
                    continue;
                }
                if (kept != null) {
                    report.retained(r.getId(), result.getItem(), kept);
                    continue;
                }
                suppressed.put(r.getId(), result.getItem());
                report.suppressed(r.getId(), result.getItem());
            }
        }

        // 6. Rewrite the recipe manager: remove disabled/invalid entries, add the automatic recipes.
        List<Recipe<?>> next = new ArrayList<>(all.size() + auto.size());
        for (Recipe<?> r : all) {
            if (!removed.contains(r.getId()) && !suppressed.containsKey(r.getId())) next.add(r);
        }
        next.addAll(auto);
        manager.replaceRecipes(next);
        Set<Recipe<?>> written = Collections.newSetFromMap(new IdentityHashMap<>());
        written.addAll(manager.getRecipes());
        lastWritten = written;

        // 7. Recycling values. Exclusions first, then explicit recipes, datapack overrides, crafting analysis.
        Set<Item> excluded = new HashSet<>();
        Map<Item, RecyclingValue> recycling = new HashMap<>();
        List<RecyclingOverride> overrides = RecyclingOverrideLoader.overrides();
        for (RecyclingOverride o : overrides) {
            if (!o.recyclable()) excluded.addAll(itemsOf(o));
        }
        for (SmithingRecipe r : active) {
            if (!r.isAuto()) recycling.putIfAbsent(r.resultItem(), new RecyclingValue(r.family(), r.metalUnits(), "smithing recipe " + r.getId()));
        }
        for (RecyclingOverride o : overrides) {
            if (!o.recyclable() || o.family() == null) continue;
            if (materials.family(o.family()).isEmpty()) {
                report.note("Recycling override for " + (o.item() != null ? o.item() : "#" + o.tag().location()) + " names unknown family " + o.family());
                continue;
            }
            for (Item item : itemsOf(o)) recycling.putIfAbsent(item, new RecyclingValue(o.family(), o.units(), "recycling override"));
        }
        for (AutoRecipeDetector.Analysis a : analyses.values()) {
            if (a.status() != AutoRecipeDetector.Status.OK || a.result().getCount() != 1) continue;
            if (!EquipmentClassifier.isCandidate(a.result())) continue;
            recycling.putIfAbsent(a.result().getItem(), new RecyclingValue(a.family(), a.units(), "crafting recipe " + a.recipeId()));
        }
        for (SmithingRecipe r : auto) {
            recycling.putIfAbsent(r.resultItem(), new RecyclingValue(r.family(), r.metalUnits(), "automatic recipe " + r.getId()));
        }
        inheritRecyclingValues(all, analyses, access, materials, recycling, report);
        // Another state of the same piece (an opened visor) melts for exactly what the piece melts for.
        CoveragePolicies.all().forEach((item, policy) -> {
            if (policy.stateOf() == null || recycling.containsKey(item) || !BuiltInRegistries.ITEM.containsKey(policy.stateOf())) return;
            RecyclingValue piece = recycling.get(BuiltInRegistries.ITEM.get(policy.stateOf()));
            if (piece != null) recycling.put(item, new RecyclingValue(piece.family(), piece.units(), "state of " + policy.stateOf() + " (" + piece.rule() + ")"));
        });
        excluded.forEach(recycling::remove);

        report.logInactiveBuiltIn();
        SmithingData data = new SmithingData(materials, active, recycling, excluded, suppressed, report.retainedById(), all, report);
        current = data;
        dirty = false;
        ImmersiveSmithing.LOGGER.info("Immersive Smithing: {} ({} ms)", report.summary(), (System.nanoTime() - start) / 1_000_000);
    }

    /**
     * The recipes to build from: the manager's own list after a datapack load, or the snapshot of that list when the
     * manager still holds what the previous rebuild wrote into it. Generated recipes are never part of the source.
     */
    private static List<Recipe<?>> sourceRecipes(RecipeManager manager) {
        Collection<Recipe<?>> currentRecipes = manager.getRecipes();
        boolean ownOutput = !lastWritten.isEmpty() && currentRecipes.size() == lastWritten.size();
        if (ownOutput) {
            for (Recipe<?> r : currentRecipes) {
                if (!lastWritten.contains(r)) {
                    ownOutput = false;
                    break;
                }
            }
        }
        if (!ownOutput) {
            List<Recipe<?>> fresh = new ArrayList<>(currentRecipes.size());
            for (Recipe<?> r : currentRecipes) {
                if (!(r instanceof SmithingRecipe s && s.isAuto())) fresh.add(r); // stale generated recipe from an earlier pass
            }
            sourceSnapshot = List.copyOf(fresh);
        }
        return sourceSnapshot;
    }

    /** The forge recipe that replaces a crafting recipe: generated from it, or making its result from the same metal. */
    @Nullable
    private static SmithingRecipe replacing(List<SmithingRecipe> forge, ResourceLocation source, @Nullable ResourceLocation family) {
        for (SmithingRecipe r : forge) {
            if (source.equals(r.sourceRecipe())) return r;
        }
        if (family == null) return null;
        for (SmithingRecipe r : forge) {
            if (r.family().equals(family)) return r;
        }
        return null;
    }

    /**
     * The forge recipe that replaces a vanilla smithing-table upgrade: generated from it, or an explicit recipe in the
     * addition's metal that either needs no base (the {@code shape} policy) or reworks a base this upgrade accepts.
     */
    @Nullable
    private static SmithingRecipe replacingUpgrade(List<SmithingRecipe> forge, SmithingTransformRecipe upgrade, MaterialRegistry materials) {
        for (SmithingRecipe r : forge) {
            if (upgrade.getId().equals(r.sourceRecipe())) return r;
        }
        Set<ResourceLocation> additions = AutoRecipeDetector.additionFamilies(upgrade, materials);
        if (additions.size() != 1) return null;
        ResourceLocation family = additions.iterator().next();
        for (SmithingRecipe r : forge) {
            if (r.isAuto() || !r.family().equals(family)) continue;
            boolean reworksBase = r.auxiliary().stream().anyMatch(a -> a.consumesEquipment()
                    && Arrays.stream(a.ingredient().getItems()).anyMatch(upgrade::isBaseIngredient));
            if (reworksBase || AutoRecipeDetector.policy(materials, family) == UpgradePolicy.SHAPE) return r;
        }
        return null;
    }

    /**
     * A recipe that changes a single finished piece without adding metal (a runic upgrade, a sword made into a spear)
     * leaves its metal in place: the result recycles for exactly what the piece did. Gems, templates and other
     * components are never refunded. Chains are followed a few steps; an item with conflicting answers gets none.
     */
    private static void inheritRecyclingValues(List<Recipe<?>> all, Map<ResourceLocation, AutoRecipeDetector.Analysis> analyses,
                                               RegistryAccess access, MaterialRegistry materials, Map<Item, RecyclingValue> recycling,
                                               CompatibilityReport report) {
        Map<Item, List<ItemStack[]>> sources = new LinkedHashMap<>();
        for (AutoRecipeDetector.Analysis a : analyses.values()) {
            if (a.status() != AutoRecipeDetector.Status.NOT_METAL || a.base() == null || a.result().getCount() != 1) continue;
            if (!EquipmentClassifier.isCandidate(a.result())) continue;
            sources.computeIfAbsent(a.result().getItem(), k -> new ArrayList<>()).add(a.base().getItems());
        }
        List<SmithingTransformRecipe> upgrades = new ArrayList<>();
        for (Recipe<?> r : all) {
            if (r instanceof SmithingTransformRecipe t && SourceRecipes.kind(r) == SourceRecipes.Kind.SMITHING_UPGRADE) upgrades.add(t);
        }
        upgrades.sort(Comparator.comparing(r -> r.getId().toString()));
        List<ItemStack> unstackables = null;
        for (SmithingTransformRecipe t : upgrades) {
            ItemStack result;
            try {
                result = t.getResultItem(access);
            } catch (RuntimeException e) {
                continue;
            }
            if (result.isEmpty() || result.getCount() != 1 || !EquipmentClassifier.isCandidate(result)) continue;
            // A metal upgrade the forge makes already has its own value (checked below). One it does not make (gilding with a
            // gold ingot, a template the forge never reproduces) melts for its base piece only: the addition is not refunded.
            if (unstackables == null) {
                unstackables = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    ItemStack stack = new ItemStack(item);
                    if (stack.getMaxStackSize() == 1 && stack.isDamageableItem()) unstackables.add(stack);
                }
            }
            List<ItemStack> bases = new ArrayList<>();
            for (ItemStack candidate : unstackables) {
                if (t.isBaseIngredient(candidate)) bases.add(candidate);
            }
            if (!bases.isEmpty()) sources.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(bases.toArray(ItemStack[]::new));
        }
        for (int pass = 0; pass < 4; pass++) {
            boolean changed = false;
            for (Map.Entry<Item, List<ItemStack[]>> entry : sources.entrySet()) {
                if (recycling.containsKey(entry.getKey())) continue;
                RecyclingValue inherited = null;
                boolean conflict = false;
                boolean pending = false;
                for (ItemStack[] alternatives : entry.getValue()) {
                    for (ItemStack base : alternatives) {
                        RecyclingValue value = recycling.get(base.getItem());
                        if (value == null) {
                            pending = true;
                            continue;
                        }
                        if (inherited == null) inherited = value;
                        else if (!inherited.family().equals(value.family()) || inherited.units() != value.units()) conflict = true;
                    }
                }
                if (inherited == null || conflict || pending) {
                    if (conflict && pass == 3) report.note("No recycling value for " + BuiltInRegistries.ITEM.getKey(entry.getKey())
                            + ": the pieces it is made from recycle differently");
                    continue;
                }
                recycling.put(entry.getKey(), new RecyclingValue(inherited.family(), inherited.units(),
                        "keeps the metal of the piece it is made from (" + inherited.rule() + ")"));
                changed = true;
            }
            if (!changed) break;
        }
    }

    private static List<Item> itemsOf(RecyclingOverride o) {
        if (o.item() != null) {
            return BuiltInRegistries.ITEM.containsKey(o.item()) ? List.of(BuiltInRegistries.ITEM.get(o.item())) : List.of();
        }
        return BuiltInRegistries.ITEM.getTag(o.tag()).map(s -> s.stream().map(Holder::value).toList()).orElse(List.of());
    }

    /** Clears state when the server stops, so a later world starts clean. */
    public static void reset() {
        current = EMPTY;
        dirty = true;
        sourceSnapshot = List.of();
        lastWritten = Set.of();
    }
}
