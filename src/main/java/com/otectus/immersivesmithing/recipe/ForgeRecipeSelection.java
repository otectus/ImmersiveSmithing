package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Which recipes the Forge screen may offer: right family, enough molten metal, auxiliary items on hand. */
public final class ForgeRecipeSelection {

    /** The inventory slot holding the tongs, which must never be consumed as an ingredient. */
    public static int protectedSlot(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : 40;
    }

    public static List<SmithingRecipe> eligible(Player player, SmithsForgeBlockEntity forge, int protectedSlot) {
        return SmithingData.current().recipesFor(forge.family()).stream()
                .filter(r -> unitsFor(player, r) <= forge.moltenUnits())
                .filter(r -> AuxInventory.hasAll(player, r.auxiliary(), protectedSlot))
                .filter(r -> denial(player, r).isEmpty())
                .sorted(Comparator.comparing(r -> BuiltInRegistries.ITEM.getKey(r.resultItem()).toString()))
                .toList();
    }

    /** What the forge will charge this smith: the recipe's units through the registered cost modifiers. */
    public static int unitsFor(Player player, SmithingRecipe recipe) {
        return player instanceof ServerPlayer smith ? ImmersiveSmithingAPI.metalUnits(smith, recipe) : recipe.metalUnits();
    }

    /** The first registered gate's reason to refuse this recipe, if any. Only the server can ask. */
    public static Optional<Component> denial(Player player, SmithingRecipe recipe) {
        return player instanceof ServerPlayer smith ? ImmersiveSmithingAPI.recipeDenial(smith, recipe) : Optional.empty();
    }

    /** A short reason why no recipe can be offered. */
    public static Component whyNone(Player player, SmithsForgeBlockEntity forge, int protectedSlot) {
        Component family = forge.familyData().map(MaterialFamily::displayName).orElse(Component.literal("?"));
        List<SmithingRecipe> all = SmithingData.current().recipesFor(forge.family());
        if (all.isEmpty()) {
            return Component.translatable("message.immersive_smithing.material_unsupported", family);
        }
        int cheapest = all.stream().mapToInt(r -> unitsFor(player, r)).min().orElse(0);
        if (cheapest > forge.moltenUnits()) {
            return Component.translatable("message.immersive_smithing.not_enough_metal", family,
                    MaterialUnits.describe(forge.moltenUnits()), MaterialUnits.describe(cheapest));
        }
        Component denied = null;
        for (SmithingRecipe r : all) {
            if (unitsFor(player, r) > forge.moltenUnits()) continue;
            Optional<AuxiliaryIngredient> missing = AuxInventory.firstMissing(player, r.auxiliary(), protectedSlot);
            if (missing.isPresent()) {
                return Component.translatable("message.immersive_smithing.missing_auxiliary", missing.get().count(),
                        missing.get().displayStack().getHoverName(), r.result().getHoverName());
            }
            // Affordable and in hand, so the only thing keeping it off the list is a gate: say so, with its reason.
            if (denied == null) denied = denial(player, r).orElse(null);
        }
        if (denied != null) return denied;
        return Component.translatable("message.immersive_smithing.no_recipes");
    }

    private ForgeRecipeSelection() {}
}
