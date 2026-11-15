package com.otectus.immersivesmithing.workpiece;

import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.ThermalRules;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A workpiece carried in tongs or lying as a Hot Workpiece item. It cools in the air only while its holder is simulated
 * (in a loaded player's inventory, or an item entity in a loaded chunk): the payload is updated every {@link #INTERVAL}
 * ticks, never for time spent offline or unloaded. Older payloads are rewritten in the current schema the first time
 * their holder ticks on the server, which is the one migration point for items.
 */
public final class HeldWorkpieces {
    public static final int INTERVAL = 10;

    public static void tick(ItemStack holder, Level level) {
        if (level.isClientSide || (level.getGameTime() % INTERVAL) != 0 || !WorkpieceCodec.isHolding(holder)) return;
        WorkpieceData piece = WorkpieceCodec.getHeld(holder).orElse(null);
        if (piece == null) return; // unreadable: the raw payload stays exactly as it is
        boolean upgrade = WorkpieceCodec.needsUpgrade(holder);
        ProcessState p = piece.process();
        boolean warm = false;
        for (int h : p.heat) warm |= h > 0;
        if (!warm && !upgrade) return;
        ProcessState next = p.copy();
        next.heatTick = ProcessState.ITEM_HELD;
        next.heat = ThermalRules.cool(next.heat, INTERVAL, next.thermal, 1.0);
        WorkpieceCodec.setHeld(holder, piece.withProcess(next));
    }

    public static void appendTooltip(WorkpieceData data, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.holding", data.target().getHoverName()).withStyle(ChatFormatting.GOLD));
        ProcessState p = data.process();
        tooltip.add(Component.translatable("stage.immersive_smithing." + p.stage.key()).withStyle(ChatFormatting.GRAY));
        if (p.stage.shapeable() || p.stage == com.otectus.immersivesmithing.work.WorkStage.QUENCHING) {
            ThermalProfile.HeatBand band = p.thermal.band(ThermalRules.coolest(p.heat));
            tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.heat",
                    Component.translatable("heat.immersive_smithing." + band.key())).withStyle(ChatFormatting.GRAY));
        }
        if (p.stage.shapeable() && !p.allComplete()) {
            tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.progress", p.progress() / 10).withStyle(ChatFormatting.GRAY));
        }
        if (p.needsFitting()) {
            tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.components", p.socketsLeft()).withStyle(ChatFormatting.GRAY));
        }
        if (data.faulty() || p.burnt) tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.faulty").withStyle(ChatFormatting.DARK_RED));
        if (p.has(ProcessState.MIGRATED)) tooltip.add(Component.translatable("tooltip.immersive_smithing.workpiece.legacy").withStyle(ChatFormatting.DARK_GRAY));
    }

    private HeldWorkpieces() {}
}
