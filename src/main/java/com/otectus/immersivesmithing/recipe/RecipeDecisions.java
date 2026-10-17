package com.otectus.immersivesmithing.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the last rebuild decided for each output item: every source recipe it considered, the smithing recipe generated
 * from it or standing in for it, and the configuration selector that decided, if one did. Filled while the rebuild runs
 * (through {@link CompatibilityReport}) and read by {@code /immersivesmithing recipe <item>}, the report and the catalog.
 */
public final class RecipeDecisions {

    /** Whose recipe the decision is about: an explicit smithing recipe, automatic detection, or a native recipe. */
    public enum Origin {
        EXPLICIT, AUTOMATIC, NATIVE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Outcome {
        /** An explicit smithing recipe is in use. */
        ACTIVE,
        /** An explicit smithing recipe is not (unknown metal, excluded result, the native recipe it follows is gone...). */
        INACTIVE,
        /** An automatic smithing recipe was generated from this source. */
        GENERATED,
        /** Automatic detection made nothing from this source. */
        SKIPPED,
        /** The native recipe was replaced and removed. */
        DISABLED,
        /** The native recipe stays although the forge makes the item. */
        KEPT,
        /** The native recipe stays because no forge recipe makes the item. */
        UNTOUCHED;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One decision. {@code recipe} is the source recipe (a native recipe, or the explicit smithing recipe itself);
     * {@code smithing} the smithing recipe generated from it or replacing it; {@code selector} the configuration entry
     * that decided ({@code recipes.recipeBlacklist = minecraft:iron_sword}), null when no option did.
     */
    public record Decision(Item output, @Nullable ResourceLocation recipe, Origin origin, Outcome outcome,
                           @Nullable ResourceLocation smithing, String reason, @Nullable String selector) {}

    private final Map<Item, List<Decision>> byItem = new HashMap<>();
    private final Map<ResourceLocation, String> types = new HashMap<>();

    void add(Decision decision) {
        List<Decision> list = byItem.computeIfAbsent(decision.output(), k -> new ArrayList<>());
        if (!list.contains(decision)) list.add(decision);
    }

    /** Remembers the serializer of every source recipe, so a decision can name its type. */
    void sources(Collection<Recipe<?>> recipes) {
        for (Recipe<?> r : recipes) {
            ResourceLocation serializer = SourceRecipes.serializerId(r);
            types.put(r.getId(), serializer != null ? serializer.toString() : "?");
        }
    }

    /** The decisions about one output item, explicit and active ones first. */
    public List<Decision> decisionsFor(Item item) {
        List<Decision> list = new ArrayList<>(byItem.getOrDefault(item, List.of()));
        list.sort(Comparator.comparing((Decision d) -> d.origin().ordinal()).thenComparing(d -> d.outcome().ordinal())
                .thenComparing(d -> String.valueOf(d.recipe())));
        return list;
    }

    /** Every decision a configuration selector made, by item id. */
    public Map<String, List<Decision>> bySelector() {
        Map<String, List<Decision>> out = new TreeMap<>();
        byItem.values().forEach(list -> list.forEach(d -> {
            if (d.selector() != null) out.computeIfAbsent(d.selector(), k -> new ArrayList<>()).add(d);
        }));
        out.values().forEach(list -> list.sort(Comparator.comparing(d -> BuiltInRegistries.ITEM.getKey(d.output()).toString())));
        return out;
    }

    /** The recipe's serializer ({@code minecraft:crafting_shaped}), or {@code immersive_smithing:smithing} for a generated recipe. */
    public String type(@Nullable ResourceLocation recipe) {
        if (recipe == null) return "?";
        String type = types.get(recipe);
        return type != null ? type : "immersive_smithing:smithing";
    }

    /** "minecraft:iron_sword (minecraft:crafting_shaped, native): disabled, replaced by ... [recipes.recipeBlacklist = ...]". */
    public String describe(Decision d) {
        StringBuilder out = new StringBuilder();
        out.append(d.recipe() != null ? d.recipe().toString() : "-").append(" (").append(type(d.recipe())).append(", ")
                .append(d.origin().id()).append("): ").append(d.outcome().id());
        if (!d.reason().isEmpty()) out.append(": ").append(d.reason());
        if (d.smithing() != null && !d.smithing().equals(d.recipe())) out.append(" -> ").append(d.smithing());
        if (d.selector() != null) out.append(" [").append(d.selector()).append(']');
        return out.toString();
    }
}
