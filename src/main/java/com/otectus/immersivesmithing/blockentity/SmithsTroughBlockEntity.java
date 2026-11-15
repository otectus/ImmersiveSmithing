package com.otectus.immersivesmithing.blockentity;

import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.util.StationEffects;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.QuenchRules;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.work.WorkQuality;
import com.otectus.immersivesmithing.work.WorkStage;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Water for quenching, in millibuckets, and the quench slot: while a piece is being lowered, immersed or left in the water,
 * the trough holds it (never the tongs as well). Lowering reserves one quench's water; crossing the immersion boundary
 * charges it, once per immersion; lifting before the boundary releases the reservation. Pipes may fill the trough; nothing
 * drains it automatically, and reserved water cannot be taken out with a bucket.
 */
public class SmithsTroughBlockEntity extends BlockEntity {
    private int water;
    private int reservedWater;
    @Nullable
    private WorkpieceData quench;
    @Nullable
    private CompoundTag quenchRaw;
    private int lowerTicks;
    private boolean immersed;
    @Nullable
    private UUID quencher;
    private long clock;
    private int instanceId = new java.util.Random().nextInt();
    private int clientCapacity;

    private final LazyOptional<IFluidHandler> fluidInput = LazyOptional.of(WaterInput::new);

    public SmithsTroughBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMITHS_TROUGH.get(), pos, state);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.2, 0, 0.2).expandTowards(0, 0.8, 0);
    }

    public int water() {
        return water;
    }

    public int availableWater() {
        return Math.max(0, water - reservedWater);
    }

    public int capacity() {
        return FluidType.BUCKET_VOLUME * ServerConfig.get(ServerConfig.TROUGH_CAPACITY_BUCKETS);
    }

    public int waterPerQuench() {
        return Math.max(1, FluidType.BUCKET_VOLUME / ServerConfig.get(ServerConfig.TROUGH_QUENCHES_PER_BUCKET));
    }

    public int quenchesLeft() {
        return availableWater() / waterPerQuench();
    }

    public float fillFraction() {
        return capacity() <= 0 ? 0F : Math.min(1F, water / (float) capacity());
    }

    public boolean canQuench() {
        return availableWater() >= waterPerQuench();
    }

    public boolean canAcceptBucket() {
        return water < capacity();
    }

    public void addBucket() {
        setWater(Math.min(capacity(), water + FluidType.BUCKET_VOLUME));
    }

    public boolean takeBucket() {
        if (availableWater() < FluidType.BUCKET_VOLUME) return false;
        setWater(water - FluidType.BUCKET_VOLUME);
        return true;
    }

    /** Charges one quench's water at once (used by automation-free direct quench paths and tests). */
    public boolean consumeQuench() {
        if (!canQuench()) return false;
        setWater(water - waterPerQuench());
        return true;
    }

    public long clock() {
        return clock;
    }

    public int instanceId() {
        return instanceId;
    }

    @Nullable
    public WorkpieceData quenchPiece() {
        return quench;
    }

    public boolean occupied() {
        return quench != null || quenchRaw != null;
    }

    public boolean immersed() {
        return immersed;
    }

    public int lowerTicks() {
        return lowerTicks;
    }

    @Nullable
    public UUID quencher() {
        return quencher;
    }

    public int quenchDuration() {
        return quench == null ? 60 : QuenchRules.duration(quench.process(), ServerConfig.get(ServerConfig.TROUGH_QUENCH_TIME_MULTIPLIER));
    }

    /** Starts lowering a piece: the trough takes it and reserves one quench's water. */
    public boolean lower(WorkpieceData piece, UUID smith) {
        // A quench already begun was charged its water at its first immersion; continuing it costs nothing more.
        boolean paid = piece.process().dips > 0;
        if (occupied() || (!paid && !canQuench())) return false;
        ProcessState p = piece.process().copy();
        p.heatTick = ProcessState.ITEM_HELD; // simulated tick by tick while in the trough
        quench = piece.withProcess(p);
        reservedWater = paid ? 0 : waterPerQuench();
        lowerTicks = 0;
        immersed = false;
        quencher = smith;
        sync();
        return true;
    }

    /**
     * Lifts the piece out. Before the immersion boundary the reservation is released and the piece is exactly as it went
     * in; after it, the quench progress and the water already charged stay with the piece.
     */
    @Nullable
    public WorkpieceData lift() {
        WorkpieceData out = quench;
        if (out == null) return null;
        if (!immersed) reservedWater = 0;
        quench = null;
        lowerTicks = 0;
        immersed = false;
        quencher = null;
        sync();
        ProcessState p = out.process().copy();
        p.heatTick = ProcessState.ITEM_HELD;
        p.revision++;
        return out.withProcess(p);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmithsTroughBlockEntity be) {
        be.clock++;
        if (be.quench == null) return;
        ProcessState p = be.quench.process();
        int duration = be.quenchDuration();
        if (!be.immersed) {
            be.lowerTicks++;
            if (be.lowerTicks >= QuenchRules.BOUNDARY_TICKS) be.crossBoundary((ServerLevel) level);
            return;
        }
        if (p.quenchTicks < QuenchRules.maxTicks(duration)) {
            p.quenchTicks++;
            for (int z = 0; z < p.heat.length; z++) p.heat[z] = ThermalRules.quenchCool(p.heat[z], 1, duration);
            if (p.quenchTicks == duration) {
                // The end cue: the hiss subsides, the steam clears.
                level.playSound(null, pos, ModSounds.QUENCH_SETTLE.get(), SoundSource.BLOCKS, 0.7F, 1.1F);
                StationEffects.send((ServerLevel) level, pos, StationEffects.STEAM_CLEAR, 0.5F);
                be.sync();
            } else if (p.quenchTicks % 20 == 0) {
                be.sync();
            }
        }
        if (be.clock % 100 == 0) be.setChanged();
    }

    /** The piece reaches the water: the reserved water is charged (once) and the shaping phase is finalized (once). */
    private void crossBoundary(ServerLevel level) {
        if (quench == null) return;
        immersed = true;
        water = Math.max(0, water - reservedWater);
        reservedWater = 0;
        ProcessState p = quench.process();
        p.dips++;
        p.quenchPos = worldPosition.immutable();
        int anvil = quench.anvilScore();
        boolean faulty = quench.faulty() || p.burnt;
        if (p.stage != WorkStage.QUENCHING) {
            if (anvil < 0) anvil = WorkQuality.anvilScore(p, quench.anvilScore());
            p.stage = WorkStage.QUENCHING;
        }
        quench = quench.withScores(quench.forgeScore(), anvil, faulty, p);
        level.playSound(null, worldPosition, ModSounds.QUENCH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 1.2F);
        StationEffects.send(level, worldPosition, StationEffects.STEAM, 1.0F);
        sync();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SmithsTroughBlockEntity be) {
        be.clock++;
        if (be.quench == null) return;
        ProcessState p = be.quench.process();
        if (!be.immersed) {
            be.lowerTicks = Math.min(QuenchRules.BOUNDARY_TICKS, be.lowerTicks + 1);
        } else if (p.quenchTicks < QuenchRules.maxTicks(be.quenchDuration())) {
            p.quenchTicks++;
            for (int z = 0; z < p.heat.length; z++) p.heat[z] = ThermalRules.quenchCool(p.heat[z], 1, be.quenchDuration());
        }
    }

    /** Breaking the trough keeps the piece (with its quench progress) and spills the water. */
    public void dropContents() {
        if (level == null || (quench == null && quenchRaw == null)) return;
        ItemStack item = new ItemStack(ModItems.HOT_WORKPIECE.get());
        if (quench != null) {
            ProcessState p = quench.process().copy();
            p.heatTick = ProcessState.ITEM_HELD;
            WorkpieceCodec.setHeld(item, quench.withProcess(p));
        } else {
            WorkpieceCodec.setRawHeld(item, quenchRaw);
        }
        quench = null;
        quenchRaw = null;
        reservedWater = 0;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, item);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) WorkLeases.onStationGone(level, worldPosition);
    }

    private void setWater(int amount) {
        water = Math.max(0, amount);
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeState(tag, true);
    }

    private void writeState(CompoundTag tag, boolean full) {
        tag.putInt("Water", water);
        tag.putInt("Reserved", reservedWater);
        if (quench != null) tag.put("Quench", WorkpieceCodec.save(quench));
        else if (quenchRaw != null && full) tag.put("Quench", quenchRaw);
        tag.putInt("Lower", lowerTicks);
        tag.putBoolean("Immersed", immersed);
        if (quencher != null) tag.putUUID("Quencher", quencher);
        tag.putLong("Clock", clock);
        tag.putInt("Instance", instanceId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        water = Math.max(0, tag.getInt("Water"));
        reservedWater = Math.max(0, Math.min(water, tag.getInt("Reserved")));
        quench = null;
        quenchRaw = null;
        if (tag.contains("Quench", Tag.TAG_COMPOUND)) {
            WorkpieceCodec.Read read = WorkpieceCodec.read(tag.getCompound("Quench"));
            if (read.ok()) quench = read.data();
            else quenchRaw = tag.getCompound("Quench").copy();
        }
        lowerTicks = Math.max(0, tag.getInt("Lower"));
        immersed = tag.getBoolean("Immersed");
        quencher = tag.hasUUID("Quencher") ? tag.getUUID("Quencher") : null;
        clock = Math.max(0, tag.getLong("Clock"));
        if (tag.contains("Instance", Tag.TAG_INT)) instanceId = tag.getInt("Instance");
        if (quench == null && quenchRaw == null) reservedWater = 0;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag, false);
        tag.putInt("Capacity", capacity());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        clientCapacity = tag.getInt("Capacity");
    }

    /** Fill fraction for rendering; uses the capacity sent by the server. */
    public float clientFillFraction() {
        int cap = clientCapacity > 0 ? clientCapacity : capacity();
        return cap <= 0 ? 0F : Math.min(1F, water / (float) cap);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) handleUpdateTag(tag);
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && ServerConfig.get(ServerConfig.ENABLE_AUTOMATION)
                && ServerConfig.get(ServerConfig.TROUGH_FLUID_INPUT)) {
            return fluidInput.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidInput.invalidate();
    }

    private final class WaterInput implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return water > 0 ? new FluidStack(Fluids.WATER, water) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().isSame(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !isFluidValid(0, resource)) return 0;
            int filled = Math.min(capacity() - water, resource.getAmount());
            if (filled > 0 && action.execute()) setWater(water + filled);
            return Math.max(0, filled);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
