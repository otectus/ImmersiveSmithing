package com.otectus.immersivesmithing.quality;

import com.otectus.immersivesmithing.ImmersiveSmithing;
import com.otectus.immersivesmithing.api.ISmithingEfficacyHandler;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.compat.spartanweaponry.SpartanCompat;
import com.otectus.immersivesmithing.registry.ModTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Carries smithing efficacy to projectiles. A thrown Spartan Weaponry weapon uses its own quality; arrows and
 * bolts use the quality of the bow or crossbow that fired them. Base damage is scaled once, when the projectile
 * first enters a level; a persistent-data flag stops a second scaling after a dimension change. Projectiles in
 * {@code #immersive_smithing:weapon_scaled_projectiles} (Simply Swords' thrown spears and swords) already read their
 * damage from the thrown weapon's quality-adjusted attack damage at launch and are never scaled again.
 */
@Mod.EventBusSubscriber(modid = ImmersiveSmithing.MOD_ID)
public final class ProjectileEfficacy {
    private static final String SCALED = ImmersiveSmithing.MOD_ID + ":efficacy_scaled";
    private static final String LAUNCH_EFFICACY = ImmersiveSmithing.MOD_ID + ":launch_efficacy";

    private ProjectileEfficacy() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || arrow.getPersistentData().getBoolean(SCALED)) return;
        if (arrow.getType().is(ModTags.WEAPON_SCALED_PROJECTILES)) return;
        if (arrow instanceof com.otectus.immersivesmithing.compat.epicknights.NativeProjectile.ThrownStack thrown) {
            epicSnapshot(arrow, thrown.immersive_smithing$thrownStack());
        }
        double efficacy = arrow.getPersistentData().contains(LAUNCH_EFFICACY)
                ? arrow.getPersistentData().getDouble(LAUNCH_EFFICACY) : efficacy(sourceOf(arrow));
        if (!Double.isFinite(efficacy) || efficacy <= 0) efficacy = 1;
        if (!(arrow instanceof com.otectus.immersivesmithing.compat.epicknights.NativeProjectile.DirectDamage))
            arrow.setBaseDamage(arrow.getBaseDamage() * efficacy);
        arrow.getPersistentData().putBoolean(SCALED, true);
    }

    /** The stack whose quality applies to the projectile, or empty. */
    static ItemStack sourceOf(AbstractArrow arrow) {
        if (ModList.get().isLoaded(SpartanCompat.MOD_ID)) {
            ItemStack thrown = SpartanCompat.thrownWeapon(arrow);
            if (!thrown.isEmpty()) return thrown;
        }
        return ItemStack.EMPTY;
    }

    /** Called with the actual firing stack before insertion into the world; both hands and NPCs are unambiguous. */
    public static void snapshot(Entity projectile, ItemStack stack) {
        if (!projectile.level().isClientSide()) projectile.getPersistentData().putDouble(LAUNCH_EFFICACY, efficacy(stack));
    }

    public static void epicSnapshot(Entity projectile, ItemStack stack) {
        if (com.otectus.immersivesmithing.config.ServerConfig.get(com.otectus.immersivesmithing.config.ServerConfig.EPIC_KNIGHTS_RANGED_EFFICACY)) snapshot(projectile, stack);
    }

    /** Immutable, persisted value for optional native damage handlers; never consults a shooter's current hands. */
    public static double launchMultiplier(Entity projectile) {
        double multiplier = projectile.getPersistentData().contains(LAUNCH_EFFICACY)
                ? projectile.getPersistentData().getDouble(LAUNCH_EFFICACY) : 1;
        return Double.isFinite(multiplier) && multiplier > 0 ? multiplier : 1;
    }

    /** Damage multiplier for a projectile fired or thrown from this stack; 1.0 when quality does not apply. */
    public static double efficacy(ItemStack stack) {
        Optional<QualityData> data = QualityData.get(stack);
        if (data.isEmpty() || ImmersiveSmithingAPI.isExcluded(stack) || com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.owns(stack)) return 1.0;
        for (ISmithingEfficacyHandler handler : ImmersiveSmithingAPI.efficacyHandlers()) {
            if (handler.handles(stack)) return 1.0;
        }
        return QualityCalculator.efficacyMultiplier(data.get());
    }
}
