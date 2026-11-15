package com.otectus.immersivesmithing.blockentity;

import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.block.SmithsForgeBlock;
import com.otectus.immersivesmithing.config.ServerConfig;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.recipe.SmithingData;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.registry.ModTags;
import com.otectus.immersivesmithing.work.ForgeJob;
import com.otectus.immersivesmithing.work.ProcessState;
import com.otectus.immersivesmithing.work.ThermalRules;
import com.otectus.immersivesmithing.work.WorkLeases;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * State of a Smith's Forge: the active material family, unmelted deposits (kept as the original stacks so they can be
 * taken back before melting), molten units, fuel or lava, melt progress, the bellows' air, the pinned plan, at most one
 * committed {@link ForgeJob} (reserved metal, escrowed components and the billet on the tray) and the piece on the reheat
 * rest. Conservation: {@code moltenUnits} always includes the job's reserved units until the billet is drawn.
 */
public class SmithsForgeBlockEntity extends BlockEntity {
    public static final ItemStack LAVA_HEAT_SOURCE = new ItemStack(Items.LAVA_BUCKET);
    private static final int SYNC_INTERVAL = 20;

    /** Metal put into the forge but not yet melted. */
    public record Deposit(ItemStack stack, int unitsEach) {
        int units() {
            return stack.getCount() * unitsEach;
        }
    }

    public enum Result {
        OK, LOCKED, NOT_METAL, WRONG_FAMILY, FULL, NOT_FUEL, DIFFERENT_FUEL, LAVA_PRESENT, SOLID_FUEL_PRESENT,
        NOTHING_TO_MELT, NO_FUEL, WRONG_FUEL, ALREADY_LIT, LAVA_NEEDS_NO_IGNITION, UNKNOWN_FAMILY,
        /** Another mod refused melting this item ({@code ImmersiveSmithingAPI#meltVeto} gives the reason). */
        VETOED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    @Nullable
    private ResourceLocation family;
    private int familyTint = MaterialFamily.DEFAULT_TINT;
    private final List<Deposit> deposits = new ArrayList<>();
    private int moltenUnits;
    private int meltTicks;
    private ItemStack fuel = ItemStack.EMPTY;
    private ItemStack burningFuel = ItemStack.EMPTY;
    private int burnTicks;
    private int burnTicksTotal;
    private int lavaTicks;
    private boolean ignited;
    /**
     * Ticks a lit fire stays banked once nothing is being heated and its current fuel item has burned down: lit but burning
     * nothing, so a piece put back on the reheat rest (or a new billet) lights the next fuel without a new spark.
     */
    private int bankedTicks;
    private static final int BANKED_TICKS = 1200;
    /** Escrow from a save made before jobs existed: refunded exactly once on the first tick. */
    private final List<ItemStack> legacyEscrow = new ArrayList<>();

    private long clock;
    private int instanceId = new java.util.Random().nextInt();
    private int air;
    private long lastPump = -1000;
    @Nullable
    private ResourceLocation plan;
    @Nullable
    private ForgeJob job;
    @Nullable
    private CompoundTag jobRaw;
    @Nullable
    private WorkpieceData rest;
    @Nullable
    private CompoundTag restRaw;
    private boolean fireOn;

    // Transient: the pattern-book selection lock never survives a restart or chunk unload.
    @Nullable
    private UUID sessionOwner;

    private final LazyOptional<IItemHandler> metalInput = LazyOptional.of(() -> new InputHandler(true));
    private final LazyOptional<IItemHandler> fuelInput = LazyOptional.of(() -> new InputHandler(false));

    public SmithsForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMITHS_FORGE.get(), pos, state);
    }

    /** The book, drawing and supply bundle rise above the rim; retain normal frustum culling. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 0.5, 0);
    }

    // ---------------------------------------------------------------- queries

    @Nullable
    public ResourceLocation family() {
        return family;
    }

    public Optional<MaterialFamily> familyData() {
        return SmithingData.current().materials().family(family);
    }

    public int familyTint() {
        return familyTint;
    }

    public List<Deposit> deposits() {
        return deposits;
    }

    public int depositUnits() {
        int sum = 0;
        for (Deposit d : deposits) sum += d.units();
        return sum;
    }

    /** Every molten unit in the reservoir, reserved ones included. */
    public int moltenUnits() {
        return moltenUnits;
    }

    /** Units a committed job holds; they stay in the reservoir until the billet is drawn. */
    public int reservedUnits() {
        return job != null ? Math.min(job.units, moltenUnits) : 0;
    }

    /** Molten units free for casting, a new job or the pattern book. */
    public int availableUnits() {
        return Math.max(0, moltenUnits - reservedUnits());
    }

    public int totalUnits() {
        return moltenUnits + depositUnits();
    }

    public int capacity() {
        return ServerConfig.get(ServerConfig.FORGE_CAPACITY_UNITS);
    }

    public boolean isReady() {
        return moltenUnits > 0 && deposits.isEmpty();
    }

    public boolean isEmpty() {
        return moltenUnits == 0 && deposits.isEmpty();
    }

    public ItemStack fuel() {
        return fuel;
    }

    public ItemStack burningFuel() {
        return burningFuel;
    }

    public int lavaTicks() {
        return lavaTicks;
    }

    public boolean hasLava() {
        return lavaTicks > 0;
    }

    public boolean isBurning() {
        return burnTicks > 0 || (ignited && !fuel.isEmpty());
    }

    /** Whether the fire is heating this tick (fuel the family accepts, or lava). */
    public boolean fireOn() {
        return fireOn;
    }

    public float meltProgress() {
        int required = requiredMeltTicks();
        return required <= 0 ? 0F : Math.min(1F, meltTicks / (float) required);
    }

    public boolean isLocked() {
        return sessionOwner != null;
    }

    public boolean isLockedFor(UUID player) {
        return sessionOwner != null && !sessionOwner.equals(player);
    }

    public int requiredMeltTicks() {
        float multiplier = familyData().map(MaterialFamily::meltMultiplier).orElse(1.0F);
        return Math.max(20, Math.round(ServerConfig.get(ServerConfig.BASE_MELT_TIME_TICKS) * multiplier));
    }

    public long clock() {
        return clock;
    }

    public int instanceId() {
        return instanceId;
    }

    public int air() {
        return air;
    }

    public long lastPump() {
        return lastPump;
    }

    @Nullable
    public ResourceLocation plan() {
        return plan;
    }

    @Nullable
    public ForgeJob job() {
        return job;
    }

    public boolean hasUnreadableJob() {
        return jobRaw != null;
    }

    @Nullable
    public WorkpieceData rest() {
        return rest;
    }

    public boolean restOccupied() {
        return rest != null || restRaw != null;
    }

    // ---------------------------------------------------------------- metal

    /** Adds up to {@code max} items of {@code stack} as metal. Returns how many were (or would be) accepted. */
    public int insertMetal(ItemStack stack, int max, boolean simulate, Result[] error) {
        if (isLocked()) return fail(error, Result.LOCKED);
        Optional<MaterialResolver.Resolved> resolved = MaterialResolver.resolve(stack);
        if (resolved.isEmpty()) return fail(error, Result.NOT_METAL);
        if (ImmersiveSmithingAPI.meltVeto(stack).isPresent()) return fail(error, Result.VETOED);
        MaterialResolver.Resolved r = resolved.get();
        if (family != null && !family.equals(r.family().id())) return fail(error, Result.WRONG_FAMILY);
        int space = capacity() - totalUnits();
        int fit = Math.min(Math.min(max, stack.getCount()), space / r.unitsEach());
        if (fit <= 0) return fail(error, Result.FULL);
        if (!simulate) {
            ItemStack added = stack.copyWithCount(fit);
            Deposit last = deposits.isEmpty() ? null : deposits.get(deposits.size() - 1);
            if (last != null && last.unitsEach() == r.unitsEach() && ItemStack.isSameItemSameTags(last.stack(), added)
                    && last.stack().getCount() + fit <= last.stack().getMaxStackSize()) {
                last.stack().grow(fit);
            } else {
                deposits.add(new Deposit(added, r.unitsEach()));
            }
            if (family == null) meltTicks = 0;
            family = r.family().id();
            familyTint = r.family().tint();
            changed(true);
        }
        return fit;
    }

    /** Takes back the most recent unmelted deposit. Only possible while the forge is not heating. */
    public ItemStack extractLastDeposit() {
        if (deposits.isEmpty()) return ItemStack.EMPTY;
        Deposit d = deposits.remove(deposits.size() - 1);
        if (deposits.isEmpty()) meltTicks = 0;
        if (isEmpty() && job == null) family = null;
        changed(true);
        return d.stack();
    }

    public boolean canExtractDeposits() {
        return !deposits.isEmpty() && !ignited && !hasUsableLava();
    }

    /** Deliberate recycling: melts a workpiece straight back into molten units. Its progress is destroyed. */
    public Result acceptWorkpiece(WorkpieceData workpiece) {
        if (isLocked()) return Result.LOCKED;
        if (com.otectus.immersivesmithing.compat.epicknights.ExternalWorkflow.owns(workpiece.target())) return Result.UNKNOWN_FAMILY;
        Optional<MaterialFamily> wpFamily = SmithingData.current().materials().family(workpiece.family());
        if (wpFamily.isEmpty()) return Result.UNKNOWN_FAMILY;
        if (family != null && !family.equals(workpiece.family())) return Result.WRONG_FAMILY;
        int recovery = workpiece.recoverableNow();
        if ((long) totalUnits() + recovery > capacity()) return Result.FULL;
        moltenUnits += recovery;
        family = wpFamily.get().id();
        familyTint = wpFamily.get().tint();
        changed(true);
        return Result.OK;
    }

    // ---------------------------------------------------------------- fuel

    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.LAVA_BUCKET)) return true;
        if (stack.hasCraftingRemainingItem()) return false;
        if (ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) <= 0) return false;
        return stack.is(ModTags.FORGE_FUELS) || ServerConfig.get(ServerConfig.ACCEPT_ANY_FURNACE_FUEL);
    }

    public int insertFuel(ItemStack stack, int max, boolean simulate, Result[] error) {
        if (isLocked()) return fail(error, Result.LOCKED);
        if (!isFuel(stack) || stack.is(Items.LAVA_BUCKET)) return fail(error, Result.NOT_FUEL);
        if (lavaTicks > 0) return fail(error, Result.LAVA_PRESENT);
        if (!fuel.isEmpty() && !ItemStack.isSameItemSameTags(fuel, stack)) return fail(error, Result.DIFFERENT_FUEL);
        int fit = Math.min(Math.min(max, stack.getCount()), stack.getMaxStackSize() - fuel.getCount());
        if (fit <= 0) return fail(error, Result.FULL);
        if (!simulate) {
            if (fuel.isEmpty()) fuel = stack.copyWithCount(fit);
            else fuel.grow(fit);
            changed(true);
        }
        return fit;
    }

    public Result addLavaBucket(boolean simulate) {
        if (isLocked()) return Result.LOCKED;
        if (!fuel.isEmpty() || burnTicks > 0) return Result.SOLID_FUEL_PRESENT;
        int perBucket = ServerConfig.get(ServerConfig.LAVA_BUCKET_HEAT_TICKS);
        if (lavaTicks + perBucket > perBucket * ServerConfig.get(ServerConfig.MAX_LAVA_BUCKETS)) return Result.FULL;
        if (!simulate) {
            lavaTicks += perBucket;
            changed(true);
        }
        return Result.OK;
    }

    /** Scoops a full bucket of lava back out, if one bucket's worth remains. */
    public boolean takeLavaBucket() {
        int perBucket = ServerConfig.get(ServerConfig.LAVA_BUCKET_HEAT_TICKS);
        if (isLocked() || lavaTicks < perBucket) return false;
        lavaTicks -= perBucket;
        changed(true);
        return true;
    }

    public ItemStack extractFuel() {
        if (fuel.isEmpty() || ignited) return ItemStack.EMPTY;
        ItemStack out = fuel;
        fuel = ItemStack.EMPTY;
        changed(true);
        return out;
    }

    /** Lights the fire: there must be something to heat (metal to melt, a billet, or a piece on the rest). */
    public Result ignite() {
        if (isLocked()) return Result.LOCKED;
        if (!wantsHeat()) return Result.NOTHING_TO_MELT;
        if (lavaTicks > 0) return Result.LAVA_NEEDS_NO_IGNITION;
        if (fuel.isEmpty() && burnTicks <= 0) return Result.NO_FUEL;
        ItemStack source = !fuel.isEmpty() ? fuel : burningFuel;
        if (!fuelAllowed(requiredFuelTag(), source)) return Result.WRONG_FUEL;
        if (ignited && burnTicks > 0) return Result.ALREADY_LIT;
        ignited = true;
        changed(true);
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.FORGE_IGNITE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return Result.OK;
    }

    @Nullable
    public TagKey<Item> requiredFuelTag() {
        return familyData().map(MaterialFamily::requiredFuelTag).orElse(null);
    }

    /** Whether the current heat source satisfies a family's fuel rule (null: any). */
    public boolean heatSourceAllows(@Nullable TagKey<Item> required) {
        if (lavaTicks > 0) return fuelAllowed(required, LAVA_HEAT_SOURCE);
        return fuelAllowed(required, burningFuel.isEmpty() ? fuel : burningFuel);
    }

    /**
     * Whether the forge has a fire that will heat a piece of {@code pieceFamily} from the next tick: lava, or fuel that is
     * burning or lit, accepted by that metal. ({@link #fireOn()} only says whether something was heated this tick.)
     */
    public boolean canHeat(@Nullable ResourceLocation pieceFamily) {
        Optional<MaterialFamily> own = familyData();
        boolean lightable = !fuel.isEmpty() && (ignited || own.map(f -> !f.requiresIgnition()).orElse(false));
        boolean fire = lavaTicks > 0 || burnTicks > 0 || lightable;
        TagKey<Item> required = pieceFamily == null ? null
                : SmithingData.current().materials().family(pieceFamily).map(MaterialFamily::requiredFuelTag).orElse(null);
        return fire && heatSourceAllows(required);
    }

    private boolean hasUsableLava() {
        return lavaTicks > 0 && fuelAllowed(requiredFuelTag(), LAVA_HEAT_SOURCE);
    }

    private static boolean fuelAllowed(@Nullable TagKey<Item> required, ItemStack source) {
        return required == null || (!source.isEmpty() && source.is(required));
    }

    private boolean wantsHeat() {
        return !deposits.isEmpty() || job != null || rest != null;
    }

    // ---------------------------------------------------------------- ticking

    public static void serverTick(Level level, BlockPos pos, BlockState state, SmithsForgeBlockEntity be) {
        be.tick();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SmithsForgeBlockEntity be) {
        be.clock++;
        boolean lit = state.hasProperty(SmithsForgeBlock.LIT) && state.getValue(SmithsForgeBlock.LIT);
        be.simulateHeat(lit, false);
    }

    private void tick() {
        if (level == null) return;
        clock++;
        if (!legacyEscrow.isEmpty()) {
            for (ItemStack stack : legacyEscrow) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
            }
            legacyEscrow.clear();
            setChanged();
        }
        boolean sync = false;
        boolean heating = false;
        if (wantsHeat()) {
            HeatResult heat = consumeHeat();
            sync |= heat.changed;
            heating = heat.heating;
            if (heating) bankedTicks = BANKED_TICKS;
            if (!deposits.isEmpty() && heating) {
                meltTicks++;
                if (meltTicks >= requiredMeltTicks()) {
                    completeMelt();
                    sync = true;
                } else if (meltTicks % 20 == 0) {
                    setChanged();
                }
            }
        } else if (burnTicks > 0) {
            // Nothing left to heat: the current fuel item burns down, then the fire banks (below).
            burnTicks--;
            if (burnTicks == 0) {
                burningFuel = ItemStack.EMPTY;
                sync = true;
            }
        } else if (ignited) {
            // A banked fire between heats: it burns no fuel and goes out after a minute, or at once without fuel.
            if (bankedTicks > 0 && !fuel.isEmpty()) {
                bankedTicks--;
                if (bankedTicks % 20 == 0) setChanged();
            } else {
                ignited = false;
                bankedTicks = 0;
                sync = true;
            }
        }
        fireOn = heating;
        boolean warmWork = simulateHeat(heating, true);
        if (warmWork && clock % SYNC_INTERVAL == 0) sync = true;
        if (sync) changed(true);
        else if (warmWork && clock % 100 == 0) setChanged();
        updateBlockState();
    }

    /**
     * One tick of the thermal model for the billet and the rest piece; shared by the server and the client mirror. Returns
     * whether anything is warm and changing.
     */
    private boolean simulateHeat(boolean lit, boolean server) {
        int target = ThermalRules.fireTarget(lit, air);
        boolean active = air > 0;
        if (job != null) {
            for (int z = 0; z < job.heat.length; z++) {
                job.heat[z] = ThermalRules.heatStep(job.heat[z], ThermalRules.trayExposure(job.position, z), target, job.thermal);
            }
            if (server) {
                job.scale = Math.min(1000, job.scale + ThermalRules.scaleStep(job.heat, job.thermal));
                if (ServerConfig.get(ServerConfig.WORK_OVERHEAT_BURNS) && ThermalRules.burning(job.heat)) {
                    if (++job.burnTicks >= 60) job.burnt = true;
                }
            }
            active = true;
        }
        if (rest != null) {
            ProcessState p = rest.process();
            boolean restLit = lit && (!server || heatSourceAllows(SmithingData.current().materials().family(rest.family())
                    .map(MaterialFamily::requiredFuelTag).orElse(null)));
            int restTarget = restLit ? target : 0;
            for (int z = 0; z < p.heat.length; z++) p.heat[z] = ThermalRules.heatStep(p.heat[z], ThermalRules.REST_EXPOSURE, restTarget, p.thermal);
            if (server) {
                p.scale = Math.min(1000, p.scale + ThermalRules.scaleStep(p.heat, p.thermal));
                if (ServerConfig.get(ServerConfig.WORK_OVERHEAT_BURNS) && ThermalRules.burning(p.heat)) {
                    if (++p.burnTicks >= 60) p.burnt = true;
                }
            }
            active = true;
        }
        air = ThermalRules.airStep(air);
        return active;
    }

    private record HeatResult(boolean heating, boolean changed) {}

    private HeatResult consumeHeat() {
        MaterialFamily fam = familyData().orElse(null);
        TagKey<Item> required = fam != null ? fam.requiredFuelTag() : null;
        if (lavaTicks > 0 && fuelAllowed(required, LAVA_HEAT_SOURCE)) {
            lavaTicks--;
            return new HeatResult(true, lavaTicks == 0);
        }
        boolean needsIgnition = fam == null || fam.requiresIgnition();
        if (!ignited) {
            if (needsIgnition || fuel.isEmpty() || !fuelAllowed(required, fuel)) return new HeatResult(false, false);
            ignited = true;
        }
        boolean changed = false;
        if (burnTicks <= 0) {
            if (!fuel.isEmpty() && fuelAllowed(required, fuel)) {
                double multiplier = ServerConfig.get(ServerConfig.FUEL_CONSUMPTION_MULTIPLIER);
                int ticks = (int) Math.max(1, ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING) / multiplier);
                burningFuel = fuel.copyWithCount(1);
                fuel.shrink(1);
                if (fuel.isEmpty()) fuel = ItemStack.EMPTY;
                burnTicks = burnTicksTotal = ticks;
                changed = true;
            } else {
                ignited = false;
                burningFuel = ItemStack.EMPTY;
                return new HeatResult(false, true);
            }
        }
        burnTicks--;
        return new HeatResult(fuelAllowed(required, burningFuel), changed);
    }

    private void completeMelt() {
        moltenUnits += depositUnits();
        deposits.clear();
        meltTicks = 0;
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.FORGE_READY.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void updateBlockState() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmithsForgeBlock)) return;
        boolean lit = (ignited && burnTicks > 0) || lavaTicks > 0;
        boolean ready = isReady();
        if (state.getValue(SmithsForgeBlock.LIT) != lit || state.getValue(SmithsForgeBlock.READY) != ready) {
            level.setBlock(worldPosition, state.setValue(SmithsForgeBlock.LIT, lit).setValue(SmithsForgeBlock.READY, ready), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- bellows, plan and job

    /** One compression of the bellows. False while the previous stroke is still in progress (not extra air). */
    public boolean pump() {
        if (clock - lastPump < ThermalRules.PUMP_COOLDOWN_TICKS) return false;
        lastPump = clock;
        air = Math.min(ThermalRules.AIR_MAX, air + ThermalRules.AIR_PER_PUMP);
        changed(true);
        return true;
    }

    public void setPlan(@Nullable ResourceLocation recipe) {
        plan = recipe;
        changed(true);
    }

    /** Takes a fully validated job: the caller has already taken the escrow from the smith. */
    public void beginJob(ForgeJob newJob) {
        job = newJob;
        changed(true);
    }

    /** Moves the billet along the tray. */
    public void turnBillet(int position) {
        if (job == null) return;
        job.position = Math.floorMod(position, ThermalRules.TRAY_POSITIONS);
        changed(true);
    }

    /** Cancels a job before its billet is drawn: the reservation lapses and the escrow is handed back exactly once. */
    public List<ItemStack> cancelJob() {
        if (job == null) return List.of();
        List<ItemStack> refund = new ArrayList<>(job.escrow);
        job = null;
        if (isEmpty()) family = null;
        changed(true);
        updateBlockState();
        return refund;
    }

    /**
     * Draws the billet: the reserved units leave the reservoir and the escrow becomes the workpiece's (components to fit,
     * assimilated bases), all in this one call. Null if there is no job or the reservoir no longer holds its units.
     */
    @Nullable
    public WorkpieceData drawJob() {
        if (job == null || moltenUnits < job.units) return null;
        WorkpieceData piece = job.draw();
        moltenUnits -= job.units;
        job = null;
        if (isEmpty()) family = null;
        changed(true);
        updateBlockState();
        return piece;
    }

    public void touchJob(long gameTime) {
        if (job != null) {
            job.lastTouch = gameTime;
            setChanged();
        }
    }

    public void placeOnRest(WorkpieceData piece) {
        ProcessState p = piece.process().copy();
        p.heatTick = ProcessState.ITEM_HELD; // the forge simulates the rest piece tick by tick
        rest = piece.withProcess(p);
        changed(true);
    }

    @Nullable
    public WorkpieceData takeFromRest() {
        WorkpieceData out = rest;
        if (out == null) return null;
        rest = null;
        changed(true);
        ProcessState p = out.process().copy();
        p.heatTick = ProcessState.ITEM_HELD;
        p.revision++;
        return out.withProcess(p);
    }

    // ---------------------------------------------------------------- selection lock (pattern book)

    public void lockForSelection(UUID player) {
        sessionOwner = player;
    }

    public void endSession() {
        sessionOwner = null;
    }

    @Nullable
    public UUID sessionOwner() {
        return sessionOwner;
    }

    // ---------------------------------------------------------------- casting

    /**
     * Takes molten metal out to be cast into ingots or nuggets: only from a ready forge, never more than is free of a job's
     * reservation. The family is cleared once the forge is empty. Returns false and changes nothing otherwise.
     */
    public boolean castUnits(int units) {
        if (units <= 0 || !isReady() || units > availableUnits()) return false;
        moltenUnits -= units;
        if (isEmpty() && job == null) family = null;
        changed(true);
        updateBlockState();
        return true;
    }

    /** Puts back units a cast could not deliver, with the family the cast took them from if that emptied the forge. */
    public void refundCast(int units, ResourceLocation castFamily) {
        if (units <= 0) return;
        if (family == null) family = castFamily;
        moltenUnits += units;
        changed(true);
        updateBlockState();
    }

    // ---------------------------------------------------------------- removal

    /**
     * Drops what can be recovered: unmelted deposits, solid fuel, an undrawn job's escrow (refunded, the reservation lapses),
     * the piece on the rest as a hot workpiece, and any legacy escrow. Molten metal is lost.
     */
    public void dropContents() {
        if (level == null) return;
        List<ItemStack> drops = new ArrayList<>();
        deposits.forEach(d -> drops.add(d.stack()));
        if (!fuel.isEmpty()) drops.add(fuel);
        if (job != null) drops.addAll(job.escrow);
        drops.addAll(legacyEscrow);
        if (rest != null || restRaw != null) {
            ItemStack piece = new ItemStack(ModItems.HOT_WORKPIECE.get());
            if (rest != null) WorkpieceCodec.setHeld(piece, rest);
            else WorkpieceCodec.setRawHeld(piece, restRaw);
            drops.add(piece);
        }
        deposits.clear();
        fuel = ItemStack.EMPTY;
        job = null;
        legacyEscrow.clear();
        rest = null;
        restRaw = null;
        for (ItemStack stack : drops) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            SessionManager.onStationUnloaded(level, worldPosition);
            WorkLeases.onStationGone(level, worldPosition);
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        writeState(tag, true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        family = tag.contains("Family") ? ResourceLocation.tryParse(tag.getString("Family")) : null;
        familyTint = tag.contains("Tint") ? tag.getInt("Tint") : MaterialFamily.DEFAULT_TINT;
        deposits.clear();
        ListTag list = tag.getList("Deposits", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag d = list.getCompound(i);
            ItemStack stack = ItemStack.of(d.getCompound("Stack"));
            int units = d.getInt("Units");
            if (!stack.isEmpty() && units > 0) deposits.add(new Deposit(stack, units));
        }
        moltenUnits = Math.max(0, tag.getInt("Molten"));
        meltTicks = tag.getInt("MeltTicks");
        fuel = ItemStack.of(tag.getCompound("Fuel"));
        burningFuel = ItemStack.of(tag.getCompound("Burning"));
        burnTicks = tag.getInt("BurnTicks");
        burnTicksTotal = tag.getInt("BurnTotal");
        lavaTicks = tag.getInt("Lava");
        ignited = tag.getBoolean("Ignited");
        bankedTicks = Math.max(0, Math.min(BANKED_TICKS, tag.getInt("Banked")));
        clock = Math.max(0, tag.getLong("Clock"));
        if (tag.contains("Instance", Tag.TAG_INT)) instanceId = tag.getInt("Instance");
        air = Math.max(0, Math.min(ThermalRules.AIR_MAX, tag.getInt("Air")));
        lastPump = tag.contains("LastPump") ? tag.getLong("LastPump") : -1000;
        plan = tag.contains("Plan", Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString("Plan")) : null;
        boolean full = !tag.getBoolean("Partial");
        job = null;
        jobRaw = null;
        if (tag.contains("Job", Tag.TAG_COMPOUND)) {
            job = ForgeJob.load(tag.getCompound("Job"), full);
            if (job == null && full) jobRaw = tag.getCompound("Job").copy();
        }
        rest = null;
        restRaw = null;
        if (tag.contains("Rest", Tag.TAG_COMPOUND)) {
            WorkpieceCodec.Read read = WorkpieceCodec.read(tag.getCompound("Rest"));
            if (read.ok()) rest = read.data();
            else restRaw = tag.getCompound("Rest").copy();
        }
        legacyEscrow.clear();
        // Saves from before jobs: escrow without a job belonged to a minigame session that cannot survive a reload, so it is
        // refunded at the forge exactly once, as it always was. It is never mistaken for a committed billet.
        ListTag esc = tag.getList("Escrow", Tag.TAG_COMPOUND);
        for (int i = 0; i < esc.size(); i++) {
            ItemStack stack = ItemStack.of(esc.getCompound(i));
            if (!stack.isEmpty()) legacyEscrow.add(stack);
        }
    }

    private void writeState(CompoundTag tag, boolean full) {
        if (family != null) tag.putString("Family", family.toString());
        tag.putInt("Tint", familyTint);
        ListTag list = new ListTag();
        for (Deposit d : deposits) {
            CompoundTag c = new CompoundTag();
            c.put("Stack", d.stack().save(new CompoundTag()));
            c.putInt("Units", d.unitsEach());
            list.add(c);
        }
        tag.put("Deposits", list);
        tag.putInt("Molten", moltenUnits);
        tag.putInt("MeltTicks", meltTicks);
        tag.put("Fuel", fuel.save(new CompoundTag()));
        tag.put("Burning", burningFuel.save(new CompoundTag()));
        tag.putInt("BurnTicks", burnTicks);
        tag.putInt("BurnTotal", burnTicksTotal);
        tag.putInt("Lava", lavaTicks);
        tag.putBoolean("Ignited", ignited);
        if (bankedTicks > 0) tag.putInt("Banked", bankedTicks);
        tag.putLong("Clock", clock);
        tag.putInt("Instance", instanceId);
        tag.putInt("Air", air);
        tag.putLong("LastPump", lastPump);
        if (plan != null) tag.putString("Plan", plan.toString());
        if (job != null) tag.put("Job", job.save(full));
        else if (jobRaw != null && full) tag.put("Job", jobRaw);
        if (rest != null) tag.put("Rest", WorkpieceCodec.save(rest));
        else if (restRaw != null && full) tag.put("Rest", restRaw);
        if (full && !legacyEscrow.isEmpty()) {
            ListTag esc = new ListTag();
            for (ItemStack stack : legacyEscrow) esc.add(stack.save(new CompoundTag()));
            tag.put("Escrow", esc);
        }
        if (!full) tag.putBoolean("Partial", true);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeState(tag, false);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Every update replaces the client copy, so a cancelled job or a taken piece never lingers on screen. */
    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    private void changed(boolean sync) {
        setChanged();
        if (sync && level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Forces a client update after an external change (a GameTest, a command). */
    public void sync() {
        changed(true);
    }

    private static int fail(Result[] error, Result result) {
        if (error != null && error.length > 0) error[0] = result;
        return 0;
    }

    // ---------------------------------------------------------------- automation

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && side != null && ServerConfig.get(ServerConfig.ENABLE_AUTOMATION)) {
            String face = relativeFace(side);
            if (ServerConfig.list(ServerConfig.FORGE_METAL_FACES).contains(face)) return metalInput.cast();
            if (ServerConfig.list(ServerConfig.FORGE_FUEL_FACES).contains(face)) return fuelInput.cast();
        }
        return super.getCapability(cap, side);
    }

    /** The face name as seen on a north-facing forge, so config is independent of placement. */
    private String relativeFace(Direction side) {
        if (side.getAxis().isVertical()) return side.getName();
        Direction facing = getBlockState().hasProperty(SmithsForgeBlock.FACING) ? getBlockState().getValue(SmithsForgeBlock.FACING) : Direction.NORTH;
        int turns = (side.get2DDataValue() - facing.get2DDataValue() + 4) % 4;
        return Direction.from2DDataValue(Direction.NORTH.get2DDataValue() + turns).getName();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        metalInput.invalidate();
        fuelInput.invalidate();
    }

    /** Insert-only handler. Automation can load metal or fuel but never extract, plan, commit or work a billet. */
    private final class InputHandler implements IItemHandler {
        private final boolean metal;

        InputHandler(boolean metal) {
            this.metal = metal;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || level == null || level.isClientSide) return stack;
            int accepted;
            if (metal) {
                accepted = insertMetal(stack, stack.getCount(), simulate, null);
            } else {
                if (stack.is(Items.LAVA_BUCKET)) return stack; // buckets would need their container handed back
                accepted = insertFuel(stack, stack.getCount(), simulate, null);
            }
            if (accepted <= 0) return stack;
            return stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (!metal) return isFuel(stack) && !stack.is(Items.LAVA_BUCKET);
            return MaterialResolver.resolve(stack).isPresent() && ImmersiveSmithingAPI.meltVeto(stack).isEmpty();
        }
    }
}
