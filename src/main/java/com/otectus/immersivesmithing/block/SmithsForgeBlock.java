package com.otectus.immersivesmithing.block;

import com.otectus.immersivesmithing.advancement.ModAdvancements;
import com.otectus.immersivesmithing.api.ImmersiveSmithingAPI;
import com.otectus.immersivesmithing.blockentity.SmithsForgeBlockEntity;
import com.otectus.immersivesmithing.config.ClientConfig;
import com.otectus.immersivesmithing.item.SmithingTongsItem;
import com.otectus.immersivesmithing.material.MaterialFamily;
import com.otectus.immersivesmithing.material.MaterialResolver;
import com.otectus.immersivesmithing.material.MaterialUnits;
import com.otectus.immersivesmithing.minigame.SessionManager;
import com.otectus.immersivesmithing.quality.QualityData;
import com.otectus.immersivesmithing.registry.ModBlockEntities;
import com.otectus.immersivesmithing.registry.ModItems;
import com.otectus.immersivesmithing.registry.ModParticles;
import com.otectus.immersivesmithing.registry.ModSounds;
import com.otectus.immersivesmithing.registry.ModTags;
import com.otectus.immersivesmithing.util.Feedback;
import com.otectus.immersivesmithing.util.StationEffects;
import com.otectus.immersivesmithing.work.ForgeActions;
import com.otectus.immersivesmithing.work.StationLayout;
import com.otectus.immersivesmithing.workpiece.WorkpieceCodec;
import com.otectus.immersivesmithing.workpiece.WorkpieceData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Smith's Forge. The basin takes metal, the firebox fuel and ignition; the billet tray, bellows, reheat rest and
 * pattern-book rest are parts of the same block, chosen from the hit position through {@link StationLayout#forgePart}.
 * Tool work (planning, committing, the bellows, turning and drawing the billet, reheating, recycling) goes through
 * {@link ForgeActions}, the same code the focused scene uses.
 */
public class SmithsForgeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty READY = BooleanProperty.create("ready");

    /**
     * Plinth, firebox walls round an open mouth (the right one also enclosing the bellows), the back wall, iron lintel and
     * the reheat bar across the mouth, hearth and the basin's rim stones: eleven boxes, within the station shape budget in
     * every facing. The divider between tray and basin, the bellows' outer pixel and its moving board are drawn but have no
     * collision of their own (their motion is cosmetic).
     */
    private static final StationShape SHAPE = new StationShape(Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 1, 4, 9, 12),
            Block.box(12, 2, 1, 15, 9, 12),
            Block.box(1, 2, 12, 15, 9, 15),
            Block.box(4, 7, 1, 12, 9, 4),
            Block.box(4, 5, 1.5, 12, 6, 2.5),
            Block.box(1, 9, 1, 15, 10, 15),
            Block.box(0, 10, 0, 16, 14, 3),
            Block.box(0, 10, 13, 16, 14, 16),
            Block.box(0, 10, 3, 3, 14, 13),
            Block.box(13, 10, 3, 16, 14, 13)));

    public SmithsForgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(READY, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? 13 : state.getValue(READY) ? 9 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, READY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.facing(state.getValue(FACING));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmithsForgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? createTickerHelper(type, ModBlockEntities.SMITHS_FORGE.get(), SmithsForgeBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.SMITHS_FORGE.get(), SmithsForgeBlockEntity::serverTick);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SmithsForgeBlockEntity forge) || forge.isEmpty()) return 0;
        if (forge.isReady()) return 15;
        return 1 + Math.min(13, 13 * forge.totalUnits() / Math.max(1, forge.capacity()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof SmithsForgeBlockEntity forge) {
            SessionManager.onStationRemoved(level, pos);
            com.otectus.immersivesmithing.work.WorkLeases.onStationGone(level, pos);
            forge.dropContents();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && held.getItem() instanceof BlockItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof SmithsForgeBlockEntity forge) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        net.minecraft.world.phys.Vec3 local = StationLayout.toLocal(pos, state.getValue(FACING), hit.getLocation());
        interact(sp, forge, held, hand, local);
        return InteractionResult.CONSUME;
    }

    private void interact(ServerPlayer player, SmithsForgeBlockEntity forge, ItemStack held, InteractionHand hand, net.minecraft.world.phys.Vec3 local) {
        if (forge.isLockedFor(player.getUUID())) {
            Feedback.fail(player, "station_in_use");
            return;
        }
        boolean sneaking = player.isSecondaryUseActive();
        boolean upper = local.y >= 0.5;
        StationLayout.ForgePart part = StationLayout.forgePart(local);
        Item item = held.getItem();

        if (item instanceof SmithingTongsItem) {
            ForgeActions.useTongs(player, forge, hand, part, sneaking);
            return;
        }
        if (item == ModItems.HOT_WORKPIECE.get()) {
            if (sneaking && part == StationLayout.ForgePart.BASIN) ForgeActions.recycle(player, forge, hand);
            else Feedback.info(player, "use_tongs_for_workpiece");
            return;
        }
        if (part == StationLayout.ForgePart.BELLOWS && !SmithsForgeBlockEntity.isFuel(held)) {
            ForgeActions.pump(player, forge, -1);
            return;
        }
        if (held.isEmpty()) {
            if (part == StationLayout.ForgePart.BOOK) ForgeActions.openBook(player, forge, hand);
            else if (sneaking && part == StationLayout.ForgePart.TRAY && forge.job() != null) ForgeActions.cancel(player, forge);
            else if (!sneaking) describe(player, forge);
            else if (upper) extractDeposit(player, forge);
            else extractFuel(player, forge);
            return;
        }
        if (held.is(ModTags.FORGE_IGNITERS)) {
            ignite(player, forge, held, hand);
            return;
        }
        if (held.is(Items.LAVA_BUCKET)) {
            addLava(player, forge, held, hand);
            return;
        }
        if (held.is(Items.BUCKET) && !upper) {
            if (forge.takeLavaBucket()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.LAVA_BUCKET)));
                player.level().playSound(null, forge.getBlockPos(), SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                Feedback.fail(player, "no_lava_to_take");
            }
            return;
        }
        boolean metal = MaterialResolver.resolve(held).isPresent();
        boolean fuel = SmithsForgeBlockEntity.isFuel(held);
        if (upper) {
            if (metal) insertMetal(player, forge, held, sneaking);
            else if (fuel) Feedback.fail(player, "fuel_goes_below");
            else if (part == StationLayout.ForgePart.BOOK) ForgeActions.openBook(player, forge, hand);
            else Feedback.fail(player, "not_metal", held.getHoverName());
        } else {
            if (fuel) insertFuel(player, forge, held, sneaking);
            else if (metal) Feedback.fail(player, "metal_goes_above");
            else Feedback.fail(player, "not_fuel", held.getHoverName());
        }
    }

    private void describe(ServerPlayer player, SmithsForgeBlockEntity forge) {
        com.otectus.immersivesmithing.work.ForgeJob job = forge.job();
        if (job != null) {
            Feedback.info(player, "forge_status_job", job.target.getHoverName(), Component.translatable("heat.immersive_smithing."
                    + job.thermal.band(com.otectus.immersivesmithing.work.ThermalRules.coolest(job.heat)).key()));
            return;
        }
        if (forge.isEmpty()) {
            Feedback.info(player, "forge_status_empty");
            return;
        }
        Component family = familyName(forge);
        if (forge.isReady()) {
            Feedback.info(player, forge.plan() != null ? "forge_status_ready_plan" : "forge_status_ready", family,
                    MaterialUnits.describe(forge.moltenUnits()));
        } else if (forge.meltProgress() > 0 && (forge.isBurning() || forge.hasLava())) {
            Feedback.info(player, "forge_status_melting", family, Math.round(forge.meltProgress() * 100));
        } else {
            Feedback.info(player, "forge_status_loaded", family, MaterialUnits.describe(forge.totalUnits()));
        }
    }

    private void insertMetal(ServerPlayer player, SmithsForgeBlockEntity forge, ItemStack held, boolean wholeStack) {
        boolean previouslyForged = QualityData.has(held);
        SmithsForgeBlockEntity.Result[] error = new SmithsForgeBlockEntity.Result[1];
        int inserted = forge.insertMetal(held, wholeStack ? held.getCount() : 1, false, error);
        if (inserted <= 0) {
            if (error[0] == SmithsForgeBlockEntity.Result.WRONG_FAMILY) Feedback.fail(player, "result.wrong_family", familyName(forge));
            else if (error[0] == SmithsForgeBlockEntity.Result.VETOED) Feedback.fail(player, ImmersiveSmithingAPI.meltVeto(held).orElse(Component.empty()));
            else Feedback.fail(player, "result." + error[0].key());
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(inserted);
        player.level().playSound(null, forge.getBlockPos(), ModSounds.FORGE_DEPOSIT.get(), SoundSource.BLOCKS, 0.8F, 0.9F + player.getRandom().nextFloat() * 0.2F);
        if (previouslyForged) ModAdvancements.award(player, ModAdvancements.AGAIN);
    }

    private void insertFuel(ServerPlayer player, SmithsForgeBlockEntity forge, ItemStack held, boolean wholeStack) {
        SmithsForgeBlockEntity.Result[] error = new SmithsForgeBlockEntity.Result[1];
        int inserted = forge.insertFuel(held, wholeStack ? held.getCount() : 1, false, error);
        if (inserted <= 0) {
            Feedback.fail(player, "result." + error[0].key());
            return;
        }
        if (!player.getAbilities().instabuild) held.shrink(inserted);
        player.level().playSound(null, forge.getBlockPos(), ModSounds.FORGE_FUEL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    private void addLava(ServerPlayer player, SmithsForgeBlockEntity forge, ItemStack held, InteractionHand hand) {
        SmithsForgeBlockEntity.Result result = forge.addLavaBucket(false);
        if (result != SmithsForgeBlockEntity.Result.OK) {
            Feedback.fail(player, "result." + result.key());
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.BUCKET)));
        }
        player.level().playSound(null, forge.getBlockPos(), SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void ignite(ServerPlayer player, SmithsForgeBlockEntity forge, ItemStack held, InteractionHand hand) {
        SmithsForgeBlockEntity.Result result = forge.ignite();
        if (result == SmithsForgeBlockEntity.Result.WRONG_FUEL) {
            Feedback.fail(player, "result.wrong_fuel", familyName(forge), requiredFuelName(forge.requiredFuelTag()));
            return;
        }
        if (result != SmithsForgeBlockEntity.Result.OK) {
            Feedback.fail(player, "result." + result.key());
            return;
        }
        if (held.isDamageableItem()) {
            held.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        } else if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        StationEffects.send((ServerLevel) player.level(), forge.getBlockPos(), StationEffects.IGNITE, 1.0F);
        ModAdvancements.award(player, ModAdvancements.STOKE_THE_FORGE);
    }

    private void extractDeposit(ServerPlayer player, SmithsForgeBlockEntity forge) {
        if (forge.deposits().isEmpty()) {
            Feedback.fail(player, forge.moltenUnits() > 0 ? "molten_cannot_be_removed" : "forge_status_empty");
            return;
        }
        if (!forge.canExtractDeposits()) {
            Feedback.fail(player, "cannot_extract_while_heating");
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, forge.extractLastDeposit());
    }

    private void extractFuel(ServerPlayer player, SmithsForgeBlockEntity forge) {
        if (forge.fuel().isEmpty()) {
            Feedback.fail(player, forge.hasLava() ? "lava_needs_bucket" : "no_fuel_to_take");
            return;
        }
        ItemStack fuel = forge.extractFuel();
        if (fuel.isEmpty()) {
            Feedback.fail(player, "cannot_extract_burning_fuel");
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, fuel);
    }

    private static Component familyName(SmithsForgeBlockEntity forge) {
        return forge.familyData().map(MaterialFamily::displayName).orElse(Component.literal("?"));
    }

    private static Component requiredFuelName(@Nullable TagKey<Item> tag) {
        if (tag == null) return Component.literal("?");
        return BuiltInRegistries.ITEM.getTag(tag)
                .flatMap(set -> set.stream().findFirst())
                .<Component>map(holder -> holder.value().getDescription())
                .orElse(Component.literal("#" + tag.location()));
    }

    // ------------------------------------------------------------------ ambience (client)

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double cx = pos.getX() + 0.5;
        double cz = pos.getZ() + 0.5;
        if (state.getValue(LIT)) {
            int flames = ClientConfig.scaleParticles(ClientConfig.get(ClientConfig.REDUCED_FLASHES) ? 1 : 2);
            for (int i = 0; i < flames; i++) {
                double ox = (random.nextDouble() - 0.5) * 0.6;
                double oz = (random.nextDouble() - 0.5) * 0.6;
                double fx = cx + ox + facing.getStepX() * 0.3;
                double fz = cz + oz + facing.getStepZ() * 0.3;
                level.addParticle(ParticleTypes.FLAME, fx, pos.getY() + 0.2 + random.nextDouble() * 0.2, fz, 0, 0.01, 0);
            }
            if (random.nextFloat() < 0.4F * ClientConfig.scaleParticles(10) / 10F) {
                level.addParticle(ParticleTypes.SMOKE, cx + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.95, cz + (random.nextDouble() - 0.5) * 0.5, 0, 0.04, 0);
            }
            if (random.nextFloat() < 0.15F * ClientConfig.scaleParticles(10) / 10F) {
                level.addParticle(ModParticles.SPARK.get(), cx + (random.nextDouble() - 0.5) * 0.4, pos.getY() + 0.7, cz + (random.nextDouble() - 0.5) * 0.4,
                        (random.nextDouble() - 0.5) * 0.05, 0.08, (random.nextDouble() - 0.5) * 0.05);
            }
            if (random.nextInt(8) == 0) {
                level.playLocalSound(cx, pos.getY() + 0.3, cz, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.7F, 0.9F, false);
            }
            if (random.nextInt(40) == 0) {
                level.playLocalSound(cx, pos.getY() + 0.5, cz, ModSounds.FORGE_ROAR.get(), SoundSource.BLOCKS, 0.4F, 0.8F + random.nextFloat() * 0.2F, false);
            }
        }
        if (state.getValue(READY) && !ClientConfig.get(ClientConfig.REDUCED_FLASHES)
                && random.nextFloat() < 0.10F * ClientConfig.scaleParticles(10) / 10F) {
            level.addParticle(ModParticles.SPARK.get(), cx + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.85,
                    cz + (random.nextDouble() - 0.5) * 0.5, 0, 0.025, 0);
        }
    }
}
