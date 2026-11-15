package com.otectus.immersivesmithing.compat.epicknights;

import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.recipe.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

import java.util.*;

/** Converts reviewed equipment templates using active recipes, never the generator's historical numeric cost. */
public final class EpicRecipeResolver {
    private record Plan(SmithingRecipe recipe, ComponentGraph.Fraction added, Ingredient base) {}
    private final ComponentGraph components;
    private final Map<ResourceLocation, Plan> plans = new LinkedHashMap<>();
    private final RegistryAccess access;
    private final MaterialRegistry materials;
    private final CompatibilityReport report;

    public EpicRecipeResolver(List<Recipe<?>> sources, RegistryAccess access, MaterialRegistry materials, CompatibilityReport report) {
        this.components = new ComponentGraph(sources, access, materials);
        this.access = access;
        this.materials = materials;
        this.report = report;
    }

    public List<SmithingRecipe> resolve(List<SmithingRecipe> templates, List<Recipe<?>> sources) {
        List<SmithingRecipe> out = new ArrayList<>();
        Map<Item, List<Recipe<?>>> byResult = new HashMap<>();
        Set<ResourceLocation> claimed = new HashSet<>();
        Map<Item, SmithingRecipe> smithable = new HashMap<>();
        for (SmithingRecipe template : templates) {
            if (!template.resolvesEpicSource()) {
                claimed.addAll(template.replaces());
                out.add(template);
            }
            smithable.putIfAbsent(template.resultItem(), template);
        }
        for (Recipe<?> source : sources) {
            if (source instanceof SmithingRecipe) continue;
            try {
                ItemStack result = source.getResultItem(access);
                if (!result.isEmpty()) byResult.computeIfAbsent(result.getItem(), key -> new ArrayList<>()).add(source);
            } catch (RuntimeException ignored) { /* Preserve invalid upstream recipes; they establish no replacement. */ }
        }
        byResult.values().forEach(list -> list.sort(Comparator.comparing(r -> r.getId().toString())));
        AutoRecipeDetector.UpgradeContext upgrades = new AutoRecipeDetector.UpgradeContext(sources, access, materials, smithable);
        for (SmithingRecipe template : templates) {
            if (!template.resolvesEpicSource()) continue;
            if (materials.family(template.family()).isEmpty()) {
                report.invalidRecipe(template, "unknown material family " + template.family(), null);
                continue;
            }
            if (Integrations.compatRecipeDisabled(template.getId()) != null) {
                report.invalidRecipe(template, Integrations.compatRecipeDisabled(template.getId()), Integrations.configKey(Integrations.compatModOf(template.getId())));
                continue;
            }
            List<Recipe<?>> nativeRecipes = byResult.getOrDefault(template.resultItem(), List.of());
            if (nativeRecipes.isEmpty()) report.invalidRecipe(template, "none of the native recipes it follows is loaded", null);
            for (Recipe<?> source : nativeRecipes) {
                if (claimed.contains(source.getId())) continue;
                if (SourceRecipePolicies.preserves(source.getId())) {
                    keep(source, template, SourceRecipePolicies.reason(source.getId()));
                    continue;
                }
                try {
                    ItemStack result = source.getResultItem(access);
                    if (result.getCount() != 1 || result.getMaxStackSize() != 1) {
                        keep(source, template, "equipment source has unsupported output count");
                        continue;
                    }
                    SmithingRecipe resolved;
                    if (source instanceof SmithingTransformRecipe upgrade) {
                        // Only the established genuine netherite policy may replace a smithing template.
                        if (SourceRecipes.kind(source) != SourceRecipes.Kind.SMITHING_UPGRADE
                                || !template.family().equals(new ResourceLocation("minecraft", "netherite"))
                                || !upgrade.isAdditionIngredient(new ItemStack(Items.NETHERITE_INGOT))
                                || !upgrade.isTemplateIngredient(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE))) {
                            keep(source, template, "native template transformation; base, addition and template retained");
                            continue;
                        }
                        SmithingRecipe converted = AutoRecipeDetector.upgradeRecipe(upgrade, result, upgrades, report).orElse(null);
                        if (converted == null) continue;
                        resolved = recipe(template, source, converted.metalUnits(), converted.auxiliary(), result);
                        plans.put(resolved.getId(), new Plan(resolved, ComponentGraph.Fraction.of(converted.metalUnits()),
                                converted.auxiliary().stream().filter(AuxiliaryIngredient::consumesEquipment).map(AuxiliaryIngredient::ingredient).findFirst().orElse(null)));
                    } else if (SourceRecipes.kind(source) == SourceRecipes.Kind.PLAIN_CRAFTING) {
                        resolved = crafting(template, source, result);
                    } else {
                        keep(source, template, "native custom serializer: " + SourceRecipes.describe(source));
                        continue;
                    }
                    if (resolved != null) {
                        out.add(resolved);
                        claimed.add(source.getId());
                    }
                } catch (RuntimeException failure) {
                    keep(source, template, "source analysis refused: " + failure.getMessage());
                }
            }
        }
        priceRecovery(out);
        components.reasons().entrySet().stream().sorted(Comparator.comparing(e -> BuiltInRegistries.ITEM.getKey(e.getKey()).toString()))
                .forEach(e -> report.note("Component " + BuiltInRegistries.ITEM.getKey(e.getKey()) + ": " + e.getValue() + "; kept as an actual input"));
        return out;
    }

    private SmithingRecipe crafting(SmithingRecipe template, Recipe<?> source, ItemStack result) {
        ComponentGraph.Fraction added = ComponentGraph.Fraction.ZERO;
        Map<String, ComponentGraph.Part> parts = new TreeMap<>();
        Ingredient base = null;
        for (Ingredient ingredient : source.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            ItemStack[] choices = ingredient.getItems();
            if (choices.length == 0 || Arrays.stream(choices).anyMatch(ItemStack::hasCraftingRemainingItem)) {
                keep(source, template, "empty ingredient or native remainder/catalyst; native construction retained");
                return null;
            }
            if (Arrays.stream(choices).allMatch(stack -> isBase(stack, result))) {
                if (base != null) {
                    keep(source, template, "assembly consumes several equipment bases; native assembly retained");
                    return null;
                }
                base = ingredient;
                continue;
            }
            ComponentGraph.Value value = components.ingredient(ingredient).orElse(null);
            if (value != null && value.family().equals(template.family())) {
                added = added.add(value.metal());
                value.auxiliary().values().forEach(part -> ComponentGraph.add(parts, part.ingredient(), part.count()));
            } else ComponentGraph.add(parts, ingredient, ComponentGraph.Fraction.of(1));
        }
        if (added.ceil() <= 0) {
            SmithingRecipe accounting = recipe(template, source, 1, List.of(), result);
            plans.put(accounting.getId(), new Plan(accounting, ComponentGraph.Fraction.ZERO, base));
            keep(source, template, "no unambiguous added primary metal; native transform/assembly and every input retained");
            return null;
        }
        List<AuxiliaryIngredient> auxiliary = new ArrayList<>();
        if (base != null) auxiliary.add(new AuxiliaryIngredient(base, 1, true));
        for (ComponentGraph.Part part : parts.values()) {
            if (!part.count().integral() || part.count().floor() > 4096) {
                keep(source, template, "fractional or excessive auxiliary cost; native component batch retained");
                return null;
            }
            auxiliary.add(new AuxiliaryIngredient(part.ingredient(), part.count().floor(), false));
        }
        SmithingRecipe recipe = recipe(template, source, added.ceil(), auxiliary, result);
        plans.put(recipe.getId(), new Plan(recipe, added, base));
        report.note("Epic source " + source.getId() + ": " + (base == null ? "FORGE_BASE" : "FORGE_UPGRADE")
                + ", added " + added + " " + template.family() + ", charge " + added.ceil()
                + (base == null ? "" : ", consumed base " + base.toJson()));
        return recipe;
    }

    private static boolean isBase(ItemStack input, ItemStack result) {
        if (input.getMaxStackSize() != 1) return false;
        var slot = EquipmentClassifier.armorSlot(input);
        if (slot != null) return slot == EquipmentClassifier.armorSlot(result);
        if (EquipmentClassifier.isShield(input)) return EquipmentClassifier.isShield(result);
        if (EpicEquipment.role(input) == EpicEquipment.Role.RANGED) return EpicEquipment.role(result) == EpicEquipment.Role.RANGED;
        if (EpicEquipment.role(input) == EpicEquipment.Role.HORSE_ARMOR) return EpicEquipment.role(result) == EpicEquipment.Role.HORSE_ARMOR;
        return EquipmentClassifier.hasOwnAttackDamage(input) && EquipmentClassifier.hasOwnAttackDamage(result);
    }

    private SmithingRecipe recipe(SmithingRecipe template, Recipe<?> source, int units, List<AuxiliaryIngredient> auxiliary, ItemStack result) {
        ResourceLocation id = new ResourceLocation(template.getId().getNamespace(), template.getId().getPath()
                + "/from/" + source.getId().getNamespace() + "/" + source.getId().getPath());
        return new SmithingRecipe(id, template.family(), units, auxiliary, result.copy(), template.forgePattern(),
                template.explicitAnvilPattern(), false, source.getId()).sources(List.of(source.getId()), List.of())
                .keepBaseData(true).policyTemplate(template.getId()).processProfile(template.processProfile());
    }

    private void keep(Recipe<?> source, SmithingRecipe template, String reason) {
        report.retained(source.getId(), template.resultItem(), reason);
        // An unproven cheaper native route must not inherit the cost of a more expensive forge route.
        // Only plain, single-output recipes have semantics we can value. Unknown/custom/batched routes
        // establish a zero bound; a pack may provide an explicit, reviewed recycling override.
        SmithingRecipe accounting = recipe(template, source, 1, List.of(), template.result());
        if (plans.containsKey(accounting.getId())) return;
        ComponentGraph.Fraction added = ComponentGraph.Fraction.ZERO;
        Ingredient base = null;
        try {
            ItemStack result = source.getResultItem(access);
            if ((SourceRecipes.kind(source) == SourceRecipes.Kind.PLAIN_CRAFTING
                    || SourceRecipes.kind(source) == SourceRecipes.Kind.SMITHING_UPGRADE) && result.getCount() == 1) {
                boolean multiple = false;
                for (Ingredient ingredient : source.getIngredients()) {
                    if (ingredient.isEmpty()) continue;
                    if (Arrays.stream(ingredient.getItems()).anyMatch(ItemStack::hasCraftingRemainingItem)) {
                        added = ComponentGraph.Fraction.ZERO; base = null; break;
                    }
                    if (ingredient.getItems().length > 0 && Arrays.stream(ingredient.getItems()).allMatch(s -> isBase(s, result))) {
                        if (base != null) multiple = true;
                        base = ingredient;
                    } else {
                        var value = components.ingredient(ingredient).orElse(null);
                        if (value != null && value.family().equals(template.family())) added = added.add(value.metal());
                    }
                }
                if (multiple) base = null; // no inferred value for the several consumed equipment pieces
            }
        } catch (RuntimeException ignored) { added = ComponentGraph.Fraction.ZERO; base = null; }
        plans.put(accounting.getId(), new Plan(accounting, added, base));
    }

    /** Minimum proven content, with a fixed point bounded by the number of equipment nodes, not recipe order. */
    private void priceRecovery(List<SmithingRecipe> recipes) {
        Map<String, ComponentGraph.Fraction> known = new HashMap<>();
        Set<String> pending = new HashSet<>();
        plans.values().forEach(plan -> pending.add(key(plan.recipe().resultItem(), plan.recipe().family())));
        for (SmithingRecipe recipe : recipes) {
            String key = key(recipe.resultItem(), recipe.family());
            if (plans.containsKey(recipe.getId())) pending.add(key);
            else if (recipe.auxiliary().stream().noneMatch(AuxiliaryIngredient::consumesEquipment)) {
                known.merge(key, ComponentGraph.Fraction.of(recipe.metalUnits()), EpicRecipeResolver::minimum);
            }
        }
        for (int pass = 0; pass <= pending.size(); pass++) {
            boolean changed = false;
            for (Plan plan : plans.values()) {
                ComponentGraph.Fraction total = total(plan, known, pending);
                if (total == null) continue;
                String key = key(plan.recipe().resultItem(), plan.recipe().family());
                ComponentGraph.Fraction old = known.get(key);
                if (old == null || total.compareTo(old) < 0) { known.put(key, total); changed = true; }
            }
            if (!changed) break;
        }
        for (Plan plan : plans.values()) {
            ComponentGraph.Fraction total = known.get(key(plan.recipe().resultItem(), plan.recipe().family()));
            plan.recipe().recoverableUnits(total == null ? 0 : total.floor()).addedContentUnits(plan.added().floor());
            if (total == null) report.note("No acyclic material proof for " + plan.recipe().getId() + "; no inferred recycling");
        }
    }

    private ComponentGraph.Fraction total(Plan plan, Map<String, ComponentGraph.Fraction> known, Set<String> pending) {
        if (plan.base() == null) return plan.added();
        ComponentGraph.Fraction base = null;
        for (ItemStack choice : plan.base().getItems()) {
            String key = key(choice.getItem(), plan.recipe().family());
            ComponentGraph.Fraction value = known.get(key);
            if (value == null && pending.contains(key)) return null;
            if (value == null) value = ComponentGraph.Fraction.ZERO; // different/unknown material: never invent recovery
            base = base == null ? value : minimum(base, value);
        }
        return base == null ? null : plan.added().add(base);
    }

    private static String key(Item item, ResourceLocation family) { return BuiltInRegistries.ITEM.getKey(item) + "@" + family; }
    private static ComponentGraph.Fraction minimum(ComponentGraph.Fraction a, ComponentGraph.Fraction b) { return a.compareTo(b) <= 0 ? a : b; }
}
