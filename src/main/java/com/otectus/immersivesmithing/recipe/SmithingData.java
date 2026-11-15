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
    /** What the rebuild decided about every recipe for this item, and the selector that decided, if one did. */
    public List<RecipeDecisions.Decision> decisionsFor(Item item) { return report.decisions().decisionsFor(item); }

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
        report.integrations(Integrations.disabledNamespaces());
        // One parse of every selector per rebuild, after tags are bound; each pass below asks the same object.
        AutoRecipeDetector.Scope scope = AutoRecipeDetector.Scope.fromConfig(report);
        ConfigSelectors selectors = scope.selectors();

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
        materials = materials.withVerifiedSources(new com.otectus.immersivesmithing.compat.epicknights.ComponentGraph(all, access, materials).integralSources());
        report.decisions().sources(all);
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
                String reason = Integrations.compatRecipeDisabled(r.getId());
                report.invalidRecipe(r.getId(), reason);
                ItemStack result = resultOf(r, access);
                if (!result.isEmpty()) report.decision(result.getItem(), r.getId(), RecipeDecisions.Origin.EXPLICIT, RecipeDecisions.Outcome.INACTIVE,
                        null, reason, Integrations.configKey(Integrations.compatModOf(r.getId())));
            }
        }
        for (ISmithingRecipeProvider provider : ImmersiveSmithingAPI.recipeProviders()) {
            try {
                for (SmithingRecipe recipe : provider.recipes(materials, access)) {
                    if (loadedIds.contains(recipe.getId())) {
                        report.note("Provider recipe " + recipe.getId() + " ignored: loaded datapack recipe has priority");
                    } else explicit.add(recipe);
                }
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Smithing recipe provider failed", e);
            }
        }
        explicit.sort(Comparator.comparing(r -> r.getId().toString()));
        explicit = new com.otectus.immersivesmithing.compat.epicknights.EpicRecipeResolver(all, access, materials, report).resolve(explicit, all);
        explicit.sort(Comparator.comparing(r -> r.getId().toString()));
        Set<ResourceLocation> seenIds = new HashSet<>();
        Set<String> familyBlacklist = Set.copyOf(ServerConfig.list(ServerConfig.MATERIAL_FAMILY_BLACKLIST));
        // Explicit recipes switched off by id: melting still follows them, and detection does not stand in for them.
        List<SmithingRecipe> switchedOff = new ArrayList<>();
        for (SmithingRecipe r : explicit) {
            String reason = null;
            String selector = null;
            if (!seenIds.add(r.getId())) {
                report.note("Duplicate provider recipe " + r.getId() + " ignored; the first valid ID is retained");
                continue;
            }
            if (com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.owns(r.result())) {
                reason = com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.reason();
                selector = "external workflow owner";
            } else if (selectors.smithingBlacklisted(r.getId()) != null
                    || (r.policyTemplate() != null && selectors.smithingBlacklisted(r.policyTemplate()) != null)) {
                reason = "switched off by config";
                selector = selectors.smithingBlacklisted(r.getId()) != null ? selectors.smithingBlacklisted(r.getId())
                        : selectors.smithingBlacklisted(r.policyTemplate());
                selectors.withhold(r.resultItem(), selector);
                if (materials.family(r.family()).isPresent()) switchedOff.add(r);
            }
            else if (Integrations.compatRecipeDisabled(r.getId()) != null) {
                reason = Integrations.compatRecipeDisabled(r.getId());
                selector = Integrations.configKey(Integrations.compatModOf(r.getId()));
            } else if (materials.family(r.family()).isEmpty()) {
                reason = "unknown material family " + r.family();
                if (familyBlacklist.contains(r.family().toString())) selector = "recipes.materialFamilyBlacklist = " + r.family();
            } else if (r.result().is(ModTags.NON_SMITHABLE_EQUIPMENT)) {
                reason = "result is excluded from smithing";
                selector = "#" + ModTags.NON_SMITHABLE_EQUIPMENT.location();
            } else if (ImmersiveSmithingAPI.isExcluded(r.result())) {
                reason = "result is excluded from smithing";
                selector = "API exclusion";
            } else if (!r.follows().isEmpty() && r.follows().stream().noneMatch(loadedIds::contains)) {
                // The pack removed the native recipe (or this version of the mod has none): the forge alternative goes too.
                reason = "none of the native recipes it follows is loaded " + r.follows();
            } else if (r.follows().stream().anyMatch(SourceRecipePolicies::preserves)) {
                reason = "source recipe kept native by a pack recipe policy";
            } else if (r.metalUnits() <= 0 || r.result().isEmpty() || r.result().getCount() < 1
                    || r.result().getCount() > r.result().getMaxStackSize()) {
                reason = "invalid metal charge or result count";
            } else if (r.auxiliary().stream().anyMatch(a -> a.count() <= 0 || a.ingredient().getItems().length == 0)) {
                reason = "empty or invalid auxiliary ingredient";
            }
            if (reason != null) {
                removed.add(r.getId());
                report.invalidRecipe(r, reason, selector);
                continue;
            }
            active.add(r);
            explicitResults.add(r.resultItem());
            report.explicitRecipe(r);
        }

        // 3. Crafting analysis, used both for automatic recipes and for recycling values.
        Map<ResourceLocation, AutoRecipeDetector.Analysis> analyses = AutoRecipeDetector.analyzeCrafting(all, access, materials, explicitResults, report);
        List<SmithingRecipe> auto = new ArrayList<>();
        AutoRecipeDetector.UpgradeContext upgradeContext = null;
        if (ServerConfig.get(ServerConfig.AUTO_DETECT_EQUIPMENT)) {
            auto.addAll(AutoRecipeDetector.generate(analyses.values(), explicitResults, scope, report));
            // Smithing-table upgrades (modded netherite and similar) are redirected into the forge as well.
            Map<Item, SmithingRecipe> smithable = new HashMap<>();
            active.forEach(r -> smithable.putIfAbsent(r.resultItem(), r));
            auto.forEach(r -> smithable.putIfAbsent(r.resultItem(), r));
            AutoRecipeDetector.UpgradeContext upgrades = new AutoRecipeDetector.UpgradeContext(all, access, materials, smithable);
            upgradeContext = upgrades;
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
            if (r.sourceRecipe() != null) declared.computeIfAbsent(r.resultItem(), k -> new HashSet<>()).add(r.sourceRecipe());
        });
        Map<ResourceLocation, Item> suppressed = new HashMap<>();
        boolean replace = ServerConfig.get(ServerConfig.REPLACE_METAL_EQUIPMENT_RECIPES)
                && !ServerConfig.get(ServerConfig.ALLOW_VANILLA_CRAFTING_ALONGSIDE_SMITHING);
        if (replace) {
            boolean smithingTable = ServerConfig.get(ServerConfig.SUPPRESS_SMITHING_TABLE_UPGRADES);
            for (Recipe<?> r : all) {
                if (r instanceof SmithingRecipe) continue;
                if (scope.skipsRecipe(r.getId())) {
                    keptBySelector(report, r, access, "recipe blacklisted by config: never disabled", scope.recipeSelector(r.getId()));
                    continue;
                }
                ItemStack result;
                try {
                    result = r.getResultItem(access);
                } catch (RuntimeException e) {
                    continue;
                }
                if (result.isEmpty()) continue;
                List<SmithingRecipe> forge = forgeByResult.get(result.getItem());
                if (forge == null) {
                    // Diamond, wooden, leather and other work no forge recipe makes: left exactly as it is.
                    if (isNativeEquipmentRecipe(r, result) && !report.isRetained(r.getId())) report.untouched(r.getId(), result.getItem());
                    continue;
                }
                if (scope.skipsItem(result.getItem())) {
                    keptBySelector(report, r, access, scope.skipReason(result.getItem()) + ": never disabled", scope.itemSelector(result.getItem()));
                    continue;
                }
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
                        report.retained(r.getId(), result.getItem(), kept,
                                kept.startsWith("smithing-table upgrades") ? "recipes.suppressSmithingTableUpgrades = false" : null);
                    } else {
                        suppressed.put(r.getId(), result.getItem());
                        report.suppressed(r.getId(), result.getItem(), forge.stream().filter(f -> f.replaces().contains(r.getId()) || r.getId().equals(f.sourceRecipe()))
                                .map(SmithingRecipe::getId).findFirst().orElse(null));
                    }
                    continue;
                }
                // An unlinked explicit recipe is an additional route, never permission to remove another one.
                if (isNativeEquipmentRecipe(r, result)) {
                    report.retained(r.getId(), result.getItem(), "no validated forge recipe names this exact source ID");
                }
            }
        } else {
            // Native recipes stay by the config: say so for every one the forge would otherwise have looked at.
            String selector = !ServerConfig.get(ServerConfig.REPLACE_METAL_EQUIPMENT_RECIPES) ? "recipes.replaceMetalEquipmentRecipes = false"
                    : "recipes.allowVanillaCraftingAlongsideSmithing = true";
            for (Recipe<?> r : all) {
                if (r instanceof SmithingRecipe) continue;
                ItemStack result = resultOf(r, access);
                if (result.isEmpty() || !forgeByResult.containsKey(result.getItem()) || !isNativeEquipmentRecipe(r, result)) continue;
                report.decision(result.getItem(), r.getId(), RecipeDecisions.Origin.NATIVE, RecipeDecisions.Outcome.KEPT, null,
                        "native recipes are kept by the config", selector);
            }
        }

        // An automatic-only exclusion of an item an explicit recipe makes changes nothing: say so, with its native recipes' fate.
        for (Map.Entry<Item, String> exclusion : selectors.autoExclusions().entrySet()) {
            List<SmithingRecipe> forge = forgeByResult.get(exclusion.getKey());
            if (forge == null) continue;
            List<String> disabled = suppressed.entrySet().stream().filter(e -> e.getValue() == exclusion.getKey())
                    .map(e -> e.getKey().toString()).sorted().toList();
            String fate = disabled.isEmpty() ? "its native recipes stay" : "its native recipes " + disabled + " are disabled by it";
            for (SmithingRecipe r : forge) {
                if (r.isAuto()) continue;
                String reason = "excluded from automatic detection, but this explicit recipe makes it: the exclusion has no effect; " + fate;
                report.decision(exclusion.getKey(), r.getId(), RecipeDecisions.Origin.EXPLICIT, RecipeDecisions.Outcome.ACTIVE, r.getId(),
                        reason, exclusion.getValue());
                report.note(exclusion.getValue() + " matches " + BuiltInRegistries.ITEM.getKey(exclusion.getKey()) + ", which "
                        + r.getId() + " makes: the exclusion has no effect; " + fate);
            }
        }

        // Stage the authoritative manager contents, including providers. Publish only after every index is built.
        Map<ResourceLocation, Recipe<?>> next = new LinkedHashMap<>();
        for (Recipe<?> r : all) {
            if (!(r instanceof SmithingRecipe) && !removed.contains(r.getId()) && !suppressed.containsKey(r.getId())) next.put(r.getId(), r);
        }
        active.forEach(r -> next.put(r.getId(), r));

        // Recycling exclusions and pack overrides take priority over every inferred source.
        Set<Item> excluded = new HashSet<>();
        Map<Item, RecyclingValue> recycling = new HashMap<>();
        List<RecyclingOverride> overrides = RecyclingOverrideLoader.overrides();
        for (RecyclingOverride o : overrides) {
            if (!o.recyclable()) excluded.addAll(itemsOf(o));
        }
        for (RecyclingOverride o : overrides) {
            if (!o.recyclable() || o.family() == null) continue;
            if (materials.family(o.family()).isEmpty()) {
                report.note("Recycling override for " + (o.item() != null ? o.item() : "#" + o.tag().location()) + " names unknown family " + o.family());
                continue;
            }
            for (Item item : itemsOf(o)) recycling.putIfAbsent(item, new RecyclingValue(o.family(), o.units(), "recycling override"));
        }
        Set<Item> overridden = Set.copyOf(recycling.keySet());
        for (SmithingRecipe r : active) {
            if (r.isAuto() || overridden.contains(r.resultItem())) continue;
            RecyclingValue value = new RecyclingValue(r.family(), r.recoverableUnits() / r.result().getCount(), "smithing content " + r.getId());
            recycling.merge(r.resultItem(), value, (a, b) -> a.family().equals(b.family())
                    ? (a.units() <= b.units() ? a : b) : new RecyclingValue(a.family(), 0, "conflicting primary metals"));
        }
        for (AutoRecipeDetector.Analysis a : analyses.values()) {
            if (a.status() != AutoRecipeDetector.Status.OK || a.result().getCount() != 1) continue;
            if (!EquipmentClassifier.isCandidate(a.result())) continue;
            recycling.putIfAbsent(a.result().getItem(), new RecyclingValue(a.family(), a.units(), "crafting recipe " + a.recipeId()));
        }
        for (SmithingRecipe r : auto) {
            recycling.putIfAbsent(r.resultItem(), new RecyclingValue(r.family(), r.recoverableUnits() / r.result().getCount(), "automatic recipe " + r.getId()));
        }
        // Excluding an item from forging never stops it melting: it melts as its forge recipe would have made it.
        for (SmithingRecipe r : switchedOff) {
            if (!r.resolvesEpicSource()) recycling.putIfAbsent(r.resultItem(), new RecyclingValue(r.family(), r.recoverableUnits() / r.result().getCount(), "smithing recipe " + r.getId() + " (switched off for forging)"));
        }
        if (upgradeContext != null) {
            for (SmithingRecipe r : upgradeContext.recyclingOnly().values()) {
                recycling.putIfAbsent(r.resultItem(), new RecyclingValue(r.family(), r.metalUnits(),
                        "excluded from forging; melts as " + r.getId() + " would make it"));
            }
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
        manager.replaceRecipes(next.values());
        Set<Recipe<?>> written = Collections.newSetFromMap(new IdentityHashMap<>());
        written.addAll(manager.getRecipes());
        lastWritten = written;
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
                if (!(r instanceof SmithingRecipe s && (s.isAuto() || s.policyTemplate() != null))) fresh.add(r);
            }
            sourceSnapshot = List.copyOf(fresh);
        }
        return sourceSnapshot;
    }

    /** A native crafting recipe or smithing-table upgrade of candidate equipment: what the forge could stand in for. */
    private static boolean isNativeEquipmentRecipe(Recipe<?> r, ItemStack result) {
        boolean type = r.getType() == RecipeType.CRAFTING || (r.getType() == RecipeType.SMITHING && r instanceof SmithingTransformRecipe);
        return type && result.getCount() == 1 && EquipmentClassifier.isCandidate(result);
    }

    /** A native recipe a selector protects from being disabled, recorded as a decision of its result (if it makes equipment). */
    private static void keptBySelector(CompatibilityReport report, Recipe<?> r, RegistryAccess access, String reason, String selector) {
        ItemStack result = resultOf(r, access);
        if (result.isEmpty() || !isNativeEquipmentRecipe(r, result)) return;
        report.decision(result.getItem(), r.getId(), RecipeDecisions.Origin.NATIVE, RecipeDecisions.Outcome.KEPT, null, reason, selector);
    }

    private static ItemStack resultOf(Recipe<?> r, RegistryAccess access) {
        try {
            return r.getResultItem(access);
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
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
        for (int pass = 0; pass <= sources.size(); pass++) {
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
