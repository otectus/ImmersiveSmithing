package com.otectus.immersivesmithing.mixin;

import com.otectus.immersivesmithing.compat.epicknights.ArtilleryThrow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {"net.mcreator.artilleryaddon.entity.JavelinProectileEntity", "net.mcreator.artilleryaddon.entity.ThrowingCrossProEntity"}, remap = false)
public abstract class ArtilleryThrownEntityMixin implements IEntityAdditionalSpawnData {
    @Inject(method = {"getPickupItem", "m_7941_"}, at = @At("HEAD"), cancellable = true)
    private void immersive_smithing$recoverStack(CallbackInfoReturnable<ItemStack> ci) {
        ItemStack stored = ArtilleryThrow.stored((Entity)(Object)this);
        if (!stored.isEmpty()) ci.setReturnValue(stored);
    }
    @Override public void writeSpawnData(FriendlyByteBuf buffer) { buffer.writeItem(ArtilleryThrow.stored((Entity)(Object)this)); }
    @Override public void readSpawnData(FriendlyByteBuf buffer) {
        ((Entity)(Object)this).getPersistentData().put(ArtilleryThrow.STACK, buffer.readItem().save(new net.minecraft.nbt.CompoundTag()));
    }
}
