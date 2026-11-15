package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.minigame.EquipmentClassifier;
import com.otectus.immersivesmithing.recipe.AuxiliaryIngredient;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Which process a recipe uses, in this order: (1) its own valid {@code process_profile}; (2) the profile mapped to its anvil
 * pattern; (3) the profile mapped to the pattern the equipment classifier picks for its result; (4) the generic profile.
 * Also resolves the heat bands (a material's {@code thermal} block over the profile's {@code heat} over the defaults), the
 * size scale of the work and the fitting sockets.
 */
public final class ProcessResolver {

    public enum Source { EXPLICIT, PATTERN, CLASSIFIER, FALLBACK, UNKNOWN_EXPLICIT }

    public record Resolution(ProcessProfile profile, Source source) {}

    private static volatile Map<ResourceLocation, ThermalProfile> materialThermals = Map.of();

    public static ProcessProfile resolve(SmithingRecipe recipe) {
        return explain(recipe).profile();
    }

    public static Resolution explain(SmithingRecipe recipe) {
        ResourceLocation explicit = recipe.processProfile();
        boolean unknown = false;
        if (explicit != null) {
            ProcessProfile p = ProcessProfiles.find(explicit);
            if (p != null) return new Resolution(p, Source.EXPLICIT);
            unknown = true;
        }
        ProcessProfile byPattern = ProcessProfiles.forPattern(recipe.anvilPattern());
        if (byPattern != null) return new Resolution(byPattern, unknown ? Source.UNKNOWN_EXPLICIT : Source.PATTERN);
        ProcessProfile byClass = ProcessProfiles.forPattern(EquipmentClassifier.classify(recipe.result()));
        if (byClass != null) return new Resolution(byClass, unknown ? Source.UNKNOWN_EXPLICIT : Source.CLASSIFIER);
        return new Resolution(ProcessProfiles.generic(), unknown ? Source.UNKNOWN_EXPLICIT : Source.FALLBACK);
    }

    /** The profile for a legacy piece that only knows its pattern and target. */
    public static ProcessProfile forLegacy(@Nullable ResourceLocation pattern, ItemStack target) {
        ProcessProfile p = ProcessProfiles.forPattern(pattern);
        if (p == null && !target.isEmpty()) p = ProcessProfiles.forPattern(EquipmentClassifier.classify(target));
        return p != null ? p : ProcessProfiles.generic();
    }

    public static ThermalProfile thermal(@Nullable ResourceLocation family, ProcessProfile profile) {
        ThermalProfile material = family == null ? null : materialThermals.get(family);
        if (material != null) return material;
        return profile.heat() != null ? profile.heat() : ThermalProfile.DEFAULT;
    }

    /** Material {@code thermal} blocks from the last material reload; installed by the material loader. */
    public static void installMaterialThermals(Map<ResourceLocation, ThermalProfile> thermals) {
        materialThermals = Collections.unmodifiableMap(new java.util.HashMap<>(thermals));
    }

    public static Map<ResourceLocation, ThermalProfile> materialThermals() {
        return materialThermals;
    }

    /** Work grows with the square root of the metal, from 70% for a dagger to 160% for the largest armour. */
    public static int workScale(int metalUnits) {
        double scale = Math.sqrt(Math.max(1, metalUnits) / 18.0);
        return (int) Math.round(1000 * Math.max(0.7, Math.min(1.6, scale)));
    }

    /**
     * The fitting sockets of a committed job: one per non-equipment auxiliary in recipe order, holding exactly the reserved
     * stacks bound to it (NBT included). Consumed equipment is assimilated into the piece and never fitted.
     */
    public static List<ProcessState.Socket> sockets(SmithingRecipe recipe, List<ItemStack> escrow, ProcessProfile profile) {
        int[] left = escrow.stream().mapToInt(ItemStack::getCount).toArray();
        List<ProcessState.Socket> out = new ArrayList<>();
        List<AuxiliaryIngredient> roles = recipe.auxiliary();
        for (int r = 0; r < roles.size(); r++) {
            AuxiliaryIngredient role = roles.get(r);
            int need = role.count();
            List<ItemStack> bound = new ArrayList<>();
            for (int i = 0; i < escrow.size() && need > 0; i++) {
                ItemStack stack = escrow.get(i);
                if (left[i] <= 0 || !role.ingredient().test(stack)) continue;
                int amount = Math.min(left[i], need);
                left[i] -= amount;
                need -= amount;
                if (!role.consumesEquipment()) bound.add(stack.copyWithCount(amount));
            }
            if (role.consumesEquipment() || bound.isEmpty()) continue;
            String name = r < profile.sockets().size() ? profile.sockets().get(r) : socketFor(bound.get(0));
            if (bound.size() > ProcessState.MAX_STACKS_PER_SOCKET) {
                // Never happens with real inventories; merge the tail rather than lose items.
                List<ItemStack> merged = new ArrayList<>(bound.subList(0, ProcessState.MAX_STACKS_PER_SOCKET - 1));
                for (ItemStack extra : bound.subList(ProcessState.MAX_STACKS_PER_SOCKET - 1, bound.size())) {
                    ItemStack last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
                    if (last != null && ItemStack.isSameItemSameTags(last, extra)) last.grow(extra.getCount());
                    else merged.add(extra);
                }
                bound = merged.subList(0, Math.min(merged.size(), ProcessState.MAX_STACKS_PER_SOCKET));
            }
            if (out.size() < ProcessProfile.MAX_SOCKETS) out.add(new ProcessState.Socket(name, List.copyOf(bound), false));
        }
        return out;
    }

    /** A readable socket name for an automatically detected component. */
    public static String socketFor(ItemStack stack) {
        if (stack.is(Tags.Items.RODS_WOODEN)) return "haft";
        if (stack.is(ItemTags.PLANKS) || stack.is(ItemTags.LOGS)) return "backing";
        if (stack.is(Tags.Items.LEATHER)) return "strap";
        if (stack.is(Tags.Items.STRING)) return "string";
        if (stack.is(Tags.Items.GEMS)) return "inlay";
        if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.NUGGETS)) return "fitting";
        return "component";
    }

    private ProcessResolver() {}
}
