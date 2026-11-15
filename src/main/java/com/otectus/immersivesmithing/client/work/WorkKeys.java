package com.otectus.immersivesmithing.client.work;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Remappable station controls. Primary and secondary actions are the player's own attack and use bindings; these add the
 * focus key, keyboard part selection, turning the piece over and the heavy-blow modifier. Prompts always show the
 * player's actual bindings ({@link #name}).
 */
public final class WorkKeys {
    public static final String CATEGORY = "key.categories.immersive_smithing";

    public static final KeyMapping FOCUS = new KeyMapping("key.immersive_smithing.focus", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.immersive_smithing.next_part", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, CATEGORY);
    public static final KeyMapping PREVIOUS = new KeyMapping("key.immersive_smithing.previous_part", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, CATEGORY);
    public static final KeyMapping ACT = new KeyMapping("key.immersive_smithing.act", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SPACE, CATEGORY);
    public static final KeyMapping FLIP = new KeyMapping("key.immersive_smithing.flip", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping HEAVY = new KeyMapping("key.immersive_smithing.heavy", KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping CAMERA = new KeyMapping("key.immersive_smithing.camera_mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(FOCUS);
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(ACT);
        event.register(FLIP);
        event.register(HEAVY);
        event.register(CAMERA);
    }

    /** Whether a key mapping is physically held right now (works inside screens, where mappings do not update). */
    public static boolean held(KeyMapping mapping) {
        if (mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getValue() > 0 && InputConstants.isKeyDown(window, key.getValue());
    }

    /** The binding's key name, for prompts. */
    public static Component name(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage();
    }

    private WorkKeys() {}
}
