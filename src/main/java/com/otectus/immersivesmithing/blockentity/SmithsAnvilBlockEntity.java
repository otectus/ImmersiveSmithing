package com.otectus.immersivesmithing.blockentity;

import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalProfile;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The anvil's work surface: at most one workpiece being shaped or fitted, or the finished piece waiting to be collected,
 * never both. A workpiece that no longer decodes is kept as its raw tag and dropped intact if the anvil is broken. The
 * anvil keeps a work clock that only runs while it is loaded, and a piece's heat is measured against it, so cooling never
 * happens in an unloaded chunk. No automation capability is exposed: only players move pieces.
 */
public class SmithsAnvilBlockEntity extends BlockEntity {
    private static final int HOT_SYNC_INTERVAL = 200;

    @Nullable
    private WorkpieceData workpiece;
    @Nullable
    private CompoundTag workpieceRaw;
    private ItemStack output = ItemStack.EMPTY;
    @Nullable
    private ResourceLocation outputVisual;
    private boolean outputFlipped;
    private long clock;
    private int instanceId = new java.util.Random().nextInt();

    public SmithsAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMITHS_ANVIL.get(), pos, state);
    }

    /** The visible workpiece/pile can rise above the block; retain normal frustum culling. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.25, 0, 0.25).expandTowards(0, 0.5, 0);
    }

    @Nullable
    public WorkpieceData workpiece() {
        return workpiece;
    }

    public boolean hasWorkpiece() {
        return workpiece != null;
    }

    public boolean hasUnreadablePiece() {
        return workpieceRaw != null;
    }

    public ItemStack output() {
        return output;
    }

    public boolean hasOutput() {
        return !output.isEmpty();
    }

    @Nullable
    public ResourceLocation outputVisual() { return outputVisual; }

    public boolean outputFlipped() { return outputFlipped; }

    /** Anything on the face at all: a piece (readable or not) or a finished item. */
    public boolean isOccupied() {
        return workpiece != null || workpieceRaw != null || !output.isEmpty();
    }

    public long clock() {
        return clock;
    }

    public int instanceId() {
        return instanceId;
    }

    /** The piece's zones' heat right now. */
    public int[] currentHeat() {
        return workpiece == null ? new int[0] : workpiece.process().heatAt(clock, 1.0);
    }

    /** Places a piece taken from tongs or a dropped item; its heat is re-based to this anvil's clock. */
    public void setWorkpiece(@Nullable WorkpieceData data) {
        if (data != null) {
            ProcessState p = data.process().copy();
            p.bake(clock, clock);
            if (p.heatTick == ProcessState.ITEM_HELD || p.heatTick != clock) p.heatTick = clock;
            data = data.withProcess(p);
        }
        this.workpiece = data;
        sync();
    }

    /** Stores a piece already re-based to this anvil's clock (after a blow, a flip or a fitting). */
    public void replaceWorkpiece(WorkpieceData data) {
        this.workpiece = data;
        sync();
    }

    /** Takes the piece off; its current heat is written down for whatever holds it next. */
    @Nullable
    public WorkpieceData takeWorkpiece() {
        WorkpieceData data = workpiece;
        workpiece = null;
        sync();
        if (data == null) return null;
        ProcessState p = data.process().copy();
        p.bake(clock, ProcessState.ITEM_HELD);
        p.revision++;
        return data.withProcess(p);
    }

    public void setOutput(ItemStack stack) {
        setOutput(stack, com.otectus.immersivesmithing.work.ProcessResolver.forLegacy(null, stack).visual(), false);
    }

    /** Keep the completed piece in its last work frame, including datapack profiles and the face turned upward. */
    public void setOutput(ItemStack stack, ResourceLocation visual, boolean flipped) {
        output = stack;
        outputVisual = stack.isEmpty() ? null : visual;
        outputFlipped = !stack.isEmpty() && flipped;
        sync();
    }

    public ItemStack takeOutput() {
        ItemStack out = output;
        output = ItemStack.EMPTY;
        outputVisual = null;
        outputFlipped = false;
        sync();
        return out;
    }

    /** A lease held by another player. */
    public boolean isLockedFor(UUID player) {
        return level != null && WorkLeases.heldByOther(level, worldPosition, player);
    }

    public boolean isLocked() {
        return level != null && WorkLeases.holder(level, worldPosition) != null;
    }

    /** Drops the workpiece (as a Hot Workpiece item, raw data intact) and any finished piece, so nothing is lost with the block. */
    public void dropWorkpiece() {
        if (level == null) return;
        if (workpiece != null || workpieceRaw != null) {
            ItemStack item = new ItemStack(ModItems.HOT_WORKPIECE.get());
            if (workpiece != null) {
                ProcessState p = workpiece.process().copy();
                p.bake(clock, ProcessState.ITEM_HELD);
                WorkpieceCodec.setHeld(item, workpiece.withProcess(p));
            } else {
                WorkpieceCodec.setRawHeld(item, workpieceRaw);
            }
            workpiece = null;
            workpieceRaw = null;
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, item);
        }
        if (!output.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, output);
            output = ItemStack.EMPTY;
            outputVisual = null;
            outputFlipped = false;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmithsAnvilBlockEntity be) {
        be.clock++;
        if (be.workpiece != null) {
            if (be.clock % 100 == 0) be.setChanged();
            if (be.clock % HOT_SYNC_INTERVAL == 0 && ThermalProfile.fraction(com.otectus.immersivesmithing.work.ThermalRules.mean(be.currentHeat())) > 0.05F) {
                be.sync();
            }
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SmithsAnvilBlockEntity be) {
        be.clock++;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            SessionManager.onStationUnloaded(level, worldPosition);
            WorkLeases.onStationGone(level, worldPosition);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (workpiece != null) tag.put("Workpiece", WorkpieceCodec.save(workpiece));
        else if (workpieceRaw != null) tag.put("Workpiece", workpieceRaw);
        if (!output.isEmpty()) {
            tag.put("Output", output.save(new CompoundTag()));
            if (outputVisual != null) tag.putString("OutputVisual", outputVisual.toString());
            tag.putBoolean("OutputFlipped", outputFlipped);
        }
        tag.putLong("Clock", clock);
        tag.putInt("Instance", instanceId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        workpiece = null;
        workpieceRaw = null;
        if (tag.contains("Workpiece", Tag.TAG_COMPOUND)) {
            WorkpieceCodec.Read read = WorkpieceCodec.read(tag.getCompound("Workpiece"));
            if (read.ok()) {
                workpiece = read.data();
                if (read.migrated()) {
                    // Old pieces had no heat: start them workable on this anvil's clock.
                    ProcessState p = workpiece.process().copy();
                    p.heatTick = Math.max(0, tag.getLong("Clock"));
                    workpiece = workpiece.withProcess(p);
                }
            } else {
                workpieceRaw = tag.getCompound("Workpiece").copy();
            }
        }
        output = tag.contains("Output", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Output")) : ItemStack.EMPTY;
        String visual = tag.getString("OutputVisual");
        outputVisual = output.isEmpty() || visual.isBlank() ? null : ResourceLocation.tryParse(visual);
        outputFlipped = !output.isEmpty() && tag.getBoolean("OutputFlipped");
        clock = Math.max(0, tag.getLong("Clock"));
        if (tag.contains("Instance", Tag.TAG_INT)) instanceId = tag.getInt("Instance");
    }

    /**
     * Always carries the occupancy flag. An empty anvil would otherwise send an empty tag, which
     * {@link ClientboundBlockEntityDataPacket} turns into null and the client never loads, so a workpiece taken with
     * tongs stayed drawn on every client until the chunk was sent again.
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        tag.putBoolean("Occupied", isOccupied());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Loads every update, including an empty one, so the client can never keep a workpiece the server no longer has. */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        load(tag != null ? tag : new CompoundTag());
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
