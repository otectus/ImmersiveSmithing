package com.otectus.immersivesmithing.item;

import com.otectus.immersivesmithing.client.TongsClientExtensions;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import com.otectus.immersivesmithing.workpiece.HeldWorkpieces;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Commits, turns and draws billets at the forge and carries workpieces between forge, anvil, trough and the reheat rest.
 * A carried piece cools while the tongs are in a loaded inventory ({@link HeldWorkpieces}).
 */
public class SmithingTongsItem extends SmithingToolItem {

    public SmithingTongsItem(SmithingTier tier, Properties properties) {
        super(tier, properties);
    }

    /** Tongs stab instead of swinging (first and third person). Only called on the client. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(TongsClientExtensions.INSTANCE);
    }

    /** Using empty tongs in the air picks up a dropped Hot Workpiece from the inventory. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tongs = player.getItemInHand(hand);
        if (WorkpieceCodec.isHolding(tongs)) return InteractionResultHolder.pass(tongs);
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack candidate = inv.getItem(slot);
            if (!candidate.is(ModItems.HOT_WORKPIECE.get())) continue;
            Optional<WorkpieceData> data = WorkpieceCodec.getHeld(candidate);
            if (data.isEmpty()) continue;
            if (!level.isClientSide) {
                WorkpieceCodec.setHeld(tongs, data.get());
                candidate.shrink(1);
                level.playSound(null, player.blockPosition(), ModSounds.TONGS_GRAB.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            return InteractionResultHolder.sidedSuccess(tongs, level.isClientSide);
        }
        return InteractionResultHolder.pass(tongs);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Optional<WorkpieceData> data = WorkpieceCodec.getHeld(stack);
        if (data.isPresent()) {
            appendWorkpiece(data.get(), tooltip);
        } else if (WorkpieceCodec.isHolding(stack)) {
            tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.unreadable").withStyle(ChatFormatting.RED));
        } else {
            tooltip.add(Component.translatable("tooltip.immersive_smithing.tongs.empty").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void appendWorkpiece(WorkpieceData data, List<Component> tooltip) {
        HeldWorkpieces.appendTooltip(data, tooltip);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        HeldWorkpieces.tick(stack, level);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        HeldWorkpieces.tick(stack, entity.level());
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }
}
