package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.WorkEventPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic station events for everyone tracking the chunk. Each carries the contact point in station-local coordinates
 * (or, for anvil work, the canonical point on the piece), so every client draws the sound, sparks and material change of
 * one accepted action at the same place, at most once.
 */
public final class WorkEvents {
    public static final int STRIKE = 0;
    public static final int REFUSED = 1;
    public static final int PUMP = 2;
    public static final int TURN = 3;
    public static final int DRAW = 4;
    public static final int PLACE = 5;
    public static final int TAKE = 6;
    public static final int FLIP = 7;
    public static final int FIT = 8;
    public static final int COMPLETE = 9;
    public static final int LOWER = 10;
    public static final int LIFT = 11;
    public static final int GRIND = 12;
    public static final int STAMP = 13;
    public static final int RECYCLE = 14;
    public static final int COMMIT = 15;
    public static final int CANCEL = 16;
    public static final int COLLECT = 17;
    public static final int SHAPED = 18;
    public static final int REFINED = 19;

    public static void send(ServerLevel level, BlockPos pos, int type, @Nullable Player actor, int sequence,
                            float x, float y, float z, float strength, int data) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)),
                new WorkEventPacket(pos, type, actor != null ? actor.getId() : -1, sequence, x, y, z, strength, data));
    }

    public static void send(ServerLevel level, BlockPos pos, int type, @Nullable Player actor, int sequence,
                            net.minecraft.world.phys.Vec3 local, float strength, int data) {
        send(level, pos, type, actor, sequence, (float) local.x, (float) local.y, (float) local.z, strength, data);
    }

    private WorkEvents() {}
}
