package com.otectus.immersivesmithing.material;

import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.recipe.SmithingData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Per-piece metal accounting, independent of quality: a discounted forging cannot return its undiscounted cost. */
public final class RecyclingData {
    private static final String FAMILY = "RecyclingFamily";
    private static final String UNITS = "RecyclingUnits";

    public static void record(ItemStack stack, ResourceLocation family, int units) {
        CompoundTag root = stack.getOrCreateTagElement(QualityData.ROOT);
        root.putString(FAMILY, family.toString());
        root.putInt(UNITS, Math.max(0, units));
    }

    private static boolean hasRecord(ItemStack stack) {
        CompoundTag root = stack.getTagElement(QualityData.ROOT);
        return root != null && (root.contains(FAMILY) || root.contains(UNITS));
    }

    /** Old/found pieces retain their normal value. A recorded cap can only reduce it, before efficiency is applied. */
    static Optional<RecyclingValue> limit(ItemStack stack, RecyclingValue value) {
        if (!hasRecord(stack)) return Optional.of(value);
        CompoundTag root = stack.getTagElement(QualityData.ROOT);
        // Unknown or changed composition is not permission to refund a different metal.
        if (!value.family().toString().equals(root.getString(FAMILY))) return Optional.empty();
        int units = Math.min(value.units(), Math.max(0, root.getInt(UNITS)));
        return Optional.of(new RecyclingValue(value.family(), units, value.rule()));
    }

    /**
     * A crafting repair/remake must not wash off a discount, even with quality inheritance disabled or two graded
     * inputs. Count one consumed item per occupied crafting slot, including added raw stock of the result's metal;
     * never refund more than either the result's usual value or the known metal in those inputs. Native smithing
     * upgrades already copy the base's whole tag. A new forging replaces this accounting at the quench.
     */
    public static void inherit(ItemStack result, Container inputs) {
        if (result.isEmpty()) return;
        boolean recorded = false;
        for (int i = 0; i < inputs.getContainerSize(); i++) recorded |= hasRecord(inputs.getItem(i));
        if (!recorded) return;
        Optional<RecyclingValue> normal = MaterialResolver.baseRecyclingValue(result);
        if (normal.isEmpty()) return;
        RecyclingValue value = normal.get();
        long total = 0;
        for (int i = 0; i < inputs.getContainerSize(); i++) {
            ItemStack input = inputs.getItem(i);
            if (input.isEmpty()) continue;
            MaterialRegistry.Entry stock = SmithingData.current().materials().lookup(input.getItem());
            if (stock != null) {
                if (stock.family().equals(value.family())) total += stock.units();
            } else {
                RecyclingValue piece = MaterialResolver.recyclingValue(input).orElse(null);
                if (piece != null && piece.family().equals(value.family())) total += piece.units();
            }
        }
        int cap = (int) Math.min(value.units(), total / result.getCount());
        record(result, value.family(), cap);
    }

    private RecyclingData() {}
}
