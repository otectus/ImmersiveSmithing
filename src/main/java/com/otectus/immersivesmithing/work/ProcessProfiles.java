package com.otectus.immersivesmithing.work;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The loaded process profiles ({@code data/<namespace>/immersive_smithing/process_profiles/*.json}) and the legacy anvil
 * pattern each one covers. A reload validates every file first and swaps the complete set in at once; a file that fails
 * validation is reported with its resource id and field and left out entirely, never half-applied. A built-in generic
 * profile always exists, so no recipe is ever without a process.
 */
public final class ProcessProfiles {
    public static final ResourceLocation GENERIC_ID = ImmersiveSmithing.id("generic");
    /** Used when even the bundled data is missing: a broad blank with rough, shaping and finishing work. */
    public static final ProcessProfile GENERIC = new ProcessProfile(GENERIC_ID, GENERIC_ID, null, List.of(
            new ProcessProfile.Operation("rough_out", "body", ProcessProfile.Kind.DRAW, 500, 2, ProcessProfile.Face.FRONT,
                    0F, 0.6F, -0.5F, 0.5F, List.of()),
            new ProcessProfile.Operation("form_end", "end", ProcessProfile.Kind.SHAPE, 400, 1, ProcessProfile.Face.FRONT,
                    0.6F, 1F, -0.5F, 0.5F, List.of()),
            new ProcessProfile.Operation("dress", "surface", ProcessProfile.Kind.FINISH, 300, 1, ProcessProfile.Face.BACK,
                    0F, 1F, -0.5F, 0.5F, List.of("rough_out"))),
            60, 650, List.of(), List.of());

    private static volatile Map<ResourceLocation, ProcessProfile> profiles = Map.of(GENERIC_ID, GENERIC);
    private static volatile Map<ResourceLocation, ResourceLocation> byPattern = Map.of();
    private static volatile List<String> problems = List.of();

    public static ProcessProfile get(@Nullable ResourceLocation id) {
        ProcessProfile p = id == null ? null : profiles.get(id);
        return p != null ? p : generic();
    }

    @Nullable
    public static ProcessProfile find(@Nullable ResourceLocation id) {
        return id == null ? null : profiles.get(id);
    }

    public static ProcessProfile generic() {
        ProcessProfile p = profiles.get(GENERIC_ID);
        return p != null ? p : GENERIC;
    }

    /** The profile mapped to a legacy anvil pattern id, or null. */
    @Nullable
    public static ProcessProfile forPattern(@Nullable ResourceLocation pattern) {
        ResourceLocation id = pattern == null ? null : byPattern.get(pattern);
        return id == null ? null : profiles.get(id);
    }

    public static Map<ResourceLocation, ProcessProfile> all() {
        return profiles;
    }

    public static Map<ResourceLocation, ResourceLocation> patternMap() {
        return byPattern;
    }

    /** Files rejected by the last reload, each "resource: problem". */
    public static List<String> problems() {
        return problems;
    }

    static void install(Map<ResourceLocation, ProcessProfile> loaded, List<String> errors) {
        Map<ResourceLocation, ProcessProfile> next = new LinkedHashMap<>(loaded);
        next.putIfAbsent(GENERIC_ID, GENERIC);
        Map<ResourceLocation, ResourceLocation> patterns = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>(errors);
        for (ProcessProfile p : new TreeMap<>(next).values()) {
            for (ResourceLocation pattern : p.patterns()) {
                ResourceLocation previous = patterns.putIfAbsent(pattern, p.id());
                if (previous != null) problems.add(p.id() + ": pattern " + pattern + " is already covered by " + previous + "; ignored here");
            }
        }
        profiles = Collections.unmodifiableMap(next);
        byPattern = Collections.unmodifiableMap(patterns);
        ProcessProfiles.problems = List.copyOf(problems);
        for (String problem : problems) ImmersiveSmithing.LOGGER.error("Immersive Smithing process profile {}", problem);
    }

    /** Loads {@code immersive_smithing/process_profiles}. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();
        private final ICondition.IContext context;

        public Loader(ICondition.IContext context) {
            super(GSON, "immersive_smithing/process_profiles");
            this.context = context;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, ProcessProfile> loaded = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : new TreeMap<>(files).entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "process profile");
                    if (!CraftingHelper.processConditions(json, "conditions", context)) continue;
                    loaded.put(entry.getKey(), ProcessProfile.fromJson(entry.getKey(), json));
                } catch (RuntimeException e) {
                    errors.add(entry.getKey() + ": " + e.getMessage());
                }
            }
            install(loaded, errors);
            ImmersiveSmithing.LOGGER.debug("Loaded {} process profiles", loaded.size());
        }
    }

    private ProcessProfiles() {}
}
