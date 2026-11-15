package com.otectus.immersivesmithing.client.work;

import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.WorkActionPacket;
import com.otectus.immersivesmithing.work.SmithingActionService;
import com.otectus.immersivesmithing.work.WorkAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Sends station intents. Every request carries the station instance it was aimed at and a strictly growing sequence number;
 * the server drops duplicates, replays and requests for a station that has been replaced since.
 */
public final class WorkClient {
    private static int sequence;

    /** Sends an intent and returns its sequence number, or -1 when there is no station there. */
    public static int send(BlockPos pos, WorkAction action, int target, float u, float v, int flags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getConnection() == null) return -1;
        var be = mc.level.getBlockEntity(pos);
        if (SmithingActionService.stationOf(be) == null) return -1;
        int seq = ++sequence;
        ModNetwork.CHANNEL.sendToServer(new WorkActionPacket(pos, SmithingActionService.instanceOf(be), action, seq, target,
                Float.isFinite(u) ? u : 0F, Float.isFinite(v) ? v : 0F, flags));
        return seq;
    }

    public static int send(BlockPos pos, WorkAction action) {
        return send(pos, action, -1, 0F, 0F, 0);
    }

    private WorkClient() {}
}
