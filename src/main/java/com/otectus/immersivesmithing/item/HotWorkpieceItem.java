package com.otectus.immersivesmithing.item;

import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A workpiece left behind when its station was broken (anvil, trough, forge rest), with its whole process record (or, if it
 * no longer decodes, its raw data untouched). It cannot be used as equipment; pick it up with Smithing Tongs, place it on an
 * anvil, or deliberately recycle it at a forge.
 */
public class HotWorkpieceItem extends Item {

    public HotWorkpieceItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        return WorkpieceCodec.getHeld(stack)
                .<Component>map(d -> Component.translatable("item.immersive_smithing.hot_workpiece.of", d.target().getHoverName()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        var data = WorkpieceCodec.getHeld(stack);
        if (data.isPresent()) SmithingTongsItem.appendWorkpiece(data.get(), tooltip);
        else if (WorkpieceCodec.isHolding(stack)) tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.unreadable").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.immersive_smithing.hot_workpiece").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        com.otectus.immersivesmithing.workpiece.HeldWorkpieces.tick(stack, level);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, net.minecraft.world.entity.item.ItemEntity entity) {
        com.otectus.immersivesmithing.workpiece.HeldWorkpieces.tick(stack, entity.level());
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
