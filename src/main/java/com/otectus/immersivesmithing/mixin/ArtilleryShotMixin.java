package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.quality.ProjectileEfficacy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.otectus.immersivesmithing.compat.epicknights.ArtilleryShot;

/** Pinned procedural launch boundary: capture the provided weapon before spawning each projectile. */
@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.procedures.ArquebusRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.BronzegunneRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.CarbineRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.ChuKoNuShootProcedure",
        "net.mcreator.artilleryaddon.procedures.DualWheellockCarbineRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.DualWheellockPistolRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.FireLanceRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.HackbutMatchlockRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.HandcannonRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.HarquebusRightclick2Procedure",
        "net.mcreator.artilleryaddon.procedures.MarkmenRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.MatchlockPistolRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.MatchlockRifleRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.MinipistolaRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.NobleGonneRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.TestgunRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.TillergunRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.ToccolaRightclickedProcedure",
        "net.mcreator.artilleryaddon.procedures.ToradarRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockBreechloadingRilfeRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockHandMortarRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockHuntingRilfeRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockMusketRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockPistolRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockWallgunRightclickProcedure"}, remap = false)
public abstract class ArtilleryShotMixin {
    @Inject(method = "execute", at = @At("HEAD"))
    private static void immersiveSmithing$begin(LevelAccessor world, double x, double y, double z,
            Entity shooter, ItemStack weapon, CallbackInfo ci) { ArtilleryShot.begin(weapon); }

    @Inject(method = "execute", at = @At("RETURN"))
    private static void immersiveSmithing$end(LevelAccessor world, double x, double y, double z,
            Entity shooter, ItemStack weapon, CallbackInfo ci) { ArtilleryShot.end(); }

    @Redirect(method = {"execute", "lambda$execute$0", "lambda$execute$1", "lambda$execute$2", "lambda$execute$3", "lambda$execute$4", "lambda$execute$5"}, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean immersiveSmithing$snapshot(Level receiver, Entity projectile) {
        ProjectileEfficacy.epicSnapshot(projectile, ArtilleryShot.current());
        return receiver.addFreshEntity(projectile);
    }
}
