package com.otectus.immersivesmithing.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersivesmithing.minigame.ForgePattern;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON format:
 * <pre>
 * {
 *   "type": "immersive_smithing:smithing",
 *   "material": "minecraft:iron",
 *   "metal_units": 27,
 *   "auxiliary": [{"ingredient": {"item": "minecraft:stick"}, "count": 2}],
 *   "result": {"item": "minecraft:iron_pickaxe"},
 *   "forge_pattern": "immersive_smithing:standard",
 *   "anvil_pattern": "immersive_smithing:pickaxe",
 *   "keep_base_data": true,
 *   "process_profile": "immersive_smithing:straight_blade"
 * }
 * </pre>
 * {@code forge_pattern} is still read for compatibility but no longer used; {@code process_profile} is optional (the anvil
 * pattern, then the equipment classifier, choose one otherwise).
 * <pre>
 * </pre>
 */
public class SmithingRecipeSerializer implements RecipeSerializer<SmithingRecipe> {

    @Override
    public SmithingRecipe fromJson(ResourceLocation id, JsonObject json) {
        ResourceLocation family = new ResourceLocation(GsonHelper.getAsString(json, "material"));
        int units = epicPolicy(json) ? 1 : GsonHelper.getAsInt(json, "metal_units");
        if (units <= 0) throw new JsonParseException("metal_units must be positive");
        List<AuxiliaryIngredient> aux = new ArrayList<>();
        if (json.has("auxiliary")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "auxiliary")) {
                aux.add(AuxiliaryIngredient.fromJson(GsonHelper.convertToJsonObject(e, "auxiliary")));
            }
        }
        ItemStack result = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
        if (result.isEmpty()) throw new JsonParseException("Smithing recipe result is empty");
        ResourceLocation forgePattern = json.has("forge_pattern")
                ? new ResourceLocation(GsonHelper.getAsString(json, "forge_pattern")) : ForgePattern.STANDARD;
        ResourceLocation anvilPattern = json.has("anvil_pattern")
                ? new ResourceLocation(GsonHelper.getAsString(json, "anvil_pattern")) : null;
        return new SmithingRecipe(id, family, units, aux, result, forgePattern, anvilPattern, false, null)
                .keepBaseData(GsonHelper.getAsBoolean(json, "keep_base_data", true))
                .resolveEpicSource(epicPolicy(json))
                .sources(ids(json, "replaces"), ids(json, "derived_from"))
                .processProfile(json.has("process_profile") ? new ResourceLocation(GsonHelper.getAsString(json, "process_profile")) : null);
    }

    private static boolean epicPolicy(JsonObject json) {
        if (!json.has("source_policy")) return false;
        if (!GsonHelper.getAsString(json, "source_policy").equals("epic_knights")) {
            throw new JsonParseException("Unknown source_policy (expected epic_knights)");
        }
        return true;
    }

    private static List<ResourceLocation> ids(JsonObject json, String key) {
        if (!json.has(key)) return List.of();
        List<ResourceLocation> out = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, key)) out.add(new ResourceLocation(GsonHelper.convertToString(e, key)));
        return out;
    }

    @Override
    public @Nullable SmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
        ResourceLocation family = buf.readResourceLocation();
        int units = buf.readVarInt();
        int auxCount = buf.readVarInt();
        List<AuxiliaryIngredient> aux = new ArrayList<>(auxCount);
        for (int i = 0; i < auxCount; i++) aux.add(AuxiliaryIngredient.read(buf));
        ItemStack result = buf.readItem();
        ResourceLocation forgePattern = buf.readResourceLocation();
        ResourceLocation anvilPattern = buf.readBoolean() ? buf.readResourceLocation() : null;
        boolean auto = buf.readBoolean();
        ResourceLocation source = buf.readBoolean() ? buf.readResourceLocation() : null;
        SmithingRecipe recipe = new SmithingRecipe(id, family, units, aux, result, forgePattern, anvilPattern, auto, source);
        recipe.keepBaseData(buf.readBoolean()).recoverableUnits(buf.readVarInt()).addedContentUnits(buf.readVarInt());
        recipe.processProfile(buf.readBoolean() ? buf.readResourceLocation() : null);

        Component familyName = buf.readComponent();
        int slots = buf.readVarInt();
        List<List<ItemStack>> display = new ArrayList<>(slots);
        for (int i = 0; i < slots; i++) {
            int n = buf.readVarInt();
            List<ItemStack> alternatives = new ArrayList<>(n);
            for (int j = 0; j < n; j++) alternatives.add(buf.readItem());
            display.add(List.copyOf(alternatives));
        }
        ResourceLocation resolved = buf.readResourceLocation();
        recipe.setDisplay(familyName, List.copyOf(display), resolved);
        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf, SmithingRecipe recipe) {
        buf.writeResourceLocation(recipe.family());
        buf.writeVarInt(recipe.metalUnits());
        buf.writeVarInt(recipe.auxiliary().size());
        recipe.auxiliary().forEach(a -> a.write(buf));
        buf.writeItem(recipe.result());
        buf.writeResourceLocation(recipe.forgePattern());
        buf.writeBoolean(recipe.explicitAnvilPattern() != null);
        if (recipe.explicitAnvilPattern() != null) buf.writeResourceLocation(recipe.explicitAnvilPattern());
        buf.writeBoolean(recipe.isAuto());
        buf.writeBoolean(recipe.sourceRecipe() != null);
        if (recipe.sourceRecipe() != null) buf.writeResourceLocation(recipe.sourceRecipe());
        buf.writeBoolean(recipe.keepsBaseData());
        buf.writeVarInt(recipe.recoverableUnits());
        buf.writeVarInt(recipe.addedContentUnits());
        buf.writeBoolean(recipe.processProfile() != null);
        if (recipe.processProfile() != null) buf.writeResourceLocation(recipe.processProfile());

        buf.writeComponent(recipe.familyName());
        buf.writeVarInt(recipe.metalDisplay().size());
        for (List<ItemStack> alternatives : recipe.metalDisplay()) {
            buf.writeVarInt(alternatives.size());
            alternatives.forEach(buf::writeItem);
        }
        buf.writeResourceLocation(recipe.anvilPattern());
    }
}
