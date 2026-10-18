package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.compat.Integrations;
import com.otectus.immersivesmithing.config.ServerConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The recipe selectors of the server config, parsed and resolved once per rebuild after tags are bound, so every pass of
 * that rebuild asks the same object:
 *
 * <ul>
 *   <li>{@code recipes.recipeBlacklist}: native recipe ids that are never generated from and never disabled;</li>
 *   <li>{@code recipes.modBlacklist}: item namespaces that are neither detected nor disabled;</li>
 *   <li>{@code recipes.autoDetectExclusions}: item ids and {@code #tags} that automatic detection makes nothing for, in the
 *   crafting, upgrade and chain passes (explicit recipes and native recipes are unaffected);</li>
 *   <li>{@code recipes.smithingRecipeBlacklist}: exact smithing recipe ids, explicit or generated, removed before
 *   suppression so the native recipes they would have replaced stay;</li>
 *   <li>{@code recipes.materialFamilyBlacklist}: matched by the material registry, listed here for the report.</li>
 * </ul>
 *
 * Every entry is registered with the report, which counts what it matched. An entry that cannot be parsed is warned about
 * once per session and listed as a problem; an unknown item, mod or empty tag is a note, never fatal, because optional
 * packs make them normal.
 */
public final class ConfigSelectors {
    public static final String RECIPE_BLACKLIST = "recipes.recipeBlacklist";
    public static final String MOD_BLACKLIST = "recipes.modBlacklist";
    public static final String AUTO_DETECT_EXCLUSIONS = "recipes.autoDetectExclusions";
    public static final String SMITHING_RECIPE_BLACKLIST = "recipes.smithingRecipeBlacklist";
    public static final String MATERIAL_FAMILY_BLACKLIST = "recipes.materialFamilyBlacklist";

    /** Nothing selected; for detection run outside a rebuild (GameTests). */
    public static final ConfigSelectors NONE = new ConfigSelectors(Set.of(), Set.of(), Map.of(), Map.of());

    private static final Set<String> WARNED = Collections.synchronizedSet(new HashSet<>());

    private final Set<String> recipeBlacklist;
    private final Set<String> modBlacklist;
    /** Excluded result items and the entry that excluded each one (an item entry wins over a tag entry). */
    private final Map<Item, String> autoExclusions;
    /** Blacklisted smithing recipe ids and their selector. */
    private final Map<ResourceLocation, String> smithingBlacklist;
    /** Items whose explicit smithing recipe is blacklisted: detection does not stand in for it (filled by the rebuild). */
    private final Map<Item, String> withheld = new HashMap<>();

    private ConfigSelectors(Set<String> recipeBlacklist, Set<String> modBlacklist, Map<Item, String> autoExclusions,
                            Map<ResourceLocation, String> smithingBlacklist) {
        this.recipeBlacklist = recipeBlacklist;
        this.modBlacklist = modBlacklist;
        this.autoExclusions = autoExclusions;
        this.smithingBlacklist = smithingBlacklist;
    }

    /** Parses and resolves the server config's selectors; tags must be bound. */
    public static ConfigSelectors fromConfig(CompatibilityReport report) {
        Set<String> recipes = new LinkedHashSet<>();
        for (String entry : ServerConfig.list(ServerConfig.RECIPE_BLACKLIST)) {
            ResourceLocation id = id(entry, RECIPE_BLACKLIST, report);
            if (id == null) continue;
            recipes.add(id.toString());
            report.selector(selector(RECIPE_BLACKLIST, id.toString()));
        }
        Set<String> mods = new LinkedHashSet<>();
        for (String entry : ServerConfig.list(ServerConfig.MOD_BLACKLIST)) {
            String namespace = entry.trim();
            if (namespace.isEmpty() || !ResourceLocation.isValidNamespace(namespace)) {
                problem(report, MOD_BLACKLIST, entry, "not a mod id");
                continue;
            }
            mods.add(namespace);
            report.selector(selector(MOD_BLACKLIST, namespace));
            if (!ModList.get().isLoaded(namespace)) report.note(selector(MOD_BLACKLIST, namespace) + ": no mod with this id is loaded");
        }
        Map<Item, String> excluded = new LinkedHashMap<>();
        List<String> tagEntries = new java.util.ArrayList<>();
        for (String entry : ServerConfig.list(ServerConfig.AUTO_DETECT_EXCLUSIONS)) {
            String trimmed = entry.trim();
            if (trimmed.startsWith("#")) {
                tagEntries.add(trimmed);
                continue;
            }
            ResourceLocation id = id(trimmed, AUTO_DETECT_EXCLUSIONS, report);
            if (id == null) continue;
            String selector = selector(AUTO_DETECT_EXCLUSIONS, id.toString());
            report.selector(selector);
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                report.note(selector + ": no such item is registered");
                continue;
            }
            excluded.putIfAbsent(BuiltInRegistries.ITEM.get(id), selector);
        }
        for (String entry : tagEntries) {
            ResourceLocation id = id(entry.substring(1), AUTO_DETECT_EXCLUSIONS, report);
            if (id == null) continue;
            String selector = selector(AUTO_DETECT_EXCLUSIONS, "#" + id);
            report.selector(selector);
            TagKey<Item> tag = ItemTags.create(id);
            List<Item> items = BuiltInRegistries.ITEM.getTag(tag).map(set -> set.stream().map(Holder::value).toList()).orElse(List.of());
            if (items.isEmpty()) report.note(selector + ": the tag is empty or does not exist");
            for (Item item : items) excluded.putIfAbsent(item, selector);
        }
        Map<ResourceLocation, String> smithing = new LinkedHashMap<>();
        for (String entry : ServerConfig.list(ServerConfig.SMITHING_RECIPE_BLACKLIST)) {
            ResourceLocation id = id(entry, SMITHING_RECIPE_BLACKLIST, report);
            if (id == null) continue;
            String selector = selector(SMITHING_RECIPE_BLACKLIST, id.toString());
            smithing.put(id, selector);
            report.selector(selector);
        }
        ServerConfig.list(ServerConfig.MATERIAL_FAMILY_BLACKLIST).forEach(f -> report.selector(selector(MATERIAL_FAMILY_BLACKLIST, f.trim())));
        new TreeSet<>(Integrations.disabledNamespaces()).forEach(ns -> report.selector(Integrations.configKey(ns)));
        return new ConfigSelectors(Set.copyOf(recipes), Set.copyOf(mods), Map.copyOf(excluded), Map.copyOf(smithing));
    }

    public static String selector(String key, String entry) {
        return key + " = " + entry;
    }

    public Set<String> recipeBlacklist() {
        return recipeBlacklist;
    }

    public Set<String> modBlacklist() {
        return modBlacklist;
    }

    /** The {@code autoDetectExclusions} entry that keeps automatic detection away from this result, or null. */
    @Nullable
    public String autoExclusion(Item item) {
        return autoExclusions.get(item);
    }

    /** Every excluded item and its entry, for the report's moot exclusions. */
    public Map<Item, String> autoExclusions() {
        return autoExclusions;
    }

    /** The {@code smithingRecipeBlacklist} entry naming this smithing recipe id, or null. */
    @Nullable
    public String smithingBlacklisted(ResourceLocation recipeId) {
        return smithingBlacklist.get(recipeId);
    }

    /** An explicit recipe for this item was blacklisted: automatic detection does not stand in for it. */
    void withhold(Item item, String selector) {
        if (this != NONE) withheld.putIfAbsent(item, selector);
    }

    /** The selector that blacklisted this item's explicit recipe, or null. */
    @Nullable
    public String withheld(Item item) {
        return withheld.get(item);
    }

    @Nullable
    private static ResourceLocation id(String entry, String key, CompatibilityReport report) {
        ResourceLocation id = entry.isBlank() ? null : ResourceLocation.tryParse(entry.trim());
        if (id == null) problem(report, key, entry, "not a valid id");
        return id;
    }

    private static void problem(CompatibilityReport report, String key, String entry, String why) {
        String problem = key + " entry \"" + entry + "\": " + why + ", ignored";
        report.selectorProblem(problem);
        if (WARNED.add(problem)) ImmersiveSmithing.LOGGER.warn("Immersive Smithing config: {}", problem);
    }
}
