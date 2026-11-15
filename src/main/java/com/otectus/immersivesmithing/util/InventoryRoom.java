package com.otectus.immersivesmithing.util;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** How much of an item a player's inventory takes, for deliveries that must not drop anything on the ground. */
public final class InventoryRoom {

    /**
     * How many of {@code item} the 36 main slots take, counted as {@link Inventory#add} fills them: an empty slot holds a
     * full stack, a stack of the same item and data its remaining space. The armour, off-hand and accessory slots are
     * left out on purpose, so the answer never exceeds what really fits.
     */
    public static int room(Inventory inventory, ItemStack item) {
        if (item.isEmpty()) return 0;
        int limit = Math.min(item.getMaxStackSize(), inventory.getMaxStackSize());
        long room = 0;
        for (ItemStack stack : inventory.items) {
            if (stack.isEmpty()) room += limit;
            else if (stack.isStackable() && ItemStack.isSameItemSameTags(stack, item)) room += Math.max(0, Math.min(stack.getMaxStackSize(), limit) - stack.getCount());
        }
        return (int) Math.min(Integer.MAX_VALUE, room);
    }

    private InventoryRoom() {}
}
