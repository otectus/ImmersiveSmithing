package com.otectus.immersivesmithing.minigame;

import com.otectus.immersivesmithing.item.SmithingTier;
import com.otectus.immersivesmithing.recipe.SmithingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public final class ForgeSession extends SmithingSession {
    /** Offered recipe ids (choosable or shown dimmed); a selection outside this list is rejected. */
    List<ResourceLocation> offered = List.of();
    /** Cast outputs offered in the selection stage, with the units each costs; a cast of anything else is refused. */
    List<CastChoice> castOptions = List.of();
    /** The last accepted cast request: a sequence number at or below it is a duplicate or a replay. */
    int lastCastSequence;

    ForgeSession(int id, UUID playerId, ResourceKey<Level> dimension, BlockPos pos, InteractionHand hand, SmithingTier tier,
                 long seed, long now) {
        super(id, playerId, dimension, pos, hand, tier, seed, now);
    }

    @Override
    public Kind kind() {
        return Kind.FORGE;
    }

    /** An offered cast output: the item and what one costs. */
    record CastChoice(net.minecraft.world.item.Item item, int unitsEach) {}
}
