package com.otectus.immersivesmithing.minigame;

import com.otectus.immersivesmithing.item.SmithingTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Common state of an open station screen session. Since 1.3.0 only the forge's pattern book (choosing a plan and casting)
 * uses one; the hands-on work itself lives in the stations ({@code work/}).
 */
public abstract class SmithingSession {
    public enum Kind { FORGE, ANVIL }

    public final int id;
    public final UUID playerId;
    public final ResourceKey<Level> dimension;
    public final BlockPos pos;
    public final InteractionHand hand;
    public final SmithingTier tier;
    public final long seed;
    protected final long createdMs;
    /** The last time the smith did something in the selection stage (opening, casting); the selection times out from it. */
    protected long activityMs;

    protected SmithingSession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, InteractionHand hand,
                              SmithingTier tier, long seed, long now) {
        this.id = id;
        this.playerId = playerId;
        this.dimension = dimension;
        this.pos = pos.immutable();
        this.hand = hand;
        this.tier = tier;
        this.seed = seed;
        this.createdMs = now;
        this.activityMs = now;
    }

    public abstract Kind kind();

    public GlobalPos station() {
        return GlobalPos.of(dimension, pos);
    }
}
