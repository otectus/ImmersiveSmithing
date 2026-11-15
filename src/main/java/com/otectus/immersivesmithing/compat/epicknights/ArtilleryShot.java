package com.otectus.immersivesmithing.compat.epicknights;

import net.minecraft.world.item.ItemStack;
import java.util.ArrayDeque;
import java.util.Deque;

/** A delayed burst retains the firing stack even if its owner changes hands before the shot. */
public final class ArtilleryShot {
    private static final ThreadLocal<Deque<ItemStack>> SHOTS = ThreadLocal.withInitial(ArrayDeque::new);
    public static void begin(ItemStack stack) { SHOTS.get().push(stack.copy()); }
    public static void end() {
        var shots = SHOTS.get();
        if (!shots.isEmpty()) shots.pop();
        if (shots.isEmpty()) SHOTS.remove();
    }
    public static ItemStack current() { return SHOTS.get().isEmpty() ? ItemStack.EMPTY : SHOTS.get().peek(); }
    public static Runnable capture(Runnable nativeShot) {
        ItemStack source = current().copy();
        return () -> {
            begin(source);
            try { nativeShot.run(); } finally { end(); }
        };
    }
    private ArtilleryShot() {}
}
