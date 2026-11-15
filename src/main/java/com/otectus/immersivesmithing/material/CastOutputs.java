package com.otectus.immersivesmithing.material;

import com.otectus.immersivesmithing.recipe.CompatibilityReport;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a family's molten metal can be cast back into, resolved at every rebuild. Ingots are sources worth exactly one
 * ingot (9 units) that come from a {@code #forge:ingots/...} rule or are in {@code forge:ingots}; nuggets are 1-unit
 * sources from {@code #forge:nuggets/...} or in {@code forge:nuggets}. Raw ore, plates and other 9-unit items never
 * qualify, so a family listed item by item (Cataclysm's metals) names its ingot in the material file's {@code casting}
 * entry. Several candidates are offered in a fixed order: the file's override, then {@code casting.preferredNamespaces},
 * then the family's own namespace, then the item id. The pool keeps the family only, never which mod's ingot went in.
 */
public record CastOutputs(List<Output> ingots, List<Output> nuggets) {
    public static final CastOutputs NONE = new CastOutputs(List.of(), List.of());

    public enum Kind { INGOT, NUGGET }

    public record Output(Item item, int unitsEach, Kind kind) {}

    public boolean isEmpty() {
        return ingots.isEmpty() && nuggets.isEmpty();
    }

    /** Ingots first, then nuggets when they are allowed. */
    public List<Output> offered(boolean nuggetsAllowed) {
        List<Output> out = new ArrayList<>(ingots);
        if (nuggetsAllowed) out.addAll(nuggets);
        return out;
    }

    public Optional<Output> find(Item item) {
        for (Output o : ingots) if (o.item() == item) return Optional.of(o);
        for (Output o : nuggets) if (o.item() == item) return Optional.of(o);
        return Optional.empty();
    }

    /** The outputs of one family from its resolved sources and its material file's casting entry. */
    public static CastOutputs resolve(ResourceLocation family, List<MaterialFamily.Source> sources, @Nullable MaterialDefinition.Casting casting,
                                      List<String> preferredNamespaces, CompatibilityReport report) {
        if (casting != null && !casting.enabled()) return NONE;
        List<Output> ingots = candidates(family, sources, MaterialUnits.INGOT, Kind.INGOT, casting != null ? casting.ingot() : null,
                preferredNamespaces, report);
        List<Output> nuggets = candidates(family, sources, MaterialUnits.NUGGET, Kind.NUGGET, casting != null ? casting.nugget() : null,
                preferredNamespaces, report);
        return ingots.isEmpty() && nuggets.isEmpty() ? NONE : new CastOutputs(ingots, nuggets);
    }

    private static List<Output> candidates(ResourceLocation family, List<MaterialFamily.Source> sources, int units, Kind kind,
                                           @Nullable ResourceLocation override, List<String> preferred, CompatibilityReport report) {
        String prefix = kind == Kind.INGOT ? "#forge:ingots/" : "#forge:nuggets/";
        Set<Item> found = new LinkedHashSet<>();
        Item chosen = null;
        for (MaterialFamily.Source source : sources) {
            if (source.units() != units) continue;
            boolean tagged = source.rule().startsWith(prefix) || source.item().builtInRegistryHolder()
                    .is(kind == Kind.INGOT ? Tags.Items.INGOTS : Tags.Items.NUGGETS);
            if (override != null && override.equals(BuiltInRegistries.ITEM.getKey(source.item()))) chosen = source.item();
            else if (tagged) found.add(source.item());
        }
        if (override != null && chosen == null) {
            report.note("Casting " + kind.name().toLowerCase(java.util.Locale.ROOT) + " " + override + " of " + family
                    + " is ignored: it is not a source of the family worth " + units + (units == 1 ? " unit" : " units"));
        }
        String familyNamespace = family.getNamespace();
        List<Item> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparingInt((Item item) -> rank(item, preferred, familyNamespace))
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        List<Output> out = new ArrayList<>();
        if (chosen != null) out.add(new Output(chosen, units, kind));
        for (Item item : ordered) out.add(new Output(item, units, kind));
        return List.copyOf(out);
    }

    /** Position among the preferred namespaces, then the family's own namespace, then everything else. */
    private static int rank(Item item, List<String> preferred, String familyNamespace) {
        String namespace = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        int index = preferred.indexOf(namespace);
        if (index >= 0) return index;
        return namespace.equals(familyNamespace) ? preferred.size() : preferred.size() + 1;
    }
}
