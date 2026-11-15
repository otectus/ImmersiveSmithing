package com.otectus.immersivesmithing.mixin.client;

import com.otectus.immersivesmithing.client.work.FocusController;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Places the camera for a station work view after vanilla has set it up. Presentation only: the player entity, its
 * collision and every server check are untouched. Optional ({@code require = 0}): if a camera mod prevents it, focus
 * notices and falls back to World view.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void immersive_smithing$workView(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        FocusController.hookAlive();
        FocusController.Pose pose = FocusController.cameraPose(partialTick);
        if (pose == null) return;
        setRotation(pose.yaw(), pose.pitch());
        setPosition(pose.position());
    }
}
