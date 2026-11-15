package com.otectus.immersivesmithing.work;

import com.otectus.immersivesmithing.blockentity.SmithsAnvilBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsGrindstoneBlockEntity;
import com.otectus.immersivesmithing.blockentity.SmithsTroughBlockEntity;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.network.ModNetwork;
import com.otectus.immersivesmithing.network.packet.FocusPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Temporary control of a station: one writer per work surface. A lease is taken by the first valid action (World view) or
 * a focus request, refreshed by every accepted action and released by the smith, by leaving, by damage while focused, by
 * death, logout, dimension change, the station going away, or {@code work.leaseIdleSeconds} without an action. It owns no
 * items and no progress: releasing it never changes the work, which lives in the station's block entity or the tongs.
 * Permission (spawn protection, claims) is checked when a lease is taken, through the same right-click event normal block
 * use fires, so packets never get around a protection that the block would have respected.
 */
public final class WorkLeases {
    public enum Station {
        FORGE, ANVIL, TROUGH, GRINDSTONE;

        private static final Station[] VALUES = values();

        public static Station byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    public static final class Lease {
        public final UUID player;
        public final GlobalPos station;
        public final Station kind;
        public final int nonce;
        boolean focused;
        long lastActive;
        int lastSequence = Integer.MIN_VALUE;
        /** The station clock of the last accepted blow or stroke, for recovery intervals. */
        public long lastBlow = Long.MIN_VALUE / 2;
        int tokens = MAX_TOKENS;
        long tokenTick;

        Lease(UUID player, GlobalPos station, Station kind, int nonce) {
            this.player = player;
            this.station = station;
            this.kind = kind;
            this.nonce = nonce;
        }

        public boolean focused() {
            return focused;
        }

        /** Rejects duplicated and replayed packets: sequence numbers only grow. */
        public boolean acceptSequence(int sequence) {
            if (sequence <= lastSequence) return false;
            lastSequence = sequence;
            return true;
        }

        /** A per-smith action budget: bursts beyond it are dropped, never queued. */
        boolean takeToken(long tick) {
            if (tick > tokenTick) {
                tokens = (int) Math.min(MAX_TOKENS, tokens + (tick - tokenTick));
                tokenTick = tick;
            }
            if (tokens <= 0) return false;
            tokens--;
            return true;
        }
    }

    private static final int MAX_TOKENS = 20;
    /** How long a smith working in World view keeps a station after their last action. */
    private static final long WORLD_VIEW_HOLD_TICKS = 60;
    private static final Map<GlobalPos, Lease> BY_STATION = new HashMap<>();
    private static final Map<UUID, Lease> BY_PLAYER = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static long serverTick;

    @Nullable
    public static Lease of(ServerPlayer player) {
        return BY_PLAYER.get(player.getUUID());
    }

    @Nullable
    public static UUID holder(Level level, BlockPos pos) {
        Lease lease = BY_STATION.get(GlobalPos.of(level.dimension(), pos));
        return lease == null ? null : lease.player;
    }

    public static boolean heldByOther(Level level, BlockPos pos, UUID player) {
        UUID holder = holder(level, pos);
        return holder != null && !holder.equals(player);
    }

    /**
     * The smith's lease at this station, taking it if the station is free. Null when another smith holds it or the action
     * is not permitted here; the reason has been told to the smith.
     */
    @Nullable
    public static Lease acquire(ServerPlayer player, BlockPos pos, Station kind, boolean focus) {
        GlobalPos key = GlobalPos.of(player.level().dimension(), pos.immutable());
        Lease lease = BY_STATION.get(key);
        if (lease != null && !lease.player.equals(player.getUUID()) && !lease.focused && serverTick - lease.lastActive > WORLD_VIEW_HOLD_TICKS) {
            // A World-view hold lapses a few seconds after its last action, so a shared workshop is not blocked.
            release(lease, null, null);
            lease = null;
        }
        if (lease != null && !lease.player.equals(player.getUUID())) {
            com.otectus.immersivesmithing.util.Feedback.fail(player, "station_in_use");
            return null;
        }
        if (lease == null) {
            if (!permitted(player, pos)) {
                com.otectus.immersivesmithing.util.Feedback.fail(player, "station_protected");
                return null;
            }
            Lease previous = BY_PLAYER.get(player.getUUID());
            if (previous != null) release(previous, player, null);
            lease = new Lease(player.getUUID(), key, kind, RANDOM.nextInt());
            BY_STATION.put(key, lease);
            BY_PLAYER.put(player.getUUID(), lease);
        }
        lease.lastActive = serverTick;
        if (focus && !lease.focused) {
            lease.focused = true;
            if (!(player instanceof FakePlayer)) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new FocusPacket(true, pos, kind.ordinal(), lease.nonce, Component.empty()));
            }
        }
        return lease;
    }

    public static boolean takeToken(Lease lease) {
        return lease.takeToken(serverTick);
    }

    /** The smith lets go of the station (Escape, moving away, an explicit release). */
    public static void release(ServerPlayer player, @Nullable Component reason) {
        Lease lease = BY_PLAYER.get(player.getUUID());
        if (lease != null) release(lease, player, reason);
    }

    private static void release(Lease lease, @Nullable ServerPlayer player, @Nullable Component reason) {
        BY_STATION.remove(lease.station, lease);
        BY_PLAYER.remove(lease.player, lease);
        if (lease.focused && player != null && !(player instanceof FakePlayer) && player.connection != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new FocusPacket(false, lease.station.pos(), lease.kind.ordinal(), lease.nonce, reason != null ? reason : Component.empty()));
        }
    }

    public static void onStationGone(Level level, BlockPos pos) {
        Lease lease = BY_STATION.get(GlobalPos.of(level.dimension(), pos));
        if (lease == null) return;
        ServerPlayer player = level.getServer() != null ? level.getServer().getPlayerList().getPlayer(lease.player) : null;
        release(lease, player, Component.translatable("message.immersive_smithing.station_missing"));
    }

    public static void onLogout(ServerPlayer player) {
        Lease lease = BY_PLAYER.get(player.getUUID());
        if (lease != null) release(lease, null, null);
    }

    public static void onDeath(ServerPlayer player) {
        Lease lease = BY_PLAYER.get(player.getUUID());
        if (lease != null) release(lease, player, Component.translatable("message.immersive_smithing.session_interrupted"));
    }

    public static void onServerStopping() {
        BY_STATION.clear();
        BY_PLAYER.clear();
    }

    /** Checks every lease a few times a second; anything no longer valid is released with the reason. */
    public static void tick(MinecraftServer server) {
        serverTick++;
        if (BY_PLAYER.isEmpty() || serverTick % 5 != 0) return;
        long idle = 20L * ServerConfig.get(ServerConfig.WORK_LEASE_IDLE_SECONDS);
        for (Lease lease : new ArrayList<>(BY_PLAYER.values())) {
            ServerPlayer player = server.getPlayerList().getPlayer(lease.player);
            if (player == null) {
                release(lease, null, null);
                continue;
            }
            String why = null;
            if (!player.isAlive()) why = "session_interrupted";
            else if (!player.level().dimension().equals(lease.station.dimension())) why = "session_interrupted";
            else if (!stationPresent(player.serverLevel(), lease)) why = "station_missing";
            else if (!inReach(player, lease.station.pos())) why = "moved_away";
            else if (lease.focused && player.hurtTime > 0) why = "session_interrupted";
            else if (serverTick - lease.lastActive > idle) why = ""; // a focused client refreshes its lease while open
            if (why != null) release(lease, player, why.isEmpty() ? null : Component.translatable("message.immersive_smithing." + why));
        }
    }

    /** Keeps a focused lease alive while the smith is merely looking (no action for a while is fine while focused). */
    public static void keepAlive(ServerPlayer player) {
        Lease lease = BY_PLAYER.get(player.getUUID());
        if (lease != null) lease.lastActive = serverTick;
    }

    private static boolean stationPresent(ServerLevel level, Lease lease) {
        BlockPos pos = lease.station.pos();
        if (!level.isLoaded(pos)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        return switch (lease.kind) {
            case FORGE -> be instanceof SmithsForgeBlockEntity;
            case ANVIL -> be instanceof SmithsAnvilBlockEntity;
            case TROUGH -> be instanceof SmithsTroughBlockEntity;
            case GRINDSTONE -> be instanceof SmithsGrindstoneBlockEntity;
        };
    }

    /** Distance from the real player's eyes, never from a camera. */
    public static boolean inReach(ServerPlayer player, BlockPos pos) {
        double max = ServerConfig.get(ServerConfig.MAX_STATION_DISTANCE);
        return player.getEyePosition().distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= max * max;
    }

    /**
     * Whether the real player can see the station: a ray from their eyes to the middle of its top must reach the station's
     * own block before anything else. A detached camera's view is irrelevant.
     */
    public static boolean canSee(ServerPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3[] targets = {new Vec3(pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5), new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)};
        for (Vec3 target : targets) {
            BlockHitResult hit = player.level().clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }

    /** Spawn protection, world border, adventure-mode rules and claim mods, through the normal block-use hook. */
    private static boolean permitted(ServerPlayer player, BlockPos pos) {
        if (player.isSpectator() || !player.mayInteract(player.level(), pos)) return false;
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        return !event.isCanceled() && event.getUseBlock() != Event.Result.DENY;
    }

    /** Leases for diagnostics. */
    public static List<Lease> all() {
        return List.copyOf(BY_PLAYER.values());
    }

    private WorkLeases() {}
}
