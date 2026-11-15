package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.ArtilleryShot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.procedures.DualWheellockCarbineRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.DualWheellockPistolRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.FireLanceRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockBreechloadingRilfeRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockHandMortarRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockHuntingRilfeRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockMusketRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockPistolRightclickProcedure",
        "net.mcreator.artilleryaddon.procedures.WheellockWallgunRightclickProcedure"}, remap = false)
public abstract class ArtilleryDelayedShotMixin {
    @ModifyArg(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/mcreator/artilleryaddon/ArtilleryAddonMod;queueServerWork(ILjava/lang/Runnable;)V"), index = 1)
    private static Runnable immersiveSmithing$capture(Runnable shot) { return ArtilleryShot.capture(shot); }
}
