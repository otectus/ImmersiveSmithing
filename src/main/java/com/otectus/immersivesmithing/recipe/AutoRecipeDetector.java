package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.material.UpgradePolicy;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.minigame.ForgePattern;
import com.otectus.immersivesmithing.registry.ModTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Conservative recipe analysis. A crafting recipe becomes a smithing recipe only when every ingredient is
 * either entirely one metal family or entirely non-metal, exactly one family is involved, and the result is
 * recognisable equipment. Anything ambiguous is skipped and reported, never guessed.
 *
 * <p>The result's repair material decides its primary material: a diamond weapon with iron-nugget fittings is not iron
 * equipment, and a gold weapon with iron-nugget fittings is gold equipment whose nuggets are components. Only recipe
 * classes whose behaviour the forge reproduces are analysed at all ({@link SourceRecipes}).</p>
 */
public final class AutoRecipeDetector {

    public enum Status { OK, UPGRADE_CHAIN, NOT_METAL, NON_METAL_PRIMARY, MULTI_METAL, MIXED_INGREDIENT, EQUIPMENT_INGREDIENT, EMPTY_INGREDIENT, ERROR }

    /**
     * @param base for {@link Status#UPGRADE_CHAIN}: the one ingredient that is a finished piece of the same class as
     *             the result (a manasteel helmet reworked into a terrasteel helmet); null otherwise.
     * @param kind the source recipe's class, which decides whether the result keeps a reworked piece's data.
     */
    public record Analysis(ResourceLocation recipeId, ItemStack result, Status status, @Nullable ResourceLocation family,
                           int units, List<AuxiliaryIngredient> auxiliary, int metalSlots, int auxSlots, String detail,
                           @Nullable Ingredient base, SourceRecipes.Kind kind) {
        public Analysis(ResourceLocation recipeId, ItemStack result, Status status, @Nullable ResourceLocation family,
                        int units, List<AuxiliaryIngredient> auxiliary, int metalSlots, int auxSlots, String detail) {
            this(recipeId, result, status, family, units, auxiliary, metalSlots, auxSlots, detail, null, SourceRecipes.Kind.PLAIN_CRAFTING);
        }

        /** Whether any ingredient was recognised as metal, which makes the recipe eligible for suppression. */
        public boolean hasMetal() {
            return metalSlots > 0;
        }

        Analysis withKind(SourceRecipes.Kind kind) {
            return new Analysis(recipeId, result, status, family, units, auxiliary, metalSlots, auxSlots, detail, base, kind);
        }
    }

    /**
     * What detection must leave alone in one rebuild: blacklisted recipe ids, and the item namespaces of blacklisted mods
     * and of integrations that are switched off. Curated namespaces (Epic Fight, Epic Knights) are forged only from their
     * bundled recipes: detection never generates anything for them, but their native recipes can still be replaced. The
     * {@link ConfigSelectors} of the rebuild add excluded items and blacklisted smithing recipe ids; every pass asks them
     * before it creates a recipe, and once more about the id it would create.
     */
    public record Scope(Set<String> recipeBlacklist, Set<String> skippedNamespaces, Set<String> curatedNamespaces, ConfigSelectors selectors) {
        public Scope(Set<String> recipeBlacklist, Set<String> skippedNamespaces) {
            this(recipeBlacklist, skippedNamespaces, Set.of());
        }

        public Scope(Set<String> recipeBlacklist, Set<String> skippedNamespaces, Set<String> curatedNamespaces) {
            this(recipeBlacklist, skippedNamespaces, curatedNamespaces, ConfigSelectors.NONE);
        }

        /** The scope of one rebuild, from the server config; its selectors are registered with the report. */
        public static Scope fromConfig(CompatibilityReport report) {
            ConfigSelectors selectors = ConfigSelectors.fromConfig(report);
            Set<String> namespaces = new HashSet<>(selectors.modBlacklist());
            namespaces.addAll(Integrations.disabledNamespaces());
            return new Scope(selectors.recipeBlacklist(), Set.copyOf(namespaces), Set.copyOf(Integrations.curatedNamespaces()), selectors);
        }

        /**
         * Why detection makes nothing for this candidate item although it could: its explicit recipe is blacklisted (detection
         * does not stand in for it), or an {@code autoDetectExclusions} entry names it. {reason, selector}, or null.
         */
        @Nullable
        String[] withholds(Item item) {
            String withheld = selectors.withheld(item);
            if (withheld != null) return new String[]{"its explicit smithing recipe is switched off by config; automatic detection does not stand in for it", withheld};
            String excluded = selectors.autoExclusion(item);
            if (excluded != null) return new String[]{"excluded from automatic detection by config", excluded};
            return null;
        }

        /** The {@code smithingRecipeBlacklist} entry naming a generated id, or null. */
        @Nullable
        String blacklisted(ResourceLocation generatedId) {
            return selectors.smithingBlacklisted(generatedId);
        }

        public boolean skipsRecipe(ResourceLocation recipeId) {
            return recipeBlacklist.contains(recipeId.toString());
        }

        public boolean skipsItem(Item item) {
            return skippedNamespaces.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace());
        }

        /** Forged only from bundled recipes: automatic detection stays silent about it. */
        public boolean isCurated(Item item) {
            return curatedNamespaces.contains(BuiltInRegistries.ITEM.getKey(item).getNamespace());
        }

        /** Why an item's namespace is skipped, for the report. */
        public String skipReason(Item item) {
            String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            return Integrations.disabledNamespaces().contains(namespace) ? "integration " + namespace + " is disabled in the config"
                    : "mod blacklisted by config";
        }

        /** The configuration selector behind {@link #skipsRecipe}. */
        public String recipeSelector(ResourceLocation recipeId) {
            return "recipes.recipeBlacklist = " + recipeId;
        }

        /** The configuration selector behind {@link #skipsItem}: the integration switch, or the mod blacklist entry. */
        public String itemSelector(Item item) {
            String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            return Integrations.disabledNamespaces().contains(namespace) ? Integrations.configKey(namespace) : "recipes.modBlacklist = " + namespace;
        }
    }

    /**
     * Analyses every crafting recipe whose result is a single unstackable item, plus every recipe of an item that
     * already has an explicit smithing recipe (so its conventional recipe can be disabled even if it stacks). Recipes
     * of a custom class are not analysed; when they make equipment they are reported as kept.
     */
    public static Map<ResourceLocation, Analysis> analyzeCrafting(Collection<Recipe<?>> source, RegistryAccess access, MaterialRegistry materials,
                                                                  Set<Item> alwaysAnalyse, CompatibilityReport report) {
        Map<ResourceLocation, Analysis> out = new LinkedHashMap<>();
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (Recipe<?> r : source) {
            if (r instanceof CraftingRecipe crafting && r.getType() == RecipeType.CRAFTING) recipes.add(crafting);
        }
        recipes.sort(Comparator.comparing(r -> r.getId().toString()));
        for (CraftingRecipe recipe : recipes) {
            try {
                if (recipe.isSpecial()) continue;
                ItemStack result = recipe.getResultItem(access);
                if (result.isEmpty() || (result.getMaxStackSize() != 1 && !alwaysAnalyse.contains(result.getItem()))) continue;
                SourceRecipes.Kind kind = SourceRecipes.kind(recipe);
                if (!kind.isAdaptable()) {
                    if (EquipmentClassifier.isCandidate(result)) {
                        report.retained(recipe.getId(), result.getItem(), "custom recipe type " + SourceRecipes.describe(recipe) + " is never converted");
                    }
                    continue;
                }
                out.put(recipe.getId(), analyze(recipe.getId(), recipe.getIngredients(), result, materials).withKind(kind));
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.debug("Could not analyse recipe {}: {}", recipe.getId(), e.toString());
            }
        }
        return out;
    }

    public static Analysis analyze(ResourceLocation id, List<Ingredient> ingredients, ItemStack result, MaterialRegistry materials) {
        boolean shieldResult = EquipmentClassifier.isShield(result);
        Primary primary = primaryMaterial(result, ingredients, materials);
        Map<ResourceLocation, Integer> families = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> fittings = new LinkedHashMap<>();
        Map<String, AuxBucket> aux = new LinkedHashMap<>();
        List<Ingredient> equipmentIngredients = new ArrayList<>();
        int metalSlots = 0;
        int auxSlots = 0;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) {
                return new Analysis(id, result, Status.EMPTY_INGREDIENT, null, 0, List.of(), metalSlots, auxSlots, "an ingredient matches no items");
            }
            Set<ResourceLocation> ingFamilies = new HashSet<>();
            Set<Integer> ingUnits = new HashSet<>();
            boolean nonMetal = false;
            boolean equipment = false;
            for (ItemStack s : items) {
                MaterialRegistry.Entry e = materials.lookup(s.getItem());
                if (e == null) {
                    nonMetal = true;
                    if (s.isDamageableItem()) equipment = true;
                } else {
                    ingFamilies.add(e.family());
                    ingUnits.add(e.units());
                }
            }
            if (ingFamilies.isEmpty()) {
                if (equipment) {
                    // A metal shield built on a base shield: the base is dismissed, not required at all.
                    if (shieldResult && isBaseShield(items)) continue;
                    equipmentIngredients.add(ingredient);
                    continue;
                }
                auxSlots++;
                String key = key(items);
                aux.computeIfAbsent(key, k -> new AuxBucket(ingredient)).count++;
            } else if (nonMetal || ingFamilies.size() > 1 || ingUnits.size() > 1) {
                metalSlots++;
                return new Analysis(id, result, Status.MIXED_INGREDIENT, null, 0, List.of(), metalSlots, auxSlots,
                        "an ingredient mixes metals, unit values or non-metal items");
            } else if (primary.family() != null && !primary.family().equals(ingFamilies.iterator().next())) {
                // Fittings of another metal on a piece whose repair material is this metal: components, not stock.
                auxSlots++;
                aux.computeIfAbsent(key(items), k -> new AuxBucket(ingredient)).count++;
                fittings.merge(ingFamilies.iterator().next(), ingUnits.iterator().next(), Integer::sum);
            } else {
                metalSlots++;
                families.merge(ingFamilies.iterator().next(), ingUnits.iterator().next(), Integer::sum);
            }
        }
        if (primary.nonMetal() && !families.isEmpty()) {
            // Diamond, leather or bone equipment with metal fittings (a nugget on a leather helmet): not metal equipment.
            return new Analysis(id, result, Status.NON_METAL_PRIMARY, null, 0, List.of(), metalSlots, auxSlots,
                    "not predominantly metal: its repair material is not metal");
        }
        if (families.isEmpty()) {
            // Bone, chitin or cloth work, whatever it is built on: not the forge's business, and not worth reporting.
            // A single finished piece among the ingredients is remembered: the result keeps that piece's metal for recycling.
            Ingredient piece = equipmentIngredients.size() == 1 ? equipmentIngredients.get(0) : null;
            List<AuxiliaryIngredient> components = aux.values().stream().map(b -> new AuxiliaryIngredient(b.ingredient, b.count)).toList();
            return new Analysis(id, result, Status.NOT_METAL, null, 0, components, 0, auxSlots, "no metal ingredient", piece,
                    SourceRecipes.Kind.PLAIN_CRAFTING);
        }
        int primaryUnits = families.values().stream().mapToInt(Integer::intValue).sum();
        int fittingUnits = fittings.values().stream().mapToInt(Integer::intValue).sum();
        if (families.size() > 1 || (!fittings.isEmpty() && fittingUnits >= primaryUnits)) {
            // Fittings must be the smaller part; as much of another metal as of the primary one is a true alloy recipe.
            Set<ResourceLocation> all = new java.util.LinkedHashSet<>(families.keySet());
            all.addAll(fittings.keySet());
            return new Analysis(id, result, Status.MULTI_METAL, null, 0, List.of(), metalSlots, auxSlots,
                    "uses several metal families " + all);
        }
        Map.Entry<ResourceLocation, Integer> only = families.entrySet().iterator().next();
        List<AuxiliaryIngredient> auxiliary = aux.values().stream().map(b -> new AuxiliaryIngredient(b.ingredient, b.count)).toList();
        if (!equipmentIngredients.isEmpty()) {
            if (equipmentIngredients.size() == 1 && isUpgradeBase(equipmentIngredients.get(0), result)) {
                return new Analysis(id, result, Status.UPGRADE_CHAIN, only.getKey(), only.getValue(), auxiliary, metalSlots, auxSlots,
                        "reworks a finished piece", equipmentIngredients.get(0), SourceRecipes.Kind.PLAIN_CRAFTING);
            }
            Ingredient piece = equipmentIngredients.size() == 1 ? equipmentIngredients.get(0) : null;
            return new Analysis(id, result, Status.EQUIPMENT_INGREDIENT, only.getKey(), only.getValue(), auxiliary, metalSlots, auxSlots,
                    piece != null ? "uses equipment as an ingredient" : "uses equipment as an ingredient (several pieces)",
                    piece, SourceRecipes.Kind.PLAIN_CRAFTING);
        }
        return new Analysis(id, result, Status.OK, only.getKey(), only.getValue(), auxiliary, metalSlots, auxSlots, "");
    }

    /**
     * The result's primary material: the metal family of the ingredients it accepts as repair material, or non-metal
     * when its repair material is not metal. {@link Primary#NONE} when no ingredient is a repair material or the answer
     * is ambiguous; the general rules then apply unchanged.
     */
    record Primary(@Nullable ResourceLocation family, boolean nonMetal) {
        static final Primary NONE = new Primary(null, false);
    }

    static Primary primaryMaterial(ItemStack result, List<Ingredient> ingredients, MaterialRegistry materials) {
        Set<ResourceLocation> families = new HashSet<>();
        boolean nonMetal = false;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            for (ItemStack candidate : ingredient.getItems()) {
                if (!isRepairMaterial(result, candidate)) continue;
                MaterialRegistry.Entry entry = materials.lookup(candidate.getItem());
                if (entry == null) nonMetal = true;
                else families.add(entry.family());
            }
        }
        if (nonMetal && families.isEmpty()) return new Primary(null, true);
        if (!nonMetal && families.size() == 1) return new Primary(families.iterator().next(), false);
        return Primary.NONE;
    }

    /** Whether {@code candidate} repairs {@code result} at an anvil. Equipment never counts (self-repairing items). */
    static boolean isRepairMaterial(ItemStack result, ItemStack candidate) {
        if (candidate.isEmpty() || candidate.isDamageableItem()) return false;
        try {
            return result.getItem().isValidRepairItem(result, candidate);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether every item an ingredient accepts is candidate equipment of the same class as the result. */
    static boolean isUpgradeBase(Ingredient ingredient, ItemStack result) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0 || !EquipmentClassifier.isCandidate(result)) return false;
        ResourceLocation pattern = EquipmentClassifier.classify(result);
        for (ItemStack s : items) {
            if (!EquipmentClassifier.isCandidate(s)) return false;
            if (EquipmentClassifier.armorSlot(s) != EquipmentClassifier.armorSlot(result)) return false;
            if (EquipmentClassifier.isShield(s) != EquipmentClassifier.isShield(result)) return false;
            if (!EquipmentClassifier.classify(s).equals(pattern)) return false;
        }
        return true;
    }

    /** Turns successful analyses of candidate equipment into automatic smithing recipes. */
    public static List<SmithingRecipe> generate(Collection<Analysis> analyses, Set<Item> explicitResults, Scope scope, CompatibilityReport report) {
        Map<Item, SmithingRecipe> generated = new LinkedHashMap<>();
        Set<Item> reported = new TreeSet<>(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        Set<Item> notConsidered = new HashSet<>();

        for (Analysis a : analyses) {
            Item item = a.result().getItem();
            String notCandidate = EquipmentClassifier.whyNotCandidate(a.result());
            if (notCandidate != null) {
                // A metal recipe of something the forge never makes (a crossbow, an item tagged non-smithable): said once.
                if (a.hasMetal() && !notCandidate.equals(EquipmentClassifier.NOT_EQUIPMENT) && notConsidered.add(item)) {
                    report.notConsidered(item, a.recipeId(), notCandidate, EquipmentClassifier.candidacySelector(a.result()));
                }
                continue;
            }
            if (explicitResults.contains(item)) {
                skippedSilently(report, item, a.recipeId(), null, "an explicit smithing recipe makes this item");
                continue;
            }
            if (generated.containsKey(item)) {
                skippedSilently(report, item, a.recipeId(), generated.get(item).getId(), "another source recipe already makes it");
                continue;
            }
            if (scope.isCurated(item)) {
                skippedSilently(report, item, a.recipeId(), null, "curated integration: forged only from its bundled recipes");
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            String reason = null;
            String selector = null;
            if (scope.skipsRecipe(a.recipeId())) {
                reason = "recipe blacklisted by config";
                selector = scope.recipeSelector(a.recipeId());
            } else if (scope.skipsItem(item)) {
                reason = scope.skipReason(item);
                selector = scope.itemSelector(item);
            }
            else if (a.status() == Status.NOT_METAL) continue; // not metal equipment: nothing to report
            else if ((a.status() == Status.UPGRADE_CHAIN || a.status() == Status.EQUIPMENT_INGREDIENT) && a.base() != null) continue; // priced later from the piece's own recipe
            else if (scope.withholds(item) != null) {
                reason = scope.withholds(item)[0];
                selector = scope.withholds(item)[1];
            }
            else if (a.status() != Status.OK) reason = a.detail();
            else if (a.result().getCount() != 1) reason = "recipe makes more than one item";
            else if (EquipmentClassifier.isShield(a.result()) && !a.result().is(ModTags.SMITHABLE_SHIELDS)
                    && a.metalSlots() < a.auxSlots()) reason = "shield is not predominantly metal";
            else if (!a.result().is(ModTags.SMITHABLE_EQUIPMENT) && !a.result().is(ModTags.SMITHABLE_SHIELDS)
                    && a.metalSlots() < a.auxSlots() && a.units() < MaterialUnits.INGOT) reason = "not predominantly metal";

            if (reason != null) {
                if (reported.add(item)) report.skippedEquipment(item, a.recipeId(), reason, selector);
                else report.decision(item, a.recipeId(), RecipeDecisions.Origin.AUTOMATIC, RecipeDecisions.Outcome.SKIPPED, null, reason, selector);
                continue;
            }
            ResourceLocation id = ImmersiveSmithing.id("auto/" + itemId.getNamespace() + "/" + itemId.getPath());
            String blacklisted = scope.blacklisted(id);
            if (blacklisted != null) {
                // Melting still uses the crafting analysis; only the forge recipe is switched off.
                if (reported.add(item)) report.skippedEquipment(item, a.recipeId(), "smithing recipe " + id + " is switched off by config", blacklisted);
                continue;
            }
            SmithingRecipe recipe = new SmithingRecipe(id, a.family(), a.units(), a.auxiliary(), a.result().copy(),
                    ForgePattern.STANDARD, null, true, a.recipeId());
            generated.put(item, recipe);
            report.autoRecipe(recipe);
        }
        return new ArrayList<>(generated.values());
    }

    /** A source automatic detection passes over without a report line of its own: only its decision is recorded. */
    private static void skippedSilently(CompatibilityReport report, Item item, ResourceLocation recipe, @Nullable ResourceLocation smithing, String reason) {
        report.decision(item, recipe, RecipeDecisions.Origin.AUTOMATIC, RecipeDecisions.Outcome.SKIPPED, smithing, reason, null);
    }

    /** Whether every item an ingredient accepts is a shield (the base of a metal shield recipe). */
    static boolean isBaseShield(ItemStack[] items) {
        for (ItemStack s : items) {
            if (!EquipmentClassifier.isShield(s)) return false;
        }
        return items.length > 0;
    }

    // ------------------------------------------------------------------ smithing-table upgrades

    /** Lookups shared by every upgrade analysed in one rebuild. */
    public static final class UpgradeContext {
        final RegistryAccess access;
        final MaterialRegistry materials;
        final Map<Item, SmithingRecipe> smithable;
        final Map<Item, List<CraftingRecipe>> craftingByResult = new HashMap<>();
        final List<ItemStack> unstackables = new ArrayList<>();
        /**
         * What the upgrade and chain passes would have generated for items the config excludes from forging: never offered,
         * never a base for another recipe, only the items' recycling value (excluding forging never stops melting).
         */
        final Map<Item, SmithingRecipe> recyclingOnly = new LinkedHashMap<>();

        public Map<Item, SmithingRecipe> recyclingOnly() {
            return recyclingOnly;
        }

        public UpgradeContext(net.minecraft.world.item.crafting.RecipeManager manager, RegistryAccess access, MaterialRegistry materials,
                              Map<Item, SmithingRecipe> smithable) {
            this(manager.getRecipes(), access, materials, smithable);
        }

        public UpgradeContext(Collection<Recipe<?>> source, RegistryAccess access, MaterialRegistry materials, Map<Item, SmithingRecipe> smithable) {
            this.access = access;
            this.materials = materials;
            this.smithable = smithable;
            List<CraftingRecipe> crafting = new ArrayList<>();
            for (Recipe<?> r : source) {
                if (r instanceof CraftingRecipe c && r.getType() == RecipeType.CRAFTING) crafting.add(c);
            }
            crafting.sort(Comparator.comparing(r -> r.getId().toString()));
            for (CraftingRecipe r : crafting) {
                try {
                    if (r.isSpecial() || !SourceRecipes.kind(r).isCrafting()) continue;
                    ItemStack result = r.getResultItem(access);
                    if (!result.isEmpty()) craftingByResult.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(r);
                } catch (RuntimeException ignored) {
                    // a broken third-party recipe cannot be a base
                }
            }
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (stack.getMaxStackSize() == 1) unstackables.add(stack);
            }
        }
    }

    /**
     * Redirects smithing-table upgrades (netherite and any other single-metal addition) into the forge. The
     * template is never required. The cost is the base item's shape in the addition's metal: the base's own
     * smithing recipe if it has one, otherwise its crafting recipe, where ingredients the base accepts as a repair
     * material (or gems/ingots) count as material slots.
     */
    public static List<SmithingRecipe> generateUpgrades(Collection<Recipe<?>> source, UpgradeContext context, Scope scope, CompatibilityReport report) {
        List<SmithingTransformRecipe> upgrades = new ArrayList<>();
        for (Recipe<?> r : source) {
            if (r.getType() != RecipeType.SMITHING || !(r instanceof SmithingTransformRecipe t)) continue;
            if (SourceRecipes.kind(r) == SourceRecipes.Kind.SMITHING_UPGRADE) {
                upgrades.add(t);
                continue;
            }
            // A subclass with its own assembly (a reroll that removes a rolled power) stays on the smithing table.
            try {
                ItemStack result = t.getResultItem(context.access);
                if (!result.isEmpty() && EquipmentClassifier.isCandidate(result)) {
                    report.retained(t.getId(), result.getItem(), "custom smithing recipe " + SourceRecipes.describe(t) + " is never converted");
                }
            } catch (RuntimeException ignored) {
                // a broken third-party recipe is not reported
            }
        }
        upgrades.sort(Comparator.comparing(r -> r.getId().toString()));
        Map<Item, SmithingRecipe> generated = new LinkedHashMap<>();
        Set<Item> notConsidered = new HashSet<>();
        for (SmithingTransformRecipe upgrade : upgrades) {
            ItemStack result;
            try {
                result = upgrade.getResultItem(context.access);
            } catch (RuntimeException e) {
                continue;
            }
            if (result.isEmpty() || result.getCount() != 1) continue;
            Item item = result.getItem();
            String notCandidate = EquipmentClassifier.whyNotCandidate(result);
            if (notCandidate != null) {
                if (!notCandidate.equals(EquipmentClassifier.NOT_EQUIPMENT) && !additionFamilies(upgrade, context.materials).isEmpty()
                        && notConsidered.add(item)) {
                    report.notConsidered(item, upgrade.getId(), notCandidate, EquipmentClassifier.candidacySelector(result));
                }
                continue;
            }
            if (context.smithable.containsKey(item)) {
                skippedSilently(report, item, upgrade.getId(), context.smithable.get(item).getId(), "a forge recipe already makes this item");
                continue;
            }
            if (generated.containsKey(item)) {
                skippedSilently(report, item, upgrade.getId(), generated.get(item).getId(), "another upgrade already makes it");
                continue;
            }
            if (scope.skipsRecipe(upgrade.getId())) {
                report.skippedEquipment(item, upgrade.getId(), "recipe blacklisted by config", scope.recipeSelector(upgrade.getId()));
                continue;
            }
            if (scope.skipsItem(item)) {
                report.skippedEquipment(item, upgrade.getId(), scope.skipReason(item), scope.itemSelector(item));
                continue;
            }
            if (scope.isCurated(item)) {
                skippedSilently(report, item, upgrade.getId(), null, "curated integration: forged only from its bundled recipes");
                continue;
            }
            String[] withheld = scope.withholds(item);
            try {
                if (withheld != null) {
                    report.skippedEquipment(item, upgrade.getId(), withheld[0], withheld[1]);
                    upgradeRecipe(upgrade, result, context, new CompatibilityReport()).ifPresent(r -> context.recyclingOnly.putIfAbsent(item, r));
                    continue;
                }
                upgradeRecipe(upgrade, result, context, report).ifPresent(r -> {
                    String blacklisted = scope.blacklisted(r.getId());
                    if (blacklisted != null) {
                        report.skippedEquipment(item, upgrade.getId(), "smithing recipe " + r.getId() + " is switched off by config", blacklisted);
                        context.recyclingOnly.putIfAbsent(item, r);
                        return;
                    }
                    generated.put(item, r);
                    report.autoRecipe(r);
                });
            } catch (RuntimeException e) {
                report.skippedEquipment(item, upgrade.getId(), "upgrade analysis failed: " + e);
            }
        }
        return new ArrayList<>(generated.values());
    }

    public static java.util.Optional<SmithingRecipe> upgradeRecipe(SmithingTransformRecipe upgrade, ItemStack result, UpgradeContext context,
                                                                   CompatibilityReport report) {
        Item item = result.getItem();
        // The addition must be exactly one metal family with one unit value (e.g. netherite ingot = 9).
        Set<ResourceLocation> families = new HashSet<>();
        Set<Integer> units = new HashSet<>();
        for (var family : context.materials.families()) {
            for (var source : family.sources()) {
                if (upgrade.isAdditionIngredient(new ItemStack(source.item()))) {
                    families.add(family.id());
                    units.add(source.units());
                }
            }
        }
        if (families.isEmpty()) {
            // Not a metal upgrade (a gem or a component as the addition): the smithing table keeps it.
            report.skippedEquipment(item, upgrade.getId(), "upgrade addition is no known metal family");
            return java.util.Optional.empty();
        }
        if (families.size() != 1 || units.size() != 1) {
            report.skippedEquipment(item, upgrade.getId(), "upgrade addition is not a single metal family");
            return java.util.Optional.empty();
        }
        ResourceLocation family = families.iterator().next();
        int perAddition = units.iterator().next();

        List<ItemStack> bases = new ArrayList<>();
        for (ItemStack candidate : context.unstackables) {
            if (upgrade.isBaseIngredient(candidate)) bases.add(candidate);
        }
        if (bases.isEmpty()) {
            report.skippedEquipment(item, upgrade.getId(), "upgrade has no base item");
            return java.util.Optional.empty();
        }
        ItemStack base = bases.get(0);

        int metalUnits = -1;
        List<AuxiliaryIngredient> auxiliary = List.of();
        if (policy(context.materials, family) == UpgradePolicy.SHAPE) {
            SmithingRecipe baseRecipe = context.smithable.get(base.getItem());
            if (baseRecipe != null) {
                metalUnits = Math.max(1, Math.round(baseRecipe.metalUnits() * perAddition / 9F));
                auxiliary = baseRecipe.auxiliary();
            } else {
                for (CraftingRecipe crafting : context.craftingByResult.getOrDefault(base.getItem(), List.of())) {
                    Shape shape = baseShape(base, crafting.getIngredients(), EquipmentClassifier.isShield(result), context, 0);
                    if (shape != null) {
                        metalUnits = shape.primarySlots * perAddition;
                        auxiliary = shape.auxiliary;
                        break;
                    }
                }
            }
        }
        if (metalUnits <= 0) {
            // Addition policy, or a base with no metal shape of its own (a loot weapon, a cloth robe): the smith
            // pays the addition and reworks the base piece itself.
            metalUnits = perAddition;
            auxiliary = List.of(new AuxiliaryIngredient(Ingredient.of(bases.toArray(ItemStack[]::new)), 1, true));
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ResourceLocation id = ImmersiveSmithing.id("auto/" + itemId.getNamespace() + "/" + itemId.getPath());
        // A smithing-table upgrade hands the base piece's data to the result; a reworked base does the same here.
        return java.util.Optional.of(new SmithingRecipe(id, family, metalUnits, auxiliary, result.copy(), ForgePattern.STANDARD, null, true,
                upgrade.getId()).keepBaseData(true));
    }

    public static UpgradePolicy policy(MaterialRegistry materials, ResourceLocation family) {
        return materials.family(family).map(MaterialFamily::upgradePolicy).orElse(UpgradePolicy.SHAPE);
    }

    /** The metal families a smithing-table upgrade's addition slot accepts; empty when the addition is not metal. */
    public static Set<ResourceLocation> additionFamilies(SmithingTransformRecipe upgrade, MaterialRegistry materials) {
        Set<ResourceLocation> families = new HashSet<>();
        for (var family : materials.families()) {
            for (var source : family.sources()) {
                if (upgrade.isAdditionIngredient(new ItemStack(source.item()))) families.add(family.id());
            }
        }
        return families;
    }

    // ------------------------------------------------------------------ recipes built on a finished piece

    /**
     * Crafting recipes that use exactly one finished piece. When the forge makes that piece from the same metal as the
     * rest of the recipe (or the recipe adds no metal at all), the piece grows: the forge makes the result from the
     * piece's own recipe plus the recipe's metal and components (Epic Fight's iron longsword is an iron sword and two
     * ingots: 36 units and a stick). A piece reworked with another metal (Botania's terrasteel armour from manasteel
     * armour) is priced like a smithing-table upgrade: the base's shape in the new metal under the {@code shape} policy,
     * or the metal the recipe asks for plus the base piece itself under {@code addition}.
     */
    public static List<SmithingRecipe> generateChains(Collection<Analysis> analyses, UpgradeContext context, Scope scope, CompatibilityReport report) {
        Map<Item, SmithingRecipe> generated = new LinkedHashMap<>();
        // A piece can itself be built on a piece: a few passes reach the end of such a chain.
        for (int pass = 0; pass < 4; pass++) {
            boolean added = false;
            for (Analysis a : analyses) {
                Piece decision = pieceRecipe(a, context, scope, generated);
                if (decision.recipe() == null) continue;
                generated.put(decision.recipe().resultItem(), decision.recipe());
                context.smithable.put(decision.recipe().resultItem(), decision.recipe());
                report.autoRecipe(decision.recipe());
                added = true;
            }
            if (!added) break;
        }
        // Then say why the remaining ones were not converted. One the config excludes still gets its recycling value.
        for (Analysis a : analyses) {
            Piece decision = pieceRecipe(a, context, scope, generated);
            if (decision.reason() != null) report.skippedEquipment(a.result().getItem(), a.recipeId(), decision.reason(), decision.selector());
            if (decision.excluded()) {
                Piece unrestricted = pieceRecipe(a, context, new Scope(scope.recipeBlacklist(), scope.skippedNamespaces(), scope.curatedNamespaces()), generated);
                if (unrestricted.recipe() != null) context.recyclingOnly.putIfAbsent(unrestricted.recipe().resultItem(), unrestricted.recipe());
            }
        }
        return new ArrayList<>(generated.values());
    }

    /**
     * A recipe built from a piece, or why there is none (null when the decision is silent), the selector that decided, and
     * whether the config excluded a recipe that would otherwise exist.
     */
    private record Piece(@Nullable SmithingRecipe recipe, @Nullable String reason, @Nullable String selector, boolean excluded) {
        static final Piece NONE = new Piece(null, null, null, false);
        static Piece skip(boolean speak, String reason) {
            return skip(speak, reason, null);
        }
        static Piece skip(boolean speak, String reason, @Nullable String selector) {
            return new Piece(null, speak ? reason : null, selector, false);
        }
        static Piece excluded(String reason, String selector) {
            return new Piece(null, reason, selector, true);
        }
    }

    private static Piece pieceRecipe(Analysis a, UpgradeContext context, Scope scope, Map<Item, SmithingRecipe> generated) {
        boolean chain = a.status() == Status.UPGRADE_CHAIN;
        boolean component = a.status() == Status.EQUIPMENT_INGREDIENT;
        boolean transform = a.status() == Status.NOT_METAL;
        if (!(chain || component || transform) || a.base() == null) return Piece.NONE;
        Item item = a.result().getItem();
        if (!EquipmentClassifier.isCandidate(a.result())) return Piece.NONE;
        if (context.smithable.containsKey(item) || generated.containsKey(item) || scope.isCurated(item)) return Piece.NONE;
        boolean speak = !transform; // reworking a piece without any metal is not the forge's business
        if (scope.skipsRecipe(a.recipeId())) return Piece.skip(speak, "recipe blacklisted by config", scope.recipeSelector(a.recipeId()));
        if (scope.skipsItem(item)) return Piece.skip(speak, scope.skipReason(item), scope.itemSelector(item));
        String[] withheld = scope.withholds(item);
        if (withheld != null) return Piece.excluded(withheld[0], withheld[1]);
        if (a.result().getCount() != 1) return Piece.skip(speak, "recipe makes more than one item");
        ItemStack[] bases = a.base().getItems();
        SmithingRecipe piece = null;
        ItemStack pieceStack = ItemStack.EMPTY;
        for (ItemStack base : bases) {
            piece = context.smithable.get(base.getItem());
            if (piece != null) {
                pieceStack = base;
                break;
            }
        }
        if (piece == null) {
            String baseId = String.valueOf(BuiltInRegistries.ITEM.getKey(bases[0].getItem()));
            return Piece.skip(speak, chain ? "reworks a piece the forge does not make: " + baseId
                    : "uses equipment as an ingredient that the forge does not make: " + baseId);
        }
        ResourceLocation family;
        int metalUnits;
        List<AuxiliaryIngredient> auxiliary;
        boolean keepBaseData;
        if (a.family() == null || a.family().equals(piece.family())) {
            // The piece grows (a sword lengthened into a longsword, set on a shaft as a spear): the forge makes the whole
            // result from the same metal. Only within one kind of equipment: a pickaxe never becomes boots.
            if (!equipmentKind(pieceStack).equals(equipmentKind(a.result()))) {
                return Piece.skip(speak, "uses equipment as an ingredient of another kind: " + BuiltInRegistries.ITEM.getKey(pieceStack.getItem()));
            }
            family = piece.family();
            metalUnits = piece.metalUnits() + a.units();
            auxiliary = merge(piece.auxiliary(), a.auxiliary());
            keepBaseData = false;
        } else if (chain) {
            family = a.family();
            metalUnits = -1;
            auxiliary = List.of();
            if (policy(context.materials, a.family()) == UpgradePolicy.SHAPE) {
                metalUnits = piece.metalUnits();
                auxiliary = merge(piece.auxiliary(), a.auxiliary());
            }
            if (metalUnits <= 0) {
                metalUnits = a.units();
                List<AuxiliaryIngredient> rework = new ArrayList<>();
                rework.add(new AuxiliaryIngredient(a.base(), 1, true));
                auxiliary = merge(rework, a.auxiliary());
            }
            // A plain crafting recipe makes a fresh piece; a data-carrying one (Botania's armor upgrade) keeps the base's.
            keepBaseData = a.kind().carriesBaseData();
        } else {
            return Piece.skip(speak, "uses equipment as an ingredient made of another metal (" + piece.family() + ")");
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ResourceLocation id = ImmersiveSmithing.id("auto/" + itemId.getNamespace() + "/" + itemId.getPath());
        String blacklisted = scope.blacklisted(id);
        if (blacklisted != null) return Piece.excluded("smithing recipe " + id + " is switched off by config", blacklisted);
        return new Piece(new SmithingRecipe(id, family, metalUnits, auxiliary, a.result().copy(), ForgePattern.STANDARD, null, true,
                a.recipeId()).keepBaseData(keepBaseData), null, null, false);
    }

    /** The broad kind a piece can grow within: one armour slot, shields, mining tools or weapons. */
    static String equipmentKind(ItemStack stack) {
        net.minecraft.world.entity.EquipmentSlot slot = EquipmentClassifier.armorSlot(stack);
        if (slot != null) return "armor:" + slot.getName();
        if (EquipmentClassifier.isShield(stack)) return "shield";
        if (com.otectus.immersivesmithing.quality.QualityHooks.isMiningTool(stack)) return "tool";
        return "weapon";
    }

    /** Joins two auxiliary lists, adding the counts of identical ingredients (a sword's stick and a spear's two sticks). */
    static List<AuxiliaryIngredient> merge(List<AuxiliaryIngredient> first, List<AuxiliaryIngredient> second) {
        Map<String, AuxiliaryIngredient> out = new LinkedHashMap<>();
        for (List<AuxiliaryIngredient> list : List.of(first, second)) {
            for (AuxiliaryIngredient aux : list) {
                String k = key(aux.ingredient().getItems()) + "|" + aux.consumesEquipment();
                AuxiliaryIngredient previous = out.get(k);
                out.put(k, previous == null ? aux : new AuxiliaryIngredient(previous.ingredient(), previous.count() + aux.count(), aux.consumesEquipment()));
            }
        }
        return List.copyOf(out.values());
    }

    private record Shape(int primarySlots, List<AuxiliaryIngredient> auxiliary) {}

    /**
     * Splits a base item's crafting recipe into its material slots and everything else, or null if unusable. A finished
     * piece among the ingredients counts with its own shape when that shape is gems or ingots (a diamond longsword is a
     * diamond sword and two diamonds). A piece of leather or cloth leaves the base without a metal shape (Iron's Spells'
     * wandering magician boots are leather boots and arcane essence), so its upgrade reworks it under {@code addition}.
     */
    private static Shape baseShape(ItemStack base, List<Ingredient> ingredients, boolean shieldResult, UpgradeContext context, int depth) {
        int primary = 0;
        List<AuxiliaryIngredient> nested = new ArrayList<>();
        Map<String, AuxBucket> aux = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] items = ingredient.getItems();
            if (items.length == 0) return null;
            boolean material = false;
            boolean equipment = false;
            for (ItemStack s : items) {
                // The base's repair material counts in its own recipe; inside a nested piece only gems and ingots do.
                boolean stock = s.is(net.minecraftforge.common.Tags.Items.GEMS) || s.is(net.minecraftforge.common.Tags.Items.INGOTS);
                if (stock || (depth == 0 && base.getItem().isValidRepairItem(base, s))) material = true;
                if (s.isDamageableItem()) equipment = true;
            }
            if (material) {
                primary++;
            } else if (equipment) {
                if (shieldResult && isBaseShield(items)) continue;
                Shape inner = depth < 2 ? pieceShape(base, items, shieldResult, context, depth + 1) : null;
                if (inner == null) return null;
                primary += inner.primarySlots;
                nested = merge(nested, inner.auxiliary);
            } else {
                aux.computeIfAbsent(key(items), k -> new AuxBucket(ingredient)).count++;
            }
        }
        if (primary == 0) return null;
        return new Shape(primary, merge(nested, aux.values().stream().map(b -> new AuxiliaryIngredient(b.ingredient, b.count)).toList()));
    }

    /** The shape of the first alternative of a finished-piece ingredient that has a usable crafting recipe. */
    @Nullable
    private static Shape pieceShape(ItemStack base, ItemStack[] items, boolean shieldResult, UpgradeContext context, int depth) {
        for (ItemStack piece : items) {
            for (CraftingRecipe crafting : context.craftingByResult.getOrDefault(piece.getItem(), List.of())) {
                Shape shape = baseShape(base, crafting.getIngredients(), shieldResult, context, depth);
                if (shape != null) return shape;
            }
        }
        return null;
    }

    private static String key(ItemStack[] items) {
        TreeSet<String> ids = new TreeSet<>();
        for (ItemStack s : items) {
            ids.add(BuiltInRegistries.ITEM.getKey(s.getItem()) + (s.hasTag() ? String.valueOf(s.getTag()) : ""));
        }
        return String.join(",", ids);
    }

    private static final class AuxBucket {
        final Ingredient ingredient;
        int count;

        AuxBucket(Ingredient ingredient) {
            this.ingredient = ingredient;
        }
    }

    private AutoRecipeDetector() {}
}
