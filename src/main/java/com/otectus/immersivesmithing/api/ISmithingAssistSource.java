package com.otectus.immersivesmithing.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player time allowance for the forge and anvil minigames. Answers multiply; the product is clamped to
 * {@code 1.0..maxAssistTimeMultiplier} (server config), so assistance can only lengthen a session and never past the
 * operator's ceiling. The client receives the final allowance in the session's start packet. Server thread only; a
 * source that throws counts as {@code 1.0}.
 */
@FunctionalInterface
public interface ISmithingAssistSource {
    double timeMultiplier(ServerPlayer smith, SmithingStation station);
}
