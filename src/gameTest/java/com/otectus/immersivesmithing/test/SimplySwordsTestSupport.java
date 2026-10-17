package com.otectus.immersivesmithing.test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.sweenus.simplyswords.api.WeaponImplicitRegistry;
import net.sweenus.simplyswords.entity.ThrownSpearEntity;
import net.sweenus.simplyswords.power.GemPowerComponent;
import net.sweenus.simplyswords.registry.ComponentTypeRegistry;
import net.sweenus.simplyswords.util.HelperMethods;

import java.util.List;

/** Simply Swords types for the GameTests. Only reached after {@code ModList.get().isLoaded("simplyswords")}. */
final class SimplySwordsTestSupport {
    static final String MOD_ID = "simplyswords";

    /** Gives a runic weapon a rolled runic power, as clicking it in an inventory would. */
    static void giveRunicPower(ItemStack stack, ResourceLocation power) {
        ComponentTypeRegistry.GEM_POWER.set(stack, GemPowerComponent.runic(power));
    }

    static boolean hasGemPower(ItemStack stack) {
        GemPowerComponent component = ComponentTypeRegistry.GEM_POWER.get(stack);
        return component != null && !component.isEmpty();
    }

    /**
     * Throws the spear in the given hand the way Simply Swords does on use, and returns the spear entity it spawned.
     * Signatures here use vanilla types only: the test classes are verified without Simply Swords installed.
     */
    static Entity throwSpear(Level level, TestSupport.TestPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        held.getItem().use(level, player, hand);
        List<ThrownSpearEntity> spears = level.getEntitiesOfClass(ThrownSpearEntity.class, player.getBoundingBox().inflate(4));
        if (spears.isEmpty()) throw new IllegalStateException("no spear was thrown");
        return spears.get(spears.size() - 1);
    }

    static float launchDamage(Entity spear) {
        return ((ThrownSpearEntity) spear).primaryBaseDamage;
    }

    /** Runs an attack with Simply Swords' per-weapon implicits (random double damage and the like) held off. */
    static void withoutImplicits(Runnable attack) {
        WeaponImplicitRegistry.runSuppressed(attack);
    }

    /** The damage of an attack-scaled weapon ability, the way Simply Swords computes it for its uniques. */
    static float abilityDamage(LivingEntity actor, float attackScaling) {
        return HelperMethods.abilityScaledDamage("physical", actor, attackScaling, 0F);
    }

    private SimplySwordsTestSupport() {}
}
