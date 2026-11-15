package com.otectus.immersivesmithing.recipe;

import com.google.gson.Gson;
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

import java.util.Map;
import java.util.TreeMap;

/** Pack-authored decisions for exact source IDs, evaluated before any generation or suppression. */
public final class SourceRecipePolicies extends SimpleJsonResourceReloadListener {
    public record Policy(boolean preserve, String reason) {}
    private static volatile Map<ResourceLocation, Policy> policies = Map.of();
    private final ICondition.IContext context;

    public SourceRecipePolicies(ICondition.IContext context) {
        super(new Gson(), "immersive_smithing/recipe_policies");
        this.context = context;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, Policy> next = new TreeMap<>();
        for (var file : new TreeMap<>(files).entrySet()) {
            try {
                JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "source recipe policy");
                if (!CraftingHelper.processConditions(json, "conditions", context)) continue;
                // Resource ID is the source ID, so normal resource-pack priority resolves overrides atomically.
                String operation = GsonHelper.getAsString(json, "operation");
                if (!operation.equals("native") && !operation.equals("forge")) {
                    throw new IllegalArgumentException("operation must be native or forge");
                }
                next.put(file.getKey(), new Policy(operation.equals("native"),
                        GsonHelper.getAsString(json, "reason", "pack source-recipe policy")));
            } catch (RuntimeException e) {
                // A malformed policy must never accidentally authorize removal of the native route.
                next.put(file.getKey(), new Policy(true, "invalid pack policy: " + e.getMessage()));
                ImmersiveSmithing.LOGGER.error("Invalid source recipe policy {}: {}", file.getKey(), e.getMessage());
            }
        }
        policies = Map.copyOf(next);
        SmithingData.markDirty();
    }

    public static boolean preserves(ResourceLocation source) {
        Policy policy = policies.get(source);
        return policy != null && policy.preserve();
    }

    public static String reason(ResourceLocation source) {
        Policy policy = policies.get(source);
        return policy == null ? "no source policy" : policy.reason();
    }

}
