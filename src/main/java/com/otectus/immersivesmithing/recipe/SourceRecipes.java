package com.otectus.immersivesmithing.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Classifies a conventional recipe before Immersive Smithing adapts it. Only recipes whose whole behaviour the forge
 * can reproduce are converted or disabled: exact vanilla shaped and shapeless crafting (a fresh result), exact vanilla
 * smithing-table upgrades and the known crafting recipes that hand the reworked piece's data to the result. Every
 * other class or serializer (a reroll that removes a rolled power, a recipe with its own assembly) is left alone.
 */
public final class SourceRecipes {

    public enum Kind {
        /** Exact {@link ShapedRecipe} or {@link ShapelessRecipe}: the result is a fresh stack. */
        PLAIN_CRAFTING(true, false),
        /** A crafting recipe whose result takes over the reworked piece's whole tag (Botania's armor upgrade). */
        DATA_CARRYING_CRAFTING(true, true),
        /** Exact {@link SmithingTransformRecipe}: the result takes over the base piece's whole tag. */
        SMITHING_UPGRADE(false, true),
        /** Custom classes and serializers. Never converted, never disabled. */
        CUSTOM(false, false);

        private final boolean crafting;
        private final boolean carriesBaseData;

        Kind(boolean crafting, boolean carriesBaseData) {
            this.crafting = crafting;
            this.carriesBaseData = carriesBaseData;
        }

        public boolean isCrafting() { return crafting; }
        /** Whether the native recipe's result keeps the consumed piece's data (enchantments, name, damage...). */
        public boolean carriesBaseData() { return carriesBaseData; }
        public boolean isAdaptable() { return this != CUSTOM; }
    }

    /**
     * Crafting serializers known to copy the reworked piece's whole tag into the result, so the forge reproduces them
     * by keeping the consumed piece's data (see {@link ResultAssembler}).
     */
    private static final Set<ResourceLocation> DATA_CARRYING_SERIALIZERS = Set.of(
            new ResourceLocation("botania", "armor_upgrade"));

    public static Kind kind(Recipe<?> recipe) {
        Class<?> type = recipe.getClass();
        if (type == ShapedRecipe.class || type == ShapelessRecipe.class) return Kind.PLAIN_CRAFTING;
        if (type == SmithingTransformRecipe.class) return Kind.SMITHING_UPGRADE;
        ResourceLocation serializer = serializerId(recipe);
        if (serializer != null && DATA_CARRYING_SERIALIZERS.contains(serializer) && recipe instanceof ShapedRecipe) {
            return Kind.DATA_CARRYING_CRAFTING;
        }
        return Kind.CUSTOM;
    }

    @Nullable
    public static ResourceLocation serializerId(Recipe<?> recipe) {
        try {
            return BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "type minecraft:smithing_transform" style label for the compatibility report. */
    public static String describe(Recipe<?> recipe) {
        ResourceLocation serializer = serializerId(recipe);
        return (serializer != null ? serializer.toString() : "unregistered serializer") + " (" + recipe.getClass().getName() + ")";
    }

    private SourceRecipes() {}
}
