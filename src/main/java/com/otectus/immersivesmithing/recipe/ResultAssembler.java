package com.otectus.immersivesmithing.recipe;

import com.otectus.immersivesmithing.quality.QualityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Builds the target of a forging from the recipe and the components actually held in escrow, before anything is
 * consumed. A recipe that reworks a finished piece ({@code consume_equipment}) hands that piece's data to the result the
 * way its native recipe does (a smithing-table upgrade, Botania's armor upgrade): enchantments, name, lore, damage,
 * custom model data and other mods' state carry over, while the old smithing quality and maker's mark are dropped
 * because the new forging writes its own at the quench. Where the native recipe makes a fresh piece
 * ({@link SmithingRecipe#keepsBaseData()} false), or when several pieces are reworked at once, the result starts from
 * the recipe's own output. The recipe's shared output stack is never modified.
 */
public final class ResultAssembler {

    public static Optional<ItemStack> assemble(SmithingRecipe recipe, List<ItemStack> escrow) {
        ItemStack result = recipe.result().copy();
        if (result.isEmpty()) return Optional.empty();
        result.setCount(1);
        ItemStack piece = recipe.keepsBaseData() ? consumedPiece(recipe, escrow) : null;
        if (piece != null && piece.getTag() != null) {
            CompoundTag merged = piece.getTag().copy();
            merged.remove(QualityData.ROOT);
            // The recipe's own result data wins, except the damage every damageable stack carries (Damage:0), which
            // would otherwise wipe the piece's wear.
            CompoundTag own = recipe.result().getTag();
            if (own != null) {
                for (String key : own.getAllKeys()) {
                    if (!key.equals(ItemStack.TAG_DAMAGE)) merged.put(key, own.get(key).copy());
                }
            }
            result.setTag(merged.isEmpty() ? null : merged);
            // A heavier piece reworked into a lighter one is never handed over broken or past its durability.
            if (result.isDamageableItem() && result.getDamageValue() >= result.getMaxDamage()) {
                result.setDamageValue(Math.max(0, result.getMaxDamage() - 1));
            }
        }
        if (result.isEmpty() || result.getItem() != recipe.resultItem()) return Optional.empty();
        return Optional.of(result);
    }

    /** The one escrowed piece a {@code consume_equipment} auxiliary took, or null when there is none or several. */
    @Nullable
    static ItemStack consumedPiece(SmithingRecipe recipe, List<ItemStack> escrow) {
        ItemStack found = null;
        for (AuxiliaryIngredient aux : recipe.auxiliary()) {
            if (!aux.consumesEquipment()) continue;
            for (ItemStack stack : escrow) {
                if (stack.isEmpty() || !aux.ingredient().test(stack)) continue;
                if (found != null || stack.getCount() != 1) return null;
                found = stack;
            }
        }
        return found;
    }

    private ResultAssembler() {}
}
