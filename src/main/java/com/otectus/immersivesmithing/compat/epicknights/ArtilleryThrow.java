package com.otectus.immersivesmithing.compat.epicknights;

import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Scope of the pinned item's native use call. No inventory search, client-supplied value, or new ammo ownership. */
public final class ArtilleryThrow {
    public static final String STACK = "immersive_smithing:artillery_thrown_stack";
    private record Use(LivingEntity owner, ItemStack stack) {}
    private static final ThreadLocal<Use> CURRENT = new ThreadLocal<>();
    public static void begin(LivingEntity owner, ItemStack stack) { CURRENT.set(new Use(owner, stack)); }
    public static void end() { CURRENT.remove(); }
    public static ItemStack actualHand(LivingEntity owner) {
        Use use = CURRENT.get();
        return use != null && use.owner() == owner ? use.stack() : owner.getMainHandItem();
    }
    public static void snapshot(Entity projectile) {
        Use use = CURRENT.get();
        if (use == null || projectile.level().isClientSide()) return;
        projectile.getPersistentData().put(STACK, use.stack().copyWithCount(1).save(new net.minecraft.nbt.CompoundTag()));
        ProjectileEfficacy.epicSnapshot(projectile, use.stack());
    }
    public static ItemStack stored(Entity projectile) {
        return ItemStack.of(projectile.getPersistentData().getCompound(STACK));
    }
    private ArtilleryThrow() {}
}
