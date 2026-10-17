package com.otectus.immersivesmithing.test;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.damagesource.EpicFightDamageSources;

/** Epic Fight types for the GameTests. Only reached after {@code ModList.get().isLoaded("epicfight")}. */
final class EpicFightTestSupport {
    static final String MOD_ID = "epicfight";

    /** Private: callers outside this class must never see Epic Fight types, or verifying them needs Epic Fight. */
    private static ServerPlayerPatch patch(TestSupport.TestPlayer player) {
        ServerPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) throw new IllegalStateException("Epic Fight attached no player patch");
        return patch;
    }

    static void vanillaMode(TestSupport.TestPlayer player) {
        patch(player).toVanillaMode(false);
    }

    /** Switches the player to Epic Fight's battle mode, where attacks run through its own damage path. */
    static void battleMode(TestSupport.TestPlayer player) {
        patch(player).toMode(PlayerPatch.PlayerMode.EPICFIGHT, false);
    }

    /**
     * One battle-mode hit with the given hand through {@code PlayerPatch.attack}, the method Epic Fight's attack
     * animations call: it swaps the hands' attack-damage modifiers for an offhand hit and runs the vanilla attack.
     */
    static void battleAttack(TestSupport.TestPlayer player, LivingEntity target, InteractionHand hand) {
        patch(player).attack(EpicFightDamageSources.playerAttack(player), target, hand);
    }

    static boolean offhandAttacksAllowed(TestSupport.TestPlayer player) {
        return patch(player).isOffhandItemValid();
    }

    /** Epic Fight's weapon category for a stack: forging must never change it. */
    static String category(ItemStack stack) {
        CapabilityItem capability = EpicFightCapabilities.getItemStackCapability(stack);
        return capability == null || capability.isEmpty() ? "none" : String.valueOf(capability.getWeaponCategory());
    }

    private EpicFightTestSupport() {}
}
