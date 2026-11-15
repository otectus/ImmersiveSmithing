package com.otectus.immersivesmithing.client.screen;

import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Interface sounds of the smithing screens and the readiness cue (both honour the client's cue options). */
public final class SmithingGui {

    public static void playUi(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** A short readiness cue (a billet ready to draw, a quench done), when the player has cues on. */
    public static void playCue() {
        if (!ClientConfig.get(ClientConfig.TIMING_CUE_SOUNDS)) return;
        float volume = (float) ClientConfig.get(ClientConfig.SOUND_CUE_VOLUME);
        if (volume > 0F) playUi(ModSounds.TIMING_CUE.get(), 1.4F, volume);
    }

    private SmithingGui() {}
}
