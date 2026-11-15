package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.VisorSwap;
import net.minecraftforge.network.NetworkEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.ice_the_noob.quick_swap.network.SwapPacket", remap = false)
public abstract class QuickSwapPacketMixin {
    @Shadow @Final private int playerID;
    @Shadow @Final private String target;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void immersive_smithing$validateSwap(Supplier<NetworkEvent.Context> supplier, CallbackInfo ci) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> VisorSwap.apply(context.getSender(), playerID, target));
        context.setPacketHandled(true);
        ci.cancel();
    }
}
