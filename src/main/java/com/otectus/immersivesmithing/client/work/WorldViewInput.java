package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.client.StationNotices;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingHammerItem;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ShapingRules;
import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.work.WorkAction;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Station input without a work view. A physical input is resolved exactly once, before anything else happens: either it
 * becomes one station intent (and vanilla's attack or use is cancelled, so a hammer blow never also breaks the anvil or
 * hits a bystander), or it is left entirely to vanilla block use, which reaches the same server actions. In Cinematic and
 * Shoulder modes, using a station where hands-on work waits asks for the focused view instead.
 */
public final class WorldViewInput {

    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (FocusController.active()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        WorkLeases.Station kind = SmithingActionService.stationOf(be);
        if (kind == null) return;
        Direction facing = WorkTargets.facing(mc.level, pos);
        Vec3 local = StationLayout.toLocal(pos, facing, hit.getLocation());
        if (event.isAttack()) {
            if (be instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null) {
                // A piece is on the anvil: a left click works it (with a hammer) or only explains; it never breaks the anvil.
                event.setCanceled(true);
                if (hand(player, SmithingHammerItem.class) != null && anvil.workpiece().stage().shapeable()) {
                    strike(mc, player, pos, facing, anvil, event);
                } else {
                    event.setSwingHand(false);
                    StationNotices.show(Component.translatable("message.immersive_smithing.use_hammer"), false);
                }
            } else if (be instanceof SmithsForgeBlockEntity && StationLayout.forgePart(local) == StationLayout.ForgePart.BELLOWS) {
                event.setCanceled(true);
                event.setSwingHand(true);
                WorkClient.send(pos, WorkAction.PUMP);
            }
            return;
        }
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (FocusController.mode() != ClientConfig.CameraMode.WORLD && focusWanted(player, be, local)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            FocusController.request(pos);
            return;
        }
        if (be instanceof SmithsGrindstoneBlockEntity stone && !stone.piece().isEmpty() && player.getMainHandItem().isEmpty() && !player.isShiftKeyDown()) {
            // Holding use keeps the edge on the wheel: vanilla repeats this event while the button is held.
            event.setCanceled(true);
            event.setSwingHand(false);
            WorkClient.send(pos, WorkAction.GRIND_HOLD);
        }
    }

    private static void strike(Minecraft mc, LocalPlayer player, BlockPos pos, Direction facing, SmithsAnvilBlockEntity anvil,
                               InputEvent.InteractionKeyMappingTriggered event) {
        WorkpieceData piece = anvil.workpiece();
        ProcessState p = piece.process();
        Vec3 eye = player.getEyePosition();
        WorkTargets.Target target = WorkTargets.pick(mc.level, pos, WorkLeases.Station.ANVIL, eye, player.getViewVector(1F));
        float u, v;
        int op;
        if (target != null && target.kind() == WorkTargets.Kind.OPERATION) {
            u = target.u();
            v = target.v();
            op = target.index();
        } else {
            // Aimed off the piece: the next operation (the server explains if it cannot be worked).
            op = ShapingRules.nextOperation(p);
            if (op < 0) {
                event.setSwingHand(false);
                return;
            }
            u = p.profile.operations().get(op).centerU();
            v = p.profile.operations().get(op).centerV();
        }
        int flags = WorkKeys.held(WorkKeys.HEAVY) ? WorkAction.HEAVY : 0;
        WorkClient.send(pos, WorkAction.STRIKE, op, u, v, flags);
        event.setSwingHand(true);
    }

    /** Whether a right click should open the focused view here: hands-on work is waiting for the tool in hand. */
    public static boolean focusWanted(LocalPlayer player, BlockEntity be, Vec3 local) {
        ItemStack main = player.getMainHandItem();
        if (player.isShiftKeyDown()) return false;
        if (be instanceof SmithsAnvilBlockEntity anvil) {
            WorkpieceData piece = anvil.workpiece();
            if (piece == null || anvil.hasOutput()) return false;
            if (piece.stage().shapeable() && hand(player, SmithingHammerItem.class) != null) return true;
            return piece.stage().quenched() && piece.process().needsFitting() && main.isEmpty();
        }
        if (be instanceof SmithsForgeBlockEntity forge) {
            if (forge.job() == null) return false;
            return main.getItem() instanceof SmithingTongsItem && !WorkpieceCodec.isHolding(main)
                    && StationLayout.forgePart(local) != StationLayout.ForgePart.BOOK;
        }
        if (be instanceof SmithsTroughBlockEntity trough) {
            if (!(main.getItem() instanceof SmithingTongsItem)) return false;
            if (trough.occupied()) return !WorkpieceCodec.isHolding(main);
            WorkpieceData held = WorkpieceCodec.getHeld(main).orElse(null);
            return held != null && (held.stage() == WorkStage.QUENCHING || (held.stage().shapeable() && held.process().allComplete()));
        }
        if (be instanceof SmithsGrindstoneBlockEntity stone) {
            return !stone.piece().isEmpty() && main.isEmpty();
        }
        return false;
    }

    /** Keys outside any screen: the focus key, turning a piece over, the grindstone aspect, and the camera mode. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null) return;
        BlockPos looked = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        BlockEntity be = looked != null ? mc.level.getBlockEntity(looked) : null;
        while (WorkKeys.CAMERA.consumeClick()) {
            ClientConfig.CameraMode next = ClientConfig.CameraMode.values()[(FocusController.mode().ordinal() + 1) % ClientConfig.CameraMode.values().length];
            ClientConfig.CAMERA_MODE.set(next);
            ClientConfig.SPEC.save();
            StationNotices.show(Component.translatable("message.immersive_smithing.camera_mode", Component.translatable("camera.immersive_smithing." + next.name().toLowerCase(java.util.Locale.ROOT))), false);
        }
        while (WorkKeys.FOCUS.consumeClick()) {
            if (be == null || SmithingActionService.stationOf(be) == null) continue;
            if (FocusController.mode() == ClientConfig.CameraMode.WORLD) {
                StationNotices.show(Component.translatable("message.immersive_smithing.world_view_selected", WorkKeys.name(WorkKeys.CAMERA)), false);
            } else {
                FocusController.request(looked);
            }
        }
        while (WorkKeys.FLIP.consumeClick()) {
            if (be instanceof SmithsAnvilBlockEntity anvil && anvil.workpiece() != null) WorkClient.send(looked, WorkAction.FLIP);
        }
        while (WorkKeys.NEXT.consumeClick()) {
            if (be instanceof SmithsGrindstoneBlockEntity stone && !stone.piece().isEmpty()) {
                WorkClient.send(looked, WorkAction.GRIND_ASPECT, stone.efficacy() ? 0 : 1, 0F, 0F, 0);
            }
        }
        while (WorkKeys.PREVIOUS.consumeClick()) {
            if (be instanceof SmithsGrindstoneBlockEntity stone && !stone.piece().isEmpty()) {
                WorkClient.send(looked, WorkAction.GRIND_ASPECT, stone.efficacy() ? 0 : 1, 0F, 0F, 0);
            }
        }
    }

    /** The station under the crosshair, for the World-view status readout. */
    @Nullable
    public static BlockPos lookedAtStation() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return SmithingActionService.stationOf(mc.level.getBlockEntity(hit.getBlockPos())) != null ? hit.getBlockPos() : null;
    }

    @Nullable
    private static InteractionHand hand(LocalPlayer player, Class<?> type) {
        for (InteractionHand hand : InteractionHand.values()) if (type.isInstance(player.getItemInHand(hand).getItem())) return hand;
        return null;
    }

    private WorldViewInput() {}
}
