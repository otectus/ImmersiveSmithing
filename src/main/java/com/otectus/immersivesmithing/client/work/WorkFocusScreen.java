package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.work.WorkAction;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The input layer of a focused work view. It draws no background: the world (and the station) stays in view, unpaused.
 * The pointer aims at parts of the real station; the arrow keys (or the player's bindings) select the same parts from the
 * keyboard, and the action key acts on the selection, so every action has a plain-click and a keyboard way. Holding (the
 * piece in the water, an edge on the wheel) can be switched to toggling in the client config. Escape, a movement key or the
 * focus key leave at once; chat and the inventory open over the view and return to it.
 */
public class WorkFocusScreen extends Screen {
    private final FocusController.Focus focus;
    @Nullable
    private WorkTargets.Target hovered;
    private int selected;
    private boolean pointerMoved;
    @Nullable
    private WorkTargets.Kind holding;
    private int holdTicks;
    private boolean lowered;
    /** The action key is down: a key repeat is not another press. */
    private boolean actDown;

    WorkFocusScreen(FocusController.Focus focus) {
        super(Component.translatable("screen.immersive_smithing.work"));
        this.focus = focus;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.level == null) return;
        WorkTargets.Target shown = current();
        WorkHud.renderFocused(g, font, focus, shown, width, height);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** The part under the pointer, or the keyboard selection when the pointer is off the station. */
    @Nullable
    WorkTargets.Target current() {
        if (minecraft == null || minecraft.level == null) return null;
        if (hovered != null && pointerMoved) return hovered;
        List<WorkTargets.Target> targets = WorkTargets.list(minecraft.level, focus.pos, focus.kind);
        if (targets.isEmpty()) return null;
        selected = Math.floorMod(selected, targets.size());
        return targets.get(selected);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (minecraft == null || minecraft.level == null) return;
        FocusController.Pose pose = FocusController.cameraPose(1F);
        if (pose == null) return;
        double aspect = minecraft.getWindow().getWidth() / (double) Math.max(1, minecraft.getWindow().getHeight());
        Vec3 dir = WorkCamera.ray(pose.yaw(), pose.pitch(), FocusController.lastFov(), aspect, mouseX / Math.max(1, width), mouseY / Math.max(1, height));
        hovered = WorkTargets.pick(minecraft.level, focus.pos, focus.kind, pose.position(), dir);
        pointerMoved = hovered != null;
        if (hovered != null) {
            List<WorkTargets.Target> targets = WorkTargets.list(minecraft.level, focus.pos, focus.kind);
            for (int i = 0; i < targets.size(); i++) if (targets.get(i).same(hovered)) selected = i;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (minecraft == null) return false;
        mouseMoved(mouseX, mouseY);
        if (minecraft.options.keyAttack.matchesMouse(button)) {
            primary(current());
            return true;
        }
        if (minecraft.options.keyUse.matchesMouse(button)) {
            secondary(current());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (minecraft != null && minecraft.options.keyAttack.matchesMouse(button)) release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        cycle(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (minecraft == null || minecraft.player == null) return false;
        var options = minecraft.options;
        if (key == GLFW.GLFW_KEY_ESCAPE || WorkKeys.FOCUS.matches(key, scanCode)) {
            FocusController.exit(null, true);
            return true;
        }
        // Work keys come before movement: the default action key (Space) is also the default jump key.
        if (workKey(key, scanCode)) return true;
        if (options.keyUp.matches(key, scanCode) || options.keyDown.matches(key, scanCode) || options.keyLeft.matches(key, scanCode)
                || options.keyRight.matches(key, scanCode) || options.keyJump.matches(key, scanCode)) {
            // Movement takes the player back: leave, and the held key keeps walking once the screen is gone.
            FocusController.exit(null, true);
            return true;
        }
        if (options.keyChat.matches(key, scanCode)) {
            FocusController.openOverlay(new ChatScreen(""));
            return true;
        }
        if (options.keyCommand.matches(key, scanCode)) {
            FocusController.openOverlay(new ChatScreen("/"));
            return true;
        }
        if (options.keyInventory.matches(key, scanCode)) {
            FocusController.openOverlay(new InventoryScreen(minecraft.player));
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (options.keyHotbarSlots[i].matches(key, scanCode)) {
                minecraft.player.getInventory().selected = i;
                return true;
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    /** Part selection, turning the piece over, the action and the secondary action from the keyboard. */
    private boolean workKey(int key, int scanCode) {
        var options = minecraft.options;
        if (WorkKeys.NEXT.matches(key, scanCode)) {
            cycle(1);
            return true;
        }
        if (WorkKeys.PREVIOUS.matches(key, scanCode)) {
            cycle(-1);
            return true;
        }
        if (WorkKeys.FLIP.matches(key, scanCode) && focus.kind == WorkLeases.Station.ANVIL) {
            WorkClient.send(focus.pos, WorkAction.FLIP);
            return true;
        }
        if (WorkKeys.ACT.matches(key, scanCode) || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || options.keyAttack.matches(key, scanCode)) {
            pointerMoved = false;
            if (!actDown) {
                actDown = true;
                // While holding, a press only acts again in toggle mode (where it is how a hold is ended).
                if (holding == null || !ClientConfig.get(ClientConfig.HOLD_TO_ACT)) primary(current());
            }
            return true;
        }
        if (options.keyUse.matches(key, scanCode)) {
            pointerMoved = false;
            secondary(current());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (minecraft != null && (WorkKeys.ACT.matches(key, scanCode) || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || minecraft.options.keyAttack.matches(key, scanCode))) {
            actDown = false;
            release();
        }
        return super.keyReleased(key, scanCode, modifiers);
    }

    private void cycle(int direction) {
        if (minecraft == null || minecraft.level == null) return;
        List<WorkTargets.Target> targets = WorkTargets.list(minecraft.level, focus.pos, focus.kind);
        if (targets.isEmpty()) return;
        pointerMoved = false;
        selected = Math.floorMod(selected + direction, targets.size());
        if (minecraft.getNarrator().isActive()) minecraft.getNarrator().sayNow(targets.get(selected).label());
    }

    private boolean tongsLoaded() {
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (player == null) return false;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SmithingTongsItem && WorkpieceCodec.isHolding(stack)) return true;
        }
        return false;
    }

    private void primary(@Nullable WorkTargets.Target t) {
        if (t == null || minecraft == null || minecraft.level == null) return;
        boolean hold = ClientConfig.get(ClientConfig.HOLD_TO_ACT);
        switch (t.kind()) {
            case BELLOWS -> WorkClient.send(focus.pos, WorkAction.PUMP);
            case BILLET -> WorkClient.send(focus.pos, WorkAction.TURN);
            case DRAW -> WorkClient.send(focus.pos, WorkAction.DRAW);
            case COMMIT -> WorkClient.send(focus.pos, WorkAction.COMMIT);
            case REST -> WorkClient.send(focus.pos, tongsLoaded() ? WorkAction.REST_PLACE : WorkAction.REST_TAKE);
            case BOOK -> {
                focus.overlay = true;
                WorkClient.send(focus.pos, WorkAction.BOOK);
            }
            case OPERATION -> {
                int flags = WorkKeys.held(WorkKeys.HEAVY) ? WorkAction.HEAVY : 0;
                int seq = WorkClient.send(focus.pos, WorkAction.STRIKE, t.index(), t.u(), t.v(), flags);
                if (seq >= 0) SceneTools.strike(focus.pos, seq, t.world(), flags != 0);
            }
            case SOCKET -> WorkClient.send(focus.pos, WorkAction.FIT, t.index(), t.u(), t.v(), 0);
            case OUTPUT -> WorkClient.send(focus.pos, WorkAction.COLLECT);
            case PIECE -> WorkClient.send(focus.pos, WorkAction.TAKE_PIECE);
            case PLACE -> WorkClient.send(focus.pos, WorkAction.PLACE_PIECE);
            case WATER -> {
                boolean occupied = minecraft.level.getBlockEntity(focus.pos) instanceof SmithsTroughBlockEntity trough && trough.occupied();
                if (occupied && (!hold || !lowered)) {
                    WorkClient.send(focus.pos, WorkAction.LIFT);
                    lowered = false;
                } else if (!occupied) {
                    WorkClient.send(focus.pos, WorkAction.LOWER);
                    lowered = true;
                    if (hold) holding = WorkTargets.Kind.WATER;
                }
            }
            case WHEEL -> {
                if (holding == WorkTargets.Kind.WHEEL) {
                    WorkClient.send(focus.pos, WorkAction.GRIND_STOP);
                    holding = null;
                } else {
                    WorkClient.send(focus.pos, WorkAction.GRIND_HOLD);
                    holding = WorkTargets.Kind.WHEEL;
                    holdTicks = 0;
                }
            }
            case TAKE -> {
                if (holding == WorkTargets.Kind.WHEEL) {
                    WorkClient.send(focus.pos, WorkAction.GRIND_STOP);
                    holding = null;
                }
                WorkClient.send(focus.pos, WorkAction.GRIND_TAKE);
            }
        }
    }

    private void secondary(@Nullable WorkTargets.Target t) {
        if (minecraft == null || minecraft.level == null) return;
        switch (focus.kind) {
            case FORGE -> {
                if (t != null && t.kind() == WorkTargets.Kind.BILLET) WorkClient.send(focus.pos, WorkAction.DRAW);
                else if (t != null && t.kind() == WorkTargets.Kind.REST) WorkClient.send(focus.pos, WorkAction.REST_TAKE);
            }
            case ANVIL -> WorkClient.send(focus.pos, WorkAction.FLIP);
            case TROUGH -> {
                WorkClient.send(focus.pos, WorkAction.LIFT);
                lowered = false;
                holding = null;
            }
            case GRINDSTONE -> {
                boolean efficacy = minecraft.level.getBlockEntity(focus.pos) instanceof SmithsGrindstoneBlockEntity stone && stone.efficacy();
                WorkClient.send(focus.pos, WorkAction.GRIND_ASPECT, efficacy ? 0 : 1, 0F, 0F, 0);
            }
        }
    }

    /** End of a hold: lift the piece, take the edge off the wheel. In toggle mode a release does nothing. */
    private void release() {
        if (holding == null || !ClientConfig.get(ClientConfig.HOLD_TO_ACT)) return;
        if (holding == WorkTargets.Kind.WATER) {
            WorkClient.send(focus.pos, WorkAction.LIFT);
            lowered = false;
        } else if (holding == WorkTargets.Kind.WHEEL) {
            WorkClient.send(focus.pos, WorkAction.GRIND_STOP);
        }
        holding = null;
    }

    @Override
    public void tick() {
        if (holding == WorkTargets.Kind.WHEEL && ++holdTicks % 5 == 0) WorkClient.send(focus.pos, WorkAction.GRIND_HOLD);
    }

    @Override
    public void onClose() {
        FocusController.exit(null, true);
    }

    @Override
    public void removed() {
        if (holding == WorkTargets.Kind.WHEEL) WorkClient.send(focus.pos, WorkAction.GRIND_STOP);
        holding = null;
        super.removed();
    }

    @Override
    public Component getNarrationMessage() {
        if (minecraft == null || minecraft.level == null) return super.getNarrationMessage();
        return WorkHud.narration(minecraft.level, focus.pos, focus.kind, current());
    }

    public FocusController.Focus focus() {
        return focus;
    }

    @Nullable
    public WorkTargets.Target hoveredTarget() {
        return current();
    }

    /** The focus screen currently showing, if any. */
    @Nullable
    public static WorkFocusScreen showing() {
        return Minecraft.getInstance().screen instanceof WorkFocusScreen s ? s : null;
    }
}
