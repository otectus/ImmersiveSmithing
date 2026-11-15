package com.otectus.immersivesmithing.compat.epicknights;

import com.otectus.immersivesmithing.material.MaterialRegistry;
import com.otectus.immersivesmithing.recipe.SourceRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.math.BigInteger;
import java.util.*;

/** Reload-local, bounded analysis of reviewed manufacturing components. Never registers a melt source. */
public final class ComponentGraph {
    public record Fraction(BigInteger numerator, BigInteger denominator) implements Comparable<Fraction> {
        public static final Fraction ZERO = of(0);
        public Fraction {
            if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("negative component cost");
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
            if (numerator.bitLength() > 48 || denominator.bitLength() > 48) throw new IllegalArgumentException("component arithmetic limit");
        }
        public static Fraction of(long n) { return new Fraction(BigInteger.valueOf(n), BigInteger.ONE); }
        public Fraction add(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator));
        }
        public Fraction divide(int n) { return new Fraction(numerator, denominator.multiply(BigInteger.valueOf(n))); }
        public int floor() { return numerator.divide(denominator).intValueExact(); }
        public int ceil() { return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator).intValueExact(); }
        public boolean integral() { return denominator.equals(BigInteger.ONE); }
        @Override public int compareTo(Fraction other) { return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)); }
        @Override public String toString() { return integral() ? numerator.toString() : numerator + "/" + denominator; }
    }

    public record Part(Ingredient ingredient, Fraction count) {}
    public record Value(ResourceLocation family, Fraction metal, Map<String, Part> auxiliary) {
        public Value { auxiliary = Map.copyOf(auxiliary); }
        public Value divide(int count) {
            Map<String, Part> divided = new TreeMap<>();
            auxiliary.forEach((key, part) -> divided.put(key, new Part(part.ingredient(), part.count().divide(count))));
            return new Value(family, metal.divide(count), divided);
        }
        public String signature() {
            StringBuilder out = new StringBuilder(family + "=" + metal);
            new TreeMap<>(auxiliary).forEach((key, p) -> out.append(';').append(key).append('=').append(p.count()));
            return out.toString();
        }
    }

    // Exact IDs reviewed in the pinned jars. Handles, stocks, locks, templates, equipment and alloy production stay out.
    private static final Set<String> COMPONENTS = Set.of(
            "magistuarmory:steel_plate", "magistuarmory:small_steel_plate", "magistuarmory:steel_ring",
            "magistuarmory:steel_chain", "magistuarmory:steel_chainmail", "magistuarmory:lamellar_rows",
            "antiquelegacy:iron_plate", "antiquelegacy:bronze_plate", "antiquelegacy:small_iron_plate",
            "antiquelegacy:small_bronze_plate", "antiquelegacy:iron_ring", "antiquelegacy:iron_chainmail",
            "antiquelegacy:iron_lamellar_rows", "antiquelegacy:bronze_lamellar_rows",
            "ekotsk:blacked_steel_plate", "ekotsk:blacked_steel_small_plate", "ekotsk:blacked_steel_ring",
            "ekotsk:blacked_steel_chainmail", "ekotsk:gilded_steel_plate", "ekotsk:gilded_steel_small_plate",
            "ekotsk:gilded_steel_ring", "ekotsk:gilded_steel_chainmail", "bronzeaddition_epicknights:bronze_plate");

    private final MaterialRegistry materials;
    private final RegistryAccess access;
    private final Map<Item, List<Recipe<?>>> sources = new HashMap<>();
    private final Map<Item, Optional<Value>> cache = new HashMap<>();
    private final Map<Item, String> reasons = new HashMap<>();
    private final Set<Item> visiting = new HashSet<>();
    private int visits;

    public ComponentGraph(Collection<Recipe<?>> recipes, RegistryAccess access, MaterialRegistry materials) {
        this.access = access;
        this.materials = materials;
        recipes.stream().sorted(Comparator.comparing(r -> r.getId().toString())).forEach(recipe -> {
            try {
                ItemStack result = recipe.getResultItem(access);
                if (!result.isEmpty() && isComponent(result.getItem())) sources.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(recipe);
            } catch (RuntimeException ignored) { /* A broken native recipe cannot prove a component's cost. */ }
        });
    }

    public static boolean isComponent(Item item) { return COMPONENTS.contains(BuiltInRegistries.ITEM.getKey(item).toString()); }
    public Map<Item, String> reasons() { return Map.copyOf(reasons); }

    public Optional<Value> item(Item item) {
        // Bundled fixed plate sources were removed. A remaining explicit material source is a pack/API override.
        MaterialRegistry.Entry raw = materials.lookup(item);
        if (raw != null && (!isComponent(item) || !raw.rule().startsWith("verified active component recipes"))) {
            return Optional.of(new Value(raw.family(), Fraction.of(raw.units()), Map.of()));
        }
        if (!isComponent(item)) {
            return Optional.empty();
        }
        if (cache.containsKey(item)) return cache.get(item);
        if (visiting.size() >= 64 || ++visits > 4096 || !visiting.add(item)) return failed(item, "cycle or component graph limit");
        Optional<Value> result;
        try {
            List<Recipe<?>> recipes = sources.getOrDefault(item, List.of());
            if (recipes.isEmpty()) return failed(item, "no active component recipe");
            Value value = null;
            for (Recipe<?> recipe : recipes) {
                if (SourceRecipes.kind(recipe) != SourceRecipes.Kind.PLAIN_CRAFTING) return failed(item, "nonstandard component serializer: " + recipe.getId());
                ItemStack output = recipe.getResultItem(access);
                if (output.getCount() <= 0 || output.getCount() > output.getMaxStackSize() || output.hasTag()) {
                    return failed(item, "invalid count or stateful component output: " + recipe.getId());
                }
                Value next = construction(recipe.getIngredients()).orElse(null);
                if (next == null) return failed(item, "ambiguous or unknown component ingredients: " + recipe.getId());
                next = next.divide(output.getCount());
                if (value != null && !value.signature().equals(next.signature())) return failed(item, "component routes disagree: " + recipe.getId());
                value = next;
            }
            result = Optional.ofNullable(value);
        } catch (RuntimeException e) {
            return failed(item, e.getMessage());
        } finally {
            visiting.remove(item);
        }
        cache.put(item, result);
        return result;
    }

    /** Direct insertion is allowed only for proven whole, pure metal components; fractions remain native batches. */
    public Map<Item, MaterialRegistry.Entry> integralSources() {
        Map<Item, MaterialRegistry.Entry> out = new HashMap<>();
        for (String name : new TreeSet<>(COMPONENTS)) {
            ResourceLocation id = new ResourceLocation(name);
            if (!BuiltInRegistries.ITEM.containsKey(id)) continue;
            Item item = BuiltInRegistries.ITEM.get(id);
            item(item).filter(value -> value.metal().integral() && value.metal().floor() > 0 && value.auxiliary().isEmpty())
                    .ifPresent(value -> out.put(item, new MaterialRegistry.Entry(value.family(), value.metal().floor(), "verified active component recipes for " + id)));
        }
        return Map.copyOf(out);
    }

    /** An ingredient may expand only when every alternative has identical verified metal and nonmetal costs. */
    public Optional<Value> ingredient(Ingredient ingredient) {
        if (ingredient.getClass() != Ingredient.class) return Optional.empty();
        ItemStack[] alternatives = ingredient.getItems();
        if (alternatives.length == 0 || alternatives.length > 1024) return Optional.empty();
        Value same = null;
        for (ItemStack stack : alternatives) {
            if (stack.hasTag() || stack.hasCraftingRemainingItem()) return Optional.empty();
            Value value = item(stack.getItem()).orElse(null);
            if (value == null || (same != null && !same.signature().equals(value.signature()))) return Optional.empty();
            same = value;
        }
        return Optional.ofNullable(same);
    }

    private Optional<Value> construction(List<Ingredient> ingredients) {
        ResourceLocation family = null;
        Fraction metal = Fraction.ZERO;
        Map<String, Part> auxiliary = new TreeMap<>();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            if (ingredient.getItems().length == 0 || Arrays.stream(ingredient.getItems()).anyMatch(ItemStack::hasCraftingRemainingItem)) return Optional.empty();
            Value value = ingredient(ingredient).orElse(null);
            if (value == null) {
                // A failed metal subcomponent cannot be silently reinterpreted as a nonmetal fitting.
                if (Arrays.stream(ingredient.getItems()).anyMatch(s -> isComponent(s.getItem()) || materials.lookup(s.getItem()) != null)) return Optional.empty();
                add(auxiliary, ingredient, Fraction.of(1));
            } else {
                if (family != null && !family.equals(value.family())) return Optional.empty();
                family = value.family();
                metal = metal.add(value.metal());
                value.auxiliary().values().forEach(part -> add(auxiliary, part.ingredient(), part.count()));
            }
        }
        return family == null || metal.ceil() == 0 ? Optional.empty() : Optional.of(new Value(family, metal, auxiliary));
    }

    public static void add(Map<String, Part> auxiliary, Ingredient ingredient, Fraction count) {
        String key = ingredient.toJson().toString();
        auxiliary.merge(key, new Part(ingredient, count), (a, b) -> new Part(a.ingredient(), a.count().add(b.count())));
    }

    private Optional<Value> failed(Item item, String reason) {
        reasons.put(item, reason);
        cache.put(item, Optional.empty());
        return Optional.empty();
    }
}
