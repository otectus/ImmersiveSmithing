package com.otectus.immersivesmithing.blockentity;

import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.work.GrindstoneActions;
import com.otectus.immersivesmithing.work.WorkLeases;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The grindstone's station state: the finished piece resting at the wheel, which aspect is being refined, and the smith
 * currently holding an edge to the turning wheel. A pass needs continuous contact for the configured time; the XP and the
 * score change are committed together when it completes ({@link GrindstoneActions#finishPass}), and an interrupted pass
 * costs and gives nothing. Older worlds' grindstones get this block entity the first time they are used; nothing else in
 * the block changes. The piece on the rest belongs to the workshop like a chest's contents: anyone can take it.
 */
public class SmithsGrindstoneBlockEntity extends BlockEntity {
    private ItemStack piece = ItemStack.EMPTY;
    private boolean efficacy;
    @Nullable
    private UUID contact;
    /** The smith holding the edge, while the pass lasts (never saved). */
    private java.lang.ref.WeakReference<net.minecraft.server.level.ServerPlayer> contactPlayer = new java.lang.ref.WeakReference<>(null);
    private int contactTicks;
    private long lastKeepalive;
    private long clock;
    private int instanceId = new java.util.Random().nextInt();

    // Client presentation only.
    public float wheelAngle;
    public float wheelAngleO;
    private float wheelSpeed;

    public SmithsGrindstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMITHS_GRINDSTONE.get(), pos, state);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.25, 0, 0.25).expandTowards(0, 0.5, 0);
    }

    public ItemStack piece() {
        return piece;
    }

    public boolean efficacy() {
        return efficacy;
    }

    @Nullable
    public UUID contact() {
        return contact;
    }

    public int contactTicks() {
        return contactTicks;
    }

    public long clock() {
        return clock;
    }

    public int instanceId() {
        return instanceId;
    }

    public int passTicks() {
        return ServerConfig.get(ServerConfig.GRINDSTONE_PASS_TICKS);
    }

    public void setPiece(ItemStack stack) {
        piece = stack;
        stopContact();
        sync();
    }

    public ItemStack takePiece() {
        ItemStack out = piece;
        piece = ItemStack.EMPTY;
        stopContact();
        sync();
        return out;
    }

    /** The piece was changed in place (a completed pass): resync it. */
    public void pieceChanged() {
        sync();
    }

    public void setEfficacy(boolean efficacy) {
        if (this.efficacy == efficacy) return;
        this.efficacy = efficacy;
        stopContact();
        sync();
    }

    /** Starts or continues holding the edge to the wheel. A different smith cannot take over a pass in progress. */
    public boolean holdContact(net.minecraft.server.level.ServerPlayer smith) {
        if (contact != null && !contact.equals(smith.getUUID())) return false;
        if (contact == null) {
            contact = smith.getUUID();
            contactTicks = 0;
            sync();
        }
        contactPlayer = new java.lang.ref.WeakReference<>(smith);
        lastKeepalive = clock;
        return true;
    }

    @Nullable
    public net.minecraft.server.level.ServerPlayer contactPlayer() {
        net.minecraft.server.level.ServerPlayer player = contactPlayer.get();
        return player != null && player.getUUID().equals(contact) ? player : null;
    }

    public void stopContact() {
        boolean had = contact != null;
        contact = null;
        contactTicks = 0;
        if (had) sync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmithsGrindstoneBlockEntity be) {
        be.clock++;
        if (be.contact == null) return;
        if (be.piece.isEmpty() || be.clock - be.lastKeepalive > 10) {
            be.stopContact();
            return;
        }
        be.contactTicks++;
        if (be.contactTicks % 4 == 0) GrindstoneActions.contactEffects((ServerLevel) level, be);
        if (be.contactTicks >= be.passTicks()) GrindstoneActions.finishPass((ServerLevel) level, be);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SmithsGrindstoneBlockEntity be) {
        be.clock++;
        be.wheelAngleO = be.wheelAngle;
        float target = be.contact != null ? 24F : 0F;
        be.wheelSpeed += (target - be.wheelSpeed) * 0.15F;
        be.wheelAngle = (be.wheelAngle + be.wheelSpeed) % 360F;
        if (be.contact != null) be.contactTicks++;
    }

    public void dropContents() {
        if (level == null || piece.isEmpty()) return;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, piece);
        piece = ItemStack.EMPTY;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) WorkLeases.onStationGone(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!piece.isEmpty()) tag.put("Piece", piece.save(new CompoundTag()));
        tag.putBoolean("Efficacy", efficacy);
        tag.putLong("Clock", clock);
        tag.putInt("Instance", instanceId);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        piece = tag.contains("Piece", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Piece")) : ItemStack.EMPTY;
        efficacy = tag.getBoolean("Efficacy");
        clock = Math.max(0, tag.getLong("Clock"));
        if (tag.contains("Instance", Tag.TAG_INT)) instanceId = tag.getInt("Instance");
        contact = tag.hasUUID("Contact") ? tag.getUUID("Contact") : null;
        contactTicks = tag.getInt("ContactTicks");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        if (contact != null) tag.putUUID("Contact", contact);
        tag.putInt("ContactTicks", contactTicks);
        tag.putBoolean("Occupied", !piece.isEmpty());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

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
