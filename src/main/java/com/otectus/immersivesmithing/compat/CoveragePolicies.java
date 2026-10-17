package com.otectus.immersivesmithing.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.recipe.SmithingData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code data/<namespace>/immersive_smithing/coverage/<name>.json}: the reviewed decision for a supported mod's items
 * that have no forge recipe on purpose (relics, transformations, visor states, assemblies), with the reason the coverage
 * catalog prints, and the version the decisions were made against. An item of a curated integration that has neither a
 * forge recipe nor a decision here is reported as {@link CoverageDisposition#MISSING_SUPPORT}.
 *
 * <pre>
 * { "conditions": [...], "mod": "magistuarmory", "tested_version": "10.15",
 *   "policies": [ { "items": ["magistuarmory:rustedbarbute"], "disposition": "protected_loot", "reason": "..." },
 *                 { "items": ["epicknights_open_helms:open_armet_helmet"], "disposition": "native_transformation",
 *                   "reason": "...", "state_of": "magistuarmory:armet" } ] }
 * </pre>
 *
 * {@code state_of} marks another state of the same physical piece (an opened visor): it melts for exactly what that piece
 * melts for. Items may also be {@code #tag} references. Unknown items are ignored, so one file serves every version.
 */
public final class CoveragePolicies extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public record Policy(CoverageDisposition disposition, String reason, @Nullable ResourceLocation stateOf) {}

    private record Rule(String target, Policy policy) {}

    private static volatile List<Rule> rules = List.of();
    private static volatile Map<String, String> testedVersions = Map.of();

    private final ICondition.IContext context;

    public CoveragePolicies(ICondition.IContext context) {
        super(GSON, "immersive_smithing/coverage");
        this.context = context;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Rule> list = new ArrayList<>();
        Map<String, String> versions = new TreeMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "coverage policy");
                if (!CraftingHelper.processConditions(json, "conditions", context)) continue;
                if (json.has("mod") && json.has("tested_version")) {
                    versions.put(GsonHelper.getAsString(json, "mod"), GsonHelper.getAsString(json, "tested_version"));
                }
                for (JsonElement e : GsonHelper.getAsJsonArray(json, "policies")) {
                    JsonObject p = GsonHelper.convertToJsonObject(e, "policy");
                    Policy policy = new Policy(CoverageDisposition.byId(GsonHelper.getAsString(p, "disposition")),
                            GsonHelper.getAsString(p, "reason"),
                            p.has("state_of") ? new ResourceLocation(GsonHelper.getAsString(p, "state_of")) : null);
                    for (JsonElement item : GsonHelper.getAsJsonArray(p, "items")) list.add(new Rule(item.getAsString(), policy));
                }
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Skipping malformed coverage policy {}: {}", entry.getKey(), e.getMessage());
            }
        }
        rules = List.copyOf(list);
        testedVersions = Map.copyOf(versions);
        SmithingData.markDirty();
    }

    /** The decision recorded for an item, if any. Item entries win over tag entries. Needs bound tags for tag entries. */
    public static Optional<Policy> policy(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        Policy byTag = null;
        for (Rule rule : rules) {
            if (rule.target().equals(id)) return Optional.of(rule.policy());
            if (byTag == null && rule.target().startsWith("#")
                    && item.builtInRegistryHolder().is(ItemTags.create(new ResourceLocation(rule.target().substring(1))))) {
                byTag = rule.policy();
            }
        }
        return Optional.ofNullable(byTag);
    }

    /** Every item a decision names directly, in file order; tag entries are resolved through the bound tags. */
    public static Map<Item, Policy> all() {
        Map<Item, Policy> out = new LinkedHashMap<>();
        for (Rule rule : rules) {
            if (rule.target().startsWith("#")) {
                BuiltInRegistries.ITEM.getTag(ItemTags.create(new ResourceLocation(rule.target().substring(1))))
                        .ifPresent(set -> set.stream().map(Holder::value).forEach(i -> out.putIfAbsent(i, rule.policy())));
            } else {
                ResourceLocation id = ResourceLocation.tryParse(rule.target());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) out.putIfAbsent(BuiltInRegistries.ITEM.get(id), rule.policy());
            }
        }
        return out;
    }

    /** Mod id to the version the bundled decisions and recipes were made against. */
    public static Map<String, String> testedVersions() {
        return testedVersions;
    }
}
