package com.otectus.immersivesmithing.api;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player assistance for hands-on work. Answers multiply; the product is clamped to
 * {@code 1.0..maxAssistTimeMultiplier} (server config), so assistance only ever helps and never past the operator's
 * ceiling. Since 1.3.0 the {@link SmithingStation#ANVIL} answer divides the hammer's recovery time between blows (never
 * below four ticks); the forge has no timed work any more, so {@link SmithingStation#FORGE} is not asked. The server applies
 * it when it validates each blow; nothing is sent to the client. Server thread only; a source that throws counts as
 * {@code 1.0}.
 */
@FunctionalInterface
public interface ISmithingAssistSource {
    double timeMultiplier(ServerPlayer smith, SmithingStation station);
}
