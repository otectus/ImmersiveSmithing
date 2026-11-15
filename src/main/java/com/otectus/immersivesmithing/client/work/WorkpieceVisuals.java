package com.otectus.immersivesmithing.client.work;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.otectus.immersivesmithing.ImmersiveSmithing;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's workpiece visual profiles, reloaded with resource packs. A missing or invalid profile falls back to a broad
 * generic blank, so a piece always renders and gameplay never depends on presentation data. Resource reloads rebuild
 * visuals only; no gameplay state changes.
 */
public final class WorkpieceVisuals extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, WorkpieceVisual> visuals = Map.of();
    private static volatile int generation;

    public WorkpieceVisuals() {
        super(GSON, "immersive_smithing/workpiece_profiles");
    }

    public static WorkpieceVisual get(@Nullable ResourceLocation id) {
        WorkpieceVisual v = id == null ? null : visuals.get(id);
        if (v != null) return v;
        v = visuals.get(ImmersiveSmithing.id("generic"));
        return v != null ? v : WorkpieceVisual.fallback(id != null ? id : ImmersiveSmithing.id("generic"));
    }

    public static boolean has(ResourceLocation id) {
        return visuals.containsKey(id);
    }

    /** Bumped on every reload, for caches keyed by profile. */
    public static int generation() {
        return generation;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, WorkpieceVisual> next = new HashMap<>();
        files.forEach((id, json) -> {
            try {
                next.put(id, WorkpieceVisual.fromJson(id, GsonHelper.convertToJsonObject(json, "workpiece profile")));
            } catch (RuntimeException e) {
                ImmersiveSmithing.LOGGER.error("Ignoring workpiece profile {}: {}", id, e.getMessage());
            }
        });
        visuals = Map.copyOf(next);
        generation++;
    }
}
